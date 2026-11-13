package dev.nez.arksurvivalreturns.gametest;

import java.util.UUID;
import dev.nez.arksurvivalreturns.Config;
import dev.nez.arksurvivalreturns.feature.behavior.BehaviorAction;
import dev.nez.arksurvivalreturns.feature.behavior.BehaviorState;
import dev.nez.arksurvivalreturns.feature.creature.CreatureEntity;
import dev.nez.arksurvivalreturns.feature.creature.Species;
import dev.nez.arksurvivalreturns.feature.recovery.DownedPolicy;
import dev.nez.arksurvivalreturns.feature.taming.CreatureRideController;
import dev.nez.arksurvivalreturns.feature.taming.MountedCombat;
import dev.nez.arksurvivalreturns.feature.taming.RideKinematics;
import dev.nez.arksurvivalreturns.feature.taming.TamingService;
import dev.nez.arksurvivalreturns.feature.taming.TorporService;
import dev.nez.arksurvivalreturns.feature.tech.TechProgressData;
import dev.nez.arksurvivalreturns.feature.tech.TechTribeProgress;
import dev.nez.arksurvivalreturns.feature.work.WorkGoal;
import dev.nez.arksurvivalreturns.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** Regression checks for the fixes of the phase 2 audit (Audit/02-bugs-and-design.md, A02). */
final class AuditGameTests {
    private static CreatureEntity create(GameTestHelper h, Species species, BlockPos at) {
        var entity = ModContent.CREATURES.get(species).get().create(h.getLevel(), EntitySpawnReason.COMMAND);
        entity.setNoAi(true);
        entity.setPos(Vec3.atBottomCenterOf(h.absolutePos(at)));
        TorporService.of(entity);
        TorporService.tickEntity(entity);
        return entity;
    }

    private static void advance(CreatureEntity creature, int ticks) {
        for (int i = 0; i < ticks; i++) {
            TorporService.tickEntity(creature);
            creature.tick();
        }
    }

    /** BUG-02, BUG-12: a tame leaves its herd and the look of its last wild moment. */
    static void tameHandover(GameTestHelper h) {
        var creature = create(h, Species.VELOCIRAPTOR, new BlockPos(8, 2, 8));
        var pack = UUID.randomUUID();
        creature.joinPack(pack);
        creature.setBehavior(BehaviorState.FLEE);
        creature.setAction(BehaviorAction.BOLT);
        creature.setNightActive(true);
        Player owner = h.makeMockPlayer(GameType.SURVIVAL);
        TamingService.of(creature).setOwner(owner.getUUID());
        creature.onTamed(owner.getUUID());
        h.assertTrue(!creature.packId().equals(pack), "A tame kept its wild pack id");
        h.assertTrue(creature.behavior() == BehaviorState.ROAM && creature.action() == BehaviorAction.IDLE && !creature.nightActive(),
                "A tame kept its wild look: " + creature.behavior() + "/" + creature.action() + " night=" + creature.nightActive());
        creature.discard();
        h.succeed();
    }

    /** BUG-01: sedatives in the hold keep a long knock-out tame under until plain food finishes it; without them it wakes. */
    static void holdSedatives(GameTestHelper h) {
        for (boolean narcotics : new boolean[] {true, false}) {
            var creature = create(h, Species.TYRANNOSAURUS, new BlockPos(8, 2, 8));
            var torpor = TorporService.of(creature);
            Player claimant = h.makeMockPlayer(GameType.SURVIVAL);
            // Real doses of an arrow's potency until it collapses.
            for (int i = 0; i < 40 && !torpor.restricted(); i++) {
                TorporService.sedate(creature, Config.TRANQUILIZER_ARROW_POTENCY.get(), claimant, "test");
                advance(creature, 1);
            }
            advance(creature, 120);
            h.assertTrue(TorporService.feedable(creature), "The Tyrannosaurus did not go under");
            TamingService.claimIfFree(creature, claimant);
            var profile = creature.profile();
            // Plain accepted food: the first accepted item of its meat tag that is not its favourite.
            ItemStack plain = ItemStack.EMPTY;
            for (var item : net.minecraft.core.registries.BuiltInRegistries.ITEM) {
                var stack = new ItemStack(item, 64);
                if (profile.accepts(stack) && !profile.preferred(stack)) { plain = stack; break; }
            }
            h.assertTrue(!plain.isEmpty(), "No plain food found for the test");
            creature.tamingInventory().setItem(0, plain);
            if (narcotics) creature.tamingInventory().setItem(1, new ItemStack(ModContent.NARCOTICS.get(), 32));
            var taming = TamingService.of(creature);
            for (int t = 0; t < 20 * 600 && !taming.tamed() && torpor.restricted(); t++) advance(creature, 1);
            if (narcotics) h.assertTrue(taming.tamed(), "Narcotics in the hold did not carry the tame through: progress=" + taming.progress());
            else h.assertTrue(!taming.tamed(), "A 360 s tame finished on plain food in one sleep");
            creature.discard();
        }
        h.succeed();
    }

