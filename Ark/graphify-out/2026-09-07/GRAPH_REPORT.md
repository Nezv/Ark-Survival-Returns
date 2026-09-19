# Graph Report - Ark  (2026-09-07)

## Corpus Check
- 142 files · ~56,449 words
- Verdict: corpus is large enough that graph structure adds value.

## Summary
- 560 nodes · 1201 edges · 23 communities (21 shown, 2 thin omitted)
- Extraction: 97% EXTRACTED · 3% INFERRED · 0% AMBIGUOUS · INFERRED: 42 edges (avg confidence: 0.82)
- Token cost: 0 input · 0 output

## Graph Freshness
- Built from commit: `4dfd8ef4`
- Run `git rev-parse HEAD` and compare to check if the graph is stale.
- Run `graphify update .` after code changes (no API cost).

## Community Hubs (Navigation)
- WildlifeGoal
- CreatureEntity
- net.neoforged.bus.api.SubscribeEvent
- Species
- ArkData
- DangerMapPayload
- org.junit.jupiter.api.Test
- FollowPackGoal
- Wildlife behavior: research and implemented model
- BehaviorState
- import_creatures.py
- build_test_structure.py
- gradlew
- AGENTS.md
- net.minecraft.server.level.ServerLevel
- ProgressionData.java
- Verification — 2026-09-07
- ArkGameTests
- preview_danger_map.py
- ArkSurvivalReturns.java
- verify_client_pack.py

## God Nodes (most connected - your core abstractions)
1. `CreatureEntity` - 63 edges
2. `Species` - 40 edges
3. `WildlifeGoal` - 32 edges
4. `ArkSurvivalReturns` - 21 edges
5. `BehaviorState` - 20 edges
6. `WildlifeMind` - 19 edges
7. `ModContent` - 19 edges
8. `ArkData` - 18 edges
9. `Config` - 16 edges
10. `ArkGameTests` - 14 edges

## Surprising Connections (you probably didn't know these)
- `Config` --references--> `Species`  [EXTRACTED]
  src/main/java/dev/nez/arksurvivalreturns/Config.java → src/main/java/dev/nez/arksurvivalreturns/feature/creature/Species.java
- `Config` --references--> `BiomeTier`  [EXTRACTED]
  src/main/java/dev/nez/arksurvivalreturns/Config.java → src/main/java/dev/nez/arksurvivalreturns/feature/spawn/BiomeTier.java
- `CreatureModel` --references--> `CreatureEntity`  [EXTRACTED]
  src/main/java/dev/nez/arksurvivalreturns/client/CreatureModel.java → src/main/java/dev/nez/arksurvivalreturns/feature/creature/CreatureEntity.java
- `CreatureModel` --references--> `Species`  [EXTRACTED]
  src/main/java/dev/nez/arksurvivalreturns/client/CreatureModel.java → src/main/java/dev/nez/arksurvivalreturns/feature/creature/Species.java
- `CreatureRenderer` --references--> `CreatureEntity`  [EXTRACTED]
  src/main/java/dev/nez/arksurvivalreturns/client/CreatureRenderer.java → src/main/java/dev/nez/arksurvivalreturns/feature/creature/CreatureEntity.java

## Import Cycles
- None detected.

## Communities (23 total, 2 thin omitted)

### Community 0 - "WildlifeGoal"
Cohesion: 0.09
Nodes (12): net.minecraft.world.entity.LivingEntity, net.minecraft.world.entity.player.Player, net.minecraft.world.level.storage.ValueOutput, net.minecraft.world.phys.Vec3, net.neoforged.neoforge.event.entity.player.AttackEntityEvent, net.neoforged.neoforge.event.level.block.BreakBlockEvent, Override, WildlifeGoal (+4 more)

### Community 1 - "CreatureEntity"
Cohesion: 0.06
Nodes (27): BlockState, Builder, com.geckolib.animatable.GeoEntity, com.geckolib.animatable.instance.AnimatableInstanceCache, ControllerRegistrar, DamageSource, Entity, EntityDataAccessor (+19 more)

### Community 2 - "net.neoforged.bus.api.SubscribeEvent"
Cohesion: 0.06
Nodes (31): BooleanValue, CommandSourceStack, DoubleValue, IntValue, net.minecraft.ChatFormatting, net.minecraft.server.level.ServerBossEvent, net.minecraft.server.level.ServerPlayer, net.neoforged.bus.api.SubscribeEvent (+23 more)

### Community 3 - "Species"
Cohesion: 0.07
Nodes (31): com.geckolib.renderer.base.RenderPassInfo, com.geckolib.renderer.GeoEntityRenderer, com.mojang.blaze3d.vertex.PoseStack, Context, DeferredHolder, DeferredItem, Items, net.minecraft.client.renderer.entity.state.EntityRenderState (+23 more)

### Community 4 - "ArkData"
Cohesion: 0.18
Nodes (7): CachedOutput, Client, DataProvider, JsonElement, PackOutput, ArkData, Override

### Community 5 - "DangerMapPayload"
Cohesion: 0.06
Nodes (26): FunctionalInterface, io.github.billstark001.xaerobridge.api.MapOverlayContext, LoggingOut, net.minecraft.client.gui.screens.Screen, net.minecraft.network.chat.Component, net.minecraft.network.codec.StreamCodec, net.minecraft.network.protocol.common.custom.CustomPacketPayload, net.minecraft.network.RegistryFriendlyByteBuf (+18 more)

