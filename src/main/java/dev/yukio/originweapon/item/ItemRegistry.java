package dev.yukio.originweapon.item;

import dev.yukio.originweapon.OriginWeapon;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.event.Listener;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Реестр всех предметов плагина. */
public final class ItemRegistry {

    private final OriginWeapon plugin;
    private final Map<String, OriginItem> items = new LinkedHashMap<>();

    public ItemRegistry(OriginWeapon plugin) {
        this.plugin = plugin;
    }

    public void register(OriginItem item) {
        if (items.putIfAbsent(item.id(), item) != null) {
            throw new IllegalStateException("Предмет с id '" + item.id() + "' уже зарегистрирован");
        }
        if (item instanceof Listener listener) {
            plugin.getServer().getPluginManager().registerEvents(listener, plugin);
        }
    }

    /** Перечитать настройки всех предметов из текущего конфига. */
    public void loadAll() {
        ConfigurationSection root = plugin.getConfig().getConfigurationSection("items");
        for (OriginItem item : items.values()) {
            item.load(root == null ? null : root.getConfigurationSection(item.id()));
            plugin.debug("Предмет " + item.id() + ": " + (item.isEnabled() ? "включён" : "выключен"));
        }
    }

    public void shutdown() {
        items.values().forEach(OriginItem::shutdown);
    }

    public @Nullable OriginItem get(String id) {
        return items.get(id);
    }

    public Collection<OriginItem> all() {
        return Collections.unmodifiableCollection(items.values());
    }
}
