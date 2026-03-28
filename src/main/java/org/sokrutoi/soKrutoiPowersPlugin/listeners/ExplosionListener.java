package org.sokrutoi.soKrutoiPowersPlugin.listeners;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.sokrutoi.soKrutoiPowersPlugin.SoKrutoiPowersPlugin;
import org.sokrutoi.soKrutoiPowersPlugin.powers.ExplosionPower;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ExplosionListener implements Listener {

    private final SoKrutoiPowersPlugin plugin;
    private final ExplosionPower       power;

    /** UUID → ID букккит-таска тика зарядки */
    private final Map<UUID, Integer> chargeTasks = new HashMap<>();

    public ExplosionListener(SoKrutoiPowersPlugin plugin, ExplosionPower power) {
        this.plugin = plugin;
        this.power  = power;
    }

    @EventHandler
    public void onSneak(PlayerToggleSneakEvent event) {
        Player player = event.getPlayer();
        UUID   uuid   = player.getUniqueId();

        if (!power.hasPower(uuid)) return;

        if (event.isSneaking()) {
            // Shift нажат — начинаем зарядку
            power.startCharge(player);

            // Запускаем тик-таск каждые 2 тика (≈100 мс) для плавного таймера
            int taskId = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                if (!player.isOnline()) {
                    cancelTask(uuid);
                    return;
                }
                // Если игрок уже не зажимает — таск сам остановится через cancelCharge
                power.tickCharge(player);

                // Если зарядка завершилась (взорвался) — убираем таск
                if (!power.isCharging(uuid)) {
                    cancelTask(uuid);
                }
            }, 0L, 2L).getTaskId();

            chargeTasks.put(uuid, taskId);

        } else {
            // Shift отпущен — сбрасываем зарядку
            cancelTask(uuid);
            power.cancelCharge(player);
        }
    }

    private void cancelTask(UUID uuid) {
        Integer taskId = chargeTasks.remove(uuid);
        if (taskId != null) {
            Bukkit.getScheduler().cancelTask(taskId);
        }
    }
}