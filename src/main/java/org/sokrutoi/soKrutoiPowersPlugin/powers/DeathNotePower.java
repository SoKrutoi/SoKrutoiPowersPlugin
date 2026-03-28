package org.sokrutoi.soKrutoiPowersPlugin.powers;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;
import org.sokrutoi.soKrutoiPowersPlugin.SoKrutoiPowersPlugin;
import org.sokrutoi.soKrutoiPowersPlugin.listeners.DeathNoteBookListener;
import org.sokrutoi.soKrutoiPowersPlugin.listeners.DeathNoteJoinListener;

import java.util.Arrays;
import java.util.List;

public class DeathNotePower extends SuperPower {

    public DeathNotePower(SoKrutoiPowersPlugin plugin) {
        super(plugin);
    }

    @Override
    public String getName() { return "DeathNote"; }

    /** Тетрадь — предметная сила, не вытесняет привязанные. */
    @Override
    public boolean isBound() { return false; }

    @Override
    public String getDescription() {
        long delay = ((SoKrutoiPowersPlugin) plugin).getConfig()
                .getLong("death-delay-seconds", 40);
        return "Тетрадь Смерти — напиши ник и он умрёт через " + delay + " секунд";
    }

    @Override
    public void register() {
        SoKrutoiPowersPlugin main = (SoKrutoiPowersPlugin) plugin;
        plugin.getServer().getPluginManager().registerEvents(new DeathNoteBookListener(main), plugin);
        plugin.getServer().getPluginManager().registerEvents(new DeathNoteJoinListener(main), plugin);
    }

    @Override
    public boolean hasPlayer(Player player) {
        return containsDeathNote(player.getInventory())
                || containsDeathNote(player.getEnderChest());
    }

    private boolean containsDeathNote(Inventory inventory) {
        return Arrays.stream(inventory.getContents())
                .anyMatch(this::isDeathNote);
    }

    @Override
    public void giveToPlayer(Player player) {
        ItemStack book = createDeathNote();

        Location spawnLoc = player.getLocation().clone().add(0, 50, 0);
        Item item = player.getWorld().dropItem(spawnLoc, book);
        item.setVelocity(new Vector(
                (Math.random() - 0.5) * 0.05,
                -0.2,
                (Math.random() - 0.5) * 0.05
        ));
        item.setPickupDelay(10);

        player.sendMessage(Component.text("С неба упала ", NamedTextColor.GRAY)
                .append(Component.text("Тетрадь Смерти", NamedTextColor.GRAY))
                .append(Component.text("...", NamedTextColor.GRAY)));
    }

    public ItemStack createDeathNote() {
        SoKrutoiPowersPlugin main = (SoKrutoiPowersPlugin) plugin;
        long delay = main.getConfig().getLong("death-delay-seconds", 40);

        ItemStack book = new ItemStack(Material.WRITABLE_BOOK);
        BookMeta  meta = (BookMeta) book.getItemMeta();

        meta.displayName(Component.text("Тетрадь Смерти")
                .decoration(TextDecoration.ITALIC, false));

        meta.lore(List.of(
                Component.text("Напиши имя игрока...", NamedTextColor.DARK_GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("...и он умрёт через " + delay + " секунд.", NamedTextColor.DARK_GRAY)
                        .decoration(TextDecoration.ITALIC, false)
        ));

        meta.getPersistentDataContainer().set(
                main.getDeathNoteKey(),
                PersistentDataType.BYTE,
                (byte) 1
        );

        book.setItemMeta(meta);
        return book;
    }

    private boolean isDeathNote(ItemStack item) {
        if (item == null) return false;
        if (item.getType() != Material.WRITABLE_BOOK && item.getType() != Material.WRITTEN_BOOK) return false;
        if (!(item.getItemMeta() instanceof BookMeta meta)) return false;
        return meta.getPersistentDataContainer().has(
                ((SoKrutoiPowersPlugin) plugin).getDeathNoteKey(), PersistentDataType.BYTE
        );
    }
}