package dev.yukio.originweapon;

import org.bukkit.plugin.java.JavaPlugin;

public final class OriginWeapon extends JavaPlugin {

    @Override
    public void onEnable() {
        saveDefaultConfig();
        // TODO: обновление config.yml (дописывать новые предметы, не трогая правки)
        // TODO: регистрация предметов в ItemRegistry и их слушателей
        // TODO: команды /originweapon give|reload
    }

    @Override
    public void onDisable() {
    }
}
