package com.npjt25.godzillaminusone.client.render;

import com.npjt25.godzillaminusone.GodzillaMinusOne;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * The high-detail Godzilla mesh (godzilla_mesh/godzilla.mesh, written by tools/build_godzilla_mesh.py): triangles
 * with UVs, normals and two skin weights per vertex, bound to the bones of the GeckoLib skeleton.
 */
public final class GodzillaMesh {
    public static final ResourceLocation LOCATION = GodzillaMinusOne.id("godzilla_mesh/godzilla.mesh");

    public final String[] bones;
    public final String[] plateBones;
    /** Charge-animation time (s) at which each dorsal plate punches up and lights. */
    public final float[] plateTimes;
    public final Part[] parts;

    public static final class Part {
        public final ResourceLocation texture;
        public final boolean plates;
        public final int vertexCount;
        public final float[] pos;
        public final float[] normal;
        public final float[] uv;
        public final byte[] bone0;
        public final byte[] bone1;
        public final float[] weight0;
        public final byte[] glowGroup;
        public int[] triangles = new int[0];

        Part(ResourceLocation texture, boolean plates, int n) {
            this.texture = texture;
            this.plates = plates;
            this.vertexCount = n;
            this.pos = new float[n * 3];
            this.normal = new float[n * 3];
            this.uv = new float[n * 2];
            this.bone0 = new byte[n];
            this.bone1 = new byte[n];
            this.weight0 = new float[n];
            this.glowGroup = new byte[n];
        }
    }

    @Nullable
    private static GodzillaMesh instance;

    private GodzillaMesh(String[] bones, String[] plateBones, float[] plateTimes, Part[] parts) {
        this.bones = bones;
        this.plateBones = plateBones;
        this.plateTimes = plateTimes;
        this.parts = parts;
    }

    @Nullable
    public static GodzillaMesh get() {
        return instance;
    }

    public static ResourceManagerReloadListener reloadListener() {
        return manager -> {
            try {
                instance = load(manager);
            } catch (IOException | RuntimeException e) {
                instance = null;
                GodzillaMinusOne.LOGGER.error("Could not load the Godzilla mesh {}", LOCATION, e);
            }
        };
    }

    private static GodzillaMesh load(ResourceManager manager) throws IOException {
        try (InputStream raw = manager.open(LOCATION); DataInputStream in = new DataInputStream(new BufferedInputStream(raw))) {
            byte[] magic = new byte[4];
            in.readFully(magic);
            if (magic[0] != 'G' || magic[1] != 'Z' || magic[2] != 'M' || magic[3] != '1') {
                throw new IOException("not a Godzilla mesh file");
            }
            String[] bones = new String[in.readInt()];
            for (int i = 0; i < bones.length; i++) {
                bones[i] = in.readUTF();
            }
            int plateCount = in.readInt();
            String[] plateBones = new String[plateCount];
            float[] plateTimes = new float[plateCount];
            for (int i = 0; i < plateCount; i++) {
                plateBones[i] = in.readUTF();
                plateTimes[i] = in.readFloat();
            }
            Part[] parts = new Part[in.readInt()];
            for (int p = 0; p < parts.length; p++) {
                ResourceLocation texture = new ResourceLocation(in.readUTF());
                boolean plates = in.readByte() != 0;
                int n = in.readInt();
                Part part = new Part(texture, plates, n);
                for (int v = 0; v < n; v++) {
                    for (int k = 0; k < 3; k++) {
                        part.pos[v * 3 + k] = in.readFloat();
                    }
                    for (int k = 0; k < 3; k++) {
                        part.normal[v * 3 + k] = in.readFloat();
                    }
                    part.uv[v * 2] = in.readFloat();
                    part.uv[v * 2 + 1] = in.readFloat();
                    part.bone0[v] = in.readByte();
                    part.bone1[v] = in.readByte();
                    part.weight0[v] = in.readFloat();
                    part.glowGroup[v] = in.readByte();
                }
                part.triangles = new int[in.readInt() * 3];
                for (int i = 0; i < part.triangles.length; i++) {
                    part.triangles[i] = in.readInt();
                }
                parts[p] = part;
            }
            return new GodzillaMesh(bones, plateBones, plateTimes, parts);
        }
    }
}
