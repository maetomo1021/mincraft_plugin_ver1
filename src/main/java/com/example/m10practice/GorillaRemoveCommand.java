package com.example.m10practice;

import com.ticxo.modelengine.api.ModelEngineAPI;
import com.ticxo.modelengine.api.model.ModeledEntity;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Pig;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** 暴走ゴリラの見た目・BossBar・ベースのブタをまとめて削除するテストコマンド。 */
public final class GorillaRemoveCommand implements CommandExecutor {
    private final JavaPlugin plugin;
    private final GorillaBossManager manager;

    public GorillaRemoveCommand(JavaPlugin plugin, GorillaBossManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // タグ付きブタに加えて、管理中のUUIDも対象にする
        Set<UUID> ids = new HashSet<>(manager.getTrackedIds());
        for (World world : Bukkit.getWorlds()) {
            for (Pig pig : world.getEntitiesByClass(Pig.class)) {
                if (pig.getScoreboardTags().contains("m10_gorilla_test")) {
                    ids.add(pig.getUniqueId());
                }
            }
        }

        int success = 0;
        for (UUID uuid : ids) {
            if (removeGorilla(uuid, manager, plugin)) success++;
        }
        sender.sendMessage("§a暴走ゴリラの削除処理: " + success + " / " + ids.size() + " 体");
        if (ids.isEmpty()) {
            sender.sendMessage("§e対象が見つかりません。既に/killで土台を消した場合は、サーバー再起動と再ログインで残像を確認してください。");
        }
        return true;
    }

    public static boolean removeGorilla(UUID uuid, GorillaBossManager manager, JavaPlugin plugin) {
        // まずプレイヤーのBossBarを確実に解除
        manager.remove(uuid);
        boolean worked = false;
        try {
            // ModelEngineの表示エンティティを管理から解除し、全ボーンを破棄
            ModeledEntity modeled = ModelEngineAPI.removeModeledEntity(uuid);
            if (modeled != null) {
                modeled.destroy();
                worked = true;
            }
        } catch (Exception ex) {
            plugin.getLogger().warning("ゴリラModelEngine解除エラー " + uuid + ": " + ex.getMessage());
        }
        Entity base = Bukkit.getEntity(uuid);
        if (base != null) {
            base.remove();
            worked = true;
        }
        return worked;
    }
}
