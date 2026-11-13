
package com.example.m10practice;

import com.ticxo.modelengine.api.ModelEngineAPI;
import com.ticxo.modelengine.api.model.ActiveModel;
import com.ticxo.modelengine.api.model.ModeledEntity;

import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Pig;
import org.bukkit.entity.Player;

public class MiniGolemCommand implements CommandExecutor {

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

        // MiniGolemのモデルを生成
        ActiveModel model =
                ModelEngineAPI.createActiveModel("minigolem");

        if (model == null) {
            player.sendMessage("MiniGolemが見つかりません。");
            return true;
        }

        // プレイヤーの約2ブロック前方に召喚
        Location location = player.getLocation()
                .getBlock()
                .getRelative(player.getFacing(), 2)
                .getLocation()
                .add(0.5, 0, 0.5);

        // ベースとなるブタを生成
        Pig pig = location.getWorld().spawn(location, Pig.class);

        pig.setAI(false);
        pig.setSilent(true);
        pig.setRemoveWhenFarAway(false);
        pig.setInvisible(true);

        // ModelEngineのモデルを紐付ける
        ModeledEntity modeledEntity =
                ModelEngineAPI.createModeledEntity(pig);

        model.setHitboxVisible(true);
        modeledEntity.addModel(model, true);

        player.sendMessage("§aMiniGolemを召喚しました！");

        return true;
    }
}
