package org.sokrutoi.soKrutoiPowersPlugin.listeners;

import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.sokrutoi.soKrutoiPowersPlugin.SoKrutoiPowersPlugin;
import org.sokrutoi.soKrutoiPowersPlugin.powers.ThiefPower;

public class ThiefListener implements Listener {

    private final SoKrutoiPowersPlugin plugin;
    private final ThiefPower           power;

    public ThiefListener(SoKrutoiPowersPlugin plugin, ThiefPower power) {
        this.plugin = plugin;
        this.power  = power;
    }

    @EventHandler
    public void onRightClickPlayer(PlayerInteractAtEntityEvent event) {
        // Игнорируем offhand-дубль события
        if (event.getHand() != EquipmentSlot.HAND) return;

        // Только ПКМ по живому игроку
        if (event.getRightClicked().getType() != EntityType.PLAYER) return;

        Player thief  = event.getPlayer();
        Player victim = (Player) event.getRightClicked();

        // Сам себя не трогаем
        if (thief.equals(victim)) return;

        // Есть ли у вора эта способность?
        if (!power.hasPower(thief.getUniqueId())) return;

        // Рука должна быть пустой — иначе предметы работают как обычно
        if (thief.getInventory().getItemInMainHand().getType() != Material.AIR) return;

        // Отменяем стандартное взаимодействие
        event.setCancelled(true);

        power.trySteal(thief, victim);
    }
}