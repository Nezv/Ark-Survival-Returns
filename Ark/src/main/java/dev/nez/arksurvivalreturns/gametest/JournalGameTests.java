package dev.nez.arksurvivalreturns.gametest;

import dev.ftb.mods.ftbquests.quest.ServerQuestFile;
import dev.ftb.mods.ftbquests.quest.task.ItemTask;
import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import dev.nez.arksurvivalreturns.feature.creature.Species;
import dev.nez.arksurvivalreturns.registry.ModContent;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.GameType;

/** The shipped quest pack loads, and every Primitive objective resolves its content. */
final class JournalGameTests {
    private static final String PRIMITIVE = "0000000000000100";

    static void pack(GameTestHelper h) {
        h.assertTrue(ServerQuestFile.exists(), "The survival journal did not load");
        var file = ServerQuestFile.getInstance();
        var chapter = file.getAllChapters().stream()
                .filter(candidate -> candidate.getCodeString().equals(PRIMITIVE))
                .findFirst().orElse(null);
        h.assertTrue(chapter != null, "The Primitive chapter is missing");
        h.assertTrue(chapter.getQuests().size() == 6, "Primitive chapter changed size: " + chapter.getQuests().size());
        for (var quest : chapter.getQuests()) {
            h.assertFalse(quest.getTasks().isEmpty(), "Quest has no tasks: " + quest.getCodeString());
            h.assertFalse(quest.getRewards().isEmpty(), "Quest has no rewards: " + quest.getCodeString());
            for (var task : quest.getTasks()) {
                if (task instanceof ItemTask itemTask) {
                    h.assertFalse(itemTask.getItemStack().isEmpty(), "Unresolved task item: " + task.getCodeString());
                }
            }
        }
        var recipeKey = ResourceKey.create(Registries.RECIPE, ArkSurvivalReturns.id("field_journal"));
        h.assertFalse(h.getLevel().recipeAccess().byKey(recipeKey).isEmpty(), "Field Journal recipe is missing");
        h.succeed();
    }

    /** A tame that came from the rank-5 band grants the map entitlement; other origins do not. */
    static void tamingUnlock(GameTestHelper h) {
        var world = h.getLevel();
        var owner = h.makeMockPlayer(GameType.SURVIVAL);
        world.addFreshEntity(owner);
        var outsider = h.makeMockPlayer(GameType.SURVIVAL);

        var rare = ModContent.CREATURES.get(Species.PARASAUR).get().create(world, EntitySpawnReason.COMMAND);
        rare.recordOrigin(5);
        var unlocks = dev.nez.arksurvivalreturns.feature.map.MapUnlockData.get(world);
        h.assertFalse(unlocks.isUnlocked(owner.getUUID()), "Entitlement should start locked");
        dev.nez.arksurvivalreturns.feature.taming.TamingService.applyTameEffects(rare, owner.getUUID());
        h.assertTrue(unlocks.isUnlocked(owner.getUUID()), "A rank-5-origin tame did not unlock the map");
        rare.discard();

        var ordinary = ModContent.CREATURES.get(Species.PARASAUR).get().create(world, EntitySpawnReason.COMMAND);
        ordinary.recordOrigin(1);
        dev.nez.arksurvivalreturns.feature.taming.TamingService.applyTameEffects(ordinary, outsider.getUUID());
        h.assertFalse(unlocks.isUnlocked(outsider.getUUID()), "An ordinary-origin tame unlocked the map");
        ordinary.discard();
        h.succeed();
    }

    private JournalGameTests() {}
}
