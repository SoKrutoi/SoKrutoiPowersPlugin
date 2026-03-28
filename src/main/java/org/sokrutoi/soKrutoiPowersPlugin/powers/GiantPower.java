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

    // Размеры: 0 — обычный, 1 — большой, 2 — гигантский
    // scale, maxHealth multiplier, reach multiplier
    private static final double[] SCALE       = {1.0,  2.5,  5.0};
    private static final double[] HP_MULT     = {1.0,  2.5,  5.0};  // от базового 20 hp
    private static final double[] REACH_ADD   = {0.0,  3.0,  7.5};  // добавка к дальности (5.0 базово)

    // NamespacedKey для модификаторов — используем плагин как namespace
    private NamespacedKey keyScale;
    private NamespacedKey keyHp;
    private NamespacedKey keyReachBlock;
    private NamespacedKey keyReachEntity;

    /** UUID игрока → текущий уровень (1 или 2); отсутствие = уровень 0 (обычный) */
    private final Map<UUID, Integer> sizeLevel = new HashMap<>();
    /** UUID игроков с силой */
    private final Set<UUID> players = new HashSet<>();

    public GiantPower(SoKrutoiPowersPlugin plugin) {
        super(plugin);
        keyScale       = new NamespacedKey(plugin, "giant_scale");
        keyHp          = new NamespacedKey(plugin, "giant_health");
        keyReachBlock  = new NamespacedKey(plugin, "giant_reach_block");
        keyReachEntity = new NamespacedKey(plugin, "giant_reach_entity");
        loadPlayers();
    }

    @Override
    public String getName() { return "Giant"; }

    @Override
    public String getDescription() {
        return "Гигант — Shift ×1: большой, Shift ×2: огромный, Shift ×3: обычный";
    }

    @Override
    public void register() {
        plugin.getServer().getPluginManager()
                .registerEvents(new GiantListener((SoKrutoiPowersPlugin) plugin, this), plugin);
    }

    @Override
    public void giveToPlayer(Player player) {
        UUID uuid = player.getUniqueId();
        if (players.contains(uuid)) {
            player.sendMessage(Component.text(
                    "У тебя уже есть сила Гиганта.", NamedTextColor.YELLOW));
            return;
        }
        players.add(uuid);
        savePlayers();
        player.sendMessage(Component.text("Ты получил силу ", NamedTextColor.GRAY)
                .append(Component.text("Гиганта", NamedTextColor.GREEN))
                .append(Component.text(
                        ". Shift ×1 = большой, ×2 = огромный, ×3 = обычный.",
                        NamedTextColor.GRAY)));
    }

    @Override
    public boolean hasPlayer(Player player) {
        return players.contains(player.getUniqueId());
    }

    @Override
    public void revoke(Player player) {
        UUID uuid = player.getUniqueId();
        if (!players.remove(uuid)) return;
        savePlayers();
        resetSize(player);
        sizeLevel.remove(uuid);
        player.sendMessage(Component.text(
                "Твоя сила Гиганта была изъята.", NamedTextColor.DARK_RED));
    }

    /** Вызывается из GiantListener при нажатии Shift */
    public void cycleSize(Player player) {
        UUID uuid = player.getUniqueId();
        int current = sizeLevel.getOrDefault(uuid, 0);
        int next = (current + 1) % 3;   // 0→1→2→0

        if (next == 0) {
            resetSize(player);
            sizeLevel.remove(uuid);
            player.sendActionBar(Component.text(
                    "◎ Обычный размер", NamedTextColor.GRAY));
        } else {
            applySize(player, next);
            sizeLevel.put(uuid, next);
            String label = next == 1 ? "Большой" : "Огромный";
            NamedTextColor color = next == 1 ? NamedTextColor.YELLOW : NamedTextColor.GREEN;
            player.sendActionBar(Component.text("◉ " + label, color));
        }
    }

    private void applySize(Player player, int level) {
        double scale    = SCALE[level];
        double hpMult   = HP_MULT[level];
        double reachAdd = REACH_ADD[level];

        // --- Scale ---
        setModifier(player, Attribute.SCALE,
                keyScale, scale - 1.0,
                AttributeModifier.Operation.ADD_NUMBER);

        // --- Max Health ---
        double baseHp = getBase(player, Attribute.MAX_HEALTH, 20.0);
        setModifier(player, Attribute.MAX_HEALTH,
                keyHp, baseHp * (hpMult - 1.0),
                AttributeModifier.Operation.ADD_NUMBER);
        // Восполняем до нового максимума
        AttributeInstance hpAttr = player.getAttribute(Attribute.MAX_HEALTH);
        if (hpAttr != null) player.setHealth(hpAttr.getValue());

        // --- Block reach (placement) ---
        setModifier(player, Attribute.BLOCK_INTERACTION_RANGE,
                keyReachBlock, reachAdd,
                AttributeModifier.Operation.ADD_NUMBER);

        // --- Entity reach (attack) ---
        setModifier(player, Attribute.ENTITY_INTERACTION_RANGE,
                keyReachEntity, reachAdd,
                AttributeModifier.Operation.ADD_NUMBER);
    }

    private void resetSize(Player player) {
        removeModifier(player, Attribute.SCALE,                    keyScale);
        removeModifier(player, Attribute.BLOCK_INTERACTION_RANGE,  keyReachBlock);
        removeModifier(player, Attribute.ENTITY_INTERACTION_RANGE, keyReachEntity);

        // HP — сначала снижаем хп до нового (меньшего) максимума чтобы не зависнуть выше него
        AttributeInstance hpAttr = player.getAttribute(Attribute.MAX_HEALTH);
        if (hpAttr != null) {
            removeModifier(player, Attribute.MAX_HEALTH, keyHp);
            double newMax = hpAttr.getValue();
            if (player.getHealth() > newMax) player.setHealth(newMax);
        }
    }

    // ── Вспомогательные методы ──────────────────────────────────────────

    private void setModifier(Player player, Attribute attribute,
                             NamespacedKey key, double amount,
                             AttributeModifier.Operation operation) {
        AttributeInstance inst = player.getAttribute(attribute);
        if (inst == null) return;

        // Убираем старый модификатор с тем же ключом, если есть
        inst.getModifiers().stream()
                .filter(m -> m.getKey().equals(key))
                .findFirst()
                .ifPresent(inst::removeModifier);

        if (amount != 0.0) {
            inst.addModifier(new AttributeModifier(
                    key, amount, operation, EquipmentSlotGroup.ANY));
        }
    }

    private void removeModifier(Player player, Attribute attribute, NamespacedKey key) {
        AttributeInstance inst = player.getAttribute(attribute);
        if (inst == null) return;
        inst.getModifiers().stream()
                .filter(m -> m.getKey().equals(key))
                .findFirst()
                .ifPresent(inst::removeModifier);
    }

    private double getBase(Player player, Attribute attribute, double fallback) {
        AttributeInstance inst = player.getAttribute(attribute);
        return inst != null ? inst.getBaseValue() : fallback;
    }

    public boolean hasPower(UUID uuid) {
        return players.contains(uuid);
    }

    // --- Персистентность ---

    private void loadPlayers() {
        List<String> list = plugin.getConfig().getStringList("giant-players");
        for (String s : list) {
            try { players.add(UUID.fromString(s)); }
            catch (Exception ignored) {}
        }
        plugin.getLogger().info("[Giant] Загружено игроков: " + players.size());
    }

    public void savePlayers() {
        plugin.getConfig().set("giant-players",
                players.stream().map(UUID::toString).toList());
        plugin.saveConfig();
    }
}