package org.sokrutoi.soKrutoiPowersPlugin.listeners;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.sokrutoi.soKrutoiPowersPlugin.SoKrutoiPowersPlugin;
import org.sokrutoi.soKrutoiPowersPlugin.powers.ShrinkPower;

public class ShrinkListener implements Listener {

    private final SoKrutoiPowersPlugin plugin;
    private final ShrinkPower           power;

    public ShrinkListener(SoKrutoiPowersPlugin plugin, ShrinkPower power) {
        this.plugin = plugin;
        this.power  = power;
    }

    @EventHandler
    public void onSneak(PlayerToggleSneakEvent event) {
        if (!event.isSneaking()) return;

        var player = event.getPlayer();
        if (!power.hasPower(player.getUniqueId())) return;

        power.cycleSize(player);
    }
}