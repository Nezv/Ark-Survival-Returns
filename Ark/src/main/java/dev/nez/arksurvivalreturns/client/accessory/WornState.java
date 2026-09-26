package dev.nez.arksurvivalreturns.client.accessory;

import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * One accessory draw: the wearer's pose plus what this draw needs (slot side, arm model, wing spread, which
 * armour pieces it must fit over). Rendering is deferred, so each submit carries its own state.
 */
public final class WornState {
    /** The wearer's render state in third person; null for a first-person arm. */
    final @Nullable HumanoidRenderState humanoid;
    final @Nullable HumanoidArm firstPerson;
    /** right or left for sided slots (rings, bracelets), null to draw every side. */
    final @Nullable String side;
    final boolean slim;
    final float spread;
    final boolean helmet, chest, legs, boots;
    final float capeFlap, capeLean, capeLean2;

    private WornState(@Nullable HumanoidRenderState humanoid, @Nullable HumanoidArm firstPerson, @Nullable String side,
            boolean slim, float spread) {
        this.humanoid = humanoid;
        this.firstPerson = firstPerson;
        this.side = side;
        this.slim = slim;
        this.spread = spread;
        this.helmet = humanoid != null && covers(humanoid.headEquipment);
        this.chest = humanoid != null && covers(humanoid.chestEquipment);
        this.legs = humanoid != null && covers(humanoid.legsEquipment);
        this.boots = humanoid != null && covers(humanoid.feetEquipment);
        var avatar = humanoid instanceof AvatarRenderState a ? a : null;
        this.capeFlap = avatar == null ? 0 : avatar.capeFlap;
        this.capeLean = avatar == null ? 0 : avatar.capeLean;
        this.capeLean2 = avatar == null ? 0 : avatar.capeLean2;
    }

    static WornState worn(HumanoidRenderState humanoid, @Nullable String side, boolean slim, float spread) {
        return new WornState(humanoid, null, side, slim, spread);
    }

    static WornState firstPerson(HumanoidArm arm, @Nullable String side, boolean slim) {
        return new WornState(null, arm, side, slim, 0);
    }

    boolean fitted(String fit) {
        return switch (fit) {
            case "helmet" -> helmet;
            case "chest" -> chest;
            case "legs" -> legs;
            case "boots" -> boots;
            default -> false;
        };
    }

    /** Armour that draws over the body: anything equippable into the slot (a carved pumpkin counts too). */
    private static boolean covers(ItemStack stack) {
        return !stack.isEmpty() && stack.has(DataComponents.EQUIPPABLE);
    }
}
