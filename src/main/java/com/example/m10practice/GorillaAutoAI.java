
package com.example.m10practice;

import com.ticxo.modelengine.api.model.ActiveModel;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Pig;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class GorillaAutoAI {

    private static final double DETECT_SQ =
            24.0 * 24.0;

    private static final double ATTACK_SQ =
            6.0 * 6.0;

    private static final double STOP_SQ =
            4.0 * 4.0;

    // 前回調整した毎tick移動の速度を維持
    private static final double CHASE_SPEED = 0.10;
    private static final double WANDER_SPEED = 0.05;

    private static final double WANDER_RADIUS = 7.0;
    private static final int ATTACK_COOLDOWN = 136;

    private enum Movement {
        NONE, IDLE, WALK
    }

    // 既存3技と追加6技
    private enum Attack {
        NONE, BEAM, KNOCKBACK, JUMP,
        SPIKES, MISSILES, BLIZZARD, DOUBLE_WAVE, METEOR, CAGE
    }

    private static final class State {

        final Location home;

        Location wanderGoal;
        Location beamAnchor;

        UUID targetId;

        int nextWanderTick;
        int nextAttackTick;
        int nextLongRangeRollTick;
        int nextTargetSelectionTick;
        int nextAnimationCheckTick;

        Movement movement = Movement.NONE;
        Attack lastAttack = Attack.NONE;

        State(Location home) {
            this.home = home.clone();
        }
    }

    private final JavaPlugin plugin;
    private final GorillaCommand command;
    private final GorillaListener knockback;

    private final GorillaFreezeBeam beam;
    private final GorillaJumpAttack jump;

    private final GorillaExtraAttacks extras;

    private final Map<UUID, State> states =
            new HashMap<>();

    private final Map<ActiveModel, Movement> animations =
            new IdentityHashMap<>();

    private long aiTicks = 0;

    private String lastMove = "not attempted";
    private String lastTarget = "none";

    public GorillaAutoAI(
            JavaPlugin plugin,
            GorillaCommand command,
            GorillaListener knockback
    ) {
        this.plugin = plugin;
        this.command = command;
        this.knockback = knockback;

        this.beam = new GorillaFreezeBeam(plugin);
        this.jump = new GorillaJumpAttack(plugin);

        this.extras = new GorillaExtraAttacks(plugin);

        // カクつき対策で調整した毎tick更新を維持
        Bukkit.getScheduler().runTaskTimer(
                plugin,
                this::tick,
                20L,
                1L
        );
    }

    // =====================================
    // AIメインループ
    // =====================================

    private void tick() {

        Map<ActiveModel, UUID> gorillas =
                command.getSpawnedGorillas();

        Set<UUID> living = new HashSet<>();

        int valid = 0;

        for (Map.Entry<ActiveModel, UUID> entry
                : gorillas.entrySet()) {

            ActiveModel model = entry.getKey();
            UUID id = entry.getValue();

            Entity entity = Bukkit.getEntity(id);

            if (!(entity instanceof Pig pig)
                    || !pig.isValid()
                    || !pig.getScoreboardTags()
                            .contains("m10_gorilla_test")) {

                animations.remove(model);
                continue;
            }

            valid++;
            living.add(id);

            State state = states.computeIfAbsent(
                    id,
                    key -> new State(pig.getLocation())
            );

            // 全攻撃中は通常移動を禁止
            if (extras.isAttacking(model)
                    || jump.isAttacking(model)
                    || knockback.isAttacking(model)) {

                pause(model, pig, state);
                continue;
            }

            // 通常冷凍ビーム中の位置固定
            if (beam.isAttacking(model)) {

                if (state.beamAnchor != null
                        && pig.getLocation()
                                .distanceSquared(
                                        state.beamAnchor
                                ) > 0.0004) {

                    pig.teleport(state.beamAnchor);
                }

                pig.setVelocity(new Vector(0, 0, 0));

                stopAnimation(model);
                state.movement = Movement.NONE;

                continue;
            }

            state.beamAnchor = null;

            Player target = chooseTarget(pig, state);

            if (target == null) {

                lastTarget = "none";
                wander(model, pig, state);

            } else {

                lastTarget = target.getName();
                state.wanderGoal = null;

                chase(
                        model,
                        id,
                        pig,
                        state,
                        target
                );
            }
        }

        states.keySet().retainAll(living);

        animations.keySet().retainAll(
                gorillas.keySet()
        );

        if (++aiTicks % 100 == 0) {
            plugin.getLogger().info(
                    "[GorillaAI DEBUG] registered="
                            + gorillas.size()
                            + " valid=" + valid
                            + " target=" + lastTarget
                            + " move=" + lastMove
            );
        }
    }

    // =====================================
    // 攻撃中の移動停止
    // =====================================

    private void pause(
            ActiveModel model,
            Pig pig,
            State state
    ) {
        stopHorizontal(pig);
        stopAnimation(model);
        state.movement = Movement.NONE;
    }

    // =====================================
    // プレイヤー追跡・攻撃判断
    // =====================================

    private void chase(
            ActiveModel model,
            UUID id,
            Pig pig,
            State state,
            Player target
    ) {

        Vector toward = target.getLocation()
                .toVector()
                .subtract(pig.getLocation().toVector())
                .setY(0);

        double distanceSq = toward.lengthSquared();

        if (distanceSq < 0.001) {

            stopHorizontal(pig);

            setMovement(
                    model,
                    state,
                    Movement.IDLE
            );

            return;
        }

        Vector direction = toward.normalize();

        GorillaFacing.faceDirection(
                pig,
                model,
                direction
        );

        boolean near =
                distanceSq <= ATTACK_SQ;

        boolean jumpRange =
                distanceSq <= 12.0 * 12.0;

        boolean beamRange =
                distanceSq <= 14.0 * 14.0;

        boolean extraRange =
                distanceSq <= 22.0 * 22.0;

        if (pig.getTicksLived() >= state.nextAttackTick
                && extraRange) {

            List<Attack> options = new ArrayList<>();

            if (near) {
                options.add(Attack.KNOCKBACK);
                options.add(Attack.BEAM);
                options.add(Attack.JUMP);
            } else if (jumpRange) {
                options.add(Attack.JUMP);
                options.add(Attack.BEAM);
            } else if (beamRange) {
                options.add(Attack.BEAM);
            }

            // 追加技は全距離帯に分散させる
            options.add(Attack.SPIKES);
            options.add(Attack.MISSILES);
            options.add(Attack.BLIZZARD);
            options.add(Attack.DOUBLE_WAVE);
            options.add(Attack.METEOR);
            options.add(Attack.CAGE);

            // 同じ攻撃の連発をできるだけ回避
            if (options.size() > 1) {
                options.remove(state.lastAttack);
            }

            Attack selected = Attack.NONE;

            if (near) {

                selected = options.get(
                        ThreadLocalRandom.current()
                                .nextInt(options.size())
                );

            } else if (
                    pig.getTicksLived()
                            >= state.nextLongRangeRollTick
            ) {

                state.nextLongRangeRollTick =
                        pig.getTicksLived() + 60;

                // 中・遠距離では40%で攻撃
                if (ThreadLocalRandom.current()
                        .nextInt(10) < 4
                        && !(options.size() == 1
                        && options.get(0) == state.lastAttack)) {

                    selected = options.get(
                            ThreadLocalRandom.current()
                                    .nextInt(options.size())
                    );
                }
            }

            if (selected != Attack.NONE
                    && startAttack(
                            model,
                            id,
                            pig,
                            state,
                            target,
                            selected
                    )) {
                return;
            }
        }

        // =================================
        // 通常追跡
        // =================================

        if (distanceSq <= STOP_SQ) {

            stopHorizontal(pig);

            setMovement(
                    model,
                    state,
                    Movement.IDLE
            );

        } else if (moveHorizontal(
                pig,
                direction,
                CHASE_SPEED
        )) {

            setMovement(
                    model,
                    state,
                    Movement.WALK
            );

        } else {

            setMovement(
                    model,
                    state,
                    Movement.IDLE
            );
        }
    }

    // =====================================
    // 4種類の攻撃起動
    // =====================================

    private boolean startAttack(
            ActiveModel model,
            UUID id,
            Pig pig,
            State state,
            Player target,
            Attack selected
    ) {

        stopHorizontal(pig);
        stopAnimation(model);

        model.getAnimationHandler()
                .stopAnimation("walk");

        model.getAnimationHandler()
                .stopAnimation("idle");

        state.movement = Movement.NONE;

        boolean started;

        switch (selected) {

            case BEAM -> started =
                    beam.start(model, id, target);

            case JUMP -> started =
                    jump.start(model, id, target);

            case SPIKES, MISSILES, BLIZZARD,
                 DOUBLE_WAVE, METEOR, CAGE -> started =
                    extras.start(
                            GorillaExtraAttacks.Skill.valueOf(selected.name()),
                            model, id, target
                    );

            case KNOCKBACK -> started =
                    knockback.startKnockback(
                            model,
                            id,
                            null
                    );

            default -> started = false;
        }

        if (!started) {
            return false;
        }

        state.lastAttack = selected;

        if (selected == Attack.BEAM) {
            state.beamAnchor =
                    pig.getLocation().clone();
        }

        // 長い演出中はbusyでロックし、終了後に再攻撃できる
        state.nextAttackTick =
                pig.getTicksLived() + ATTACK_COOLDOWN;

        plugin.getLogger().info(
                "[GorillaAI] attack="
                        + selected
                        + " target="
                        + target.getName()
        );

        return true;
    }

    // =====================================
    // 徘徊
    // =====================================

    private void wander(
            ActiveModel model,
            Pig pig,
            State state
    ) {

        int now = pig.getTicksLived();

        if (state.wanderGoal != null) {

            Vector toward = state.wanderGoal
                    .toVector()
                    .subtract(
                            pig.getLocation().toVector()
                    )
                    .setY(0);

            if (toward.lengthSquared() <= 1.0) {

                state.wanderGoal = null;

                state.nextWanderTick =
                        now + ThreadLocalRandom.current()
                                .nextInt(30, 81);

            } else {

                Vector direction = toward.normalize();

                GorillaFacing.faceDirection(
                        pig,
                        model,
                        direction
                );

                if (moveHorizontal(
                        pig,
                        direction,
                        WANDER_SPEED
                )) {

                    setMovement(
                            model,
                            state,
                            Movement.WALK
                    );

                } else {

                    state.wanderGoal = null;
                    state.nextWanderTick = now + 20;

                    setMovement(
                            model,
                            state,
                            Movement.IDLE
                    );
                }

                return;
            }
        }

        stopHorizontal(pig);

        setMovement(model, state, Movement.IDLE);

        if (now < state.nextWanderTick) {
            return;
        }

        double angle = ThreadLocalRandom.current()
                .nextDouble(0, Math.PI * 2);

        double radius = ThreadLocalRandom.current()
                .nextDouble(2.5, WANDER_RADIUS);

        state.wanderGoal = state.home.clone().add(
                Math.cos(angle) * radius,
                0,
                Math.sin(angle) * radius
        );

        state.nextWanderTick = now + 40;
    }

    // =====================================
    // 攻撃対象の選択
    // =====================================

    private Player chooseTarget(
            Pig pig,
            State state
    ) {

        List<Player> candidates =
                new ArrayList<>();

        Location origin = pig.getLocation();

        for (Player player :
                pig.getWorld().getPlayers()) {

            if (player.isDead()
                    || player.getGameMode()
                            == GameMode.SPECTATOR) {
                continue;
            }

            if (player.getLocation()
                    .distanceSquared(origin) <= DETECT_SQ) {

                candidates.add(player);
            }
        }

        if (candidates.isEmpty()) {

            state.targetId = null;
            state.nextTargetSelectionTick = 0;

            return null;
        }

        Player previous = state.targetId == null
                ? null
                : Bukkit.getPlayer(state.targetId);

        if (previous != null
                && candidates.contains(previous)
                && pig.getTicksLived()
                        < state.nextTargetSelectionTick) {

            return previous;
        }

        Player chosen = candidates.get(
                ThreadLocalRandom.current()
                        .nextInt(candidates.size())
        );

        state.targetId = chosen.getUniqueId();

        state.nextTargetSelectionTick =
                pig.getTicksLived() + 160;

        return chosen;
    }

    // =====================================
    // アニメーション制御
    // =====================================

    private void setMovement(
            ActiveModel model,
            State state,
            Movement wanted
    ) {

        if (wanted == Movement.NONE) {

            stopAnimation(model);
            state.movement = Movement.NONE;
            return;
        }

        String animation =
                wanted == Movement.WALK
                        ? "walk"
                        : "idle";

        boolean changed =
                state.movement != wanted;

        if (!changed && model.getAnimationHandler()
                .isPlayingAnimation(animation)) {
            return;
        }

        int now = (int) aiTicks;

        if (!changed
                && now < state.nextAnimationCheckTick) {
            return;
        }

        if (changed) {
            stopAnimation(model);
        }

        if (model.getAnimationHandler()
                .playAnimation(
                        animation,
                        0.12,
                        0.12,
                        1.0,
                        false
                ) != null) {

            state.movement = wanted;
            animations.put(model, wanted);

        } else {

            state.movement = Movement.NONE;
            animations.remove(model);
        }

        state.nextAnimationCheckTick = now + 10;
    }

    private void stopAnimation(
            ActiveModel model
    ) {

        Movement previous =
                animations.remove(model);

        if (previous == Movement.IDLE) {
            model.getAnimationHandler()
                    .stopAnimation("idle");
        }

        if (previous == Movement.WALK) {
            model.getAnimationHandler()
                    .stopAnimation("walk");
        }
    }

    // =====================================
    // 水平方向への移動
    // =====================================

    private boolean moveHorizontal(
            Pig pig,
            Vector direction,
            double speed
    ) {

        Location next = pig.getLocation().add(
                direction.getX() * speed,
                0,
                direction.getZ() * speed
        );

        if (!next.getBlock().isPassable()
                || !next.clone()
                        .add(0, 1, 0)
                        .getBlock().isPassable()) {

            lastMove = "BLOCKED";
            return false;
        }

        next.setYaw(
                GorillaFacing.yawFromDirection(
                        direction
                )
        );

        next.setPitch(0f);

        boolean success = pig.teleport(next);

        lastMove = success
                ? "MOVED"
                : "TELEPORT_FALSE";

        return success;
    }

    private void stopHorizontal(Pig pig) {

        pig.setVelocity(
                new Vector(
                        0,
                        pig.getVelocity().getY(),
                        0
                )
        );
    }
}
