package org.sokrutoi.soKrutoiPowersPlugin.powers;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.sokrutoi.soKrutoiPowersPlugin.SoKrutoiPowersPlugin;
import org.sokrutoi.soKrutoiPowersPlugin.listeners.AnchorListener;

import java.util.*;

public class AnchorPower extends SuperPower {

    private final Set<UUID>              players       = new HashSet<>();
    private final Map<UUID, AnchorState> activeAnchors = new HashMap<>();
    // armor stand UUID → owner UUID — нужно для быстрой защиты стойки
    private final Map<UUID, UUID>        standToPlayer = new HashMap<>();
    private final Map<UUID, Long>        lastShiftTime = new HashMap<>();
    private final Map<UUID, Long>        cooldownEnd   = new HashMap<>();

    public AnchorPower(SoKrutoiPowersPlugin plugin) {
        super(plugin);
        loadPlayers();
    }

    @Override public String getName() { return "Anchor"; }

    @Override
    public String getDescription() {
        long dur = plugin.getConfig().getLong("anchor.duration-seconds", 20L);
        long cd  = plugin.getConfig().getLong("anchor.cooldown-seconds", 60L);
        return "Якорь — двойной Shift: точка возврата + полное HP на " + dur + " сек. (кд " + cd + " сек.)";
    }

    @Override
    public void register() {
        plugin.getServer().getPluginManager()
                .registerEvents(new AnchorListener((SoKrutoiPowersPlugin) plugin, this), plugin);
    }

    // ── Выдача ─────────────────────────────────────────────────────────────

    @Override
    public void giveToPlayer(Player player) {
        if (!players.add(player.getUniqueId())) {
            player.sendMessage(Component.text("У тебя уже есть сила Якоря.", NamedTextColor.YELLOW));
            return;
        }
        savePlayers();
        player.sendMessage(Component.text("Ты получил силу ", NamedTextColor.GRAY)
                .append(Component.text("Якоря", NamedTextColor.DARK_AQUA))
                .append(Component.text(
                        ". Двойной Shift: установить якорь / вернуться к нему.",
                        NamedTextColor.GRAY)));
    }

    @Override
    public boolean giveToOfflineUUID(UUID uuid) {
        if (!players.add(uuid)) return false;
        savePlayers();
        return true;
    }

    @Override public boolean hasPlayer(Player player) { return players.contains(player.getUniqueId()); }
    @Override public Set<UUID> getAllPlayerUUIDs()     { return Collections.unmodifiableSet(players); }
    public  boolean hasPower(UUID uuid)               { return players.contains(uuid); }

    // ── Отзыв ─────────────────────────────────────────────────────────────

    @Override
    public void revoke(Player player) {
        UUID uuid = player.getUniqueId();
        if (!players.remove(uuid)) return;
        deactivateAnchor(uuid, false);   // убрать стойку, не телепортировать
        savePlayers();
        lastShiftTime.remove(uuid);
        player.sendMessage(Component.text("Твоя сила Якоря была изъята.", NamedTextColor.DARK_RED));
    }

    @Override
    public void revokeOffline(UUID uuid) {
        players.remove(uuid);
        lastShiftTime.remove(uuid);
        savePlayers();
    }

    @Override
    public void resetPlayer(Player player) {
        UUID uuid = player.getUniqueId();
        cooldownEnd.remove(uuid);
        lastShiftTime.remove(uuid);
        if (activeAnchors.containsKey(uuid)) {
            deactivateAnchor(uuid, true);
        }
        player.sendActionBar(Component.text("◎ Якорь сброшен", NamedTextColor.GRAY));
    }

    // ── Обработка двойного Shift из AnchorListener ────────────────────────

    public void onShiftPress(Player player) {
        UUID uuid     = player.getUniqueId();
        long now      = System.currentTimeMillis();
        long windowMs = plugin.getConfig().getLong("anchor.double-shift-window-ms", 400L);

        Long lastPress = lastShiftTime.get(uuid);
        lastShiftTime.put(uuid, now);

        if (lastPress == null || (now - lastPress) > windowMs) return;

        // Двойной шифт зафиксирован
        lastShiftTime.remove(uuid);

        if (activeAnchors.containsKey(uuid)) {
            // Якорь уже стоит — досрочный возврат
            deactivateAnchor(uuid, true);
        } else {
            // Проверяем кулдаун
            Long cdEnd = cooldownEnd.get(uuid);
            if (cdEnd != null && now < cdEnd) {
                long secLeft = (long) Math.ceil((cdEnd - now) / 1000.0);
                player.sendActionBar(Component.text(
                        "⏳ Якорь: " + secLeft + " сек.", NamedTextColor.GRAY));
                return;
            }
            activateAnchor(player);
        }
    }

    // ── Активация якоря ────────────────────────────────────────────────────

