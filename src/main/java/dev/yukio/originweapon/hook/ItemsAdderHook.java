package dev.yukio.originweapon.hook;

import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Locale;
import java.util.logging.Logger;

/**
 * Мягкая интеграция с ItemsAdder через рефлексию (API CustomStack),
 * чтобы плагин собирался и работал и без ItemsAdder на сервере.
 */
public final class ItemsAdderHook {

    private static final String CUSTOM_STACK = "dev.lone.itemsadder.api.CustomStack";

    private static boolean available;
    private static Method byItemStack;
    private static Method getNamespacedId;
    private static Method getInstance;
    private static Method getItemStack;
    private static @Nullable Method getRegistryIds;

    private ItemsAdderHook() {
    }

    public static void init(Logger logger) {
        available = false;
        if (Bukkit.getPluginManager().getPlugin("ItemsAdder") == null) {
            logger.info("ItemsAdder не найден — предметы распознаются только по метке плагина.");
            return;
        }
        try {
            Class<?> cs = Class.forName(CUSTOM_STACK, true, ItemsAdderHook.class.getClassLoader());
            byItemStack = cs.getMethod("byItemStack", ItemStack.class);
            getNamespacedId = cs.getMethod("getNamespacedID");
            getInstance = cs.getMethod("getInstance", String.class);
            getItemStack = cs.getMethod("getItemStack");
            try {
                getRegistryIds = cs.getMethod("getNamespacedIdsInRegistry");
            } catch (NoSuchMethodException ignored) {
                getRegistryIds = null;
            }
            available = true;
            logger.info("ItemsAdder найден — включено распознавание предметов по id ItemsAdder.");
        } catch (ReflectiveOperationException | LinkageError e) {
            logger.warning("ItemsAdder найден, но его API недоступно: " + e);
        }
    }

    public static boolean isAvailable() {
        return available;
    }

    /** namespace:id предмета ItemsAdder или null, если это не предмет ItemsAdder. */
    public static @Nullable String namespacedId(ItemStack stack) {
        if (!available) {
            return null;
        }
        try {
            Object custom = byItemStack.invoke(null, stack);
            return custom == null ? null : (String) getNamespacedId.invoke(custom);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    /**
     * Новый стак предмета ItemsAdder. Принимает "namespace:id" или просто "id"
     * (тогда ищется первый предмет с таким id в любом namespace).
     */
    public static @Nullable ItemStack createItem(String id) {
        if (!available) {
            return null;
        }
        try {
            Object custom = getInstance.invoke(null, id);
            if (custom == null && id.indexOf(':') < 0 && getRegistryIds != null) {
                String suffix = ":" + id.toLowerCase(Locale.ROOT);
                Object ids = getRegistryIds.invoke(null);
                if (ids instanceof Collection<?> all) {
                    for (Object candidate : all) {
                        if (candidate instanceof String s && s.toLowerCase(Locale.ROOT).endsWith(suffix)) {
                            custom = getInstance.invoke(null, s);
                            break;
                        }
                    }
                }
            }
            return custom == null ? null : ((ItemStack) getItemStack.invoke(custom)).clone();
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }
}
