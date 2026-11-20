
package com.example.m10practice;

import com.ticxo.modelengine.api.model.ActiveModel;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Pig;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class GorillaJumpAttack {

    // ======================================
    // 大ジャンプ
    // ======================================

    private static final int JUMP_TICKS = 40;
    private static final double JUMP_HEIGHT = 12.0;

    // ======================================
    // マグマ衝撃波
    // ======================================

    // 見た目とダメージの共通半径
    private static final int WAVE_RADIUS = 8;

    // 1tickで進む距離（ブロック）
    // 大きいほど波が速く広がる
    private static final double WAVE_SPEED = 0.75;

    // 衝撃波の命中幅
    private static final double HIT_WIDTH = 0.85;

    // 地上限定の固定ダメージ
    private static final double WAVE_DAMAGE = 10.0;

    // マグマのアニメーション
    private static final int RISE_TICKS = 7;
    private static final int HOLD_TICKS = 4;
    private static final int SINK_TICKS = 8;

    private static final double MIN_HEIGHT = 3.0;
    private static final double MAX_HEIGHT = 8.0;
    private static final double HIDDEN_DEPTH = 1.25;

    private static final int WAVE_TICKS =
            (int) Math.ceil(WAVE_RADIUS / WAVE_SPEED)
            + RISE_TICKS
            + HOLD_TICKS
            + SINK_TICKS
            + 6;

    private static final String MAGMA_TAG =
            "m10_gorilla_magma";

    private final JavaPlugin plugin;

    private final Set<ActiveModel> busy =
            Collections.newSetFromMap(
                    new IdentityHashMap<>()
            );

    private static final class MagmaPiece {

        final BlockDisplay display;
        final Location underground;
        final int delay;
        final double riseHeight;

        MagmaPiece(
                BlockDisplay display,
                Location underground,
                int delay,
                double riseHeight
        ) {
            this.display = display;
            this.underground = underground;
            this.delay = delay;
            this.riseHeight = riseHeight;
        }
    }

    public GorillaJumpAttack(JavaPlugin plugin) {
        this.plugin = plugin;

        // 前回の残存演出を削除
        for (World world : Bukkit.getWorlds()) {
            for (BlockDisplay display :
                    world.getEntitiesByClass(BlockDisplay.class)) {

                if (display.getScoreboardTags()
                        .contains(MAGMA_TAG)) {
                    display.remove();
                }
            }
        }
    }

    public boolean isAttacking(ActiveModel model) {
        return busy.contains(model);
    }

    // ======================================
    // 攻撃開始
    // ======================================

    public boolean start(
            ActiveModel model,
            UUID pigId,
            Player target
    ) {
        if (target == null || target.isDead()) {
            return false;
        }

        Entity entity = Bukkit.getEntity(pigId);

        if (!(entity instanceof Pig pig)
                || !pig.isValid()) {
            return false;
        }

        if (!pig.getWorld().equals(target.getWorld())) {
            return false;
        }

        if (!busy.add(model)) {
            return false;
        }

        Location origin = pig.getLocation().clone();
        Location landing = target.getLocation().clone();

        // 平坦な闘技場を前提とする
        landing.setY(Math.floor(landing.getY()));

        if (!landing.clone().subtract(0, 1, 0)
                .getBlock().getType().isSolid()
                || !landing.getBlock().isPassable()
                || !landing.clone().add(0, 1, 0)
                        .getBlock().isPassable()) {

            busy.remove(model);
            return false;
        }

        Vector horizontal = landing.toVector()
                .subtract(origin.toVector())
                .setY(0);

        if (horizontal.lengthSquared() > 0.001) {
            Vector direction = horizontal.normalize();

            float yaw = (float) Math.toDegrees(
                    Math.atan2(
                            -direction.getX(),
                            direction.getZ()
                    )
            );

            landing.setYaw(yaw);
            GorillaFacing.faceYaw(pig, model, yaw);
        }

        World world = origin.getWorld();

        if (world == null) {
            busy.remove(model);
            return false;
        }

        pig.setVelocity(new Vector(0, 0, 0));

        world.playSound(
                origin,
                Sound.ENTITY_RAVAGER_ROAR,
                2.5f,
                0.6f
        );

        world.playSound(
                origin,
                Sound.ENTITY_IRON_GOLEM_ATTACK,
                1.8f,
                0.5f
        );

        world.spawnParticle(
                Particle.CLOUD,
                origin.clone().add(0, 0.3, 0),
                80,
                1.5, 0.3, 1.5,
                0.08
        );

        new BukkitRunnable() {

            private int age = 0;
            private boolean impacted = false;

            private final List<MagmaPiece> pieces =
                    new ArrayList<>();

            // このジャンプ攻撃でダメージを受けた人
            private final Set<UUID> hitPlayers =
                    new HashSet<>();

            @Override
            public void run() {

                Entity current = Bukkit.getEntity(pigId);

                if (!(current instanceof Pig jumper)
                        || !jumper.isValid()
                        || !jumper.getScoreboardTags()
                                .contains("m10_gorilla_test")) {

                    finish();
                    return;
                }

                // ① 大ジャンプ
                if (age <= JUMP_TICKS) {
                    moveJump(
                            jumper,
                            origin,
                            landing,
                            age
                    );
                }

                // ② 着地
                if (age == JUMP_TICKS && !impacted) {
                    impacted = true;

                    // 着地はエフェクトのみ
                    // ダメージは衝撃波で与える
                    impact(world, landing);

                    createMagmaField(
                            world,
                            landing,
                            pieces
                    );
                }

                // ③ 着地点から外側へ衝撃波
                if (impacted) {
                    int waveAge = age - JUMP_TICKS;

                    if (waveAge <= WAVE_TICKS) {

                        // 表示用マグマの移動
                        if (waveAge % 2 == 0) {
                            updateWave(
                                    pieces,
                                    waveAge
                            );
                        }

                        // 毎tick判定
                        // 見た目のマグマとは独立
                        if (waveAge * WAVE_SPEED
                                <= WAVE_RADIUS + HIT_WIDTH) {

                            damageGroundPlayers(
                                    world,
                                    landing,
                                    waveAge,
                                    hitPlayers
                            );

                            drawShockwave(
                                    world,
                                    landing,
                                    waveAge
                            );
                        }

                    } else {
                        finish();
                        return;
                    }
                }

                age++;
            }

            private void finish() {
                for (MagmaPiece piece : pieces) {
                    if (piece.display.isValid()) {
                        piece.display.remove();
                    }
                }

                pieces.clear();
                hitPlayers.clear();

                busy.remove(model);
                cancel();
            }

        }.runTaskTimer(plugin, 0L, 1L);

        return true;
    }

    // ======================================
    // ゴリラのジャンプ
    // ======================================

    private void moveJump(
            Pig pig,
            Location start,
            Location end,
            int tick
    ) {

        double t = Math.min(
                1.0,
                tick / (double) JUMP_TICKS
        );

        double arc =
                4.0 * JUMP_HEIGHT * t * (1.0 - t);

        Location next = start.clone().add(
                (end.getX() - start.getX()) * t,
                (end.getY() - start.getY()) * t + arc,
                (end.getZ() - start.getZ()) * t
        );

        next.setYaw(end.getYaw());
        next.setPitch(0f);

        pig.teleport(next);
        pig.setVelocity(new Vector(0, 0, 0));

        if (tick % 2 == 0) {
            pig.getWorld().spawnParticle(
                    Particle.CLOUD,
                    next.clone().add(0, 0.2, 0),
                    12,
                    0.7, 0.2, 0.7,
                    0.02
            );
        }
    }

    // ======================================
    // 着地演出
    // ダメージはここでは与えない
    // ======================================

    private void impact(
            World world,
            Location center
    ) {

        Location point =
                center.clone().add(0, 0.5, 0);

        world.playSound(
                center,
                Sound.ENTITY_GENERIC_EXPLODE,
                3.0f,
                0.55f
        );

        world.playSound(
                center,
                Sound.ENTITY_WARDEN_SONIC_BOOM,
                2.0f,
                0.7f
        );

        world.playSound(
                center,
                Sound.BLOCK_BASALT_BREAK,
                2.5f,
                0.6f
        );

        world.spawnParticle(
                Particle.EXPLOSION_EMITTER,
                point,
                3,
                1.5, 0.5, 1.5,
                0
        );

        world.spawnParticle(
                Particle.CLOUD,
                point,
                260,
                4.0, 0.7, 4.0,
                0.2
        );

        world.spawnParticle(
                Particle.LAVA,
                point,
                140,
                3.5, 1.2, 3.5,
                0.12
        );

        world.spawnParticle(
                Particle.FLAME,
                point,
                200,
                4.0, 1.0, 4.0,
                0.1
        );
    }

    // ======================================
    // マグマ表示の生成
    // ======================================

    private void createMagmaField(
            World world,
            Location center,
            List<MagmaPiece> pieces
    ) {

        ThreadLocalRandom random =
                ThreadLocalRandom.current();

        int centerX = center.getBlockX();
        int centerZ = center.getBlockZ();
        int groundY = center.getBlockY();

        for (int dx = -WAVE_RADIUS;
                dx <= WAVE_RADIUS;
                dx++) {

            for (int dz = -WAVE_RADIUS;
                    dz <= WAVE_RADIUS;
                    dz++) {

                double distance = Math.sqrt(
                        dx * dx + dz * dz
                );

                if (distance > WAVE_RADIUS) {
                    continue;
                }

                // ゴリラの真下は空ける
                if (distance < 1.0) {
                    continue;
                }

                // 不規則に間引く
                if (random.nextDouble() < 0.30) {
                    continue;
                }

                // 波の速度と同じタイミングで隆起
                int delay = (int) Math.floor(
                        distance / WAVE_SPEED
                );

                Location underground = new Location(
                        world,
                        centerX + dx,
                        groundY - HIDDEN_DEPTH,
                        centerZ + dz
                );

                BlockDisplay display = world.spawn(
                        underground,
                        BlockDisplay.class
                );

                display.addScoreboardTag(MAGMA_TAG);

                display.setBlock(
                        Material.MAGMA_BLOCK.createBlockData()
                );

                display.setViewRange(3.0f);
                display.setTeleportDuration(2);

                double height = random.nextDouble(
                        MIN_HEIGHT,
                        MAX_HEIGHT
                );

                if (random.nextDouble() < 0.12) {
                    height += 1.0;
                }

                height = Math.min(height, 9.0);

                pieces.add(
                        new MagmaPiece(
                                display,
                                underground.clone(),
                                delay,
                                height
                        )
                );
            }
        }
    }

    // ======================================
    // マグマブロックの隆起・沈下
    // ======================================

    private void updateWave(
            List<MagmaPiece> pieces,
            int waveAge
    ) {

        for (MagmaPiece piece : pieces) {

            if (!piece.display.isValid()) {
                continue;
            }

            int elapsed = waveAge - piece.delay;
            double progress;

            if (elapsed < 0) {
                progress = 0.0;

            } else if (elapsed < RISE_TICKS) {
                double t =
                        elapsed / (double) RISE_TICKS;

                progress = smoothStep(t);

            } else if (elapsed
                    < RISE_TICKS + HOLD_TICKS) {

                progress = 1.0;

            } else if (elapsed
                    < RISE_TICKS
                    + HOLD_TICKS
                    + SINK_TICKS) {

                double t = (
                        elapsed
                        - RISE_TICKS
                        - HOLD_TICKS
                ) / (double) SINK_TICKS;

                progress = 1.0 - smoothStep(t);

            } else {
                progress = 0.0;
            }

            Location next =
                    piece.underground.clone().add(
                            0,
                            piece.riseHeight * progress,
                            0
                    );

            piece.display.teleport(next);
        }
    }

    // ======================================
    // 地上プレイヤー限定の衝撃波ダメージ
    // ======================================

    private void damageGroundPlayers(
            World world,
            Location center,
            int waveAge,
            Set<UUID> hitPlayers
    ) {

        double waveFront = waveAge * WAVE_SPEED;

        for (Player player : world.getPlayers()) {

            if (player.getGameMode()
                    != GameMode.SURVIVAL
                    && player.getGameMode()
                    != GameMode.ADVENTURE) {
                continue;
            }

            if (player.isDead()
                    || player.isInvulnerable()) {
                continue;
            }

            if (hitPlayers.contains(
                    player.getUniqueId())) {
                continue;
            }

            // 空中にいるプレイヤーは無効
            if (!player.isOnGround()) {
                continue;
            }

            Location location = player.getLocation();

            // 着地面の高さに近い地上のみ
            if (Math.abs(
                    location.getY() - center.getY()
            ) > 1.0) {
                continue;
            }

            double dx =
                    location.getX() - center.getX();

            double dz =
                    location.getZ() - center.getZ();

            double distance =
                    Math.sqrt(dx * dx + dz * dz);

            // 波の外周の外は攻撃しない
            if (distance > WAVE_RADIUS + 0.3) {
                continue;
            }

            // 波紋の輪に触れた瞬間だけ命中
            if (Math.abs(distance - waveFront)
                    > HIT_WIDTH) {
                continue;
            }

            // この攻撃では再ヒット禁止
            hitPlayers.add(player.getUniqueId());

            // 吸収ハートを先に消費
            double remaining = WAVE_DAMAGE;
            double absorption =
                    player.getAbsorptionAmount();

            double used =
                    Math.min(absorption, remaining);

            if (used > 0) {
                player.setAbsorptionAmount(
                        absorption - used
                );

                remaining -= used;
            }

            // 防具を無視する固定HP方式
            if (remaining > 0) {
                player.setHealth(
                        Math.max(
                                0.0,
                                player.getHealth() - remaining
                        )
                );
            }

            // 地上のプレイヤーを吹き飛ばす
            if (!player.isDead()) {

                Vector away = new Vector(dx, 0, dz);

                if (away.lengthSquared() < 0.001) {
                    away = new Vector(0, 0, 1);
                }

                player.setVelocity(
                        away.normalize()
                                .multiply(1.0)
                                .setY(0.45)
                );
            }

            player.getWorld().playSound(
                    location,
                    Sound.ENTITY_IRON_GOLEM_DAMAGE,
                    1.8f,
                    0.7f
            );

            player.getWorld().spawnParticle(
                    Particle.LAVA,
                    location.clone().add(0, 0.5, 0),
                    15,
                    0.4, 0.3, 0.4,
                    0.05
            );

            player.sendMessage(
                    "§cマグマ衝撃波が直撃！ "
                    + "§f固定10HPダメージ"
            );
        }
    }

    // ======================================
    // 高速で外側に広がる波の可視化
    // ======================================

    private void drawShockwave(
            World world,
            Location center,
            int waveAge
    ) {

        double radius = waveAge * WAVE_SPEED;

        if (radius > WAVE_RADIUS) {
            return;
        }

        // 衝撃波を円形に描く
        for (int i = 0; i < 32; i++) {

            double angle =
                    Math.PI * 2.0 * i / 32.0;

            Location point = center.clone().add(
                    Math.cos(angle) * radius,
                    0.15,
                    Math.sin(angle) * radius
            );

            world.spawnParticle(
                    Particle.FLAME,
                    point,
                    2,
                    0.08, 0.06, 0.08,
                    0.01
            );

            world.spawnParticle(
                    Particle.CLOUD,
                    point,
                    1,
                    0.05, 0.03, 0.05,
                    0.01
            );
        }

        // 地割れが広がる重低音
        if (waveAge == 2
                || waveAge == 6
                || waveAge == 10) {

            world.playSound(
                    center,
                    Sound.BLOCK_BASALT_BREAK,
                    2.0f,
                    0.55f
            );

            world.playSound(
                    center,
                    Sound.BLOCK_PISTON_EXTEND,
                    1.6f,
                    0.6f
            );
        }
    }

    private static double smoothStep(double t) {

        t = Math.max(0.0, Math.min(1.0, t));

        return t * t * (3.0 - 2.0 * t);
    }
}
