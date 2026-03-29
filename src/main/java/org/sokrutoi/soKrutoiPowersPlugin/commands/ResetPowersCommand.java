package org.sokrutoi.soKrutoiPowersPlugin.commands;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.sokrutoi.soKrutoiPowersPlugin.SoKrutoiPowersPlugin;
import org.sokrutoi.soKrutoiPowersPlugin.powers.SuperPower;

import java.util.ArrayList;
import java.util.List;

public class ResetPowersCommand implements CommandExecutor, TabCompleter {

    private final SoKrutoiPowersPlugin plugin;

    public ResetPowersCommand(SoKrutoiPowersPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length < 1) {
            sender.sendMessage(Component.text(
                    "Использование: /resetpowers <игрок|**>", NamedTextColor.RED));
            return true;
        }

        if (args[0].equals("**")) {
            int count = 0;
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (resetPlayer(p)) count++;
            }
            sender.sendMessage(Component.text(
                    "Состояние сил сброшено у " + count + " игроков.", NamedTextColor.GREEN));
        } else {
            Player target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                // /resetpowers работает только на онлайн-игроках —
                // сбрасывать эффекты офлайн-игроку бессмысленно (они и так пропадут)
                sender.sendMessage(Component.text(
                        "Игрок «" + args[0] + "» не в сети. Сброс состояния возможен только онлайн.",
                        NamedTextColor.RED));
                return true;
            }
            resetPlayer(target);
            sender.sendMessage(Component.text(
                    "Состояние сил у " + target.getName() + " сброшено.", NamedTextColor.GREEN));
        }
        return true;
    }

    /**
     * Сбрасывает активное состояние всех сил у игрока:
     * кулдауны, невидимость, размер — но не саму силу (UUID остаётся).
     */
    private boolean resetPlayer(Player player) {
        boolean reset = false;
        for (SuperPower power : plugin.getPowerManager().getAll()) {
            if (power.hasPlayer(player)) {
                power.resetPlayer(player);
                reset = true;
            }
        }
        if (reset) {
            player.sendMessage(Component.text(
                    "Состояние твоих суперсил было сброшено администратором.", NamedTextColor.YELLOW));
        }
        return reset;
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