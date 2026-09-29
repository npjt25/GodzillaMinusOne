package com.npjt25.godzillaminusone.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.npjt25.godzillaminusone.GodzillaMinusOne;
import com.npjt25.godzillaminusone.entity.ShindenEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.util.Mth;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class ShindenRenderer extends GeoEntityRenderer<ShindenEntity> {
    public ShindenRenderer(EntityRendererProvider.Context context) {
        super(context, new DefaultedEntityGeoModel<>(GodzillaMinusOne.id("shinden")));
        this.shadowRadius = 3.0F;
    }

    /** Keeps the plane level in flight, leaning only into turns, and rocks it when it's hit (like a boat). */
    @Override
    protected void applyRotations(ShindenEntity entity, PoseStack poseStack, float ageInTicks, float rotationYaw, float partialTick) {
        super.applyRotations(entity, poseStack, ageInTicks, rotationYaw, partialTick);
        float hurt = entity.getWobbleTime() - partialTick;
        float damage = Math.max(0.0F, entity.getWobbleDamage() - partialTick);
        if (hurt > 0.0F) {
            poseStack.mulPose(Axis.ZP.rotationDegrees(Mth.sin(hurt) * hurt * damage / 10.0F * entity.getWobbleSide()));
        }
        float bank = entity.getBank(partialTick);
        if (bank != 0.0F) {
            // the model faces -z with its right wing along +x: a negative roll about z drops the right wing
            poseStack.translate(0.0F, 1.5F, 0.0F);
            poseStack.mulPose(Axis.ZP.rotationDegrees(-bank));
            poseStack.translate(0.0F, -1.5F, 0.0F);
        }
    }
}
