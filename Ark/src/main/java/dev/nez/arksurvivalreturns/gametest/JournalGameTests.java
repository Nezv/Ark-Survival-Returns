package dev.nez.arksurvivalreturns.gametest;

import dev.ftb.mods.ftbquests.quest.ServerQuestFile;
import dev.ftb.mods.ftbquests.quest.task.ItemTask;
import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;

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

    private JournalGameTests() {}
}
