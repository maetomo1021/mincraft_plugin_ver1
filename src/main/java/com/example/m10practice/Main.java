package com.example.m10practice;

import org.bukkit.plugin.java.JavaPlugin;
import java.util.Objects;

public class Main extends JavaPlugin {

    @Override
    public void onEnable() {
        getLogger().info("M10PracticeMenu 起動成功！");

        Objects.requireNonNull(getCommand("m10menu"))
            .setExecutor((sender, command, label, args) -> {

                sender.sendMessage("§aテスト成功！");

                return true;
            });
    }
}