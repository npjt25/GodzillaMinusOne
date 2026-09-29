package com.npjt25.godzillaminusone.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.npjt25.godzillaminusone.GodzillaMinusOne;
import com.npjt25.godzillaminusone.entity.GodzillaEntity;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

import java.util.HashMap;
import java.util.Map;

/**
 * Renders Godzilla as the high-detail skinned mesh ({@link GodzillaMesh}) driven by the add-on's GeckoLib
 * skeleton and animations:
 * <ol>
 *   <li>GeckoLib walks the animated skeleton; we record every bone's transform (no cubes are drawn),</li>
 *   <li>the mesh is skinned on the CPU (two bones per vertex) and drawn with its textures,</li>
 *   <li>while charging, the dorsal plates glow blue one after another from the tail tip to the neck, as the
 *       charge animation punches them up,</li>
 *   <li>the atomic breath: bone "B", which the charge animation stretches into the beam, drawn as glowing energy.</li>
 * </ol>
 */
public class GodzillaRenderer extends GeoEntityRenderer<GodzillaEntity> {
    private static final ResourceLocation PLATE_GLOW = GodzillaMinusOne.id("textures/entity/godzilla_plates_glow.png");
    private static final ResourceLocation BEAM_CORE = GodzillaMinusOne.id("textures/entity/godzilla_beam.png");
    private static final ResourceLocation BEAM_SWIRL = new ResourceLocation("textures/entity/creeper/creeper_armor.png");
    private static final String BEAM_BONE = "B";

    private enum Pass {
        MODEL, BEAM
    }

    private Pass pass = Pass.MODEL;
    private final Map<String, Matrix4f> bonePoses = new HashMap<>();
    private final Map<String, Matrix3f> boneNormals = new HashMap<>();
    private float[] skinnedPos = new float[0];
    private float[] skinnedNormal = new float[0];

    public GodzillaRenderer(EntityRendererProvider.Context context) {
        super(context, new GodzillaModel());
        withScale(6.1F);
        this.shadowRadius = 8.0F;
        addRenderLayer(new MeshLayer(this));
        addRenderLayer(new BeamLayer(this));
    }

    @Override
    public void renderCubesOfBone(PoseStack poseStack, GeoBone bone, VertexConsumer buffer, int packedLight, int packedOverlay,
                                  float red, float green, float blue, float alpha) {
        if (this.pass == Pass.MODEL) {
            this.bonePoses.computeIfAbsent(bone.getName(), n -> new Matrix4f()).set(poseStack.last().pose());
            this.boneNormals.computeIfAbsent(bone.getName(), n -> new Matrix3f()).set(poseStack.last().normal());
        } else if (BEAM_BONE.equals(bone.getName())) {
            super.renderCubesOfBone(poseStack, bone, buffer, packedLight, packedOverlay, red, green, blue, alpha);
        }
    }

    // ------------------------------------------------------------------ skinned mesh

    private boolean skin(GodzillaMesh mesh, GodzillaMesh.Part part, Matrix4f[] poses, Matrix3f[] normals) {
        int n = part.vertexCount;
        if (this.skinnedPos.length < n * 3) {
            this.skinnedPos = new float[n * 3];
            this.skinnedNormal = new float[n * 3];
        }
        Vector3f a = new Vector3f();
        Vector3f b = new Vector3f();
        for (int v = 0; v < n; v++) {
            int i = v * 3;
            int b0 = part.bone0[v] & 0xFF;
            int b1 = part.bone1[v] & 0xFF;
            float w = part.weight0[v];
            float x = part.pos[i];
            float y = part.pos[i + 1];
            float z = part.pos[i + 2];
            poses[b0].transformPosition(x, y, z, a);
            if (w < 0.999F) {
                poses[b1].transformPosition(x, y, z, b);
                a.mul(w).add(b.mul(1.0F - w));
            }
            this.skinnedPos[i] = a.x;
            this.skinnedPos[i + 1] = a.y;
            this.skinnedPos[i + 2] = a.z;

            float nx = part.normal[i];
            float ny = part.normal[i + 1];
            float nz = part.normal[i + 2];
            normals[b0].transform(nx, ny, nz, a);
            if (w < 0.999F) {
                normals[b1].transform(nx, ny, nz, b);
                a.mul(w).add(b.mul(1.0F - w));
            }
            float len = a.length();
            if (len > 1.0E-6F) {
                a.div(len);
            }
            this.skinnedNormal[i] = a.x;
            this.skinnedNormal[i + 1] = a.y;
            this.skinnedNormal[i + 2] = a.z;
        }
        return true;
    }

    private void emit(VertexConsumer buffer, GodzillaMesh.Part part, float shade, int light, int overlay, float[] glow) {
        int[] tris = part.triangles;
        for (int t = 0; t < tris.length; t += 3) {
            float g = 1.0F;
            if (glow != null) {
                int group = part.glowGroup[tris[t]];
                g = group >= 0 && group < glow.length ? glow[group] : 0.0F;
                if (g < 0.02F) {
                    continue;
                }
            }
            float r = shade * g;
            vertex(buffer, part, tris[t], r, light, overlay);
            vertex(buffer, part, tris[t + 1], r, light, overlay);
            vertex(buffer, part, tris[t + 2], r, light, overlay);
            vertex(buffer, part, tris[t + 2], r, light, overlay);
        }
    }

