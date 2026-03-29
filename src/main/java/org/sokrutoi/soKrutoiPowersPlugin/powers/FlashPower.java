package org.sokrutoi.soKrutoiPowersPlugin.powers;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.GameMode;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import org.sokrutoi.soKrutoiPowersPlugin.SoKrutoiPowersPlugin;
import org.sokrutoi.soKrutoiPowersPlugin.listeners.FlashListener;

import java.util.*;

public class FlashPower extends SuperPower implements Listener {

    private final Set<UUID>         players         = new HashSet<>();
    private final Set<UUID>         active          = new HashSet<>();
    private final Map<UUID, Long>   dashCooldownEnd = new HashMap<>();

    // Ключ для модификатора step height
    private final NamespacedKey keyStepHeight;

    // +1.4 к базовому step height (0.6) → итого 2.0 → шаг на 2 блока вверх без прыжка
    // Значение берётся из конфига: flash.step-height-bonus (по умолчанию 1.5)

    public FlashPower(SoKrutoiPowersPlugin plugin) {
        super(plugin);
        keyStepHeight = new NamespacedKey(plugin, "flash_step_height");
        loadPlayers();
    }

    @Override public String getName() { return "Flash"; }

    @Override
    public String getDescription() {
        int level = plugin.getConfig().getInt("flash.speed-level", 5);
        return "Флеш — Shift: Speed " + level + " + шаг на 2 блока; ПКМ пустой рукой — рывок";
    }

