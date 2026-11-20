package com.example.m10practice;

import com.ticxo.modelengine.api.model.ActiveModel;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Pig;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.UUID;

/**
 * 暴走ゴリラの冷凍ビーム（開発用）。
 * 口元に青白いチャージ演出 → 狙った方向にビーム → 命中で固定6HP + 鈍足。
 * 地形のブロック変更は行わない。すべてメインスレッドで実行する。
 */
public final class GorillaFreezeBeam {
    private static final double RANGE = 14.0;
    private static final double BEAM_RADIUS = 0.9;
    private static final double DAMAGE = 6.0;
    private static final int WINDUP_TICKS = 20;  // 1.0秒の予兆
    private static final int FIRE_TICKS = 12;    // 0.6秒照射
    private static final int TOTAL_TICKS = 48;   // 2.4秒（復帰含む）
    private static final Particle.DustOptions ICE_BLUE =
            new Particle.DustOptions(Color.fromRGB(35, 175, 255), 1.55f);
    private static final Particle.DustOptions ICE_WHITE =
            new Particle.DustOptions(Color.fromRGB(205, 245, 255), 1.0f);

    private final JavaPlugin plugin;
    private final Set<ActiveModel> busy = Collections.newSetFromMap(new IdentityHashMap<>());

    public GorillaFreezeBeam(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean isAttacking(ActiveModel model) {
        return busy.contains(model);
    }

    public boolean start(ActiveModel model, UUID pigId, Player target) {
        if (target == null || target.isDead() || !busy.add(model)) return false;
        Entity entity = Bukkit.getEntity(pigId);
        if (!(entity instanceof Pig pig) || !pig.isValid()) {
            busy.remove(model);
            return false;
        }

        // 予兆開始時に狙う方向を固定。回避できる攻撃にする。
        Location mouth = getMouth(pig);
        Vector aim = target.getEyeLocation().toVector().subtract(mouth.toVector());
        if (aim.lengthSquared() < 0.01) {
            busy.remove(model);
            return false;
        }
        aim.normalize();
        Vector forward = aim.clone().setY(0);
        if (forward.lengthSquared() > 0.001) {
            GorillaFacing.faceDirection(pig, model, forward);
        }
        pig.getWorld().playSound(mouth, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.2f, 0.55f);

        new BukkitRunnable() {
            int age = 0;
            final Set<UUID> hitPlayers = new HashSet<>();

            @Override
            public void run() {
                Entity current = Bukkit.getEntity(pigId);
                if (!(current instanceof Pig caster) || !caster.isValid()
                        || !caster.getScoreboardTags().contains("m10_gorilla_test")) {
                    finish();
                    return;
                }
                Location source = getMouth(caster);
                if (age < WINDUP_TICKS) {
                    drawCharge(caster, source, age);
                } else if (age < WINDUP_TICKS + FIRE_TICKS) {
                    if (age == WINDUP_TICKS) {
                        
                        // 冷凍ビーム発射：重低音＋高音
                        caster.getWorld().playSound(
                                source,
                                Sound.ENTITY_WARDEN_SONIC_BOOM,
                                2.0f,
                                1.25f
                        );
                        caster.getWorld().playSound(
                                source,
                                Sound.ENTITY_GUARDIAN_ATTACK,
                                1.8f,
                                1.7f
                        );
                    }
                    drawBeam(caster, source, aim, hitPlayers);
                } else if (age == WINDUP_TICKS + FIRE_TICKS) {
                    caster.getWorld().spawnParticle(Particle.SNOWFLAKE,
                            source, 35, 0.7, 0.7, 0.7, 0.04);
                }
                age++;
                if (age >= TOTAL_TICKS) finish();
            }

            private void finish() {
                busy.remove(model);
                cancel();
            }
        }.runTaskTimer(plugin, 0L, 1L);
        return true;
    }

    private Location getMouth(Pig pig) {
        // モデルの顔の高さに合わせた暫定値。ゲーム内で上下調整する。
        Vector facing = pig.getLocation().getDirection().setY(0);
        if (facing.lengthSquared() < 0.001) facing = new Vector(0, 0, 1);
        return pig.getLocation().add(0, 3.0, 0)
                .add(facing.normalize().multiply(0.65));
    }

    /**
     * 前半: 身体を包む回転する氷オーラ。
     * 後半: 粒子が口元へ引き寄せられ、青白い光球へ収束する。
     * 粒子は全員に送信されるので、1tickの生成数を抑える。
     */
    private void drawCharge(Pig caster, Location mouth, int age) {
        var world = caster.getWorld();
        Location center = caster.getLocation().add(0, 1.75, 0);
        double progress = Math.min(1.0, age / (double) (WINDUP_TICKS - 1));

        // 体を巻く2重の螺旋。終盤で半径を縮めて「収束」を見せる。
        double bodyRadius = 1.65 * (1.0 - progress * 0.75);
        for (int i = 0; i < 12; i++) {
            double theta = age * 0.36 + i * Math.PI / 6.0;
            for (int spiral = 0; spiral < 2; spiral++) {
                double phase = theta + spiral * Math.PI;
                double height = (i / 11.0) * 2.8 - 1.15;
                Location point = center.clone().add(
                        Math.cos(phase) * bodyRadius, height,
                        Math.sin(phase) * bodyRadius);
                world.spawnParticle(Particle.DUST, point, 1, 0, 0, 0, 0,
                        spiral == 0 ? ICE_BLUE : ICE_WHITE);
                if (i % 4 == 0) {
                    world.spawnParticle(Particle.SNOWFLAKE, point, 1, 0, 0, 0, 0);
                }
            }
        }

        // 口の周りを回る小さな氷の光点。時間とともに中心へ縮小。
        double mouthRadius = 1.15 * (1.0 - progress) + 0.09;
        for (int i = 0; i < 12; i++) {
            double angle = (i * Math.PI * 2 / 12.0) - age * 0.29;
            Location point = mouth.clone().add(
                    Math.cos(angle) * mouthRadius,
                    Math.sin(angle) * mouthRadius,
                    Math.sin(angle * 1.7 + age * 0.2) * 0.20);
            world.spawnParticle(Particle.DUST, point, 1, 0, 0, 0, 0, ICE_BLUE);
        }
        world.spawnParticle(Particle.END_ROD, mouth, 2 + (int) (progress * 4),
                0.10, 0.10, 0.10, 0.005);
        world.spawnParticle(Particle.SNOWFLAKE, mouth, 3,
                0.20, 0.18, 0.20, 0.01);

        // チャージが進むほど音程が上昇
        if (age == 4 || age == 9 || age == 14 || age == 18) {
            float pitch = 0.6f + (age / 20.0f) * 1.2f;

            world.playSound(
                    mouth,
                    Sound.BLOCK_BEACON_POWER_SELECT,
                    1.6f,
                    pitch
            );

            world.playSound(
                    mouth,
                    Sound.BLOCK_AMETHYST_BLOCK_CHIME,
                    1.0f,
                    pitch
            );
        }
    }

    private void drawBeam(Pig caster, Location origin, Vector direction, Set<UUID> hitPlayers) {
        var world = caster.getWorld();
        // 壁を貫通しない。
        double length = RANGE;
        var ray = world.rayTraceBlocks(origin, direction, RANGE);
        if (ray != null) length = Math.min(length,
                ray.getHitPosition().distance(origin.toVector()));

        // ビーム軸に垂直な2本のベクトルを作り、青い二重螺旋を描く。
        Vector perpendicular = direction.clone().crossProduct(new Vector(0, 1, 0));
        if (perpendicular.lengthSquared() < 0.001) {
            perpendicular = direction.clone().crossProduct(new Vector(1, 0, 0));
        }
        perpendicular.normalize();
        Vector other = direction.clone().crossProduct(perpendicular).normalize();
        long tick = caster.getTicksLived();

        for (double d = 0; d <= length; d += 0.42) {
            Location axis = origin.clone().add(direction.clone().multiply(d));
            world.spawnParticle(Particle.DUST, axis, 1, 0, 0, 0, 0, ICE_WHITE);
            if (((int) (d / 0.42)) % 2 == 0) {
                world.spawnParticle(Particle.END_ROD, axis, 1, 0.02, 0.02, 0.02, 0);
            }
            double angle = d * 3.4 + tick * 0.42;
            for (int side = 0; side < 2; side++) {
                double theta = angle + side * Math.PI;
                double radius = 0.32;
                Vector offset = perpendicular.clone().multiply(Math.cos(theta) * radius)
                        .add(other.clone().multiply(Math.sin(theta) * radius));
                Location spiral = axis.clone().add(offset);
                world.spawnParticle(Particle.DUST, spiral, 1, 0, 0, 0, 0, ICE_BLUE);
            }
            if (((int) (d / 0.42)) % 4 == 0) {
                world.spawnParticle(Particle.SNOWFLAKE, axis, 1, 0.22, 0.22, 0.22, 0.01);
            }
        }
        Location impact = origin.clone().add(direction.clone().multiply(length));
        world.spawnParticle(Particle.SNOWFLAKE, impact, 16, 0.4, 0.4, 0.4, 0.035);
        world.spawnParticle(Particle.DUST, impact, 8, 0.36, 0.36, 0.36, 0, ICE_BLUE);

        for (Player player : world.getPlayers()) {
            if (hitPlayers.contains(player.getUniqueId()) || player.isDead() || player.isInvulnerable()) continue;
            GameMode mode = player.getGameMode();
            if (mode != GameMode.SURVIVAL && mode != GameMode.ADVENTURE) continue;
            Vector playerCenter = player.getLocation().add(0, 1.0, 0).toVector();
            Vector offset = playerCenter.clone().subtract(origin.toVector());
            double projected = offset.dot(direction);
            if (projected < 0 || projected > length) continue;
            Vector closest = origin.toVector().add(direction.clone().multiply(projected));
            if (closest.distanceSquared(playerCenter) > BEAM_RADIUS * BEAM_RADIUS) continue;
            hitPlayers.add(player.getUniqueId());
            applyHit(player);
        }
    }

    private void applyHit(Player player) {
        // 以前の吹き飛ばし攻撃と同じ開発用固定HP方式。
        double absorption = player.getAbsorptionAmount();
        double absorbed = Math.min(absorption, DAMAGE);
        if (absorbed > 0) player.setAbsorptionAmount(absorption - absorbed);
        double remaining = DAMAGE - absorbed;
        if (remaining > 0) player.setHealth(Math.max(0, player.getHealth() - remaining));
        if (!player.isDead()) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 80, 2));
            player.setFreezeTicks(Math.max(player.getFreezeTicks(), 110));
        }
        player.getWorld().spawnParticle(Particle.SNOWFLAKE,
                player.getLocation().add(0, 1, 0), 65, 0.55, 0.9, 0.55, 0.07);
        
        // 冷凍ビーム命中：氷の破砕音
        player.getWorld().playSound(
                player.getLocation(),
                Sound.BLOCK_GLASS_BREAK,
                1.7f,
                0.65f
        );

        player.getWorld().playSound(
                player.getLocation(),
                Sound.BLOCK_AMETHYST_CLUSTER_BREAK,
                1.4f,
                0.85f
        );

        player.sendMessage("§b暴走ゴリラの冷凍ビーム！ §f6ダメージ＋鈍足");
    }
}
