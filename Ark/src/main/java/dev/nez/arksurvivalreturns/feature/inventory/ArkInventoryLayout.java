package dev.nez.arksurvivalreturns.feature.inventory;

import java.util.Map;

/**
 * Where everything sits in the Ark inventory (panel-relative, the top-left pixel inside a slot frame). Its top band holds
 * the worn gear around a tall player and, on the right, the inventory's crafting graph; below, the storage is eleven
 * columns of three rows (a side pocket column on each side of the nine) over the nine-slot hotbar. The panel art is drawn
 * from the same numbers by tools/build_ui_pack.py (ark_inventory_screen()).
 */
public final class ArkInventoryLayout {
    public static final int WIDTH = 388, HEIGHT = 248, TEXTURE_WIDTH = 512, TEXTURE_HEIGHT = 256;
    /** Worn gear: two curio columns and the armour beside the player, two curio columns after it. */
    public static final int GEAR_Y = 45;
    public static final int ARMOR_X = 44;
    public static final int OFFHAND_X = 116, OFFHAND_Y = GEAR_Y + 54;
    public static final int PLAYER_X0 = 62, PLAYER_Y0 = 8, PLAYER_X1 = 114, PLAYER_Y1 = 154, PLAYER_SIZE = 46;
    /** The crafting graph's panel, in GUI pixels (one design unit each). */
    public static final int CRAFT_X = 174, CRAFT_Y = 6, CRAFT_W = 208, CRAFT_H = 150;
    /** Storage: the nine columns of the vanilla inventory, a side pocket column left and right of them. */
    public static final int STORAGE_X = 113, STORAGE_Y = 164, HOTBAR_Y = 222;
    public static final int POCKET_LEFT_X = STORAGE_X - 18, POCKET_RIGHT_X = STORAGE_X + 9 * 18;

    private static final Map<String, int[][]> CURIOS = Map.ofEntries(
            Map.entry("head", new int[][] {{8, GEAR_Y}, {26, GEAR_Y}}),
            Map.entry("necklace", new int[][] {{8, GEAR_Y + 18}}),
            Map.entry("body", new int[][] {{26, GEAR_Y + 18}}),
            Map.entry("belt", new int[][] {{8, GEAR_Y + 36}}),
            Map.entry("legs", new int[][] {{26, GEAR_Y + 36}}),
            Map.entry("feet", new int[][] {{8, GEAR_Y + 54}, {26, GEAR_Y + 54}}),
            Map.entry("back", new int[][] {{116, GEAR_Y}}),
            Map.entry("hands", new int[][] {{134, GEAR_Y}}),
            Map.entry("bracelet", new int[][] {{116, GEAR_Y + 18}, {134, GEAR_Y + 18}}),
            Map.entry("ring", new int[][] {{116, GEAR_Y + 36}, {134, GEAR_Y + 36}}),
            Map.entry("charm", new int[][] {{134, GEAR_Y + 54}}),
            Map.entry("curio", new int[][] {{152, GEAR_Y + 54}}));

    /** The place of a curio slot, or null when the layout has none for it (another mod's slot type). */
    public static int[] curio(String identifier, int index) {
        int[][] places = CURIOS.get(identifier);
        return places != null && index < places.length ? places[index] : null;
    }
    /** The pocket at a storage row, left (0-2) or right (3-5). */
    public static int[] pocket(int index) {
        return new int[] {index < 3 ? POCKET_LEFT_X : POCKET_RIGHT_X, STORAGE_Y + (index % 3) * 18};
    }

    private ArkInventoryLayout() {}
}
