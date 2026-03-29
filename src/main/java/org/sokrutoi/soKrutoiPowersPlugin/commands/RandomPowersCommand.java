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

import java.util.*;
import java.util.stream.Collectors;

public class RandomPowersCommand implements CommandExecutor, TabCompleter {

    private final SoKrutoiPowersPlugin plugin;

    public RandomPowersCommand(SoKrutoiPowersPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd,
                             @NotNull String label, @NotNull String[] args) {

        // ── Собираем пул сил ──────────────────────────────────────────────
        List<SuperPower> pool;

        if (args.length > 0) {
            // Пользователь явно задал список сил
            pool = new ArrayList<>();
            for (String arg : args) {
                Optional<SuperPower> opt = plugin.getPowerManager().getByName(arg);
                if (opt.isEmpty()) {
                    sender.sendMessage(Component.text(
                            "Суперсила «" + arg + "» не найдена.", NamedTextColor.RED));
                    return true;
                }
                SuperPower p = opt.get();
                if (!p.isBound()) {
                    sender.sendMessage(Component.text(
                            "Сила «" + p.getName() + "» предметная — нельзя раздать рандомно.",
                            NamedTextColor.RED));
                    return true;
                }
                if (pool.contains(p)) {
                    sender.sendMessage(Component.text(
                            "Сила «" + p.getName() + "» указана дважды.", NamedTextColor.RED));
                    return true;
                }
                pool.add(p);
            }
        } else {
            // Все привязанные силы
            pool = plugin.getPowerManager().getAll().stream()
                    .filter(SuperPower::isBound)
                    .collect(Collectors.toList());
        }

        if (pool.isEmpty()) {
            sender.sendMessage(Component.text(
                    "Нет доступных привязанных сил для раздачи.", NamedTextColor.RED));
            return true;
        }

        List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
        if (players.isEmpty()) {
            sender.sendMessage(Component.text("Нет онлайн-игроков.", NamedTextColor.RED));
            return true;
        }

        // ── Отзываем все текущие привязанные силы ─────────────────────────
        // (тихо — чтобы не спамить чат игрокам сообщениями об изъятии)
        for (Player player : players) {
            for (SuperPower existing : plugin.getPowerManager().getAll()) {
                if (existing.isBound() && existing.hasPlayer(player)) {
                    existing.revoke(player);
                }
            }
        }

        // ── Формируем равномерно распределённый список назначений ─────────
        //
        // Алгоритм: round-robin по случайно перетасованному пулу.
        // Пример: 7 игроков, 3 силы → [3, 2, 2] (первые (7 % 3) = 1 сила получает +1).
        // После перемешивания пула и игроков — распределение честное.

        Collections.shuffle(players);               // случайный порядок игроков
        List<SuperPower> shuffledPool = new ArrayList<>(pool);
        Collections.shuffle(shuffledPool);          // случайный порядок «кто получит лишнего»

        int playerCount = players.size();
        int poolSize    = shuffledPool.size();
        int base        = playerCount / poolSize;   // минимум на каждую силу
        int extra       = playerCount % poolSize;   // столько сил получат +1 игрока

        // Строим итоговый список назначений такого же размера, как players
        List<SuperPower> assignments = new ArrayList<>(playerCount);
        for (int i = 0; i < poolSize; i++) {
            int count = base + (i < extra ? 1 : 0);
            for (int j = 0; j < count; j++) {
                assignments.add(shuffledPool.get(i));
            }
        }
        // Перемешиваем назначения, чтобы игроки рядом не получали одну силу
        Collections.shuffle(assignments);

        // ── Раздаём силы ─────────────────────────────────────────────────
        for (int i = 0; i < playerCount; i++) {
            assignments.get(i).giveToPlayer(players.get(i));
        }

        // ── Сводная статистика для отправителя ────────────────────────────
        Map<String, Long> summary = assignments.stream()
                .collect(Collectors.groupingBy(SuperPower::getName, Collectors.counting()));

        sender.sendMessage(Component.text("═══ Рандомная раздача сил ═══", NamedTextColor.GOLD));
        sender.sendMessage(
                Component.text("Игроков получивших силы: ", NamedTextColor.GRAY)
                        .append(Component.text(playerCount + "", NamedTextColor.WHITE)));

        // ── Широковещательное объявление всем игрокам ────────────────────
        Bukkit.broadcast(
                Component.text("⚡ ", NamedTextColor.GOLD)
                        .append(Component.text("Суперсилы случайно перераспределены между игроками!",
                                NamedTextColor.YELLOW)));

        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command cmd,
                                      @NotNull String label, @NotNull String[] args) {
        if (args.length >= 1) {
            // Уже выбранные аргументы (кроме текущего)
            Set<String> used = new HashSet<>(Arrays.asList(args).subList(0, args.length - 1));
            String current   = args[args.length - 1].toLowerCase();

            return plugin.getPowerManager().getAll().stream()
                    .filter(SuperPower::isBound)
                    .map(SuperPower::getName)
                    .filter(n -> !used.contains(n))
                    .filter(n -> n.toLowerCase().startsWith(current))
                    .collect(Collectors.toList());
        }
        return List.of();
    }
}