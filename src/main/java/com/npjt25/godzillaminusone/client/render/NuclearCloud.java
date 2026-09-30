package com.npjt25.godzillaminusone.client.render;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.npjt25.godzillaminusone.ClientConfig;
import com.npjt25.godzillaminusone.GodzillaMinusOne;
import com.npjt25.godzillaminusone.client.particle.Fx;
import com.npjt25.godzillaminusone.registry.ModParticles;
import net.minecraft.Util;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * The atomic blast's mushroom cloud: Alex's Caves' nuclear mushroom cloud, its normal orange version
 * (MushroomCloudParticle and MushroomCloudModel, by AlexModGuy; its textures, textures/effect/mushroom_cloud*.png,
 * are the source's own), scaled evenly to this mod's blasts: mushroomCloudHeight blocks tall (240 by default) and
 * hanging in the sky for mushroomCloudSeconds (60).
 *
 * <ul>
 *   <li>For its first 10 ticks it is a white-hot fireball (the model's lightBall), glowing at full strength.</li>
 *   <li>Then the mushroom bursts out of it ({@link MushroomCloudModel#animate}): the fiery plume stretches up, the
 *       dark cap spreads on top of it and the low cloud over the ground, while the shockwave ring races outwards
 *       and two more rings spread and turn above the cap. It grows fast at first, then slower and slower
 *       (the source's log(1 + t) * 2, smoothed off at 1).</li>
 *   <li>It is drawn three times over, as the source does: its texture, see-through and lit by the world; the same
 *       again, glowing on top of that (where the first pass drew); and the glow texture on top of that, which
 *       fades away as it grows: the fire in it cools to smoke.</li>
 *   <li>While it grows it throws off fiery puffs: smoke from its heart, explosions over the ground around it.</li>
 *   <li>Over the last 1/40 of its life it fades out.</li>
 * </ul>
 *
 * It is a client-side effect (started by the ATOMIC_BLAST packet, at ground zero of the beam's or the bomb's
 * impact, once per blast) rather than an entity or particle, so it is seen from as far away as the packet reaches;
 * it's drawn through the fog of the render distance.
 */
public final class NuclearCloud {
    private static final ResourceLocation TEXTURE = GodzillaMinusOne.id("textures/effect/mushroom_cloud.png");
    private static final ResourceLocation TEXTURE_GLOW = GodzillaMinusOne.id("textures/effect/mushroom_cloud_glow.png");
    /** Alex's Caves' own nuclear bomb draws its cloud this big (its size 3, plus 0.2): its puffs are sized for that. */
    private static final float SOURCE_SCALE = 3.2F;
    /** How far the model rises above its origin by the end of its life, at scale 1 (tools/mushroom_cloud_model.py). */
    public static final float MODEL_HEIGHT = 12.14F;
    /** Ticks of fireball before the mushroom shows. */
    private static final int BALL_FOR = 10;

    private static final List<NuclearCloud> CLOUDS = new ArrayList<>();
    private static final RandomSource RANDOM = RandomSource.create();
    @Nullable
    private static MushroomCloudModel model;

    /** Where the source puts its particle: two (source-sized) blocks above ground zero. */
    private final Vec3 pos;
    private final float scale;
    private final int lifetime;
    private int age;

    private NuclearCloud(Vec3 zero) {
        this.scale = ClientConfig.NUCLEAR_CLOUD_HEIGHT.get() / MODEL_HEIGHT;
        this.lifetime = Math.max(BALL_FOR + 40, ClientConfig.NUCLEAR_CLOUD_SECONDS.get() * 20);
        this.pos = zero.add(0.0, 2.0 * sourceScale(), 0.0);
    }

    /**
     * A mushroom cloud at ground zero. (The atomic breath's blast and the nuclear bomb both make the same one, an air
     * burst too, centred where it goes off.)
     */
    public static void spawn(Vec3 pos, float yaw, boolean airburst) {
        CLOUDS.add(new NuclearCloud(pos));
    }

    public static void tick() {
        for (NuclearCloud cloud : CLOUDS) {
            cloud.age++;
            cloud.puffs();
        }
        CLOUDS.removeIf(c -> c.age >= c.lifetime);
    }

    public static void clear() {
        CLOUDS.clear();
    }

    /** How many times bigger than the source's own cloud this one is. */
    private float sourceScale() {
        return this.scale / SOURCE_SCALE;
    }

    private int fadeTicks() {
        return Math.max(10, Math.round(this.lifetime / 40.0F));
    }

    /** How far it has grown (the source's measure): 0 as the mushroom appears, log(2) * 2 at the end. */
    private float life(float partialTick) {
        return (float) Math.log(1.0 + (this.age - BALL_FOR + partialTick) / (double) (this.lifetime - BALL_FOR)) * 2.0F;
    }

    /** ACMath.smin: a smooth minimum. */
    private static float smoothMin(float a, float b, float k) {
        float h = Math.max(k - Math.abs(a - b), 0.0F) / k;
        return Math.min(a, b) - h * h * k * 0.25F;
    }

    /** The source's puffs, as many a tick as its own nuke throws off, each as much bigger (and further) as the cloud. */
    private void puffs() {
        if (this.age < BALL_FOR || this.age >= this.lifetime - fadeTicks()) {
            return;
        }
        float size = sourceScale();
        float spread = (12.0F * life(0.0F) + 4.0F) * this.scale;
        // smoke flung out of its heart
        int smoke = Mth.ceil((1 + RANDOM.nextInt(2)) * SOURCE_SCALE);
        for (int i = 0; i < smoke; i++) {
            Vec3 from = new Vec3(RANDOM.nextFloat() - 0.5F, RANDOM.nextFloat() - 0.5F, RANDOM.nextFloat() - 0.5F).scale(this.scale * 1.4F).add(this.pos);
            Vec3 away = new Vec3(RANDOM.nextFloat() - 0.5F, RANDOM.nextFloat() - 0.5F, RANDOM.nextFloat() - 0.5F).scale(2.34F * size);
            Fx.nukePuff(ModParticles.NUKE_SMOKE, from, away, size);
        }
        // explosions going off over the ground around it, further out as it grows
        int fire = Mth.ceil(SOURCE_SCALE * SOURCE_SCALE);
        for (int i = 0; i < fire; i++) {
            Vec3 at = new Vec3((RANDOM.nextFloat() - 0.5F) * spread, (-0.6F + RANDOM.nextFloat() * 0.5F) * spread * 0.1F,
                    (RANDOM.nextFloat() - 0.5F) * spread).add(this.pos);
            Fx.nukePuff(ModParticles.NUKE_FIRE, at, Vec3.ZERO, size);
        }
    }

    /** Draws every cloud; called after the level's particles are drawn. */
    public static void renderAll(PoseStack poseStack, Camera camera, float partialTick) {
        ClientLevel level = Minecraft.getInstance().level;
        if (CLOUDS.isEmpty() || level == null) {
            return;
        }
        if (model == null) {
            model = new MushroomCloudModel();
        }
        MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        Vec3 cam = camera.getPosition();
        // the cloud towers over the landscape: it is seen through the haze of the render distance, not lost in it
        float fogStart = RenderSystem.getShaderFogStart();
        RenderSystem.setShaderFogStart(Float.MAX_VALUE);
        for (NuclearCloud cloud : CLOUDS) {
            cloud.render(level, poseStack, buffers, cam, partialTick);
        }
        RenderSystem.setShaderFogStart(fogStart);
    }

    private void render(ClientLevel level, PoseStack poseStack, MultiBufferSource.BufferSource buffers, Vec3 cam, float partialTick) {
        float life = life(partialTick);
        float glow = life < 1.0F ? Math.min(1.0F - life, 1.0F) : 0.0F;
        float left = this.lifetime - this.age - partialTick;
        float alpha = Mth.clamp(left / fadeTicks(), 0.0F, 1.0F);
        MushroomCloudModel cloud = model;
        cloud.animate(this.age < BALL_FOR, smoothMin(life, 1.0F, 0.5F), this.age + partialTick);

        poseStack.pushPose();
        // (anchored to the world: only the camera's position is taken off, never its direction)
        poseStack.translate(this.pos.x - cam.x, this.pos.y - cam.y - 0.5 * sourceScale(), this.pos.z - cam.z);
        poseStack.scale(-this.scale, -this.scale, this.scale);
        int light = LevelRenderer.getLightColor(level, BlockPos.containing(this.pos));
        cloud.render(poseStack, buffers.getBuffer(RenderType.entityTranslucent(TEXTURE)), light, OverlayTexture.NO_OVERLAY,
                1.0F, 1.0F, 1.0F, alpha);
        cloud.render(poseStack, buffers.getBuffer(Types.glow(TEXTURE)), 240, OverlayTexture.NO_OVERLAY, 1.0F, 1.0F, 1.0F, alpha);
        cloud.render(poseStack, buffers.getBuffer(Types.glow(TEXTURE_GLOW)), 240, OverlayTexture.NO_OVERLAY, 1.0F, 1.0F, 1.0F,
                glow * alpha);
        buffers.endBatch();
        poseStack.popPose();
    }

    /**
     * The source's glowing passes (ACRenderTypes.getEyesAlphaEnabled): the glowing-eyes shader, added on top of what's
     * there, both sides of every face, and only where the first pass drew (the same depth), so it can't show through.
     */
    private static final class Types extends RenderType {
        private static final TransparencyStateShard GLOW_TRANSPARENCY = new TransparencyStateShard("godzillaminusone_mushroom_cloud_glow", () -> {
            RenderSystem.enableBlend();
            RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE,
                    GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        }, () -> {
            RenderSystem.disableBlend();
            RenderSystem.defaultBlendFunc();
        });

        private static final Function<ResourceLocation, RenderType> GLOW = Util.memoize(texture -> create(
                GodzillaMinusOne.MODID + ":mushroom_cloud_glow", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 256, true, false,
                CompositeState.builder()
                        .setShaderState(RENDERTYPE_EYES_SHADER)
                        .setTextureState(new TextureStateShard(texture, false, false))
                        .setTransparencyState(GLOW_TRANSPARENCY)
                        .setCullState(NO_CULL)
                        .setLightmapState(LIGHTMAP)
                        .setOverlayState(OVERLAY)
                        .setDepthTestState(EQUAL_DEPTH_TEST)
                        .createCompositeState(true)));

        static RenderType glow(ResourceLocation texture) {
            return GLOW.apply(texture);
        }

        private Types(String name, VertexFormat format, VertexFormat.Mode mode, int bufferSize, boolean crumbling, boolean sort,
                      Runnable setup, Runnable clear) {
            super(name, format, mode, bufferSize, crumbling, sort, setup, clear);
        }
    }
}
