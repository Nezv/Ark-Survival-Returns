package dev.nez.arksurvivalreturns.client.draw;

import java.io.InputStream;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import com.mojang.blaze3d.platform.NativeImage;
import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.ARGB;
import net.minecraft.util.Util;

/**
 * What one texture holds under the faces of a {@link CreatureMesh}: which cubes are covered by fully opaque
 * texels on every face (only those hide their own far sides) and which faces are covered by fully transparent
 * ones (those draw nothing). Read from the image off the render thread; until it is there, and when the image
 * cannot be read, nothing is known and nothing is skipped.
 */
final class TextureCoverage {
    @FunctionalInterface
    interface Alpha {
        int at(int x, int y);
    }

    private final CreatureMesh mesh;
    /** Bit per cube: every face of it shows opaque texels only. */
    final long[] opaqueCubes;
    /** Bit per face (cube * 6 + face): transparent texels only. */
    final long[] emptyFaces;
    volatile boolean ready;

    TextureCoverage(CreatureMesh mesh) {
        this.mesh = mesh;
        opaqueCubes = new long[(mesh.cubes + 63) / 64];
        emptyFaces = new long[(mesh.cubes * CreatureMesh.FACES + 63) / 64 + 1];   // one more: the writer reads six bits across a word's end
    }

    void load(Identifier texture) {
        Minecraft minecraft = Minecraft.getInstance();
        CompletableFuture.runAsync(() -> {
            Optional<Resource> resource = minecraft.getResourceManager().getResource(texture);
            if (resource.isEmpty()) return;
            try (InputStream stream = resource.get().open(); NativeImage image = NativeImage.read(stream)) {
                read(image.getWidth(), image.getHeight(), (x, y) -> ARGB.alpha(image.getPixel(x, y)));
            } catch (Exception | LinkageError e) {
                ArkSurvivalReturns.LOGGER.warn("Could not read {} for creature drawing; its creatures are drawn without skipped faces", texture, e);
            }
        }, Util.backgroundExecutor());
    }

    /** Fills the masks from an image of the given size and publishes them. */
    void read(int width, int height, Alpha alpha) {
        float[] uv = mesh.uv;
        for (int cube = 0; cube < mesh.cubes; cube++) {
            boolean opaque = true;
            for (int face = 0; face < CreatureMesh.FACES; face++) {
                if ((mesh.present[cube] >> face & 1) == 0) continue;
                int at = (cube * CreatureMesh.FACES + face) * 8;
                float u0 = uv[at], u1 = u0, v0 = uv[at + 1], v1 = v0;
                for (int vertex = 1; vertex < 4; vertex++) {
                    u0 = Math.min(u0, uv[at + vertex * 2]);
                    u1 = Math.max(u1, uv[at + vertex * 2]);
                    v0 = Math.min(v0, uv[at + vertex * 2 + 1]);
                    v1 = Math.max(v1, uv[at + vertex * 2 + 1]);
                }
                // The texels the face's inside can sample with nearest filtering; a face outside the image is unknown.
                // A coordinate on a texel's edge is a float a hair off it; a thousandth of a texel is taken for that.
                int x0 = (int) Math.floor((double) u0 * width + 1e-3), x1 = (int) Math.ceil((double) u1 * width - 1e-3);
                int y0 = (int) Math.floor((double) v0 * height + 1e-3), y1 = (int) Math.ceil((double) v1 * height - 1e-3);
                if (x0 < 0 || y0 < 0 || x1 > width || y1 > height || x1 <= x0 || y1 <= y0) {
                    opaque = false;
                    continue;
                }
                boolean empty = true;
                for (int y = y0; y < y1 && (opaque || empty); y++) {
                    for (int x = x0; x < x1; x++) {
                        int value = alpha.at(x, y);
                        if (value != 255) opaque = false;
                        if (value != 0) empty = false;
                    }
                }
                int bit = cube * CreatureMesh.FACES + face;
                if (empty) emptyFaces[bit >> 6] |= 1L << bit;
            }
            if (opaque) opaqueCubes[cube >> 6] |= 1L << cube;
        }
        ready = true;
    }

    int opaqueCount() {
        int count = 0;
        for (long word : opaqueCubes) count += Long.bitCount(word);
        return count;
    }

    int emptyCount() {
        int count = 0;
        for (long word : emptyFaces) count += Long.bitCount(word);
        return count;
    }
}
