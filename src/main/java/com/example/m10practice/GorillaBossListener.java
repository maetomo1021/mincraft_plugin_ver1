package com.example.m10practice;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.projectiles.ProjectileSource;

/** Minecraft本来のHPを使わず、専用HPへ受けたダメージを転送。 */
public final class GorillaBossListener implements Listener {
    private final GorillaBossManager manager;

    public GorillaBossListener(GorillaBossManager manager) {
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onBossDamaged(EntityDamageEvent event) {
        Entity victim = event.getEntity();
        if (!manager.isBoss(victim.getUniqueId())) return;

        // ブタの実HPは減らさず、常にこのイベントをキャンセルする
        // ここではプレイヤーによる攻撃だけを専用HPへ反映
        if (event.isCancelled()) return;
        double finalDamage = event.getFinalDamage();
        event.setCancelled(true);

        if (!(event instanceof EntityDamageByEntityEvent attack)) return;
        Player player = null;
        Entity damager = attack.getDamager();
        if (damager instanceof Player p) {
            player = p;
        } else if (damager instanceof Projectile projectile) {
            ProjectileSource shooter = projectile.getShooter();
            if (shooter instanceof Player p) player = p;
        }
        if (player == null) return;
        manager.damage(victim.getUniqueId(), finalDamage, player);
    }
}
