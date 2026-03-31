package org.sokrutoi.soKrutoiPowersPlugin.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
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

    // ── Переключение размера (Shift) ──────────────────────────────────────

    @EventHandler
    public void onSneak(PlayerToggleSneakEvent event) {
        if (!event.isSneaking()) return;

        var player = event.getPlayer();
        if (!power.hasPower(player.getUniqueId())) return;

        power.cycleSize(player);
    }

    // ── Снижение получаемого урона ────────────────────────────────────────

    /**
     * Маленький игрок — труднее попасть, поэтому получает меньше урона.
     * Процент снижения берётся из конфига: shrink.level1.damage-reduction / level2.
     * Срабатывает для любого типа урона (мечи, луки, взрывы и т.д.).
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        int level = power.getSizeLevel(player.getUniqueId());
        if (level <= 0) return;   // обычный размер — без снижения

        double defaultReduction = level == 1 ? 0.25 : 0.50;
        double reduction = plugin.getConfig().getDouble(
                "shrink.level" + level + ".damage-reduction", defaultReduction);

        // Ограничиваем в допустимом диапазоне [0; 0.95]
        reduction = Math.max(0.0, Math.min(0.95, reduction));

        event.setDamage(event.getDamage() * (1.0 - reduction));
    }
}