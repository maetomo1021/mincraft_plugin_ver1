
package com.example.m10practice;

import com.ticxo.modelengine.api.events.BaseEntityInteractEvent;
import com.ticxo.modelengine.api.model.ActiveModel;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class MiniGolemListener implements Listener {

    private final JavaPlugin plugin;
    private final Set<UUID> busy = new HashSet<>();

    public MiniGolemListener(JavaPlugin plugin) {
        this.plugin = plugin;
    }


    @EventHandler
    public void onModelClick(BaseEntityInteractEvent event) {
    
        plugin.getLogger().info(
            "MiniGolemクリック検知: action="
            + event.getAction()
            + ", model="
            + event.getModel().getBlueprint().getName()
            + ", slot="
            + event.getSlot()
        );

        // 攻撃ではなく右クリックのみ
        if (event.getAction()
                == BaseEntityInteractEvent.Action.ATTACK) {
            return;
        }

        // オフハンドによる重複実行を防止
        if (event.getSlot() != EquipmentSlot.HAND) {
            return;
        }

        ActiveModel model = event.getModel();

        // MiniGolemだけを対象とする
        if (!model.getBlueprint().getName()
                .equalsIgnoreCase("minigolem")) {
            return;
        }

        UUID entityId = event.getPlayer().getUniqueId();

        // アニメーション中の連打を防止
        if (!busy.add(entityId)) {
            return;
        }

        Player player = event.getPlayer();

        player.sendMessage("MiniGolemが飴を取り出しています...");

        // 腕のアニメーション再生
        var animation = model.getAnimationHandler()
                .playAnimation("give_candy", 0.1, 0.1, 1.0, true);

        if (animation == null) {
            player.sendMessage("give_candyを再生できませんでした。");
            busy.remove(entityId);
            return;
        }

        // 2秒後に飴を渡す
        plugin.getServer().getScheduler().runTaskLater(
                plugin,
                () -> {
                    busy.remove(entityId);

                    if (!player.isOnline()) {
                        return;
                    }

                    ItemStack candy = new ItemStack(Material.SUGAR, 1);
                    ItemMeta meta = candy.getItemMeta();
                    meta.setDisplayName("§dハロウィンの飴");
                    candy.setItemMeta(meta);

                    var leftovers = player.getInventory().addItem(candy);

                    // インベントリ満杯なら足元に落とす
                    leftovers.values().forEach(item ->
                            player.getWorld().dropItemNaturally(
                                    player.getLocation(), item));

                    player.sendMessage("§aMiniGolemから飴をもらいました！");
                },
                40L
        );
    }
}
