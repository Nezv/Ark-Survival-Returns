package dev.nez.arksurvivalreturns.feature.playthrough;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import com.google.gson.JsonArray;
import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import dev.nez.arksurvivalreturns.feature.creature.Species;
import dev.nez.arksurvivalreturns.feature.drop.LootCrateBlockEntity;
import dev.nez.arksurvivalreturns.feature.drop.SupplyTier;
import dev.nez.arksurvivalreturns.feature.station.StationBlock;
import dev.nez.arksurvivalreturns.feature.station.WorkstationCatalog;
import dev.nez.arksurvivalreturns.feature.tech.TechProgressData;
import dev.nez.arksurvivalreturns.feature.tech.TechService;
import dev.nez.arksurvivalreturns.feature.tech.TechTree;
import dev.nez.arksurvivalreturns.feature.testground.SpecimenPostBlock;
import dev.nez.arksurvivalreturns.feature.testground.SpecimenPostBlockEntity;
import dev.nez.arksurvivalreturns.feature.testground.TestGroundContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The sweeps after the stages, so that at the end of a run every recipe, block, screen, armour set, weapon and supply
 * drop tier has been through the game once: what each gave is in the events file, nothing is judged here.
 */
public final class Sweeps {
    static final String NS = ArkSurvivalReturns.MOD_ID;
    /** The wall of blocks, from the run's origin: west of the fight lane. */
    static final int WALL_X = -70, WALL_Z = 120, WALL_ROW = 16;

    /** Steps planned when the run reaches them, from the world as it is then; ticked one after another. */
    public static Step sequence(String name, Function<PlayRun, List<Step>> plan) {
        return new Step() {
            List<Step> steps;
            int at, ticks;
            @Override public String name() { return name; }
            @Override public int timeout() { return Integer.MAX_VALUE; }
            @Override public boolean tick(PlayRun run, int outer) {
                if (steps == null) steps = plan.apply(run);
                if (at >= steps.size()) return true;
                var step = steps.get(at);
                boolean done;
                try { done = step.tick(run, ticks++); }
                catch (RuntimeException e) {
                    ArkSurvivalReturns.LOGGER.error("Playthrough: step {} failed", step.name(), e);
                    var failed = run.event("step_error");
                    failed.addProperty("inner", step.name());
                    failed.addProperty("error", String.valueOf(e));
                    run.write(failed);
                    done = true;
                }
                if (done || ticks > step.timeout()) { at++; ticks = 0; }
                return false;
            }
        };
    }

    // ------------------------------------------------------------------ recipes

    /** Every workstation craft once, its cost given: does each recipe craft at all, at the level it asks. */
    public static Step recipes() {
        return sequence("recipe sweep", run -> {
            var steps = new ArrayList<Step>();
            steps.add(Verbs.command("arklevel set " + run.player().getScoreboardName() + " 100"));
            int n = 0;
            for (var station : WorkstationCatalog.all().values())
                for (var craft : station.crafts()) {
                    if (craft.variant().apply()) continue;
                    if (n++ % 8 == 0) steps.add(Verbs.command("clear " + run.player().getScoreboardName()));
                    for (var cost : craft.variant().cost().entrySet()) {
                        String item = Gathering.resolve(run.player(), cost.getKey());
                        if (item != null) steps.add(Gathering.give(item + " " + cost.getValue(), "recipe_sweep"));
                    }
                    steps.add(Verbs.craft(craft.variant().item(), 1));
                }
            steps.add(Verbs.command("clear " + run.player().getScoreboardName()));
            return steps;
        });
    }

    // ------------------------------------------------------------------ blocks

    static List<Block> arkBlocks() {
        var out = new ArrayList<Block>();
        for (var block : BuiltInRegistries.BLOCK)
            if (BuiltInRegistries.BLOCK.getKey(block).getNamespace().equals(NS) && block.asItem() != net.minecraft.world.item.Items.AIR) out.add(block);
        return out;
    }

