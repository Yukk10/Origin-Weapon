package dev.yukio.originweapon.item.avian;

import dev.yukio.originweapon.OriginWeapon;
import dev.yukio.originweapon.effect.ParticleEffect;
import dev.yukio.originweapon.effect.SoundEffect;
import dev.yukio.originweapon.item.AbstractOriginItem;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInputEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.util.Vector;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Оружие авианов (ItemsAdder: avian_weapon).
 * <p>
 * Пока предмет в основной или второй руке: прыжок с земли, затем повторное
 * нажатие прыжка в воздухе — двойной прыжок. Игрока подбрасывает на
 * {@code double-jump.height} блоков, под ним появляются облака и звучит порыв ветра.
 * <p>
 * Нажатие прыжка ловится через {@link PlayerInputEvent} (клиент сам сообщает
 * о нажатых клавишах), поэтому полёт игроку не включается и античиты не ругаются.
 */
public final class AvianWeapon extends AbstractOriginItem implements Listener {

    public static final String ID = "avian_weapon";

    /** Сколько тиков после прыжка с земли игрок ещё может числиться «на земле». */
    private static final int GROUND_GRACE_TICKS = 5;

    private enum Phase { GROUND_JUMP, AIRBORNE, USED }

    private static final class JumpState {
        Phase phase;
        int armedTick;
        double dx;
        double dz;

        JumpState(Phase phase, int tick) {
            this.phase = phase;
            this.armedTick = tick;
        }
    }

    private final Map<UUID, JumpState> states = new HashMap<>();
    private final Map<UUID, Integer> readyAtTick = new HashMap<>();

    // --- настройки ---
    private boolean mainHand;
    private boolean offHand;
    private double height;
    private double jumpVelocity;
    private double keepMomentum;
    private double forwardBoost;
    private boolean requireGroundJump;
    private int cooldownTicks;
    private String cooldownMessage;
    private boolean resetFallDistance;
    private boolean allowInWater;
    private Set<GameMode> gameModes = EnumSet.of(GameMode.SURVIVAL, GameMode.ADVENTURE);
    private boolean particlesEnabled;
    private double particleYOffset;
    private List<ParticleEffect> particles = List.of();
    private boolean soundsEnabled;
    private List<SoundEffect> sounds = List.of();

    public AvianWeapon(OriginWeapon plugin) {
        super(plugin, ID);
    }

