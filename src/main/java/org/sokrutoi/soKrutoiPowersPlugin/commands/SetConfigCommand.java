package org.sokrutoi.soKrutoiPowersPlugin.commands;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.sokrutoi.soKrutoiPowersPlugin.SoKrutoiPowersPlugin;

import java.util.List;

public class SetConfigCommand implements CommandExecutor, TabCompleter {

    private final SoKrutoiPowersPlugin plugin;

    private static final List<String> KEYS = List.of(
            // Тетрадь смерти
            "death-delay-seconds",
            "death-warn-seconds",
            // Взрыв
            "explosion-charge-seconds",
            "explosion-power",
            "explosion-break-blocks",
            "explosion-cooldown-seconds",
            // Вор
            "thief-cooldown-seconds",
            // Гигант
            "giant.level1.scale",
            "giant.level1.hp-multiplier",
            "giant.level1.reach-add",
            "giant.level1.step-height-add",
            "giant.level1.speed-multiplier",
            "giant.level1.jump-strength-add",
            "giant.level1.knockback-resistance-add",
            "giant.level1.attack-knockback-add",
            "giant.level2.scale",
            "giant.level2.hp-multiplier",
            "giant.level2.reach-add",
            "giant.level2.step-height-add",
            "giant.level2.speed-multiplier",
            "giant.level2.jump-strength-add",
            "giant.level2.knockback-resistance-add",
            "giant.level2.attack-knockback-add",
            // Уменьшение
            "shrink.level1.scale",
            "shrink.level1.hp-multiplier",
            "shrink.level2.scale",
            "shrink.level2.hp-multiplier",
            // За Варудо
            "zawarudo.radius",
            "zawarudo.duration-seconds",
            "zawarudo.cooldown-seconds",
            "zawarudo.double-shift-window-ms",
            "zawarudo.freeze-whole-server",
            // Флеш
            "flash.speed-level",
            "flash.dash-velocity",
            "flash.dash-cooldown-seconds",
            "flash.step-height-bonus",
            "flash.hunger-interval-ticks"
    );

    public SetConfigCommand(SoKrutoiPowersPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd,
                             @NotNull String label, @NotNull String[] args) {

        if (args.length < 2) {
            sender.sendMessage(Component.text(
                    "Использование: /setconfig <ключ> <значение>", NamedTextColor.RED));
            sender.sendMessage(Component.text("Доступные ключи:", NamedTextColor.GRAY));
            KEYS.forEach(k -> sender.sendMessage(
                    Component.text("  • ", NamedTextColor.DARK_GRAY)
                            .append(Component.text(k, NamedTextColor.YELLOW))
                            .append(Component.text(" = ", NamedTextColor.DARK_GRAY))
                            .append(Component.text(
                                    String.valueOf(plugin.getConfig().get(k)), NamedTextColor.WHITE))));
            return true;
        }

        String key      = args[0];
        String valueStr = args[1];

        if (!KEYS.contains(key)) {
            sender.sendMessage(Component.text(
                    "Ключ «" + key + "» не разрешён для изменения.", NamedTextColor.RED));
            return true;
        }

        Object current = plugin.getConfig().get(key);
        if (current == null) {
            sender.sendMessage(Component.text(
                    "Ключ «" + key + "» не найден в конфиге.", NamedTextColor.RED));
            return true;
        }

        try {
            if (current instanceof Boolean) {
                plugin.getConfig().set(key, Boolean.parseBoolean(valueStr));
            } else if (current instanceof Double) {
                plugin.getConfig().set(key, Double.parseDouble(valueStr));
            } else if (current instanceof Number) {
                plugin.getConfig().set(key, Long.parseLong(valueStr));
            } else {
                plugin.getConfig().set(key, valueStr);
            }
        } catch (NumberFormatException e) {
            sender.sendMessage(Component.text(
                    "Неверное значение: «" + valueStr + "». Ожидается: "
                            + typeName(current) + ".", NamedTextColor.RED));
            return true;
        }

        plugin.saveConfig();

        sender.sendMessage(
                Component.text("✓ ", NamedTextColor.GREEN)
                        .append(Component.text(key, NamedTextColor.YELLOW))
                        .append(Component.text(" = ", NamedTextColor.GRAY))
                        .append(Component.text(
                                String.valueOf(plugin.getConfig().get(key)), NamedTextColor.WHITE)));
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command cmd,
                                      @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            return KEYS.stream()
                    .filter(k -> k.toLowerCase().startsWith(args[0].toLowerCase()))
                    .toList();
        }
        if (args.length == 2) {
            Object val = plugin.getConfig().get(args[0]);
            if (val instanceof Boolean) return List.of("true", "false");
            if (val != null) return List.of(val.toString());
        }
        return List.of();
    }

    private String typeName(Object obj) {
        if (obj instanceof Boolean) return "true/false";
        if (obj instanceof Double)  return "дробное число";
        if (obj instanceof Number)  return "целое число";
        return "строка";
    }
}