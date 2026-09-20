package dev.nez.arksurvivalreturns.gametest;

import java.util.List;
import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import dev.nez.arksurvivalreturns.Config;
import dev.nez.arksurvivalreturns.feature.creature.CreatureEntity;
import dev.nez.arksurvivalreturns.feature.creature.FlyingCreatureEntity;
import dev.nez.arksurvivalreturns.feature.creature.Species;
import dev.nez.arksurvivalreturns.feature.taming.CreatureProfileRegistry;
import dev.nez.arksurvivalreturns.feature.taming.CreatureRideController;
import dev.nez.arksurvivalreturns.feature.taming.CreatureTorporClips;
import dev.nez.arksurvivalreturns.feature.taming.TamingFeedback;
import dev.nez.arksurvivalreturns.feature.taming.TamingMethod;
import dev.nez.arksurvivalreturns.feature.taming.TamingService;
import dev.nez.arksurvivalreturns.feature.taming.TamingTags;
import dev.nez.arksurvivalreturns.feature.taming.TorporService;
import dev.nez.arksurvivalreturns.feature.taming.TorporState;
import dev.nez.arksurvivalreturns.feature.taming.UnconsciousBehavior;
import dev.nez.arksurvivalreturns.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * Headless verification of the taming, torpor, inventory and riding rules against the real registered
 * entities, the real item registry and real serialization.
 */
final class TamingGameTests {
    private static CreatureEntity create(GameTestHelper h, Species species) {
        var entity = ModContent.CREATURES.get(species).get().create(h.getLevel(), EntitySpawnReason.COMMAND);
        entity.setNoAi(true);
        entity.setPos(Vec3.atCenterOf(h.absolutePos(new BlockPos(8, 3, 8))));
        // A wild creature rolls its feeding hunger on its first simulated tick, so prime the attachments
        // here: a test that sets the hunger before the first interaction must not be overwritten by the roll.
        TorporService.of(entity);
        TorporService.tickEntity(entity);
        return entity;
    }

    /** One pass over the whole roster plus a few ticks of the shared torpor tick. */
    private static void advance(GameTestHelper h, CreatureEntity creature, int ticks) {
        for (int i = 0; i < ticks; i++) {
            TorporService.tickEntity(creature);
            creature.tick();
        }
    }

    /** Primes the profile ceiling (one tick) and delivers a full knock-out dose to the creature. */
    private static void knockOut(GameTestHelper h, CreatureEntity creature) {
        var state = TorporService.of(creature);
        TorporService.tickEntity(creature);
        TorporService.sedate(creature, state.maximum() * 4, null, "test");
        TorporService.tickEntity(creature);
    }

    private static String torporDebug(CreatureEntity creature) {
        var torpor = TorporService.of(creature);
        return " phase=" + torpor.phase() + " torpor=" + torpor.torpor() + "/" + torpor.maximum()
                + " tick=" + creature.tickCount;
    }

    /** Diagnostics for a stalled knock-out attempt. */
    private static String torch(CreatureEntity creature) {
        var torpor = TorporService.of(creature);
        return " torporPhase=" + torpor.phase() + " torpor=" + torpor.torpor() + "/" + torpor.maximum()
                + " acceptedFood=" + creature.tamingInventory().countAccepted()
                + " totalTicks=" + creature.tickCount;
    }

    /** The audit reports live outside the game directory in every dev run configuration. */
    private static java.nio.file.@org.jspecify.annotations.Nullable Path auditFile(String name) {
        for (String prefix : new String[]{"", "..", "../..", "../../.."}) {
            var candidate = java.nio.file.Path.of(prefix.isEmpty() ? name : prefix + "/" + name);
            if (java.nio.file.Files.isRegularFile(candidate)) return candidate;
        }
        return null;
    }

    static void roster(GameTestHelper h) {
        var problems = CreatureProfileRegistry.validate();
        h.assertTrue(problems.isEmpty(), "Roster problems: " + problems);
        var itemProblems = CreatureProfileRegistry.validateItems();
        h.assertTrue(itemProblems.isEmpty(), "Diet problems: " + itemProblems);
        h.assertTrue(Species.values().length == 41, "The roster changed; update the profile table");
        for (var species : Species.values()) {
            var profile = CreatureProfileRegistry.of(species);
            h.assertTrue(profile != null, "Missing profile: " + species.id);
            h.assertTrue(profile.species() == species, "Profile bound to the wrong species: " + species.id);
            h.assertTrue(CreatureProfileRegistry.ride(species) != null, "Missing ride profile: " + species.id);
            h.assertTrue(CreatureTorporClips.of(species) != null, "Missing torpor clips: " + species.id);
            int meals = profile.mealsPerTame();
            h.assertTrue(Math.abs(meals * profile.progressPerMeal() - 100.0) < 0.001,
                    "Meals do not add up to one tame: " + species.id);
            h.assertTrue(profile.targetSeconds() * 20 >= (meals - 1) * Config.MINIMUM_FEED_INTERVAL.get(),
                    "Target duration contradicts the meal budget: " + species.id);
            h.assertTrue(profile.accepts(new ItemStack(profile.preferred().getFirst().get())),
                    "Favourite is not accepted food: " + species.id);
            h.assertTrue(!profile.accepts(new ItemStack(ModContent.BERRIES.get("narcoberry").get())),
                    "Narcoberry must never be taming food: " + species.id);
            var ride = CreatureProfileRegistry.ride(species);
            h.assertTrue(ride.seatY() > 0 && ride.seatY() <= species.height,
                    "Seat height outside the hitbox: " + species.id);
            h.assertTrue(Math.abs(ride.groundDismountZ()) > species.width / 2,
                    "Ground dismount lands inside the hitbox: " + species.id);
            h.assertTrue(Math.abs(ride.altDismountX()) > species.width / 2,
                    "Side dismount lands inside the hitbox: " + species.id);
            if (species.realm() == Species.Realm.AIR)
                h.assertTrue(profile.method() == dev.nez.arksurvivalreturns.feature.taming.TamingMethod.AERIAL,
                        "Flying creature must use aerial feeding: " + species.id);
            else
                h.assertTrue(profile.method() != dev.nez.arksurvivalreturns.feature.taming.TamingMethod.AERIAL,
                        "Aerial feeding outside the flying realm: " + species.id);
        }
        tranquilizerRecipe(h);
        manifestMatchesTheTables(h);
        h.succeed();
    }

