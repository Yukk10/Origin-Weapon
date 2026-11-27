package dev.yukio.originweapon.item;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Базовый контракт предмета Origin-Weapon.
 * Каждый предмет — отдельный класс в пакете item/, со своей секцией
 * в config.yml по {@link #id()}.
 * <p>
 * Если класс предмета реализует {@link org.bukkit.event.Listener},
 * {@link ItemRegistry} сам зарегистрирует его как слушатель.
 */
public interface OriginItem {

    /** Уникальный id предмета, он же ключ секции items.&lt;id&gt; в config.yml. */
    String id();

    /**
     * Перечитать настройки предмета из его секции конфига.
     * Вызывается при включении плагина и по /originweapon reload.
     *
     * @param section секция items.&lt;id&gt; (null, если её нет — предмет считается выключенным)
     */
    void load(@Nullable ConfigurationSection section);

    /** items.&lt;id&gt;.enabled — выключенный предмет не работает и не выдаётся. */
    boolean isEnabled();

    /** Является ли стак этим предметом (PDC-метка плагина или id ItemsAdder). */
    boolean matches(@Nullable ItemStack stack);

    /** Создать предмет для выдачи командой. */
    ItemStack createItem();

    /** Очистка при выключении плагина. */
    default void shutdown() {
    }
}
