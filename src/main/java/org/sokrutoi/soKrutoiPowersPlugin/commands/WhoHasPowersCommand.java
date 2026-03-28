package org.sokrutoi.soKrutoiPowersPlugin.commands;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.sokrutoi.soKrutoiPowersPlugin.SoKrutoiPowersPlugin;
import org.sokrutoi.soKrutoiPowersPlugin.powers.SuperPower;

import java.util.*;

public class WhoHasPowersCommand implements CommandExecutor {

    private final SoKrutoiPowersPlugin plugin;

    public WhoHasPowersCommand(SoKrutoiPowersPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd,
                             @NotNull String label, @NotNull String[] args) {

        Collection<? extends Player> online = Bukkit.getOnlinePlayers();

        if (online.isEmpty()) {
            sender.sendMessage(Component.text("Нет игроков в сети.", NamedTextColor.GRAY));
            return true;
        }

        Collection<SuperPower> allPowers = plugin.getPowerManager().getAll();

        // power → список игроков с этой силой
        Map<SuperPower, List<String>> powerToPlayers = new LinkedHashMap<>();
        for (SuperPower power : allPowers) {
            powerToPlayers.put(power, new ArrayList<>());
        }

        // игроки без единой суперсилы
        List<String> noPower = new ArrayList<>();

        for (Player player : online) {
            boolean hasSome = false;
            for (SuperPower power : allPowers) {
                if (power.hasPlayer(player)) {
                    powerToPlayers.get(power).add(player.getName());
                    hasSome = true;
                }
            }
            if (!hasSome) {
                noPower.add(player.getName());
            }
        }

        // --- Вывод ---
        sender.sendMessage(Component.text(
                "═══════ Суперсилы ═══════", NamedTextColor.GOLD));

        boolean anyFound = false;
        for (SuperPower power : allPowers) {
            List<String> players = powerToPlayers.get(power);
            if (players.isEmpty()) continue;
            anyFound = true;

            sender.sendMessage(Component.text(
                    "◆ " + power.getName() + ":", NamedTextColor.YELLOW));
            for (String name : players) {
                sender.sendMessage(Component.text(
                        "  • " + name, NamedTextColor.WHITE));
            }
        }

        if (!anyFound) {
            sender.sendMessage(Component.text(
                    "  (никто не имеет суперсил)", NamedTextColor.DARK_GRAY));
        }

        if (!noPower.isEmpty()) {
            sender.sendMessage(Component.text(
                    "◇ Нет сил:", NamedTextColor.GRAY));
            for (String name : noPower) {
                sender.sendMessage(Component.text(
                        "  • " + name, NamedTextColor.DARK_GRAY));
            }
        }

        sender.sendMessage(Component.text(
                "═════════════════════════", NamedTextColor.GOLD));

        return true;
    }
}