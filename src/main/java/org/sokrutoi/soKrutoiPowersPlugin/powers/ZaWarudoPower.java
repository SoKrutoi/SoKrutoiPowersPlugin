package org.sokrutoi.soKrutoiPowersPlugin.powers;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.*;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import org.sokrutoi.soKrutoiPowersPlugin.SoKrutoiPowersPlugin;
import org.sokrutoi.soKrutoiPowersPlugin.listeners.ZaWarudoListener;

import java.time.Duration;
import java.util.*;

public class ZaWarudoPower extends SuperPower {

    private final Set<UUID>             players        = new HashSet<>();
    private final Map<UUID, Long>       cooldownEnd    = new HashMap<>();
    private final Map<UUID, Long>       lastShiftPress = new HashMap<>();
    private final Map<UUID, FreezeZone> activeFreezes  = new HashMap<>();

    public ZaWarudoPower(SoKrutoiPowersPlugin plugin) {
        super(plugin);
        loadPlayers();
    }

    @Override public String getName() { return "ZaWarudo"; }

    @Override
    public String getDescription() {
        long dur = plugin.getConfig().getLong("zawarudo.duration-seconds", 9L);
        long cd  = plugin.getConfig().getLong("zawarudo.cooldown-seconds",  300L) / 60L;
        return "За Варудо — двойной Shift останавливает время на " + dur + " сек. (кд " + cd + " мин.)";
    }

    @Override
    public void register() {
        plugin.getServer().getPluginManager()
                .registerEvents(new ZaWarudoListener(this), plugin);
        plugin.getServer().getPluginManager()
                .registerEvents(new org.sokrutoi.soKrutoiPowersPlugin.listeners.ZaWarudoFreezeListener(this), plugin);
    }

    // ── Выдача ─────────────────────────────────────────────────────────────

    @Override
    public void giveToPlayer(Player player) {
        if (!players.add(player.getUniqueId())) {
            player.sendMessage(Component.text("У тебя уже есть сила За Варудо.", NamedTextColor.YELLOW));
            return;
        }
        savePlayers();
        player.sendMessage(Component.text("Ты получил силу ", NamedTextColor.GRAY)
                .append(Component.text("За Варудо", NamedTextColor.LIGHT_PURPLE))
                .append(Component.text(". Двойной Shift — остановить время в радиусе "
                                + (int) plugin.getConfig().getDouble("zawarudo.radius", 25.0) + " блоков.",
                        NamedTextColor.GRAY)));
    }

    @Override
    public boolean giveToOfflineUUID(UUID uuid) {
        if (!players.add(uuid)) return false;
        savePlayers();
        return true;
    }

    // ── Проверка ───────────────────────────────────────────────────────────

    @Override public boolean hasPlayer(Player player) { return players.contains(player.getUniqueId()); }
    @Override public Set<UUID> getAllPlayerUUIDs()     { return Collections.unmodifiableSet(players); }
    public  boolean hasPower(UUID uuid)               { return players.contains(uuid); }

    /** Возвращает true если игрок прямо сейчас заморожен чьей-либо остановкой времени. */
    public boolean isFrozen(UUID uuid) {
        for (FreezeZone zone : activeFreezes.values()) {
            if (zone.frozenPlayers.containsKey(uuid)) return true;
        }
        return false;
    }

    /** Возвращает true если стойка является фантомом активатора в активной зоне. */
    public boolean isDummyStand(ArmorStand stand) {
        for (FreezeZone zone : activeFreezes.values()) {
            if (stand.equals(zone.dummyStand)) return true;
        }
        return false;
    }

    /** Возвращает true если переданный UUID — это фейковая стойка одной из активных зон. */
    public boolean isDummyStand(UUID entityUUID) {
        for (FreezeZone zone : activeFreezes.values()) {
            if (zone.dummyStand != null && zone.dummyStand.getUniqueId().equals(entityUUID)) return true;
        }
        return false;
    }

    // ── Отзыв ─────────────────────────────────────────────────────────────

