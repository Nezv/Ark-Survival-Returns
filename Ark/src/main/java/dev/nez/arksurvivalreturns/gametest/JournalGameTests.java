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

/** The shipped quest pack loads, and every shipped objective resolves its content. */
final class JournalGameTests {
    private static final String PRIMITIVE = "0000000000000100";
    private static final String CAMP = "0000000000000200";
    private static final String WORK = "0000000000000300";

    static void pack(GameTestHelper h) {
        h.assertTrue(ServerQuestFile.exists(), "The survival journal did not load");
        var file = ServerQuestFile.getInstance();
        verify(h, file, PRIMITIVE, 6);
        verify(h, file, CAMP, 6);
        verify(h, file, WORK, 5);
        for (String recipe : new String[]{"field_journal", "bedroll", "fiber_bandage", "flint_knife", "spear",
                "pack_harness", "reinforced_harness", "trough", "drying_rack", "cooking_pot",
                "concentrated_sedative", "improved_tranquilizer_arrow"}) {
            var key = ResourceKey.create(Registries.RECIPE, ArkSurvivalReturns.id(recipe));
            h.assertFalse(h.getLevel().recipeAccess().byKey(key).isEmpty(), "Recipe is missing: " + recipe);
        }
        h.succeed();
    }

    /** Every quest in a chapter must carry a reward and resolve any item task. */
    private static void verify(GameTestHelper h, ServerQuestFile file, String chapterId, int expectedQuests) {
        var chapter = file.getAllChapters().stream()
                .filter(candidate -> candidate.getCodeString().equals(chapterId))
                .findFirst().orElse(null);
        h.assertTrue(chapter != null, "The chapter is missing: " + chapterId);
        h.assertTrue(chapter.getQuests().size() == expectedQuests,
                "Chapter " + chapterId + " changed size: " + chapter.getQuests().size());
        for (var quest : chapter.getQuests()) {
            h.assertFalse(quest.getTasks().isEmpty(), "Quest has no tasks: " + quest.getCodeString());
            h.assertFalse(quest.getRewards().isEmpty(), "Quest has no rewards: " + quest.getCodeString());
            for (var task : quest.getTasks()) {
                if (task instanceof ItemTask itemTask) {
                    h.assertFalse(itemTask.getItemStack().isEmpty(), "Unresolved task item: " + task.getCodeString());
                }
            }
        }
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