    private void vertex(VertexConsumer buffer, GodzillaMesh.Part part, int v, float shade, int light, int overlay) {
        int i = v * 3;
        buffer.vertex(this.skinnedPos[i], this.skinnedPos[i + 1], this.skinnedPos[i + 2], shade, shade, shade, 1.0F,
                part.uv[v * 2], part.uv[v * 2 + 1], overlay, light,
                this.skinnedNormal[i], this.skinnedNormal[i + 1], this.skinnedNormal[i + 2]);
    }

    /** Glow of each dorsal plate: they light up in the order (and at the times) the charge animation raises them. */
    private static float[] plateGlow(GodzillaMesh mesh, GodzillaEntity entity, float partialTick) {
        int state = entity.getChargeState();
        if (state == GodzillaEntity.CHARGE_NONE) {
            return null;
        }
        float[] glow = new float[mesh.plateTimes.length];
        if (state == GodzillaEntity.CHARGE_COOLING) {
            float fade = Mth.clamp(1.0F - entity.getCoolingSeconds(partialTick) / 1.5F, 0.0F, 1.0F);
            java.util.Arrays.fill(glow, fade);
            return glow;
        }
        float t = entity.getChargeSeconds(partialTick);
        float pulse = 0.85F + 0.15F * Mth.sin(t * 7.0F);
        for (int k = 0; k < glow.length; k++) {
            float x = Mth.clamp((t - mesh.plateTimes[k]) / 0.4F, 0.0F, 1.0F);
            glow[k] = x * x * (3.0F - 2.0F * x) * pulse;
        }
        return glow;
    }

    private static class MeshLayer extends GeoRenderLayer<GodzillaEntity> {
        private final GodzillaRenderer godzilla;

        MeshLayer(GodzillaRenderer renderer) {
            super(renderer);
            this.godzilla = renderer;
        }

        @Override
        public void render(PoseStack poseStack, GodzillaEntity entity, BakedGeoModel model, RenderType renderType, MultiBufferSource buffers,
                           VertexConsumer buffer, float partialTick, int packedLight, int packedOverlay) {
            GodzillaMesh mesh = GodzillaMesh.get();
            if (mesh == null) {
                return;
            }
            Matrix4f[] poses = new Matrix4f[mesh.bones.length];
            Matrix3f[] normals = new Matrix3f[mesh.bones.length];
            for (int i = 0; i < mesh.bones.length; i++) {
                poses[i] = this.godzilla.bonePoses.get(mesh.bones[i]);
                normals[i] = this.godzilla.boneNormals.get(mesh.bones[i]);
                if (poses[i] == null || normals[i] == null) {
                    return; // skeleton not rendered yet
                }
            }
            GodzillaEntity.Phase phase = entity.getPhase();
            float shade = phase == GodzillaEntity.Phase.COLLAPSING || phase == GodzillaEntity.Phase.DEAD ? 0.7F : 1.0F;
            float[] glow = plateGlow(mesh, entity, partialTick);
            for (GodzillaMesh.Part part : mesh.parts) {
                this.godzilla.skin(mesh, part, poses, normals);
                this.godzilla.emit(buffers.getBuffer(RenderType.entityCutoutNoCull(part.texture)), part, shade, packedLight, packedOverlay, null);
                if (part.plates && glow != null) {
                    this.godzilla.emit(buffers.getBuffer(RenderType.eyes(PLATE_GLOW)), part, 1.0F, LightTexture.FULL_BRIGHT,
                            OverlayTexture.NO_OVERLAY, glow);
                }
            }
        }
    }

    private static class BeamLayer extends GeoRenderLayer<GodzillaEntity> {
        private final GodzillaRenderer godzilla;

        BeamLayer(GodzillaRenderer renderer) {
            super(renderer);
            this.godzilla = renderer;
        }

        @Override
        public void render(PoseStack poseStack, GodzillaEntity entity, BakedGeoModel model, RenderType renderType, MultiBufferSource buffers,
                           VertexConsumer buffer, float partialTick, int packedLight, int packedOverlay) {
            if (!entity.isCharging()) {
                return;
            }
            float t = entity.tickCount + partialTick;
            this.godzilla.beamPass(poseStack, entity, model, buffers, RenderType.eyes(BEAM_CORE), partialTick, 1.0F, 1.0F, 1.0F);
            this.godzilla.beamPass(poseStack, entity, model, buffers,
                    RenderType.energySwirl(BEAM_SWIRL, (t * 0.03F) % 1.0F, (t * 0.02F) % 1.0F), partialTick, 0.3F, 0.7F, 1.0F);
        }
    }

    private void beamPass(PoseStack poseStack, GodzillaEntity entity, BakedGeoModel model, MultiBufferSource buffers, RenderType type,
                          float partialTick, float red, float green, float blue) {
        this.pass = Pass.BEAM;
        try {
            reRender(model, poseStack, buffers, entity, type, buffers.getBuffer(type), partialTick, LightTexture.FULL_BRIGHT,
                    OverlayTexture.NO_OVERLAY, red, green, blue, 1.0F);
        } finally {
            this.pass = Pass.MODEL;
        }
    }
}
