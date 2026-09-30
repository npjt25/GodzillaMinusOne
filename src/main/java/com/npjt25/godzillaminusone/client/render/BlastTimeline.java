package com.npjt25.godzillaminusone.client.render;

/**
 * The atomic blast's timeline and the shape of its cloud (client.render.NuclearCloud), after the Ginza blast in
 * Godzilla Minus One: the flash; the fireball, blazing as it swells and rises; the dust wave rolling out over the
 * ground; the column rising under the fireball as it spreads into the cap; the cloud full-grown, its fire cooling;
 * and at last its breaking up. Plain arithmetic (no Minecraft classes), so it can be tested anywhere and ported
 * (tools/nuclear_cloud_model.py is the same, for the offline previews).
 *
 * <p>All in blocks from ground zero and seconds from the impact. The cloud's proportions are fractions of its
 * height (the top of the cap once it has risen):
 */
public final class BlastTimeline {
    /** The fireball's radius when fully swollen. */
    public static final float FIREBALL_RADIUS = 0.15F;
    /** The cap's radius, and half its thickness, once it has spread, and the height of its middle. */
    public static final float CAP_RADIUS = 0.5F;
    public static final float CAP_HALF_HEIGHT = 0.12F;
    public static final float CAP_CENTRE = 0.8F;
    /** The column's radius at its narrowest (it flares out at its foot and into the cap). */
    public static final float STEM_RADIUS = 0.075F;
    /** How far the dust churned up at the column's foot spreads. */
    public static final float SKIRT_RADIUS = 0.36F;
    /** How far the dust wave rolls out. */
    public static final float WAVE_REACH = 0.95F;
    /** How high the ring of mist the blast's pressure wave leaves in the air goes round. */
    public static final float RING_ALTITUDE = 0.3F;

    public final float height;
    public final float flashSeconds;
    public final float fireballSeconds;
    public final float shockwaveSeconds;
    public final float riseSeconds;
    public final float coolingSeconds;
    public final float matureSeconds;
    public final float dissipateSeconds;

    public BlastTimeline(float height, float flash, float fireball, float shockwave, float rise, float cooling, float mature, float dissipate) {
        this.height = height;
        this.flashSeconds = flash;
        this.fireballSeconds = fireball;
        this.shockwaveSeconds = shockwave;
        this.riseSeconds = Math.max(rise, 1.0F);
        this.coolingSeconds = Math.max(cooling, 0.5F);
        this.matureSeconds = mature;
        this.dissipateSeconds = Math.max(dissipate, 1.0F);
    }

    /** How long the cloud is there at all. */
    public float lifetime() {
        return this.riseSeconds + this.matureSeconds + this.dissipateSeconds;
    }

    /** How bright the flash is (0 to 1): full at once, then dying away over flashSeconds. */
    public float flash(float t) {
        if (t < 0.0F || t > this.flashSeconds) {
            return 0.0F;
        }
        float rise = 0.06F;
        float v = t < rise ? t / rise : (float) Math.exp(-(t - rise) / (0.22F * this.flashSeconds));
        return v * (1.0F - smoothstep(0.6F * this.flashSeconds, this.flashSeconds, t));
    }

    /** How hot the cloud still is inside (0 to 1): white-hot at first, cooled by coolingSeconds. */
    public float heat(float t) {
        return 1.0F - smoothstep(0.3F, this.coolingSeconds, t);
    }

    /** How white its heat still is, rather than yellow, orange or red: for the first second or so. */
    public float white(float t) {
        return (float) Math.exp(-Math.max(t, 0.0F) / 0.6F);
    }

    /** The whole fireball ablaze (1), before it cools from the outside in (0 by 1.5 x fireballSeconds). */
    public float blaze(float t) {
        return 1.0F - smoothstep(0.5F * this.fireballSeconds, 1.5F * this.fireballSeconds, t);
    }

    /** How far through its time full-grown it is (0 to 1). */
    public float matured(float t) {
        return clamp((t - this.riseSeconds) / Math.max(this.matureSeconds, 1.0E-3F));
    }

    /** How far through breaking up it is (0 to 1). */
    public float dissipated(float t) {
        return clamp((t - this.riseSeconds - this.matureSeconds) / this.dissipateSeconds);
    }

    public float fireballRadius(float t) {
        return FIREBALL_RADIUS * this.height * (1.0F - (float) Math.exp(-Math.max(t, 0.0F) / 0.35F));
    }

    /** How far the fireball has risen towards the cap's height (0 to 1): quickly at first, slower and slower. */
    public float risen(float t) {
        float x = clamp((t - 0.4F) / (this.riseSeconds - 0.4F));
        return 1.0F - (float) Math.pow(1.0F - x, 2.6);
    }

    /** How far the fireball has spread into the cap (0 to 1). */
    public float spread(float t) {
        float x = clamp((t - 0.4F) / (this.riseSeconds * 0.8F));
        return 1.0F - (1.0F - x) * (1.0F - x);
    }

    /** Height of the middle of the fireball, then of the cap it becomes. */
    public float capCentre(float t) {
        float r = fireballRadius(t) * 0.55F;
        return r + (CAP_CENTRE * this.height - r) * risen(t) + 0.04F * this.height * matured(t) + 0.06F * this.height * dissipated(t);
    }

    /** Its radius across. */
    public float capRadius(float t) {
        return mix(fireballRadius(t), CAP_RADIUS * this.height, spread(t)) + 0.06F * this.height * matured(t)
                + 0.3F * this.height * dissipated(t);
    }

    /** Half its thickness. */
    public float capHalfHeight(float t) {
        return mix(fireballRadius(t), CAP_HALF_HEIGHT * this.height, spread(t)) * (1.0F + 0.3F * dissipated(t));
    }

    /** How far the cap has rolled over on itself (radians): rising in its middle, over its top, down its outside. */
    public float roll(float t) {
        return (float) (2.0 * Math.PI * t / 120.0 + 1.2 * (1.0 - Math.exp(-t / 8.0)));
    }

    /** How far the dust wave has rolled out: fast, then slowing, all the way out at shockwaveSeconds. */
    public float waveRadius(float t) {
        float tau = this.shockwaveSeconds / 3.0F;
        return WAVE_REACH * this.height * (1.0F - (float) Math.exp(-Math.max(t, 0.0F) / tau))
                / (1.0F - (float) Math.exp(-this.shockwaveSeconds / tau));
    }

    /** When the dust wave reaches {@code u} (0 to 1) of its reach. */
    public float wavePasses(float u) {
        float tau = this.shockwaveSeconds / 3.0F;
        return (float) (-tau * Math.log(Math.max(1.0E-6, 1.0 - u * (1.0 - Math.exp(-this.shockwaveSeconds / tau)))));
    }

    /**
     * How hard the black rain falls from the cloud (0 to 1): from a little after the fireball, all the while it
     * towers overhead, easing off as it breaks up.
     */
    public float fallout(float t) {
        return smoothstep(this.fireballSeconds + 2.0F, this.fireballSeconds + 6.0F, t) * (1.0F - smoothstep(0.0F, 0.6F, dissipated(t)));
    }

    public static float clamp(float x) {
        return x < 0.0F ? 0.0F : Math.min(x, 1.0F);
    }

    public static float smoothstep(float a, float b, float x) {
        if (b <= a) {
            return x >= b ? 1.0F : 0.0F;
        }
        float t = clamp((x - a) / (b - a));
        return t * t * (3.0F - 2.0F * t);
    }

    public static float mix(float a, float b, float t) {
        return a + (b - a) * t;
    }
}
