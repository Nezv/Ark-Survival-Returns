# Graph Report - Ark  (2026-09-11)

## Corpus Check
- 176 files · ~78,026 words
- Verdict: corpus is large enough that graph structure adds value.

## Summary
- 827 nodes · 1924 edges · 47 communities (44 shown, 3 thin omitted)
- Extraction: 96% EXTRACTED · 4% INFERRED · 0% AMBIGUOUS · INFERRED: 84 edges (avg confidence: 0.83)
- Token cost: 0 input · 0 output

## Graph Freshness
- Built from commit: `ec0c975f`
- Run `git rev-parse HEAD` and compare to check if the graph is stale.
- Run `graphify update .` after code changes (no API cost).

## Community Hubs (Navigation)
- WildlifeGoal
- CreatureEntity
- DangerMapSync.java
- Species
- ArkData
- HabitatPayload
- org.junit.jupiter.api.Test
- net.minecraft.server.level.ServerLevel
- Ark Survival Returns
- BehaviorState
- import_creatures.py
- build_test_structure.py
- gradlew
- Land Ecosystem & Behavior Patch — proposal
- .canSpawn
- ProgressionData.java
- Verification — 2026-09-07
- net.minecraft.gametest.framework.GameTestHelper
- preview_danger_map.py
- Species.java
- NestBlock
- verify_client_pack.py
- ModContent
- NightEyesLayer.java
- net.minecraft.core.BlockPos
- BiomeAnnouncements
- ArkSurvivalReturns.java
- net.neoforged.bus.api.SubscribeEvent
- WildlifeSenses
- TargetHealthBar
- .spawn
- BiomeTier.java
- HabitatSync
- CreatureEntity.java
- Land Ecosystem & Behavior Patch: flying habitats
- Wildlife behavior: research and implemented model
- SpawnRules.java
- FollowPackGoal
- Nighttime patch proposal
- WildlifeGoal.java
- NighttimeCycle
- flying-ecosystem.md
- Optional client pack and difficulty map
- Flying Ecosystem — 2026-09-11
- MovementProbe

## God Nodes (most connected - your core abstractions)
1. `CreatureEntity` - 77 edges
2. `Species` - 53 edges
3. `FlyingCreatureEntity` - 37 edges
4. `WildlifeGoal` - 34 edges
5. `ModContent` - 27 edges
6. `ArkSurvivalReturns` - 24 edges
7. `BehaviorState` - 24 edges
8. `WildlifeMind` - 24 edges
9. `Config` - 23 edges
10. `ArkData` - 20 edges

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

## Communities (47 total, 3 thin omitted)

### Community 0 - "WildlifeGoal"
Cohesion: 0.18
Nodes (3): net.minecraft.world.entity.LivingEntity, Override, WildlifeGoal

### Community 1 - "CreatureEntity"
Cohesion: 0.10
Nodes (12): com.geckolib.animatable.instance.AnimatableInstanceCache, PathfinderMob, SoundEvent, CreatureEntity, BlockPos, BlockState, Builder, ControllerRegistrar (+4 more)

### Community 2 - "DangerMapSync.java"
Cohesion: 0.17
Nodes (8): CommandSourceStack, net.minecraft.server.level.ServerPlayer, net.neoforged.neoforge.event.RegisterCommandsEvent, net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent, DangerMapSync, PlayerChangedDimensionEvent, PlayerLoggedInEvent, PlayerRespawnEvent

### Community 3 - "Species"
Cohesion: 0.12
Nodes (13): net.minecraft.world.level.Level, EntityType, EntityType, Species, ARGENTAVIS, BRONTOSAURUS, GIGANOTOSAURUS, PTERANODON (+5 more)

### Community 4 - "ArkData"
Cohesion: 0.18
Nodes (7): CachedOutput, Client, DataProvider, JsonElement, PackOutput, ArkData, Override

### Community 5 - "HabitatPayload"
Cohesion: 0.06
Nodes (31): FunctionalInterface, io.github.billstark001.xaerobridge.api.MapOverlayContext, net.minecraft.client.gui.screens.Screen, net.minecraft.network.chat.Component, net.minecraft.network.codec.StreamCodec, net.minecraft.network.protocol.common.custom.CustomPacketPayload, net.minecraft.network.RegistryFriendlyByteBuf, net.neoforged.fml.event.lifecycle.FMLClientSetupEvent (+23 more)