    /** Every Ark block put on a wall, pictured from above and from the front, then broken with the right bronze tool. */
    public static Step blocks() {
        return sequence("block sweep", run -> {
            var steps = new ArrayList<Step>();
            var blocks = arkBlocks();
            for (String tool : List.of("bronze_pickaxe", "bronze_axe", "bronze_shovel"))
                steps.add(Gathering.give(NS + ":" + tool + " 1", "block_sweep"));
            steps.add(Step.of("place the wall", run2 -> {
                for (int i = 0; i < blocks.size(); i++) {
                    var pos = wall(run2, i);
                    run2.level().setBlockAndUpdate(pos.below(), Blocks.GRASS_BLOCK.defaultBlockState());
                    run2.level().setBlockAndUpdate(pos, blocks.get(i).defaultBlockState());
                }
            }));
            steps.add(Verbs.waitTicks(20));
            steps.add(Step.of("check the wall", run2 -> {
                var fell = new JsonArray();
                for (int i = 0; i < blocks.size(); i++) if (!run2.level().getBlockState(wall(run2, i)).is(blocks.get(i))) fell.add(Verbs.id(blocks.get(i)));
                var event = run2.event("block_wall");
                event.addProperty("blocks", blocks.size());
                event.add("did_not_stay", fell);
                run2.write(event);
            }));
            int rows = (blocks.size() + WALL_ROW - 1) / WALL_ROW;
            steps.add(Verbs.teleport(WALL_X + WALL_ROW * 1.5, 30, WALL_Z + rows * 1.5, 180, 90));
            steps.add(Verbs.shot("blocks_above", 60));
            steps.add(Verbs.teleport(WALL_X + WALL_ROW * 1.5, 4, WALL_Z - 10, 0, 15));
            steps.add(Verbs.shot("blocks_front", 60));
            for (int i = 0; i < blocks.size(); i++) {
                int index = i;
                steps.add(new Step() {
                    Map<String, Integer> before;
                    @Override public String name() { return "break " + Verbs.id(blocks.get(index)); }
                    @Override public boolean tick(PlayRun run2, int ticks) {
                        var pos = wall(run2, index);
                        var player = run2.player();
                        if (ticks == 0) {
                            Verbs.tp(run2, pos.getX() - run2.origin.getX(), 0, pos.getZ() - run2.origin.getZ() - 2, 0, 30);
                            before = Verbs.counts(player);
                            var state = run2.level().getBlockState(pos);
                            Gathering.bestTool(player, state);
                            var event = run2.event("block");
                            event.addProperty("block", Verbs.id(blocks.get(index)));
                            event.addProperty("present", state.is(blocks.get(index)));
                            event.addProperty("broken", !state.isAir() && player.gameMode.destroyBlock(pos));
                            event.addProperty("tool", Verbs.id(player.getMainHandItem().getItem()));
                            run2.write(event);
                            return false;
                        }
                        Gathering.pullDrops(run2, pos, 2);
                        if (ticks < 8) return false;
                        var event = run2.event("block_drops");
                        event.addProperty("block", Verbs.id(blocks.get(index)));
                        event.add("gained", Verbs.difference(before, Verbs.counts(player)));
                        run2.write(event);
                        return true;
                    }
                });
            }
            return steps;
        });
    }

    static BlockPos wall(PlayRun run, int i) { return run.at(WALL_X + (i % WALL_ROW) * 3, 1, WALL_Z + (i / WALL_ROW) * 3); }

    // ------------------------------------------------------------------ screens

    /** Every Ark block with a screen opened by a click and pictured, then the screens only the client opens. */
    public static Step screens() {
        return sequence("screen sweep", run -> {
            var steps = new ArrayList<Step>();
            steps.add(Verbs.command("gamemode creative " + run.player().getScoreboardName()));
            for (var block : arkBlocks()) {
                if (!opens(run, block)) continue;
                String id = BuiltInRegistries.BLOCK.getKey(block).getPath();
                steps.add(Step.of("open " + id, run2 -> {
                    var player = run2.player();
                    player.closeContainer();
                    var pos = run2.at(-20, 1, 100);
                    Verbs.tp(run2, -20, 0, 102.5, 180, 25);
                    run2.level().setBlockAndUpdate(pos, block.defaultBlockState());
                    if (run2.level().getBlockEntity(pos) instanceof SpecimenPostBlockEntity post) post.configure(Species.PARASAUR, 0, 1, SpecimenPostBlockEntity.Mode.FROZEN, 50, false);
                    run2.level().getBlockState(pos).useWithoutItem(run2.level(), player, new BlockHitResult(Vec3.atCenterOf(pos), Direction.SOUTH, pos, false));
                    var event = run2.event("screen_open");
                    event.addProperty("block", id);
                    event.addProperty("menu", player.containerMenu == player.inventoryMenu ? "" : player.containerMenu.getClass().getSimpleName());
                    run2.write(event);
                }));
                steps.add(Verbs.shot("screen_" + id, 15));
                steps.add(Step.of("close " + id, run2 -> { run2.player().closeContainer(); run2.level().removeBlock(run2.at(-20, 1, 100), false); }));
                steps.add(Verbs.client("screen:close", 2));
            }
            for (String screen : List.of("inventory", "tech")) {
                steps.add(Verbs.client("screen:" + screen, 5));
                steps.add(Verbs.shot("screen_" + screen, 15));
                steps.add(Verbs.client("screen:close", 2));
            }
            steps.add(Verbs.command("gamemode survival " + run.player().getScoreboardName()));
            return steps;
        });
    }