    @Override
    public void revoke(Player player) {
        UUID uuid = player.getUniqueId();
        if (!players.remove(uuid)) return;
        savePlayers();
        deactivate(uuid);
        lastShiftPress.remove(uuid);
        player.sendMessage(Component.text("Твоя сила За Варудо была изъята.", NamedTextColor.DARK_RED));
    }

    @Override
    public void revokeOffline(UUID uuid) {
        if (!players.remove(uuid)) return;
        lastShiftPress.remove(uuid);
        savePlayers();
    }

    // ── Сброс ─────────────────────────────────────────────────────────────

    @Override
    public void resetPlayer(Player player) {
        UUID uuid = player.getUniqueId();
        cooldownEnd.remove(uuid);
        lastShiftPress.remove(uuid);
        deactivate(uuid);
        player.sendActionBar(Component.text("◎ За Варудо сброшен", NamedTextColor.GRAY));
    }

    // ── Обработка нажатия Shift из ZaWarudoListener ───────────────────────

    public void onShiftPress(Player player) {
        UUID uuid = player.getUniqueId();

        if (activeFreezes.containsKey(uuid)) return;

        long now      = System.currentTimeMillis();
        long windowMs = plugin.getConfig().getLong("zawarudo.double-shift-window-ms", 400L);

        Long lastPress = lastShiftPress.get(uuid);
        lastShiftPress.put(uuid, now);

        if (lastPress == null || (now - lastPress) > windowMs) return;

        lastShiftPress.remove(uuid);

        Long cdEnd = cooldownEnd.get(uuid);
        if (cdEnd != null && now < cdEnd) {
            long secLeft = (long) Math.ceil((cdEnd - now) / 1000.0);
            player.sendActionBar(Component.text(
                    "⏳ За Варудо: " + formatCd(secLeft), NamedTextColor.GRAY));
            return;
        }

        activate(player);
    }

    // ── Активация остановки времени ────────────────────────────────────────