    private void activateAnchor(Player player) {
        UUID     uuid        = player.getUniqueId();
        long     durationSec = plugin.getConfig().getLong("anchor.duration-seconds", 20L);
        Location loc         = player.getLocation().clone();

        // Сохраняем текущее состояние
        double savedHp   = player.getHealth();
        int    savedFood = player.getFoodLevel();

        // Спавним стойку-якорь
        ArmorStand stand = player.getWorld().spawn(loc, ArmorStand.class, s -> {
            s.setInvulnerable(true);
            s.setGravity(false);
            s.setVisible(true);
            s.setSmall(false);
            s.setArms(false);
            s.setBasePlate(false);
            s.setCanPickupItems(false);
            s.setCustomNameVisible(true);
            s.customName(Component.text("⚓ " + player.getName(), NamedTextColor.DARK_AQUA));
            s.setPersistent(false);    // удалится при перезагрузке чанка, если мы не вернёмся
            s.setSilent(true);

            // Надеваем голову игрока
            ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta  = (SkullMeta) skull.getItemMeta();
            meta.setOwningPlayer(player);
            skull.setItemMeta(meta);
            s.getEquipment().setHelmet(skull);
        });

        standToPlayer.put(stand.getUniqueId(), uuid);

        // Таймер автовозврата
        int taskId = plugin.getServer().getScheduler()
                .runTaskLater(plugin, () -> {
                    if (activeAnchors.containsKey(uuid)) deactivateAnchor(uuid, true);
                }, durationSec * 20L).getTaskId();

        activeAnchors.put(uuid, new AnchorState(
                stand.getUniqueId(), loc, savedHp, savedFood, taskId));

        // Полное HP и голод
        AttributeInstance hpInst = player.getAttribute(Attribute.MAX_HEALTH);
        double maxHp = hpInst != null ? hpInst.getValue() : 20.0;
        player.setHealth(maxHp);
        player.setFoodLevel(20);
        player.setSaturation(20f);

        // Кратковременная слепота — вспышка «отсоединения» (3 сек)
        player.addPotionEffect(new PotionEffect(
                PotionEffectType.NAUSEA, 400, 1, false, false));

        player.sendActionBar(Component.text(
                "⚓ Якорь установлен! ",
                NamedTextColor.DARK_AQUA));
        player.playSound(loc, Sound.BLOCK_ANVIL_LAND, 0.6f, 1.6f);
        player.getWorld().spawnParticle(
                Particle.SOUL, loc.clone().add(0, 1, 0), 20, 0.4, 0.6, 0.4, 0.05);
    }

    // ── Деактивация якоря ─────────────────────────────────────────────────

    /**
     * @param teleport true  — телепортировать игрока к якорю и восстановить состояние
     *                 false — просто убрать якорь (при /clearpowers, /resetpowers)
     */
    public void deactivateAnchor(UUID uuid, boolean teleport) {
        AnchorState state = activeAnchors.remove(uuid);
        if (state == null) return;

        plugin.getServer().getScheduler().cancelTask(state.taskId);
        standToPlayer.remove(state.standUuid);

        // Убираем стойку из всех миров
        for (World world : Bukkit.getWorlds()) {
            Entity e = world.getEntity(state.standUuid);
            if (e != null) { e.remove(); break; }
        }

        // Ставим кулдаун
        long cdMs = plugin.getConfig().getLong("anchor.cooldown-seconds", 60L) * 1000L;
        cooldownEnd.put(uuid, System.currentTimeMillis() + cdMs);

        Player player = Bukkit.getPlayer(uuid);
        if (player == null || !player.isOnline()) return;

        player.removePotionEffect(PotionEffectType.NAUSEA);

        if (teleport) {
            player.teleport(state.anchorLocation);

            // Восстанавливаем HP (но не выше текущего максимума)
            AttributeInstance hpInst = player.getAttribute(Attribute.MAX_HEALTH);
            double maxHp = hpInst != null ? hpInst.getValue() : 20.0;
            player.setHealth(Math.min(state.savedHp, maxHp));
            player.setFoodLevel(state.savedFood);

            player.sendActionBar(Component.text(
                    "⚓ Возврат к якорю.", NamedTextColor.DARK_AQUA));
            player.playSound(state.anchorLocation, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 0.8f);
            player.getWorld().spawnParticle(
                    Particle.PORTAL, state.anchorLocation.clone().add(0, 1, 0),
                    30, 0.4, 0.6, 0.4, 0.1);
        }
    }

    /** Используется AnchorListener и ZaWarudoFreezeListener для защиты стойки. */
    public boolean isDummyStand(UUID standUuid) { return standToPlayer.containsKey(standUuid); }

    public boolean isActive(UUID playerUuid) { return activeAnchors.containsKey(playerUuid); }

    // ── Состояние активного якоря ─────────────────────────────────────────

    public static class AnchorState {
        public final UUID     standUuid;
        public final Location anchorLocation;
        public final double   savedHp;
        public final int      savedFood;
        public final int      taskId;

        public AnchorState(UUID standUuid, Location anchorLocation,
                           double savedHp, int savedFood, int taskId) {
            this.standUuid      = standUuid;
            this.anchorLocation = anchorLocation;
            this.savedHp        = savedHp;
            this.savedFood      = savedFood;
            this.taskId         = taskId;
        }
    }

    // ── Персистентность ───────────────────────────────────────────────────

    private void loadPlayers() {
        for (String s : plugin.getConfig().getStringList("anchor-players")) {
            try { players.add(UUID.fromString(s)); } catch (Exception ignored) {}
        }
        plugin.getLogger().info("[Anchor] Загружено игроков: " + players.size());
    }

    public void savePlayers() {
        plugin.getConfig().set("anchor-players", players.stream().map(UUID::toString).toList());
        plugin.saveConfig();
    }
}