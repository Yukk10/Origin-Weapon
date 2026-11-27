package dev.yukio.originweapon.effect;

import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Одна запись частиц из конфига.
 * <pre>
 * - type: CLOUD     # id частицы (CLOUD, POOF, minecraft:gust ...)
 *   shape: RING     # POINT — облако в точке, RING — кольцо, разлетающееся наружу
 *   count: 16       # кол-во частиц (для RING — точек на кольце)
 *   radius: 0.6     # радиус кольца (только RING)
 *   offset-x: 0.3   # разброс (только POINT)
 *   offset-y: 0.05
 *   offset-z: 0.3
 *   speed: 0.05     # скорость частиц
 * </pre>
 */
public record ParticleEffect(Particle particle, Shape shape, int count, double radius,
                             double offsetX, double offsetY, double offsetZ, double speed) {

    public enum Shape { POINT, RING }

    public void play(Location at) {
        World world = at.getWorld();
        if (world == null || count <= 0) {
            return;
        }
        if (shape == Shape.POINT) {
            world.spawnParticle(particle, at, count, offsetX, offsetY, offsetZ, speed);
            return;
        }
        for (int i = 0; i < count; i++) {
            double angle = 2 * Math.PI * i / count;
            double dx = Math.cos(angle);
            double dz = Math.sin(angle);
            // count = 0 → offset задаёт направление движения, speed — его скорость
            world.spawnParticle(particle, at.getX() + dx * radius, at.getY(), at.getZ() + dz * radius,
                    0, dx, 0, dz, speed);
        }
    }

    /** Прочитать список частиц (path — путь к списку внутри section). */
    public static List<ParticleEffect> parseList(ConfigurationSection section, String path, Logger logger) {
        List<ParticleEffect> result = new ArrayList<>();
        String where = section.getCurrentPath() + "." + path;
        for (Map<?, ?> raw : section.getMapList(path)) {
            MemoryConfiguration entry = new MemoryConfiguration();
            raw.forEach((k, v) -> entry.set(String.valueOf(k), v));
            ParticleEffect effect = parse(entry, where, logger);
            if (effect != null) {
                result.add(effect);
            }
        }
        return List.copyOf(result);
    }

    private static @Nullable ParticleEffect parse(ConfigurationSection entry, String where, Logger logger) {
        String type = entry.getString("type", "");
        NamespacedKey key = NamespacedKey.fromString(type.toLowerCase(Locale.ROOT));
        Particle particle = key == null ? null : Registry.PARTICLE_TYPE.get(key);
        if (particle == null) {
            logger.warning(where + ": неизвестная частица '" + type + "', пропускаю.");
            return null;
        }
        if (particle.getDataType() != Void.class) {
            logger.warning(where + ": частица '" + type + "' требует доп. данные и не поддерживается, пропускаю.");
            return null;
        }
        Shape shape;
        try {
            shape = Shape.valueOf(entry.getString("shape", "POINT").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            logger.warning(where + ": неизвестная форма '" + entry.getString("shape") + "', использую POINT.");
            shape = Shape.POINT;
        }
        return new ParticleEffect(particle, shape,
                Math.max(0, entry.getInt("count", 10)),
                entry.getDouble("radius", 0.6),
                entry.getDouble("offset-x", 0.3),
                entry.getDouble("offset-y", 0.05),
                entry.getDouble("offset-z", 0.3),
                entry.getDouble("speed", 0.05));
    }
}
