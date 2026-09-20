package dev.nez.arksurvivalreturns.gametest;

import dev.nez.arksurvivalreturns.feature.companion.CompanionGoal;
import dev.nez.arksurvivalreturns.feature.companion.CompanionOrder;
import dev.nez.arksurvivalreturns.feature.companion.CompanionService;
import dev.nez.arksurvivalreturns.feature.creature.CreatureEntity;
import dev.nez.arksurvivalreturns.feature.creature.Species;
import dev.nez.arksurvivalreturns.feature.taming.TamingService;
import dev.nez.arksurvivalreturns.feature.taming.TorporService;
import dev.nez.arksurvivalreturns.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * Headless verification of the companion orders: cycling, anchors, save/load, owner resolution,
 * threat filtering and the realm steering hooks every class shares.
 */
final class CompanionGameTests {
    private static CreatureEntity tamed(GameTestHelper h, Species species, Player owner) {
        var entity = ModContent.CREATURES.get(species).get().create(h.getLevel(), EntitySpawnReason.COMMAND);
        entity.setNoAi(true);
        entity.setPos(Vec3.atCenterOf(h.absolutePos(new BlockPos(8, 3, 8))));
        TamingService.of(entity).setOwner(owner.getUUID());
        TorporService.of(entity);
        return entity;
    }

    static void run(GameTestHelper h) {
        Player owner = h.makeMockPlayer(GameType.SURVIVAL);
        owner.setPos(Vec3.atCenterOf(h.absolutePos(new BlockPos(8, 3, 8))));
        h.getLevel().addFreshEntity(owner);

        // --- cycling, anchors and persistence ---
        var creature = tamed(h, Species.VELOCIRAPTOR, owner);
        h.assertTrue(CompanionService.of(creature).order() == CompanionOrder.FOLLOW, "Fresh companion must follow");
        CompanionService.setOrder(creature, CompanionOrder.STAY);
        h.assertTrue(CompanionService.of(creature).order() == CompanionOrder.STAY, "STAY was not stored");
        h.assertTrue(creature.blockPosition().equals(CompanionService.of(creature).anchor()), "STAY did not anchor");
        CompanionService.orderCommand(creature, owner);
        h.assertTrue(CompanionService.of(creature).order() == CompanionOrder.WANDER, "Whistle did not cycle to WANDER");
        CompanionService.orderCommand(creature, owner);
        h.assertTrue(CompanionService.of(creature).order() == CompanionOrder.FOLLOW
                && CompanionService.of(creature).anchor() == null, "Whistle did not cycle back to FOLLOW");
        CompanionService.setOrder(creature, CompanionOrder.WANDER);
        BlockPos anchor = CompanionService.of(creature).anchor();
        h.assertTrue(anchor != null, "WANDER did not anchor");
        var output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, h.getLevel().registryAccess());
        creature.saveWithoutId(output);
        var restored = tamed(h, Species.VELOCIRAPTOR, owner);
        restored.load(TagValueInput.create(ProblemReporter.DISCARDING, h.getLevel().registryAccess(),
                output.buildResult()));
        h.assertTrue(CompanionService.of(restored).order() == CompanionOrder.WANDER, "Order was lost on load");
        h.assertTrue(anchor.equals(CompanionService.of(restored).anchor()), "Anchor was lost on load");
        restored.discard();
        creature.discard();

        // --- orders drive real movement intent ---
        var follower = tamed(h, Species.VELOCIRAPTOR, owner);
        h.assertTrue(CompanionService.owner(follower) == owner, "Owner was not resolved from the loaded entity");
        var goal = new CompanionGoal(follower);
        h.assertTrue(goal.canUse(), "Tamed companion goal refused to run");
        owner.setPos(follower.position().add(12, 0, 0));
        goal.tick();
        h.assertTrue(follower.isCompanionTraveling(), "FOLLOW did not start traveling to the owner");
        owner.setPos(follower.position().add(3, 0, 0));
        goal.tick();
        h.assertFalse(follower.isCompanionTraveling(), "FOLLOW kept traveling inside the stop distance");
        CompanionService.setOrder(follower, CompanionOrder.STAY);
        follower.setPos(follower.position().add(8, 0, 0));
        goal.tick();
        h.assertTrue(follower.isCompanionTraveling(), "STAY did not return to its anchor");
        follower.companionHold();
        // The owner is never a threat, even while it is the last attacker.
        follower.setLastHurtByMob(owner);
        goal.tick();
        h.assertTrue(follower.getTarget() == null, "Companion targeted its owner");
        follower.setLastHurtByMob(null);
        follower.discard();

        // --- unconscious companions ignore orders ---
        var sleeper = tamed(h, Species.VELOCIRAPTOR, owner);
        var sleeperGoal = new CompanionGoal(sleeper);
        TorporService.tickEntity(sleeper);
        TorporService.sedate(sleeper, TorporService.of(sleeper).maximum() * 4, null, "test");
        TorporService.tickEntity(sleeper);
        h.assertFalse(sleeperGoal.canUse(), "Unconscious companion still obeyed orders");
        sleeper.discard();

        // --- every species and realm accepts orders and steers ---
        for (var species : Species.values()) {
            var entity = tamed(h, species, owner);
            var speciesGoal = new CompanionGoal(entity);
            h.assertTrue(speciesGoal.canUse(), "Companion goal refused " + species);
            CompanionService.setOrder(entity, CompanionOrder.WANDER);
            h.assertTrue(CompanionService.of(entity).anchor() != null, "WANDER did not anchor " + species);
            entity.companionTravel(entity.position().add(8, 0, 0), 1.0);
            h.assertTrue(entity.isCompanionTraveling(), "Companion steering refused " + species);
            entity.companionHold();
            h.assertFalse(entity.isCompanionTraveling(), "Companion steering did not stop " + species);
            entity.discard();
        }
        owner.discard();
        h.succeed();
    }
    private CompanionGameTests() {}
}
