package com.example.m10practice;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** テスト版: ゴリラ専用HPとBossBarを管理する。 */
public final class GorillaBossManager {
    // ここだけ変更すれば新しく召喚したボスの最大HPを変えられる
    public static final double MAX_HP = 150000.0;
    private static final double BAR_DISTANCE_SQUARED = 64 * 64;
    private final JavaPlugin plugin;
    private final Map<UUID, BossData> bosses = new HashMap<>();

    private static final class BossData {
        final BossBar bar;
        double hp;
        BossData(BossBar bar) {
            this.bar = bar;
            this.hp = MAX_HP;
        }
    }

    public GorillaBossManager(JavaPlugin plugin) {
        this.plugin = plugin;
        // エンティティ削除と画面表示を1秒ごとに検査
        Bukkit.getScheduler().runTaskTimer(plugin, this::updateBars, 20L, 20L);
    }

    public void register(Entity entity) {
        UUID uuid = entity.getUniqueId();
        remove(uuid);
        BossBar bar = Bukkit.createBossBar("暴走ゴリラ", BarColor.RED, BarStyle.SEGMENTED_20);
        BossData data = new BossData(bar);
        bosses.put(uuid, data);
        refresh(data);
        updateBars();
    }

    public Set<UUID> getTrackedIds() {
        return new HashSet<>(bosses.keySet());
    }

    public boolean isBoss(UUID uuid) {
        return bosses.containsKey(uuid);
    }

    public double getHp(UUID uuid) {
        BossData data = bosses.get(uuid);
        return data == null ? 0 : data.hp;
    }

    public void damage(UUID uuid, double amount, Player attacker) {
        BossData data = bosses.get(uuid);
        if (data == null || amount <= 0 || !Double.isFinite(amount)) return;
        data.hp = Math.max(0, data.hp - amount);
        refresh(data);
        if (attacker != null) {
            attacker.sendActionBar("§c暴走ゴリラ §f" + (long)Math.ceil(data.hp) + " / " + (long)MAX_HP + " HP §7(-" + String.format(java.util.Locale.ROOT, "%.1f", amount) + ")");
        }
        if (data.hp <= 0) {
            Entity entity = Bukkit.getEntity(uuid);
            if (entity != null && entity.isValid()) {
                World world = entity.getWorld();
                world.strikeLightningEffect(entity.getLocation());
                world.getPlayers().stream()
                    .filter(player -> player.getLocation().distanceSquared(entity.getLocation()) <= BAR_DISTANCE_SQUARED)
                    .forEach(player -> player.sendMessage("§6§l暴走ゴリラ討伐成功！"));
                entity.remove();
            }
            remove(uuid);
        }
    }

    private void refresh(BossData data) {
        data.bar.setProgress(Math.max(0, Math.min(1, data.hp / MAX_HP)));
        data.bar.setTitle("§c§l暴走ゴリラ §f" + (long)Math.ceil(data.hp) + " / " + (long)MAX_HP + " HP");
    }

    private void updateBars() {
        for (UUID uuid : new HashSet<>(bosses.keySet())) {
            Entity entity = Bukkit.getEntity(uuid);
            if (entity == null || !entity.isValid()) {
                remove(uuid);
                continue;
            }
            BossData data = bosses.get(uuid);
            if (data == null) continue;
            for (Player player : Bukkit.getOnlinePlayers()) {
                boolean nearby = player.getWorld().equals(entity.getWorld())
                    && player.getLocation().distanceSquared(entity.getLocation()) <= BAR_DISTANCE_SQUARED;
                if (nearby && !data.bar.getPlayers().contains(player)) data.bar.addPlayer(player);
                if (!nearby && data.bar.getPlayers().contains(player)) data.bar.removePlayer(player);
            }
        }
    }

    public void remove(UUID uuid) {
        BossData old = bosses.remove(uuid);
        if (old != null) {
            old.bar.removeAll();
            old.bar.setVisible(false);
        }
    }

    public void shutdown() {
        // Shutdown時もBossBarとModelEngineの表示を残さない
        for (UUID uuid : getTrackedIds()) {
            GorillaRemoveCommand.removeGorilla(uuid, this, plugin);
        }
    }
}
