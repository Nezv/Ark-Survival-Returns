# Graph Report - Ark  (2026-09-06)

## Corpus Check
- 115 files · ~15,940 words
- Verdict: corpus is large enough that graph structure adds value.

## Summary
- 303 nodes · 620 edges · 19 communities (17 shown, 2 thin omitted)
- Extraction: 97% EXTRACTED · 3% INFERRED · 0% AMBIGUOUS · INFERRED: 16 edges (avg confidence: 0.84)
- Token cost: 0 input · 0 output

## Graph Freshness
- Built from commit: `4dfd8ef4`
- Run `git rev-parse HEAD` and compare to check if the graph is stale.
- Run `graphify update .` after code changes (no API cost).

## Community Hubs (Navigation)
- ModContent
- CreatureEntity
- ArkSurvivalReturns.java
- Species
- ArkData
- Config
- org.junit.jupiter.api.Test
- FollowPackGoal
- Ark Survival Returns
- ArkGameTests
- import_creatures.py
- build_test_structure.py
- gradlew
- AGENTS.md
- SpawnRules.java
- CreatureRenderer.java
- verification.md

## God Nodes (most connected - your core abstractions)
1. `CreatureEntity` - 42 edges
2. `Species` - 31 edges
3. `ModContent` - 19 edges
4. `ArkData` - 18 edges
5. `ArkSurvivalReturns` - 16 edges
6. `Config` - 15 edges
7. `ProgressionData` - 12 edges
8. `ArkGameTests` - 12 edges
9. `TargetHealthBar` - 11 edges
10. `BiomeTier` - 11 edges

## Surprising Connections (you probably didn't know these)
- `Config` --references--> `Species`  [EXTRACTED]
  src/main/java/dev/nez/arksurvivalreturns/Config.java → src/main/java/dev/nez/arksurvivalreturns/feature/creature/Species.java
- `CreatureModel` --references--> `CreatureEntity`  [EXTRACTED]
  src/main/java/dev/nez/arksurvivalreturns/client/CreatureModel.java → src/main/java/dev/nez/arksurvivalreturns/feature/creature/CreatureEntity.java
- `CreatureRenderer` --references--> `CreatureEntity`  [EXTRACTED]
  src/main/java/dev/nez/arksurvivalreturns/client/CreatureRenderer.java → src/main/java/dev/nez/arksurvivalreturns/feature/creature/CreatureEntity.java
- `CreatureEntity` --references--> `Species`  [EXTRACTED]
  src/main/java/dev/nez/arksurvivalreturns/feature/creature/CreatureEntity.java → src/main/java/dev/nez/arksurvivalreturns/feature/creature/Species.java
- `FollowPackGoal` --references--> `CreatureEntity`  [EXTRACTED]
  src/main/java/dev/nez/arksurvivalreturns/feature/creature/FollowPackGoal.java → src/main/java/dev/nez/arksurvivalreturns/feature/creature/CreatureEntity.java

## Import Cycles
- None detected.

## Communities (19 total, 2 thin omitted)

### Community 0 - "ModContent"
Cohesion: 0.21
Nodes (10): DeferredHolder, DeferredItem, Items, net.minecraft.world.entity.EntityType, net.minecraft.world.item.CreativeModeTab, net.minecraft.world.item.Item, net.minecraft.world.item.SpawnEggItem, net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent (+2 more)

### Community 1 - "CreatureEntity"
Cohesion: 0.10
Nodes (19): Builder, com.geckolib.animatable.GeoEntity, com.geckolib.animatable.instance.AnimatableInstanceCache, ControllerRegistrar, Entity, EntityDataAccessor, Goal, net.minecraft.world.DifficultyInstance (+11 more)

### Community 2 - "ArkSurvivalReturns.java"
Cohesion: 0.08
Nodes (23): net.minecraft.ChatFormatting, net.minecraft.server.level.ServerBossEvent, net.minecraft.server.level.ServerPlayer, net.neoforged.bus.api.IEventBus, net.neoforged.bus.api.SubscribeEvent, net.neoforged.fml.common.EventBusSubscriber, net.neoforged.fml.common.Mod, net.neoforged.fml.ModContainer (+15 more)

### Community 3 - "Species"
Cohesion: 0.14
Nodes (15): com.geckolib.model.GeoModel, com.geckolib.renderer.base.GeoRenderState, net.minecraft.resources.Identifier, CreatureModel, Override, Species, ARGENTAVIS, BRONTOSAURUS (+7 more)

### Community 4 - "ArkData"
Cohesion: 0.18
Nodes (7): CachedOutput, Client, DataProvider, JsonElement, PackOutput, ArkData, Override

