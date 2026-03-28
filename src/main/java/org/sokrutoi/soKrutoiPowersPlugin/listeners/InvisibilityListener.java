package org.sokrutoi.soKrutoiPowersPlugin.listeners;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.sokrutoi.soKrutoiPowersPlugin.SoKrutoiPowersPlugin;
import org.sokrutoi.soKrutoiPowersPlugin.powers.InvisibilityPower;

public class InvisibilityListener implements Listener {

    private final SoKrutoiPowersPlugin plugin;
    private final InvisibilityPower    power;

    public InvisibilityListener(SoKrutoiPowersPlugin plugin, InvisibilityPower power) {
        this.plugin = plugin;
        this.power  = power;
    }

    @EventHandler
    public void onSneak(PlayerToggleSneakEvent event) {
        // Реагируем только на нажатие Shift (не на отпускание)
        if (!event.isSneaking()) return;

        var player = event.getPlayer();

        // Есть ли у игрока эта суперсила?
        if (!power.hasPower(player.getUniqueId())) return;

        power.toggle(player);
    }
}