    /** The ranged sedative route is a shipped recipe; a data-pack regression must fail this suite. */
    private static void tranquilizerRecipe(GameTestHelper h) {
        var key = ResourceKey.create(Registries.RECIPE, ArkSurvivalReturns.id("tranquilizer_arrow"));
        var recipe = h.getLevel().getServer().getRecipeManager().byKey(key);
        h.assertTrue(recipe.isPresent(), "Tranquilizer arrow recipe did not load from the data pack");
        h.assertTrue(recipe.get().value() instanceof ShapelessRecipe,
                "Tranquilizer arrow recipe is not the expected shapeless recipe");
        var shapeless = (ShapelessRecipe) recipe.get().value();
        var input = CraftingInput.of(3, 2, List.of(
                new ItemStack(Items.ARROW), new ItemStack(Items.ARROW),
                new ItemStack(Items.ARROW), new ItemStack(Items.ARROW),
                new ItemStack(ModContent.BERRIES.get("narcoberry").get()), new ItemStack(Items.BONE)));
        h.assertTrue(shapeless.matches(input, h.getLevel()),
                "Tranquilizer arrow recipe no longer matches four arrows, narcoberry and bone");
        var crafted = shapeless.assemble(input);
        h.assertTrue(crafted.is(ModContent.TRANQUILIZER_ARROW_ITEM.get()) && crafted.getCount() == 4,
                "Tranquilizer arrow recipe result changed: " + crafted);
    }

    /** The generated Java tables must still agree with the on-disk audit reports. */
    private static void manifestMatchesTheTables(GameTestHelper h) {
        var seatFile = auditFile("docs/taming-seat-manifest.json");
        var matrixFile = auditFile("docs/taming-animation-matrix.json");
        h.assertTrue(seatFile != null && matrixFile != null,
                "The taming audit reports are missing; run tools/build_taming_manifests.py");
        try {
            var seats = com.google.gson.JsonParser.parseString(
                    java.nio.file.Files.readString(seatFile)).getAsJsonObject();
            h.assertTrue(seats.getAsJsonArray("species").size() == Species.values().length,
                    "The seat manifest does not cover the whole roster");
            for (var element : seats.getAsJsonArray("species")) {
                var row = element.getAsJsonObject();
                var profile = CreatureProfileRegistry.ride(speciesById(row.get("id").getAsString()));
                double x = row.get("local_x").getAsDouble();
                double y = row.get("recommended_local_y").getAsDouble();
                double z = row.get("local_z").getAsDouble();
                h.assertTrue(Math.abs(profile.seatX() - x) < 0.001 && Math.abs(profile.seatY() - y) < 0.001
                        && Math.abs(profile.seatZ() - z) < 0.001,
                        "Stale seat in the manifest for " + row.get("id").getAsString());
            }
            var missing = com.google.gson.JsonParser.parseString(
                    java.nio.file.Files.readString(matrixFile))
                    .getAsJsonObject().getAsJsonArray("species_without_torpor_assets");
            for (var element : missing) {
                var clips = CreatureTorporClips.of(speciesById(element.getAsString()));
                h.assertTrue(clips.in() == null && clips.loop() == null && clips.eat() == null,
                        "Reported gap has clips: " + element.getAsString());
            }
        } catch (java.io.IOException error) {
            h.assertTrue(false, "Could not read the audit reports: " + error);
        }
    }

    private static Species speciesById(String id) {
        for (var species : Species.values()) if (species.id.equals(id)) return species;
        throw new IllegalArgumentException("Unknown registry id in the manifest: " + id);
    }

