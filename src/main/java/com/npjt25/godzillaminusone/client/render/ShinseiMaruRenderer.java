package com.npjt25.godzillaminusone.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.npjt25.godzillaminusone.GodzillaMinusOne;
import com.npjt25.godzillaminusone.entity.ShinseiMaruEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.util.Mth;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * The Shinsei Maru (tools/make_props.py, built at half size): she faces her heading and rides the swell, the stern gun
 * turns and tilts the way it's aimed, the rack on deck shows as many of her naval mines as she carries (up to three),
 * and she rocks when she's hit.
 */
public class ShinseiMaruRenderer extends GeoEntityRenderer<ShinseiMaruEntity> {
    public ShinseiMaruRenderer(EntityRendererProvider.Context context) {
        super(context, new DefaultedEntityGeoModel<ShinseiMaruEntity>(GodzillaMinusOne.id("shinsei_maru")) {
            @Override
            public void setCustomAnimations(ShinseiMaruEntity boat, long instanceId, AnimationState<ShinseiMaruEntity> state) {
                super.setCustomAnimations(boat, instanceId, state);
                getBone("gun").ifPresent(b -> b.setRotY(-boat.getGunYaw() * Mth.DEG_TO_RAD));
                getBone("gun_barrel").ifPresent(b -> b.setRotX(boat.getGunPitch() * Mth.DEG_TO_RAD));
                int mines = boat.getMinesAboard();
                for (int i = 1; i <= 3; i++) {
                    int n = i;
                    getBone("mine" + i).ifPresent(b -> b.setHidden(mines < n));
                }
            }
        });
        withScale(2.0F);
        this.shadowRadius = 3.0F;
    }

    /**
     * She turns with her heading (GeckoLib only turns living things by their body: she'd always face the same way),
     * pitches and rolls on the swell, and rocks when she's hit.
     */
    @Override
    protected void applyRotations(ShinseiMaruEntity boat, PoseStack poseStack, float ageInTicks, float rotationYaw, float partialTick) {
        super.applyRotations(boat, poseStack, ageInTicks, Mth.rotLerp(partialTick, boat.yRotO, boat.getYRot()), partialTick);
        float[] tilt = boat.swellTilt(boat.level().getGameTime() + partialTick);
        // (about her waterline, which sits her draft above her keel)
        float draft = (float) ShinseiMaruEntity.DRAFT;
        poseStack.translate(0.0F, draft, 0.0F);
        poseStack.mulPose(Axis.XP.rotationDegrees(-tilt[0]));
        poseStack.mulPose(Axis.ZP.rotationDegrees(tilt[1]));
        poseStack.translate(0.0F, -draft, 0.0F);
        float wobble = boat.getWobble() - partialTick;
        if (wobble > 0.0F) {
            poseStack.mulPose(Axis.ZP.rotationDegrees(Mth.sin(wobble) * wobble * 0.3F));
        }
    }
}
