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
    private static final String HOMESTEAD = "0000000000000400";
    private static final String GUARDIAN = "0000000000000500";

    static void pack(GameTestHelper h) {
        h.assertTrue(ServerQuestFile.exists(), "The survival journal did not load");
        var file = ServerQuestFile.getInstance();
        verify(h, file, PRIMITIVE, 5);
        verify(h, file, CAMP, 5);
        verify(h, file, WORK, 5);
        verify(h, file, HOMESTEAD, 4);
        verify(h, file, GUARDIAN, 3);
        for (String recipe : new String[]{"bedroll", "fiber_bandage", "flint_knife", "keratin_spear",
                "pack_harness", "reinforced_harness", "trough", "drying_rack", "cooking_pot",
                "narcotics", "mortar_and_pestle", "tranquilizer_arrow"}) {
            var key = ResourceKey.create(Registries.RECIPE, ArkSurvivalReturns.id(recipe));
            boolean inGraph = dev.nez.arksurvivalreturns.feature.station.WorkstationCatalog.all().values().stream()
                    .flatMap(station -> station.crafts().stream()).anyMatch(c -> ("arksurvivalreturns:" + recipe).equals(c.variant().item()));
            h.assertTrue(h.getLevel().recipeAccess().byKey(key).isPresent() || inGraph, "Recipe is missing: " + recipe);
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
            // A checkmark quest is an introduction: a click pays nothing (A02 BUG-19); every other quest pays.
            boolean checkmark = quest.getTasks().stream().allMatch(task -> task.getType().getTypeId().getPath().equals("checkmark"));
            h.assertTrue(checkmark ? quest.getRewards().isEmpty() : !quest.getRewards().isEmpty(),
                    (checkmark ? "A checkmark quest pays a reward: " : "Quest has no rewards: ") + quest.getCodeString());
            for (var task : quest.getTasks()) {
                if (task instanceof ItemTask itemTask) {
                    h.assertFalse(itemTask.getItemStack().isEmpty(), "Unresolved task item: " + task.getCodeString());
                }
            }
        }
    }

    private JournalGameTests() {}
}
