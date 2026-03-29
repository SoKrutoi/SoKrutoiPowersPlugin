package org.sokrutoi.soKrutoiPowersPlugin.powers;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.sokrutoi.soKrutoiPowersPlugin.SoKrutoiPowersPlugin;
import org.sokrutoi.soKrutoiPowersPlugin.listeners.ThiefListener;

import java.util.*;

public class ThiefPower extends SuperPower {

    private final Set<UUID>       players   = new HashSet<>();
    private final Map<UUID, Long> cooldowns = new HashMap<>();

    public ThiefPower(SoKrutoiPowersPlugin plugin) {
        super(plugin);
        loadPlayers();
    }

    @Override public String getName() { return "Thief"; }

    @Override
    public String getDescription() {
        long cd = plugin.getConfig().getLong("thief-cooldown-seconds", 420L);
        return "Вор — ПКМ по игроку крадёт случайный предмет (кд " + cd + " сек.)";
    }

    @Override
    public void register() {
        plugin.getServer().getPluginManager()
                .registerEvents(new ThiefListener((SoKrutoiPowersPlugin) plugin, this), plugin);
    }

    // --- Выдача ---

    @Override
    public void giveToPlayer(Player player) {
        if (!players.add(player.getUniqueId())) {
            player.sendMessage(Component.text("У тебя уже есть сила Вора.", NamedTextColor.YELLOW));
            return;
        }
        savePlayers();
        player.sendMessage(Component.text("Ты получил силу ", NamedTextColor.GRAY)
                .append(Component.text("Вора", NamedTextColor.GOLD))
                .append(Component.text(". Нажми ПКМ по игроку чтобы украсть предмет.", NamedTextColor.GRAY)));
    }

    @Override
    public boolean giveToOfflineUUID(UUID uuid) {
        if (!players.add(uuid)) return false;
        savePlayers();
        return true;
    }

    // --- Проверка ---

    @Override
    public boolean hasPlayer(Player player) { return players.contains(player.getUniqueId()); }

    @Override
    public Set<UUID> getAllPlayerUUIDs() { return Collections.unmodifiableSet(players); }

    public boolean hasPower(UUID uuid) { return players.contains(uuid); }

    // --- Отзыв ---

    @Override
    public void revoke(Player player) {
        UUID uuid = player.getUniqueId();
        if (!players.remove(uuid)) return;
        savePlayers();
        cooldowns.remove(uuid);
        player.sendMessage(Component.text("Твоя сила Вора была изъята.", NamedTextColor.DARK_RED));
    }

    @Override
    public void revokeOffline(UUID uuid) {
        if (!players.remove(uuid)) return;
        cooldowns.remove(uuid);
        savePlayers();
    }

    // --- Сброс ---

    @Override
    public void resetPlayer(Player player) {
        cooldowns.remove(player.getUniqueId());
        player.sendActionBar(Component.text("◎ Кулдаун кражи сброшен", NamedTextColor.GRAY));
    }

    // --- Логика кражи ---

    public boolean trySteal(Player thief, Player victim) {
        UUID uuid = thief.getUniqueId();
        long now  = System.currentTimeMillis();

        long cdEnd = cooldowns.getOrDefault(uuid, 0L);
        if (now < cdEnd) {
            long secLeft = (long) Math.ceil((cdEnd - now) / 1000.0);
            thief.sendActionBar(Component.text("⏳ Кулдаун: " + formatCooldown(secLeft), NamedTextColor.GRAY));
            return false;
        }

        PlayerInventory inv         = victim.getInventory();
        List<int[]>     filledSlots = new ArrayList<>();

        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack item = inv.getItem(i);
            if (item != null && !item.getType().isAir()) filledSlots.add(new int[]{0, i});
        }
        ItemStack[] armor = inv.getArmorContents();
        for (int i = 0; i < armor.length; i++) {
            if (armor[i] != null && !armor[i].getType().isAir()) filledSlots.add(new int[]{1, i});
        }
        ItemStack offhand = inv.getItemInOffHand();
        if (!offhand.getType().isAir()) filledSlots.add(new int[]{2, 0});

        if (filledSlots.isEmpty()) {
            thief.sendMessage(Component.text("У " + victim.getName() + " нечего красть.", NamedTextColor.GRAY));
            return false;
        }

        // Шанс провала = ceil(пустые ячейки основного инвентаря * 1.5) процентов
        int emptySlots = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack s = inv.getItem(i);
            if (s == null || s.getType().isAir()) emptySlots++;
        }
        int failChance = (int) Math.ceil(emptySlots * 1.5);
        if (failChance > 0 && new Random().nextInt(100) < failChance) {
            long fullCdMs = plugin.getConfig().getLong("thief-cooldown-seconds", 420L) * 1000L;
            long failCdMs = Math.max(30_000L, fullCdMs / 3);
            cooldowns.put(uuid, now + failCdMs);
            long failSec = failCdMs / 1000;
            thief.sendActionBar(Component.text(
                    "✗ Кража провалилась! Повтор через " + formatCooldown(failSec), NamedTextColor.RED));
            return false;
        }

        int[]     chosen = filledSlots.get(new Random().nextInt(filledSlots.size()));
        ItemStack stolen;

        switch (chosen[0]) {
            case 0 -> { stolen = inv.getItem(chosen[1]).clone();  inv.setItem(chosen[1], null); }
            case 1 -> { stolen = armor[chosen[1]].clone(); armor[chosen[1]] = null; inv.setArmorContents(armor); }
            default -> { stolen = offhand.clone(); inv.setItemInOffHand(null); }
        }

        long cdMs = plugin.getConfig().getLong("thief-cooldown-seconds", 420L) * 1000L;
        cooldowns.put(uuid, now + cdMs);

        Map<Integer, ItemStack> leftover = thief.getInventory().addItem(stolen);
        if (!leftover.isEmpty()) {
            leftover.values().forEach(item -> thief.getWorld().dropItemNaturally(thief.getLocation(), item));
            thief.sendMessage(Component.text("Украдено (нет места — упало рядом): ", NamedTextColor.GOLD)
                    .append(stolen.displayName()));
        } else {
            thief.sendMessage(Component.text("Ты украл у " + victim.getName() + ": ", NamedTextColor.GOLD)
                    .append(stolen.displayName()));
        }

        victim.sendMessage(Component.text(thief.getName() + " украл у тебя предмет!", NamedTextColor.RED));
        thief.sendActionBar(Component.text(
                "✓ Украдено! Следующая кража через " + formatCooldown(cdMs / 1000), NamedTextColor.GOLD));

        return true;
    }

    private String formatCooldown(long totalSec) {
        if (totalSec < 60) return totalSec + " сек.";
        long min = totalSec / 60;
        long sec = totalSec % 60;
        return min + " мин. " + (sec > 0 ? sec + " сек." : "");
    }

    // --- Персистентность ---

    private void loadPlayers() {
        for (String s : plugin.getConfig().getStringList("thief-players")) {
            try { players.add(UUID.fromString(s)); } catch (Exception ignored) {}
        }
        plugin.getLogger().info("[Thief] Загружено игроков: " + players.size());
    }

    public void savePlayers() {
        plugin.getConfig().set("thief-players", players.stream().map(UUID::toString).toList());
        plugin.saveConfig();
    }
}