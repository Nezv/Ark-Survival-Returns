package dev.nez.arksurvivalreturns.gametest;

import java.util.UUID;
import com.mojang.authlib.GameProfile;
import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import dev.nez.arksurvivalreturns.feature.creature.CreatureEntity;
import dev.nez.arksurvivalreturns.feature.creature.CreatureVariants;
import dev.nez.arksurvivalreturns.feature.creature.Species;
import dev.nez.arksurvivalreturns.feature.testground.SpecimenPostBlockEntity;
import dev.nez.arksurvivalreturns.feature.testground.SpecimenPostBlockEntity.Action;
import dev.nez.arksurvivalreturns.feature.testground.SpecimenPostBlockEntity.Mode;
import dev.nez.arksurvivalreturns.feature.testground.TestGround;
import dev.nez.arksurvivalreturns.feature.testground.TestGroundContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

/** The testing ground: its world type and biome load, and a Specimen Post puts out, freezes, times and tames its creature. */
final class TestGroundGameTests {
    static void post(GameTestHelper h) {
        var world = h.getLevel();
        var biomes = world.registryAccess().lookupOrThrow(Registries.BIOME);
        h.assertTrue(biomes.get(TestGround.BIOME).isPresent(), "The testing ground biome did not load");
        var presets = world.registryAccess().lookupOrThrow(Registries.WORLD_PRESET);
        var preset = presets.get(ResourceKey.create(Registries.WORLD_PRESET, ArkSurvivalReturns.id("testing_ground")));
        h.assertTrue(preset.isPresent(), "The testing ground world type did not load");
        h.assertTrue(preset.get().is(TagKey.create(Registries.WORLD_PRESET, net.minecraft.resources.Identifier.withDefaultNamespace("normal"))),
                "The testing ground is not offered on the Create World screen");
        for (var category : net.minecraft.world.entity.MobCategory.values())
            h.assertTrue(biomes.getOrThrow(TestGround.BIOME).value().getMobSettings().getMobs(category).isEmpty(),
                    "The testing ground biome spawns " + category.getName());

        BlockPos local = new BlockPos(16, 3, 16);
        for (int x = -3; x <= 3; x++) for (int z = -3; z <= 3; z++) h.setBlock(local.offset(x, -1, z), Blocks.STONE);
        h.setBlock(local, TestGroundContent.SPECIMEN_POST.get());
        BlockPos pos = h.absolutePos(local);
        if (!(world.getBlockEntity(pos) instanceof SpecimenPostBlockEntity post)) { h.fail("No post block entity"); return; }
        var owner = FakePlayerFactory.get(world, new GameProfile(UUID.randomUUID(), "ArkSpecimenKeeper"));
        post.configure(Species.VELOCIRAPTOR, 3, 5, Mode.FROZEN, 80, false);
        post.act(Action.SPAWN, owner);
        var first = specimen(h, pos);
        h.assertTrue(first != null, "The post put out no specimen");
        h.assertTrue(CreatureVariants.pick(Species.VELOCIRAPTOR, first.getUUID()) == 3, "The specimen is not in the chosen colour");
        h.assertTrue(first.isNoAi() && first.creatureLevel() == 5, "A frozen level-5 specimen came out with AI " + !first.isNoAi() + ", level " + first.creatureLevel());
        h.assertTrue(Math.abs(first.wildlife().mind().hunger() - 0.8) < 1e-6, "The specimen's hunger is not the post's");
        post.act(Action.KNOCK_OUT, owner);
        h.runAfterDelay(3, () -> {
            h.assertTrue(first.torpor().restricted(), "Knock out did not put the specimen down: " + first.torpor().phase());
            post.act(Action.TAME, owner);
            h.assertTrue(first.isTamed(), "Tame did not tame the specimen");
            h.runAfterDelay(2, () -> {
                h.assertTrue(post.reports().stream().anyMatch(line -> line.contains("went down")), "The post did not time the knock-out: " + post.reports());
                h.assertTrue(post.reports().stream().anyMatch(line -> line.contains("tamed")), "The post did not time the tame: " + post.reports());
                post.act(Action.SPAWN, owner);
                h.assertTrue(first.isRemoved(), "A new specimen did not replace the old one");
                var second = specimen(h, pos);
                h.assertTrue(second != null && second != first, "No second specimen");
                second.hurtServer(world, world.damageSources().generic(), 2f);
                h.runAfterDelay(3, () -> {
                    post.act(Action.KILL, owner);
                    h.runAfterDelay(40, () -> {
                        h.assertTrue(post.reports().stream().anyMatch(line -> line.contains("died") && line.contains("after the first hit")),
                                "The post did not time the death: " + post.reports());
                        h.assertTrue(post.reports().stream().anyMatch(line -> line.contains("death animation")),
                                "The post did not time the body's removal: " + post.reports());
                        h.assertTrue(!post.hasSpecimen(), "The post still holds a dead specimen");
                        h.setBlock(local, Blocks.AIR);
                        fight(h, owner);
                    });
                });
            });
        });
    }