    private static boolean opens(PlayRun run, Block block) {
        if (block instanceof StationBlock || block instanceof SpecimenPostBlock) return true;
        if (!(block instanceof EntityBlock entity)) return false;
        try { return entity.newBlockEntity(BlockPos.ZERO, block.defaultBlockState()) instanceof MenuProvider; }
        catch (RuntimeException e) { return false; }
    }

    // ------------------------------------------------------------------ armour and weapons

    /** Armour sets by name (keratin, bronze, ... and hide), each piece in its slot. */
    static Map<String, List<String>> armourSets() {
        var sets = new LinkedHashMap<String, List<String>>();
        for (var item : BuiltInRegistries.ITEM) {
            var key = BuiltInRegistries.ITEM.getKey(item);
            boolean hide = key.getNamespace().equals("minecraft") && key.getPath().startsWith("leather_") && !key.getPath().contains("horse");
            if (!key.getNamespace().equals(NS) && !hide) continue;
            var equippable = new ItemStack(item).get(DataComponents.EQUIPPABLE);
            if (equippable == null) continue;
            var slot = equippable.slot();
            if (slot != EquipmentSlot.HEAD && slot != EquipmentSlot.CHEST && slot != EquipmentSlot.LEGS && slot != EquipmentSlot.FEET) continue;
            String path = key.getPath();
            int cut = path.lastIndexOf('_');
            sets.computeIfAbsent(cut > 0 ? path.substring(0, cut) : path, k -> new ArrayList<>()).add(key.toString());
        }
        return sets;
    }

    /** Each set worn and pictured from the front in third person; its armour points written down. */
    public static Step armour() {
        return sequence("armour sweep", run -> {
            var steps = new ArrayList<Step>();
            steps.add(Verbs.teleport(-40, 0, 100, 180, 0));
            steps.add(Verbs.client("camera:third", 5));
            for (var set : armourSets().entrySet()) {
                steps.add(Verbs.unequip());
                steps.add(Verbs.equip(set.getValue().toArray(String[]::new)));
                steps.add(Verbs.shot("armour_" + set.getKey(), 30));
            }
            steps.add(Verbs.unequip());
            steps.add(Verbs.client("camera:first", 5));
            return steps;
        });
    }

