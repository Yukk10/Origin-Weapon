package dev.yukio.originweapon.item;

/**
 * Базовый контракт предмета Origin-Weapon.
 * Каждый предмет — отдельный класс в пакете item/, со своей секцией
 * в config.yml по {@link #id()}.
 */
public interface OriginItem {

    /** Уникальный id предмета, он же ключ секции items.&lt;id&gt; в config.yml. */
    String id();
}
