
package com.example.m10practice;

import com.ticxo.modelengine.api.events.BaseEntityInteractEvent;
import com.ticxo.modelengine.api.model.ActiveModel;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.Arrays;
import java.util.Map;
import java.util.WeakHashMap;

public class SantaGirlListener implements Listener {

    private final Main plugin;

    // SantaGirlごとの連打防止
    private final Map<ActiveModel, Long> cooldowns =
            new WeakHashMap<>();

    public SantaGirlListener(Main plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onSantaClick(BaseEntityInteractEvent event) {

        ActiveModel model = event.getModel();

        // SantaGirl以外は対象外
        if (!model.getBlueprint().getName()
                .equalsIgnoreCase("santamini")) {
            return;
        }

        // 右クリック、メインハンドのみ
        if (event.getAction()
                != BaseEntityInteractEvent.Action.INTERACT_ON) {
            return;
        }

        if (event.getSlot() != EquipmentSlot.HAND) {
            return;
        }

        Player player = event.getPlayer();

        // 約6秒間の連打防止
        long now = System.currentTimeMillis();
        long previous = cooldowns.getOrDefault(model, 0L);

        if (now - previous < 6000) {
            return;
        }

        // アニメーションが存在するか確認
        if (!model.getBlueprint().getAnimations()
                .containsKey("give_present")) {
            player.sendMessage(ChatColor.RED
                    + "give_present が見つかりません。");
            return;
        }

        cooldowns.put(model, now);

        // 開始時は必ずBOXを非表示
        model.getBone("gift_box").ifPresent(bone -> {
            bone.setVisible(false);
        });

        // プレゼントを渡すアニメーション
        model.getAnimationHandler().playAnimation(
                "give_present",
                0.1,
                0.1,
                1.0,
                true
        );

        player.sendMessage(ChatColor.LIGHT_PURPLE
                + "SantaGirl: プレゼントをどうぞ！");

        // 2.5秒後：BOXを表示
        Bukkit.getScheduler().runTaskLater(plugin, () -> {

            model.getBone("gift_box").ifPresentOrElse(bone -> {

                bone.setVisible(true);

                plugin.getLogger().info(
                    "SantaGirl: gift_box表示ON"
                );

            }, () -> {

                plugin.getLogger().warning(
                    "SantaGirl: gift_boxが見つかりません"
                );

            });

        }, 50L);

        // 4秒後：聖夜の結晶を渡す
        Bukkit.getScheduler().runTaskLater(plugin, () -> {

            if (!player.isOnline()) {
                return;
            }

            ItemStack present =
                    new ItemStack(Material.AMETHYST_SHARD, 1);

            ItemMeta meta = present.getItemMeta();

            // 聖夜の結晶の専用モデル
            meta.setItemModel(
                NamespacedKey.fromString(
                    "m10practice:holy_night_crystal"
                )
            );

            // アイテム名
            meta.setDisplayName(
                ChatColor.AQUA + "❄ 聖夜の結晶 ❄"
            );

            // 説明文
            meta.setLore(Arrays.asList(
                ChatColor.GRAY
                    + "冬の夜にだけ現れる、不思議な結晶。",
                ChatColor.GRAY
                    + "SantaGirlから授かった特別な贈り物。",
                "",
                ChatColor.LIGHT_PURPLE
                    + "Christmas Limited Item",
                ChatColor.GOLD
                    + "★ 特別報酬 ★"
            ));

            // 独自アイテムID
            meta.getPersistentDataContainer().set(
                new NamespacedKey(
                    plugin,
                    "holy_night_crystal"
                ),
                PersistentDataType.STRING,
                "holy_night_crystal"
            );

            present.setItemMeta(meta);

            // インベントリに入らない場合は足元へ落とす
            var leftover =
                    player.getInventory().addItem(present);

            for (ItemStack item : leftover.values()) {
                player.getWorld().dropItemNaturally(
                    player.getLocation(),
                    item
                );
            }

            player.sendMessage(ChatColor.AQUA
                    + "❄ 聖夜の結晶を受け取りました！");

        }, 80L);

        // 4.5秒後：BOXを非表示
        Bukkit.getScheduler().runTaskLater(plugin, () -> {

            model.getBone("gift_box").ifPresent(bone -> {
                bone.setVisible(false);
            });

            plugin.getLogger().info(
                "SantaGirl: gift_box表示OFF"
            );

        }, 110L);
    }
}
