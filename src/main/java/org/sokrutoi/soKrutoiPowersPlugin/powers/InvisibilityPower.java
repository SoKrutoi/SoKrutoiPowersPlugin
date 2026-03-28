package org.sokrutoi.soKrutoiPowersPlugin.powers;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.sokrutoi.soKrutoiPowersPlugin.SoKrutoiPowersPlugin;
import org.sokrutoi.soKrutoiPowersPlugin.listeners.InvisibilityListener;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class InvisibilityPower extends SuperPower implements Listener {

    private final Set<UUID> players   = new HashSet<>();
    private final Set<UUID> invisible = new HashSet<>();

    public InvisibilityPower(SoKrutoiPowersPlugin plugin) {
        super(plugin);
        loadPlayers();
    }

    @Override
    public String getName() { return "Invisibility"; }

    @Override
    public String getDescription() {
        return "Невидимость — зажми Shift чтобы включить/выключить";
    }

    @Override
    public void register() {
        plugin.getServer().getPluginManager()
                .registerEvents(new InvisibilityListener((SoKrutoiPowersPlugin) plugin, this), plugin);
        // Регистрируем сам класс для обработки смерти
        plugin.getServer().getPluginManager()
                .registerEvents(this, plugin);
    }

    @Override
    public void giveToPlayer(Player player) {
        UUID uuid = player.getUniqueId();
        if (players.contains(uuid)) {
            player.sendMessage(Component.text(
                    "У тебя уже есть сила невидимости.", NamedTextColor.YELLOW));
            return;
        }
        players.add(uuid);
        savePlayers();

        player.sendMessage(Component.text("Ты получил силу ", NamedTextColor.GRAY)
                .append(Component.text("Невидимости", NamedTextColor.AQUA))
                .append(Component.text(". Нажми Shift чтобы активировать.", NamedTextColor.GRAY)));
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

        if (invisible.remove(uuid)) {
            player.removePotionEffect(PotionEffectType.INVISIBILITY);
        }

        player.sendMessage(Component.text(
                "Твоя сила невидимости была изъята.", NamedTextColor.DARK_RED));
    }

    /**
     * При смерти сбрасываем активную невидимость.
     * Эффект всё равно снимается при смерти ваниллой,
     * но нам нужно убрать UUID из invisible чтобы счётчик нажатий не сбился.
     */
    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        UUID uuid = event.getEntity().getUniqueId();
        // Убираем из invisible — после респауна первый Shift снова включит инвиз
        invisible.remove(uuid);
    }

    public void toggle(Player player) {
        UUID uuid = player.getUniqueId();

        if (invisible.contains(uuid)) {
            invisible.remove(uuid);
            player.removePotionEffect(PotionEffectType.INVISIBILITY);
            player.sendActionBar(Component.text(
                    "◎ Невидимость выключена", NamedTextColor.GRAY));
        } else {
            invisible.add(uuid);
            player.addPotionEffect(new PotionEffect(
                    PotionEffectType.INVISIBILITY,
                    Integer.MAX_VALUE,
                    0,
                    false,
                    false
            ));
            player.sendActionBar(Component.text(
                    "◉ Невидимость включена", NamedTextColor.AQUA));
        }
    }

    public boolean hasPower(UUID uuid) {
        return players.contains(uuid);
    }

    private void loadPlayers() {
        List<String> list = plugin.getConfig().getStringList("invisibility-players");
        for (String s : list) {
            try { players.add(UUID.fromString(s)); }
            catch (Exception ignored) {}
        }
        plugin.getLogger().info("[Invisibility] Загружено игроков: " + players.size());
    }

    public void savePlayers() {
        plugin.getConfig().set("invisibility-players",
                players.stream().map(UUID::toString).toList());
        plugin.saveConfig();
    }
}