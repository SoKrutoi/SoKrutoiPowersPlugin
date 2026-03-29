package org.sokrutoi.soKrutoiPowersPlugin.powers;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Set;
import java.util.UUID;

/**
 * Абстрактный класс суперсилы.
 * Чтобы добавить новую суперсилу:
 *   1. Создай класс extends SuperPower
 *   2. Реализуй getName(), getDescription(), giveToPlayer()
 *   3. При необходимости переопредели остальные методы
 *   4. Зарегистрируй в PowerManager
 */
public abstract class SuperPower {

    protected final JavaPlugin plugin;

    public SuperPower(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /** Уникальное имя силы */
    public abstract String getName();

    /** Описание для справки */
    public abstract String getDescription();

    /** Выдать суперсилу онлайн-игроку */
    public abstract void giveToPlayer(Player player);

    /**
     * Выдать суперсилу офлайн-игроку по UUID.
     * Предметные силы возвращают false (нельзя бросить предмет офлайн-игроку).
     * UUID-based силы добавляют UUID в хранилище и возвращают true.
     */
    public boolean giveToOfflineUUID(UUID uuid) { return false; }

    /**
     * Привязанная ли сила (хранится по UUID, а не как предмет)?
     * Привязанные силы вытесняют друг друга при выдаче новой.
     */
    public boolean isBound() { return true; }

    /**
     * Есть ли у онлайн-игрока эта суперсила?
     */
    public boolean hasPlayer(Player player) { return false; }

    /**
     * UUID всех игроков (онлайн + офлайн) с этой силой.
     * Используется в /whohaspowers.
     * Предметные силы возвращают пустой сет.
     */
    public Set<UUID> getAllPlayerUUIDs() { return Set.of(); }

    /**
     * Отнять суперсилу у онлайн-игрока.
     */
    public void revoke(Player player) {}

    /**
     * Отнять суперсилу у офлайн-игрока (только UUID-хранилище, без снятия эффектов).
     */
    public void revokeOffline(UUID uuid) {}

    /**
     * Сбросить активное состояние силы у онлайн-игрока:
     * кулдауны, эффекты, размер — но не саму силу.
     * Используется в /resetpowers.
     */
    public void resetPlayer(Player player) {}

    /**
     * Регистрация слушателей. Вызывается один раз при старте.
     */
    public void register() {}
}