    @Override
    public void register() {
        plugin.getServer().getPluginManager()
                .registerEvents(new FlashListener(this), plugin);
        plugin.getServer().getPluginManager()
                .registerEvents(this, plugin);

        long hungerInterval = plugin.getConfig().getLong("flash.hunger-interval-ticks", 300L);
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            for (UUID uuid : new HashSet<>(active)) {
                Player player = plugin.getServer().getPlayer(uuid);
                if (player == null || !player.isOnline()) continue;
                if (player.getGameMode() == GameMode.CREATIVE
                        || player.getGameMode() == GameMode.SPECTATOR) continue;
                int food = player.getFoodLevel();
                if (food > 0) player.setFoodLevel(food - 1);
            }
        }, hungerInterval, hungerInterval);
    }

    // ── Выдача ────────────────────────────────────────────────────────────

    @Override
    public void giveToPlayer(Player player) {
        if (!players.add(player.getUniqueId())) {
            player.sendMessage(Component.text("У тебя уже есть сила Флеша.", NamedTextColor.YELLOW));
            return;
        }
        savePlayers();
        player.sendMessage(Component.text("Ты получил силу ", NamedTextColor.GRAY)
                .append(Component.text("Флеш", NamedTextColor.YELLOW))
                .append(Component.text(
                        ". Shift — включить скорость. ПКМ пустой рукой — рывок.",
                        NamedTextColor.GRAY)));
    }

    @Override
    public boolean giveToOfflineUUID(UUID uuid) {
        if (!players.add(uuid)) return false;
        savePlayers();
        return true;
    }

    // ── Проверка ──────────────────────────────────────────────────────────

    @Override public boolean hasPlayer(Player player) { return players.contains(player.getUniqueId()); }
    @Override public Set<UUID> getAllPlayerUUIDs()     { return Collections.unmodifiableSet(players); }

    public boolean hasPower(UUID uuid) { return players.contains(uuid); }
    public boolean isActive(UUID uuid) { return active.contains(uuid); }

    // ── Отзыв ────────────────────────────────────────────────────────────

    @Override
    public void revoke(Player player) {
        UUID uuid = player.getUniqueId();
        if (!players.remove(uuid)) return;
        savePlayers();
        if (active.remove(uuid)) {
            player.removePotionEffect(PotionEffectType.SPEED);
            removeStepHeight(player);
            if (player.getGameMode() == GameMode.SURVIVAL || player.getGameMode() == GameMode.ADVENTURE) {
                player.setAllowFlight(false);
                player.setFlying(false);
            }
        }
        dashCooldownEnd.remove(uuid);
        player.sendMessage(Component.text("Твоя сила Флеша была изъята.", NamedTextColor.DARK_RED));
    }

    @Override
    public void revokeOffline(UUID uuid) {
        if (!players.remove(uuid)) return;
        active.remove(uuid);
        dashCooldownEnd.remove(uuid);
        savePlayers();
    }

    // ── Сброс ────────────────────────────────────────────────────────────

    @Override
    public void resetPlayer(Player player) {
        UUID uuid = player.getUniqueId();
        dashCooldownEnd.remove(uuid);
        if (active.remove(uuid)) {
            player.removePotionEffect(PotionEffectType.SPEED);
            removeStepHeight(player);
            if (player.getGameMode() == GameMode.SURVIVAL || player.getGameMode() == GameMode.ADVENTURE) {
                player.setAllowFlight(false);
                player.setFlying(false);
            }
            player.sendActionBar(Component.text("◎ Флеш сброшен", NamedTextColor.GRAY));
        }
    }

    // ── Переключение скорости (Shift) ─────────────────────────────────────

    public void toggle(Player player) {
        if (active.contains(player.getUniqueId())) {
            deactivateSpeed(player);
        } else {
            activateSpeed(player);
        }
    }

    private void activateSpeed(Player player) {
        active.add(player.getUniqueId());

        int amplifier = Math.max(0, plugin.getConfig().getInt("flash.speed-level", 5) - 1);
        player.addPotionEffect(new PotionEffect(
                PotionEffectType.SPEED, Integer.MAX_VALUE, amplifier, false, false, false));

        applyStepHeight(player);

        // Разрешаем «полёт» чтобы второй прыжок в воздухе генерировал
        // PlayerToggleFlightEvent — именно так работает двойной прыжок в Bukkit
        if (player.getGameMode() == GameMode.SURVIVAL || player.getGameMode() == GameMode.ADVENTURE) {
            player.setAllowFlight(true);
        }

        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_BREEZE_SHOOT, 0.6f, 1.8f);
        player.sendActionBar(Component.text("⚡ Флеш активирован!", NamedTextColor.YELLOW));
    }

    private void deactivateSpeed(Player player) {
        active.remove(player.getUniqueId());
        player.removePotionEffect(PotionEffectType.SPEED);
        removeStepHeight(player);

        // Убираем разрешение полёта вместе с деактивацией
        if (player.getGameMode() == GameMode.SURVIVAL || player.getGameMode() == GameMode.ADVENTURE) {
            player.setAllowFlight(false);
            player.setFlying(false);
        }

        player.sendActionBar(Component.text("◎ Флеш выключен", NamedTextColor.GRAY));
    }

    // ── Рывок ─────────────────────────────────────────────────────────────

    public void dash(Player player) {
        UUID uuid = player.getUniqueId();
        long now  = System.currentTimeMillis();

        long cdMs  = plugin.getConfig().getLong("flash.dash-cooldown-seconds", 3L) * 1000L;
        Long cdEnd = dashCooldownEnd.get(uuid);
        if (cdEnd != null && now < cdEnd) {
            long secLeft = (long) Math.ceil((cdEnd - now) / 1000.0);
            player.sendActionBar(Component.text("⚡ Рывок: " + secLeft + " сек.", NamedTextColor.GRAY));
            return;
        }

        dashCooldownEnd.put(uuid, now + cdMs);

        double speed = plugin.getConfig().getDouble("flash.dash-velocity", 1.8);

        // Берём направление взгляда и слегка прижимаем Y,
        // чтобы рывок шёл горизонтально, а не резко вниз/вверх
        Vector dir = player.getLocation().getDirection().clone();
        dir.setY(Math.max(-0.25, Math.min(0.25, dir.getY())));
        dir.normalize().multiply(speed);

        player.setVelocity(dir);

        player.getWorld().spawnParticle(
                Particle.SWEEP_ATTACK,
                player.getLocation().add(0, 1, 0),
                8, 0.4, 0.4, 0.4, 0.0);
        player.getWorld().spawnParticle(
                Particle.ELECTRIC_SPARK,
                player.getLocation().add(0, 1, 0),
                20, 0.5, 0.8, 0.5, 0.2);

        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_BREEZE_SHOOT, 1.0f, 1.5f);
        player.sendActionBar(Component.text("⚡ Рывок!", NamedTextColor.YELLOW));
    }

    // ── Step Height ───────────────────────────────────────────────────────

    private void applyStepHeight(Player player) {
        AttributeInstance inst = player.getAttribute(Attribute.STEP_HEIGHT);
        if (inst == null) return;
        inst.getModifiers().stream()
                .filter(m -> m.getKey().equals(keyStepHeight))
                .findFirst()
                .ifPresent(inst::removeModifier);
        double bonus = plugin.getConfig().getDouble("flash.step-height-bonus", 1.5);
        inst.addModifier(new AttributeModifier(
                keyStepHeight,
                bonus,
                AttributeModifier.Operation.ADD_NUMBER,
                EquipmentSlotGroup.ANY));
    }

    private void removeStepHeight(Player player) {
        AttributeInstance inst = player.getAttribute(Attribute.STEP_HEIGHT);
        if (inst == null) return;
        inst.getModifiers().stream()
                .filter(m -> m.getKey().equals(keyStepHeight))
                .findFirst()
                .ifPresent(inst::removeModifier);
    }

    // ── События ───────────────────────────────────────────────────────────

    @EventHandler
    public void onFallDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!active.contains(player.getUniqueId())) return;
        if (event.getCause() == EntityDamageEvent.DamageCause.FALL) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (active.remove(player.getUniqueId())) {
            removeStepHeight(player);
            if (player.getGameMode() == GameMode.SURVIVAL || player.getGameMode() == GameMode.ADVENTURE) {
                player.setAllowFlight(false);
                player.setFlying(false);
            }
        }
    }

    // ── Персистентность ───────────────────────────────────────────────────

    private void loadPlayers() {
        for (String s : plugin.getConfig().getStringList("flash-players")) {
            try { players.add(UUID.fromString(s)); } catch (Exception ignored) {}
        }
        plugin.getLogger().info("[Flash] Загружено игроков: " + players.size());
    }

    public void savePlayers() {
        plugin.getConfig().set("flash-players", players.stream().map(UUID::toString).toList());
        plugin.saveConfig();
    }
}