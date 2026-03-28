package org.sokrutoi.soKrutoiPowersPlugin.listeners;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerEditBookEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.persistence.PersistentDataType;
import org.sokrutoi.soKrutoiPowersPlugin.SoKrutoiPowersPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class DeathNoteBookListener implements Listener {

    private final SoKrutoiPowersPlugin plugin;

    public DeathNoteBookListener(SoKrutoiPowersPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onBookEdit(PlayerEditBookEvent event) {
        Player    player = event.getPlayer();
        ItemStack main   = player.getInventory().getItemInMainHand();
        ItemStack off    = player.getInventory().getItemInOffHand();
        if (!isDeathNote(main) && !isDeathNote(off)) return;

        // Запрет подписывания — нельзя превратить в Written Book
        if (event.isSigning()) {
            event.setCancelled(true);
            player.sendMessage(Component.text(
                    "Тетрадь Смерти нельзя подписать!", NamedTextColor.DARK_RED
            ));
            return;
        }

        BookMeta newMeta    = event.getNewBookMeta();
        boolean  scheduled  = false;

        // Сканируем страницы и ставим в очередь смерти
        for (Component page : newMeta.pages()) {
            String text = PlainTextComponentSerializer.plainText().serialize(page);
            for (String line : text.split("\n")) {
                String name = line.trim();
                if (name.isEmpty()) continue;
                if (scheduleDeathByName(name, player.getUniqueId())) {
                    scheduled = true;
                }
            }
        }

        // Очищаем страницы в тетради после записи имён
        // Используем setNewBookMeta — это работает прямо в событии, без runTaskLater
        if (scheduled) {
            BookMeta cleared = newMeta.clone();
            cleared.pages(List.of()); // убираем все страницы
            event.setNewBookMeta(cleared);
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player target     = event.getEntity();
        UUID   targetUUID = target.getUniqueId();

        // Только смерти от тетради
        if (!plugin.getDeathNoteKills().contains(targetUUID)) return;
        plugin.getDeathNoteKills().remove(targetUUID);

        event.deathMessage(Component.text(target.getName() + " умер"));

        // Зачёркиваем ник в тетради writer'а
        UUID writerUUID = plugin.getWriterMap().get(targetUUID);
        if (writerUUID != null) {
            Player writer = Bukkit.getPlayer(writerUUID);
            if (writer != null && writer.isOnline()) {
                updateDeathNoteBook(writer, target.getName());
            }
        }
    }

    /**
     * @return true если смерть была успешно запланирована
     */
    private boolean scheduleDeathByName(String name, UUID writerUUID) {
        Player target = Bukkit.getPlayerExact(name);
        if (target != null) {
            plugin.scheduleDeath(target.getUniqueId(), target.getName(), writerUUID);
            return true;
        }
        @SuppressWarnings("deprecation")
        var offline = Bukkit.getOfflinePlayer(name);
        if (offline.hasPlayedBefore() && offline.getUniqueId() != null) {
            plugin.scheduleDeath(offline.getUniqueId(), name, writerUUID);
            return true;
        }
        return false;
    }

    // Зачёркиваем имя цели в тетради (если страницы ещё есть)
    private void updateDeathNoteBook(Player writer, String targetName) {
        for (int i = 0; i < writer.getInventory().getSize(); i++) {
            ItemStack item = writer.getInventory().getItem(i);
            if (!isDeathNote(item)) continue;

            BookMeta         meta     = (BookMeta) item.getItemMeta();
            List<Component>  newPages = new ArrayList<>();

            for (Component page : meta.pages()) {
                String plain = PlainTextComponentSerializer.plainText().serialize(page);
                newPages.add(buildPageWithStrikethrough(plain, targetName));
            }

            meta.pages(newPages);
            item.setItemMeta(meta);
            break;
        }
    }

    private Component buildPageWithStrikethrough(String plainText, String targetName) {
        String[]  lines = plainText.split("\n", -1);
        Component page  = Component.empty();
        for (int i = 0; i < lines.length; i++) {
            String    line     = lines[i];
            Component lineComp = line.trim().equalsIgnoreCase(targetName)
                    ? Component.text(line)
                    .color(NamedTextColor.DARK_RED)
                    .decorate(TextDecoration.STRIKETHROUGH)
                    : Component.text(line);
            page = i == 0 ? lineComp : page.append(Component.newline()).append(lineComp);
        }
        return page;
    }

    /**
     * Проверяем по PDC-тегу — переименование через наковальню не сработает.
     */
    private boolean isDeathNote(ItemStack item) {
        if (item == null) return false;
        if (item.getType() != Material.WRITABLE_BOOK && item.getType() != Material.WRITTEN_BOOK) return false;
        if (!(item.getItemMeta() instanceof BookMeta meta)) return false;
        return meta.getPersistentDataContainer().has(
                plugin.getDeathNoteKey(), PersistentDataType.BYTE
        );
    }
}