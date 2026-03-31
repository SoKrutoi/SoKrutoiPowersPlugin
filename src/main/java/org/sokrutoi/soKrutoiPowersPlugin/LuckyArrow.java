package org.sokrutoi.soKrutoiPowersPlugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.sokrutoi.soKrutoiPowersPlugin.powers.SuperPower;

import java.util.*;

public class LuckyArrow implements Listener {

    // Длительность зажатия в тиках (5 секунд = 100 тиков)
    private static final int CHARGE_TICKS   = 100;
    // Как часто обновляем прогресс-бар (каждые N тиков)
    private static final int UPDATE_INTERVAL = 2;

    private final SoKrutoiPowersPlugin plugin;
    private final NamespacedKey        itemKey;

    // uuid → количество тиков прошедших с начала зарядки
    private final Map<UUID, Integer> chargingTicks = new HashMap<>();
    // uuid → id задачи таймера
    private final Map<UUID, Integer> tasks         = new HashMap<>();
    // uuid → тик последнего нажатия ПКМ (для определения удержания)
    private final Map<UUID, Integer> lastRightClick = new HashMap<>();

    public LuckyArrow(SoKrutoiPowersPlugin plugin) {
        this.plugin = plugin;
        this.itemKey = new NamespacedKey(plugin, "lucky_arrow");
        registerCraft();
    }

    // ── Создание предмета ─────────────────────────────────────────────────

