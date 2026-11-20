
package com.example.m10practice;

import com.ticxo.modelengine.api.ModelEngineAPI;
import com.ticxo.modelengine.api.model.ActiveModel;
import com.ticxo.modelengine.api.model.ModeledEntity;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Pig;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class GorillaCommand implements CommandExecutor {

    private final Map<ActiveModel, UUID> gorillas =
            new IdentityHashMap<>();

    private final GorillaBossManager bossManager;

    private final JavaPlugin plugin;

    public GorillaCommand(JavaPlugin plugin, GorillaBossManager bossManager) {
        this.plugin = plugin;
        this.bossManager = bossManager;
    }

    public UUID getGorillaEntityId(ActiveModel model) {
        return gorillas.get(model);
    }

    public Map<ActiveModel, UUID> getSpawnedGorillas() {
        return Collections.unmodifiableMap(
                new IdentityHashMap<>(gorillas)
        );
    }

    @Override
    public boolean onCommand(
            CommandSender sender,
            Command command,
            String label,
            String[] args
    ) {

        if (!(sender instanceof Player player)) {
            sender.sendMessage(
                    "§cプレイヤー専用コマンドです。"
            );
            return true;
        }

        // プレイヤーの正面4ブロックを召喚地点にする
        Location spawn = player.getLocation().clone();

        Vector direction = spawn.getDirection().setY(0);

        if (direction.lengthSquared() < 0.001) {
            direction = new Vector(0, 0, 1);
        }

        direction.normalize().multiply(4);
        spawn.add(direction);
        // 召喚したゴリラをプレイヤー側へ向ける
        spawn.setYaw(player.getLocation().getYaw() + 180.0f);
        spawn.setPitch(0.0f);

        player.sendMessage(
                "§b§l周囲の空気が震え始めた……"
        );

        // この段階ではゴリラはまだ生成しない
        startSpawnIntro(spawn);

        return true;
    }

    /**
     * ゴリラ登場演出
     *
     * 0～4秒   ：雷鳴、落雷、エネルギー収束
     * 4秒      ：大爆発
     * 約4.6秒  ：ゴリラを生成しAI開始
     */
    private void startSpawnIntro(Location center) {

        World world = center.getWorld();

        if (world == null) {
            return;
        }

        new BukkitRunnable() {

            private int ticks = 0;

            @Override
            public void run() {

                // 演出開始
                if (ticks == 0) {

                    world.playSound(
                            center,
                            Sound.ENTITY_LIGHTNING_BOLT_THUNDER,
                            5.0f,
                            0.55f
                    );

                    world.playSound(
                            center,
                            Sound.ENTITY_WARDEN_SONIC_BOOM,
                            2.5f,
                            0.55f
                    );
                }

                // 周囲へのランダム落雷
                if (ticks % 15 == 0 && ticks < 80) {

                    spawnLightningAround(center);

                }

                // 20tickごとに雷鳴
                if (ticks > 0
                        && ticks % 20 == 0
                        && ticks < 80) {

                    world.playSound(
                            center,
                            Sound.ENTITY_LIGHTNING_BOLT_THUNDER,
                            4.0f,
                            0.7f
                    );

                }

                // 召喚地点にエネルギーを収束させる
                if (ticks < 80) {

                    drawSummoningEnergy(center, ticks);

                }

                // 4秒後：巨大な爆発
                if (ticks >= 80) {

                    playFinalExplosion(center);

                    cancel();

                    // 爆発後、少し遅れてゴリラが出現
                    Bukkit.getScheduler().runTaskLater(
                            plugin,
                            () -> spawnGorilla(center),
                            12L
                    );

                    return;
                }

                ticks += 5;
            }

        }.runTaskTimer(plugin, 0L, 5L);
    }

    /**
     * 周囲に演出専用の雷を落とす。
     * strikeLightningEffect は本物の落雷ダメージを発生させない。
     */
    private void spawnLightningAround(Location center) {

        World world = center.getWorld();

        if (world == null) {
            return;
        }

        ThreadLocalRandom random =
                ThreadLocalRandom.current();

        // 一度に2か所
        for (int i = 0; i < 2; i++) {

            double angle =
                    random.nextDouble(0, Math.PI * 2);

            double radius =
                    random.nextDouble(6.0, 13.0);

            Location lightning = center.clone().add(
                    Math.cos(angle) * radius,
                    0,
                    Math.sin(angle) * radius
            );

            // 地表の上に落雷を表示
            lightning.setY(
                    world.getHighestBlockYAt(lightning) + 1
            );

            // 見た目だけの稲妻
            world.strikeLightningEffect(lightning);

            world.spawnParticle(
                    Particle.ELECTRIC_SPARK,
                    lightning.clone().add(0, 1, 0),
                    45,
                    1.1,
                    1.5,
                    1.1,
                    0.12
            );
        }
    }

    /**
     * 召喚地点の周囲に青白いエネルギーを集める。
     */
    private void drawSummoningEnergy(
            Location center,
            int ticks
    ) {

        World world = center.getWorld();

        if (world == null) {
            return;
        }

        double progress = ticks / 80.0;

        // 時間が進むほど半径が縮まる
        double radius = 4.0 - progress * 3.4;

        // 螺旋状のエネルギー
        for (int i = 0; i < 28; i++) {

            double angle =
                    i * (Math.PI * 2 / 28)
                    + ticks * 0.16;

            double height =
                    0.4
                    + (i % 7) * 0.45
                    + Math.sin(angle * 2) * 0.3;

            Location particle = center.clone().add(
                    Math.cos(angle) * radius,
                    height,
                    Math.sin(angle) * radius
            );

            world.spawnParticle(
                    Particle.ELECTRIC_SPARK,
                    particle,
                    2,
                    0.08,
                    0.08,
                    0.08,
                    0.02
            );

            world.spawnParticle(
                    Particle.END_ROD,
                    particle,
                    1,
                    0,
                    0,
                    0,
                    0
            );
        }

        // 召喚地点の冷気
        world.spawnParticle(
                Particle.CLOUD,
                center.clone().add(0, 0.3, 0),
                25,
                1.5,
                0.3,
                1.5,
                0.03
        );

        // 終盤は中心が強く発光
        if (ticks >= 60) {

            world.spawnParticle(
                    Particle.FLASH,
                    center.clone().add(0, 1.5, 0),
                    1,
                    0,
                    0,
                    0,
                    0
            );

            world.playSound(
                    center,
                    Sound.BLOCK_BEACON_POWER_SELECT,
                    1.6f,
                    0.7f + (float) progress
            );
        }
    }

    /**
     * ゴリラ出現直前の大爆発。
     * world.createExplosion は使用しない。
     */
    private void playFinalExplosion(Location center) {

        World world = center.getWorld();

        if (world == null) {
            return;
        }

        Location explosion =
                center.clone().add(0, 1.5, 0);

        // 中心の巨大な爆発
        world.spawnParticle(
                Particle.EXPLOSION_EMITTER,
                explosion,
                1,
                0,
                0,
                0,
                0
        );

        // 大量の煙と衝撃波
        world.spawnParticle(
                Particle.CLOUD,
                explosion,
                240,
                3.5,
                2.0,
                3.5,
                0.18
        );

        // 電気エネルギーの飛散
        world.spawnParticle(
                Particle.ELECTRIC_SPARK,
                explosion,
                180,
                3.0,
                2.0,
                3.0,
                0.25
        );

        // 閃光
        world.spawnParticle(
                Particle.FLASH,
                explosion,
                1,
                0,
                0,
                0,
                0
        );

        // 重低音を重ねる
        world.playSound(
                center,
                Sound.ENTITY_GENERIC_EXPLODE,
                5.0f,
                0.55f
        );

        world.playSound(
                center,
                Sound.ENTITY_WARDEN_SONIC_BOOM,
                3.0f,
                0.65f
        );

        world.playSound(
                center,
                Sound.ENTITY_LIGHTNING_BOLT_THUNDER,
                4.0f,
                0.5f
        );
    }

    /**
     * 登場演出の終了後にゴリラを生成。
     * ここで初めてAI管理マップとBossBarへ登録する。
     */
    private void spawnGorilla(Location spawn) {

        ActiveModel model;

        try {

            model = ModelEngineAPI.createActiveModel(
                    "gigant_snow_golem"
            );

        } catch (Exception e) {

            plugin.getLogger().severe(
                    "ゴリラモデルの生成に失敗: "
                    + e.getMessage()
            );

            return;
        }

        if (model == null) {

            plugin.getLogger().warning(
                    "モデル gigant_snow_golem が見つかりません。"
            );

            return;
        }

        World world = spawn.getWorld();

        if (world == null) {
            return;
        }

        Pig pig = world.spawn(spawn, Pig.class);
        // ModelEngineの土台エンティティにも角度を反映
        pig.setRotation(spawn.getYaw(), 0.0f);

        pig.setAI(false);
        pig.setSilent(true);
        pig.setRemoveWhenFarAway(false);
        pig.setInvisible(true);
        pig.setInvulnerable(false);

        pig.addScoreboardTag("m10_gorilla_test");

        try {

            ModeledEntity modeled =
                    ModelEngineAPI.createModeledEntity(pig);

            modeled.addModel(model, true);

            // ブタだけでなくModelEngine本体にも召喚時の向きを即時反映。
            GorillaFacing.faceYaw(pig, model, spawn.getYaw());
            // モデルの初期化が次tickになる環境でも向きを確定させる。
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (pig.isValid()) {
                    GorillaFacing.syncModel(model, pig.getLocation().getYaw());
                }
            }, 2L);

            // HPとBossBarを登録
            bossManager.register(pig);

            // 最後にAIへ登録して追跡開始
            gorillas.put(model, pig.getUniqueId());

            world.playSound(
                    spawn,
                    Sound.ENTITY_IRON_GOLEM_REPAIR,
                    2.0f,
                    0.5f
            );

            world.spawnParticle(
                    Particle.CLOUD,
                    spawn.clone().add(0, 1, 0),
                    60,
                    1.4,
                    1.5,
                    1.4,
                    0.04
            );

            plugin.getLogger().info(
                    "暴走ゴリラ登場演出完了。AI起動。"
            );

        } catch (Exception e) {

            gorillas.remove(model);
            pig.remove();

            plugin.getLogger().severe(
                    "ゴリラ召喚失敗: " + e.getMessage()
            );
        }
    }
}
