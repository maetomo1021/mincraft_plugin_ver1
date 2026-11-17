
package com.example.m10practice;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

public class Main extends JavaPlugin {

    // トナカイ射的ゲーム管理
    private ReindeerGameManager reindeerGameManager;

    @Override
    public void onEnable() {

        getLogger().info("M10PracticeMenu 起動成功！");

        // メニュークリックイベント
        getServer().getPluginManager().registerEvents(
            new MenuListener(this), this
        );

        // MiniGolemクリックイベント
        getServer().getPluginManager().registerEvents(
            new MiniGolemListener(this), this
        );

        // MiniGolem召喚
        getCommand("spawnminigolem")
            .setExecutor(new MiniGolemCommand());

        // SantaGirl召喚
        getCommand("spawnsantagirl")
            .setExecutor(new SantaGirlCommand());

        // SantaGirlクリックイベント
        getServer().getPluginManager().registerEvents(
            new SantaGirlListener(this), this
        );

        // トナカイ召喚・逃走AI
        getCommand("spawnreindeer")
            .setExecutor(new ReindeerCommand(this));

        // トナカイ射的ゲーム
        reindeerGameManager = new ReindeerGameManager(this);

        getCommand("startreindeergame")
            .setExecutor(
                new ReindeerGameCommand(reindeerGameManager)
            );

        // トナカイ射的ゲームのイベント
        getServer().getPluginManager().registerEvents(
            new ReindeerGameListener(reindeerGameManager),
            this
        );

    } // onEnable終了

    @Override
    public void onDisable() {

        // サーバー停止時にゲーム用MOBを削除
        if (reindeerGameManager != null) {
            reindeerGameManager.stop(false);
        }

    } // onDisable終了

    @Override
    public boolean onCommand(
            CommandSender sender,
            Command command,
            String label,
            String[] args) {

        if (!command.getName().equalsIgnoreCase("m10menu")) {
            return false;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage("プレイヤー専用コマンドです");
            return true;
        }

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

} // Mainクラス終了
