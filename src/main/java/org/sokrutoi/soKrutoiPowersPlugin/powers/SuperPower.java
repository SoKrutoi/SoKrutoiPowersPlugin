package org.sokrutoi.soKrutoiPowersPlugin.powers;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Абстрактный класс суперсилы.
 * Чтобы добавить новую суперсилу:
 *   1. Создай класс extends SuperPower
 *   2. Реализуй getName(), getDescription(), giveToPlayer()
 *   3. При необходимости переопредели register(), hasPlayer(), revoke(), isBound()
 *   4. Зарегистрируй в PowerManager
 */
public abstract class SuperPower {

    protected final JavaPlugin plugin;

    public SuperPower(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /** Уникальное имя силы — используется в /givepower */
    public abstract String getName();

    /** Описание для справки */
    public abstract String getDescription();

    /** Выдать суперсилу игроку (вызывается /givepower) */
    public abstract void giveToPlayer(Player player);

    /**
     * Привязанная ли сила (хранится по UUID, а не как предмет)?
     * Привязанные силы вытесняют друг друга при выдаче новой.
     * Предметные силы (тетрадь смерти) возвращают false.
     */
    public boolean isBound() {
        return true;
    }

    /**
     * Есть ли у игрока эта суперсила прямо сейчас?
     * Используется /whohaspowers.
     */
    public boolean hasPlayer(Player player) {
        return false;
    }

    /**
     * Отнять суперсилу у игрока — вызывается /clearpowers и при вытеснении.
     * Переопредели чтобы убрать эффекты, удалить из UUID-хранилища и т.п.
     * По умолчанию — ничего (для предметных сил достаточно изъятия предмета).
     */
    public void revoke(Player player) {}

    /**
     * Регистрация слушателей и логики силы.
     * Вызывается один раз при старте сервера.
     */
    public void register() {}
}