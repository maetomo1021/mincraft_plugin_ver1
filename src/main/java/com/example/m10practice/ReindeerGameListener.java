
package com.example.m10practice;

import org.bukkit.entity.Pig;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class ReindeerGameListener implements Listener {

    private final ReindeerGameManager manager;

    public ReindeerGameListener(ReindeerGameManager manager) {
        this.manager = manager;
    }

    @EventHandler(ignoreCancelled = true)
    public void onSnowballHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Snowball snowball)) {
            return;
        }

        if (!(snowball.getShooter() instanceof Player player)) {
            return;
        }

        if (!(event.getHitEntity() instanceof Pig pig)) {
            return;
        }

        if (!manager.isTarget(pig)) {
            return;
        }

        manager.hit(player, pig);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Pig pig)) {
            return;
        }

        if (manager.isTarget(pig)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (manager.isPlaying(event.getPlayer())
                || manager.isRunning()) {
            // この試作では1人分のゲームを管理
            // 実際の参加者のみ中断対象にする
            if (manager.isPlaying(event.getPlayer())) {
                manager.stop(false);
            }
        }
    }
}
