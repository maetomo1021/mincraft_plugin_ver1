
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

    private static final double DETECT_DISTANCE_SQ = 24.0 * 24.0;
    private static final double ATTACK_DISTANCE_SQ = 6.0 * 6.0;
    private static final double STOP_DISTANCE_SQ = 4.0 * 4.0;

    private static final double CHASE_SPEED = 0.10;
    private static final double WANDER_SPEED = 0.05;
    private static final double WANDER_RADIUS = 7.0;

    private static final int ATTACK_COOLDOWN_TICKS = 136;

    private enum Movement {
        NONE, IDLE, WALK
    }

    private enum Attack {
        NONE, BEAM, KNOCKBACK, JUMP
    }

    private static final class GorillaState {

        final Location home;

        Location wanderGoal;
        Location beamAnchor;

        int nextWanderTick;
        int nextAttackTick;
        int nextLongRangeRollTick;
        int nextTargetSelectionTick;
        int nextAnimationCheckTick;

        UUID targetId;

        Movement movement = Movement.NONE;
        Attack lastAttack = Attack.NONE;

        GorillaState(Location home) {
            this.home = home.clone();
        }
    }

    private final JavaPlugin plugin;
    private final GorillaCommand command;
    private final GorillaListener attackListener;
    private final GorillaFreezeBeam freezeBeam;
    private final GorillaJumpAttack jumpAttack;

    private final Map<UUID, GorillaState> states =
            new HashMap<>();

    private final Map<ActiveModel, Movement> animationStates =
            new IdentityHashMap<>();

    private long debugTicks = 0;
    private String lastMoveResult = "not attempted";
    private String lastTargetName = "none";

    public GorillaAutoAI(
            JavaPlugin plugin,
            GorillaCommand command,
            GorillaListener attackListener
    ) {
        this.plugin = plugin;
        this.command = command;
        this.attackListener = attackListener;

        this.freezeBeam = new GorillaFreezeBeam(plugin);
        this.jumpAttack = new GorillaJumpAttack(plugin);

        Bukkit.getScheduler().runTaskTimer(
                plugin,
                this::tick,
                20L,
                1L
        );
    }

    // =========================================
    // AIメインループ
    // =========================================

    private void tick() {

        Map<ActiveModel, UUID> gorillas =
                command.getSpawnedGorillas();

        int valid = 0;

        Set<UUID> livingIds = new HashSet<>();

        for (Map.Entry<ActiveModel, UUID> entry
                : gorillas.entrySet()) {

            ActiveModel model = entry.getKey();
            UUID id = entry.getValue();

            Entity entity = Bukkit.getEntity(id);

            if (!(entity instanceof Pig pig)
                    || !pig.isValid()
                    || !pig.getScoreboardTags()
                            .contains("m10_gorilla_test")) {

                animationStates.remove(model);
                continue;
            }

            valid++;
            livingIds.add(id);

            GorillaState state = states.computeIfAbsent(
                    id,
                    ignored -> new GorillaState(pig.getLocation())
            );

            // ジャンプ攻撃中
            if (jumpAttack.isAttacking(model)) {

                pauseMovement(model, pig, state);
                continue;
            }

            // ビーム攻撃中
            if (freezeBeam.isAttacking(model)) {

                if (state.beamAnchor != null
                        && pig.getLocation().distanceSquared(
                                state.beamAnchor
                        ) > 0.0004) {

                    pig.teleport(state.beamAnchor);

                    // 修正：固定位置への移動後に
                    // ModelEngine本体のYawを再同期する
                    GorillaFacing.syncModel(
                            model,
                            pig.getLocation().getYaw()
                    );
                }

                pig.setVelocity(new Vector(0, 0, 0));

                stopMovementAnimation(model);

                state.movement = Movement.NONE;
                continue;
            }

            state.beamAnchor = null;

            // ノックバック攻撃中
            if (attackListener.isAttacking(model)) {

                pauseMovement(model, pig, state);
                continue;
            }

            Player target = chooseTarget(pig, state);

            if (target != null) {

                lastTargetName = target.getName();

                state.wanderGoal = null;

                chaseOrAttack(
                        model,
                        id,
                        pig,
                        state,
                        target
                );

            } else {

                lastTargetName = "none";

                wander(model, pig, state);
            }
        }

        states.keySet().retainAll(livingIds);

        animationStates.keySet().retainAll(
                gorillas.keySet()
        );

        if (++debugTicks % 50 == 0) {

            plugin.getLogger().info(
                    "[GorillaAI DEBUG] registered="
                            + gorillas.size()
                            + " valid=" + valid
                            + " target=" + lastTargetName
                            + " move=" + lastMoveResult
            );
        }
    }

    // =========================================
    // 攻撃中の移動停止
    // =========================================

    private void pauseMovement(
            ActiveModel model,
            Pig pig,
            GorillaState state
    ) {

        stopHorizontalMovement(pig);
        stopMovementAnimation(model);

        state.movement = Movement.NONE;
    }

    // =========================================
    // プレイヤー追跡・攻撃
    // =========================================

    private void chaseOrAttack(
            ActiveModel model,
            UUID id,
            Pig pig,
            GorillaState state,
            Player target
    ) {

        Vector toTarget = target.getLocation()
                .toVector()
                .subtract(pig.getLocation().toVector())
                .setY(0);

        double distanceSq = toTarget.lengthSquared();

        if (distanceSq < 0.001) {

            stopHorizontalMovement(pig);

            setMovement(
                    model,
                    state,
                    Movement.IDLE
            );

            return;
        }

        Vector direction = toTarget.normalize();

        // まず攻撃方向に向ける
        // 移動した場合は移動完了後にも同期する
        GorillaFacing.faceDirection(
                pig,
                model,
                direction
        );

        boolean near =
                distanceSq <= ATTACK_DISTANCE_SQ;

        boolean inJumpRange =
                distanceSq <= 12.0 * 12.0;

        boolean inBeamRange =
                distanceSq <= 14.0 * 14.0;

        boolean cooldownDone =
                pig.getTicksLived()
                        >= state.nextAttackTick;

        if (cooldownDone && inBeamRange) {

            List<Attack> choices = new ArrayList<>();

            if (near) {

                choices.add(Attack.KNOCKBACK);
                choices.add(Attack.BEAM);
                choices.add(Attack.JUMP);

            } else if (inJumpRange) {

                choices.add(Attack.JUMP);
                choices.add(Attack.BEAM);

            } else {

                choices.add(Attack.BEAM);
            }

            // 直前と同じ攻撃をできるだけ避ける
            if (choices.size() > 1) {
                choices.remove(state.lastAttack);
            }

            Attack chosen = Attack.NONE;

            if (near) {

                chosen = choices.get(
                        ThreadLocalRandom.current()
                                .nextInt(choices.size())
                );

            } else if (
                    pig.getTicksLived()
                            >= state.nextLongRangeRollTick
            ) {

                state.nextLongRangeRollTick =
                        pig.getTicksLived() + 60;

                if (ThreadLocalRandom.current()
                        .nextInt(10) < 4
                        && !choices.isEmpty()) {

                    if (choices.size() == 1
                            && choices.get(0) == Attack.BEAM
                            && state.lastAttack == Attack.BEAM) {

                        chosen = Attack.NONE;

                    } else {

                        chosen = choices.get(
                                ThreadLocalRandom.current()
                                        .nextInt(choices.size())
                        );
                    }
                }
            }

            if (chosen != Attack.NONE) {

                stopHorizontalMovement(pig);
                stopMovementAnimation(model);

                model.getAnimationHandler()
                        .stopAnimation("walk");

                model.getAnimationHandler()
                        .stopAnimation("idle");

                state.movement = Movement.NONE;

                boolean started;

                if (chosen == Attack.BEAM) {

                    started = freezeBeam.start(
                            model,
                            id,
                            target
                    );

                } else if (chosen == Attack.JUMP) {

                    started = jumpAttack.start(
                            model,
                            id,
                            target
                    );

                } else {

                    started = attackListener.startKnockback(
                            model,
                            id,
                            null
                    );
                }

                if (started) {

                    state.lastAttack = chosen;

                    if (chosen == Attack.BEAM) {

                        state.beamAnchor =
                                pig.getLocation().clone();
                    }

                    state.nextAttackTick =
                            pig.getTicksLived()
                                    + ATTACK_COOLDOWN_TICKS;

                    plugin.getLogger().info(
                            "[GorillaAI] attack="
                                    + chosen
                                    + " target="
                                    + target.getName()
                    );

                    return;
                }
            }
        }

        // =====================================
        // 通常追跡
        // =====================================

        if (distanceSq <= STOP_DISTANCE_SQ) {

            stopHorizontalMovement(pig);

            setMovement(
                    model,
                    state,
                    Movement.IDLE
            );

        } else {

            // 修正：移動とモデルの向き同期を
            // 同じメソッド内で完了させる
            if (moveHorizontal(
                    model,
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
    }

    // =========================================
    // 徘徊
    // =========================================

    private void wander(
            ActiveModel model,
            Pig pig,
            GorillaState state
    ) {

        int now = pig.getTicksLived();

        Location position = pig.getLocation();

        if (state.wanderGoal != null) {

            Vector toward = state.wanderGoal
                    .toVector()
                    .subtract(position.toVector())
                    .setY(0);

            if (toward.lengthSquared() <= 1.0) {

                state.wanderGoal = null;

                state.nextWanderTick =
                        now + ThreadLocalRandom.current()
                                .nextInt(30, 81);

            } else {

                Vector direction = toward.normalize();

                if (moveHorizontal(
                        model,
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

        stopHorizontalMovement(pig);

        setMovement(
                model,
                state,
                Movement.IDLE
        );

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

    // =========================================
    // ターゲット選択
    // =========================================

    private Player chooseTarget(
            Pig pig,
            GorillaState state
    ) {

        Location origin = pig.getLocation();

        List<Player> candidates = new ArrayList<>();

        for (Player player :
                pig.getWorld().getPlayers()) {

            if (player.isDead()
                    || player.getGameMode()
                            == GameMode.SPECTATOR) {

                continue;
            }

            if (player.getLocation()
                    .distanceSquared(origin)
                    <= DETECT_DISTANCE_SQ) {

                candidates.add(player);
            }
        }

        if (candidates.isEmpty()) {

            state.targetId = null;
            state.nextTargetSelectionTick = 0;

            return null;
        }

        Player existing =
                state.targetId == null
                        ? null
                        : Bukkit.getPlayer(state.targetId);

        if (existing != null
                && candidates.contains(existing)
                && pig.getTicksLived()
                        < state.nextTargetSelectionTick) {

            return existing;
        }

        Player selected = candidates.get(
                ThreadLocalRandom.current()
                        .nextInt(candidates.size())
        );

        state.targetId = selected.getUniqueId();

        state.nextTargetSelectionTick =
                pig.getTicksLived() + 160;

        return selected;
    }

    // =========================================
    // 移動アニメーション
    // =========================================

    private void setMovement(
            ActiveModel model,
            GorillaState state,
            Movement wanted
    ) {

        if (wanted == Movement.NONE) {

            stopMovementAnimation(model);
            state.movement = Movement.NONE;
            return;
        }

        String name =
                wanted == Movement.WALK
                        ? "walk"
                        : "idle";

        boolean changed =
                state.movement != wanted;

        boolean actuallyPlaying =
                model.getAnimationHandler()
                        .isPlayingAnimation(name);

        if (!changed && actuallyPlaying) {
            return;
        }

        int now = (int) (debugTicks);

        if (!changed
                && now < state.nextAnimationCheckTick) {
            return;
        }

        if (changed) {
            stopMovementAnimation(model);
        }

        if (model.getAnimationHandler()
                .playAnimation(
                        name,
                        0.12,
                        0.12,
                        1.0,
                        false
                ) != null) {

            state.movement = wanted;

            animationStates.put(model, wanted);

            state.nextAnimationCheckTick = now + 10;

        } else {

            state.nextAnimationCheckTick = now + 10;

            state.movement = Movement.NONE;

            animationStates.remove(model);
        }
    }

    private void stopMovementAnimation(
            ActiveModel model
    ) {

        Movement previous =
                animationStates.remove(model);

        if (previous == Movement.IDLE) {

            model.getAnimationHandler()
                    .stopAnimation("idle");
        }

        if (previous == Movement.WALK) {

            model.getAnimationHandler()
                    .stopAnimation("walk");
        }
    }

    // =========================================
    // 移動処理
    // =========================================

    private boolean moveHorizontal(
            ActiveModel model,
            Pig pig,
            Vector direction,
            double speed
    ) {

        Location current = pig.getLocation();

        Location next = current.clone().add(
                direction.getX() * speed,
                0,
                direction.getZ() * speed
        );

        if (!next.getBlock().isPassable()
                || !next.clone().add(0, 1, 0)
                        .getBlock().isPassable()) {

            lastMoveResult = "BLOCKED";
            return false;
        }

        // 移動方向からYawを計算
        float yaw =
                GorillaFacing.yawFromDirection(direction);

        next.setYaw(yaw);
        next.setPitch(0f);

        // 先に土台を移動させる
        boolean success = pig.teleport(next);

        lastMoveResult =
                success
                        ? "MOVED"
                        : "TELEPORT_FALSE";

        if (!success) {
            return false;
        }

        // ★重要な修正
        // teleport完了後の土台の向きを基準に、
        // ModelEngineの本体・頭部の向きを同期する。
        GorillaFacing.faceYaw(
                pig,
                model,
                pig.getLocation().getYaw()
        );

        return true;
    }

    // =========================================
    // 移動停止
    // =========================================

    private void stopHorizontalMovement(Pig pig) {

        double y = pig.getVelocity().getY();

        pig.setVelocity(
                new Vector(0, y, 0)
        );
    }
}