### Community 6 - "org.junit.jupiter.api.Test"
Cohesion: 0.14
Nodes (6): org.junit.jupiter.api.Test, Observation, LevelScaling, WildlifeMindTest, LevelScalingTest, DangerBandsTest

### Community 7 - "FollowPackGoal"
Cohesion: 0.31
Nodes (3): net.minecraft.world.entity.ai.goal.Goal, FollowPackGoal, Override

### Community 8 - "Wildlife behavior: research and implemented model"
Cohesion: 0.07
Nodes (26): Acceptance evidence, ARK methods, properties and registration concepts, Bounded simulation and limits, Difficulty and apex correction, Implemented behavior contract, Main finding, Player-facing development priorities after this model, What I inspected in the installed ARK files (+18 more)

### Community 9 - "BehaviorState"
Cohesion: 0.07
Nodes (17): alarm(), BehaviorState, ALERT, DEFEND, DRINK, FEED, FLEE, FORAGE (+9 more)

### Community 10 - "import_creatures.py"
Cohesion: 0.13
Nodes (12): berry(), main(), Author crisp 32px item sprites from pixel shapes, and an inventory review sheet., main(), Import only runtime clips; normalize geometry and position tracks together.…, scale_track(), write(), main() (+4 more)

### Community 11 - "build_test_structure.py"
Cohesion: 0.60
Nodes (5): main(), named(), Create the small empty structure required by the headless GameTests (NBT, no…, string(), write_structure()

### Community 12 - "gradlew"
Cohesion: 0.83
Nodes (3): gradlew script, die(), warn()

### Community 16 - "net.minecraft.server.level.ServerLevel"
Cohesion: 0.10
Nodes (26): AABB, net.minecraft.core.BlockPos, net.minecraft.core.Holder, net.minecraft.server.level.ServerLevel, net.minecraft.tags.TagKey, net.minecraft.util.RandomSource, net.minecraft.world.entity.Entity, net.minecraft.world.entity.SpawnGroupData (+18 more)

### Community 17 - "ProgressionData.java"
Cohesion: 0.17
Nodes (6): com.mojang.serialization.Codec, net.minecraft.world.level.saveddata.SavedData, net.minecraft.world.level.saveddata.SavedDataType, net.neoforged.neoforge.event.server.ServerStartedEvent, MapUnlockData, ProgressionData

### Community 18 - "Verification — 2026-09-07"
Cohesion: 0.40
Nodes (4): Gameplay coverage, Map and client pack coverage, Verification — 2026-09-07, World compatibility and scope

### Community 19 - "ArkGameTests"
Cohesion: 0.26
Nodes (6): Block, Item, ItemStack, net.minecraft.gametest.framework.GameTestHelper, net.neoforged.neoforge.registries.DeferredRegister, ArkGameTests

### Community 20 - "preview_danger_map.py"
Cohesion: 0.67
Nodes (3): danger(), main(), Plot the production danger-region formula and its discrete area shares.

### Community 21 - "ArkSurvivalReturns.java"
Cohesion: 0.10
Nodes (15): com.geckolib.model.GeoModel, com.geckolib.renderer.base.GeoRenderState, net.minecraft.resources.Identifier, net.neoforged.bus.api.IEventBus, net.neoforged.fml.common.Mod, net.neoforged.fml.ModContainer, ArkSurvivalReturns, CreatureModel (+7 more)

## Knowledge Gaps
- **53 isolated node(s):** `ROAM`, `FORAGE`, `DRINK`, `SEEK_WATER`, `REST` (+48 more)
  These have ≤1 connection - possible missing edges or undocumented components.
- **2 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `CreatureEntity` connect `CreatureEntity` to `WildlifeGoal`, `net.neoforged.bus.api.SubscribeEvent`, `Species`, `org.junit.jupiter.api.Test`, `FollowPackGoal`, `BehaviorState`, `net.minecraft.server.level.ServerLevel`, `ArkGameTests`, `ArkSurvivalReturns.java`?**
  _High betweenness centrality (0.268) - this node is a cross-community bridge._
- **Why does `Species` connect `Species` to `WildlifeGoal`, `CreatureEntity`, `net.neoforged.bus.api.SubscribeEvent`, `net.minecraft.server.level.ServerLevel`, `ArkGameTests`, `ArkSurvivalReturns.java`?**
  _High betweenness centrality (0.110) - this node is a cross-community bridge._
- **Why does `WildlifeGoal` connect `WildlifeGoal` to `net.minecraft.server.level.ServerLevel`, `CreatureEntity`, `BehaviorState`, `FollowPackGoal`?**
  _High betweenness centrality (0.076) - this node is a cross-community bridge._
- **What connects `ROAM`, `FORAGE`, `DRINK` to the rest of the system?**
  _53 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `WildlifeGoal` be split into smaller, more focused modules?**
  _Cohesion score 0.09393939393939393 - nodes in this community are weakly interconnected._
- **Should `CreatureEntity` be split into smaller, more focused modules?**
  _Cohesion score 0.055523085914669784 - nodes in this community are weakly interconnected._
- **Should `net.neoforged.bus.api.SubscribeEvent` be split into smaller, more focused modules?**
  _Cohesion score 0.05952380952380952 - nodes in this community are weakly interconnected._