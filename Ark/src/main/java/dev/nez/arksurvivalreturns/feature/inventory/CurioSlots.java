package dev.nez.arksurvivalreturns.feature.inventory;

import java.util.TreeMap;
import net.minecraft.world.entity.player.Player;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.common.inventory.CurioSlot;

/**
 * The Curios slots of the Ark inventory, each at its place in {@link ArkInventoryLayout} (the Ark slot set: two heads,
 * necklace, body, belt, legs, two feet, back, hands, two bracelets, two rings, charm and curio). A slot type the layout
 * has no place for (another mod's) is left out. Sorted by type so the server and the client add them in one order.
 */
final class CurioSlots {
    static void add(ArkInventoryMenu menu, Player player) {
        CuriosApi.getCuriosInventory(player).ifPresent(curios -> {
            for (var entry : new TreeMap<>(curios.getCurios()).entrySet()) {
                var type = entry.getValue();
                if (!type.isVisible()) continue;
                var stacks = type.getStacks();
                for (int i = 0; i < stacks.getSlots(); i++) {
                    int[] at = ArkInventoryLayout.curio(entry.getKey(), i);
                    if (at != null) menu.addCurio(new CurioSlot(player, stacks, i, entry.getKey(), at[0], at[1],
                            type.getRenders(), type.getActiveStates(), type.canToggleRendering(), false));
                }
            }
        });
    }
    private CurioSlots() {}
}
