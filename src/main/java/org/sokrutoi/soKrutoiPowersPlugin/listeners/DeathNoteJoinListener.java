package org.sokrutoi.soKrutoiPowersPlugin.listeners;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.sokrutoi.soKrutoiPowersPlugin.SoKrutoiPowersPlugin;

public class DeathNoteJoinListener implements Listener {

    private final SoKrutoiPowersPlugin plugin;

    public DeathNoteJoinListener(SoKrutoiPowersPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Long deathTime = plugin.getPendingDeaths().get(player.getUniqueId());

        if (deathTime != null && System.currentTimeMillis() >= deathTime) {
            plugin.getPendingDeaths().remove(player.getUniqueId());
            // Ждём 1 тик — игрок должен загрузиться
            Bukkit.getScheduler().runTaskLater(plugin, () -> plugin.killPlayer(player), 20L);
        }
    }
}