
package com.example.m10practice;

import com.ticxo.modelengine.api.ModelEngineAPI;
import com.ticxo.modelengine.api.model.ActiveModel;
import com.ticxo.modelengine.api.model.ModeledEntity;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Pig;
import org.bukkit.entity.Player;

public class SantaGirlCommand implements CommandExecutor {

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

        // ModelEngineに登録済みのモデルを取得
        ActiveModel model;
        try {
            model = ModelEngineAPI.createActiveModel("santamini");
        } catch (Exception e) {
            player.sendMessage(ChatColor.RED
                    + "SantaGirlのモデルが見つかりません。");
            return true;
        }

        // プレイヤーの近くに召喚
        Pig pig = player.getWorld().spawn(
                player.getLocation().add(1, 0, 1),
                Pig.class
        );

        // 勝手に歩かないようにする
        pig.setAI(false);
        pig.setSilent(true);
        pig.setInvisible(true);
        pig.setInvulnerable(true);

        // PigにSantaGirlモデルを装着
        ModeledEntity modeledEntity =
                ModelEngineAPI.createModeledEntity(pig);

        modeledEntity.addModel(model, true);
        // 召喚直後はプレゼントBOXを非表示
        model.getBone("gift_box").ifPresent(bone -> {
            bone.setVisible(false);
        });
        
        // 通常時のアニメーションを明示的に再生
        model.getAnimationHandler().playAnimation(
            "idle",
            0.0,
            0.0,
            1.0,
            true
        );
        
        player.sendMessage(ChatColor.GREEN
                + "SantaGirlを召喚しました！");

        return true;
    }
}
