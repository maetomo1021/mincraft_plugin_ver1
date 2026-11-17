
package com.example.m10practice;

import com.ticxo.modelengine.api.ModelEngineAPI;
import com.ticxo.modelengine.api.model.ActiveModel;
import com.ticxo.modelengine.api.model.ModeledEntity;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Pig;
import org.bukkit.entity.Player;
import org.bukkit.entity.Entity;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import org.bukkit.plugin.java.JavaPlugin;

public class ReindeerCommand implements CommandExecutor {

    private final JavaPlugin plugin;

    private static final double DETECT_RANGE = 8.0;
    private static final double FLEE_DISTANCE = 7.0;
    private static final double RUN_SPEED = 1.8;

    public ReindeerCommand(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(
            CommandSender sender,
            Command command,
            String label,
            String[] args) {

        if (!(sender instanceof Player player)) {
            sender.sendMessage("プレイヤー専用コマンドです。");
            return true;
        }

        ActiveModel model;

        try {
            model = ModelEngineAPI.createActiveModel("runreindeer");
        } catch (Exception e) {
            player.sendMessage("§cトナカイモデルの取得に失敗しました。");
            return true;
        }

        if (model == null) {
            player.sendMessage("§crunreindeer が見つかりません。");
            return true;
        }

        // プレイヤーの約2ブロック前方
        Vector forward = player.getLocation()
                .getDirection().setY(0);

        if (forward.lengthSquared() < 0.001) {
            forward = new Vector(1, 0, 0);
        }

        Location location = player.getLocation()
                .add(forward.normalize().multiply(2));

        Pig pig = player.getWorld().spawn(location, Pig.class);

        // 経路探索を使用するためAIはON
        pig.setAI(true);
        pig.setSilent(true);
        pig.setInvisible(true);
        pig.setInvulnerable(true);
        pig.setRemoveWhenFarAway(false);

        // 今後の識別用
        pig.addScoreboardTag("m10_reindeer");

        ModeledEntity modeledEntity =
                ModelEngineAPI.createModeledEntity(pig);

        modeledEntity.addModel(model, true);

        playAnimation(model, "idle", null);

        // 逃走処理を開始
        startFleeTask(pig, model);

        player.sendMessage(ChatColor.GREEN
                + "トナカイを召喚しました！");

        return true;
    }

    private void startFleeTask(Pig pig, ActiveModel model) {

        new BukkitRunnable() {

            private String currentAnimation = "idle";
            private boolean fleeing = false;

            @Override
            public void run() {

                if (!pig.isValid() || pig.isDead()) {
                    cancel();
                    return;
                }

                Player nearest = null;
                double closest = DETECT_RANGE * DETECT_RANGE;

                // 最も近いプレイヤーを探す
                for (Player player : pig.getWorld().getPlayers()) {

                    if (player.isDead()
                            || player.getGameMode()
                            == org.bukkit.GameMode.SPECTATOR) {
                        continue;
                    }

                    double distance = player.getLocation()
                            .distanceSquared(pig.getLocation());

                    if (distance < closest) {
                        closest = distance;
                        nearest = player;
                    }
                }

                // 近くにプレイヤーがいない場合
                if (nearest == null) {

                    if (fleeing) {
                        pig.getPathfinder().stopPathfinding();
                        fleeing = false;
                    }

                    if (!currentAnimation.equals("idle")) {
                        playAnimation(
                                model, "idle", currentAnimation);
                        currentAnimation = "idle";
                    }

                    return;
                }

                // プレイヤーから離れる方向
                Vector away = pig.getLocation().toVector()
                        .subtract(
                            nearest.getLocation().toVector()
                        );

                away.setY(0);

                if (away.lengthSquared() < 0.001) {
                    away = new Vector(1, 0, 0);
                }

                away.normalize();

                // 正面・左右斜め方向に逃走先を試す
                double[] angles = {0, 45, -45, 90, -90, 180};
                boolean pathStarted = false;

                for (double angle : angles) {

                    Vector offset = away.clone()
                            .rotateAroundY(Math.toRadians(angle))
                            .multiply(FLEE_DISTANCE);

                    Location destination = pig.getLocation()
                            .clone().add(offset);

                    // 簡易的な安全判定
                    if (!destination.getBlock().isPassable()
                            || !destination.clone()
                                .add(0, 1, 0)
                                .getBlock().isPassable()
                            || !destination.clone()
                                .add(0, -1, 0)
                                .getBlock().getType().isSolid()) {
                        continue;
                    }

                    // Paperの経路探索
                    if (pig.getPathfinder()
                            .moveTo(destination, RUN_SPEED)) {
                        pathStarted = true;
                        break;
                    }
                }

                if (pathStarted) {

                    fleeing = true;

                    if (!currentAnimation.equals("run")) {
                        playAnimation(
                                model, "run", currentAnimation);
                        currentAnimation = "run";
                    }

                } else if (!pig.getPathfinder().hasPath()) {

                    fleeing = false;

                    if (!currentAnimation.equals("idle")) {
                        playAnimation(
                                model, "idle", currentAnimation);
                        currentAnimation = "idle";
                    }
                }
            }

        }.runTaskTimer(plugin, 10L, 10L);
    }

    private void playAnimation(
            ActiveModel model,
            String next,
            String previous) {

        if (previous != null) {
            model.getAnimationHandler()
                    .stopAnimation(previous);
        }

        model.getAnimationHandler().playAnimation(
                next,
                0.1,
                0.1,
                1.0,
                false
        );
    }
}
