package com.npjt25.godzillaminusone.client;

import com.google.gson.JsonSyntaxException;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.npjt25.godzillaminusone.GodzillaMinusOne;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.PostPass;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.util.List;

/**
 * Blurs the whole view (vanilla's two-pass "blur" shader program) with an adjustable radius: the blurred
 * vision after an atomic blast or a naval mine.
 */
final class BlurEffect {
    private static final ResourceLocation CHAIN = GodzillaMinusOne.id("shaders/post/blast_blur.json");
    /** Blur radius in pixels at full strength on a 1080 pixel tall window. */
    private static final float MAX_RADIUS = 14.0F;

    private static PostChain chain;
    private static List<PostPass> passes = List.of();
    private static int width = -1;
    private static int height = -1;
    private static boolean failed;

    static void render(float strength) {
        Minecraft mc = Minecraft.getInstance();
        RenderTarget main = mc.getMainRenderTarget();
        // The shader averages 2r+1 samples, so keep r a multiple of 0.5 or the picture gets darker.
        float radius = Math.round(strength * MAX_RADIUS * Math.max(main.height, 360) / 1080.0F * 2.0F) / 2.0F;
        if (radius < 0.5F || failed || !load(mc, main)) {
            return;
        }
        if (main.width != width || main.height != height) {
            width = main.width;
            height = main.height;
            chain.resize(width, height);
        }
        for (PostPass pass : passes) {
            pass.getEffect().safeGetUniform("Radius").set(radius);
        }
        RenderSystem.disableBlend();
        RenderSystem.disableDepthTest();
        RenderSystem.resetTextureMatrix();
        chain.process(mc.getFrameTime());
        main.bindWrite(false);
        RenderSystem.enableDepthTest();
    }

    private static boolean load(Minecraft mc, RenderTarget main) {
        if (chain != null) {
            return true;
        }
        try {
            chain = new PostChain(mc.getTextureManager(), mc.getResourceManager(), main, CHAIN);
            RenderTarget swap = chain.getTempTarget("swap");
            PostPass horizontal = chain.addPass("blur", main, swap);
            PostPass vertical = chain.addPass("blur", swap, main);
            horizontal.getEffect().safeGetUniform("BlurDir").set(1.0F, 0.0F);
            vertical.getEffect().safeGetUniform("BlurDir").set(0.0F, 1.0F);
            passes = List.of(horizontal, vertical);
            width = main.width;
            height = main.height;
            chain.resize(width, height);
            return true;
        } catch (IOException | JsonSyntaxException e) {
            GodzillaMinusOne.LOGGER.warn("Could not load the blast blur shader", e);
            failed = true;
            close();
            return false;
        }
    }

    static void close() {
        if (chain != null) {
            chain.close();
            chain = null;
        }
        passes = List.of();
        width = height = -1;
    }

    private BlurEffect() {
    }
}
