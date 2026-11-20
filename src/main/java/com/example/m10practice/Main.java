package com.example.m10practice;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

/** M10PracticeMenu の起動・終了とコマンド登録を担当する。 */
public class Main extends JavaPlugin implements CommandExecutor {

    private ReindeerGameManager reindeerGameManager;
    private GorillaBossManager gorillaBossManager;

    @Override
    public void onEnable() {
        getLogger().info("M10PracticeMenu 起動成功！");
        registerMenu();
        registerModelExamples();
        registerGorilla();
        registerReindeer();
    }

    private void registerMenu() {
        registerListener(new MenuListener(this));
        registerCommand("m10menu", this);
    }

    private void registerModelExamples() {
        registerListener(new MiniGolemListener(this));
        registerCommand("spawnminigolem", new MiniGolemCommand());

        registerListener(new SantaGirlListener(this));
        registerCommand("spawnsantagirl", new SantaGirlCommand());
    }

    private void registerGorilla() {
        gorillaBossManager = new GorillaBossManager(this);
        GorillaCommand gorillaCommand = new GorillaCommand(this, gorillaBossManager);
        GorillaListener gorillaListener = new GorillaListener(this, gorillaCommand);

        registerCommand("spawngorilla", gorillaCommand);
        registerCommand("removegorilla", new GorillaRemoveCommand(this, gorillaBossManager));
        registerListener(gorillaListener);
        registerListener(new GorillaBossListener(gorillaBossManager));

        // AIが自分の周期タスクを登録する。攻撃処理の設定は変更しない。
        new GorillaAutoAI(this, gorillaCommand, gorillaListener);
    }

    private void registerReindeer() {
        registerCommand("spawnreindeer", new ReindeerCommand(this));

        reindeerGameManager = new ReindeerGameManager(this);
        registerCommand("startreindeergame", new ReindeerGameCommand(reindeerGameManager));
        registerListener(new ReindeerGameListener(reindeerGameManager));
    }

    private void registerCommand(String name, CommandExecutor executor) {
        var pluginCommand = getCommand(name);
        if (pluginCommand == null) {
            throw new IllegalStateException("plugin.yml にコマンド定義がありません: " + name);
        }
        pluginCommand.setExecutor(executor);
    }

    private void registerListener(org.bukkit.event.Listener listener) {
        getServer().getPluginManager().registerEvents(listener, this);
    }

    @Override
    public void onDisable() {
        if (gorillaBossManager != null) {
            gorillaBossManager.shutdown();
        }
        if (reindeerGameManager != null) {
            reindeerGameManager.stop(false);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command,
                             String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("m10menu")) {
            return false;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage("プレイヤー専用コマンドです");
            return true;
        }

        Inventory menu = Bukkit.createInventory(null, 9, "M10 Practice Menu");
        menu.setItem(4, new ItemStack(Material.DIAMOND));
        menu.setItem(8, new ItemStack(Material.BARRIER));
        menu.setItem(6, new ItemStack(Material.REDSTONE));
        player.openInventory(menu);
        return true;
    }
}