### Community 5 - "Config"
Cohesion: 0.10
Nodes (16): BooleanValue, com.mojang.serialization.Codec, DoubleValue, IntValue, net.minecraft.world.level.saveddata.SavedData, net.minecraft.world.level.saveddata.SavedDataType, net.neoforged.neoforge.common.ModConfigSpec, net.neoforged.neoforge.event.server.ServerStartedEvent (+8 more)

### Community 6 - "org.junit.jupiter.api.Test"
Cohesion: 0.16
Nodes (5): org.junit.jupiter.api.Test, LevelScaling, DangerBands, LevelScalingTest, DangerBandsTest

### Community 7 - "FollowPackGoal"
Cohesion: 0.24
Nodes (3): net.minecraft.world.entity.ai.goal.Goal, FollowPackGoal, Override

### Community 8 - "Ark Survival Returns"
Cohesion: 0.20
Nodes (9): Ark Survival Returns, Berries, Build and verification, Configuration, Difficulty and levels, Play on Windows, Playable systems, Playtest checklist (+1 more)

### Community 9 - "ArkGameTests"
Cohesion: 0.27
Nodes (6): Block, Item, ItemStack, net.minecraft.gametest.framework.GameTestHelper, net.neoforged.neoforge.registries.DeferredRegister, ArkGameTests

### Community 10 - "import_creatures.py"
Cohesion: 0.16
Nodes (9): berry(), main(), Author crisp 32px item sprites from pixel shapes, and an inventory review sheet., main(), Import only runtime clips; normalize geometry and position tracks together.…, scale_track(), write(), Offline preview of the imported runtime resources, using the repository's… (+1 more)

### Community 11 - "build_test_structure.py"
Cohesion: 0.60
Nodes (5): main(), named(), Create the small empty structure required by the headless GameTests (NBT, no…, string(), write_structure()

### Community 12 - "gradlew"
Cohesion: 0.83
Nodes (3): gradlew script, die(), warn()

### Community 16 - "SpawnRules.java"
Cohesion: 0.13
Nodes (20): AABB, net.minecraft.core.BlockPos, net.minecraft.core.Holder, net.minecraft.server.level.ServerLevel, net.minecraft.tags.TagKey, net.minecraft.util.RandomSource, net.minecraft.world.entity.Entity, net.minecraft.world.entity.SpawnGroupData (+12 more)

### Community 17 - "CreatureRenderer.java"
Cohesion: 0.25
Nodes (8): com.geckolib.renderer.base.RenderPassInfo, com.geckolib.renderer.GeoEntityRenderer, com.mojang.blaze3d.vertex.PoseStack, Context, net.minecraft.client.renderer.entity.state.EntityRenderState, CreatureRenderer, Override, SuppressWarnings

## Knowledge Gaps
- **24 isolated node(s):** `PTERANODON`, `VELOCIRAPTOR`, `ARGENTAVIS`, `TRICERATOPS`, `THERIZINOSAURUS` (+19 more)
  These have ≤1 connection - possible missing edges or undocumented components.
- **2 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `CreatureEntity` connect `CreatureEntity` to `ModContent`, `ArkSurvivalReturns.java`, `Species`, `org.junit.jupiter.api.Test`, `FollowPackGoal`, `ArkGameTests`, `SpawnRules.java`, `CreatureRenderer.java`?**
  _High betweenness centrality (0.293) - this node is a cross-community bridge._
- **Why does `Species` connect `Species` to `ModContent`, `CreatureEntity`, `ArkSurvivalReturns.java`, `Config`, `FollowPackGoal`, `ArkGameTests`, `SpawnRules.java`, `CreatureRenderer.java`?**
  _High betweenness centrality (0.140) - this node is a cross-community bridge._
- **Why does `ArkData` connect `ArkData` to `ArkSurvivalReturns.java`?**
  _High betweenness centrality (0.094) - this node is a cross-community bridge._
- **What connects `PTERANODON`, `VELOCIRAPTOR`, `ARGENTAVIS` to the rest of the system?**
  _24 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `CreatureEntity` be split into smaller, more focused modules?**
  _Cohesion score 0.09523809523809523 - nodes in this community are weakly interconnected._
- **Should `ArkSurvivalReturns.java` be split into smaller, more focused modules?**
  _Cohesion score 0.0797872340425532 - nodes in this community are weakly interconnected._
- **Should `Species` be split into smaller, more focused modules?**
  _Cohesion score 0.14285714285714285 - nodes in this community are weakly interconnected._