package com.npjt25.godzillaminusone.client;

import com.npjt25.godzillaminusone.GodzillaMinusOne;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;

/**
 * Pulls the third-person camera further back than Minecraft's 4 blocks, so something big (Godzilla as a player's
 * shape, a Shinden and its pilot) fits in the picture: it looks at {@code middle} from {@code distance} away, along
 * the way the camera already faces, coming in closer if the ground or a wall is in the way.
 */
public final class CameraPlacement {
    @Nullable
    private static Method setPosition;
    private static boolean lookedUp;

    static void pullBack(Camera camera, Entity around, Vec3 middle, double distance) {
        Vec3 look = new Vec3(camera.getLookVector());
        Vec3 at = middle.subtract(look.scale(distance));
        HitResult hit = around.level().clip(new ClipContext(middle, at, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, around));
        if (hit.getType() != HitResult.Type.MISS) {
            at = hit.getLocation().add(look.scale(0.5));
        }
        place(camera, at);
    }

    /**
     * Camera#setPosition(Vec3) is protected, and its name differs between the development and the released game, so
     * it's found by its signature: the only method of Camera taking just a Vec3 and returning nothing.
     */
    public static void place(Camera camera, Vec3 at) {
        if (!lookedUp) {
            lookedUp = true;
            for (Method method : Camera.class.getDeclaredMethods()) {
                if (!Modifier.isStatic(method.getModifiers()) && method.getReturnType() == void.class
                        && Arrays.equals(method.getParameterTypes(), new Class<?>[] {Vec3.class})) {
                    try {
                        method.setAccessible(true);
                        setPosition = method;
                    } catch (RuntimeException e) {
                        GodzillaMinusOne.LOGGER.warn("Can't move the camera back", e);
                    }
                    break;
                }
            }
        }
        if (setPosition != null) {
            try {
                setPosition.invoke(camera, at);
            } catch (ReflectiveOperationException e) {
                setPosition = null;
            }
        }
    }

    private CameraPlacement() {
    }
}