    public ItemStack createItem() {
        ItemStack item = new ItemStack(Material.SPECTRAL_ARROW);
        ItemMeta  meta = item.getItemMeta();

        meta.displayName(Component.text("✦ Стрела Судьбы", NamedTextColor.GOLD)
                .decoration(TextDecoration.ITALIC, false)
                .decoration(TextDecoration.BOLD, true));

        meta.lore(List.of(
                Component.text("Удерживай ПКМ 5 секунд,", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("удерживая стрелу в руке,", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("чтобы получить случайную суперсилу.", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.empty(),
                Component.text("⚠ Не держи лук/арбалет в левой руке!", NamedTextColor.DARK_RED)
                        .decoration(TextDecoration.ITALIC, false)
        ));

        meta.getPersistentDataContainer().set(itemKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    public boolean isLuckyArrow(ItemStack item) {
        if (item == null || item.getType() != Material.SPECTRAL_ARROW) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        return meta.getPersistentDataContainer().has(itemKey, PersistentDataType.BYTE);
    }

    // ── Крафт ─────────────────────────────────────────────────────────────
    // Рецепт (верстак 3x3):
    //  N  BR  N
    //  GI  A  D
    //  N  GD  N
    // BR = blaze_rod, GI = gold_ingot, A = spectral_arrow, D = diamond, GD = glowstone_dust

    private void registerCraft() {
        ShapelessRecipe recipe = new ShapelessRecipe(itemKey, createItem());
        recipe.addIngredient(Material.ARROW);
        recipe.addIngredient(Material.BLAZE_ROD);
        recipe.addIngredient(Material.GOLD_INGOT);
        recipe.addIngredient(Material.DIAMOND);
        recipe.addIngredient(Material.GLOWSTONE_DUST);
        plugin.getServer().addRecipe(recipe);
        plugin.getLogger().info("[LuckyArrow] Крафт зарегистрирован.");
    }

    // ── Обработчик ПКМ — старт/обновление зарядки ────────────────────────

    @EventHandler(ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR
                && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        if (!isLuckyArrow(player.getInventory().getItemInMainHand())) return;

        // Проверяем офф-хенд — лук или арбалет заряжают себя через ПКМ
        Material offhand = player.getInventory().getItemInOffHand().getType();
        if (offhand == Material.BOW || offhand == Material.CROSSBOW) {
            player.sendActionBar(Component.text(
                    "⚠ Убери лук/арбалет из левой руки!", NamedTextColor.RED));
            return;
        }

        // Запоминаем тик нажатия ПКМ — таймер использует это чтобы понять,
        // что кнопка всё ещё удерживается (клиент шлёт повторные interact-пакеты
        // пока кнопка зажата)
        lastRightClick.put(player.getUniqueId(), plugin.getServer().getCurrentTick());

        // Запускаем зарядку только если она ещё не идёт
        UUID uuid = player.getUniqueId();
        if (!chargingTicks.containsKey(uuid)) {
            startCharging(player);
        }
    }

    // ── Смена слота — отменяем зарядку ТОЛЬКО если стрела была в руке ─────

    @EventHandler
    public void onItemHeld(PlayerItemHeldEvent event) {
        Player player   = event.getPlayer();
        UUID   uuid     = player.getUniqueId();

        // Если зарядка не идёт — ничего не делаем (нет сообщения "прервана")
        if (!chargingTicks.containsKey(uuid)) return;

        cancelCharging(player, true);
    }

    // ── Логика зарядки ────────────────────────────────────────────────────

    private void startCharging(Player player) {
        UUID uuid = player.getUniqueId();
        chargingTicks.put(uuid, 0);

        int taskId = plugin.getServer().getScheduler().runTaskTimer(plugin, new Runnable() {
            @Override
            public void run() {
                Player p = plugin.getServer().getPlayer(uuid);
                if (p == null || !p.isOnline()) {
                    forceCancel(uuid);
                    return;
                }

                // Стрела пропала из руки
                if (!isLuckyArrow(p.getInventory().getItemInMainHand())) {
                    cancelCharging(p, true);
                    return;
                }

                // Лук/арбалет появился в офф-хенде
                Material offhand = p.getInventory().getItemInOffHand().getType();
                if (offhand == Material.BOW || offhand == Material.CROSSBOW) {
                    cancelCharging(p, true);
                    p.sendActionBar(Component.text(
                            "⚠ Убери лук/арбалет из левой руки!", NamedTextColor.RED));
                    return;
                }

                // Проверяем, что ПКМ всё ещё удерживается:
                // клиент присылает interact-пакеты примерно каждые 4 тика пока зажата кнопка.
                // Если последний пакет был более 10 тиков назад — кнопку отпустили.
                int currentTick = plugin.getServer().getCurrentTick();
                Integer lastClick = lastRightClick.get(uuid);
                if (lastClick == null || currentTick - lastClick > 3) {
                    cancelCharging(p, true);
                    return;
                }

                int elapsed = chargingTicks.merge(uuid, UPDATE_INTERVAL, Integer::sum);
                float progress = (float) elapsed / CHARGE_TICKS;

                if (elapsed >= CHARGE_TICKS) {
                    cancelCharging(p, false);
                    activateArrow(p);
                    return;
                }

                showProgress(p, progress);
                spawnParticles(p);
            }
        }, 0L, UPDATE_INTERVAL).getTaskId();

        tasks.put(uuid, taskId);
    }

    private void cancelCharging(Player player, boolean notify) {
        UUID uuid = player.getUniqueId();
        forceCancel(uuid);
        lastRightClick.remove(uuid);
        if (notify) {
            player.sendActionBar(Component.text("✗ Зарядка прервана", NamedTextColor.RED));
        }
    }

    private void forceCancel(UUID uuid) {
        chargingTicks.remove(uuid);
        Integer taskId = tasks.remove(uuid);
        if (taskId != null) plugin.getServer().getScheduler().cancelTask(taskId);
    }

    // ── Активация ─────────────────────────────────────────────────────────

    private void activateArrow(Player player) {
        // Собираем пул: только привязанные силы (isBound = true), не предметные
        List<SuperPower> pool = new ArrayList<>();
        for (SuperPower power : plugin.getPowerManager().getAll()) {
            if (power.isBound()) pool.add(power);
        }

        if (pool.isEmpty()) {
            player.sendMessage(Component.text("Нет доступных суперсил!", NamedTextColor.RED));
            return;
        }

        // Отзываем все текущие привязанные силы
        for (SuperPower power : plugin.getPowerManager().getAll()) {
            if (power.isBound() && power.hasPlayer(player)) {
                power.revoke(player);
            }
        }

        // Выдаём случайную силу
        SuperPower chosen = pool.get(new Random().nextInt(pool.size()));
        chosen.giveToPlayer(player);

        // Эффекты активации
        player.addPotionEffect(new PotionEffect(
                PotionEffectType.GLOWING, 20 * 10, 0, false, false));
        player.getWorld().playSound(player.getLocation(),
                Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
        player.getWorld().spawnParticle(
                Particle.TOTEM_OF_UNDYING,
                player.getLocation().add(0, 1, 0),
                60, 0.5, 0.8, 0.5, 0.3);

        // Забираем одну стрелу из руки
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getAmount() > 1) {
            hand.setAmount(hand.getAmount() - 1);
        } else {
            player.getInventory().setItemInMainHand(null);
        }

        player.sendMessage(Component.text("✦ Стрела Судьбы активирована! Сила: ", NamedTextColor.GOLD)
                .append(Component.text(chosen.getName(), NamedTextColor.YELLOW)));
        player.sendActionBar(Component.text(
                "✦ Получена сила: " + chosen.getName() + "!", NamedTextColor.GOLD));
    }

    // ── Визуалы во время зарядки ──────────────────────────────────────────

    private void showProgress(Player player, float progress) {
        int filled  = (int) (progress * 20);
        int empty   = 20 - filled;
        int seconds = (int) Math.ceil((CHARGE_TICKS - progress * CHARGE_TICKS) / 20.0);

        player.sendActionBar(
                Component.text("✦ Зарядка: [", NamedTextColor.GRAY)
                        .append(Component.text("█".repeat(filled), NamedTextColor.GOLD))
                        .append(Component.text("░".repeat(empty), NamedTextColor.DARK_GRAY))
                        .append(Component.text("] " + seconds + "с  ⚠ не отпускай ПКМ!", NamedTextColor.GRAY))
        );
    }

    private void spawnParticles(Player player) {
        Location loc = player.getLocation().add(0, 1, 0);
        player.getWorld().spawnParticle(
                Particle.ELECTRIC_SPARK, loc, 5, 0.3, 0.4, 0.3, 0.05);
        player.getWorld().spawnParticle(
                Particle.END_ROD, loc, 3, 0.2, 0.3, 0.2, 0.05);
        player.getWorld().playSound(loc,
                Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.3f, 0.8f + (float) Math.random() * 0.4f);
    }
}