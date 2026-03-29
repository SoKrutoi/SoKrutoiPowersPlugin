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
import org.sokrutoi.soKrutoiPowersPlugin.listeners.GiantListener;

import java.util.*;

public class GiantPower extends SuperPower {

    // ── NamespacedKey для каждого модификатора ─────────────────────────────
    private final NamespacedKey keyScale;
    private final NamespacedKey keyHp;
    private final NamespacedKey keyReachBlock;
    private final NamespacedKey keyReachEntity;
    private final NamespacedKey keyStepHeight;
    private final NamespacedKey keySpeed;
    private final NamespacedKey keyJump;
    private final NamespacedKey keyKbResist;
    private final NamespacedKey keyAtkKb;
    private final NamespacedKey keySafeFall;

    private final Map<UUID, Integer> sizeLevel = new HashMap<>();
    private final Set<UUID>          players   = new HashSet<>();

    public GiantPower(SoKrutoiPowersPlugin plugin) {
        super(plugin);
        keyScale       = new NamespacedKey(plugin, "giant_scale");
        keyHp          = new NamespacedKey(plugin, "giant_health");
        keyReachBlock  = new NamespacedKey(plugin, "giant_reach_block");
        keyReachEntity = new NamespacedKey(plugin, "giant_reach_entity");
        keyStepHeight  = new NamespacedKey(plugin, "giant_step_height");
        keySpeed       = new NamespacedKey(plugin, "giant_speed");
        keyJump        = new NamespacedKey(plugin, "giant_jump");
        keyKbResist    = new NamespacedKey(plugin, "giant_kb_resist");
        keyAtkKb       = new NamespacedKey(plugin, "giant_atk_kb");
        keySafeFall    = new NamespacedKey(plugin, "giant_safe_fall");
        loadPlayers();
    }

    @Override public String getName() { return "Giant"; }

    @Override
    public String getDescription() {
        return "Гигант — Shift ×1: большой, Shift ×2: огромный, Shift ×3: обычный";
    }

    @Override
    public void register() {
        plugin.getServer().getPluginManager()
                .registerEvents(new GiantListener((SoKrutoiPowersPlugin) plugin, this), plugin);
    }

    // ── Выдача ─────────────────────────────────────────────────────────────

    @Override
    public void giveToPlayer(Player player) {
        if (!players.add(player.getUniqueId())) {
            player.sendMessage(Component.text("У тебя уже есть сила Гиганта.", NamedTextColor.YELLOW));
            return;
        }
        savePlayers();
        player.sendMessage(Component.text("Ты получил силу ", NamedTextColor.GRAY)
                .append(Component.text("Гиганта", NamedTextColor.GREEN))
                .append(Component.text(". Shift ×1 = большой, ×2 = огромный, ×3 = обычный.", NamedTextColor.GRAY)));
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

    // ── Отзыв ─────────────────────────────────────────────────────────────

    @Override
    public void revoke(Player player) {
        UUID uuid = player.getUniqueId();
        if (!players.remove(uuid)) return;
        savePlayers();
        resetSize(player);
        sizeLevel.remove(uuid);
        player.sendMessage(Component.text("Твоя сила Гиганта была изъята.", NamedTextColor.DARK_RED));
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

        // Запоминаем процент HP ДО изменения максимума — чтобы не дать восстановить HP шифтом
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
            String         label = next == 1 ? "Большой" : "Огромный";
            NamedTextColor color = next == 1 ? NamedTextColor.YELLOW : NamedTextColor.GREEN;
            player.sendActionBar(Component.text("◉ " + label, color));
        }
    }

    /**
     * Восстанавливает HP = новый_максимум × процент.
     * Гарантирует минимум 1 HP и не превышает текущий максимум.
     */
    private void restoreHpPercent(Player player, double percent) {
        AttributeInstance hpAttr = player.getAttribute(Attribute.MAX_HEALTH);
        if (hpAttr == null) return;
        double target = hpAttr.getValue() * Math.max(0.005, percent);
        player.setHealth(Math.min(Math.max(1.0, target), hpAttr.getValue()));
    }

    // ── Применение атрибутов ───────────────────────────────────────────────

