package org.sokrutoi.soKrutoiPowersPlugin.listeners;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.sokrutoi.soKrutoiPowersPlugin.powers.ZaWarudoPower;

public class ZaWarudoListener implements Listener {

    private final ZaWarudoPower power;

    public ZaWarudoListener(ZaWarudoPower power) {
        this.power = power;
    }

    /**
     * Каждое нажатие Shift передаётся в ZaWarudoPower.onShiftPress().
     * Там хранится время последнего нажатия и определяется двойной шифт.
     * Одиночное нажатие для других сил (Гигант, Невидимость и т.д.) — не мешает,
     * потому что все привязанные силы взаимоисключают друг друга.
     */
    @EventHandler
    public void onSneak(PlayerToggleSneakEvent event) {
        if (!event.isSneaking()) return;

        var player = event.getPlayer();
        if (!power.hasPower(player.getUniqueId())) return;

        power.onShiftPress(player);
    }
}