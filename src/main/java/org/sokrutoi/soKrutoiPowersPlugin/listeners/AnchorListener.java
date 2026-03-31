package org.sokrutoi.soKrutoiPowersPlugin.listeners;

import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.sokrutoi.soKrutoiPowersPlugin.SoKrutoiPowersPlugin;
import org.sokrutoi.soKrutoiPowersPlugin.powers.AnchorPower;

public class AnchorListener implements Listener {

    private final SoKrutoiPowersPlugin plugin;
    private final AnchorPower           power;

    public AnchorListener(SoKrutoiPowersPlugin plugin, AnchorPower power) {
        this.plugin = plugin;
        this.power  = power;
    }

    // ── Двойной Shift ─────────────────────────────────────────────────────

    @EventHandler
    public void onSneak(PlayerToggleSneakEvent event) {
        if (!event.isSneaking()) return;
        Player player = event.getPlayer();
        if (!power.hasPower(player.getUniqueId())) return;
        power.onShiftPress(player);
    }

    // ── Защита стойки-якоря ───────────────────────────────────────────────

    /** Делаем стойку абсолютно неуязвимой для любого урона. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onStandDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof ArmorStand stand)) return;
        if (!power.isDummyStand(stand.getUniqueId())) return;
        event.setCancelled(true);
    }

    /** Блокируем ПКМ на стойке (надеть броню / взять предмет). */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteractAtStand(PlayerInteractAtEntityEvent event) {
        if (!(event.getRightClicked() instanceof ArmorStand stand)) return;
        if (!power.isDummyStand(stand.getUniqueId())) return;
        event.setCancelled(true);
    }

    /** Блокируем взаимодействие с живой сущностью-стойкой (второй вариант события). */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof ArmorStand stand)) return;
        if (!power.isDummyStand(stand.getUniqueId())) return;
        event.setCancelled(true);
    }

    /** Блокируем ARM-слот взаимодействия (снятие/надевание брони через Shift+ПКМ). */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onManipulateStand(PlayerArmorStandManipulateEvent event) {
        if (!power.isDummyStand(event.getRightClicked().getUniqueId())) return;
        event.setCancelled(true);
    }
}