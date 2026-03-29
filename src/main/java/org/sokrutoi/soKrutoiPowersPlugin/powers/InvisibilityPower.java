package org.sokrutoi.soKrutoiPowersPlugin.powers;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.sokrutoi.soKrutoiPowersPlugin.SoKrutoiPowersPlugin;
import org.sokrutoi.soKrutoiPowersPlugin.listeners.InvisibilityListener;

import java.util.*;

public class InvisibilityPower extends SuperPower implements Listener {

    private final Set<UUID> players   = new HashSet<>();
    private final Set<UUID> invisible = new HashSet<>();

    // Голод снимается 1 единица еды каждые HUNGER_INTERVAL тиков.
    // 600 тиков = 30 секунд — очень медленно (у игрока 20 еды → 10 минут до 0).
    private static final long HUNGER_INTERVAL_TICKS = 600L;

    public InvisibilityPower(SoKrutoiPowersPlugin plugin) {
        super(plugin);
        loadPlayers();
    }

    @Override public String getName() { return "Invisibility"; }

    @Override
    public String getDescription() {
        return "Невидимость — зажми Shift чтобы включить/выключить";
    }

    @Override
    public void register() {
        plugin.getServer().getPluginManager()
                .registerEvents(new InvisibilityListener((SoKrutoiPowersPlugin) plugin, this), plugin);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);

        // Планировщик очень слабого голода для невидимых игроков.
        // Раз в 30 сек снимает 1 единицу еды (при 20 еды — 10 минут до нуля).
        // Creative/Spectator не затрагивается.
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            for (UUID uuid : new HashSet<>(invisible)) {
                Player player = plugin.getServer().getPlayer(uuid);
                if (player == null || !player.isOnline()) continue;
                // Не трогаем режимы без голода
                if (player.getGameMode() == GameMode.CREATIVE
                        || player.getGameMode() == GameMode.SPECTATOR) continue;
                int food = player.getFoodLevel();
                if (food > 0) {
                    player.setFoodLevel(food - 1);
                }
            }
        }, HUNGER_INTERVAL_TICKS, HUNGER_INTERVAL_TICKS);
    }

    // --- Выдача ---

    @Override
    public void giveToPlayer(Player player) {
        if (!players.add(player.getUniqueId())) {
            player.sendMessage(Component.text("У тебя уже есть сила невидимости.", NamedTextColor.YELLOW));
            return;
        }
        savePlayers();
        player.sendMessage(Component.text("Ты получил силу ", NamedTextColor.GRAY)
                .append(Component.text("Невидимости", NamedTextColor.AQUA))
                .append(Component.text(". Нажми Shift чтобы активировать.", NamedTextColor.GRAY)));
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
        if (invisible.remove(uuid)) player.removePotionEffect(PotionEffectType.INVISIBILITY);
        player.sendMessage(Component.text("Твоя сила невидимости была изъята.", NamedTextColor.DARK_RED));
    }

    @Override
    public void revokeOffline(UUID uuid) {
        if (!players.remove(uuid)) return;
        invisible.remove(uuid);
        savePlayers();
    }

    // --- Сброс ---

    @Override
    public void resetPlayer(Player player) {
        UUID uuid = player.getUniqueId();
        if (invisible.remove(uuid)) {
            player.removePotionEffect(PotionEffectType.INVISIBILITY);
            player.sendActionBar(Component.text("◎ Невидимость сброшена", NamedTextColor.GRAY));
        }
    }

    // --- Логика ---

    public void toggle(Player player) {
        UUID uuid = player.getUniqueId();
        if (invisible.contains(uuid)) {
            invisible.remove(uuid);
            player.removePotionEffect(PotionEffectType.INVISIBILITY);
            player.sendActionBar(Component.text("◎ Невидимость выключена", NamedTextColor.GRAY));
        } else {
            invisible.add(uuid);
            player.addPotionEffect(new PotionEffect(
                    PotionEffectType.INVISIBILITY, Integer.MAX_VALUE, 0, false, false));
            player.sendActionBar(Component.text("◉ Невидимость включена", NamedTextColor.AQUA));
        }
    }

    public boolean hasPower(UUID uuid) { return players.contains(uuid); }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        invisible.remove(event.getEntity().getUniqueId());
    }

    // --- Персистентность ---

    private void loadPlayers() {
        for (String s : plugin.getConfig().getStringList("invisibility-players")) {
            try { players.add(UUID.fromString(s)); } catch (Exception ignored) {}
        }
        plugin.getLogger().info("[Invisibility] Загружено игроков: " + players.size());
    }

    public void savePlayers() {
        plugin.getConfig().set("invisibility-players",
                players.stream().map(UUID::toString).toList());
        plugin.saveConfig();
    }
}