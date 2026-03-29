package org.sokrutoi.soKrutoiPowersPlugin.listeners;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.GameMode;
import org.sokrutoi.soKrutoiPowersPlugin.powers.FlashPower;

public class FlashListener implements Listener {

    private final FlashPower power;

    public FlashListener(FlashPower power) {
        this.power = power;
    }

    /** Shift — переключение режима Флеша. */
    @EventHandler
    public void onSneak(PlayerToggleSneakEvent event) {
        if (!event.isSneaking()) return;
        var player = event.getPlayer();
        if (!power.hasPower(player.getUniqueId())) return;
        power.toggle(player);
    }

    /**
     * Двойной прыжок — рывок.
     *
     * Принцип: когда игрок не в Creative/Spectator, мы разрешаем ему
     * «полёт» (setAllowFlight(true)) пока флеш активен. Первый прыжок —
     * обычный. Второй прыжок в воздухе вызывает PlayerToggleFlightEvent
     * (попытка включить полёт). Мы перехватываем это событие, отменяем
     * включение полёта и вместо этого делаем рывок.
     */
    @EventHandler
    public void onDoubleJump(PlayerToggleFlightEvent event) {
        var player = event.getPlayer();

        // Только выживание — в Creative двойной прыжок уже занят полётом
        if (player.getGameMode() == GameMode.CREATIVE
                || player.getGameMode() == GameMode.SPECTATOR) return;

        if (!power.hasPower(player.getUniqueId())) return;
        if (!power.isActive(player.getUniqueId())) return;

        // Отменяем включение полёта — вместо него делаем рывок
        event.setCancelled(true);
        player.setFlying(false);

        power.dash(player);
    }
}