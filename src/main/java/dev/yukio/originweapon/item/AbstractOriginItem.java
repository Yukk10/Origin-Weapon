package dev.yukio.originweapon.item;

import dev.yukio.originweapon.OriginWeapon;
import dev.yukio.originweapon.hook.ItemsAdderHook;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Общая часть всех предметов: enabled, имя/лор, запасной материал,
 * привязка к предметам ItemsAdder и PDC-метка {@code originweapon:item_id}.
 * <p>
 * Конкретный предмет переопределяет {@link #loadSettings(ConfigurationSection)}
 * и читает там свои параметры.
 */
public abstract class AbstractOriginItem implements OriginItem {

    protected final OriginWeapon plugin;
    private final String id;

    private boolean enabled;
    private String permission = "";
    private Component name = Component.empty();
    private List<Component> lore = List.of();
    private Material material = Material.STICK;

    /** id ItemsAdder: "id" (любой namespace) или "namespace:id", в нижнем регистре. */
    private List<String> itemsAdderIds = List.of();
    private String itemsAdderGiveId = "";
    private boolean overrideItemsAdderDisplay;

    protected AbstractOriginItem(OriginWeapon plugin, String id) {
        this.plugin = plugin;
        this.id = id;
    }

    @Override
    public final String id() {
        return id;
    }

    @Override
    public final boolean isEnabled() {
        return enabled;
    }

    @Override
    public final void load(@Nullable ConfigurationSection section) {
        if (section == null) {
            enabled = false;
            plugin.getLogger().warning("В config.yml нет секции items." + id + " — предмет выключен.");
            return;
        }
        enabled = section.getBoolean("enabled", true);
        permission = section.getString("permission", "").trim();

        MiniMessage mm = MiniMessage.miniMessage();
        name = noItalic(mm.deserialize(section.getString("name", id)));
        List<Component> loreLines = new ArrayList<>();
        for (String line : section.getStringList("lore")) {
            loreLines.add(noItalic(mm.deserialize(line)));
        }
        lore = List.copyOf(loreLines);

        String materialName = section.getString("material", "STICK");
        Material parsed = Material.matchMaterial(materialName);
        if (parsed == null || !parsed.isItem()) {
            plugin.getLogger().warning("items." + id + ".material: неизвестный материал '" + materialName + "', использую STICK.");
            parsed = Material.STICK;
        }
        material = parsed;

        ConfigurationSection ia = section.getConfigurationSection("itemsadder");
        if (ia != null) {
            itemsAdderIds = ia.getStringList("ids").stream()
                    .map(s -> s.trim().toLowerCase(Locale.ROOT))
                    .filter(s -> !s.isEmpty())
                    .toList();
            itemsAdderGiveId = ia.getString("give-id", "").trim();
            overrideItemsAdderDisplay = ia.getBoolean("override-name-lore", false);
        } else {
            itemsAdderIds = List.of();
            itemsAdderGiveId = "";
            overrideItemsAdderDisplay = false;
        }

        loadSettings(section);
    }

    /** items.&lt;id&gt;.permission — если задано, способности работают только у игроков с этим правом. */
    protected boolean hasPermission(Player player) {
        return permission.isEmpty() || player.hasPermission(permission);
    }

    /** Чтение параметров конкретного предмета. */
    protected abstract void loadSettings(ConfigurationSection section);

    @Override
    public boolean matches(@Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        String tagged = stack.getPersistentDataContainer().get(plugin.itemIdKey(), PersistentDataType.STRING);
        if (id.equals(tagged)) {
            return true;
        }
        if (itemsAdderIds.isEmpty()) {
            return false;
        }
        String iaId = ItemsAdderHook.namespacedId(stack);
        if (iaId == null) {
            return false;
        }
        iaId = iaId.toLowerCase(Locale.ROOT);
        int colon = iaId.indexOf(':');
        String bare = colon >= 0 ? iaId.substring(colon + 1) : iaId;
        for (String wanted : itemsAdderIds) {
            if (wanted.indexOf(':') >= 0 ? wanted.equals(iaId) : wanted.equals(bare)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public ItemStack createItem() {
        ItemStack stack = null;
        String giveId = !itemsAdderGiveId.isEmpty() ? itemsAdderGiveId
                : (itemsAdderIds.isEmpty() ? "" : itemsAdderIds.getFirst());
        if (!giveId.isEmpty()) {
            stack = ItemsAdderHook.createItem(giveId);
        }
        boolean fromItemsAdder = stack != null;
        if (stack == null) {
            stack = ItemStack.of(material);
        }
        NamespacedKey key = plugin.itemIdKey();
        boolean applyDisplay = !fromItemsAdder || overrideItemsAdderDisplay;
        stack.editMeta(meta -> {
            if (applyDisplay) {
                meta.displayName(name);
                meta.lore(lore.isEmpty() ? null : lore);
            }
            meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, id);
        });
        return stack;
    }

    private static Component noItalic(Component component) {
        return component.decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }
}
