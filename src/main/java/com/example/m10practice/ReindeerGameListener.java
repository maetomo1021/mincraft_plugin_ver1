package com.example.m10practice;

import org.bukkit.Bukkit;
import org.bukkit.entity.Pig;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.plugin.java.JavaPlugin;

public class ReindeerGameListener implements Listener {
    private final ReindeerGameManager manager;
    private final JavaPlugin plugin;

    public ReindeerGameListener(ReindeerGameManager manager) {
        this.manager = manager;
        // 既存Main.javaのコンストラクタ呼び出しを変えないため、
        // プラグインインスタンスはBukkitから取得します。
        this.plugin = JavaPlugin.getProvidingPlugin(ReindeerGameListener.class);
    }

    @EventHandler(ignoreCancelled = true)
    public void onSnowballHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Snowball snowball)) return;
        if (!(snowball.getShooter() instanceof Player player)) return;
        if (!(event.getHitEntity() instanceof Pig pig)) return;
        if (manager.isTarget(pig)) manager.hit(player, pig);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Pig pig && manager.isTarget(pig)) {
            event.setCancelled(true);
        }
        // テストゲーム中にプレイヤーが死亡して持ち物を失うのを防止
        if (event.getEntity() instanceof Player player && manager.isParticipant(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (manager.isParticipant(event.getPlayer())) manager.stop(false);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) manager.recoverInventory(player);
        });
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player && manager.isParticipant(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player && manager.isParticipant(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (manager.isParticipant(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player && manager.isParticipant(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent event) {
        if (manager.isParticipant(event.getPlayer())) event.setCancelled(true);
    }
}
