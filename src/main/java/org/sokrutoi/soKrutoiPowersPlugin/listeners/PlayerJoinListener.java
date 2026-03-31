package org.sokrutoi.soKrutoiPowersPlugin.listeners;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.sokrutoi.soKrutoiPowersPlugin.SoKrutoiPowersPlugin;

public class PlayerJoinListener implements Listener {

    private final SoKrutoiPowersPlugin plugin;

    public PlayerJoinListener(SoKrutoiPowersPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // Показываем подсказку всем через 2 тика (чтобы чат успел загрузиться)
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            player.sendMessage(
                    Component.text("  ⚡ ", NamedTextColor.DARK_GRAY)
                            .append(Component.text("Помощь по плагину суперсил: ", NamedTextColor.DARK_GRAY))
                            .append(Component.text("/powers", NamedTextColor.GRAY)));
        }, 2L);

        // При первом заходе — выдаём Стрелу Судьбы
        if (!player.hasPlayedBefore()) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                ItemStack arrow = plugin.getLuckyArrow().createItem();
                player.getInventory().addItem(arrow);
                player.sendMessage(
                        Component.text("  ✦ ", NamedTextColor.GOLD)
                                .append(Component.text("Ты получил ", NamedTextColor.GRAY))
                                .append(Component.text("Стрелу Судьбы", NamedTextColor.YELLOW))
                                .append(Component.text("! Используй её чтобы получить суперсилу.", NamedTextColor.GRAY)));
            }, 40L); // через 2 секунды чтобы инвентарь точно загрузился
        }
    }
}