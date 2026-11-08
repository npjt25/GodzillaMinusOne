package com.npjt25.godzillaminusone.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.npjt25.godzillaminusone.ClientConfig;
import com.npjt25.godzillaminusone.GodzillaMinusOne;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

import static com.npjt25.godzillaminusone.client.render.BlastTimeline.clamp;
import static com.npjt25.godzillaminusone.client.render.BlastTimeline.mix;
import static com.npjt25.godzillaminusone.client.render.BlastTimeline.smoothstep;

/**
 * The atomic blast as you see it, after the Ginza blast in Godzilla Minus One ({@link BlastTimeline} has its timing and
 * proportions; the settings are in ClientConfig's atomicBlast section):
 *
 * <ol>
 *   <li>the flash: a white-hot core at ground zero, swelling in a moment into a pale yellow glare that whites out
 *       the view (client.ClientFx), then dying away;</li>
 *   <li>the fireball: a ragged, blazing ball, white-hot, then yellow and orange, swelling and starting to rise;
 *       under it a wall of dust rolls out over the ground, reaching further and further (not everywhere at once),
 *       and a ring of mist races out through the air and is gone in a few seconds;</li>
 *   <li>the column: as the glare fades, a thick, lumpy column of smoke rises under the fireball, its foot lost in
 *       the dust churned up round it;</li>
 *   <li>the cap: the fireball spreads into a broad, heavy cap that rolls over on itself (rising through its middle,
 *       over its top and down its outside), far wider than the stem: charcoal, grey and dirty brown, lighter where
 *       its top billows catch the light, dark underneath, glowing orange and yellow only low down, while it's hot;</li>
 *   <li>the mature cloud: towering over ground zero, still slowly rising, spreading and rolling; its fire dies
 *       out, well before its smoke; the black rain falls from it meanwhile (client.ClientFx);</li>
 *   <li>its end: the cap broadens and softens, its outer billows thin away first, then its heart and the stem break
 *       up and drift, leaving scattered smoke and a haze over the ground that fade last.</li>
 * </ol>
 *
 * <p>It is built, like Alex's Caves' mushroom cloud (by AlexModGuy), from its textures and in layers, but made of
 * several hundred puffs instead of one model so it keeps its shape from every side, above and below: camera-facing
 * sprites (textures/effect/nuclear_cloud.png, made from Alex's Caves' cloud textures by tools/make_cloud_sprites.py),
 * each placed, sized, coloured and faded by the timeline. They are drawn in two passes, as the source draws its
 * cloud: the smoke, sorted back to front and see-through, lit by the sky (dimmer at night and in the rain) and fading
 * into the distance haze; then, added on top where it's hot, the fire inside it (the glow textures), the flash and
 * the fireball's glare. Everything is in blocks, as big whoever looks and from wherever (Godzilla's size has nothing
 * to do with it), and moves smoothly between ticks.
 *
 * <p>It's a client-side effect, one per blast, started by the ATOMIC_BLAST packet at ground zero (the ground under
 * where the atomic breath landed or the nuclear bomb went off): no entity, so it is seen from as far as the packet
 * reaches (and the view goes: four times the render distance) and it isn't lost in the fog. The blast's damage,
 * crater and flattening are the server's, at the moment of impact; this is only its look. tools/preview_blast.py
 * draws the same cloud offline.
 */
public final class NuclearCloud {
    private static final ResourceLocation TEXTURE = GodzillaMinusOne.id("textures/effect/nuclear_cloud.png");
    private static final float TAU = (float) (Math.PI * 2.0);

    // The sprite sheet's cells (tools/make_cloud_sprites.py): 0-3 billows of smoke and 4-7 the fire inside each;
    // dust, a wisp, a tall clump for the column and its fire, the fireball, the flash.
    private static final int DUST = 8;
    private static final int WISP = 9;
    private static final int COLUMN = 10;
    private static final int FIREBALL = 12;
    private static final int FLASH = 13;

