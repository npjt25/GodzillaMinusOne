package com.npjt25.godzillaminusone.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.npjt25.godzillaminusone.GodzillaMinusOne;
import com.npjt25.godzillaminusone.entity.NavalMineEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.util.Mth;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class NavalMineRenderer extends GeoEntityRenderer<NavalMineEntity> {
    public NavalMineRenderer(EntityRendererProvider.Context context) {
        super(context, new DefaultedEntityGeoModel<>(GodzillaMinusOne.id("naval_mine")));
        withScale(1.3F); // add-on: minecraft:scale 1.3
        this.shadowRadius = 1.5F;
    }

    /** It rocks when it's hit, like a boat. */
    @Override
    protected void applyRotations(NavalMineEntity entity, PoseStack poseStack, float ageInTicks, float rotationYaw, float partialTick) {
        super.applyRotations(entity, poseStack, ageInTicks, rotationYaw, partialTick);
        float wobble = entity.getWobble() - partialTick;
        if (wobble > 0.0F) {
            poseStack.translate(0.0F, 1.0F, 0.0F);
            poseStack.mulPose(Axis.ZP.rotationDegrees(Mth.sin(wobble) * wobble * 1.2F));
            poseStack.translate(0.0F, -1.0F, 0.0F);
        }
    }
}
