package org.sokrutoi.soKrutoiPowersPlugin.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.sokrutoi.soKrutoiPowersPlugin.powers.ZaWarudoPower;

/**
 * Блокирует для замороженных игроков:
 *  - открытие любого инвентаря
 *  - клики внутри инвентаря (перемещение, взятие предметов)
 *  - выбрасывание предметов (Q / Ctrl+Q)
 */
public class ZaWarudoFreezeListener implements Listener {

    private final ZaWarudoPower power;

    public ZaWarudoFreezeListener(ZaWarudoPower power) {
        this.power = power;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        if (!power.isFrozen(player.getUniqueId())) return;
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!power.isFrozen(player.getUniqueId())) return;
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDropItem(PlayerDropItemEvent event) {
        if (!power.isFrozen(event.getPlayer().getUniqueId())) return;
        event.setCancelled(true);
    }
}