    private void applySize(Player player, int level) {
        double scale       = cfgD(level, "scale",                    level == 1 ? 2.5  : 5.0);
        double hpMult      = cfgD(level, "hp-multiplier",            level == 1 ? 2.5  : 5.0);
        double reachAdd    = cfgD(level, "reach-add",                level == 1 ? 4.0  : 9.0);
        double stepAdd     = cfgD(level, "step-height-add",          level == 1 ? 1.0  : 2.5);
        double speedMult   = cfgD(level, "speed-multiplier",         level == 1 ? 0.25 : 0.6);
        double jumpAdd     = cfgD(level, "jump-strength-add",        level == 1 ? 0.2  : 0.5);
        double kbResistAdd = cfgD(level, "knockback-resistance-add", level == 1 ? 0.3  : 0.7);
        double atkKbAdd    = cfgD(level, "attack-knockback-add",     level == 1 ? 1.0  : 2.5);
        // Безопасная высота падения без урона (+N блоков к базовым 3)
        double safeFallAdd = cfgD(level, "safe-fall-add",            level == 1 ? 4.0  : 10.0);

        setMod(player, Attribute.SCALE,                    keyScale,       scale - 1.0,
                AttributeModifier.Operation.ADD_NUMBER);

        // Максимальное HP — модификатор без принудительного heal'а
        double baseHp = getBase(player, Attribute.MAX_HEALTH, 20.0);
        setMod(player, Attribute.MAX_HEALTH,               keyHp,          baseHp * (hpMult - 1.0),
                AttributeModifier.Operation.ADD_NUMBER);

        setMod(player, Attribute.BLOCK_INTERACTION_RANGE,  keyReachBlock,  reachAdd,
                AttributeModifier.Operation.ADD_NUMBER);
        setMod(player, Attribute.ENTITY_INTERACTION_RANGE, keyReachEntity, reachAdd,
                AttributeModifier.Operation.ADD_NUMBER);
        setMod(player, Attribute.STEP_HEIGHT,              keyStepHeight,  stepAdd,
                AttributeModifier.Operation.ADD_NUMBER);
        setMod(player, Attribute.MOVEMENT_SPEED,           keySpeed,       speedMult,
                AttributeModifier.Operation.ADD_SCALAR);
        setMod(player, Attribute.JUMP_STRENGTH,            keyJump,        jumpAdd,
                AttributeModifier.Operation.ADD_NUMBER);
        setMod(player, Attribute.KNOCKBACK_RESISTANCE,     keyKbResist,    kbResistAdd,
                AttributeModifier.Operation.ADD_NUMBER);
        setMod(player, Attribute.ATTACK_KNOCKBACK,         keyAtkKb,       atkKbAdd,
                AttributeModifier.Operation.ADD_NUMBER);
        setMod(player, Attribute.SAFE_FALL_DISTANCE,       keySafeFall,    safeFallAdd,
                AttributeModifier.Operation.ADD_NUMBER);
    }

    private void resetSize(Player player) {
        // Сначала убираем HP-модификатор, потом обрезаем текущее HP
        AttributeInstance hpAttr = player.getAttribute(Attribute.MAX_HEALTH);
        if (hpAttr != null) {
            removeModifier(player, Attribute.MAX_HEALTH, keyHp);
            double newMax = hpAttr.getValue();
            if (player.getHealth() > newMax) player.setHealth(newMax);
        }

        removeModifier(player, Attribute.SCALE,                    keyScale);
        removeModifier(player, Attribute.BLOCK_INTERACTION_RANGE,  keyReachBlock);
        removeModifier(player, Attribute.ENTITY_INTERACTION_RANGE, keyReachEntity);
        removeModifier(player, Attribute.STEP_HEIGHT,              keyStepHeight);
        removeModifier(player, Attribute.MOVEMENT_SPEED,           keySpeed);
        removeModifier(player, Attribute.JUMP_STRENGTH,            keyJump);
        removeModifier(player, Attribute.KNOCKBACK_RESISTANCE,     keyKbResist);
        removeModifier(player, Attribute.ATTACK_KNOCKBACK,         keyAtkKb);
        removeModifier(player, Attribute.SAFE_FALL_DISTANCE,       keySafeFall);
    }

    public boolean hasPower(UUID uuid) { return players.contains(uuid); }

    /** 0 = обычный, 1 = большой, 2 = огромный. */
    public int getSizeLevel(UUID uuid) { return sizeLevel.getOrDefault(uuid, 0); }

    // ── Вспомогательные ───────────────────────────────────────────────────

    private double cfgD(int level, String key, double def) {
        return plugin.getConfig().getDouble("giant.level" + level + "." + key, def);
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
        for (String s : plugin.getConfig().getStringList("giant-players")) {
            try { players.add(UUID.fromString(s)); } catch (Exception ignored) {}
        }
        plugin.getLogger().info("[Giant] Загружено игроков: " + players.size());
    }

    public void savePlayers() {
        plugin.getConfig().set("giant-players", players.stream().map(UUID::toString).toList());
        plugin.saveConfig();
    }
}