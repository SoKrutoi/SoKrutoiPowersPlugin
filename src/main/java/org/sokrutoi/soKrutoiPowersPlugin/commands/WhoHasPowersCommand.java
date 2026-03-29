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

        Set<UUID> onlineUUIDs = new HashSet<>();
        for (Player p : Bukkit.getOnlinePlayers()) onlineUUIDs.add(p.getUniqueId());

        Collection<SuperPower> allPowers = plugin.getPowerManager().getAll();

        // power → список строк "имя (офлайн?)"
        Map<SuperPower, List<String>> powerToPlayers = new LinkedHashMap<>();
        for (SuperPower power : allPowers) powerToPlayers.put(power, new ArrayList<>());

        // UUID-based силы: берём всех из хранилища (онлайн + офлайн)
        Set<UUID> anyPowerUUIDs = new HashSet<>();
        for (SuperPower power : allPowers) {
            for (UUID uuid : power.getAllPlayerUUIDs()) {
                anyPowerUUIDs.add(uuid);
                String name   = nameOf(uuid);
                String label2 = onlineUUIDs.contains(uuid) ? name : name + " §7(офлайн)";
                powerToPlayers.get(power).add(label2);
            }
        }

        // Предметные силы (DeathNote): только онлайн-игроки, проверяем инвентарь
        for (Player player : Bukkit.getOnlinePlayers()) {
            for (SuperPower power : allPowers) {
                // Если сила не имеет UUID-хранилища (getAllPlayerUUIDs пустой) — проверяем hasPlayer
                if (power.getAllPlayerUUIDs().isEmpty() && power.hasPlayer(player)) {
                    anyPowerUUIDs.add(player.getUniqueId());
                    powerToPlayers.get(power).add(player.getName());
                }
            }
        }

        // Игроки без сил — только онлайн (офлайн без сил нам неинтересны)
        List<String> noPower = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!anyPowerUUIDs.contains(p.getUniqueId())) noPower.add(p.getName());
        }

        // --- Вывод ---
        sender.sendMessage(Component.text("═══════ Суперсилы ═══════", NamedTextColor.GOLD));

        boolean anyFound = false;
        for (SuperPower power : allPowers) {
            List<String> players = powerToPlayers.get(power);
            if (players.isEmpty()) continue;
            anyFound = true;
            sender.sendMessage(Component.text("◆ " + power.getName() + ":", NamedTextColor.YELLOW));
            for (String name : players)
                sender.sendMessage(Component.text("  • " + name, NamedTextColor.WHITE));
        }

        if (!anyFound)
            sender.sendMessage(Component.text("  (никто не имеет суперсил)", NamedTextColor.DARK_GRAY));

        if (!noPower.isEmpty()) {
            sender.sendMessage(Component.text("◇ Нет сил (онлайн):", NamedTextColor.GRAY));
            for (String name : noPower)
                sender.sendMessage(Component.text("  • " + name, NamedTextColor.DARK_GRAY));
        }

        sender.sendMessage(Component.text("═════════════════════════", NamedTextColor.GOLD));
        return true;
    }

    private String nameOf(UUID uuid) {
        Player online = Bukkit.getPlayer(uuid);
        if (online != null) return online.getName();
        String name = Bukkit.getOfflinePlayer(uuid).getName();
        return name != null ? name : uuid.toString().substring(0, 8) + "...";
    }
}