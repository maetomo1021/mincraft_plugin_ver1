package com.example.m10practice;

import com.ticxo.modelengine.api.model.ActiveModel;
import com.ticxo.modelengine.api.model.ModeledEntity;
import org.bukkit.entity.Pig;
import org.bukkit.util.Vector;

/**
 * Keeps the invisible Bukkit base and the visible ModelEngine body facing
 * the same direction. All angles are Bukkit yaw values, in degrees.
 */
public final class GorillaFacing {

    private GorillaFacing() {}

    public static float yawFromDirection(Vector direction) {
        return (float) Math.toDegrees(Math.atan2(
                -direction.getX(), direction.getZ()
        ));
    }

    public static void faceDirection(Pig pig, ActiveModel model, Vector direction) {
        if (direction.lengthSquared() < 0.000001) {
            return;
        }
        faceYaw(pig, model, yawFromDirection(direction));
    }

    public static void faceYaw(Pig pig, ActiveModel model, float yaw) {
        pig.setRotation(yaw, 0f);
        syncModel(model, yaw);
    }

    public static void syncModel(ActiveModel model, float yaw) {
        if (model == null) {
            return;
        }
        ModeledEntity modeled = model.getModeledEntity();
        if (modeled != null && !modeled.isDestroyed()) {
            // Updating only Pig yaw does not immediately turn the rendered body.
            modeled.setYBodyRotImmediately(yaw);
            modeled.setYHeadRotImmediately(yaw);
        }
    }
}
