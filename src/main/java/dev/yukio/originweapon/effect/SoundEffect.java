package dev.yukio.originweapon.effect;

import net.kyori.adventure.key.InvalidKeyException;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Один звук из конфига. Ключ может быть ванильным или из ресурспака (ItemsAdder).
 * <pre>
 * - key: "minecraft:entity.wind_charge.wind_burst"
 *   source: PLAYER   # MASTER, MUSIC, RECORD, WEATHER, BLOCK, HOSTILE, NEUTRAL, PLAYER, AMBIENT, VOICE
 *   volume: 1.0
 *   pitch: 1.0       # 0.5 – 2.0
 * </pre>
 */
public record SoundEffect(Sound sound) {

    public void play(Location at) {
        World world = at.getWorld();
        if (world != null) {
            world.playSound(sound, at.getX(), at.getY(), at.getZ());
        }
    }

    public static List<SoundEffect> parseList(ConfigurationSection section, String path, Logger logger) {
        List<SoundEffect> result = new ArrayList<>();
        String where = section.getCurrentPath() + "." + path;
        for (Map<?, ?> raw : section.getMapList(path)) {
            MemoryConfiguration entry = new MemoryConfiguration();
            raw.forEach((k, v) -> entry.set(String.valueOf(k), v));
            String keyString = entry.getString("key", "");
            Key key;
            try {
                key = Key.key(keyString.toLowerCase(Locale.ROOT));
            } catch (InvalidKeyException e) {
                logger.warning(where + ": неверный ключ звука '" + keyString + "', пропускаю.");
                continue;
            }
            Sound.Source source = Sound.Source.NAMES.value(entry.getString("source", "player").toLowerCase(Locale.ROOT));
            if (source == null) {
                logger.warning(where + ": неизвестный source '" + entry.getString("source") + "', использую PLAYER.");
                source = Sound.Source.PLAYER;
            }
            result.add(new SoundEffect(Sound.sound(key, source,
                    (float) entry.getDouble("volume", 1.0),
                    (float) entry.getDouble("pitch", 1.0))));
        }
        return List.copyOf(result);
    }
}