### Community 6 - "org.junit.jupiter.api.Test"
Cohesion: 0.07
Nodes (12): org.junit.jupiter.api.Test, Observation, Routine, LevelScaling, MovementTuning, DangerBands, NighttimeMindTest, WildlifeMindTest (+4 more)

### Community 7 - "net.minecraft.server.level.ServerLevel"
Cohesion: 0.11
Nodes (18): net.minecraft.server.level.ServerLevel, FlyingCreatureEntity, Builder, ControllerRegistrar, DamageSource, EntityDataAccessor, Override, ValueInput (+10 more)

### Community 8 - "Ark Survival Returns"
Cohesion: 0.18
Nodes (11): Ark Survival Returns, Berries, Build and verification, Configuration, Difficulty and levels, Flying habitats and eggs, Play on Windows, Playable systems (+3 more)

### Community 9 - "BehaviorState"
Cohesion: 0.06
Nodes (20): alarm(), BehaviorState, ALERT, DEFEND, DRINK, FEED, FLEE, FORAGE (+12 more)

### Community 10 - "import_creatures.py"
Cohesion: 0.12
Nodes (14): berry(), main(), Author crisp 32px item sprites from pixel shapes, and an inventory review sheet., main(), Import only runtime clips; normalize geometry and position tracks together.…, Authored quiet standing sleep: stable feet, lowered head, closed eyes and slow…, scale_track(), sleep_pose() (+6 more)