    /** Sedative, knockout, further sedative, recovery and wake through the shared service. */
    static void torpor(GameTestHelper h) {
        for (var species : new Species[]{Species.TRICERATOPS, Species.CNIDARIA, Species.PTERANODON}) {
            var creature = create(h, species);
            var state = TorporService.of(creature);
            var profile = CreatureProfileRegistry.of(species);
            TorporService.tickEntity(creature);
            h.assertTrue(state.maximum() == profile.size().maxTorpor(), "Wrong ceiling: " + species.id);
            h.assertTrue(!state.restricted(), "Wild creature started unconscious: " + species.id);

            double potency = state.maximum() / profile.sedativeResistance();
            TorporService.sedate(creature, potency, null, "test");
            TorporService.tickEntity(creature);
            h.assertTrue(state.restricted(), "Sedative did not knock the creature out: " + species.id + torporDebug(creature));
            h.assertTrue(state.phase() == TorporState.Phase.COLLAPSING, "Wrong phase after a full dose: " + species.id);

            // Additional sedative only refills the meter; progress must not move.
            float before = TamingService.of(creature).progress();
            TorporService.sedate(creature, potency / 2, null, "test");
            h.assertTrue(TamingService.of(creature).progress() == before,
                    "Additional sedative advanced taming: " + species.id);

            advance(h, creature, 400);
            h.assertTrue(state.phase() == TorporState.Phase.TORPID || state.phase() == TorporState.Phase.WAKING_WILD,
                    "Collapse did not settle into torpor: " + species.id);

            // The wake threshold is exact: above it the creature stays down, below it the wake starts.
            TorporService.setTorpor(creature, state.wakeThreshold() + 1.0);
            TorporService.tickEntity(creature);
            h.assertTrue(state.torpid(), "Woke above the wake threshold: " + species.id + torporDebug(creature));
            TorporService.setTorpor(creature, state.wakeThreshold() - 1.0);
            TorporService.tickEntity(creature);
            h.assertTrue(state.phase() == TorporState.Phase.WAKING_WILD
                    || state.phase() == TorporState.Phase.WAKING_TAMED,
                    "Below the threshold the wake sequence must start: " + species.id + torporDebug(creature));

            advance(h, creature, 20 * 400);
            h.assertTrue(!state.restricted(), "Creature never woke: " + species.id);
            h.assertTrue(state.torpor() == 0.0, "Torpor did not decay to zero: " + species.id);
            h.assertTrue(state.phase() == TorporState.Phase.AWAKE, "Wrong wake phase: " + species.id);
            creature.discard();
        }
        h.succeed();
    }