    /** BUG-11: a plain right-click with Narcotics does not dose the player. */
    static void narcoticsSelfUse(GameTestHelper h) {
        Player player = h.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModContent.NARCOTICS.get(), 4));
        var result = ModContent.NARCOTICS.get().use(h.getLevel(), player, InteractionHand.MAIN_HAND);
        h.assertTrue(result == InteractionResult.PASS && TorporService.of(player).torpor() == 0,
                "A plain right-click with Narcotics dosed the player");
        h.succeed();
    }

    /** BUG-04: dismounting puts the rider on the ground beside the body, for every land species. */
    static void dismountGround(GameTestHelper h) {
        var world = h.getLevel();
        for (var species : Species.values()) {
            if (species.flyer() || species.aquatic()) continue;
            var creature = create(h, species, new BlockPos(16, 2, 16));
            world.addFreshEntity(creature);
            Player rider = h.makeMockPlayer(GameType.SURVIVAL);
            TamingService.of(creature).setOwner(rider.getUUID());
            creature.setItemSlot(EquipmentSlot.SADDLE, new ItemStack(ModContent.SADDLES.get(species).get()));
            creature.setOnGround(true);
            rider.setPos(creature.position());
            rider.startRiding(creature, true, false);
            rider.stopRiding();
            double ground = creature.getBoundingBox().minY;
            h.assertTrue(Math.abs(rider.getY() - ground) <= 2.0,
                    "Rider left " + species.id + " at y=" + rider.getY() + ", ground " + ground + " (box top " + creature.getBoundingBox().maxY + ")");
            creature.discard();
        }
        h.succeed();
    }

    /** BUG-03: each realm's push settles on the ridden target pace. */
    static void rideKinematics(GameTestHelper h) {
        for (var medium : RideKinematics.Medium.values()) {
            double target = 12.0;
            double push = RideKinematics.push(target, medium);
            double simulated = RideKinematics.simulate(push, medium, 400);
            h.assertTrue(Math.abs(simulated - target) / target < 0.01, medium + " settles at " + simulated + " for " + target);
        }
        var rex = create(h, Species.TYRANNOSAURUS, new BlockPos(8, 2, 8));
        double own = dev.nez.arksurvivalreturns.feature.creature.MovementTuning.blocksPerSecond(
                rex.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED));
        double ridden = CreatureRideController.targetBlocksPerSecond(rex, rex.rideProfile().mode());
        h.assertTrue(ridden > own && ridden < own * 1.5, "Ridden pace " + ridden + " against own top speed " + own);
        rex.discard();
        h.succeed();
    }

    /** BUG-06: an owner's blow does not hurt their own tame while friendly fire is off. */
    static void tribeAllies(GameTestHelper h) {
        var world = h.getLevel();
        var creature = create(h, Species.PARASAUR, new BlockPos(8, 2, 8));
        creature.setNoAi(false);
        world.addFreshEntity(creature);
        Player owner = h.makeMockPlayer(GameType.SURVIVAL);
        TamingService.of(creature).setOwner(owner.getUUID());
        float before = creature.getHealth();
        creature.hurtServer(world, world.damageSources().playerAttack(owner), 5f);
        h.assertTrue(creature.getHealth() == before, "An owner's blow hurt its own tame");
        h.assertTrue(creature.isAlliedTo(owner), "A tame is not allied to its owner");
        creature.discard();
        h.succeed();
    }

    /** BUG-05: a rider's strike makes the mount strike the creature in front of it. */
    static void mountedStrike(GameTestHelper h) {
        var world = h.getLevel();
        var mount = create(h, Species.TYRANNOSAURUS, new BlockPos(16, 2, 16));
        mount.setNoAi(false);
        mount.setYRot(0); mount.yBodyRot = 0;
        world.addFreshEntity(mount);
        Player rider = h.makeMockPlayer(GameType.SURVIVAL);
        rider.setPos(mount.position());
        TamingService.of(mount).setOwner(rider.getUUID());
        mount.setItemSlot(EquipmentSlot.SADDLE, new ItemStack(ModContent.SADDLES.get(Species.TYRANNOSAURUS).get()));
        rider.startRiding(mount, true, false);
        var prey = create(h, Species.PARASAUR, new BlockPos(16, 2, 16));
        prey.setPos(mount.position().add(0, 0, mount.getBbWidth() / 2 + 1.0));
        world.addFreshEntity(prey);
        h.assertTrue(MountedCombat.target(mount, rider) == prey, "The mount found no target in front of it");
        h.assertTrue(rider.getControlledVehicle() == mount && mount.isRidden(), "The rider does not drive the mount: vehicle="
                + rider.getVehicle() + " tamed=" + mount.isTamed() + " saddled=" + mount.isSaddled());
        h.assertTrue(mount.canStrike(), "The mount cannot strike");
        h.assertTrue(mount.hasLineOfSight(prey), "The mount does not see the prey in front of it");
        h.assertTrue(MountedCombat.strike(rider), "The rider's strike did not start the mount's");
        prey.discard(); rider.stopRiding(); mount.discard();
        h.succeed();
    }

    /** BUG-10: a wide worker reaches a block beside its flank. */
    static void workReach(GameTestHelper h) {
        var anky = create(h, Species.ANKYLOSAURUS, new BlockPos(16, 2, 16));
        var beside = BlockPos.containing(anky.getBoundingBox().maxX + 0.5, anky.getY(), anky.getZ());
        h.getLevel().setBlockAndUpdate(beside, Blocks.STONE.defaultBlockState());
        h.assertTrue(WorkGoal.inReach(anky, beside), "A block at the flank is out of reach");
        h.assertFalse(WorkGoal.inReach(anky, beside.east(4)), "A block four blocks off the flank is in reach");
        h.getLevel().setBlockAndUpdate(beside, Blocks.AIR.defaultBlockState());
        anky.discard();
        h.succeed();
    }

    /** BUG-08, BUG-14: a hit costs a tenth of the window; a player's solo tech progress joins their party's. */
    static void downedAndProgress(GameTestHelper h) {
        h.assertTrue(DownedPolicy.bleedOut(1200, 1200, 1f, 1.0, 0.1) == 1080, "A hit on a downed player must cost a tenth of the window");
        h.assertTrue(DownedPolicy.bleedOut(100, 1200, 1f, 1.0, 0.1) == 0, "The window must bottom out at zero");
        var data = TechProgressData.get(h.getLevel());
        UUID solo = UUID.randomUUID(), party = UUID.randomUUID();
        var progress = new TechTribeProgress();
        progress.mark("test:mark");
        progress.complete("test_node");
        data.store(solo, progress);
        h.assertTrue(data.merge(solo, party), "Solo progress was not merged");
        h.assertTrue(data.progress(party).marked("test:mark") && data.progress(party).completed("test_node"), "Merged progress is missing");
        data.reset(solo); data.reset(party);
        h.succeed();
    }

    /** A01 OPT-12: a resting body ticks its physics on a slower beat but still falls when its ground goes. */
    static void restingBody(GameTestHelper h) {
        var world = h.getLevel();
        BlockPos floor = new BlockPos(24, 4, 24);
        for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) h.setBlock(floor.offset(x, 0, z), Blocks.STONE);
        var creature = create(h, Species.PARASAUR, floor.above());
        creature.setNoAi(false);
        world.addFreshEntity(creature);
        TorporService.sedate(creature, TorporService.of(creature).maximum() * 4, null, "test");
        h.runAfterDelay(60, () -> {
            h.assertTrue(TorporService.restricted(creature) && creature.onGround(), "The test animal did not lie on the floor");
            double y = creature.getY();
            for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) h.setBlock(floor.offset(x, 0, z), Blocks.AIR);
            h.runAfterDelay(20, () -> {
                h.assertTrue(creature.getY() < y - 0.5, "A resting body did not fall when its ground was removed: " + y + " -> " + creature.getY());
                creature.discard();
                h.succeed();
            });
        });
    }

    private AuditGameTests() {}
}