### Community 11 - "build_test_structure.py"
Cohesion: 0.60
Nodes (5): main(), named(), Create the small empty structure required by the headless GameTests (NBT, no…, string(), write_structure()

### Community 12 - "gradlew"
Cohesion: 0.83
Nodes (3): gradlew script, die(), warn()

### Community 13 - "Land Ecosystem & Behavior Patch — proposal"
Cohesion: 0.14
Nodes (12): Ark Survival Returns, Design precedents and scope choices, Existing behavior compared with the request, Families and initial distances, Group decisions and satiation, Habitat lifecycle and save migration, Implementation and acceptance checks after feedback, Land Ecosystem & Behavior Patch — proposal (+4 more)

### Community 16 - ".canSpawn"
Cohesion: 0.25
Nodes (7): AABB, net.minecraft.util.RandomSource, Post, PopulationDirector, BlockPos, EntitySpawnReason, EntityType

### Community 17 - "ProgressionData.java"
Cohesion: 0.15
Nodes (6): net.minecraft.world.level.saveddata.SavedData, net.minecraft.world.level.saveddata.SavedDataType, net.minecraft.world.phys.AABB, net.neoforged.neoforge.event.server.ServerStartedEvent, MapUnlockData, ProgressionData

### Community 18 - "Verification — 2026-09-07"
Cohesion: 0.40
Nodes (4): Gameplay coverage, Map and client pack coverage, Verification — 2026-09-07, World compatibility and scope

### Community 19 - "net.minecraft.gametest.framework.GameTestHelper"
Cohesion: 0.24
Nodes (5): net.minecraft.gametest.framework.GameTestHelper, net.neoforged.neoforge.registries.DeferredRegister, ArkGameTests, ItemStack, NighttimeLoadProbe

### Community 20 - "preview_danger_map.py"
Cohesion: 0.67
Nodes (3): danger(), main(), Plot the production danger-region formula and its discrete area shares.

### Community 21 - "Species.java"
Cohesion: 0.17
Nodes (8): cohesionDistance(), defaultSprintRatio(), flyer(), groupHeightRange(), groupRadius(), herd(), restClip(), sleepClip()

### Community 22 - "NestBlock"
Cohesion: 0.14
Nodes (21): Block, BlockGetter, BlockHitResult, CollisionContext, com.mojang.serialization.MapCodec, Direction, InteractionHand, InteractionResult (+13 more)

### Community 24 - "ModContent"
Cohesion: 0.12
Nodes (18): Blocks, BooleanValue, DeferredBlock, DeferredHolder, DeferredItem, IntValue, Item, Items (+10 more)

### Community 25 - "NightEyesLayer.java"
Cohesion: 0.10
Nodes (19): com.geckolib.cache.model.GeoBone, com.geckolib.constant.dataticket.DataTicket, com.geckolib.renderer.base.RenderPassInfo, com.geckolib.renderer.GeoEntityRenderer, com.geckolib.renderer.layer.GeoRenderLayer, com.mojang.blaze3d.vertex.PoseStack, Context, GeoRenderer (+11 more)

### Community 26 - "net.minecraft.core.BlockPos"
Cohesion: 0.21
Nodes (6): com.mojang.serialization.Codec, net.minecraft.core.BlockPos, SavedData, Habitat, HabitatData, SavedDataType

### Community 27 - "BiomeAnnouncements"
Cohesion: 0.15
Nodes (11): com.geckolib.model.GeoModel, com.geckolib.renderer.base.GeoRenderState, net.minecraft.ChatFormatting, net.minecraft.resources.Identifier, CreatureModel, Override, BiomeAnnouncements, PlayerLoggedOutEvent (+3 more)

### Community 28 - "ArkSurvivalReturns.java"
Cohesion: 0.21
Nodes (8): net.neoforged.bus.api.IEventBus, net.neoforged.fml.common.EventBusSubscriber, net.neoforged.fml.common.Mod, net.neoforged.fml.ModContainer, net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent, ArkSurvivalReturns, ArkClient, HabitatMapClient

### Community 29 - "net.neoforged.bus.api.SubscribeEvent"
Cohesion: 0.18
Nodes (7): net.neoforged.bus.api.SubscribeEvent, net.neoforged.neoforge.event.entity.player.AttackEntityEvent, net.neoforged.neoforge.event.level.block.BreakBlockEvent, net.neoforged.neoforge.event.server.ServerStoppedEvent, RegisterRenderers, LoggingOut, WildlifeNoise

### Community 30 - "WildlifeSenses"
Cohesion: 0.19
Nodes (5): net.minecraft.world.phys.Vec3, Noise, Detection, WildlifeSenses, NighttimeGameTests

### Community 31 - "TargetHealthBar"
Cohesion: 0.18
Nodes (7): net.minecraft.server.level.ServerBossEvent, ServerBossEvent, PlayerChangedDimensionEvent, PlayerLoggedOutEvent, PlayerRespawnEvent, Post, TargetHealthBar

### Community 32 - ".spawn"
Cohesion: 0.30
Nodes (3): FlyerHabitats, BlockPos, FlyingGameTests

### Community 33 - "BiomeTier.java"
Cohesion: 0.20
Nodes (10): net.minecraft.core.Holder, net.minecraft.world.level.biome.Biome, at(), BiomeTier, EASY, EXTREME, HARD, MODERATE (+2 more)

### Community 34 - "HabitatSync"
Cohesion: 0.20
Nodes (6): HabitatSync, PlayerChangedDimensionEvent, PlayerLoggedInEvent, PlayerLoggedOutEvent, PlayerRespawnEvent, Post

### Community 35 - "CreatureEntity.java"
Cohesion: 0.22
Nodes (8): com.geckolib.animatable.GeoEntity, Goal, net.minecraft.world.DifficultyInstance, net.minecraft.world.level.ServerLevelAccessor, SpawnGroupData, Entity, EntitySpawnReason, PackData

### Community 36 - "Land Ecosystem & Behavior Patch: flying habitats"
Cohesion: 0.18
Nodes (11): Current implementation versus requested behavior, Design precedents, Egg-taking contract, Habitat and nest defaults, Implementation and validation sequence, Land Ecosystem & Behavior Patch: flying habitats, Performance estimates and bounds, Phantom and extracted-animation reuse (+3 more)

### Community 37 - "Wildlife behavior: research and implemented model"
Cohesion: 0.20
Nodes (10): Acceptance evidence, ARK methods, properties and registration concepts, Bounded simulation and limits, Difficulty and apex correction, Implemented behavior contract, Main finding, Player-facing development priorities after this model, What I inspected in the installed ARK files (+2 more)

### Community 38 - "SpawnRules.java"
Cohesion: 0.29
Nodes (6): net.minecraft.tags.TagKey, net.minecraft.world.level.block.Block, net.minecraft.world.level.block.Blocks, net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent, Entity, SpawnRules

### Community 39 - "FollowPackGoal"
Cohesion: 0.31
Nodes (3): net.minecraft.world.entity.ai.goal.Goal, FollowPackGoal, Override

### Community 40 - "Nighttime patch proposal"
Cohesion: 0.22
Nodes (8): Behavior rules and implementation boundaries, Existing foundation, Lessons from other projects, Mechanics to skip or defer, Nighttime patch proposal, Performance validation after direction feedback, Recommended mechanics, Species scope

### Community 41 - "WildlifeGoal.java"
Cohesion: 0.29
Nodes (3): net.minecraft.world.level.storage.ValueInput, net.minecraft.world.level.storage.ValueOutput, BlockPos

### Community 44 - "Optional client pack and difficulty map"
Cohesion: 0.33
Nodes (6): Difficulty map and unlock, Flying habitat markers, Installation and reproducibility, Iris development-run crash workaround, Optional client pack and difficulty map, Verification and playtest

### Community 45 - "Flying Ecosystem — 2026-09-11"
Cohesion: 0.33
Nodes (6): Animation and map assets, Behavior, Flying Ecosystem — 2026-09-11, Placement and persistence, Validation, Work limits

### Community 46 - "MovementProbe"
Cohesion: 0.40
Nodes (3): ServerLevel, Vec3, MovementProbe

## Knowledge Gaps
- **97 isolated node(s):** `ROAM`, `FORAGE`, `DRINK`, `SEEK_WATER`, `REST` (+92 more)
  These have ≤1 connection - possible missing edges or undocumented components.
- **3 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `CreatureEntity` connect `CreatureEntity` to `WildlifeGoal`, `Species`, `org.junit.jupiter.api.Test`, `net.minecraft.server.level.ServerLevel`, `.canSpawn`, `net.minecraft.gametest.framework.GameTestHelper`, `ModContent`, `NightEyesLayer.java`, `net.minecraft.core.BlockPos`, `BiomeAnnouncements`, `net.neoforged.bus.api.SubscribeEvent`, `WildlifeSenses`, `TargetHealthBar`, `.spawn`, `CreatureEntity.java`, `SpawnRules.java`, `FollowPackGoal`, `WildlifeGoal.java`, `MovementProbe`?**
  _High betweenness centrality (0.202) - this node is a cross-community bridge._
- **Why does `Species` connect `Species` to `WildlifeGoal`, `CreatureEntity`, `.spawn`, `BiomeTier.java`, `HabitatPayload`, `org.junit.jupiter.api.Test`, `SpawnRules.java`, `WildlifeGoal.java`, `MovementProbe`, `.canSpawn`, `net.minecraft.gametest.framework.GameTestHelper`, `Species.java`, `ModContent`, `NightEyesLayer.java`, `net.minecraft.core.BlockPos`, `BiomeAnnouncements`, `ArkSurvivalReturns.java`, `WildlifeSenses`?**
  _High betweenness centrality (0.112) - this node is a cross-community bridge._
- **Why does `WildlifeGoal` connect `WildlifeGoal` to `CreatureEntity`, `FollowPackGoal`, `net.minecraft.server.level.ServerLevel`, `WildlifeGoal.java`, `BehaviorState`, `net.minecraft.core.BlockPos`, `WildlifeSenses`?**
  _High betweenness centrality (0.056) - this node is a cross-community bridge._
- **What connects `ROAM`, `FORAGE`, `DRINK` to the rest of the system?**
  _97 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `CreatureEntity` be split into smaller, more focused modules?**
  _Cohesion score 0.10084033613445378 - nodes in this community are weakly interconnected._
- **Should `Species` be split into smaller, more focused modules?**
  _Cohesion score 0.125 - nodes in this community are weakly interconnected._
- **Should `HabitatPayload` be split into smaller, more focused modules?**
  _Cohesion score 0.05563093622795115 - nodes in this community are weakly interconnected._