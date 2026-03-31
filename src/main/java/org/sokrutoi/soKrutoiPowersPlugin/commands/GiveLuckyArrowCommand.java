package org.sokrutoi.soKrutoiPowersPlugin.commands;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.sokrutoi.soKrutoiPowersPlugin.SoKrutoiPowersPlugin;

import java.util.List;
import java.util.stream.Collectors;

public class GiveLuckyArrowCommand implements CommandExecutor, TabCompleter {

    private final SoKrutoiPowersPlugin plugin;

    public GiveLuckyArrowCommand(SoKrutoiPowersPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length < 1) {
            sender.sendMessage(Component.text(
                    "Использование: /giveluckyarrow <игрок> [количество]", NamedTextColor.RED));
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            sender.sendMessage(Component.text(
                    "Игрок «" + args[0] + "» не в сети.", NamedTextColor.RED));
            return true;
        }

        int amount = 1;
        if (args.length >= 2) {
            try {
                amount = Math.max(1, Math.min(64, Integer.parseInt(args[1])));
            } catch (NumberFormatException e) {
                sender.sendMessage(Component.text(
                        "Неверное количество: «" + args[1] + "».", NamedTextColor.RED));
                return true;
            }
        }

        ItemStack arrow = plugin.getLuckyArrow().createItem();
        arrow.setAmount(amount);
        target.getInventory().addItem(arrow);

        sender.sendMessage(Component.text("✦ ", NamedTextColor.GOLD)
                .append(Component.text("Стрела Судьбы", NamedTextColor.YELLOW))
                .append(Component.text(" x" + amount + " выдана игроку ", NamedTextColor.GREEN))
                .append(Component.text(target.getName(), NamedTextColor.YELLOW)));
        target.sendMessage(Component.text("✦ Ты получил ", NamedTextColor.GOLD)
                .append(Component.text("Стрелу Судьбы", NamedTextColor.YELLOW))
                .append(Component.text(" x" + amount + "!", NamedTextColor.GOLD)));

        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command cmd,
                                      @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        if (args.length == 2) return List.of("1", "4", "16", "64");
        return List.of();
    }
}