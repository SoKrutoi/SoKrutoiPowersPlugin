package org.sokrutoi.soKrutoiPowersPlugin.commands;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
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
            // Онлайн-игроки — полная очистка
            int count = 0;
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (clearOnline(p)) count++;
            }
            // Офлайн-игроки — только UUID-хранилища
            Set<UUID> clearedOffline = clearAllOffline();
            sender.sendMessage(Component.text(
                    "Очищено суперсил у " + count + " онлайн-игроков и " +
                            clearedOffline.size() + " офлайн-игроков.", NamedTextColor.GREEN));
        } else {
            Player online = Bukkit.getPlayerExact(args[0]);
            if (online != null) {
                clearOnline(online);
                sender.sendMessage(Component.text(
                        "Суперсилы у " + online.getName() + " очищены.", NamedTextColor.GREEN));
            } else {
                // Ищем офлайн-игрока
                @SuppressWarnings("deprecation")
                OfflinePlayer offline = Bukkit.getOfflinePlayer(args[0]);
                if (!offline.hasPlayedBefore()) {
                    sender.sendMessage(Component.text(
                            "Игрок «" + args[0] + "» не найден.", NamedTextColor.RED));
                    return true;
                }
                boolean cleared = clearOffline(offline.getUniqueId());
                if (cleared) {
                    sender.sendMessage(Component.text(
                            "Суперсилы у " + args[0] + " (офлайн) очищены.", NamedTextColor.GREEN));
                } else {
                    sender.sendMessage(Component.text(
                            "У " + args[0] + " нет суперсил.", NamedTextColor.GRAY));
                }
            }
        }
        return true;
    }

    /** Полная очистка для онлайн-игрока (предметы + UUID + эффекты). */
    private boolean clearOnline(Player player) {
        boolean cleared = false;
        UUID    uuid    = player.getUniqueId();

        // Тетради из инвентаря и эндер-сундука
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

        // Запланированные смерти этим игроком
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

        // UUID-based силы
        for (SuperPower power : plugin.getPowerManager().getAll()) {
            if (power.hasPlayer(player)) {
                power.revoke(player);
                cleared = true;
            }
        }

        if (cleared)
            player.sendMessage(Component.text("Твои суперсилы были изъяты.", NamedTextColor.DARK_RED));
        return cleared;
    }

    /** Очистка только UUID-хранилищ для офлайн-игрока. */
    private boolean clearOffline(UUID uuid) {
        boolean cleared = false;

        // Запланированные смерти написанные этим игроком
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

        // UUID-based силы
        for (SuperPower power : plugin.getPowerManager().getAll()) {
            if (power.getAllPlayerUUIDs().contains(uuid)) {
                power.revokeOffline(uuid);
                cleared = true;
            }
        }
        return cleared;
    }

    /** Очищает всех офлайн-игроков из всех UUID-хранилищ. Возвращает затронутые UUID. */
    private Set<UUID> clearAllOffline() {
        Set<UUID> onlineUUIDs = new HashSet<>();
        for (Player p : Bukkit.getOnlinePlayers()) onlineUUIDs.add(p.getUniqueId());

        Set<UUID> cleared = new HashSet<>();
        for (SuperPower power : plugin.getPowerManager().getAll()) {
            for (UUID uuid : new HashSet<>(power.getAllPlayerUUIDs())) {
                if (!onlineUUIDs.contains(uuid)) {
                    power.revokeOffline(uuid);
                    cleared.add(uuid);
                }
            }
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