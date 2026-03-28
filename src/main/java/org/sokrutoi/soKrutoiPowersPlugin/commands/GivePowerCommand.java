package org.sokrutoi.soKrutoiPowersPlugin.commands;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
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
import java.util.stream.Collectors;

public class GivePowerCommand implements CommandExecutor, TabCompleter {

    private final SoKrutoiPowersPlugin plugin;

    public GivePowerCommand(SoKrutoiPowersPlugin plugin) {
        this.plugin = plugin;
    }

    // /givepower <player> <PowerName>
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Component.text("Использование: /givepower <игрок> <суперсила>", NamedTextColor.RED));
            sendAvailablePowers(sender);
            return true;
        }

        Player target = plugin.getServer().getPlayerExact(args[0]);
        if (target == null) {
            sender.sendMessage(Component.text("Игрок «" + args[0] + "» не в сети.", NamedTextColor.RED));
            return true;
        }

        Optional<SuperPower> powerOpt = plugin.getPowerManager().getByName(args[1]);
        if (powerOpt.isEmpty()) {
            sender.sendMessage(Component.text("Суперсила «" + args[1] + "» не найдена.", NamedTextColor.RED));
            sendAvailablePowers(sender);
            return true;
        }

        SuperPower newPower = powerOpt.get();

        // Если новая сила — привязанная (не предметная), отзываем все другие привязанные силы
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

        return true;
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

    private void sendAvailablePowers(CommandSender sender) {
        sender.sendMessage(Component.text("Доступные суперсилы:", NamedTextColor.GRAY));
        plugin.getPowerManager().getAll().forEach(p ->
                sender.sendMessage(Component.text("  • ", NamedTextColor.DARK_GRAY)
                        .append(Component.text(p.getName(), NamedTextColor.GOLD))
                        .append(Component.text(" — " + p.getDescription(), NamedTextColor.GRAY))));
    }
}