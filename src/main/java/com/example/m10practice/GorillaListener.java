
package com.example.m10practice;

import com.ticxo.modelengine.api.events.BaseEntityInteractEvent;
import com.ticxo.modelengine.api.model.ActiveModel;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Pig;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.UUID;

public class GorillaListener implements Listener {

    private final JavaPlugin plugin;
    private final GorillaCommand command;

    private final Set<ActiveModel> busy =
            Collections.newSetFromMap(new IdentityHashMap<>());

    public GorillaListener(
            JavaPlugin plugin,
            GorillaCommand command
    ) {
        this.plugin = plugin;
        this.command = command;
    }

    @EventHandler
    public void onGorillaClick(BaseEntityInteractEvent event) {

        if (event.getAction() ==
                BaseEntityInteractEvent.Action.ATTACK) {
            return;
        }

        if (event.getSlot() != EquipmentSlot.HAND) {
            return;
        }

        ActiveModel model = event.getModel();

        if (!"gigant_snow_golem".equalsIgnoreCase(
                model.getBlueprint().getName())) {
            return;
        }

        UUID baseEntityId =
                command.getGorillaEntityId(model);

        if (baseEntityId == null) {
            return;
        }

        startKnockback(
                model,
                baseEntityId,
                event.getPlayer()
        );
    }

    public boolean isAttacking(ActiveModel model) {
        return busy.contains(model);
    }

    /**
     * 手動クリックと自動AIで共通の攻撃処理。
     */
    public boolean startKnockback(
            ActiveModel model,
            UUID baseEntityId,
            Player activator
    ) {

        // 同時に複数の攻撃を発動させない
        if (!busy.add(model)) {
            return false;
        }

        Entity base = Bukkit.getEntity(baseEntityId);

        if (!(base instanceof Pig pig) || !pig.isValid()) {
            busy.remove(model);
            return false;
        }

        // Blockbenchの右腕吹き飛ばしモーション
        var animation =
                model.getAnimationHandler().playAnimation(
                        "knockback_attack_right",
                        0.1,
                        0.1,
                        1.0,
                        true
                );

        if (animation == null) {
            busy.remove(model);

            if (activator != null) {
                activator.sendMessage(
                        "§cアニメーション knockback_attack_right が見つかりません。"
                );
            }

            return false;
        }

        // 攻撃中は移動を停止
        Vector previous = pig.getVelocity();

        pig.setVelocity(
                new Vector(0, previous.getY(), 0)
        );

        if (activator != null) {
            activator.sendMessage(
                    "§e暴走ゴリラ：吹き飛ばし攻撃！"
            );
        }

        // 1.45秒後（29tick）に攻撃判定と効果音を発生
        Bukkit.getScheduler().runTaskLater(
                plugin,
                () -> {

                    Entity entity =
                            Bukkit.getEntity(baseEntityId);

                    if (!(entity instanceof Pig impactPig)
                            || !impactPig.isValid()) {
                        return;
                    }

                    Vector facing =
                            impactPig.getLocation()
                                    .getDirection()
                                    .setY(0);

                    if (facing.lengthSquared() < 0.001) {
                        facing = new Vector(0, 0, 1);
                    }

                    facing.normalize();

                    // 周囲のプレイヤーへの攻撃判定
                    for (Player target :
                            impactPig.getWorld().getPlayers()) {

                        GameMode mode = target.getGameMode();

                        if (mode != GameMode.SURVIVAL
                                && mode != GameMode.ADVENTURE) {
                            continue;
                        }

                        if (target.isDead()
                                || target.isInvulnerable()) {
                            continue;
                        }

                        Vector offset =
                                target.getLocation()
                                        .toVector()
                                        .subtract(
                                                impactPig.getLocation()
                                                        .toVector()
                                        );

                        offset.setY(0);

                        double distance = offset.length();

                        if (distance < 0.01
                                || distance > 8.0) {
                            continue;
                        }

                        Vector away =
                                offset.clone().normalize();

                        // 正面120度のみ攻撃
                        if (facing.dot(away) < 0.5) {
                            continue;
                        }

                        // 高低差の判定
                        double heightDifference =
                                Math.abs(
                                        target.getLocation().getY()
                                                - impactPig.getLocation().getY()
                                );

                        if (heightDifference > 4.0) {
                            continue;
                        }

                        // 固定8HPダメージ
                        final double hitDamage = 8.0;

                        double remaining = hitDamage;

                        // 吸収ハートを先に消費
                        double absorption =
                                target.getAbsorptionAmount();

                        double usedAbsorption =
                                Math.min(absorption, remaining);

                        if (usedAbsorption > 0) {
                            target.setAbsorptionAmount(
                                    absorption - usedAbsorption
                            );

                            remaining -= usedAbsorption;
                        }

                        // 通常HPからダメージ分を減算
                        if (remaining > 0) {
                            double beforeHealth =
                                    target.getHealth();

                            target.setHealth(
                                    Math.max(
                                            0.0,
                                            beforeHealth - remaining
                                    )
                            );
                        }

                        // 生存していたら吹き飛ばす
                        if (!target.isDead()) {
                            target.setVelocity(
                                    away.multiply(1.8)
                                            .setY(0.45)
                            );
                        }

                        target.sendMessage(
                                "§c暴走ゴリラの吹き飛ばし！ "
                                        + "§f固定"
                                        + (int) hitDamage
                                        + "ダメージ（テスト版）"
                        );
                    }

                    // ======================================
                    // 吹き飛ばし攻撃：効果音
                    // ======================================

                    // 重低音の爆発
                    impactPig.getWorld().playSound(
                            impactPig.getLocation(),
                            Sound.ENTITY_GENERIC_EXPLODE,
                            2.0f,
                            0.65f
                    );

                    // 巨大な拳の打撃音
                    impactPig.getWorld().playSound(
                            impactPig.getLocation(),
                            Sound.ENTITY_IRON_GOLEM_ATTACK,
                            1.8f,
                            0.55f
                    );

                    // ======================================
                    // 吹き飛ばし攻撃：衝撃パーティクル
                    // ======================================

                    impactPig.getWorld().spawnParticle(
                            Particle.CLOUD,
                            impactPig.getLocation()
                                    .add(
                                            facing.clone()
                                                    .multiply(2)
                                    )
                                    .add(0, 1, 0),
                            35,
                            1.4,
                            0.6,
                            1.4,
                            0.08
                    );

                },
                29L
        );

        // 攻撃モーション終了後に次の攻撃を許可
        Bukkit.getScheduler().runTaskLater(
                plugin,
                () -> busy.remove(model),
                56L
        );

        return true;
    }
}