    private void activate(Player player) {
        UUID     uuid        = player.getUniqueId();
        long     durMs       = plugin.getConfig().getLong("zawarudo.duration-seconds", 9L) * 1000L;
        long     cdMs        = plugin.getConfig().getLong("zawarudo.cooldown-seconds",  300L) * 1000L;
        double   radius      = plugin.getConfig().getDouble("zawarudo.radius", 25.0);
        boolean  wholeServer = plugin.getConfig().getBoolean("zawarudo.freeze-whole-server", false);
        // volume в playSound: 1.0 = ~16 блоков слышимости; масштабируем под радиус+20 блоков
        float    soundVolume = (float) ((radius + 20.0) / 16.0);
        Location center      = player.getLocation().clone();
        long     endsAt      = System.currentTimeMillis() + durMs;

        cooldownEnd.put(uuid, System.currentTimeMillis() + cdMs);

        FreezeZone zone = new FreezeZone(endsAt, center, radius, wholeServer);
        activeFreezes.put(uuid, zone);

        Set<UUID> immune = getAllPlayerUUIDs();

        // ── Начальная заморозка ────────────────────────────────────────────
        if (wholeServer) {
            // Игроки — ВСЕ на сервере, без ограничения по миру/радиусу
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getUniqueId().equals(uuid)) continue;
                if (immune.contains(p.getUniqueId())) continue;
                freezePlayer(p, zone);
            }
            // Сущности — во ВСЕХ мирах, без ограничения по радиусу
            // (дамми-стойка ещё не создана, проверяем null-safe)
            for (World w : Bukkit.getWorlds()) {
                for (Entity entity : w.getEntities()) {
                    if (entity instanceof Player) continue;
                    UUID eid = entity.getUniqueId();
                    if (eid.equals(uuid)) continue;
                    if (zone.dummyStand != null && zone.dummyStand.getUniqueId().equals(eid)) continue;
                    freezeNonPlayer(entity, zone);
                }
            }
        } else {
            // Режим радиуса — сущности в сфере вокруг активатора
            for (Entity entity : center.getWorld().getNearbyEntities(center, radius, radius, radius)) {
                UUID eid = entity.getUniqueId();
                if (eid.equals(uuid)) continue;
                if (zone.dummyStand != null && zone.dummyStand.getUniqueId().equals(eid)) continue;
                if (entity.getLocation().distanceSquared(center) > radius * radius) continue;
                if (entity instanceof Player p) {
                    if (immune.contains(eid)) continue;
                    freezePlayer(p, zone);
                } else {
                    freezeNonPlayer(entity, zone);
                }
            }
        }

        // ── Звуки активации ───────────────────────────────────────────────
        // В режиме всего сервера — играем звук напрямую каждому игроку,
        // т.к. world.playSound() ограничен дистанцией и не достигнет всех.
        if (wholeServer) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.playSound(p.getLocation(), Sound.BLOCK_BEACON_ACTIVATE,       1.0f, 0.3f);
                p.playSound(p.getLocation(), Sound.ENTITY_ELDER_GUARDIAN_CURSE, 1.0f, 0.5f);
            }
        } else {
            center.getWorld().playSound(center, Sound.BLOCK_BEACON_ACTIVATE,       soundVolume, 0.3f);
            center.getWorld().playSound(center, Sound.ENTITY_ELDER_GUARDIAN_CURSE, soundVolume, 0.5f);
        }

        // ── Невидимость активатора на всё время заморозки ─────────────────
        int durTicks = (int) (durMs / 50);
        player.addPotionEffect(new PotionEffect(
                PotionEffectType.INVISIBILITY, durTicks + 4, 0, false, false, false));

        // ── Стойка для брони на месте активатора ──────────────────────────
        // Копирует броню и предметы в руках, бессмертна, без гравитации, невидима сама по себе
        Location standLoc = player.getLocation().clone();
        ArmorStand stand = (ArmorStand) center.getWorld().spawnEntity(standLoc, org.bukkit.entity.EntityType.ARMOR_STAND);
        stand.setInvulnerable(true);
        stand.setGravity(false);
        stand.setVisible(true);
        stand.setArms(true);
        stand.setBasePlate(false);
        stand.customName(player.customName() != null
                ? player.customName()
                : Component.text(player.getName()));
        stand.setCustomNameVisible(false);
        // Стойка намеренно пустая — предметы не копируем, чтобы не дублировать экипировку игрока
        // Ориентация как у игрока
        stand.setRotation(standLoc.getYaw(), 0);
        zone.dummyStand = stand;

        // В режиме wholeServer дамми-стойка только что создана — замораживаем её
        // (она появилась уже после основного цикла выше)
        if (wholeServer) {
            // Стойка не должна быть заморожена — она фантом активатора, стоит на месте
            // и уже без гравитации. Ничего не делаем.
        }

        // Титл только для активатора
        player.showTitle(Title.title(
                Component.text("⏸ За Варудо!", NamedTextColor.GOLD),
                Component.text("Время остановлено на " + (durMs / 1000) + " сек.", NamedTextColor.YELLOW),
                Title.Times.times(
                        Duration.ofMillis(200),
                        Duration.ofSeconds(3),
                        Duration.ofMillis(500))));

        // ── Тик-задача ─────────────────────────────────────────────────────
        BukkitTask[] taskRef = {null};
        taskRef[0] = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            long now = System.currentTimeMillis();

            if (!activeFreezes.containsKey(uuid)) {
                taskRef[0].cancel();
                return;
            }

            if (now >= zone.endsAt) {
                deactivate(uuid);
                taskRef[0].cancel();
                return;
            }

            // ── Подхватываем новых игроков, вошедших в зону / подключившихся ──
            Iterable<? extends Player> candidates = zone.wholeServer
                    ? Bukkit.getOnlinePlayers()
                    : center.getWorld().getPlayers();

            for (Player nearby : candidates) {
                UUID pid = nearby.getUniqueId();
                if (pid.equals(uuid))                    continue;
                if (immune.contains(pid))                continue;
                if (zone.frozenPlayers.containsKey(pid)) continue;
                if (!zone.wholeServer) {
                    if (!nearby.getWorld().equals(center.getWorld())) continue;
                    if (nearby.getLocation().distanceSquared(center) > radius * radius) continue;
                }
                freezePlayer(nearby, zone);
            }

            // ── Подхватываем новые не-игровые сущности ────────────────────
            if (zone.wholeServer) {
                // Все миры, без ограничения по радиусу
                for (World w : Bukkit.getWorlds()) {
                    for (Entity entity : w.getEntities()) {
                        if (entity instanceof Player) continue;
                        UUID eid = entity.getUniqueId();
                        if (eid.equals(uuid))                     continue;
                        if (zone.frozenEntities.containsKey(eid)) continue;
                        if (isDummyStand(eid))                    continue;
                        freezeNonPlayer(entity, zone);
                    }
                }
            } else {
                // Радиус-режим: только сущности в сфере
                for (Entity entity : center.getWorld().getNearbyEntities(center, radius, radius, radius)) {
                    if (entity instanceof Player) continue;
                    UUID eid = entity.getUniqueId();
                    if (eid.equals(uuid))                     continue;
                    if (zone.frozenEntities.containsKey(eid)) continue;
                    if (isDummyStand(eid))                    continue;
                    if (entity.getLocation().distanceSquared(center) > radius * radius) continue;
                    freezeNonPlayer(entity, zone);
                }
            }

            // ── Полная заморозка: телепортируем игроков на место ───────────
            for (Map.Entry<UUID, Location> entry : new HashMap<>(zone.frozenPlayers).entrySet()) {
                Player p = Bukkit.getPlayer(entry.getKey());
                if (p == null || !p.isOnline()) continue;
                Location fix = entry.getValue().clone();
                p.teleport(fix);
                p.setVelocity(new Vector(0, 0, 0));
                // Сбрасываем накопленную высоту падения — иначе после разморозки
                // движок суммирует всё расстояние за время заморозки и даёт огромный урон
                p.setFallDistance(0f);
                // Обновляем слепоту каждый тик — иначе она слетает после респавна
                int remainingTicks = (int) ((zone.endsAt - now) / 50) + 2;
                p.addPotionEffect(new PotionEffect(
                        PotionEffectType.BLINDNESS, remainingTicks, 0, false, false, false));
                p.sendActionBar(Component.text("⏸ Время остановлено...", NamedTextColor.DARK_PURPLE));
            }

            // ── Обнуляем скорость у не-игровых сущностей каждый тик ────────
            // (движок может применять гравитацию и другие силы между тиками)
            for (UUID eid : zone.frozenEntities.keySet()) {
                Entity e = Bukkit.getEntity(eid);
                if (e != null && !e.isDead()) {
                    e.setVelocity(new Vector(0, 0, 0));
                }
            }

            // ── Частицы — только в режиме радиуса ─────────────────────────
            // В режиме wholeServer сфера не нужна — нет видимой "границы зоны".
            if (!zone.wholeServer) {
                spawnParticles(center, radius, zone); // particleTick++ внутри
            } else {
                zone.particleTick++; // всё равно считаем для звука часов
            }

            // ── Таймер в action bar для активатора ────────────────────────
            Player activator = Bukkit.getPlayer(uuid);
            if (activator != null && activator.isOnline()) {
                long remaining = zone.endsAt - now;
                activator.sendActionBar(Component.text(
                        String.format("⏸ За Варудо: %.1f сек.", remaining / 1000.0),
                        NamedTextColor.GOLD));
                // Тиканье часов раз в секунду, низкий тон
                if (zone.particleTick % 10 == 0) {
                    activator.playSound(activator.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.5f, 0.4f);
                }
            }
        }, 0L, 2L);
        zone.tickTaskId = taskRef[0].getTaskId();
    }

    // ── Заморозка игрока ───────────────────────────────────────────────────

    private void freezePlayer(Player target, FreezeZone zone) {
        UUID pid = target.getUniqueId();
        zone.frozenPlayers.put(pid, target.getLocation().clone());
        target.playSound(target.getLocation(), Sound.ENTITY_ELDER_GUARDIAN_CURSE, 0.8f, 0.7f);
    }

    // ── Заморозка не-игровой сущности с сохранением скорости ──────────────

    /**
     * Сохраняет текущий вектор скорости сущности в зоне, затем обнуляет его
     * и отключает гравитацию/ИИ. При разморозке вектор будет восстановлен,
     * так что стрела полетит туда же, куда летела, моб продолжит двигаться,
     * а падающий блок возобновит падение.
     */
    private void freezeNonPlayer(Entity entity, FreezeZone zone) {
        UUID eid = entity.getUniqueId();

        // Сохраняем скорость ДО обнуления — это ключевое исправление
        Vector savedVelocity = entity.getVelocity().clone();
        zone.frozenEntities.put(eid, savedVelocity);

        entity.setGravity(false);
        entity.setVelocity(new Vector(0, 0, 0));

        if (entity instanceof Mob mob) {
            mob.setAI(false);
        }
    }

    // ── Частицы в зоне ────────────────────────────────────────────────────

    private void spawnParticles(Location center, double radius, FreezeZone zone) {
        Random rng = new Random();
        // Максимальный размер пыли в Bukkit — 4.0. Используем его для оболочки и объёма.
        Particle.DustOptions blackHuge = new Particle.DustOptions(Color.fromRGB(10, 10, 10), 4.0f);
        Particle.DustOptions blackBig  = new Particle.DustOptions(Color.fromRGB(10, 10, 10), 3.0f);

        // ── Оболочка сферы — равномерные точки через золотое сечение ─────
        // Метод Фибоначчи даёт идеально равномерное покрытие без кластеров.
        // 120 точек * размер 4.0 практически непрерывно покрывают оболочку.
        int shellCount  = 120;
        double goldenAngle = Math.PI * (3.0 - Math.sqrt(5.0));
        for (int i = 0; i < shellCount; i++) {
            double y     = 1.0 - (2.0 * i) / (shellCount - 1); // от +1 до -1
            double r     = Math.sqrt(Math.max(0, 1.0 - y * y));
            double theta = goldenAngle * i;
            double px    = radius * r * Math.cos(theta);
            double pz    = radius * r * Math.sin(theta);
            double py    = radius * y;
            center.getWorld().spawnParticle(Particle.DUST, center.clone().add(px, py, pz),
                    1, 0, 0, 0, 0, blackHuge);
        }

        // ── Объёмный слой чуть внутри оболочки (r = 0.75–0.95 * radius) ─
        // Перекрывает дыры между точками оболочки при взгляде под углом.
        for (int i = 0; i < 80; i++) {
            double u     = 0.75 + rng.nextDouble() * 0.20; // 75–95% радиуса
            double rr    = radius * u;
            double theta = rng.nextDouble() * 2 * Math.PI;
            double phi   = Math.acos(2 * rng.nextDouble() - 1);
            double px    = rr * Math.sin(phi) * Math.cos(theta);
            double py    = rr * Math.cos(phi);
            double pz    = rr * Math.sin(phi) * Math.sin(theta);
            center.getWorld().spawnParticle(Particle.DUST, center.clone().add(px, py, pz),
                    1, 0, 0, 0, 0, blackBig);
        }

        // ── Снежинки у замороженных игроков (видны внутри) ────────────────
        for (UUID pid : zone.frozenPlayers.keySet()) {
            Player p = Bukkit.getPlayer(pid);
            if (p == null || !p.isOnline()) continue;
            center.getWorld().spawnParticle(Particle.SNOWFLAKE,
                    p.getLocation().add(0, 1, 0),
                    5, 0.3, 0.5, 0.3, 0.0);
        }

        zone.particleTick++;
    }


    // ── Деактивация зоны заморозки ─────────────────────────────────────────

    private void deactivate(UUID activatorUUID) {
        FreezeZone zone = activeFreezes.remove(activatorUUID);
        if (zone == null) return;

        if (zone.tickTaskId != -1) {
            plugin.getServer().getScheduler().cancelTask(zone.tickTaskId);
        }

        // ── Звук конца ────────────────────────────────────────────────────
        // В режиме wholeServer — рассылаем напрямую каждому игроку.
        if (zone.wholeServer) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.playSound(p.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 1.0f, 0.5f);
                p.playSound(p.getLocation(), Sound.ENTITY_WARDEN_SONIC_BOOM, 0.4f, 1.5f);
            }
        } else {
            float soundVolume = (float) ((zone.radius + 20.0) / 16.0);
            zone.center.getWorld().playSound(zone.center, Sound.BLOCK_BEACON_DEACTIVATE, soundVolume, 0.5f);
            zone.center.getWorld().playSound(zone.center, Sound.ENTITY_WARDEN_SONIC_BOOM, soundVolume * 0.4f, 1.5f);
        }

        // ── ИСПРАВЛЕНИЕ: размораживаем сущности с восстановлением скорости ─
        for (Map.Entry<UUID, Vector> entry : zone.frozenEntities.entrySet()) {
            Entity e = Bukkit.getEntity(entry.getKey());
            if (e == null || e.isDead()) continue;

            e.setGravity(true);
            if (e instanceof Mob mob) mob.setAI(true);

            // Восстанавливаем сохранённый вектор скорости — стрела полетит дальше,
            // падающий блок продолжит падать, моб возобновит движение
            Vector savedVelocity = entry.getValue();
            if (savedVelocity != null) {
                e.setVelocity(savedVelocity);
            }
        }

        // Размораживаем игроков: снимаем эффекты, без title
        for (UUID pid : zone.frozenPlayers.keySet()) {
            Player p = Bukkit.getPlayer(pid);
            if (p == null || !p.isOnline()) continue;
            p.removePotionEffect(PotionEffectType.BLINDNESS);
            // Финальный сброс счётчика падения — гарантия что урон не придёт
            p.setFallDistance(0f);
            p.sendActionBar(Component.text("▶ Время возобновилось", NamedTextColor.GRAY));
        }

        // Снимаем невидимость с активатора
        Player activator = Bukkit.getPlayer(activatorUUID);
        if (activator != null && activator.isOnline()) {
            activator.removePotionEffect(PotionEffectType.INVISIBILITY);
        }

        // Убираем стойку для брони — через remove() чтобы предметы выпали
        if (zone.dummyStand != null && !zone.dummyStand.isDead()) {
            zone.dummyStand.setInvulnerable(false);
            zone.dummyStand.remove();
        }
    }

    // ── Вспомогательные ───────────────────────────────────────────────────

    private String formatCd(long totalSec) {
        if (totalSec < 60) return totalSec + " сек.";
        long min = totalSec / 60;
        long sec = totalSec % 60;
        return min + " мин." + (sec > 0 ? " " + sec + " сек." : "");
    }

    // ── Персистентность ───────────────────────────────────────────────────

    private void loadPlayers() {
        for (String s : plugin.getConfig().getStringList("zawarudo-players")) {
            try { players.add(UUID.fromString(s)); } catch (Exception ignored) {}
        }
        plugin.getLogger().info("[ZaWarudo] Загружено игроков: " + players.size());
    }

    public void savePlayers() {
        plugin.getConfig().set("zawarudo-players", players.stream().map(UUID::toString).toList());
        plugin.saveConfig();
    }

    // ── Внутренний класс зоны заморозки ───────────────────────────────────

    private static class FreezeZone {
        final long     endsAt;
        final Location center;
        final double   radius;
        final boolean  wholeServer;
        final Map<UUID, Location> frozenPlayers  = new HashMap<>();

        /**
         * Ключ — UUID сущности, значение — её скорость на момент заморозки.
         * Map вместо Set позволяет хранить сохранённые векторы прямо здесь,
         * без отдельной коллекции.
         */
        final Map<UUID, Vector> frozenEntities = new HashMap<>();

        int        tickTaskId   = -1;
        int        particleTick = 0;
        // Стойка для брони — фантом активатора на время заморозки
        ArmorStand dummyStand   = null;

        FreezeZone(long endsAt, Location center, double radius, boolean wholeServer) {
            this.endsAt      = endsAt;
            this.center      = center;
            this.radius      = radius;
            this.wholeServer = wholeServer;
        }
    }
}