package com.npjt25.godzillaminusone.client.render;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import com.mojang.math.Axis;
import com.npjt25.godzillaminusone.GodzillaMinusOne;
import com.npjt25.godzillaminusone.entity.SearchlightEntity;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

/**
 * The searchlight (tools/make_props.py): the turret turns and the lamp tilts to where its operator looks; while lit
 * the lens glows, a beam of light widens out to where it lands, and a pool of light spreads over what it lands on.
 */
public class SearchlightRenderer extends GeoEntityRenderer<SearchlightEntity> {
    private static final ResourceLocation LENS = GodzillaMinusOne.id("textures/entity/searchlight_glow.png");

    public SearchlightRenderer(EntityRendererProvider.Context context) {
        super(context, new DefaultedEntityGeoModel<SearchlightEntity>(GodzillaMinusOne.id("searchlight")) {
            @Override
            public void setCustomAnimations(SearchlightEntity entity, long instanceId, AnimationState<SearchlightEntity> state) {
                super.setCustomAnimations(entity, instanceId, state);
                // the model faces the entity's own yaw; the turret makes up the rest
                float turn = Mth.wrapDegrees(entity.getAimYaw() - entity.getYRot());
                getBone("turret").ifPresent(b -> b.setRotY(-turn * Mth.DEG_TO_RAD));
                getBone("lamp").ifPresent(b -> b.setRotX(-entity.getAimPitch() * Mth.DEG_TO_RAD));
            }
        });
        this.shadowRadius = 1.0F;
        addRenderLayer(new LensLayer(this));
    }

    @Override
    protected void applyRotations(SearchlightEntity entity, PoseStack poseStack, float ageInTicks, float rotationYaw, float partialTick) {
        super.applyRotations(entity, poseStack, ageInTicks, entity.getYRot(), partialTick);
        float wobble = entity.getWobble() - partialTick;
        if (wobble > 0.0F) {
            poseStack.mulPose(Axis.ZP.rotationDegrees(Mth.sin(wobble) * wobble * 0.8F));
        }
    }

    /**
     * Its beam is drawn once the water and ice have been (see {@link Beams}): tested against what's in front of it but
     * hiding nothing, so water and ice show over it as usual, and it never cuts a see-through hole in them.
     */
    @Mod.EventBusSubscriber(modid = GodzillaMinusOne.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
    public static final class Beams {
        /** Beams are drawn from searchlights up to this far away. */
        private static final double MAX_DISTANCE = 384.0;

        @SubscribeEvent
        public static void onRenderLevel(RenderLevelStageEvent event) {
            if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
                return;
            }
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null) {
                return;
            }
            Vec3 cam = event.getCamera().getPosition();
            float partialTick = event.getPartialTick();
            PoseStack poseStack = event.getPoseStack();
            MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
            boolean any = false;
            for (Entity entity : mc.level.entitiesForRendering()) {
                if (entity instanceof SearchlightEntity light && light.isLit() && light.distanceToSqr(cam) < MAX_DISTANCE * MAX_DISTANCE) {
                    Vec3 at = light.getPosition(partialTick);
                    poseStack.pushPose();
                    poseStack.translate(at.x - cam.x, at.y - cam.y, at.z - cam.z);
                    renderBeam(light, cam.subtract(at), poseStack, buffers.getBuffer(Types.BEAM));
                    poseStack.popPose();
                    any = true;
                }
            }
            if (any) {
                buffers.endBatch(Types.BEAM);
            }
        }

