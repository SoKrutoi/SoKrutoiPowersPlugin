package org.sokrutoi.soKrutoiPowersPlugin.powers;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.sokrutoi.soKrutoiPowersPlugin.SoKrutoiPowersPlugin;
import org.sokrutoi.soKrutoiPowersPlugin.listeners.ShrinkListener;

import java.util.*;

public class ShrinkPower extends SuperPower {

    // Только два атрибута меняем — размер и HP.
    // Скорость, прыжок, дальность, step-height — намеренно не трогаем.
    private final NamespacedKey keyScale;
    private final NamespacedKey keyHp;

    private final Map<UUID, Integer> sizeLevel = new HashMap<>();
    private final Set<UUID>          players   = new HashSet<>();

    public ShrinkPower(SoKrutoiPowersPlugin plugin) {
        super(plugin);
        keyScale = new NamespacedKey(plugin, "shrink_scale");
        keyHp    = new NamespacedKey(plugin, "shrink_health");
        loadPlayers();
    }

    @Override public String getName() { return "Shrink"; }

    @Override
    public String getDescription() {
        return "Уменьшение — Shift ×1: маленький, Shift ×2: крошечный, Shift ×3: обычный";
    }

    @Override
    public void register() {
        plugin.getServer().getPluginManager()
                .registerEvents(new ShrinkListener((SoKrutoiPowersPlugin) plugin, this), plugin);
    }

    // ── Выдача ─────────────────────────────────────────────────────────────

    @Override
    public void giveToPlayer(Player player) {
        if (!players.add(player.getUniqueId())) {
            player.sendMessage(Component.text("У тебя уже есть сила Уменьшения.", NamedTextColor.YELLOW));
            return;
        }
        savePlayers();
        player.sendMessage(Component.text("Ты получил силу ", NamedTextColor.GRAY)
                .append(Component.text("Уменьшения", NamedTextColor.AQUA))
                .append(Component.text(". Shift ×1 = маленький, ×2 = крошечный, ×3 = обычный.",
                        NamedTextColor.GRAY)));
    }

    @Override
    public boolean giveToOfflineUUID(UUID uuid) {
        if (!players.add(uuid)) return false;
        savePlayers();
        return true;
    }

    // ── Проверка ───────────────────────────────────────────────────────────

    @Override
    public boolean hasPlayer(Player player) { return players.contains(player.getUniqueId()); }

    @Override
    public Set<UUID> getAllPlayerUUIDs() { return Collections.unmodifiableSet(players); }

    public boolean hasPower(UUID uuid) { return players.contains(uuid); }

    // ── Отзыв ─────────────────────────────────────────────────────────────

    @Override
    public void revoke(Player player) {
        UUID uuid = player.getUniqueId();
        if (!players.remove(uuid)) return;
        savePlayers();
        resetSize(player);
        sizeLevel.remove(uuid);
        player.sendMessage(Component.text("Твоя сила Уменьшения была изъята.", NamedTextColor.DARK_RED));
    }

    @Override
    public void revokeOffline(UUID uuid) {
        if (!players.remove(uuid)) return;
        sizeLevel.remove(uuid);
        savePlayers();
    }

    // ── Сброс ─────────────────────────────────────────────────────────────

    @Override
    public void resetPlayer(Player player) {
        UUID uuid = player.getUniqueId();
        if (sizeLevel.containsKey(uuid)) {
            resetSize(player);
            sizeLevel.remove(uuid);
            player.sendActionBar(Component.text("◎ Размер сброшен", NamedTextColor.GRAY));
        }
    }

    // ── Логика цикла размеров ──────────────────────────────────────────────

    public void cycleSize(Player player) {
        UUID uuid    = player.getUniqueId();
        int  current = sizeLevel.getOrDefault(uuid, 0);
        int  next    = (current + 1) % 3;

        // Сохраняем процент HP до изменения максимума
        AttributeInstance hpAttr  = player.getAttribute(Attribute.MAX_HEALTH);
        double hpPercent = (hpAttr != null && hpAttr.getValue() > 0)
                ? player.getHealth() / hpAttr.getValue()
                : 1.0;

        if (next == 0) {
            resetSize(player);
            sizeLevel.remove(uuid);
            restoreHpPercent(player, hpPercent);
            player.sendActionBar(Component.text("◎ Обычный размер", NamedTextColor.GRAY));
        } else {
            applySize(player, next);
            sizeLevel.put(uuid, next);
            restoreHpPercent(player, hpPercent);
            String         label = next == 1 ? "Маленький" : "Крошечный";
            NamedTextColor color = next == 1 ? NamedTextColor.YELLOW : NamedTextColor.AQUA;
            player.sendActionBar(Component.text("◉ " + label, color));
        }
    }

