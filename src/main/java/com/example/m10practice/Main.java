
package com.example.m10practice;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

public class Main extends JavaPlugin {

@Override
public void onEnable() {
    getLogger().info("M10PracticeMenu 起動成功！");

    getServer().getPluginManager()
    .registerEvents(new MenuListener(this), this);
}

    @Override
    public boolean onCommand(
            CommandSender sender,
            Command command,
            String label,
            String[] args) {

        if (!command.getName().equalsIgnoreCase("m10menu")) {
            return false;
        }

        if (!(sender instanceof Player)) {
            sender.sendMessage("プレイヤー専用コマンドです");
            return true;
        }

        Player player = (Player) sender;

        Inventory menu = Bukkit.createInventory(
                null,
                9,
                "M10 Practice Menu"
        );

        ItemStack diamond = new ItemStack(Material.DIAMOND);
        ItemStack barrier = new ItemStack(Material.BARRIER);
        ItemStack redstone = new ItemStack(Material.REDSTONE);
        menu.setItem(4, diamond);
        menu.setItem(8, barrier);
        menu.setItem(6, redstone);
        
        player.openInventory(menu);

        return true;
    }
}