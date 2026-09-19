# Graph Report - Ark  (2026-09-05)

## Corpus Check
- 113 files · ~9,371 words
- Verdict: corpus is large enough that graph structure adds value.

## Summary
- 226 nodes · 417 edges · 17 communities (16 shown, 1 thin omitted)
- Extraction: 99% EXTRACTED · 1% INFERRED · 0% AMBIGUOUS · INFERRED: 4 edges (avg confidence: 0.8)
- Token cost: 0 input · 0 output

## Graph Freshness
- Built from commit: `4dfd8ef4`
- Run `git rev-parse HEAD` and compare to check if the graph is stale.
- Run `graphify update .` after code changes (no API cost).

## Community Hubs (Navigation)
- SpawnRules.java
- CreatureEntity
- TargetHealthBar
- Species
- ArkData
- Config
- .initializeLevel
- FollowPackGoal
- Ark Survival Returns
- ArkGameTests
- import_creatures.py
- build_test_structure.py
- gradlew
- AGENTS.md
- ModContent

## God Nodes (most connected - your core abstractions)
1. `CreatureEntity` - 37 edges
2. `Species` - 22 edges
3. `ArkData` - 18 edges
4. `ModContent` - 18 edges
5. `ArkSurvivalReturns` - 13 edges
6. `TargetHealthBar` - 11 edges
7. `Config` - 10 edges
8. `ArkGameTests` - 10 edges
9. `FollowPackGoal` - 9 edges
10. `BiomeTier` - 9 edges

## Surprising Connections (you probably didn't know these)
- `CreatureModel` --references--> `CreatureEntity`  [EXTRACTED]
  src/main/java/dev/nez/arksurvivalreturns/client/CreatureModel.java → src/main/java/dev/nez/arksurvivalreturns/feature/creature/CreatureEntity.java
- `CreatureEntity` --references--> `Species`  [EXTRACTED]
  src/main/java/dev/nez/arksurvivalreturns/feature/creature/CreatureEntity.java → src/main/java/dev/nez/arksurvivalreturns/feature/creature/Species.java
- `FollowPackGoal` --references--> `CreatureEntity`  [EXTRACTED]
  src/main/java/dev/nez/arksurvivalreturns/feature/creature/FollowPackGoal.java → src/main/java/dev/nez/arksurvivalreturns/feature/creature/CreatureEntity.java
- `ModContent` --references--> `CreatureEntity`  [EXTRACTED]
  src/main/java/dev/nez/arksurvivalreturns/registry/ModContent.java → src/main/java/dev/nez/arksurvivalreturns/feature/creature/CreatureEntity.java
- `ModContent` --references--> `Species`  [EXTRACTED]
  src/main/java/dev/nez/arksurvivalreturns/registry/ModContent.java → src/main/java/dev/nez/arksurvivalreturns/feature/creature/Species.java

## Import Cycles
- None detected.

## Communities (17 total, 1 thin omitted)

### Community 0 - "SpawnRules.java"
Cohesion: 0.13
Nodes (17): Block, Item, net.minecraft.core.BlockPos, net.minecraft.core.Holder, net.minecraft.tags.TagKey, net.minecraft.util.RandomSource, net.minecraft.world.level.biome.Biome, net.minecraft.world.level.block.Block (+9 more)

### Community 1 - "CreatureEntity"
Cohesion: 0.10
Nodes (20): Builder, com.geckolib.animatable.GeoEntity, com.geckolib.animatable.instance.AnimatableInstanceCache, ControllerRegistrar, Entity, EntityDataAccessor, Goal, net.minecraft.server.level.ServerLevel (+12 more)

### Community 2 - "TargetHealthBar"
Cohesion: 0.14
Nodes (13): net.minecraft.server.level.ServerBossEvent, net.minecraft.server.level.ServerPlayer, net.neoforged.bus.api.SubscribeEvent, net.neoforged.fml.common.EventBusSubscriber, net.neoforged.neoforge.event.server.ServerStoppedEvent, PlayerChangedDimensionEvent, PlayerLoggedOutEvent, PlayerRespawnEvent (+5 more)

