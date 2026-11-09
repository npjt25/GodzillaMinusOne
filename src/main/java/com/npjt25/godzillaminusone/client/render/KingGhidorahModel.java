package com.npjt25.godzillaminusone.client.render;

import com.npjt25.godzillaminusone.GodzillaMinusOne;
import com.npjt25.godzillaminusone.entity.KingGhidorahEntity;
import net.minecraft.util.Mth;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.model.data.EntityModelData;

/**
 * King Ghidorah's model (tools/make_ghidorah.py: geo, texture and animations). On top of whatever he's doing, each
 * head has a mind of its own: it looks about at its own pace (its own rhythms, out of step with the others), turns
 * towards what he's looking at (the middle head most, the outer two less, and not quite where it looks), and settles
 * down while he bites, roars or fires.
 */
public class KingGhidorahModel extends DefaultedEntityGeoModel<KingGhidorahEntity> {
    /** Per head (middle, right, left): yaw and pitch rhythms (radians a second), phases, how far it wanders, how much it follows his gaze. */
    private static final float[][] MINDS = {
            {0.53F, 0.31F, 0.0F, 1.0F, 0.75F},
            {0.71F, 0.43F, 2.1F, 1.25F, 0.5F},
            {0.37F, 0.59F, 4.0F, 1.15F, 0.5F}};
    private static final String[] HEADS = {"c", "r", "l"};

    public KingGhidorahModel() {
        super(GodzillaMinusOne.id("king_ghidorah"));
    }

    @Override
    public void setCustomAnimations(KingGhidorahEntity entity, long instanceId, AnimationState<KingGhidorahEntity> state) {
        super.setCustomAnimations(entity, instanceId, state);
        float t = (entity.tickCount + state.getPartialTick()) / 20.0F + (entity.getId() % 97);
        // (his entrance has its heads' every move worked out: they're left to it; anything else he does, they settle)
        float calm = entity.inEntrance() || entity.isDeadOrDying() ? 0.0F : entity.isBusy() ? 0.25F : 1.0F;
        EntityModelData data = state.getData(DataTickets.ENTITY_MODEL_DATA);
        float follow = calm > 0.0F ? 1.0F : 0.0F;
        float lookYaw = data != null ? Mth.clamp(data.netHeadYaw(), -60.0F, 60.0F) * follow : 0.0F;
        float lookPitch = data != null ? Mth.clamp(data.headPitch(), -30.0F, 30.0F) * follow : 0.0F;
        for (int k = 0; k < 3; k++) {
            float[] m = MINDS[k];
            // its own glances about: a slow sweep, a quicker twitch, now and then a long look one way
            float yaw = (14.0F * Mth.sin(t * m[0] + m[2]) + 5.0F * Mth.sin(t * m[0] * 3.7F + m[2] * 1.3F)
                    + 8.0F * Mth.sin(t * 0.11F + m[2]) * Mth.sin(t * 0.11F + m[2])) * m[3] * calm;
            float pitch = (6.0F * Mth.sin(t * m[1] + m[2] * 0.7F) + 2.0F * Mth.sin(t * m[1] * 2.9F)) * m[3] * calm;
            float tilt = 6.0F * Mth.sin(t * m[1] * 0.8F + m[2]) * calm;
            float followYaw = lookYaw * m[4] * (k == 0 ? 1.0F : 0.85F);
            float followPitch = lookPitch * m[4];
            String h = HEADS[k];
            float headYaw = yaw * 0.55F + followYaw * 0.6F;
            float headPitch = pitch * 0.6F + followPitch * 0.6F;
            getBone("head_" + h).ifPresent(b -> {
                b.setRotY(b.getRotY() + headYaw * Mth.DEG_TO_RAD);
                b.setRotX(b.getRotX() + headPitch * Mth.DEG_TO_RAD);
                b.setRotZ(b.getRotZ() + tilt * Mth.DEG_TO_RAD);
            });
            // the rest of the turn spread down the top of its neck
            for (int seg = 5; seg <= 8; seg++) {
                float share = (seg - 4) / 10.0F;
                float neckYaw = (yaw * 0.45F + followYaw * 0.4F) * share;
                float neckPitch = (pitch * 0.4F + followPitch * 0.4F) * share;
                getBone("neck_" + h + "_" + seg).ifPresent(b -> {
                    b.setRotY(b.getRotY() + neckYaw * Mth.DEG_TO_RAD);
                    b.setRotX(b.getRotX() + neckPitch * Mth.DEG_TO_RAD);
                });
            }
        }
    }
}
