
package com.example.m10practice;

import com.ticxo.modelengine.api.ModelEngineAPI;
import com.ticxo.modelengine.api.model.ActiveModel;
import com.ticxo.modelengine.api.model.ModeledEntity;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Pig;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public class ReindeerGameManager {

    private final JavaPlugin plugin;
    private final Random random = new Random();
    private final Map<UUID, Pig> targets = new HashMap<>();

    private UUID playerId;
    private Location origin;
    private double spawnY;
    // アリーナ内側の矩形範囲（スポーンと移動を制限）
    private int minX, maxX, minZ, maxZ;
    private double maxRunRadius;
    private Vector forward;
    private Vector right;
    private ItemStack savedSlot;
    private BossBar bossBar;
    private BukkitTask timerTask;
    private BukkitTask moveTask;

    private boolean running = false;
    private boolean playing = false;
    private int seconds = 0;
    private int score = 0;

    public ReindeerGameManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean isRunning() {
        return running;
    }

    public boolean isPlaying(Player player) {
        return playing && playerId != null
                && playerId.equals(player.getUniqueId());
    }

    public boolean isTarget(Pig pig) {
        return targets.containsKey(pig.getUniqueId());
    }
    
    
    // 中央足場の位置を保存
    public void setCenter(Player player) {
    
        if (running) {
            player.sendMessage("§cゲーム中は変更できません。");
            return;
        }
    
        Location center = player.getLocation().clone();
    
        plugin.getConfig().set("reindeer.center", center);
        plugin.saveConfig();
    
        player.sendMessage(
                "§aトナカイ射的の中心地点を登録しました！"
        );
        player.sendMessage(
                "§7座標: "
                + center.getBlockX() + ", "
                + center.getBlockY() + ", "
                + center.getBlockZ()
        );
    }


    // 氷のフィールドの「内側」で、対角の2点を登録してください。
    public void setCorner(Player player, int number) {
        if (running) {
            player.sendMessage("§cゲーム中は設定できません。");
            return;
        }
        String key = "reindeer.arena.corner" + number;
        Location loc = player.getLocation();
        plugin.getConfig().set(key, loc);
        plugin.saveConfig();
        player.sendMessage("§aアリーナ内側の角" + number + "を登録しました: "
                + loc.getBlockX() + ", " + loc.getBlockZ());
    }

    public void start(Player player) {
        if (running) {
            player.sendMessage("§c現在ゲームが進行中です。");
            return;
        }

    // 登録した中央地点を取得
    Location savedCenter = plugin.getConfig()
            .getLocation("reindeer.center");
    
    if (savedCenter == null || savedCenter.getWorld() == null) {
        player.sendMessage(
                "§c先に /startreindeergame setcenter を実行してください。"
        );
        return;
    }
    
    if (!savedCenter.getWorld().equals(player.getWorld())) {
        player.sendMessage(
                "§c登録したアリーナと違うワールドにいます。"
        );
        return;
}

        Location corner1 = plugin.getConfig().getLocation("reindeer.arena.corner1");
        Location corner2 = plugin.getConfig().getLocation("reindeer.arena.corner2");
        if (corner1 == null || corner2 == null
                || !savedCenter.getWorld().equals(corner1.getWorld())
                || !savedCenter.getWorld().equals(corner2.getWorld())) {
            player.sendMessage("§c先に氷床の内側で setcorner1 と setcorner2 を登録してください。");
            return;
        }

        minX = Math.min(corner1.getBlockX(), corner2.getBlockX());
        maxX = Math.max(corner1.getBlockX(), corner2.getBlockX()) + 1;
        minZ = Math.min(corner1.getBlockZ(), corner2.getBlockZ());
        maxZ = Math.max(corner1.getBlockZ(), corner2.getBlockZ()) + 1;

        // トナカイの見た目が塀からはみ出さないように内側2ブロックに制限
        maxRunRadius = Math.min(Math.min(
                savedCenter.getX() - (minX + 2.0),
                (maxX - 2.0) - savedCenter.getX()),
                Math.min(savedCenter.getZ() - (minZ + 2.0),
                (maxZ - 2.0) - savedCenter.getZ()));
        if (maxRunRadius < 5.0) {
            player.sendMessage("§c中心点とアリーナ範囲が合っていません。"
                    + "中心を内側に登録するか、角を広げてください。");
            return;
        }

        running = true;
        playing = false;
        seconds = 0;
        score = 0;
        playerId = player.getUniqueId();
        origin = savedCenter.clone();
        // ゲーム開始者が立つ床面の高さを出現基準にする
        spawnY = player.getLocation().getY();

        forward = origin.getDirection().setY(0);
        if (forward.lengthSquared() < 0.001) {
            forward = new Vector(0, 0, 1);
        }
        forward.normalize();

        right = new Vector(
                -forward.getZ(), 0, forward.getX()
        );

        // 元のホットバー1番目を保存
        savedSlot = player.getInventory().getItem(0);
        if (savedSlot != null) {
            savedSlot = savedSlot.clone();
        }

        player.getInventory().setItem(
                0, new ItemStack(Material.SNOWBALL, 64)
        );
        player.getInventory().setHeldItemSlot(0);

        bossBar = Bukkit.createBossBar(
                "§bトナカイ射的 開始まで3秒",
                BarColor.BLUE, BarStyle.SOLID
        );
        bossBar.addPlayer(player);

        timerTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline()) {
                    stop(false);
                    return;
                }

                if (seconds < 3) {
                    int remaining = 3 - seconds;

                    bossBar.setTitle(
                            "§b開始まで " + remaining + " 秒"
                    );
                    player.sendTitle(
                            "§b" + remaining, "",
                            0, 15, 5
                    );
                } else if (seconds == 3) {
                    playing = true;
                    player.sendTitle(
                            "§aSTART!",
                            "§f雪玉を当ててください",
                            0, 20, 10
                    );
                    startMovement();
                }

                if (playing) {
                    int elapsed = seconds - 3;

                    if (elapsed >= 10) {
                        stop(true);
                        return;
                    }

                    bossBar.setTitle(
                            "§b残り " + (10 - elapsed)
                            + " 秒 §e| §fスコア " + score
                    );
                    bossBar.setProgress(
                            Math.max(0.0,
                                    (10.0 - elapsed) / 10.0)
                    );

                    // 2秒ごとに追加出現
                    if (elapsed % 2 == 0 && targets.size() < 3) {
                        spawnTarget();
                    }

                    // 雪玉が少なくなったら補充
                    ItemStack ammo = player.getInventory()
                            .getItem(0);

                    if (ammo == null
                            || ammo.getType() != Material.SNOWBALL
                            || ammo.getAmount() < 16) {
                        player.getInventory().setItem(
                                0, new ItemStack(
                                        Material.SNOWBALL, 64
                                )
                        );
                    }
                }

                seconds++;
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    
// 開始者の足元Y座標で、壁や空洞ではない出現地点だけを採用する。

private Location findFloorLocation(double x, double z) {
    if (origin == null || origin.getWorld() == null) {
        return null;
    }

    // アリーナの内側のみ許可。外周の塀やその外側は対象外。
    if (x < minX + 1.5 || x > maxX - 1.5
            || z < minZ + 1.5 || z > maxZ - 1.5) {
        return null;
    }

    // 開始時に氷床に立っていたプレイヤーの足元Yに固定
    Location spawn = new Location(origin.getWorld(), x, spawnY, z);

    // 壁や空洞を避ける（壁の上へ高さを補正しない）
    if (!spawn.getBlock().isPassable()
            || !spawn.clone().add(0, 1, 0).getBlock().isPassable()
            || !spawn.clone().add(0, -1, 0).getBlock().getType().isSolid()) {
        return null;
    }
    return spawn;
}

private void spawnTarget() {
    if (origin == null) {
        plugin.getLogger().warning("トナカイ射的: 中心座標が未設定です。");
        return;
    }

    // 角度と半径を変えて複数回試し、壁際や空洞へのスポーンを避ける。
    Location spawn = null;
    double startAngle = 0;
    double radius = 0;
    for (int attempt = 0; attempt < 16; attempt++) {
        double candidateAngle = random.nextDouble() * Math.PI * 2.0;
        // 境界から2ブロック以上内側の円だけを使用する
        double usableRadius = Math.min(9.0, maxRunRadius);
        double candidateRadius = 5.0
                + random.nextDouble() * Math.max(0.01, usableRadius - 5.0);
        double x = origin.getX() + Math.cos(candidateAngle) * candidateRadius;
        double z = origin.getZ() + Math.sin(candidateAngle) * candidateRadius;
        Location candidate = findFloorLocation(x, z);
        if (candidate != null) {
            spawn = candidate;
            startAngle = candidateAngle;
            radius = candidateRadius;
            break;
        }
    }
    if (spawn == null) {
        plugin.getLogger().warning("トナカイ射的: 出現可能な地面が見つかりません。中心="
                + origin.getBlockX() + "," + origin.getBlockY() + "," + origin.getBlockZ());
        return;
    }

    ActiveModel model;
    try {
        model = ModelEngineAPI.createActiveModel("runreindeer");
    } catch (Exception e) {
        plugin.getLogger().warning("トナカイモデル生成失敗: " + e.getMessage());
        return;
    }
    if (model == null) {
        plugin.getLogger().warning("runreindeer が見つかりません。");
        return;
    }

    int direction = random.nextBoolean() ? 1 : -1;
    double angularSpeed = 0.018 * direction;
    Pig pig = spawn.getWorld().spawn(spawn, Pig.class);
    pig.setAI(false);
    pig.setSilent(true);
    pig.setInvisible(true);
    pig.setInvulnerable(true);
    pig.setRemoveWhenFarAway(false);
    pig.addScoreboardTag("m10_reindeer_target");

    ModeledEntity modeled = ModelEngineAPI.createModeledEntity(pig);
    modeled.addModel(model, true);
    model.getAnimationHandler().playAnimation("run", 0.1, 0.1, 1.0, true);
    targets.put(pig.getUniqueId(), pig);

    plugin.getLogger().info("トナカイ射的: 出現成功 "
            + spawn.getBlockX() + "," + spawn.getBlockY() + "," + spawn.getBlockZ());

    final double initialAngle = startAngle;
    final double targetRadius = radius;
    new BukkitRunnable() {
        private int ticks = 0;
        private double angle = initialAngle;

        @Override
        public void run() {
            if (!playing || !pig.isValid() || !targets.containsKey(pig.getUniqueId())) {
                cancel();
                return;
            }
            if (ticks >= 120) {
                removeTarget(pig);
                cancel();
                return;
            }
            angle += angularSpeed;
            double x = origin.getX() + Math.cos(angle) * targetRadius;
            double z = origin.getZ() + Math.sin(angle) * targetRadius;
            Location next = findFloorLocation(x, z);
            if (next == null) {
                removeTarget(pig);
                cancel();
                return;
            }
            double tangentX = -Math.sin(angle) * direction;
            double tangentZ = Math.cos(angle) * direction;
            next.setDirection(new Vector(tangentX, 0, tangentZ));
            pig.teleport(next);
            ticks += 2;
        }
    }.runTaskTimer(plugin, 0L, 2L);
}

private void startMovement() {

    // 毎tick、射的用トナカイの周囲を確認
    moveTask = new BukkitRunnable() {

        @Override
        public void run() {

            if (!playing) {
                return;
            }

            Player gamePlayer = Bukkit.getPlayer(playerId);

            if (gamePlayer == null || !gamePlayer.isOnline()) {
                return;
            }

            // targetsをコピーして安全に走査
            for (Pig pig : new java.util.ArrayList<>(
                    targets.values())) {

                if (!pig.isValid()) {
                    targets.remove(pig.getUniqueId());
                    continue;
                }

                // 土台のPigの当たり判定を少し拡大
                var hitbox = pig.getBoundingBox()
                        .clone()
                        .expand(0.8, 1.2, 0.8);

                // トナカイ付近の飛び道具を検索
                for (var entity : pig.getNearbyEntities(
                        2.0, 2.5, 2.0)) {

                    if (!(entity instanceof
                            org.bukkit.entity.Snowball snowball)) {
                        continue;
                    }

                    // ゲーム参加者の雪玉だけ有効
                    if (!(snowball.getShooter()
                            instanceof Player shooter)) {
                        continue;
                    }

                    if (!shooter.getUniqueId()
                            .equals(playerId)) {
                        continue;
                    }

                    // 雪玉が拡張当たり判定に入ったか
                    if (!hitbox.contains(
                            snowball.getLocation().toVector())) {
                        continue;
                    }

                    // 命中処理
                    hit(shooter, pig);

                    // 命中した雪玉を削除
                    snowball.remove();

                    break;
                }
            }
        }

    }.runTaskTimer(plugin, 0L, 1L);
}



    public void hit(Player player, Pig pig) {
    
        if (!isPlaying(player) || !isTarget(pig)) {
            return;
        }
    
        // スコアを1点追加
        score++;
    
        // 命中したトナカイを削除
        removeTarget(pig);
    
        // 命中メッセージ
        player.sendActionBar(
            "§a命中！ §e+1点 §f合計: " + score
        );
    
        // 効果音
        player.playSound(
            player.getLocation(),
            Sound.ENTITY_EXPERIENCE_ORB_PICKUP,
            1.0f,
            1.3f
        );
    
        // ★ NEW：倒した瞬間に次のトナカイを出現
        if (playing && targets.size() < 3) {
            spawnTarget();
        }
    }


    private void removeTarget(Pig pig) {
        targets.remove(pig.getUniqueId());
        if (pig.isValid()) {
            pig.remove();
        }
    }

    public void stop(boolean showResult) {
        if (!running) {
            return;
        }

        running = false;
        playing = false;

        if (timerTask != null) {
            timerTask.cancel();
            timerTask = null;
        }

        if (moveTask != null) {
            moveTask.cancel();
            moveTask = null;
        }

        for (Pig pig : targets.values()) {
            if (pig.isValid()) {
                pig.remove();
            }
        }
        targets.clear();

        Player player = playerId == null
                ? null : Bukkit.getPlayer(playerId);

        if (player != null) {
            player.getInventory().setItem(0, savedSlot);

            if (showResult) {
                player.sendTitle(
                        "§6FINISH!",
                        "§e最終スコア: " + score + "点",
                        5, 60, 15
                );
                player.sendMessage(
                        "§b[トナカイ射的] §f獲得スコア: "
                        + score + "点"
                );
            }
        }

        if (bossBar != null) {
            bossBar.removeAll();
            bossBar = null;
        }

        playerId = null;
        savedSlot = null;
    }
}