### Community 3 - "Species"
Cohesion: 0.14
Nodes (15): com.geckolib.model.GeoModel, com.geckolib.renderer.base.GeoRenderState, net.minecraft.resources.Identifier, CreatureModel, Override, Species, ARGENTAVIS, BRONTOSAURUS (+7 more)

### Community 4 - "ArkData"
Cohesion: 0.19
Nodes (7): CachedOutput, Client, DataProvider, JsonElement, PackOutput, ArkData, Override

### Community 5 - "Config"
Cohesion: 0.20
Nodes (10): BooleanValue, DoubleValue, IntValue, net.neoforged.neoforge.common.ModConfigSpec, Config, BiomeTier, EASY, EXTREME (+2 more)

### Community 6 - ".initializeLevel"
Cohesion: 0.31
Nodes (3): org.junit.jupiter.api.Test, LevelScaling, LevelScalingTest

### Community 7 - "FollowPackGoal"
Cohesion: 0.24
Nodes (3): net.minecraft.world.entity.ai.goal.Goal, FollowPackGoal, Override

### Community 8 - "Ark Survival Returns"
Cohesion: 0.22
Nodes (8): Ark Survival Returns, Berries, Build and verification, Configuration and customization, Difficulty and level balance, First playable systems, Playtest checklist, Spawn rules

### Community 9 - "ArkGameTests"
Cohesion: 0.36
Nodes (4): ItemStack, net.minecraft.gametest.framework.GameTestHelper, net.neoforged.neoforge.registries.DeferredRegister, ArkGameTests

### Community 10 - "import_creatures.py"
Cohesion: 0.21
Nodes (8): berry(), main(), Author crisp 32px item sprites from pixel shapes, and an inventory review sheet., main(), Import only runtime clips; normalize geometry and position tracks together.…, scale_track(), write(), Validate the actual packaged asset graph and read-only creature import…

### Community 11 - "build_test_structure.py"
Cohesion: 0.70
Nodes (4): main(), named(), Create the small empty structure required by the headless GameTests (NBT, no…, string()

### Community 12 - "gradlew"
Cohesion: 0.83
Nodes (3): gradlew script, die(), warn()

### Community 16 - "ModContent"
Cohesion: 0.21
Nodes (10): DeferredHolder, DeferredItem, Items, net.minecraft.world.entity.EntityType, net.minecraft.world.item.CreativeModeTab, net.minecraft.world.item.Item, net.minecraft.world.item.SpawnEggItem, net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent (+2 more)

## Knowledge Gaps
- **21 isolated node(s):** `PTERANODON`, `VELOCIRAPTOR`, `ARGENTAVIS`, `TRICERATOPS`, `THERIZINOSAURUS` (+16 more)
  These have ≤1 connection - possible missing edges or undocumented components.
- **1 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `CreatureEntity` connect `CreatureEntity` to `SpawnRules.java`, `TargetHealthBar`, `Species`, `.initializeLevel`, `FollowPackGoal`, `ArkGameTests`, `ModContent`?**
  _High betweenness centrality (0.339) - this node is a cross-community bridge._
- **Why does `Species` connect `Species` to `SpawnRules.java`, `CreatureEntity`, `FollowPackGoal`, `ArkGameTests`, `ModContent`?**
  _High betweenness centrality (0.130) - this node is a cross-community bridge._
- **Why does `ArkData` connect `ArkData` to `SpawnRules.java`, `TargetHealthBar`?**
  _High betweenness centrality (0.123) - this node is a cross-community bridge._
- **What connects `PTERANODON`, `VELOCIRAPTOR`, `ARGENTAVIS` to the rest of the system?**
  _21 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `SpawnRules.java` be split into smaller, more focused modules?**
  _Cohesion score 0.13227513227513227 - nodes in this community are weakly interconnected._
- **Should `CreatureEntity` be split into smaller, more focused modules?**
  _Cohesion score 0.09682539682539683 - nodes in this community are weakly interconnected._
- **Should `TargetHealthBar` be split into smaller, more focused modules?**
  _Cohesion score 0.13666666666666666 - nodes in this community are weakly interconnected._