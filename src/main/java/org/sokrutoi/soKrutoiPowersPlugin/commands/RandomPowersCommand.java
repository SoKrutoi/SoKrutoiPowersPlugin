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

/**
 * /randompowers [--one] [сила1 сила2 ...]
 *
 * Без флагов (--all по умолчанию):
 *   Каждый онлайн-игрок получает одну случайную силу из пула.
 *   Распределение равномерное — никакая сила не может встретиться на 2+
 *   больше раз, чем другая.
 *
 * С флагом --one:
 *   Каждая сила из пула выдаётся ровно одному случайному игроку.
 *   Остальные игроки ничего не получают (их текущие силы не отзываются).
 *   Удобно для выдачи одной особенной способности (например, только DeathNote).
 *
 * Поддерживает предметные силы (DeathNote): книга падает с неба.
 */
public class RandomPowersCommand implements CommandExecutor, TabCompleter {

    private static final String FLAG_ONE = "--one";

    private final SoKrutoiPowersPlugin plugin;

    public RandomPowersCommand(SoKrutoiPowersPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd,
                             @NotNull String label, @NotNull String[] args) {

        // ── Парсим аргументы ──────────────────────────────────────────────
        boolean oneMode = false;
        List<String> powerNames = new ArrayList<>();

        for (String arg : args) {
            if (arg.equalsIgnoreCase(FLAG_ONE)) {
                oneMode = true;
            } else {
                powerNames.add(arg);
            }
        }

        // ── Собираем пул сил ──────────────────────────────────────────────
        List<SuperPower> pool;

        if (powerNames.isEmpty()) {
            // Все зарегистрированные силы (и привязанные, и предметные)
            pool = new ArrayList<>(plugin.getPowerManager().getAll());
        } else {
            pool = new ArrayList<>();
            for (String name : powerNames) {
                Optional<SuperPower> opt = plugin.getPowerManager().getByName(name);
                if (opt.isEmpty()) {
                    sender.sendMessage(Component.text(
                            "Суперсила «" + name + "» не найдена.", NamedTextColor.RED));
                    return true;
                }
                SuperPower p = opt.get();
                if (pool.contains(p)) {
                    sender.sendMessage(Component.text(
                            "Сила «" + p.getName() + "» указана дважды.", NamedTextColor.RED));
                    return true;
                }
                pool.add(p);
            }
        }

        if (pool.isEmpty()) {
            sender.sendMessage(Component.text("Нет доступных сил для раздачи.", NamedTextColor.RED));
            return true;
        }

        List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
        if (players.isEmpty()) {
            sender.sendMessage(Component.text("Нет онлайн-игроков.", NamedTextColor.RED));
            return true;
        }

        // ── Режим --one: каждой силе — один случайный игрок ──────────────
        if (oneMode) {
            return handleOneMode(sender, pool, players);
        }

        // ── Режим --all (по умолчанию): все игроки получают силу ─────────
        return handleAllMode(sender, pool, players);
    }

    // ─────────────────────────────────────────────────────────────────────
    // Режим --all: равномерная раздача всем игрокам
    // ─────────────────────────────────────────────────────────────────────
    private boolean handleAllMode(CommandSender sender, List<SuperPower> pool, List<Player> players) {

        // Отзываем ВСЕ силы у всех игроков (и привязанные, и предметные)
        for (Player player : players) {
            for (SuperPower power : plugin.getPowerManager().getAll()) {
                if (power.hasPlayer(player)) power.revoke(player);
            }
        }

        Collections.shuffle(players);

        // Алгоритм равномерного распределения:
        // 7 игроков, 3 силы → base=2, extra=1 → [3, 2, 2]
        List<SuperPower> shuffledPool = new ArrayList<>(pool);
        Collections.shuffle(shuffledPool);

        int playerCount = players.size();
        int poolSize    = shuffledPool.size();
        int base        = playerCount / poolSize;
        int extra       = playerCount % poolSize;

        List<SuperPower> assignments = new ArrayList<>(playerCount);
        for (int i = 0; i < poolSize; i++) {
            int count = base + (i < extra ? 1 : 0);
            for (int j = 0; j < count; j++) assignments.add(shuffledPool.get(i));
        }
        Collections.shuffle(assignments);

        for (int i = 0; i < playerCount; i++) {
            assignments.get(i).giveToPlayer(players.get(i));
        }

        // Минимальный вывод — без раскрытия кто что получил
        sender.sendMessage(Component.text(
                "✓ Силы розданы " + playerCount + " игрокам.", NamedTextColor.GREEN));
        Bukkit.broadcast(Component.text(
                "⚡ Суперсилы случайно перераспределены!", NamedTextColor.GOLD));
        return true;
    }

    // ─────────────────────────────────────────────────────────────────────
    // Режим --one: каждой силе ровно один случайный игрок
    // ─────────────────────────────────────────────────────────────────────
    private boolean handleOneMode(CommandSender sender, List<SuperPower> pool, List<Player> players) {

        // Перемешиваем обе стороны и берём min(сил, игроков) пар
        Collections.shuffle(players);
        List<SuperPower> shuffledPool = new ArrayList<>(pool);
        Collections.shuffle(shuffledPool);

        int count = Math.min(shuffledPool.size(), players.size());

        for (int i = 0; i < count; i++) {
            Player    target = players.get(i);
            SuperPower power  = shuffledPool.get(i);

            // Отзываем текущие силы только у получателя
            for (SuperPower existing : plugin.getPowerManager().getAll()) {
                if (existing.hasPlayer(target)) existing.revoke(target);
            }

            power.giveToPlayer(target);
        }

        sender.sendMessage(Component.text(
                "✓ " + count + " сил выдано случайным игрокам.", NamedTextColor.GREEN));
        return true;
    }

    // ── Tab completion ────────────────────────────────────────────────────

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command cmd,
                                      @NotNull String label, @NotNull String[] args) {
        List<String> argList = Arrays.asList(args);
        String current = args[args.length - 1].toLowerCase();
        Set<String> used = new HashSet<>(argList.subList(0, args.length - 1));

        List<String> options = new ArrayList<>();

        // Флаг --one (один раз)
        if (!used.contains(FLAG_ONE) && FLAG_ONE.startsWith(current)) {
            options.add(FLAG_ONE);
        }

        // Все силы (включая предметные)
        plugin.getPowerManager().getAll().stream()
                .map(SuperPower::getName)
                .filter(n -> !used.contains(n))
                .filter(n -> n.toLowerCase().startsWith(current))
                .forEach(options::add);

        return options;
    }
}