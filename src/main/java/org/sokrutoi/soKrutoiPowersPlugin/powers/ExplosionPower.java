package org.sokrutoi.soKrutoiPowersPlugin.powers;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.sokrutoi.soKrutoiPowersPlugin.SoKrutoiPowersPlugin;
import org.sokrutoi.soKrutoiPowersPlugin.listeners.ExplosionListener;

import java.util.*;

public class ExplosionPower extends SuperPower {

    private final Set<UUID>         players       = new HashSet<>();
    private final Map<UUID, Long>   chargingStart = new HashMap<>();
    private final Map<UUID, Long>   cooldownEnd   = new HashMap<>();

    // Resistance IV (amplifier=3) → 80% снижения урона.
    // При мощности взрыва 4.5 урон в центре ~15-20 ед., с 80% снижением ≈ 3-4 ед.
    // Игрок гарантированно переживает 1-2 своих взрыва.
    private static final int RESISTANCE_AMPLIFIER = 3; // Resistance IV

    public ExplosionPower(SoKrutoiPowersPlugin plugin) {
        super(plugin);
        loadPlayers();
    }

    @Override public String getName() { return "Explosion"; }

    @Override
    public String getDescription() {
        long sec = plugin.getConfig().getLong("explosion-charge-seconds", 5L);
        return "Взрыв — удерживай Shift " + sec + " сек. чтобы взорваться";
    }

    @Override
    public void register() {
        plugin.getServer().getPluginManager()
                .registerEvents(new ExplosionListener((SoKrutoiPowersPlugin) plugin, this), plugin);
    }

    // --- Выдача ---

    @Override
    public void giveToPlayer(Player player) {
        if (!players.add(player.getUniqueId())) {
            player.sendMessage(Component.text("У тебя уже есть сила Взрыва.", NamedTextColor.YELLOW));
            return;
        }
        savePlayers();
        long sec = plugin.getConfig().getLong("explosion-charge-seconds", 5L);
        player.sendMessage(Component.text("Ты получил силу ", NamedTextColor.GRAY)
                .append(Component.text("Взрыва", NamedTextColor.RED))
                .append(Component.text(". Удерживай Shift " + sec + " сек. чтобы взорваться.", NamedTextColor.GRAY)));
    }

    @Override
    public boolean giveToOfflineUUID(UUID uuid) {
        if (!players.add(uuid)) return false;
        savePlayers();
        return true;
    }

    // --- Проверка ---

    @Override
    public boolean hasPlayer(Player player) { return players.contains(player.getUniqueId()); }

    @Override
    public Set<UUID> getAllPlayerUUIDs() { return Collections.unmodifiableSet(players); }

    // --- Отзыв ---

    @Override
    public void revoke(Player player) {
        UUID uuid = player.getUniqueId();
        if (!players.remove(uuid)) return;
        savePlayers();
        chargingStart.remove(uuid);
        cooldownEnd.remove(uuid);
        removeResistance(player);
        player.sendMessage(Component.text("Твоя сила Взрыва была изъята.", NamedTextColor.DARK_RED));
    }

    @Override
    public void revokeOffline(UUID uuid) {
        if (!players.remove(uuid)) return;
        chargingStart.remove(uuid);
        cooldownEnd.remove(uuid);
        savePlayers();
    }

    // --- Сброс ---

    @Override
    public void resetPlayer(Player player) {
        UUID uuid = player.getUniqueId();
        chargingStart.remove(uuid);
        cooldownEnd.remove(uuid);
        removeResistance(player);
        player.sendActionBar(Component.text("◎ Кулдаун взрыва сброшен", NamedTextColor.GRAY));
    }

    // --- Логика зарядки ---

    public boolean hasPower(UUID uuid) { return players.contains(uuid); }

    public void startCharge(Player player) {
        UUID uuid = player.getUniqueId();
        Long cdEnd = cooldownEnd.get(uuid);
        if (cdEnd != null && System.currentTimeMillis() < cdEnd) {
            long secLeft = (long) Math.ceil((cdEnd - System.currentTimeMillis()) / 1000.0);
            player.sendActionBar(Component.text("⏳ Кулдаун: " + secLeft + " сек.", NamedTextColor.GRAY));
            return;
        }
        chargingStart.put(uuid, System.currentTimeMillis());

        // Применяем Resistance IV на всё время зарядки + небольшой буфер (3 сек),
        // чтобы защита действовала в момент самого взрыва.
        long chargeSeconds = plugin.getConfig().getLong("explosion-charge-seconds", 5L);
        int  durationTicks = (int) ((chargeSeconds + 3) * 20);
        player.addPotionEffect(new PotionEffect(
                PotionEffectType.RESISTANCE,
                durationTicks,
                RESISTANCE_AMPLIFIER,
                false,   // ambient
                false,   // particles
                false    // icon — не показываем иконку эффекта
        ));
    }