    /** Passive feeding: validation, one item per meal, ownership and the damage penalty. */
    static void passiveFeeding(GameTestHelper h) {
        var creature = create(h, Species.LYSTROSAURUS);
        var state = TamingService.of(creature);
        var profile = CreatureProfileRegistry.of(creature.species());
        Player player = h.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        Player rival = h.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        var favourite = profile.preferred().getFirst().get();

        // Wrong food, not hungry and cooldown each consume nothing and give nothing.
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STONE, 4));
        h.assertTrue(TamingService.feedByHand(creature, player, InteractionHand.MAIN_HAND)
                == TamingFeedback.Result.WRONG_FOOD, "Wrong food was accepted");
        h.assertTrue(player.getItemInHand(InteractionHand.MAIN_HAND).getCount() == 4, "Wrong food was consumed");

        state.setHunger(0);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(favourite, 8));
        h.assertTrue(TamingService.feedByHand(creature, player, InteractionHand.MAIN_HAND)
                == TamingFeedback.Result.NOT_HUNGRY, "A full creature ate");
        h.assertTrue(player.getItemInHand(InteractionHand.MAIN_HAND).getCount() == 8, "Food vanished while sated");

        state.setHunger(100);
        h.assertTrue(TamingService.feedByHand(creature, player, InteractionHand.MAIN_HAND)
                == TamingFeedback.Result.ACCEPTED, "Hungry creature refused the favourite food");
        h.assertTrue(player.getItemInHand(InteractionHand.MAIN_HAND).getCount() == 7, "Meal did not consume one item");
        float first = state.progress();
        h.assertTrue(first > 0, "Meal gave no progress");
        h.assertTrue(state.claimedBy(player.getUUID()), "Feeder is not the claimant");

        // Immediate second meal is refused by the interval.
        state.setHunger(100);
        h.assertTrue(TamingService.feedByHand(creature, player, InteractionHand.MAIN_HAND)
                == TamingFeedback.Result.COOLDOWN, "Feeding interval was ignored");

        // A rival cannot take over the attempt.
        rival.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(favourite, 4));
        h.assertTrue(TamingService.feedByHand(creature, rival, InteractionHand.MAIN_HAND)
                == TamingFeedback.Result.NOT_CLAIMANT, "Rival took over the attempt");
        h.assertTrue(rival.getItemInHand(InteractionHand.MAIN_HAND).getCount() == 4, "Rival lost food to a refusal");

        // Damage costs progress but never ownership.
        TamingService.onDamaged(creature, rival);
        h.assertTrue(state.progress() < first, "Damage did not penalise progress");

        // Feed to completion with separated meals.
        int guard = 0;
        while (!state.tamed() && guard++ < 64) {
            state.setHunger(100);
            advance(h, creature, Config.MINIMUM_FEED_INTERVAL.get() + 1);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(favourite, 8));
            var outcome = TamingService.feedByHand(creature, player, InteractionHand.MAIN_HAND);
            h.assertTrue(outcome == TamingFeedback.Result.ACCEPTED || state.tamed(),
                    "Feeding stopped early: " + outcome + " progress=" + state.progress()
                            + " hunger=" + state.hunger() + " meals=" + state.meals()
                            + " owner=" + state.owner() + " claimant=" + state.claimant());
        }
        h.assertTrue(state.tamed(), "Passive tame never completed");
        h.assertTrue(player.getUUID().equals(state.owner()), "Wrong owner after completion");
        h.assertTrue(creature.isPersistenceRequired(), "A tame must persist");

        // A tamed creature refuses further taming food and cannot be stolen by a second player.
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(favourite, 4));
        state.setHunger(100);
        h.assertTrue(TamingService.feedByHand(creature, player, InteractionHand.MAIN_HAND)
                == TamingFeedback.Result.ALREADY_TAMED, "A tame kept eating for progress");
        h.assertTrue(!TamingService.ownedBy(creature, rival), "Ownership leaked to a second player");
        h.assertTrue(TamingService.canAccess(creature, player), "Owner lost inventory access");
        h.assertTrue(!TamingService.canAccess(creature, rival), "Stranger gained inventory access");
        creature.discard();
        h.succeed();
    }

    /** Knock-out feeding: inventory access, one meal per opportunity, completion with the owner away. */
    static void knockoutFeeding(GameTestHelper h) {
        var creature = create(h, Species.TRICERATOPS);
        var profile = CreatureProfileRegistry.of(creature.species());
        var taming = TamingService.of(creature);
        var torpor = TorporService.of(creature);
        Player claimant = h.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        Player stranger = h.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);

        // Awake knock-out creature: hand feeding must be rejected, and no inventory access yet.
        claimant.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.CARROT, 4));
        h.assertTrue(TamingService.feedByHand(creature, claimant, InteractionHand.MAIN_HAND)
                == TamingFeedback.Result.WRONG_STATE, "Awake knock-out creature accepted hand feeding");
        h.assertTrue(!TamingService.canAccess(creature, claimant), "Awake creature opened its inventory");

        knockOut(h, creature);
        advance(h, creature, 200);
        h.assertTrue(TorporService.feedable(creature),
                "Unconscious creature is not feedable: " + creature.species().id + torporDebug(creature));

        TamingService.claimIfFree(creature, claimant);
        h.assertTrue(TamingService.canAccess(creature, claimant), "Claimant was refused the taming inventory");
        h.assertTrue(!TamingService.canAccess(creature, stranger), "Stranger gained the taming inventory");

        // Incorrect food stays untouched, accepted food is eaten one meal at a time.
        creature.tamingInventory().setItem(0, new ItemStack(Items.STONE, 3));
        creature.tamingInventory().setItem(1, new ItemStack(Items.CARROT, 30));
        taming.setHunger(100);
        advance(h, creature, 10);
        h.assertTrue(creature.tamingInventory().getItem(0).getCount() == 3, "Incorrect food was consumed");
        h.assertTrue(creature.tamingInventory().getItem(1).getCount() == 29, "One meal was not consumed exactly once");
        h.assertTrue(taming.meals() == 1, "A single opportunity ate more than one meal");

        // Finish the tame while no player is anywhere near the creature.
        int guard = 0;
        while (!taming.tamed() && guard++ < 128) {
            taming.setHunger(100);
            TorporService.setTorpor(creature, torpor.maximum());
            advance(h, creature, Config.MINIMUM_FEED_INTERVAL.get() + 1);
        }
        h.assertTrue(taming.tamed(), "Knock-out tame never completed: progress=" + taming.progress()
                + " meals=" + taming.meals() + " claimant=" + taming.claimant()
                + torch(creature));
        h.assertTrue(claimant.getUUID().equals(taming.owner()), "Claimant did not receive ownership");
        h.assertTrue(creature.distanceToSqr(claimant) > 4, "Test did not keep the claimant away");
        creature.discard();
        h.succeed();
    }

    /** Waking before completion drops the attempt but keeps, and never duplicates, the deposit. */
    static void wakeBeforeCompletion(GameTestHelper h) {
        var creature = create(h, Species.CARNOTAURUS);
        var profile = CreatureProfileRegistry.of(creature.species());
        var taming = TamingService.of(creature);
        var torpor = TorporService.of(creature);
        Player claimant = h.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        knockOut(h, creature);
        advance(h, creature, 200);
        TamingService.claimIfFree(creature, claimant);
        creature.tamingInventory().setItem(0, new ItemStack(profile.preferred().getFirst().get(), 5));
        taming.setHunger(100);
        advance(h, creature, Config.MINIMUM_FEED_INTERVAL.get() + 1);
        h.assertTrue(taming.progress() > 0, "No progress was recorded before waking");

        int remaining = creature.tamingInventory().getItem(0).getCount();
        TorporService.setTorpor(creature, 0);
        advance(h, creature, 20 * 60);
        h.assertTrue(!torpor.restricted(), "Creature did not wake");
        h.assertTrue(taming.progress() == 0f, "Incomplete attempt was not reset");
        h.assertTrue(taming.claimant() == null, "Claim survived an abandoned attempt");
        h.assertTrue(creature.tamingInventory().getItem(0).getCount() == remaining,
                "Waking changed the deposited food count");
        h.assertTrue(!taming.tamed(), "A failed attempt produced a tame");

        // The abandoned deposit is not eaten into an ownerless tame: with no claimant holding the attempt,
        // a fresh knock-out must leave the leftover food exactly where it is.
        int mealsBefore = taming.meals();
        knockOut(h, creature);
        advance(h, creature, 200);
        for (int i = 0; i < 10; i++) {
            taming.setHunger(100);
            TorporService.setTorpor(creature, torpor.maximum());
            advance(h, creature, Config.MINIMUM_FEED_INTERVAL.get() + 1);
        }
        h.assertTrue(creature.tamingInventory().getItem(0).getCount() == remaining,
                "An ownerless attempt ate the abandoned deposit");
        h.assertTrue(taming.meals() == mealsBefore && taming.progress() == 0f,
                "An ownerless attempt advanced taming: progress=" + taming.progress()
                        + " meals=" + taming.meals() + " (was " + mealsBefore + ")");
        creature.discard();
        h.succeed();
    }

    /** Riding rules and rider placement for the entire roster. */
    static void riding(GameTestHelper h) {
        Player rider = h.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        Player stranger = h.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        for (var species : Species.values()) {
            var creature = create(h, species);
            var taming = TamingService.of(creature);

            // Wild and unsaddled creatures cannot be mounted.
            h.assertTrue(!CreatureRideController.canMount(creature, rider), "Wild creature was mountable: " + species.id);
            taming.setOwner(rider.getUUID());
            h.assertTrue(!CreatureRideController.canMount(creature, rider), "Unsaddled creature was mountable: " + species.id);
            h.assertTrue(!CreatureRideController.canMount(creature, stranger), "Stranger could mount an owned creature: " + species.id);

            creature.setItemSlot(EquipmentSlot.SADDLE, new ItemStack(Items.SADDLE));
            h.assertTrue(creature.isSaddled(), "Saddle did not register: " + species.id);
            h.assertTrue(CreatureRideController.canMount(creature, rider), "Owner could not mount: " + species.id);
            h.assertTrue(!CreatureRideController.canMount(creature, stranger), "Stranger could mount: " + species.id);

            h.assertTrue(rider.startRiding(creature), "Mount attempt failed: " + species.id);
            h.assertTrue(creature.getControllingPassenger() == rider, "Owner is not the controller: " + species.id);
            var seat = creature.seatPosition();
            double belowFeet = seat.y - creature.getY();
            h.assertTrue(belowFeet > 0 && belowFeet <= species.height + 0.001,
                    "Seat is outside the body: " + species.id + " (" + belowFeet + ")");
            h.assertTrue(creature.getPassengers().contains(rider), "Rider was not a passenger: " + species.id);

            // Unconsciousness ejects the rider and removes control.
            knockOut(h, creature);
            h.assertTrue(TorporService.restricted(creature),
                    "Sedative failed on a ridden creature: " + species.id + torporDebug(creature));
            h.assertTrue(!creature.getPassengers().contains(rider) || rider.getVehicle() == null,
                    "Rider stayed on an unconscious creature: " + species.id);
            h.assertTrue(creature.getControllingPassenger() == null, "Unconscious creature kept a controller: " + species.id);

            // A safe dismount position exists for every creature on solid test ground.
            var safe = CreatureRideController.findSafePosition(h.getLevel(), creature, rider);
            h.assertTrue(safe != null, "No safe dismount position: " + species.id);
            creature.discard();
        }
        h.succeed();
    }

    /** Torpor, taming, ownership and the inventory survive a save/load cycle without duplication. */
    static void persistence(GameTestHelper h) {
        var creature = create(h, Species.VELOCIRAPTOR);
        Player owner = h.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        var taming = TamingService.of(creature);
        var torpor = TorporService.of(creature);
        taming.setOwner(owner.getUUID());
        taming.setProgress(63.5f);
        taming.setHunger(71.0);
        taming.claim(owner.getUUID(), 42);
        TorporService.of(creature);
        TorporService.tickEntity(creature);
        double expectedHunger = taming.hunger();
        double expectedProgress = taming.progress();
        double expectedTorpor = TorporService.of(creature).maximum() * 0.75;
        TorporService.setTorpor(creature, expectedTorpor);
        creature.setItemSlot(EquipmentSlot.SADDLE, new ItemStack(Items.SADDLE));
        creature.tamingInventory().setItem(0, new ItemStack(Items.BEEF, 7));
        creature.tamingInventory().setItem(4, new ItemStack(ModContent.BERRIES.get("narcoberry").get(), 3));

        var output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, h.getLevel().registryAccess());
        creature.saveWithoutId(output);
        var restored = create(h, Species.VELOCIRAPTOR);
        restored.load(TagValueInput.create(ProblemReporter.DISCARDING, h.getLevel().registryAccess(),
                output.buildResult()));

        var restoredTaming = TamingService.of(restored);
        var restoredTorpor = TorporService.of(restored);
        h.assertTrue(restoredTaming.tamed() && owner.getUUID().equals(restoredTaming.owner()), "Ownership was lost");
        h.assertTrue(Math.abs(restoredTaming.progress() - expectedProgress) < 0.01f, "Progress was not saved");
        h.assertTrue(Math.abs(restoredTaming.hunger() - expectedHunger) < 0.01, "Feeding hunger was not saved");
        h.assertTrue(restoredTaming.claimedBy(owner.getUUID()), "Claimant was not saved");
        h.assertTrue(Math.abs(restoredTorpor.torpor() - expectedTorpor) < 0.01, "Torpor was not saved");
        h.assertTrue(restored.isSaddled(), "Saddle was not saved");
        h.assertTrue(restored.tamingInventory().getItem(0).is(Items.BEEF)
                && restored.tamingInventory().getItem(0).getCount() == 7, "Deposited food was not saved");
        h.assertTrue(restored.tamingInventory().getItem(4).getCount() == 3, "Sedative stock was duplicated or lost");
        h.assertTrue(restored.tamingInventory().countAccepted() == 1, "Unexpected accepted stacks after load");
        restored.discard();
        creature.discard();
        h.succeed();
    }

    /** Players can be sedated but can never be tamed, and the exclusions stay excluded. */
    static void playerSedation(GameTestHelper h) {
        Player player = h.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        h.assertTrue(TorporService.eligible(player), "A survival player must be a legal target");
        TorporService.sedate(player, Config.PLAYER_MAX_TORPOR.get() * 4, null, "test");
        TorporService.tickEntity(player);
        h.assertTrue(TorporService.restricted(player), "Player was not knocked out on tick " + player.tickCount
                + " phase=" + TorporService.of(player).phase() + " torpor=" + TorporService.of(player).torpor());
        h.assertTrue(player.getData(dev.nez.arksurvivalreturns.feature.taming.TamingAttachments.TORPOR)
                .phase() == TorporState.Phase.COLLAPSING, "Player skipped the collapse phase");
        h.assertTrue(!TamingService.of(player).tamed(), "A player was tamed");
        TorporService.clear(player);
        h.assertTrue(!TorporService.restricted(player), "Player stayed unconscious after a clear");
        h.assertTrue(!TorporService.eligible(new net.minecraft.world.entity.decoration.ArmorStand(
                net.minecraft.world.entity.EntityType.ARMOR_STAND, h.getLevel())), "Armor stands must be excluded");
        h.succeed();
    }

    /**
     * Aerial feeding: a hungry, aggressive flyer accepts fish on hunger rather than chance, and the truce it
     * grants protects only the feeder, ends when that feeder attacks and never survives a reload.
     */
    static void aerialFeeding(GameTestHelper h) {
        var creature = create(h, Species.DRAGON);
        h.assertTrue(creature instanceof FlyingCreatureEntity, "The aerial test needs a flying creature");
        var flyer = (FlyingCreatureEntity) creature;
        var profile = CreatureProfileRegistry.of(creature.species());
        var taming = TamingService.of(creature);
        Player feeder = h.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        Player rival = h.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        // The theft check also asks whether the player is inside the creature's habitat leash, and mock
        // players spawn at the world origin. Put both of them on the bird, or the truce assertions below
        // would pass for the wrong reason.
        feeder.setPos(creature.position());
        rival.setPos(creature.position());
        var fish = profile.preferred().getFirst().get();

        h.assertTrue(creature.species().predator, "The aerial rule needs an aggressive flyer");
        h.assertTrue(profile.method() == TamingMethod.AERIAL, "A flyer must use aerial feeding");

        // Hunger, not random chance, decides acceptance: the same sated flyer refuses the same fish.
        taming.setHunger(0);
        feeder.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(fish, 6));
        h.assertTrue(TamingService.feedByHand(creature, feeder, InteractionHand.MAIN_HAND)
                == TamingFeedback.Result.NOT_HUNGRY, "A sated flyer ate");
        h.assertTrue(feeder.getItemInHand(InteractionHand.MAIN_HAND).getCount() == 6,
                "A refused meal still consumed food");

        // Control: while no truce is held, both players standing on the bird are valid thieves, so the
        // assertions after the meal really do measure the truce rather than the leash check.
        h.assertTrue(flyer.validThief(feeder), "Control: the feeder was never a valid thief");
        h.assertTrue(flyer.validThief(rival), "Control: the rival was never a valid thief");

        taming.setHunger(100);
        h.assertTrue(TamingService.feedByHand(creature, feeder, InteractionHand.MAIN_HAND)
                == TamingFeedback.Result.ACCEPTED, "A hungry aggressive flyer refused fish");
        h.assertTrue(feeder.getItemInHand(InteractionHand.MAIN_HAND).getCount() == 5,
                "One fish per meal was not honoured");
        h.assertTrue(taming.progress() > 0, "The fish gave no progress");
        h.assertTrue(taming.claimedBy(feeder.getUUID()), "The feeder is not the claimant");

        // The truce protects the feeder only: the rival is still a valid target, so the creature's defensive
        // behaviour is not globally disabled.
        h.assertTrue(creature.feedingTruce(feeder), "No feeding truce was granted");
        h.assertTrue(!creature.feedingTruce(rival), "The truce leaked to a second player");
        h.assertTrue(feeder.getUUID().equals(taming.truceFeeder()), "Wrong truce holder");
        h.assertTrue(!flyer.validThief(feeder), "The truce did not suppress the theft check");
        h.assertTrue(flyer.validThief(rival), "The truce disabled the creature's defence against others");

        // A truce is deliberately transient: it is not part of the saved attachment.
        var output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, h.getLevel().registryAccess());
        TamingService.serialize(taming, output);
        var restored = TamingService.deserialize(TagValueInput.create(ProblemReporter.DISCARDING,
                h.getLevel().registryAccess(), output.buildResult()));
        h.assertTrue(!restored.truceActive(feeder.getUUID()), "The feeding truce survived a reload");

        // Attacking the creature ends the truce immediately and costs progress, which restores the threat.
        float before = taming.progress();
        TamingService.onDamaged(creature, feeder);
        h.assertTrue(!creature.feedingTruce(feeder), "Attacking did not cancel the truce");
        h.assertTrue(taming.progress() < before, "Attacking did not penalise progress");
        h.assertTrue(flyer.validThief(feeder), "The cancelled truce did not restore the theft check");

        // The window is bounded by the entity's own attempt clock.
        taming.setHunger(100);
        advance(h, creature, Config.MINIMUM_FEED_INTERVAL.get() + 1);
        feeder.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(fish, 6));
        h.assertTrue(TamingService.feedByHand(creature, feeder, InteractionHand.MAIN_HAND)
                == TamingFeedback.Result.ACCEPTED, "The flyer refused a later meal");
        h.assertTrue(creature.feedingTruce(feeder), "No second truce was granted");
        advance(h, creature, Config.FEEDING_TRUCE_TICKS.get() + 1);
        h.assertTrue(!creature.feedingTruce(feeder), "The truce outlived its window");
        h.assertTrue(flyer.validThief(feeder), "The expired truce did not restore the theft check");
        creature.discard();
        h.succeed();
    }

    /** Completion must never force a premature wake: the tame stays down until ordinary recovery. */
    static void completionKeepsSleep(GameTestHelper h) {
        var creature = create(h, Species.TRICERATOPS);
        var profile = CreatureProfileRegistry.of(creature.species());
        var taming = TamingService.of(creature);
        var torpor = TorporService.of(creature);
        Player claimant = h.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);

        knockOut(h, creature);
        advance(h, creature, 200);
        TamingService.claimIfFree(creature, claimant);
        creature.tamingInventory().setItem(0, new ItemStack(profile.preferred().getFirst().get(), 40));
        int guard = 0;
        while (!taming.tamed() && guard++ < 128) {
            taming.setHunger(100);
            TorporService.setTorpor(creature, torpor.maximum());
            advance(h, creature, Config.MINIMUM_FEED_INTERVAL.get() + 1);
        }
        h.assertTrue(taming.tamed(), "The knock-out tame never completed: meals=" + taming.meals()
                + " progress=" + taming.progress() + torch(creature));

        // The tame is finished while the creature is still asleep.
        h.assertTrue(claimant.getUUID().equals(taming.owner()), "Wrong owner after completion");
        h.assertTrue(creature.isPersistenceRequired(), "A tame must persist");
        h.assertTrue(TorporService.restricted(creature), "Completion force-woke the creature");
        h.assertTrue(TorporService.feedable(creature), "A completed tame left the feeding loop early");

        // It returns to consciousness only through ordinary recovery, and keeps the tame.
        taming.setHunger(0);
        int wakeGuard = 0;
        while (TorporService.restricted(creature) && wakeGuard++ < 40) advance(h, creature, 200);
        h.assertTrue(!TorporService.restricted(creature), "The tame never woke through ordinary recovery");
        h.assertTrue(torpor.torpor() <= torpor.wakeThreshold(), "The tame woke above the wake threshold: "
                + torpor.torpor() + "/" + torpor.wakeThreshold());
        h.assertTrue(taming.tamed() && claimant.getUUID().equals(taming.owner()), "Waking lost the tame");
        h.assertTrue(taming.progress() == 100f, "Waking a tame reset its progress");
        creature.discard();
        h.succeed();
    }

    /**
     * Claim leases. An abandoned hand-fed attempt passes to the player who resumes it, while an automatic
     * knock-out attempt keeps the depositing claimant even though the creature outlives the lease.
     */
    static void claimExpiry(GameTestHelper h) {
        var passive = create(h, Species.LYSTROSAURUS);
        var passiveTaming = TamingService.of(passive);
        var passiveProfile = CreatureProfileRegistry.of(passive.species());
        var food = passiveProfile.preferred().getFirst().get();
        Player first = h.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        Player second = h.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        first.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(food, 8));
        passiveTaming.setHunger(100);
        h.assertTrue(TamingService.feedByHand(passive, first, InteractionHand.MAIN_HAND)
                == TamingFeedback.Result.ACCEPTED, "The first feeder was refused");
        h.assertTrue(passiveTaming.claimedBy(first.getUUID()), "The first feeder is not the claimant");

        advance(h, passive, Config.CLAIM_EXPIRY.get() + 1);
        h.assertTrue(passiveTaming.claimExpired(), "An abandoned lease never lapsed");
        passiveTaming.setHunger(100);
        second.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(food, 8));
        h.assertTrue(TamingService.feedByHand(passive, second, InteractionHand.MAIN_HAND)
                == TamingFeedback.Result.ACCEPTED, "The resuming feeder was refused");
        h.assertTrue(passiveTaming.claimedBy(second.getUUID()),
                "The resumed attempt still belonged to the absent player");
        h.assertTrue(second.getItemInHand(InteractionHand.MAIN_HAND).getCount() == 7,
                "The resumed meal did not consume exactly one item");
        passive.discard();

        // The automatic path has no feeder, so the claimant who deposited the food keeps the tame.
        var ko = create(h, Species.TRICERATOPS);
        var koTaming = TamingService.of(ko);
        var koTorpor = TorporService.of(ko);
        var koProfile = CreatureProfileRegistry.of(ko.species());
        Player depositor = h.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        knockOut(h, ko);
        advance(h, ko, 200);
        TamingService.claimIfFree(ko, depositor);
        long claimStart = koTaming.claimStartedAt();
        ko.tamingInventory().setItem(0, new ItemStack(koProfile.preferred().getFirst().get(), 40));
        int guard = 0;
        while (!koTaming.tamed() && guard++ < 128) {
            koTaming.setHunger(100);
            TorporService.setTorpor(ko, koTorpor.maximum());
            advance(h, ko, Config.MINIMUM_FEED_INTERVAL.get() + 1);
        }
        h.assertTrue(koTaming.tamed(), "The knock-out tame never completed: " + torch(ko));
        h.assertTrue(koTaming.ticks() - claimStart > Config.CLAIM_EXPIRY.get(),
                "The test never exercised a lease expiry (ticks=" + (koTaming.ticks() - claimStart) + ")");
        h.assertTrue(depositor.getUUID().equals(koTaming.owner()),
                "The depositing claimant lost the tame to the lease clock");
        ko.discard();
        h.succeed();
    }

    /**
     * Ordinary living mobs share the integration without the no-AI shortcut: the restraint has to be
     * goal-level, so gravity, fluids and knockback keep moving the body.
     */
    static void ordinaryMobRestraint(GameTestHelper h) {
        Mob mob = EntityType.HUSK.create(h.getLevel(), EntitySpawnReason.COMMAND);
        h.assertTrue(mob != null, "The ordinary mob could not be created");
        // A husk is not sun-sensitive, so daylight cannot interfere with the fall below.
        mob.setPos(Vec3.atCenterOf(h.absolutePos(new BlockPos(8, 6, 8))));
        double startY = mob.getY();
        h.assertTrue(!TorporService.tracked(mob), "An ordinary mob was tracked before any sedative");
        h.assertTrue(mob.goalSelector.getAvailableGoals().stream()
                .noneMatch(goal -> goal.getGoal() instanceof UnconsciousBehavior),
                "An ordinary mob started restrained");

        TorporService.sedate(mob, TorporService.maximumFor(mob) * 4, null, "test");
        TorporService.tickEntity(mob);
        h.assertTrue(TorporService.restricted(mob), "An ordinary mob was not knocked out");
        h.assertTrue(!mob.isNoAi(), "The restraint used the no-AI flag, which suppresses gravity");

        var restraint = mob.goalSelector.getAvailableGoals().stream()
                .filter(goal -> goal.getGoal() instanceof UnconsciousBehavior).findFirst().orElse(null);
        h.assertTrue(restraint != null, "The suppression goal was never installed");
        int bestVanillaPriority = mob.goalSelector.getAvailableGoals().stream()
                .filter(goal -> goal != restraint).mapToInt(goal -> goal.getPriority()).min().orElse(0);
        h.assertTrue(restraint.getPriority() < bestVanillaPriority,
                "The suppression goal does not outrank every vanilla goal: " + restraint.getPriority()
                        + " vs " + bestVanillaPriority);
        // The goal suppresses the AI by claiming every controller flag, which is what stops the vanilla
        // goals from running while leaving gravity and knockback alone.
        h.assertTrue(restraint.getGoal().getFlags().containsAll(java.util.EnumSet.allOf(Goal.Flag.class)),
                "The suppression goal does not claim every AI flag: " + restraint.getGoal().getFlags());
        for (int i = 0; i < 10; i++) mob.tick();
        h.assertTrue(restraint.isRunning(), "The suppression goal never took control");
        h.assertTrue(mob.getY() < startY, "A restrained mob stopped falling: y=" + mob.getY() + " start=" + startY);

        // Waking restores voluntary control through the same shared service, and must not leave a duplicate
        // restraint behind when the mob is sedated again.
        TorporService.setTorpor(mob, 0);
        for (int i = 0; i < 400; i++) TorporService.tickEntity(mob);
        h.assertTrue(!TorporService.restricted(mob), "The ordinary mob never woke");
        TorporService.clear(mob);
        TorporService.sedate(mob, TorporService.maximumFor(mob) * 4, null, "test");
        TorporService.tickEntity(mob);
        long restraints = mob.goalSelector.getAvailableGoals().stream()
                .filter(goal -> goal.getGoal() instanceof UnconsciousBehavior).count();
        h.assertTrue(restraints == 1, "Re-sedation installed " + restraints + " restraint goals");
        h.succeed();
    }
}
