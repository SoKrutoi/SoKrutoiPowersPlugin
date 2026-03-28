package org.sokrutoi.soKrutoiPowersPlugin.commands;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;
import org.sokrutoi.soKrutoiPowersPlugin.SoKrutoiPowersPlugin;
import org.sokrutoi.soKrutoiPowersPlugin.powers.SuperPower;

import java.util.*;
import java.util.stream.Collectors;

public class ClearPowersCommand implements CommandExecutor, TabCompleter {

    private final SoKrutoiPowersPlugin plugin;

    public ClearPowersCommand(SoKrutoiPowersPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length < 1) {
            sender.sendMessage(Component.text(
                    "Использование: /clearpowers <игрок|**>", NamedTextColor.RED));
            return true;
        }

        if (args[0].equals("**")) {
            int count = 0;
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (clearPlayer(p)) count++;
            }
            sender.sendMessage(Component.text(
                    "Очищено суперсил у " + count + " игроков.", NamedTextColor.GREEN));
        } else {
            Player target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                sender.sendMessage(Component.text(
                        "Игрок «" + args[0] + "» не в сети.", NamedTextColor.RED));
                return true;
            }
            clearPlayer(target);
            sender.sendMessage(Component.text(
                    "Суперсилы у " + target.getName() + " очищены.", NamedTextColor.GREEN));
        }
        return true;
    }

    private boolean clearPlayer(Player player) {
        boolean cleared = false;
        UUID    uuid    = player.getUniqueId();

        // 1. Предметные силы — изымаем тетради из инвентаря и эндер-сундука
        for (int i = 0; i < player.getInventory().getSize(); i++) {
            if (isDeathNote(player.getInventory().getItem(i))) {
                player.getInventory().setItem(i, null);
                cleared = true;
            }
        }
        for (int i = 0; i < player.getEnderChest().getSize(); i++) {
            if (isDeathNote(player.getEnderChest().getItem(i))) {
                player.getEnderChest().setItem(i, null);
                cleared = true;
            }
        }

        // 2. Отменить все запланированные смерти этим игроком
        List<UUID> toCancel = plugin.getWriterMap().entrySet().stream()
                .filter(e -> e.getValue().equals(uuid))
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
        for (UUID targetUUID : toCancel) {
            plugin.getPendingDeaths().remove(targetUUID);
            plugin.getWriterMap().remove(targetUUID);
            plugin.getTargetNames().remove(targetUUID);
            cleared = true;
        }

        // 3. UUID-based силы — вызываем revoke() у каждой
        for (SuperPower power : plugin.getPowerManager().getAll()) {
            if (power.hasPlayer(player)) {
                power.revoke(player);
                cleared = true;
            }
        }

        if (cleared) {
            player.sendMessage(Component.text(
                    "Твои суперсилы были изъяты.", NamedTextColor.DARK_RED));
        }
        return cleared;
    }

    private boolean isDeathNote(ItemStack item) {
        if (item == null) return false;
        if (item.getType() != Material.WRITABLE_BOOK && item.getType() != Material.WRITTEN_BOOK) return false;
        if (!(item.getItemMeta() instanceof BookMeta meta)) return false;
        return meta.getPersistentDataContainer().has(
                plugin.getDeathNoteKey(), PersistentDataType.BYTE);
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command cmd,
                                      @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> opts = new ArrayList<>(List.of("**"));
            Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(args[0].toLowerCase()))
                    .forEach(opts::add);
            return opts;
        }
        return List.of();
    }
}