package com.npjt25.godzillaminusone.client.particle;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * One configurable particle that covers the add-on's Bedrock particle files: size over life, a colour
 * gradient, additive or alpha blending, and camera-facing, flat (ground ring) or vertical-streak billboards.
 */
public class FxParticle extends TextureSheetParticle {
    /** Bedrock "particles_add". */
    public static final ParticleRenderType ADDITIVE = new ParticleRenderType() {
        @Override
        public void begin(BufferBuilder builder, TextureManager textureManager) {
            RenderSystem.depthMask(false);
            RenderSystem.setShaderTexture(0, TextureAtlas.LOCATION_PARTICLES);
            RenderSystem.enableBlend();
            RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
            builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE);
        }

        @Override
        public void end(Tesselator tesselator) {
            tesselator.end();
            RenderSystem.defaultBlendFunc();
            RenderSystem.depthMask(true);
        }

        @Override
        public String toString() {
            return "GODZILLAMINUSONE_ADDITIVE";
        }
    };

    public enum Facing {
        CAMERA, FLAT, VERTICAL
    }

    private final float startSize;
    private final float endSize;
    private final float[] stops;
    private final int[] colors;
    private final boolean additive;
    private final boolean fullBright;
    private final Facing facing;
    private final float aspect;
    private final float spin;
    private final boolean dieOnGround;

    FxParticle(ClientLevel level, Fx.Builder b, SpriteSet sprites) {
        super(level, b.x, b.y, b.z);
        this.xd = b.vx;
        this.yd = b.vy;
        this.zd = b.vz;
        this.lifetime = Math.max(1, b.lifetime);
        this.gravity = b.gravity;
        this.friction = b.friction;
        this.hasPhysics = b.collide;
        this.dieOnGround = b.collide;
        this.startSize = b.startSize;
        this.endSize = b.endSize;
        this.stops = b.stops;
        this.colors = b.colors;
        this.additive = b.additive;
        this.fullBright = b.fullBright || b.additive;
        this.facing = b.facing;
        this.aspect = b.aspect;
        this.spin = b.spin;
        this.roll = b.roll;
        this.oRoll = b.roll;
        this.quadSize = b.startSize;
        pickSprite(sprites);
        applyColor(0.0F);
    }

    @Override
    public void tick() {
        super.tick();
        this.oRoll = this.roll;
        this.roll += this.spin;
        applyColor((float) this.age / (float) this.lifetime);
        if (this.dieOnGround && this.onGround) {
            remove();
        }
    }

    private void applyColor(float t) {
        int argb = this.colors[this.colors.length - 1];
        if (t <= this.stops[0]) {
            argb = this.colors[0];
        } else {
            for (int i = 1; i < this.stops.length; i++) {
                if (t <= this.stops[i]) {
                    float f = (t - this.stops[i - 1]) / Math.max(this.stops[i] - this.stops[i - 1], 1.0E-4F);
                    argb = lerpColor(this.colors[i - 1], this.colors[i], f);
                    break;
                }
            }
        }
        setAlpha(((argb >>> 24) & 0xFF) / 255.0F);
        setColor(((argb >> 16) & 0xFF) / 255.0F, ((argb >> 8) & 0xFF) / 255.0F, (argb & 0xFF) / 255.0F);
    }

    private static int lerpColor(int a, int b, float f) {
        int result = 0;
        for (int shift = 0; shift < 32; shift += 8) {
            int ca = (a >>> shift) & 0xFF;
            int cb = (b >>> shift) & 0xFF;
            result |= (Math.round(ca + (cb - ca) * f) & 0xFF) << shift;
        }
        return result;
    }

    @Override
    public float getQuadSize(float partialTick) {
        float t = Mth.clamp((this.age + partialTick) / this.lifetime, 0.0F, 1.0F);
        return Mth.lerp(t, this.startSize, this.endSize);
    }

    @Override
    public ParticleRenderType getRenderType() {
        return this.additive ? ADDITIVE : ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    @Override
    protected int getLightColor(float partialTick) {
        return this.fullBright ? LightTexture.FULL_BRIGHT : super.getLightColor(partialTick);
    }

    /** Most of these effects are far bigger than their hitbox; never frustum-cull them. */
    @Override
    public boolean shouldCull() {
        return false;
    }

    @Override
    public void render(VertexConsumer buffer, Camera camera, float partialTick) {
        if (this.facing == Facing.CAMERA && this.aspect == 1.0F) {
            super.render(buffer, camera, partialTick);
            return;
        }
        Vec3 cam = camera.getPosition();
        float px = (float) (Mth.lerp(partialTick, this.xo, this.x) - cam.x());
        float py = (float) (Mth.lerp(partialTick, this.yo, this.y) - cam.y());
        float pz = (float) (Mth.lerp(partialTick, this.zo, this.z) - cam.z());
        float size = getQuadSize(partialTick);
        float roll = Mth.lerp(partialTick, this.oRoll, this.roll);

        Quaternionf rotation;
        switch (this.facing) {
            case FLAT -> rotation = new Quaternionf().rotationX(-Mth.HALF_PI).rotateZ(roll);
            case VERTICAL -> rotation = new Quaternionf().rotationY((float) Math.atan2(-px, -pz)); // turn to face the camera around Y only
            default -> {
                rotation = new Quaternionf(camera.rotation());
                rotation.rotateZ(roll);
            }
        }
        Vector3f[] corners = {
                new Vector3f(-1.0F, -this.aspect, 0.0F), new Vector3f(-1.0F, this.aspect, 0.0F),
                new Vector3f(1.0F, this.aspect, 0.0F), new Vector3f(1.0F, -this.aspect, 0.0F)};
        for (Vector3f corner : corners) {
            corner.rotate(rotation);
            corner.mul(size);
            corner.add(px, py, pz);
        }
        float u0 = getU0();
        float u1 = getU1();
        float v0 = getV0();
        float v1 = getV1();
        int light = getLightColor(partialTick);
        vertex(buffer, corners[0], u1, v1, light);
        vertex(buffer, corners[1], u1, v0, light);
        vertex(buffer, corners[2], u0, v0, light);
        vertex(buffer, corners[3], u0, v1, light);
        if (this.facing != Facing.CAMERA) {
            // double sided
            vertex(buffer, corners[3], u0, v1, light);
            vertex(buffer, corners[2], u0, v0, light);
            vertex(buffer, corners[1], u1, v0, light);
            vertex(buffer, corners[0], u1, v1, light);
        }
    }

    private void vertex(VertexConsumer buffer, Vector3f pos, float u, float v, int light) {
        buffer.vertex(pos.x(), pos.y(), pos.z()).uv(u, v).color(this.rCol, this.gCol, this.bCol, this.alpha).uv2(light).endVertex();
    }
}
