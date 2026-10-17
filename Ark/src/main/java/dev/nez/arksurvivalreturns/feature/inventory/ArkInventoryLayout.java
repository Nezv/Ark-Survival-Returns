package dev.nez.arksurvivalreturns.feature.inventory;

import java.util.Map;

/**
 * Where everything sits in the Ark inventory (panel-relative, the top-left pixel inside a slot frame). The panel is a
 * T: a wide top band with the gear in seven rows beside the player's arched niche (two curio columns on the left, the
 * armour column on the right with the charm, offhand and universal curio between its pieces) and the inventory's
 * crafting graph, over a narrower storage block, eleven columns of three rows (a side pocket column on each side of the
 * nine) over the hotbar. tools/build_ark_inventory.py carves the texture from the same numbers.
 */
public final class ArkInventoryLayout {
    public static final int WIDTH = 336, HEIGHT = 228, BAND_HEIGHT = 142, SECTION_X0 = 62, SECTION_X1 = 274;
    /** The texture holds the carved frame round the panel: the panel's top-left corner is at (FRAME_X, FRAME_TOP). */
    public static final int FRAME_X = 14, FRAME_TOP = 15, TEXTURE_WIDTH = 364, TEXTURE_HEIGHT = 255;
    public static final int ROW = 8, ARMOR_X = 98;
    /** Armour rows: head, chest, legs, feet; the offhand at the hands' row. */
    public static final int[] ARMOR_ROWS = {0, 2, 5, 6};
    public static final int OFFHAND_X = ARMOR_X, OFFHAND_Y = row(3);
    public static final int PLAYER_X0 = 44, PLAYER_Y0 = 7, PLAYER_X1 = 96, PLAYER_Y1 = 135, PLAYER_SIZE = 52;
    /** The crafting graph's panel, in GUI pixels (one design unit each). */
    public static final int CRAFT_X = 121, CRAFT_Y = 7, CRAFT_W = 208, CRAFT_H = 128;
    /** Storage: the nine columns of the vanilla inventory, a side pocket column left and right of them. */
    public static final int STORAGE_X = 88, STORAGE_Y = 147, HOTBAR_Y = 205;
    public static final int POCKET_LEFT_X = STORAGE_X - 18, POCKET_RIGHT_X = STORAGE_X + 9 * 18;

    private static final Map<String, int[][]> CURIOS = Map.ofEntries(
            Map.entry("head", new int[][] {{8, row(0)}, {26, row(0)}}),
            Map.entry("necklace", new int[][] {{8, row(1)}}),
            Map.entry("back", new int[][] {{26, row(1)}}),
            Map.entry("body", new int[][] {{8, row(2)}}),
            Map.entry("hands", new int[][] {{26, row(2)}}),
            Map.entry("bracelet", new int[][] {{8, row(3)}, {26, row(3)}}),
            Map.entry("belt", new int[][] {{8, row(4)}}),
            Map.entry("ring", new int[][] {{26, row(4)}, {26, row(5)}}),
            Map.entry("legs", new int[][] {{8, row(5)}}),
            Map.entry("feet", new int[][] {{8, row(6)}, {26, row(6)}}),
            Map.entry("charm", new int[][] {{ARMOR_X, row(1)}}),
            Map.entry("curio", new int[][] {{ARMOR_X, row(4)}}));

    public static int row(int k) { return ROW + 18 * k; }
    /** The place of a curio slot, or null when the layout has none for it (another mod's slot type). */
    public static int[] curio(String identifier, int index) {
        int[][] places = CURIOS.get(identifier);
        return places != null && index < places.length ? places[index] : null;
    }
    /** The pocket at a storage row, left (0-2) or right (3-5). */
    public static int[] pocket(int index) {
        return new int[] {index < 3 ? POCKET_LEFT_X : POCKET_RIGHT_X, STORAGE_Y + (index % 3) * 18};
    }
    /** Inside the T: the band, or the storage block under it (the bottom corners are cut away). */
    public static boolean inside(double x, double y) {
        return x >= 0 && x < WIDTH && y >= 0 && y < BAND_HEIGHT || x >= SECTION_X0 && x < SECTION_X1 && y >= BAND_HEIGHT && y < HEIGHT;
    }

    private ArkInventoryLayout() {}
}
