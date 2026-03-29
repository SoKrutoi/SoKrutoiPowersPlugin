package org.sokrutoi.soKrutoiPowersPlugin.listeners;

import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.sokrutoi.soKrutoiPowersPlugin.powers.ZaWarudoPower;

/**
 * Блокирует для замороженных игроков ВСЕ активные действия:
 *  - открытие любого инвентаря / клики внутри него
 *  - выбрасывание предметов (Q / Ctrl+Q)
 *  - атаки по любым сущностям
 *  - ломку и постановку блоков
 *  - ЛЮБОЕ взаимодействие с миром через ПКМ или ЛКМ:
 *      еда, луки, удочки, зелья, ПКМ по блокам, ЛКМ по воздуху и т.д.
 *  - взаимодействие с живыми сущностями (торговцы, животные, и т.п.)
 *
 * Блокирует для всех игроков взаимодействие с фейковой стойкой активатора.
 */
public class ZaWarudoFreezeListener implements Listener {

    private final ZaWarudoPower power;

    public ZaWarudoFreezeListener(ZaWarudoPower power) {
        this.power = power;
    }

    // ═══════════════════════════════════════════════════════
    //  Блокировки для замороженных игроков
    // ═══════════════════════════════════════════════════════

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

    /** Замороженный не может атаковать — ни игрока, ни моба. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onAttack(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) return;
        if (!power.isFrozen(attacker.getUniqueId())) return;
        event.setCancelled(true);
    }

    /** Замороженный не может ломать блоки. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!power.isFrozen(event.getPlayer().getUniqueId())) return;
        event.setCancelled(true);
    }

    /** Замороженный не может ставить блоки. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!power.isFrozen(event.getPlayer().getUniqueId())) return;
        event.setCancelled(true);
    }

    /**
     * Блокирует ВСЕ взаимодействия замороженного с предметами и блоками:
     *  - ПКМ по блоку (открыть сундук, дверь, нажать кнопку и т.д.)
     *  - ПКМ в воздухе (зарядить лук, съесть, бросить зелье и т.д.)
     *  - ЛКМ в воздухе (размах рукой, удочка и т.д.)
     *  - ЛКМ по блоку (удар по блоку без разрушения, нажатие и т.д.)
     *
     * BlockBreakEvent и BlockPlaceEvent уже отменены выше, но PlayerInteractEvent
     * нужен отдельно — иначе, например, лук зарядится или еда начнёт поедаться.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent event) {
        if (!power.isFrozen(event.getPlayer().getUniqueId())) return;
        event.setCancelled(true);
    }

    /**
     * Блокирует ПКМ замороженного по живым сущностям:
     * торговля с жителями, надевание сёдел, кормление животных и т.п.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (!power.isFrozen(event.getPlayer().getUniqueId())) return;
        event.setCancelled(true);
    }

    // ═══════════════════════════════════════════════════════
    //  Защита фейковой стойки от любых игроков
    // ═══════════════════════════════════════════════════════

    /**
     * Блокирует ПКМ любого игрока по фейковой стойке активатора.
     * Без этого игрок мог бы взаимодействовать со стойкой.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteractAtEntity(PlayerInteractAtEntityEvent event) {
        if (!(event.getRightClicked() instanceof ArmorStand stand)) return;
        if (!power.isDummyStand(stand.getUniqueId())) return;
        event.setCancelled(true);
    }

    /**
     * Блокирует ARM-взаимодействие (замена брони/предметов) со стойкой.
     * Это отдельное событие от PlayerInteractAtEntityEvent — нужны оба.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onArmorStandManipulate(PlayerArmorStandManipulateEvent event) {
        if (!power.isDummyStand(event.getRightClicked().getUniqueId())) return;
        event.setCancelled(true);
    }
}