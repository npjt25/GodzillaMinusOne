package com.npjt25.godzillaminusone.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.npjt25.godzillaminusone.GodzillaMinusOne;
import com.npjt25.godzillaminusone.entity.OdoIslandMonsterEntity;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

/**
 * The Odo Island Monster: Monster122's Godzillasaurus (tools/make_odo_model.py), drawn at the add-on's scale of 2,
 * about 14 blocks tall. Once nuked it is drawn with the add-on's burnt skin, whose embers glow in the cracks of
 * its hide and die down over a minute.
 */
public class OdoIslandMonsterRenderer extends GeoEntityRenderer<OdoIslandMonsterEntity> {
    public static final float SCALE = 2.0F;
    private static final ResourceLocation NORMAL = GodzillaMinusOne.id("textures/entity/odo_island_monster.png");
    private static final ResourceLocation BURNT = GodzillaMinusOne.id("textures/entity/odo_island_monster_burnt.png");
    private static final ResourceLocation EMBERS = GodzillaMinusOne.id("textures/entity/odo_island_monster_burnt_glow.png");

    public OdoIslandMonsterRenderer(EntityRendererProvider.Context context) {
        super(context, new DefaultedEntityGeoModel<>(GodzillaMinusOne.id("odo_island_monster")) {
            @Override
            public ResourceLocation getTextureResource(OdoIslandMonsterEntity entity) {
                return entity.isBurnt() ? BURNT : NORMAL;
            }
        });
        withScale(SCALE);
        this.shadowRadius = 3.5F;
        addRenderLayer(new GlowLayer(this));
    }

    private static class GlowLayer extends GeoRenderLayer<OdoIslandMonsterEntity> {
        GlowLayer(OdoIslandMonsterRenderer renderer) {
            super(renderer);
        }

        @Override
        public void render(PoseStack poseStack, OdoIslandMonsterEntity entity, BakedGeoModel model, RenderType renderType,
                           MultiBufferSource buffers, VertexConsumer buffer, float partialTick, int packedLight, int packedOverlay) {
            if (!entity.isBurnt()) {
                return;
            }
            float t = entity.getBurntSeconds(partialTick);
            float glow = Mth.clamp(1.0F - t / 60.0F, 0.0F, 1.0F) * (0.8F + 0.2F * Mth.sin(t * 3.0F));
            if (glow <= 0.01F) {
                return;
            }
            RenderType type = RenderType.eyes(EMBERS);
            getRenderer().reRender(model, poseStack, buffers, entity, type, buffers.getBuffer(type), partialTick,
                    LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, glow, glow, glow, 1.0F);
        }
    }
}
