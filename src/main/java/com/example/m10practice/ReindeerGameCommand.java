package com.example.m10practice;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class ReindeerGameCommand implements CommandExecutor {
    private final ReindeerGameManager manager;

    public ReindeerGameCommand(ReindeerGameManager manager) {
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command,
                             String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("プレイヤー専用です。");
            return true;
        }
        if (!player.hasPermission("m10practice.admin")) {
            player.sendMessage("§c権限がありません。");
            return true;
        }
        if (args.length == 1) {
            switch (args[0].toLowerCase(java.util.Locale.ROOT)) {
                case "setcenter" -> manager.setCenter(player);
                case "setcorner1" -> manager.setCorner(player, 1);
                case "setcorner2" -> manager.setCorner(player, 2);
                default -> player.sendMessage("§e使い方: /startreindeergame [setcenter|setcorner1|setcorner2]");
            }
            return true;
        }
        if (args.length != 0) {
            player.sendMessage("§e使い方: /startreindeergame [setcenter|setcorner1|setcorner2]");
            return true;
        }
        manager.start(player);
        return true;
    }
}
