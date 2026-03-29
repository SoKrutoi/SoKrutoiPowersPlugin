package org.sokrutoi.soKrutoiPowersPlugin.listeners;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.ItemStack;
import org.sokrutoi.soKrutoiPowersPlugin.SoKrutoiPowersPlugin;
import org.sokrutoi.soKrutoiPowersPlugin.powers.GiantPower;

import java.util.Set;

public class GiantListener implements Listener {

    private final SoKrutoiPowersPlugin plugin;
    private final GiantPower           power;

    // Блоки, которые гигант не может сломать в 3×3×3 режиме
    // (неразрушаемые, технические, порталы)
    private static final Set<Material> UNBREAKABLE = Set.of(
            Material.BEDROCK,
            Material.END_PORTAL,
            Material.END_PORTAL_FRAME,
            Material.END_GATEWAY,
            Material.NETHER_PORTAL,
            Material.BARRIER,
            Material.LIGHT,
            Material.STRUCTURE_BLOCK,
            Material.STRUCTURE_VOID,
            Material.COMMAND_BLOCK,
            Material.CHAIN_COMMAND_BLOCK,
            Material.REPEATING_COMMAND_BLOCK,
            Material.JIGSAW
    );

    public GiantListener(SoKrutoiPowersPlugin plugin, GiantPower power) {
        this.plugin = plugin;
        this.power  = power;
    }

    // ── Смена размера по Shift ─────────────────────────────────────────────

    @EventHandler
    public void onSneak(PlayerToggleSneakEvent event) {
        if (!event.isSneaking()) return;

        var player = event.getPlayer();
        if (!power.hasPower(player.getUniqueId())) return;

        power.cycleSize(player);
    }

    // ── 3×3×3 ломание блоков на 2-м уровне ───────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();

        // Только уровень 2 (огромный)
        if (power.getSizeLevel(player.getUniqueId()) != 2) return;

        Block    center = event.getBlock();
        ItemStack tool  = player.getInventory().getItemInMainHand();

        int cx = center.getX();
        int cy = center.getY();
        int cz = center.getZ();

        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    // Центральный блок уже сломан самим событием
                    if (dx == 0 && dy == 0 && dz == 0) continue;

                    Block b = center.getWorld().getBlockAt(cx + dx, cy + dy, cz + dz);

                    // Пропускаем воздух и жидкости
                    if (b.isEmpty() || b.isLiquid()) continue;

                    // Пропускаем неразрушаемые и технические блоки
                    Material type = b.getType();
                    if (UNBREAKABLE.contains(type)) continue;

                    // Прочность -1 означает неразрушаемый (бедрок, командные блоки и т.д.)
                    if (type.getHardness() < 0) continue;

                    // Ломаем с учётом зачарований инструмента (шёлковое касание, удача)
                    b.breakNaturally(tool);
                }
            }
        }
    }
}