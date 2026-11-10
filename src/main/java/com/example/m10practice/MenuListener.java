
package com.example.m10practice;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

public class MenuListener implements Listener {

    private final JavaPlugin plugin;

    public MenuListener(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {

        plugin.getLogger().info("クリックイベント検知！");

        if (!event.getView().getTitle().equals("M10 Practice Menu")) {
            return;
        }

        event.setCancelled(true);

        if (event.getRawSlot() >= 9) {
            return;
        }

        ItemStack item = event.getCurrentItem();

        if (item == null || item.getType() == Material.AIR) {
            return;
        }

        Player player = (Player) event.getWhoClicked();

        if (item.getType() == Material.DIAMOND) {
            player.sendMessage("§aテスト成功！");
        }

        if (item.getType() == Material.REDSTONE) {
            player.sendMessage("§cレッドストーンを選択！");
        }

        if (item.getType() == Material.BARRIER) {
            Bukkit.getScheduler().runTask(plugin, (Runnable) player::closeInventory);
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {

        plugin.getLogger().info("ドラッグイベント検知！");

        if (event.getView().getTitle().equals("M10 Practice Menu")) {
            event.setCancelled(true);
        }
    }
}
