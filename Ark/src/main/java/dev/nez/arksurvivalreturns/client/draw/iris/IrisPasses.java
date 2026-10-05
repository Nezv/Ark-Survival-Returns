package dev.nez.arksurvivalreturns.client.draw.iris;

import net.irisshaders.iris.api.v0.IrisApi;

/** What Iris is doing right now, through its public API. Loaded only when Iris is. */
public final class IrisPasses {
    /** The pass that draws the world from the sun for the shadow map: there is no eye of the player to face away from. */
    public static boolean shadow() { return IrisApi.getInstance().isRenderingShadowPass(); }

    public static boolean shaderPack() { return IrisApi.getInstance().isShaderPackInUse(); }

    private IrisPasses() {}
}