    /** Ark items that hit harder than a fist. */
    static List<String> weapons(PlayRun run) {
        var out = new ArrayList<String>();
        var player = run.player();
        var held = player.getMainHandItem().copy();
        for (var item : BuiltInRegistries.ITEM) {
            var key = BuiltInRegistries.ITEM.getKey(item);
            if (!key.getNamespace().equals(NS)) continue;
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(item));
            if (player.getAttributeValue(Attributes.ATTACK_DAMAGE) > 1.5) out.add(key.toString());
        }
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, held);
        return out;
    }

    /** Every weapon against the same frozen Triceratops: one row of time to kill per weapon. */
    public static Step weapons() {
        return sequence("weapon sweep", run -> {
            var steps = new ArrayList<Step>();
            steps.add(Verbs.unequip());
            for (String weapon : weapons(run)) {
                steps.add(Verbs.wield(weapon));
                steps.add(CombatSteps.killStreak("weapon sweep " + weapon.substring(weapon.indexOf(':') + 1), Species.TRICERATOPS, 1));
            }
            return steps;
        });
    }

    // ------------------------------------------------------------------ supply drops

    /** One real drop of each tier called down on the player: its fall, its crate opened and pictured, its contents. */
    public static Step drops() {
        return sequence("drop sweep", run -> {
            var steps = new ArrayList<Step>();
            for (var tier : SupplyTier.values()) {
                steps.add(Verbs.teleport(0, 0, 200 + tier.ordinal() * 40, 180, 0));
                steps.add(Verbs.command("execute as " + run.player().getScoreboardName() + " at @s run arkdrop " + tier.id));
                steps.add(new Step() {
                    long called;
                    @Override public String name() { return "drop " + tier.id; }
                    @Override public int timeout() { return 20 * 60 * 4; }
                    @Override public boolean tick(PlayRun run2, int ticks) {
                        if (ticks == 0) { called = run2.level().getGameTime(); return false; }
                        if (ticks % 20 != 0) return false;
                        var crate = crate(run2);
                        if (crate == null) return false;
                        var pos = crate.getBlockPos();
                        var player = run2.player();
                        Verbs.tp(run2, pos.getX() - run2.origin.getX(), pos.getY() - run2.origin.getY() - 1, pos.getZ() - run2.origin.getZ() + 2.5, 180, 30);
                        run2.level().getBlockState(pos).useWithoutItem(run2.level(), player, new BlockHitResult(Vec3.atCenterOf(pos), Direction.SOUTH, pos, false));
                        var items = new LinkedHashMap<String, Integer>();
                        for (int i = 0; i < crate.getContainerSize(); i++)
                            if (!crate.getItem(i).isEmpty()) items.merge(Verbs.id(crate.getItem(i).getItem()), crate.getItem(i).getCount(), Integer::sum);
                        var event = run2.event("drop");
                        event.addProperty("tier", tier.id);
                        event.addProperty("landed_after", run2.level().getGameTime() - called);
                        event.addProperty("crate_tier", crate.tier().id);
                        event.addProperty("menu", player.containerMenu.getClass().getSimpleName());
                        event.add("items", Verbs.json(items));
                        run2.write(event);
                        return true;
                    }
                });
                steps.add(Verbs.shot("drop_" + tier.id, 20));
                steps.add(Step.of("clear drop " + tier.id, run2 -> {
                    run2.player().closeContainer();
                    var crate = crate(run2);
                    if (crate != null) { crate.clearContent(); run2.level().removeBlock(crate.getBlockPos(), false); }
                }));
            }
            return steps;
        });
    }

    static LootCrateBlockEntity crate(PlayRun run) {
        var centre = run.player().blockPosition();
        LootCrateBlockEntity best = null;
        for (int cx = (centre.getX() >> 4) - 6; cx <= (centre.getX() >> 4) + 6; cx++)
            for (int cz = (centre.getZ() >> 4) - 6; cz <= (centre.getZ() >> 4) + 6; cz++) {
                var chunk = run.level().getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) continue;
                for (var entity : chunk.getBlockEntities().values())
                    if (entity instanceof LootCrateBlockEntity crate && (best == null || crate.getBlockPos().distSqr(centre) < best.getBlockPos().distSqr(centre))) best = crate;
            }
        return best;
    }

    // ------------------------------------------------------------------ taming and the tech tree

    /** A creature knocked out and tamed through a Specimen Post's own services (torpor, then taming). */
    public static Step tame(Species species) {
        return new Step() {
            BlockPos pos;
            @Override public String name() { return "tame " + species.id; }
            @Override public int timeout() { return 20 * 90; }
            @Override public boolean tick(PlayRun run, int ticks) {
                var player = run.player();
                if (ticks == 0) {
                    pos = run.at(-40, 1, 140);
                    run.level().setBlockAndUpdate(pos, TestGroundContent.SPECIMEN_POST.get().defaultBlockState());
                    if (!(run.level().getBlockEntity(pos) instanceof SpecimenPostBlockEntity post)) return true;
                    post.configure(species, 0, 1, SpecimenPostBlockEntity.Mode.ROOTED, 50, false);
                    post.act(SpecimenPostBlockEntity.Action.SPAWN, player);
                    Verbs.tp(run, -40, 0, 146, 180, 15);
                    return false;
                }
                if (!(run.level().getBlockEntity(pos) instanceof SpecimenPostBlockEntity post)) return true;
                if (ticks == 20) post.act(SpecimenPostBlockEntity.Action.KNOCK_OUT, player);
                if (ticks == 200) post.act(SpecimenPostBlockEntity.Action.TAME, player);
                if (ticks < 400) return false;
                var event = run.event("tame");
                event.addProperty("species", species.id);
                event.addProperty("status", post.status());
                event.add("reports", Verbs.json(post.reports()));
                run.write(event);
                post.act(SpecimenPostBlockEntity.Action.CLEAR, player);
                run.level().removeBlock(pos, false);
                return true;
            }
        };
    }

    /** Which tech nodes the player's tribe has completed so far, and which not. */
    public static Step tech(String label) {
        return Step.of("tech " + label, run -> {
            var tree = TechTree.current();
            var event = run.event("tech");
            event.addProperty("label", label);
            if (tree == null) { event.addProperty("tree", false); run.write(event); return; }
            var progress = TechProgressData.get(run.level()).progress(TechService.tribeOf(run.player()));
            var done = new JsonArray();
            var open = new JsonArray();
            for (var node : tree.nodes()) (progress.completed(node.id()) ? done : open).add(node.age() + "/" + node.id());
            event.add("completed", done);
            event.add("open", open);
            run.write(event);
        });
    }

    private Sweeps() {}
}