    public void cancelCharge(Player player) {
        if (chargingStart.remove(player.getUniqueId()) != null) {
            removeResistance(player);
            player.sendActionBar(Component.text("✗ Заряд сброшен", NamedTextColor.GRAY));
        }
    }

    public void tickCharge(Player player) {
        UUID uuid = player.getUniqueId();
        Long start = chargingStart.get(uuid);
        if (start == null) return;

        if (!player.isOnline() || player.isDead()) {
            chargingStart.remove(uuid);
            removeResistance(player);
            return;
        }

        Long cdEnd = cooldownEnd.get(uuid);
        if (cdEnd != null && System.currentTimeMillis() < cdEnd) {
            long secLeft = (long) Math.ceil((cdEnd - System.currentTimeMillis()) / 1000.0);
            player.sendActionBar(Component.text("⏳ Кулдаун: " + secLeft + " сек.", NamedTextColor.GRAY));
            return;
        }

        long chargeMs  = plugin.getConfig().getLong("explosion-charge-seconds", 5L) * 1000L;
        long elapsed   = System.currentTimeMillis() - start;
        long remaining = chargeMs - elapsed;

        if (remaining <= 0) {
            chargingStart.remove(uuid);
            explode(player);
        } else {
            player.sendActionBar(Component.text(
                    String.format("🔥 Взрыв через %.1f сек...", remaining / 1000.0),
                    NamedTextColor.RED));
        }
    }

    public boolean isCharging(UUID uuid) { return chargingStart.containsKey(uuid); }

    private void explode(Player player) {
        boolean breakBlocks = plugin.getConfig().getBoolean("explosion-break-blocks", false);
        float   power       = (float) plugin.getConfig().getDouble("explosion-power", 6.0);
        long    cdMs        = plugin.getConfig().getLong("explosion-cooldown-seconds", 60L) * 1000L;

        cooldownEnd.put(player.getUniqueId(), System.currentTimeMillis() + cdMs);
        player.sendActionBar(Component.text("💥 ВЗРЫВ!", NamedTextColor.DARK_RED));

        // Resistance уже на игроке от startCharge — она защитит его во время взрыва.
        // После взрыва снимаем её, чтобы не давать постоянный баф.
        Location loc = player.getLocation();
        player.getWorld().createExplosion(loc, power, false, breakBlocks);

        double radius    = power * 2.0;
        double maxDamage = power * 4.0;

        for (Entity entity : loc.getWorld().getNearbyEntities(loc, radius, radius, radius)) {
            if (!(entity instanceof LivingEntity living) || entity.equals(player)) continue;
            double dist = entity.getLocation().distance(loc);
            if (dist > radius) continue;
            double factor = 1.0 - (dist / radius);
            living.setNoDamageTicks(0);
            living.damage(maxDamage * factor, player);
            entity.setVelocity(entity.getLocation().toVector()
                    .subtract(loc.toVector()).normalize()
                    .multiply(factor * 2.5).setY(factor * 1.2));
        }

        // Снимаем защитный эффект после взрыва — он своё дело сделал
        removeResistance(player);
    }

    // --- Вспомогательный метод снятия Resistance ---

    private void removeResistance(Player player) {
        // Снимаем только если уровень совпадает с нашим — чтобы не трогать
        // резистанс от зелий или других плагинов.
        var current = player.getPotionEffect(PotionEffectType.RESISTANCE);
        if (current != null && current.getAmplifier() == RESISTANCE_AMPLIFIER) {
            player.removePotionEffect(PotionEffectType.RESISTANCE);
        }
    }

    // --- Персистентность ---

    private void loadPlayers() {
        for (String s : plugin.getConfig().getStringList("explosion-players")) {
            try { players.add(UUID.fromString(s)); } catch (Exception ignored) {}
        }
        plugin.getLogger().info("[Explosion] Загружено игроков: " + players.size());
    }

    public void savePlayers() {
        plugin.getConfig().set("explosion-players", players.stream().map(UUID::toString).toList());
        plugin.saveConfig();
    }
}