    // ── Применение атрибутов ───────────────────────────────────────────────

    private void applySize(Player player, int level) {
        double scale  = cfgD(level, "scale",        level == 1 ? 0.5  : 0.1);
        double hpMult = cfgD(level, "hp-multiplier", level == 1 ? 0.5 : 0.25);

        // Масштаб: ADD_NUMBER, значение = scale - 1 (отрицательное → уменьшение)
        setMod(player, Attribute.SCALE, keyScale,
                scale - 1.0, AttributeModifier.Operation.ADD_NUMBER);

        // HP: уменьшаем в hpMult раз от базового значения
        double baseHp = getBase(player, Attribute.MAX_HEALTH, 20.0);
        // modifier = baseHp * (hpMult - 1), например 20 * (0.5 - 1) = -10 → MaxHP = 10
        setMod(player, Attribute.MAX_HEALTH, keyHp,
                baseHp * (hpMult - 1.0), AttributeModifier.Operation.ADD_NUMBER);
    }

    private void resetSize(Player player) {
        // Сначала убираем HP-модификатор, потом обрезаем текущее HP
        AttributeInstance hpAttr = player.getAttribute(Attribute.MAX_HEALTH);
        if (hpAttr != null) {
            removeModifier(player, Attribute.MAX_HEALTH, keyHp);
            double newMax = hpAttr.getValue();
            if (player.getHealth() > newMax) player.setHealth(newMax);
        }
        removeModifier(player, Attribute.SCALE, keyScale);
    }

    /** Восстанавливает HP = новый_максимум × процент. */
    private void restoreHpPercent(Player player, double percent) {
        AttributeInstance hpAttr = player.getAttribute(Attribute.MAX_HEALTH);
        if (hpAttr == null) return;
        double target = hpAttr.getValue() * Math.max(0.005, percent);
        player.setHealth(Math.min(Math.max(1.0, target), hpAttr.getValue()));
    }

    public int getSizeLevel(UUID uuid) { return sizeLevel.getOrDefault(uuid, 0); }

    // ── Вспомогательные ───────────────────────────────────────────────────

    private double cfgD(int level, String key, double def) {
        return plugin.getConfig().getDouble("shrink.level" + level + "." + key, def);
    }

    private void setMod(Player player, Attribute attr, NamespacedKey key,
                        double amount, AttributeModifier.Operation op) {
        AttributeInstance inst = player.getAttribute(attr);
        if (inst == null) return;
        inst.getModifiers().stream()
                .filter(m -> m.getKey().equals(key))
                .findFirst()
                .ifPresent(inst::removeModifier);
        if (amount != 0.0)
            inst.addModifier(new AttributeModifier(key, amount, op, EquipmentSlotGroup.ANY));
    }

    private void removeModifier(Player player, Attribute attr, NamespacedKey key) {
        AttributeInstance inst = player.getAttribute(attr);
        if (inst == null) return;
        inst.getModifiers().stream()
                .filter(m -> m.getKey().equals(key))
                .findFirst()
                .ifPresent(inst::removeModifier);
    }

    private double getBase(Player player, Attribute attr, double fallback) {
        AttributeInstance inst = player.getAttribute(attr);
        return inst != null ? inst.getBaseValue() : fallback;
    }

    // ── Персистентность ───────────────────────────────────────────────────

    private void loadPlayers() {
        for (String s : plugin.getConfig().getStringList("shrink-players")) {
            try { players.add(UUID.fromString(s)); } catch (Exception ignored) {}
        }
        plugin.getLogger().info("[Shrink] Загружено игроков: " + players.size());
    }

    public void savePlayers() {
        plugin.getConfig().set("shrink-players", players.stream().map(UUID::toString).toList());
        plugin.saveConfig();
    }
}