    // The puffs, by what they make up: the cap (its rolling outside and inside, the dome on its top, its underside),
    // the column, the dust at its foot, the dust wave, the haze it leaves, the ring of mist.
    private static final int HEAD = 0;
    private static final int DOME = 1;
    private static final int UNDER = 2;
    private static final int STEM = 3;
    private static final int SKIRT = 4;
    private static final int WAVE = 5;
    private static final int HAZE = 6;
    private static final int RING = 7;
    private static final int[] COUNT = {210, 60, 50, 120, 70, 96, 110, 48};
    /** When (how far through breaking up) each fades: the cap's outer billows first, the ground haze last. */
    private static final float[] FADE_START = {0.0F, 0.12F, 0.2F, 0.28F, 0.5F, 0.0F, 0.66F, 0.0F};
    private static final float[] FADE_SPREAD = {0.18F, 0.2F, 0.2F, 0.35F, 0.2F, 0.0F, 0.12F, 0.0F};
    /** The ground's dust is squashed flat. */
    private static final float[] ASPECT = {1.0F, 1.0F, 1.0F, 1.0F, 0.85F, 0.8F, 0.6F, 1.0F};

    private static final float[] CHARCOAL = {0.20F, 0.19F, 0.18F};
    private static final float[] GREY = {0.42F, 0.41F, 0.39F};
    private static final float[] BROWN = {0.39F, 0.31F, 0.23F};
    private static final float[] PALE = {0.68F, 0.66F, 0.62F};
    private static final float[] DUST_COLOUR = {0.56F, 0.48F, 0.39F};
    private static final float[] MIST = {0.86F, 0.86F, 0.85F};
    private static final float[] DEEP_RED = {0.55F, 0.12F, 0.03F};
    private static final float[] ORANGE = {1.0F, 0.45F, 0.08F};
    private static final float[] YELLOW = {1.0F, 0.82F, 0.40F};
    private static final float[] WHITE = {1.0F, 0.98F, 0.93F};
    private static final float[] FLASH_YELLOW = {1.0F, 0.93F, 0.72F};
    /** How much brighter the fire is drawn than its colour (added light). */
    private static final float GLOW_GAIN = 1.6F;

    private static final List<NuclearCloud> CLOUDS = new ArrayList<>();
    private static final RandomSource RANDOM = RandomSource.create();
    private static final List<Sprite> SPRITES = new ArrayList<>();
    private static final BufferBuilder BUFFER = new BufferBuilder(1 << 17);
    private static final Vector3f VIEW = new Vector3f();

    private final ResourceKey<Level> dimension;
    /** Ground zero. */
    private final Vec3 zero;
    /** On the ground (a column, dust, a dust wave) or an air burst (only the fireball and the cloud). */
    private final boolean grounded;
    private final BlastTimeline time;
    private final Puff[] puffs;
    /** Which way the wind carries it off as it breaks up. */
    private final float windX;
    private final float windZ;
    private int age;

    private NuclearCloud(ResourceKey<Level> dimension, Vec3 zero, boolean grounded, float scale) {
        this.dimension = dimension;
        this.zero = zero;
        this.grounded = grounded;
        this.time = new BlastTimeline(ClientConfig.CLOUD_HEIGHT.get() * scale, ClientConfig.FLASH_SECONDS.get().floatValue(),
                ClientConfig.FIREBALL_SECONDS.get().floatValue(), ClientConfig.SHOCKWAVE_SECONDS.get().floatValue(),
                ClientConfig.RISE_SECONDS.get(), ClientConfig.COOLING_SECONDS.get(), ClientConfig.MATURE_SECONDS.get(),
                ClientConfig.DISSIPATE_SECONDS.get());
        float wind = RANDOM.nextFloat() * TAU;
        this.windX = Mth.cos(wind);
        this.windZ = Mth.sin(wind);
        List<Puff> list = new ArrayList<>();
        for (int group = 0; group < COUNT.length; group++) {
            for (int i = 0; i < COUNT[group]; i++) {
                list.add(new Puff(group, RANDOM));
            }
        }
        this.puffs = list.toArray(new Puff[0]);
    }

    /** The blast at ground zero (one cloud per ATOMIC_BLAST packet, for Godzilla's atomic breath and the nuclear bomb alike). */
    public static void spawn(Vec3 zero, boolean grounded) {
        spawn(zero, grounded, 1.0F);
    }

