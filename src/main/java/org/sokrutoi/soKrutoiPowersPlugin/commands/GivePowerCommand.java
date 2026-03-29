package org.sokrutoi.soKrutoiPowersPlugin.commands;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.sokrutoi.soKrutoiPowersPlugin.SoKrutoiPowersPlugin;
import org.sokrutoi.soKrutoiPowersPlugin.powers.SuperPower;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

public class GivePowerCommand implements CommandExecutor, TabCompleter {

    private final SoKrutoiPowersPlugin plugin;

    public GivePowerCommand(SoKrutoiPowersPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Component.text(
                    "Использование: /givepower <игрок> <суперсила>", NamedTextColor.RED));
            sendAvailablePowers(sender);
            return true;
        }

        Optional<SuperPower> powerOpt = plugin.getPowerManager().getByName(args[1]);
        if (powerOpt.isEmpty()) {
            sender.sendMessage(Component.text(
                    "Суперсила «" + args[1] + "» не найдена.", NamedTextColor.RED));
            sendAvailablePowers(sender);
            return true;
        }
        SuperPower power = powerOpt.get();

        // Сначала пробуем найти онлайн-игрока
        Player online = plugin.getServer().getPlayerExact(args[0]);
        if (online != null) {
            giveToOnline(sender, online, power);
            return true;
        }

        // Офлайн-игрок
        @SuppressWarnings("deprecation")
        OfflinePlayer offline = Bukkit.getOfflinePlayer(args[0]);
        if (!offline.hasPlayedBefore()) {
            sender.sendMessage(Component.text(
                    "Игрок «" + args[0] + "» не найден.", NamedTextColor.RED));
            return true;
        }

        giveToOffline(sender, offline, power);
        return true;
    }

    private void giveToOnline(CommandSender sender, Player target, SuperPower newPower) {
        // Привязанные силы вытесняют друг друга
        if (newPower.isBound()) {
            for (SuperPower existing : plugin.getPowerManager().getAll()) {
                if (existing == newPower) continue;
                if (existing.isBound() && existing.hasPlayer(target)) {
                    existing.revoke(target);
                    sender.sendMessage(Component.text(
                            "Сила " + existing.getName() + " отозвана у " + target.getName() + ".",
                            NamedTextColor.YELLOW));
                }
            }
        }
        newPower.giveToPlayer(target);
        sender.sendMessage(Component.text("Суперсила ", NamedTextColor.GREEN)
                .append(Component.text(newPower.getName(), NamedTextColor.GOLD))
                .append(Component.text(" выдана игроку ", NamedTextColor.GREEN))
                .append(Component.text(target.getName(), NamedTextColor.YELLOW)));
    }

    private void giveToOffline(CommandSender sender, OfflinePlayer target, SuperPower power) {
        if (!power.isBound()) {
            // Предметная сила — нельзя выдать офлайн-игроку
            sender.sendMessage(Component.text(
                    "Сила «" + power.getName() + "» предметная — нельзя выдать офлайн-игроку.",
                    NamedTextColor.RED));
            return;
        }

        UUID   uuid = target.getUniqueId();
        String name = target.getName() != null ? target.getName() : uuid.toString();

        // Вытесняем другие привязанные силы из UUID-хранилищ
        for (SuperPower existing : plugin.getPowerManager().getAll()) {
            if (existing == power) continue;
            if (existing.isBound() && existing.getAllPlayerUUIDs().contains(uuid)) {
                existing.revokeOffline(uuid);
                sender.sendMessage(Component.text(
                        "Сила " + existing.getName() + " отозвана у " + name + " (офлайн).",
                        NamedTextColor.YELLOW));
            }
        }

        boolean added = power.giveToOfflineUUID(uuid);
        if (added) {
            sender.sendMessage(Component.text("Суперсила ", NamedTextColor.GREEN)
                    .append(Component.text(power.getName(), NamedTextColor.GOLD))
                    .append(Component.text(" будет у " + name + " при следующем входе.", NamedTextColor.GREEN)));
        } else {
            sender.sendMessage(Component.text(
                    "У " + name + " уже есть сила " + power.getName() + ".", NamedTextColor.YELLOW));
        }
    }

    private void sendAvailablePowers(CommandSender sender) {
        sender.sendMessage(Component.text("Доступные суперсилы:", NamedTextColor.GRAY));
        plugin.getPowerManager().getAll().forEach(p ->
                sender.sendMessage(Component.text("  • ", NamedTextColor.DARK_GRAY)
                        .append(Component.text(p.getName(), NamedTextColor.GOLD))
                        .append(Component.text(" — " + p.getDescription(), NamedTextColor.GRAY))));
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            return plugin.getServer().getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        if (args.length == 2) {
            return plugin.getPowerManager().getAll().stream()
                    .map(SuperPower::getName)
                    .filter(n -> n.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return List.of();
    }
}