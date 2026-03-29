package org.sokrutoi.soKrutoiPowersPlugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.sokrutoi.soKrutoiPowersPlugin.commands.*;
import org.sokrutoi.soKrutoiPowersPlugin.powers.*;

import java.util.*;

public class SoKrutoiPowersPlugin extends JavaPlugin {

    private final Map<UUID, Long>   pendingDeaths  = new HashMap<>();
    private final Map<UUID, UUID>   writerMap      = new HashMap<>();
    private final Map<UUID, String> targetNames    = new HashMap<>();
    private final Set<UUID>         deathNoteKills = new HashSet<>();
    private final Set<UUID>         deathWarned    = new HashSet<>();
    private final Map<UUID, Long>   lastHeartbeat  = new HashMap<>();

    private PowerManager  powerManager;
    private NamespacedKey deathNoteKey;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        deathNoteKey = new NamespacedKey(this, "death_note");
        loadPendingDeaths();

        powerManager = new PowerManager();
        powerManager.register(new DeathNotePower(this));
        powerManager.register(new InvisibilityPower(this));
        powerManager.register(new GiantPower(this));
        powerManager.register(new ShrinkPower(this));
        powerManager.register(new ExplosionPower(this));
        powerManager.register(new ThiefPower(this));
        powerManager.register(new ZaWarudoPower(this));
        powerManager.register(new FlashPower(this));   // ← новая сила

        GivePowerCommand givePowerCmd = new GivePowerCommand(this);
        getCommand("givepower").setExecutor(givePowerCmd);
        getCommand("givepower").setTabCompleter(givePowerCmd);

        ClearPowersCommand clearCmd = new ClearPowersCommand(this);
        getCommand("clearpowers").setExecutor(clearCmd);
        getCommand("clearpowers").setTabCompleter(clearCmd);

        ResetPowersCommand resetCmd = new ResetPowersCommand(this);
        getCommand("resetpowers").setExecutor(resetCmd);
        getCommand("resetpowers").setTabCompleter(resetCmd);

        WhoHasPowersCommand whoCmd = new WhoHasPowersCommand(this);
        getCommand("whohaspowers").setExecutor(whoCmd);

        SetConfigCommand setConfigCmd = new SetConfigCommand(this);
        getCommand("setconfig").setExecutor(setConfigCmd);
        getCommand("setconfig").setTabCompleter(setConfigCmd);

        RandomPowersCommand randomPowersCmd = new RandomPowersCommand(this);
        getCommand("randompowers").setExecutor(randomPowersCmd);
        getCommand("randompowers").setTabCompleter(randomPowersCmd);

