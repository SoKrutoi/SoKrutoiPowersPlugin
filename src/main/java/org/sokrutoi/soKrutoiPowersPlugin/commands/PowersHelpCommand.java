package org.sokrutoi.soKrutoiPowersPlugin.commands;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.sokrutoi.soKrutoiPowersPlugin.SoKrutoiPowersPlugin;
import org.sokrutoi.soKrutoiPowersPlugin.powers.SuperPower;

public class PowersHelpCommand implements CommandExecutor {

    private final SoKrutoiPowersPlugin plugin;

    public PowersHelpCommand(SoKrutoiPowersPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd,
                             @NotNull String label, @NotNull String[] args) {
        sendHelp(sender);
        return true;
    }

    public static void sendHelp(CommandSender sender) {
        sender.sendMessage(Component.empty());
        sender.sendMessage(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.DARK_GRAY));
        sender.sendMessage(
                Component.text("  ⚡ ", NamedTextColor.YELLOW)
                        .append(Component.text("SoKrutoi Powers", NamedTextColor.GOLD)
                                .decoration(TextDecoration.BOLD, true))
                        .append(Component.text(" — суперсилы", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.DARK_GRAY));
        sender.sendMessage(Component.empty());
        sender.sendMessage(
                Component.text("  Каждому игроку выдаётся одна суперсила.", NamedTextColor.GRAY));
        sender.sendMessage(
                Component.text("  Силы выдаются администраторами или через", NamedTextColor.GRAY));
        sender.sendMessage(
                Component.text("  случайное распределение в начале игры.", NamedTextColor.GRAY));
        sender.sendMessage(Component.empty());
        sender.sendMessage(
                Component.text("  ✦ ", NamedTextColor.GOLD)
                        .append(Component.text("Стрела Судьбы", NamedTextColor.YELLOW)
                                .decoration(TextDecoration.BOLD, true)));
        sender.sendMessage(
                Component.text("  Особый предмет — даёт случайную суперсилу.", NamedTextColor.GRAY));
        sender.sendMessage(
                Component.text("  Удерживай ПКМ 5 секунд со стрелой в руке.", NamedTextColor.GRAY));
        sender.sendMessage(Component.empty());
        sender.sendMessage(
                Component.text("  Крафт стрелы: ", NamedTextColor.GRAY)
                        .append(Component.text("верстак (бесформенный)", NamedTextColor.WHITE)));
        sender.sendMessage(
                Component.text("  Стрела + Золотой слиток + Алмаз", NamedTextColor.DARK_AQUA));
        sender.sendMessage(
                Component.text("  + Стержень Пламени + Светопыль", NamedTextColor.DARK_AQUA));
        sender.sendMessage(Component.empty());
        sender.sendMessage(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.DARK_GRAY));
        sender.sendMessage(Component.empty());
    }
}