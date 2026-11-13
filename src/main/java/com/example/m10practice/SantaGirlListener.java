
package com.example.m10practice;

import com.ticxo.modelengine.api.events.BaseEntityInteractEvent;
import com.ticxo.modelengine.api.model.ActiveModel;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import java.util.Arrays;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

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

        // 5秒以内の連続操作を防ぐ
        long now = System.currentTimeMillis();
        long previous = cooldowns.getOrDefault(model, 0L);

        if (now - previous < 5000) {
            return;
        }

        cooldowns.put(model, now);

        // アニメーションが存在するか確認
        if (!model.getBlueprint().getAnimations()
                .containsKey("give_present")) {
            player.sendMessage(ChatColor.RED
                    + "give_present が見つかりません。");
            return;
        }

        // プレゼントを渡すアニメーションを再生
        model.getAnimationHandler().playAnimation(
                "give_present",
                0.1,
                0.1,
                1.0,
                true
        );

        player.sendMessage(ChatColor.LIGHT_PURPLE
                + "SantaGirl: プレゼントをどうぞ！");

        // 約3秒後にアイテムを渡す
        Bukkit.getScheduler().runTaskLater(plugin, () -> {

            if (!player.isOnline()) {
                return;
            }


        ItemStack present = new ItemStack(Material.AMETHYST_SHARD, 1);
        
        ItemMeta meta = present.getItemMeta();
        
        // 専用テクスチャを指定
        meta.setItemModel(
            NamespacedKey.fromString("m10practice:holy_night_crystal")
        );
        
        // アイテム名
        meta.setDisplayName(
            ChatColor.AQUA + "❄ 聖夜の結晶 ❄"
        );
        
        // 説明文
        meta.setLore(Arrays.asList(
            ChatColor.GRAY + "冬の夜にだけ現れる、不思議な結晶。",
            ChatColor.GRAY + "SantaGirlから授かった特別な贈り物。",
            "",
            ChatColor.LIGHT_PURPLE + "Christmas Limited Item",
            ChatColor.GOLD + "★ 特別報酬 ★"
        ));
        
        // 独自アイテムとして識別するID
        meta.getPersistentDataContainer().set(
            new NamespacedKey(plugin, "holy_night_crystal"),
            PersistentDataType.STRING,
            "holy_night_crystal"
        );
        
        present.setItemMeta(meta);

            // インベントリに入らない分は足元に落とす
            var leftover = player.getInventory().addItem(present);

            for (ItemStack item : leftover.values()) {
                player.getWorld().dropItemNaturally(
                        player.getLocation(), item
                );
            }

            player.sendMessage(ChatColor.AQUA
                    + "❄ 聖夜の結晶を受け取りました！");

        }, 60L);
    }
}
