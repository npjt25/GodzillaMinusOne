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
 * The Odo Island Monster (tools/make_odo_model.py), scaled to stand 15 blocks tall. Its eyes glow; once it has
 * been nuked it is drawn charred, with embers glowing in the cracks of its hide that die down over a minute.
 */
public class OdoIslandMonsterRenderer extends GeoEntityRenderer<OdoIslandMonsterEntity> {
    public static final float SCALE = 3.46F;
    private static final ResourceLocation NORMAL = GodzillaMinusOne.id("textures/entity/odo_island_monster.png");
    private static final ResourceLocation BURNT = GodzillaMinusOne.id("textures/entity/odo_island_monster_burnt.png");
    private static final ResourceLocation EYES = GodzillaMinusOne.id("textures/entity/odo_island_monster_glow.png");
    private static final ResourceLocation EMBERS = GodzillaMinusOne.id("textures/entity/odo_island_monster_burnt_glow.png");

    public OdoIslandMonsterRenderer(EntityRendererProvider.Context context) {
        super(context, new DefaultedEntityGeoModel<>(GodzillaMinusOne.id("odo_island_monster")) {
            @Override
            public ResourceLocation getTextureResource(OdoIslandMonsterEntity entity) {
                return entity.isBurnt() ? BURNT : NORMAL;
            }
        });
        withScale(SCALE);
        this.shadowRadius = 4.0F;
        addRenderLayer(new GlowLayer(this));
    }

    private static class GlowLayer extends GeoRenderLayer<OdoIslandMonsterEntity> {
        GlowLayer(OdoIslandMonsterRenderer renderer) {
            super(renderer);
        }

        @Override
        public void render(PoseStack poseStack, OdoIslandMonsterEntity entity, BakedGeoModel model, RenderType renderType,
                           MultiBufferSource buffers, VertexConsumer buffer, float partialTick, int packedLight, int packedOverlay) {
            float glow = 1.0F;
            ResourceLocation texture = EYES;
            if (entity.isBurnt()) {
                float t = entity.getBurntSeconds(partialTick);
                glow = Mth.clamp(1.0F - t / 60.0F, 0.0F, 1.0F) * (0.8F + 0.2F * Mth.sin(t * 3.0F));
                texture = EMBERS;
            }
            if (glow <= 0.01F) {
                return;
            }
            RenderType type = RenderType.eyes(texture);
            getRenderer().reRender(model, poseStack, buffers, entity, type, buffers.getBuffer(type), partialTick,
                    LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, glow, glow, glow, 1.0F);
        }
    }
}
