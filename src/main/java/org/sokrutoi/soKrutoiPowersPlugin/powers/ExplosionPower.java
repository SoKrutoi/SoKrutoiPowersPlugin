package org.sokrutoi.soKrutoiPowersPlugin.powers;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.sokrutoi.soKrutoiPowersPlugin.SoKrutoiPowersPlugin;
import org.sokrutoi.soKrutoiPowersPlugin.listeners.ExplosionListener;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class ExplosionPower extends SuperPower {

    private final Set<UUID> players = new HashSet<>();

    /** UUID → System.currentTimeMillis() когда игрок начал зажимать Shift */
    private final java.util.Map<UUID, Long> chargingStart = new java.util.HashMap<>();
    /** UUID → System.currentTimeMillis() когда последний взрыв был */
    private final java.util.Map<UUID, Long> cooldownEnd   = new java.util.HashMap<>();

    public ExplosionPower(SoKrutoiPowersPlugin plugin) {
        super(plugin);
        loadPlayers();
    }

    @Override
    public String getName() { return "Explosion"; }

    @Override
    public String getDescription() {
        long sec = ((SoKrutoiPowersPlugin) plugin).getConfig()
                .getLong("explosion-charge-seconds", 5);
        return "Взрыв — удерживай Shift " + sec + " сек. чтобы взорваться";
    }

    @Override
    public void register() {
        plugin.getServer().getPluginManager()
                .registerEvents(new ExplosionListener((SoKrutoiPowersPlugin) plugin, this), plugin);
    }

    @Override
    public void giveToPlayer(Player player) {
        UUID uuid = player.getUniqueId();
        if (players.contains(uuid)) {
            player.sendMessage(Component.text(
                    "У тебя уже есть сила Взрыва.", NamedTextColor.YELLOW));
            return;
        }
        players.add(uuid);
        savePlayers();

        long sec = ((SoKrutoiPowersPlugin) plugin).getConfig()
                .getLong("explosion-charge-seconds", 5);
        player.sendMessage(Component.text("Ты получил силу ", NamedTextColor.GRAY)
                .append(Component.text("Взрыва", NamedTextColor.RED))
                .append(Component.text(
                        ". Удерживай Shift " + sec + " сек. чтобы взорваться.",
                        NamedTextColor.GRAY)));
    }

    @Override
    public boolean hasPlayer(Player player) {
        return players.contains(player.getUniqueId());
    }

    @Override
    public void revoke(Player player) {
        UUID uuid = player.getUniqueId();
        if (!players.remove(uuid)) return;
        savePlayers();
        chargingStart.remove(uuid);
        cooldownEnd.remove(uuid);
        player.sendMessage(Component.text(
                "Твоя сила Взрыва была изъята.", NamedTextColor.DARK_RED));
    }

    public boolean hasPower(UUID uuid) {
        return players.contains(uuid);
    }

    // --- Зарядка ---

    /** Начать зарядку (Shift нажат) */
    public void startCharge(Player player) {
        UUID uuid = player.getUniqueId();
        Long cdEnd = cooldownEnd.get(uuid);
        if (cdEnd != null && System.currentTimeMillis() < cdEnd) {
            long secLeft = (long) Math.ceil((cdEnd - System.currentTimeMillis()) / 1000.0);
            player.sendActionBar(Component.text(
                    "⏳ Кулдаун: " + secLeft + " сек.", NamedTextColor.GRAY));
            return;
        }
        chargingStart.put(uuid, System.currentTimeMillis());
    }

    /** Отменить зарядку (Shift отпущен раньше времени) */
    public void cancelCharge(Player player) {
        UUID uuid = player.getUniqueId();
        if (chargingStart.remove(uuid) != null) {
            player.sendActionBar(Component.text(
                    "✗ Заряд сброшен", NamedTextColor.GRAY));
        }
    }

    /** Игрок удерживает Shift — вызывается каждый тик из таска в листенере */
    public void tickCharge(Player player) {
        UUID uuid = player.getUniqueId();
        Long start = chargingStart.get(uuid);
        if (start == null) return;

        // Игрок умер — сбрасываем зарядку без взрыва
        if (!player.isOnline() || player.isDead()) {
            chargingStart.remove(uuid);
            return;
        }

        // Кулдаун ещё идёт — показываем таймер и не заряжаем
        Long cdEnd = cooldownEnd.get(uuid);
        if (cdEnd != null && System.currentTimeMillis() < cdEnd) {
            long secLeft = (long) Math.ceil((cdEnd - System.currentTimeMillis()) / 1000.0);
            player.sendActionBar(Component.text(
                    "⏳ Кулдаун: " + secLeft + " сек.", NamedTextColor.GRAY));
            return;
        }

        long chargeMs  = ((SoKrutoiPowersPlugin) plugin).getConfig()
                .getLong("explosion-charge-seconds", 5) * 1000L;
        long elapsed   = System.currentTimeMillis() - start;
        long remaining = chargeMs - elapsed;

        if (remaining <= 0) {
            // Взрыв!
            chargingStart.remove(uuid);
            explode(player);
        } else {
            // Показываем таймер с миллисекундами
            double secLeft = remaining / 1000.0;
            player.sendActionBar(Component.text(
                    String.format("🔥 Взрыв через %.1f сек...", secLeft),
                    NamedTextColor.RED));
        }
    }

    public boolean isCharging(UUID uuid) {
        return chargingStart.containsKey(uuid);
    }

    private void explode(Player player) {
        SoKrutoiPowersPlugin main = (SoKrutoiPowersPlugin) plugin;
        boolean breakBlocks = main.getConfig()
                .getBoolean("explosion-break-blocks", false);
        float power = (float) main.getConfig()
                .getDouble("explosion-power", 6.0);

        long cooldownMs = main.getConfig().getLong("explosion-cooldown-seconds", 30) * 1000L;
        cooldownEnd.put(player.getUniqueId(), System.currentTimeMillis() + cooldownMs);

        player.sendActionBar(Component.text("💥 ВЗРЫВ!", NamedTextColor.DARK_RED));

        Location loc = player.getLocation();

        // Визуальный эффект взрыва (звук + частицы) без урона от блоков
        player.getWorld().createExplosion(
                loc,
                power,
                false,       // поджигать ли
                breakBlocks  // разрушать ли блоки
        );

        // Наносим урон и откидываем всех живых в радиусе вручную
        double radius    = power * 2.0;
        double maxDamage = power * 4.0; // максимальный урон в центре

        for (Entity entity : loc.getWorld().getNearbyEntities(loc, radius, radius, radius)) {
            if (!(entity instanceof LivingEntity living)) continue;
            if (entity.equals(player)) continue; // себя не бьём

            double dist = entity.getLocation().distance(loc);
            if (dist > radius) continue;

            double factor = 1.0 - (dist / radius);

            // Урон убывает с расстоянием
            double damage = maxDamage * factor;

            // Сбрасываем неуязвимость от предыдущего удара чтобы урон прошёл
            living.setNoDamageTicks(0);
            living.damage(damage, player);

            // Отброс от центра взрыва
            org.bukkit.util.Vector knockback = entity.getLocation()
                    .toVector()
                    .subtract(loc.toVector())
                    .normalize()
                    .multiply(factor * 2.5)
                    .setY(factor * 1.2);
            entity.setVelocity(knockback);
        }
    }

    // --- Персистентность ---

    private void loadPlayers() {
        List<String> list = plugin.getConfig().getStringList("explosion-players");
        for (String s : list) {
            try { players.add(UUID.fromString(s)); }
            catch (Exception ignored) {}
        }
        plugin.getLogger().info("[Explosion] Загружено игроков: " + players.size());
    }

    public void savePlayers() {
        plugin.getConfig().set("explosion-players",
                players.stream().map(UUID::toString).toList());
        plugin.saveConfig();
    }
}