        private Beams() {
        }
    }

    /**
     * A beam of light from the lens to where it lands, a little blocky: a column of square sections, each a step wider
     * than the last (half a block at a time) and dimmer, round a soft bright core drawn facing you so it reads from any
     * angle; and where it lands, a pool of light picked out block by block. It stops at the surface of water.
     */
    private static void renderBeam(SearchlightEntity entity, Vec3 eye, PoseStack poseStack, VertexConsumer buffer) {
        Vec3 from = entity.lens().subtract(entity.position());
        Vec3 dir = entity.beamDirection();
        HitResult hit = entity.visibleBeamHit();
        double length = hit.getLocation().distanceTo(entity.lens());
        if (length < 0.5) {
            return;
        }
        // two vectors across the beam
        Vec3 up = Math.abs(dir.y) > 0.95 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 a = dir.cross(up).normalize();
        Vec3 b = dir.cross(a).normalize();
        Matrix4f pose = poseStack.last().pose();
        int steps = Math.max(4, (int) Math.ceil(length / SECTION));
        for (int s = 0; s < steps; s++) {
            double d0 = Math.min(length, SECTION * s);
            double d1 = Math.min(length, SECTION * (s + 1));
            float alpha0 = (float) (0.30 * Math.pow(1.0 - d0 / length, 1.3));
            float alpha1 = (float) (0.30 * Math.pow(1.0 - d1 / length, 1.3));
            Vec3 c0 = from.add(dir.scale(d0));
            Vec3 c1 = from.add(dir.scale(d1));
            // the core, facing you
            float r0 = (float) SearchlightEntity.beamRadius(d0) * 0.55F;
            float r1 = (float) SearchlightEntity.beamRadius(d1) * 0.55F;
            Vec3 w0 = across(dir, eye.subtract(c0), a);
            Vec3 w1 = across(dir, eye.subtract(c1), a);
            shaft(buffer, pose, c0, c1, w0.scale(r0), w1.scale(r1), alpha0, alpha1);
            // the square section round it: as wide as the cone in the middle of the section, in half-block steps
            double half = Math.ceil(SearchlightEntity.beamRadius((d0 + d1) * 0.5) * 2.0) / 2.0;
            float sa = (float) (0.075 * Math.pow(1.0 - (d0 + d1) * 0.5 / length, 1.3));
            Vec3[] k0 = corners(c0, a, b, half);
            Vec3[] k1 = corners(c1, a, b, half);
            for (int i = 0; i < 4; i++) {
                int j = (i + 1) % 4;
                quad(buffer, pose, k0[i], k0[j], k1[j], k1[i], sa, sa);
            }
        }
        renderSpot(entity, hit, length, buffer, pose);
    }

    /** Length of each square section of the beam. */
    private static final double SECTION = 2.0;

    private static Vec3[] corners(Vec3 c, Vec3 a, Vec3 b, double half) {
        Vec3 pa = a.scale(half);
        Vec3 pb = b.scale(half);
        return new Vec3[] {c.add(pa).add(pb), c.subtract(pa).add(pb), c.subtract(pa).subtract(pb), c.add(pa).subtract(pb)};
    }

    /** Across the beam and across the line of sight to it (so a strip along that faces you); {@code fallback} end-on. */
    private static Vec3 across(Vec3 dir, Vec3 toEye, Vec3 fallback) {
        Vec3 w = dir.cross(toEye);
        return w.lengthSqr() < 1.0E-6 ? fallback : w.normalize();
    }

    /** A strip from c0 to c1, {@code w} each side: bright down the middle, clear at the edges. */
    private static void shaft(VertexConsumer buffer, Matrix4f pose, Vec3 c0, Vec3 c1, Vec3 w0, Vec3 w1, float alpha0, float alpha1) {
        for (int side = -1; side <= 1; side += 2) {
            vertex(buffer, pose, c0, alpha0);
            vertex(buffer, pose, c0.add(w0.scale(side)), 0.0F);
            vertex(buffer, pose, c1.add(w1.scale(side)), 0.0F);
            vertex(buffer, pose, c1, alpha1);
        }
    }

    /** How bright the pool of light is, in steps from its middle out (so it's lit block by block). */
    private static final float[] SPOT_LEVELS = {0.5F, 0.38F, 0.24F, 0.12F};

    /**
     * The pool of light where it lands, on the face it hits: the squares of the block grid within its radius, each lit
     * by how close it is to the middle (a few distinct steps), a little off the surface so it doesn't flicker.
     */
    private static void renderSpot(SearchlightEntity entity, HitResult beamHit, double length, VertexConsumer buffer, Matrix4f pose) {
        if (!(beamHit instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) {
            return;
        }
        Direction face = hit.getDirection();
        Vec3 normal = Vec3.atLowerCornerOf(face.getNormal());
        Vec3 at = hit.getLocation();
        Direction.Axis axis = face.getAxis();
        Direction.Axis ua = axis == Direction.Axis.X ? Direction.Axis.Y : Direction.Axis.X;
        Direction.Axis va = axis == Direction.Axis.Z ? Direction.Axis.Y : Direction.Axis.Z;
        Vec3 u = unit(ua);
        Vec3 v = unit(va);
        double radius = SearchlightEntity.spotRadius(length);
        double cell = radius > 8.0 ? 1.0 : 0.5;
        // the further it lands, the dimmer the pool
        float fade = (float) Math.max(0.25, Math.pow(1.0 - Math.min(length / SearchlightEntity.RANGE, 1.0), 0.8));
        double cu = at.get(ua);
        double cv = at.get(va);
        Vec3 plane = unit(axis).scale(at.get(axis) - entity.position().get(axis)).add(normal.scale(0.06));
        Vec3 base = entity.position().multiply(u.add(v));
        int n = (int) Math.ceil(radius / cell);
        double gu = Math.floor(cu / cell) * cell;
        double gv = Math.floor(cv / cell) * cell;
        for (int i = -n; i <= n; i++) {
            for (int j = -n; j <= n; j++) {
                double u0 = gu + i * cell;
                double v0 = gv + j * cell;
                double du = u0 + cell * 0.5 - cu;
                double dv = v0 + cell * 0.5 - cv;
                double f = Math.sqrt(du * du + dv * dv) / radius;
                if (f >= 1.0) {
                    continue;
                }
                float alpha = SPOT_LEVELS[Math.min(SPOT_LEVELS.length - 1, (int) (f * SPOT_LEVELS.length))] * fade;
                Vec3 p0 = plane.add(u.scale(u0)).add(v.scale(v0)).subtract(base);
                Vec3 p1 = p0.add(u.scale(cell));
                Vec3 p2 = p1.add(v.scale(cell));
                Vec3 p3 = p0.add(v.scale(cell));
                quad(buffer, pose, p0, p1, p2, p3, alpha, alpha);
            }
        }
    }

    private static Vec3 unit(Direction.Axis axis) {
        return switch (axis) {
            case X -> new Vec3(1, 0, 0);
            case Y -> new Vec3(0, 1, 0);
            case Z -> new Vec3(0, 0, 1);
        };
    }

    /** p0, p1 get alpha0 and p2, p3 alpha1. */
    private static void quad(VertexConsumer buffer, Matrix4f pose, Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3, float alpha0, float alpha1) {
        vertex(buffer, pose, p0, alpha0);
        vertex(buffer, pose, p1, alpha0);
        vertex(buffer, pose, p2, alpha1);
        vertex(buffer, pose, p3, alpha1);
    }

    private static void vertex(VertexConsumer buffer, Matrix4f pose, Vec3 p, float alpha) {
        buffer.vertex(pose, (float) p.x, (float) p.y, (float) p.z).color(1.0F, 0.96F, 0.82F, alpha).endVertex();
    }

    /**
     * The beam's light: added on like lightning, behind whatever is in front of it, hiding nothing (no depth written), drawn
     * from both sides; with Fabulous graphics, into the see-through layer (with the water and ice), so it's sorted with
     * them.
     */
    private static final class Types extends RenderType {
        static final RenderType BEAM = create(GodzillaMinusOne.MODID + ":searchlight_beam", DefaultVertexFormat.POSITION_COLOR,
                VertexFormat.Mode.QUADS, 1 << 16, false, false,
                CompositeState.builder()
                        .setShaderState(RENDERTYPE_LIGHTNING_SHADER)
                        .setTransparencyState(LIGHTNING_TRANSPARENCY)
                        .setCullState(NO_CULL)
                        .setDepthTestState(LEQUAL_DEPTH_TEST)
                        .setWriteMaskState(COLOR_WRITE)
                        .setOutputState(TRANSLUCENT_TARGET)
                        .createCompositeState(false));

        private Types(String name, VertexFormat format, VertexFormat.Mode mode, int bufferSize, boolean crumbling, boolean sort,
                      Runnable setup, Runnable clear) {
            super(name, format, mode, bufferSize, crumbling, sort, setup, clear);
        }
    }

    private static class LensLayer extends GeoRenderLayer<SearchlightEntity> {
        LensLayer(SearchlightRenderer renderer) {
            super(renderer);
        }

        @Override
        public void render(PoseStack poseStack, SearchlightEntity entity, BakedGeoModel model, RenderType renderType, MultiBufferSource buffers,
                           VertexConsumer buffer, float partialTick, int packedLight, int packedOverlay) {
            if (entity.isLit()) {
                RenderType type = RenderType.eyes(LENS);
                getRenderer().reRender(model, poseStack, buffers, entity, type, buffers.getBuffer(type), partialTick,
                        LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 1.0F, 1.0F, 1.0F, 1.0F);
            }
        }
    }
}
