package dev.yukio.originweapon;

import dev.yukio.originweapon.command.OriginWeaponCommand;
import dev.yukio.originweapon.config.ConfigUpdater;
import dev.yukio.originweapon.hook.ItemsAdderHook;
import dev.yukio.originweapon.item.ItemRegistry;
import dev.yukio.originweapon.item.avian.AvianWeapon;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.NamespacedKey;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public final class OriginWeapon extends JavaPlugin {

    private NamespacedKey itemIdKey;
    private ItemRegistry registry;
    private boolean debug;

    @Override
    public void onEnable() {
        itemIdKey = new NamespacedKey(this, "item_id");

        ConfigUpdater.update(this);
        reloadConfig();
        debug = getConfig().getBoolean("debug", false);

        ItemsAdderHook.init(getLogger());

        registry = new ItemRegistry(this);
        // ── предметы ─────────────────────────────────────────
        registry.register(new AvianWeapon(this));
        // ─────────────────────────────────────────────────────
        registry.loadAll();

        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event ->
                event.registrar().register(OriginWeaponCommand.build(this),
                        "Origin-Weapon: выдача предметов и перезагрузка", List.of("ow")));
    }

    @Override
    public void onDisable() {
        if (registry != null) {
            registry.shutdown();
        }
    }

    /** Перечитать config.yml (с дописыванием новых ключей) и настройки всех предметов. */
    public void reload() {
        ConfigUpdater.update(this);
        reloadConfig();
        debug = getConfig().getBoolean("debug", false);
        registry.loadAll();
    }

    public ItemRegistry registry() {
        return registry;
    }

    /** PDC-ключ originweapon:item_id. */
    public NamespacedKey itemIdKey() {
        return itemIdKey;
    }

    public void debug(String message) {
        if (debug) {
            getLogger().info("[debug] " + message);
        }
    }

    /** Отправить сообщение из секции messages (MiniMessage, с префиксом). */
    public void send(CommandSender to, String key, TagResolver... placeholders) {
        String raw = getConfig().getString("messages." + key, "");
        if (raw.isEmpty()) {
            return;
        }
        TagResolver resolver = TagResolver.resolver(
                TagResolver.resolver(placeholders),
                Placeholder.parsed("prefix", getConfig().getString("messages.prefix", "")));
        to.sendMessage(MiniMessage.miniMessage().deserialize(raw, resolver));
    }
}