    /** The same, the whole cloud {@code scale} times as big (Godzilla Minus Zero's: twice). */
    public static void spawn(Vec3 zero, boolean grounded, float scale) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) {
            return;
        }
        NuclearCloud cloud = new NuclearCloud(level.dimension(), zero, grounded, scale);
        CLOUDS.add(cloud);
        // the light of it (like lightning's: Minecraft's "Hide Lightning Flashes" option turns it off)
        if (mc.gameRenderer.getMainCamera().getPosition().distanceTo(zero) < 6.0 * cloud.time.height) {
            level.setSkyFlashTime(3);
        }
    }

    public static void tick() {
        ClientLevel level = Minecraft.getInstance().level;
        for (NuclearCloud cloud : CLOUDS) {
            cloud.age++;
        }
        // gone once it has faded, or when you leave its dimension
        CLOUDS.removeIf(c -> c.age >= c.time.lifetime() * 20.0F || level == null || level.dimension() != c.dimension);
    }

    public static void clear() {
        CLOUDS.clear();
    }

    private float seconds(float partialTick) {
        return (this.age + partialTick) / 20.0F;
    }

    /**
     * How much the flash whites out the view (0 to 0.95): the brighter the flash, the nearer it and the more
     * directly you face it; scaled by the flashStrength setting, and none with "Hide Lightning Flashes" on.
     */
    public static float whiteOut(Camera camera, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.options.hideLightningFlash().get()) {
            return 0.0F;
        }
        float strength = ClientConfig.FLASH_STRENGTH.get() / 100.0F;
        Vec3 eye = camera.getPosition();
        Vector3f look = camera.getLookVector();
        float white = 0.0F;
        for (NuclearCloud cloud : CLOUDS) {
            if (cloud.dimension != mc.level.dimension()) {
                continue;
            }
            float flash = cloud.time.flash(cloud.seconds(partialTick)) * strength;
            if (flash <= 0.0F) {
                continue;
            }
            Vec3 to = cloud.flashCentre().subtract(eye);
            double dist = Math.max(to.length(), 1.0);
            float facing = 0.35F + 0.65F * Math.max(0.0F, (float) ((to.x * look.x() + to.y * look.y() + to.z * look.z()) / dist));
            float near = clamp(1.25F - (float) dist / (8.0F * cloud.time.height));
            white = Math.max(white, flash * facing * near);
        }
        return Math.min(white, 0.95F);
    }

    /**
     * How hard black rain falls from the clouds over {@code pos} (0 to 1), or NaN if there's no cloud over it (then
     * the server's own timing for the rain holds).
     */
    public static float fallout(Vec3 pos) {
        ClientLevel level = Minecraft.getInstance().level;
        float rain = Float.NaN;
        for (NuclearCloud cloud : CLOUDS) {
            if (level == null || cloud.dimension != level.dimension()) {
                continue;
            }
            double dx = pos.x - cloud.zero.x;
            double dz = pos.z - cloud.zero.z;
            if (dx * dx + dz * dz < 4.0 * cloud.time.height * cloud.time.height) {
                float f = cloud.time.fallout(cloud.seconds(0.0F));
                rain = Float.isNaN(rain) ? f : Math.max(rain, f);
            }
        }
        return rain;
    }

    private Vec3 flashCentre() {
        return this.zero.add(0.0, this.grounded ? 0.05 * this.time.height : 0.0, 0.0);
    }

    // ------------------------------------------------------------------ drawing

    /** Draws every cloud; called after the level's particles are drawn. */
    public static void renderAll(PoseStack poseStack, Camera camera, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (CLOUDS.isEmpty() || level == null) {
            return;
        }
        Vec3 sky = level.getCloudColor(partialTick);
        float[] fog = RenderSystem.getShaderFogColor();
        Light light = new Light((float) Math.max(sky.x, 0.12), (float) Math.max(sky.y, 0.12), (float) Math.max(sky.z, 0.12),
                fog[0], fog[1], fog[2], Math.max(mc.gameRenderer.getRenderDistance(), 32.0F),
                (0.35F + 0.65F * ClientConfig.FLASH_STRENGTH.get() / 100.0F) * (mc.options.hideLightningFlash().get() ? 0.5F : 1.0F));
        Vec3 cam = camera.getPosition();
        int n = 0;
        for (NuclearCloud cloud : CLOUDS) {
            if (cloud.dimension == level.dimension()) {
                n = cloud.build(level, cam, partialTick, light, n);
            }
        }
        Matrix4f pose = poseStack.last().pose();
        int smoke = 0;
        int glow = 0;
        for (int i = 0; i < n; i++) {
            Sprite s = SPRITES.get(i);
            smoke += s.sprite >= 0 ? 1 : 0;
            glow += s.glowSprite >= 0 ? 1 : 0;
        }
        // 1) the smoke (and the fireball's surface), see-through, sorted back to front
        if (smoke > 0) {
            BUFFER.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
            for (int i = 0; i < n; i++) {
                Sprite s = SPRITES.get(i);
                if (s.sprite >= 0) {
                    quad(pose, s, s.sprite, s.r, s.g, s.b, s.alpha);
                }
            }
            Types.SMOKE.end(BUFFER, RenderSystem.getVertexSorting());
        }
        // 2) added on top: the fire inside the hot puffs, the flash, the fireball's glare
        if (glow > 0) {
            BUFFER.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
            for (int i = 0; i < n; i++) {
                Sprite s = SPRITES.get(i);
                if (s.glowSprite >= 0) {
                    quad(pose, s, s.glowSprite, s.glowR, s.glowG, s.glowB, s.alpha);
                }
            }
            Types.GLOW.end(BUFFER, RenderSystem.getVertexSorting());
        }
    }

    /** One camera-facing sprite: its cell of the sheet, turned by its roll, in view space. */
    private static void quad(Matrix4f pose, Sprite s, int cell, float r, float g, float b, float a) {
        pose.transformPosition(s.x, s.y, s.z, VIEW);
        float cos = Mth.cos(s.roll);
        float sin = Mth.sin(s.roll);
        float w = s.half;
        float h = s.half * s.aspect;
        float inset = 0.5F / 256.0F;
        float u0 = (cell % 4) * 0.25F + inset;
        float v0 = (cell / 4) * 0.25F + inset;
        float u1 = u0 + 0.25F - 2.0F * inset;
        float v1 = v0 + 0.25F - 2.0F * inset;
        vertex(-w, -h, cos, sin, u0, v1, r, g, b, a);
        vertex(w, -h, cos, sin, u1, v1, r, g, b, a);
        vertex(w, h, cos, sin, u1, v0, r, g, b, a);
        vertex(-w, h, cos, sin, u0, v0, r, g, b, a);
    }

    private static void vertex(float dx, float dy, float cos, float sin, float u, float v, float r, float g, float b, float a) {
        BUFFER.vertex(VIEW.x() + dx * cos - dy * sin, VIEW.y() + dx * sin + dy * cos, VIEW.z())
                .uv(u, v).color(Math.min(r, 1.0F), Math.min(g, 1.0F), Math.min(b, 1.0F), Math.min(a, 1.0F)).endVertex();
    }

    /** Lays out this cloud's puffs as they are at this moment, into SPRITES from index n; returns the new count. */
    private int build(ClientLevel level, Vec3 cam, float partialTick, Light light, int n) {
        float t = seconds(partialTick);
        BlastTimeline tl = this.time;
        if (t >= tl.lifetime()) {
            return n;
        }
        float height = tl.height;
        float centre = tl.capCentre(t);
        float a = tl.capRadius(t);
        float b = tl.capHalfHeight(t);
        float spread = tl.spread(t);
        float heat = tl.heat(t);
        float white = tl.white(t);
        float d = tl.dissipated(t);
        float roll = tl.roll(t);
        float blaze = tl.blaze(t);
        float drift = 0.12F * height * d * d;
        float fireball = tl.fireballSeconds;
        double ox = this.zero.x - cam.x + this.windX * drift;
        double oy = this.zero.y - cam.y;
        double oz = this.zero.z - cam.z + this.windZ * drift;
        float[] smokeColour = new float[3];
        float[] hot = new float[3];

        for (Puff p : this.puffs) {
            float fade = 1.0F - smoothstep(p.fadeAt, p.fadeAt + 0.3F, d);
            if (fade <= 0.0F) {
                continue;
            }
            float alpha = fade * (1.0F - 0.35F * d);
            float grow = 1.0F + 0.6F * d;
            float x;
            float y;
            float z;
            float size;
            float h = 0.0F;
            float baseAlpha;
            switch (p.group) {
                case HEAD, DOME, UNDER -> {
                    float theta;
                    float rad;
                    float normalY;
                    float outer;
                    float localHeat;
                    if (p.group == HEAD) {
                        theta = p.a * TAU;
                        float phi = p.b * TAU - roll * (1.0F - 0.5F * p.c);
                        float depth = p.c;
                        float core = 0.55F * a * spread;
                        float shrink = 1.0F - 0.5F * depth;
                        rad = core + (a - core) * shrink * Mth.cos(phi);
                        y = b * shrink * Mth.sin(phi);
                        normalY = Mth.sin(phi);
                        outer = (1.0F - depth) * clamp(0.5F + 0.5F * Mth.cos(phi));
                        float lowness = clamp(0.5F - 0.5F * Mth.sin(phi));
                        localHeat = clamp((float) Math.pow(lowness, 1.5) * (0.6F + 0.5F * depth) + 0.1F * depth);
                    } else if (p.group == DOME) {
                        theta = p.a * TAU + 0.01F * t;
                        rad = Mth.sqrt(p.b) * 0.55F * a;
                        float across = rad / Math.max(0.62F * a, 1.0E-3F);
                        y = 0.2F * b + 0.6F * b * Mth.sqrt(Math.max(0.0F, 1.0F - across * across)) * (0.6F + 0.4F * p.c);
                        normalY = 0.9F;
                        outer = 0.4F;
                        localHeat = 0.05F * p.c;
                    } else {
                        theta = p.a * TAU - 0.008F * t;
                        rad = (0.15F + 0.75F * Mth.sqrt(p.b)) * a;
                        y = -b * (0.62F + 0.18F * p.c);
                        normalY = -1.0F;
                        outer = 0.2F;
                        localHeat = clamp(0.7F + 0.3F * p.c);
                    }
                    x = rad * Mth.cos(theta);
                    z = rad * Mth.sin(theta);
                    y += centre;
                    size = p.size * (0.22F * a + 0.08F * b) * grow * (p.group == UNDER ? 0.9F : 1.0F);
                    h = heat * Math.max(localHeat, blaze);
                    if (p.linger) {
                        float away = 0.25F * height * d;
                        x += Mth.cos(theta) * away;
                        y += 0.2F * away;
                        z += Mth.sin(theta) * away;
                    }
                    baseAlpha = 1.0F;
                    p.smokeColour(normalY, outer, smokeColour);
                }
                case STEM -> {
                    if (!this.grounded || t < 0.6F) {
                        continue;
                    }
                    float top = centre - 0.55F * b;
                    float f = (p.b + t / 45.0F) % 1.0F;
                    y = f * top;
                    float theta = p.a * TAU + 0.03F * t + 2.5F * f;
                    float r0 = BlastTimeline.STEM_RADIUS * height;
                    float r = r0 * (1.0F + 1.6F * pow(1.0F - f, 6) + 1.4F * pow(f, 5)) * (0.55F + 0.45F * p.c);
                    r *= 1.0F + 0.18F * Mth.sin(3.0F * p.a * TAU + 5.0F * f + 0.1F * t);
                    r += 0.1F * height * d * p.e;
                    if (p.linger) {
                        r += 0.2F * height * d;
                    }
                    x = r * Mth.cos(theta);
                    z = r * Mth.sin(theta);
                    size = p.size * r0 * (1.25F + 0.8F * pow(1.0F - f, 4) + 0.7F * f * f * f) * grow;
                    h = heat * clamp(f * f * 0.9F);
                    baseAlpha = smoothstep(0.0F, 0.06F, f) * (1.0F - smoothstep(0.9F, 1.0F, f)) * smoothstep(0.6F, 2.5F, t);
                    p.smokeColour(0.2F, 0.3F, smokeColour);
                    mixInto(smokeColour, DUST_COLOUR, 0.8F, clamp(1.0F - f * 2.2F));
                }
                case SKIRT -> {
                    if (!this.grounded) {
                        continue;
                    }
                    float spreadOut = (1.0F - (float) Math.exp(-t / 5.0F)) + 0.15F * tl.matured(t) + 0.35F * d;
                    float rad = BlastTimeline.SKIRT_RADIUS * height * Mth.sqrt(p.b) * spreadOut;
                    float theta = p.a * TAU + 0.004F * t;
                    size = (0.06F + 0.05F * (1.0F - p.b)) * height * p.size * grow * (0.5F + 0.5F * Math.min(1.0F, spreadOut));
                    x = rad * Mth.cos(theta);
                    z = rad * Mth.sin(theta);
                    y = ground(level, x, z) + 0.45F * size + 0.05F * height * (1.0F - p.b) * p.c;
                    baseAlpha = 0.92F * smoothstep(0.2F, 0.9F, t);
                    for (int k = 0; k < 3; k++) {
                        smokeColour[k] = mix(DUST_COLOUR[k], BROWN[k], p.brown) * (0.8F + 0.3F * p.shade);
                    }
                }
                case WAVE -> {
                    float radius = tl.waveRadius(t) * (1.0F + 0.03F * (p.b - 0.5F));
                    if (!this.grounded || radius < 1.0F) {
                        continue;
                    }
                    float theta = (p.a + 0.3F * p.c) * TAU;
                    x = radius * Mth.cos(theta);
                    z = radius * Mth.sin(theta);
                    size = (0.035F + 0.05F * radius / (BlastTimeline.WAVE_REACH * height)) * height * p.size;
                    y = ground(level, x, z) + 0.4F * size;
                    baseAlpha = smoothstep(0.0F, 0.25F, t) * (1.0F - smoothstep(0.55F * tl.shockwaveSeconds, 1.25F * tl.shockwaveSeconds, t));
                    for (int k = 0; k < 3; k++) {
                        smokeColour[k] = DUST_COLOUR[k] * (0.85F + 0.3F * p.shade);
                    }
                }
                case HAZE -> {
                    float u = 0.15F + 0.85F * Mth.sqrt(p.b);
                    float since = t - tl.wavePasses(u);
                    if (!this.grounded || since < 0.0F) {
                        continue;
                    }
                    float theta = p.a * TAU;
                    float radius = u * BlastTimeline.WAVE_REACH * height;
                    x = radius * Mth.cos(theta);
                    z = radius * Mth.sin(theta);
                    size = (0.1F + 0.04F * u) * height * p.size * (1.0F + Math.min(0.3F, 0.3F * since / 60.0F)) * grow;
                    y = ground(level, x, z) + 0.25F * size + 0.02F * height * (1.0F - (float) Math.exp(-since / 20.0F));
                    baseAlpha = 0.3F * smoothstep(0.0F, 2.5F, since);
                    for (int k = 0; k < 3; k++) {
                        smokeColour[k] = mix(DUST_COLOUR[k], GREY[k], 0.4F * p.shade);
                    }
                }
                default -> { // RING
                    float radius = 0.12F * height + 0.75F * height * (1.0F - (float) Math.exp(-t));
                    float theta = (p.a + 0.2F * p.c) * TAU;
                    x = radius * Mth.cos(theta);
                    z = radius * Mth.sin(theta);
                    y = (this.grounded ? BlastTimeline.RING_ALTITUDE * height : 0.0F) + (p.b - 0.5F) * 0.03F * height;
                    size = 0.045F * height * p.size * (1.0F + radius / height);
                    baseAlpha = 0.5F * smoothstep(0.15F, 0.45F, t) * (1.0F - smoothstep(1.2F, 3.0F, t));
                    System.arraycopy(MIST, 0, smokeColour, 0, 3);
                }
            }
            alpha *= baseAlpha;
            if (alpha <= 0.004F) {
                continue;
            }
            float hv = smoothstep(0.05F, 0.6F, h);
            hotColour(h, white, hot);
            Sprite s = next(n++);
            s.place(ox + x, oy + y, oz + z, size, p.aspect, p.roll + p.spin * t);
            // (a squashed puff is squashed only seen from the side: from above or below it's round)
            s.aspect = mix(p.aspect, 1.0F, Math.abs(s.y) / Math.max(s.distance(), 1.0E-3F));
            float haze = light.haze(s.distance());
            for (int k = 0; k < 3; k++) {
                float c = smokeColour[k] * light.sky[k] * (1.0F - hv) * (1.0F - haze) + light.fog[k] * haze + hot[k] * hv;
                s.setColour(k, c);
            }
            s.sprite = p.sprite;
            s.alpha = alpha * s.nearFade();
            int glowCell = glowOf(p.sprite);
            float glowStrength = h * GLOW_GAIN;
            s.glowSprite = glowCell >= 0 && h * GLOW_GAIN * Math.max(hot[0], Math.max(hot[1], hot[2])) > 0.01F ? glowCell : -1;
            s.glowR = hot[0] * glowStrength;
            s.glowG = hot[1] * glowStrength;
            s.glowB = hot[2] * glowStrength;
        }

        // the flash, and the fireball's own blaze and glare
        float flash = tl.flash(t);
        Vec3 flashAt = flashCentre();
        if (flash > 0.0F) {
            float size = height * (0.05F + 0.35F * (1.0F - (float) Math.exp(-t / 0.12F)));
            n = glare(n, flashAt.x - cam.x, flashAt.y - cam.y, flashAt.z - cam.z, size, FLASH, WHITE, flash);
            n = glare(n, flashAt.x - cam.x, flashAt.y - cam.y, flashAt.z - cam.z, size * 2.8F, FLASH, FLASH_YELLOW, 0.55F * flash * light.glare);
        }
        if (t < 1.3F * fireball) {
            hotColour(1.0F, white, hot);
            double fy = this.zero.y - cam.y + centre;
            float ablaze = 1.0F - smoothstep(0.3F * fireball, 1.3F * fireball, t);
            n = glare(n, this.zero.x - cam.x, fy, this.zero.z - cam.z, 1.25F * Math.max(a, b), FIREBALL, hot, ablaze);
            if (t < fireball) {
                Sprite s = next(n++);
                s.place(this.zero.x - cam.x, fy, this.zero.z - cam.z, 1.15F * tl.fireballRadius(t), 1.0F, 0.0F);
                s.sprite = FIREBALL;
                s.r = hot[0];
                s.g = hot[1];
                s.b = hot[2];
                s.alpha = (1.0F - smoothstep(0.4F * fireball, fireball, t)) * s.nearFade();
                s.glowSprite = -1;
            }
        }
        return n;
    }

    /** A sprite only added on (in the second pass). */
    private static int glare(int n, double x, double y, double z, float size, int cell, float[] colour, float strength) {
        Sprite s = next(n);
        s.place(x, y, z, size, 1.0F, 0.0F);
        s.sprite = -1;
        s.alpha = s.nearFade();
        s.glowSprite = cell;
        s.glowR = colour[0] * strength;
        s.glowG = colour[1] * strength;
        s.glowB = colour[2] * strength;
        return n + 1;
    }

    private static Sprite next(int index) {
        while (SPRITES.size() <= index) {
            SPRITES.add(new Sprite());
        }
        return SPRITES.get(index);
    }

    /** Height of the ground above ground zero at (x, z) from it: the dust rolls over hills and down into valleys. */
    private float ground(ClientLevel level, float x, float z) {
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(this.zero.x + x), Mth.floor(this.zero.z + z));
        if (top <= level.getMinBuildHeight()) {
            return 0.0F; // (not loaded here)
        }
        return Mth.clamp((float) (top - this.zero.y), -64.0F, 0.5F * this.time.height);
    }

    private static int glowOf(int sprite) {
        return sprite >= 0 && sprite < 4 ? sprite + 4 : sprite == COLUMN ? COLUMN + 1 : -1;
    }

    private static float pow(float x, int n) {
        float r = 1.0F;
        for (int i = 0; i < n; i++) {
            r *= x;
        }
        return r;
    }

    private static void mixInto(float[] colour, float[] towards, float scale, float amount) {
        for (int k = 0; k < 3; k++) {
            colour[k] = mix(colour[k], towards[k] * scale, amount);
        }
    }

    /** Fire's colour as it cools: white-hot, yellow, orange, deep red. */
    private static void hotColour(float heat, float white, float[] out) {
        float toOrange = clamp(heat * 2.0F);
        float toYellow = clamp(heat * 2.0F - 1.0F);
        for (int k = 0; k < 3; k++) {
            out[k] = mix(mix(mix(DEEP_RED[k], ORANGE[k], toOrange), YELLOW[k], toYellow), WHITE[k], white);
        }
    }

    /** One of the cloud's puffs: what it is, and its own random numbers (where it sits, how big and dark it is). */
    private static final class Puff {
        final int group;
        final float a;
        final float b;
        final float c;
        final float d;
        final float e;
        final float size;
        final int sprite;
        final float shade;
        final float brown;
        final float roll;
        final float spin;
        final boolean linger;
        final float fadeAt;
        final float aspect;

        Puff(int group, RandomSource random) {
            this.group = group;
            this.a = random.nextFloat();
            this.b = random.nextFloat();
            this.c = random.nextFloat();
            this.d = random.nextFloat();
            this.e = random.nextFloat();
            this.size = 0.8F + 0.5F * random.nextFloat();
            int smoke = random.nextInt(4);
            this.sprite = switch (group) {
                case STEM -> this.e < 0.6F ? COLUMN : smoke;
                case SKIRT -> this.e < 0.7F ? DUST : smoke;
                case WAVE -> DUST;
                case HAZE -> this.e < 0.85F ? smoke : WISP;
                case RING -> this.e < 0.5F ? WISP : DUST;
                default -> smoke;
            };
            this.shade = random.nextFloat();
            this.brown = 0.6F * random.nextFloat();
            // sprites with a flat bottom or a long axis stay (nearly) upright; the billows are lit from above
            float tilt = (random.nextFloat() - 0.5F) * 0.7F;
            this.roll = this.sprite == DUST || this.sprite == WISP || this.sprite == COLUMN ? tilt * 0.35F : tilt;
            this.spin = (random.nextFloat() - 0.5F) * 0.004F;
            this.linger = random.nextFloat() < 0.08F;
            this.fadeAt = FADE_START[group] + FADE_SPREAD[group] * this.d + (group == HEAD ? 0.3F * this.c : 0.0F)
                    + (this.linger ? 0.3F : 0.0F);
            this.aspect = ASPECT[group];
        }

        /** Its smoke: charcoal to grey, some browner, lit from above (lighter billows outside), dark underneath. */
        void smokeColour(float normalY, float outer, float[] out) {
            float lit = clamp(0.5F + 0.5F * normalY);
            float under = 1.0F - 0.5F * clamp(-normalY);
            for (int k = 0; k < 3; k++) {
                float c = mix(mix(CHARCOAL[k], GREY[k], this.shade), BROWN[k], this.brown) * (0.55F + 0.6F * lit);
                out[k] = mix(c, PALE[k], 0.65F * lit * outer) * under;
            }
        }
    }

    /** A sprite to draw this frame: where (from the camera), how big, and its colours in each pass. */
    private static final class Sprite {
        float x;
        float y;
        float z;
        float half;
        float aspect;
        float roll;
        int sprite;
        float r;
        float g;
        float b;
        float alpha;
        int glowSprite;
        float glowR;
        float glowG;
        float glowB;

        void place(double x, double y, double z, float half, float aspect, float roll) {
            this.x = (float) x;
            this.y = (float) y;
            this.z = (float) z;
            this.half = half;
            this.aspect = aspect;
            this.roll = roll;
        }

        float distance() {
            return Mth.sqrt(this.x * this.x + this.y * this.y + this.z * this.z);
        }

        /** Fading out as the camera comes inside it, rather than a wall of smoke over the whole view. */
        float nearFade() {
            return smoothstep(0.35F * this.half, 1.2F * this.half, distance());
        }

        void setColour(int k, float value) {
            if (k == 0) {
                this.r = value;
            } else if (k == 1) {
                this.g = value;
            } else {
                this.b = value;
            }
        }
    }

    /** This frame's light: the sky's (on the smoke), the fog's colour and distance, and how bright the glare may be. */
    private record Light(float[] sky, float[] fog, float renderDistance, float glare) {
        Light(float skyR, float skyG, float skyB, float fogR, float fogG, float fogB, float renderDistance, float glare) {
            this(new float[] {skyR, skyG, skyB}, new float[] {fogR, fogG, fogB}, renderDistance, glare);
        }

        /** How much of the distance haze is over a puff this far off: none within the render distance. */
        float haze(float distance) {
            return 0.5F * smoothstep(this.renderDistance, 6.0F * this.renderDistance, distance);
        }
    }

    /**
     * The two passes: the smoke (Minecraft's plain textured shader: lit and hazed here, no fog of its own), see-through,
     * sorted back to front, hiding what's behind it; and the fire and glare, added on (like lightning), behind what's
     * in front of them.
     */
    private static final class Types extends RenderType {
        private static final ShaderStateShard SHADER = new ShaderStateShard(GameRenderer::getPositionTexColorShader);

        static final RenderType SMOKE = create(GodzillaMinusOne.MODID + ":nuclear_cloud_smoke", DefaultVertexFormat.POSITION_TEX_COLOR,
                VertexFormat.Mode.QUADS, 1 << 17, false, true,
                CompositeState.builder()
                        .setShaderState(SHADER)
                        .setTextureState(new TextureStateShard(TEXTURE, false, false))
                        .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                        .setCullState(NO_CULL)
                        .setWriteMaskState(COLOR_DEPTH_WRITE)
                        .createCompositeState(false));

        static final RenderType GLOW = create(GodzillaMinusOne.MODID + ":nuclear_cloud_glow", DefaultVertexFormat.POSITION_TEX_COLOR,
                VertexFormat.Mode.QUADS, 1 << 16, false, false,
                CompositeState.builder()
                        .setShaderState(SHADER)
                        .setTextureState(new TextureStateShard(TEXTURE, false, false))
                        .setTransparencyState(LIGHTNING_TRANSPARENCY)
                        .setCullState(NO_CULL)
                        .setWriteMaskState(COLOR_WRITE)
                        .createCompositeState(false));

        private Types(String name, VertexFormat format, VertexFormat.Mode mode, int bufferSize, boolean crumbling, boolean sort,
                      Runnable setup, Runnable clear) {
            super(name, format, mode, bufferSize, crumbling, sort, setup, clear);
        }
    }
}
