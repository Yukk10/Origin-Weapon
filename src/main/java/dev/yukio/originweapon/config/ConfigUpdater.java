package dev.yukio.originweapon.config;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Дополняет config.yml на сервере недостающими ключами из дефолтного конфига
 * (вместе с комментариями). Значения, которые уже есть у админа, не трогаются.
 */
public final class ConfigUpdater {

    private static final String VERSION_KEY = "config-version";

    private ConfigUpdater() {
    }

    public static void update(JavaPlugin plugin) {
        File file = new File(plugin.getDataFolder(), "config.yml");
        if (!file.exists()) {
            plugin.saveDefaultConfig();
            return;
        }

        YamlConfiguration defaults = new YamlConfiguration();
        try (InputStream in = plugin.getResource("config.yml")) {
            if (in == null) {
                return;
            }
            try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                defaults.load(reader);
            }
        } catch (IOException | InvalidConfigurationException e) {
            plugin.getLogger().severe("Не удалось прочитать встроенный config.yml: " + e.getMessage());
            return;
        }

        YamlConfiguration current = new YamlConfiguration();
        try {
            current.load(file);
        } catch (IOException | InvalidConfigurationException e) {
            // не перезаписываем сломанный файл — админ потеряет свои настройки
            plugin.getLogger().severe("config.yml содержит ошибку, автообновление пропущено: " + e.getMessage());
            return;
        }

        List<String> added = merge(current, defaults);

        int defaultVersion = defaults.getInt(VERSION_KEY, 1);
        int currentVersion = current.getInt(VERSION_KEY, 0);
        boolean versionChanged = currentVersion < defaultVersion;
        if (versionChanged) {
            current.set(VERSION_KEY, defaultVersion);
        }

        if (added.isEmpty() && !versionChanged) {
            return;
        }

        try {
            File backup = new File(plugin.getDataFolder(), "config.yml.bak");
            Files.copy(file.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
            current.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Не удалось сохранить обновлённый config.yml: " + e.getMessage());
            return;
        }

        List<String> topLevel = added.stream()
                .filter(p -> added.stream().noneMatch(other -> p.startsWith(other + ".")))
                .toList();
        plugin.getLogger().info("config.yml обновлён (версия " + currentVersion + " → " + Math.max(currentVersion, defaultVersion)
                + "), добавлено: " + (topLevel.isEmpty() ? "—" : String.join(", ", topLevel))
                + ". Копия старого файла: config.yml.bak");
    }

    /**
     * Дописать в current ключи из defaults, которых там нет (с комментариями).
     *
     * @return добавленные пути (включая вложенные)
     */
    public static List<String> merge(YamlConfiguration current, YamlConfiguration defaults) {
        List<String> added = new ArrayList<>();
        for (String path : defaults.getKeys(true)) {
            if (path.equals(VERSION_KEY) || current.contains(path)) {
                continue;
            }
            int dot = path.lastIndexOf('.');
            if (dot > 0) {
                String parent = path.substring(0, dot);
                if (current.contains(parent) && !current.isConfigurationSection(parent)) {
                    continue; // у админа на месте секции стоит значение — не ломаем
                }
            }
            if (defaults.isConfigurationSection(path)) {
                current.createSection(path);
            } else {
                current.set(path, defaults.get(path));
            }
            current.setComments(path, defaults.getComments(path));
            current.setInlineComments(path, defaults.getInlineComments(path));
            added.add(path);
        }

        return added;
    }
}