    /** Fight sets two rooted specimens of one kind, which would never hunt each other, on each other: one is hurt. */
    private static void fight(GameTestHelper h, net.minecraft.world.entity.player.Player owner) {
        var world = h.getLevel();
        BlockPos[] spots = {new BlockPos(11, 3, 16), new BlockPos(21, 3, 16)};
        for (int x = 7; x <= 25; x++) for (int z = 11; z <= 21; z++) h.setBlock(new BlockPos(x, 2, z), Blocks.STONE);
        var posts = new SpecimenPostBlockEntity[2];
        for (int i = 0; i < 2; i++) {
            h.setBlock(spots[i], TestGroundContent.SPECIMEN_POST.get());
            if (!(world.getBlockEntity(h.absolutePos(spots[i])) instanceof SpecimenPostBlockEntity post)) { h.fail("No post block entity"); return; }
            post.configure(Species.VELOCIRAPTOR, i, 1, Mode.ROOTED, 30, false);
            post.act(Action.SPAWN, owner);
            posts[i] = post;
        }
        var fighters = new CreatureEntity[] {specimen(h, h.absolutePos(spots[0])), specimen(h, h.absolutePos(spots[1]))};
        h.assertTrue(fighters[0] != null && fighters[1] != null && fighters[0] != fighters[1], "The two posts did not put out two specimens");
        h.runAfterDelay(20, () -> {
            for (int i = 0; i < 2; i++)
                h.assertTrue(fighters[i].position().distanceToSqr(net.minecraft.world.phys.Vec3.atBottomCenterOf(h.absolutePos(spots[i]).above())) < 0.5,
                        "A rooted specimen left its post");
            h.assertTrue(fighters[0].getHealth() == fighters[0].getMaxHealth() && fighters[1].getHealth() == fighters[1].getMaxHealth(),
                    "Rooted specimens fought before Fight");
            posts[0].act(Action.FIGHT, owner);
            h.assertTrue(posts[0].mode() == Mode.FREE && posts[1].mode() == Mode.FREE, "Fight did not let both specimens go");
            h.succeedWhen(() -> h.assertTrue(fighters[0].getHealth() < fighters[0].getMaxHealth() || fighters[1].getHealth() < fighters[1].getMaxHealth()
                    || !fighters[0].isAlive() || !fighters[1].isAlive(), "Fight: neither specimen was hurt"));
        });
    }

    private static CreatureEntity specimen(GameTestHelper h, BlockPos pos) {
        return h.getLevel().getEntitiesOfClass(CreatureEntity.class, new AABB(pos).inflate(4),
                c -> c.isAlive() && c.entityTags().contains(SpecimenPostBlockEntity.TAG)).stream().findFirst().orElse(null);
    }

    private TestGroundGameTests() {}
}