        getServer().getScheduler().runTaskTimer(this, this::checkDeaths, 20L, 4L);
        getLogger().info("SoKrutoiPowersPlugin включён. Сил: " + powerManager.getAll().size());
    }

    @Override
    public void onDisable() {
        savePendingDeaths();
        getLogger().info("SoKrutoiPowersPlugin выключен.");
    }

    public void scheduleDeath(UUID targetUUID, String playerName, UUID writerUUID) {
        if (pendingDeaths.containsKey(targetUUID)) return;
        long delayMs = getConfig().getLong("death-delay-seconds", 40L) * 1000L;
        pendingDeaths.put(targetUUID, System.currentTimeMillis() + delayMs);
        writerMap.put(targetUUID, writerUUID);
        targetNames.put(targetUUID, playerName);
        savePendingDeaths();
        getLogger().info("[DeathNote] Смерть запланирована: " + playerName + " (" + (delayMs / 1000) + " сек)");
    }

    private void checkDeaths() {
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, Long> entry : new HashMap<>(pendingDeaths).entrySet()) {
            UUID   targetUUID = entry.getKey();
            long   deathTime  = entry.getValue();
            String name       = targetNames.getOrDefault(targetUUID, "???");

            UUID writerUUID = writerMap.get(targetUUID);
            if (writerUUID != null) {
                Player writer = Bukkit.getPlayer(writerUUID);
                if (writer != null && writer.isOnline()) {
                    long remaining = deathTime - now;
                    long sec = remaining > 0 ? (long) Math.ceil(remaining / 1000.0) : 0;
                    writer.sendActionBar(Component.text(
                            "☠ " + name + " умрёт через " + sec + " сек...", NamedTextColor.RED));
                }
            }

            if (now >= deathTime) {
                Player target = Bukkit.getPlayer(targetUUID);
                if (target != null && target.isOnline()) {
                    pendingDeaths.remove(targetUUID);
                    deathWarned.remove(targetUUID);
                    savePendingDeaths();
                    killPlayer(target);
                }
            } else {
                long remaining = deathTime - now;
                long warnMs    = getConfig().getLong("death-warn-seconds", 10L) * 1000L;
                if (remaining <= warnMs) {
                    Player target = Bukkit.getPlayer(targetUUID);
                    if (target != null && target.isOnline()) {
                        if (!deathWarned.contains(targetUUID)) {
                            deathWarned.add(targetUUID);
                            target.addPotionEffect(new PotionEffect(
                                    PotionEffectType.BLINDNESS, (int) (warnMs), 0, false, false));
                        }
                        float progress   = 1.0f - (float) remaining / warnMs;
                        long  intervalMs = (long) (1000 - progress * 800);
                        long  lastBeat   = lastHeartbeat.getOrDefault(targetUUID, 0L);
                        if (now - lastBeat >= intervalMs) {
                            lastHeartbeat.put(targetUUID, now);
                            target.playSound(target.getLocation(),
                                    Sound.ENTITY_WARDEN_HEARTBEAT, 1.0f + (progress * 2), 1.0f);
                        }
                    }
                }
            }
        }
    }

    public void killPlayer(Player player) {
        UUID   uuid = player.getUniqueId();
        String name = player.getName();

        deathNoteKills.add(uuid);
        deathWarned.remove(uuid);
        lastHeartbeat.remove(uuid);
        player.removePotionEffect(PotionEffectType.BLINDNESS);
        player.setHealth(0.0);

        UUID writerUUID = writerMap.remove(uuid);
        targetNames.remove(uuid);

        if (writerUUID != null) {
            Player writer = Bukkit.getPlayer(writerUUID);
            if (writer != null && writer.isOnline()) {
                for (int i = 0; i <= 3; i++) {
                    Bukkit.getScheduler().runTaskLater(this, () ->
                            writer.sendActionBar(Component.text(
                                    "✓ " + name + " мёртв", NamedTextColor.DARK_RED)), i * 20L);
                }
            }
        }
    }

    public Map<UUID, Long>   getPendingDeaths()  { return pendingDeaths; }
    public Map<UUID, UUID>   getWriterMap()      { return writerMap; }
    public Map<UUID, String> getTargetNames()    { return targetNames; }
    public Set<UUID>         getDeathNoteKills() { return deathNoteKills; }
    public PowerManager      getPowerManager()   { return powerManager; }
    public NamespacedKey     getDeathNoteKey()   { return deathNoteKey; }

    private void loadPendingDeaths() {
        var section = getConfig().getConfigurationSection("pending-deaths");
        if (section == null) return;
        for (String key : section.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                pendingDeaths.put(uuid, section.getLong(key + ".time"));
                targetNames.put(uuid, section.getString(key + ".name", "???"));
                String w = section.getString(key + ".writer");
                if (w != null) writerMap.put(uuid, UUID.fromString(w));
            } catch (Exception ignored) {}
        }
        getLogger().info("Загружено ожидающих смертей: " + pendingDeaths.size());
    }

    private void savePendingDeaths() {
        getConfig().set("pending-deaths", null);
        pendingDeaths.forEach((uuid, time) -> {
            String path = "pending-deaths." + uuid;
            getConfig().set(path + ".time", time);
            getConfig().set(path + ".name", targetNames.getOrDefault(uuid, "???"));
            UUID w = writerMap.get(uuid);
            if (w != null) getConfig().set(path + ".writer", w.toString());
        });
        saveConfig();
    }
}