    @Override
    protected void loadSettings(ConfigurationSection section) {
        mainHand = section.getBoolean("hands.main-hand", true);
        offHand = section.getBoolean("hands.off-hand", true);

        ConfigurationSection jump = section.getConfigurationSection("double-jump");
        if (jump == null) {
            jump = new MemoryConfiguration();
        }
        height = Math.max(0, jump.getDouble("height", 4.0));
        jumpVelocity = velocityForHeight(height);
        keepMomentum = jump.getDouble("keep-momentum", 1.0);
        forwardBoost = jump.getDouble("forward-boost", 0.0);
        requireGroundJump = jump.getBoolean("require-ground-jump", true);
        cooldownTicks = Math.max(0, jump.getInt("cooldown-ticks", 0));
        cooldownMessage = jump.getString("cooldown-message", "");
        resetFallDistance = jump.getBoolean("reset-fall-distance", true);
        allowInWater = jump.getBoolean("allow-in-water", false);
        Set<GameMode> modes = EnumSet.noneOf(GameMode.class);
        for (String mode : jump.getStringList("gamemodes")) {
            try {
                modes.add(GameMode.valueOf(mode.trim().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("items." + ID + ".double-jump.gamemodes: неизвестный режим '" + mode + "'.");
            }
        }
        gameModes = modes;

        particlesEnabled = section.getBoolean("particles.enabled", true);
        particleYOffset = section.getDouble("particles.y-offset", 0.0);
        ConfigurationSection particleSection = section.getConfigurationSection("particles");
        particles = particleSection == null ? List.of()
                : ParticleEffect.parseList(particleSection, "list", plugin.getLogger());

        soundsEnabled = section.getBoolean("sounds.enabled", true);
        ConfigurationSection soundSection = section.getConfigurationSection("sounds");
        sounds = soundSection == null ? List.of()
                : SoundEffect.parseList(soundSection, "list", plugin.getLogger());

        plugin.debug(ID + ": высота " + height + " бл. → скорость " + String.format(Locale.ROOT, "%.3f", jumpVelocity));
    }

    @Override
    public void shutdown() {
        states.clear();
        readyAtTick.clear();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInput(PlayerInputEvent event) {
        if (!isEnabled()) {
            return;
        }
        Player player = event.getPlayer();
        // getCurrentInput() во время события — ещё предыдущее состояние клавиш
        boolean jumpPressed = event.getInput().isJump() && !player.getCurrentInput().isJump();
        if (!jumpPressed) {
            return;
        }
        UUID uuid = player.getUniqueId();
        int now = Bukkit.getCurrentTick();

        if (isOnGround(player)) {
            // первый прыжок — только если предмет в руке
            if (isHolding(player)) {
                states.put(uuid, new JumpState(Phase.GROUND_JUMP, now));
            } else {
                states.remove(uuid);
            }
            return;
        }

        JumpState state = states.get(uuid);
        boolean ready = state != null ? state.phase == Phase.AIRBORNE : !requireGroundJump;
        if (!ready || !isHolding(player) || !canDoubleJump(player)) {
            return;
        }

        Integer readyAt = readyAtTick.get(uuid);
        if (readyAt != null && now < readyAt) {
            sendCooldown(player, readyAt - now);
            return;
        }

        if (state == null) {
            state = new JumpState(Phase.USED, now);
            states.put(uuid, state);
        }
        performDoubleJump(player, state);
        state.phase = Phase.USED;
        if (cooldownTicks > 0) {
            readyAtTick.put(uuid, now + cooldownTicks);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        JumpState state = states.get(event.getPlayer().getUniqueId());
        if (state == null) {
            return;
        }
        Player player = event.getPlayer();
        Location from = event.getFrom();
        Location to = event.getTo();
        state.dx = to.getX() - from.getX();
        state.dz = to.getZ() - from.getZ();

        if (!isOnGround(player)) {
            if (state.phase == Phase.GROUND_JUMP) {
                state.phase = Phase.AIRBORNE;
            }
            return;
        }
        int now = Bukkit.getCurrentTick();
        if (state.phase == Phase.GROUND_JUMP && now - state.armedTick <= GROUND_GRACE_TICKS) {
            return; // только что прыгнул, сервер ещё не увидел отрыв от земли
        }
        // приземлился: при зажатом пробеле клиент сразу прыгнет снова
        if (isEnabled() && player.getCurrentInput().isJump() && isHolding(player)) {
            state.phase = Phase.GROUND_JUMP;
            state.armedTick = now;
        } else {
            states.remove(player.getUniqueId());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        states.remove(event.getPlayer().getUniqueId());
        readyAtTick.remove(event.getPlayer().getUniqueId());
    }

    private void performDoubleJump(Player player, JumpState state) {
        Vector velocity = new Vector(state.dx * keepMomentum, jumpVelocity, state.dz * keepMomentum);
        if (forwardBoost != 0) {
            Vector look = player.getLocation().getDirection().setY(0);
            if (look.lengthSquared() > 1.0E-6) {
                velocity.add(look.normalize().multiply(forwardBoost));
            }
        }
        player.setVelocity(velocity);
        if (resetFallDistance) {
            player.setFallDistance(0);
        }

        Location feet = player.getLocation();
        if (particlesEnabled) {
            Location at = feet.clone().add(0, particleYOffset, 0);
            for (ParticleEffect effect : particles) {
                effect.play(at);
            }
        }
        if (soundsEnabled) {
            for (SoundEffect sound : sounds) {
                sound.play(feet);
            }
        }
        plugin.debug(ID + ": двойной прыжок " + player.getName());
    }

    private boolean isHolding(Player player) {
        if (!hasPermission(player)) {
            return false;
        }
        PlayerInventory inv = player.getInventory();
        return (mainHand && matches(inv.getItemInMainHand()))
                || (offHand && matches(inv.getItemInOffHand()));
    }

    private boolean canDoubleJump(Player player) {
        if (!gameModes.contains(player.getGameMode())) {
            return false;
        }
        if (player.isFlying() || player.isGliding() || player.isInsideVehicle()
                || player.isRiptiding() || player.isClimbing()) {
            return false;
        }
        return allowInWater || !(player.isInWater() || player.isInLava() || player.isSwimming());
    }

    private void sendCooldown(Player player, int ticksLeft) {
        if (cooldownMessage == null || cooldownMessage.isEmpty()) {
            return;
        }
        String seconds = String.format(Locale.ROOT, "%.1f", ticksLeft / 20.0);
        player.sendActionBar(MiniMessage.miniMessage().deserialize(cooldownMessage,
                Placeholder.unparsed("time", seconds)));
    }

    @SuppressWarnings("deprecation") // onGround приходит от клиента — для этой механики достаточно
    private static boolean isOnGround(Player player) {
        return player.isOnGround();
    }

    /**
     * Начальная вертикальная скорость, чтобы подняться на height блоков.
     * Физика игрока за тик: y += v; v = (v - 0.08) * 0.98.
     */
    static double velocityForHeight(double height) {
        if (height <= 0) {
            return 0;
        }
        double lo = 0;
        double hi = 10;
        for (int i = 0; i < 50; i++) {
            double mid = (lo + hi) / 2;
            if (apex(mid) < height) {
                lo = mid;
            } else {
                hi = mid;
            }
        }
        return hi;
    }

    private static double apex(double velocity) {
        double y = 0;
        double v = velocity;
        while (v > 0) {
            y += v;
            v = (v - 0.08) * 0.98;
        }
        return y;
    }
}
