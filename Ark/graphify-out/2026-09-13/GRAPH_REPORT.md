# Graph Report - Ark  (2026-09-13)

## Corpus Check
- 256 files · ~111,233 words
- Verdict: corpus is large enough that graph structure adds value.

## Summary
- 1027 nodes · 2483 edges · 52 communities (48 shown, 4 thin omitted)
- Extraction: 96% EXTRACTED · 4% INFERRED · 0% AMBIGUOUS · INFERRED: 106 edges (avg confidence: 0.82)
- Token cost: 0 input · 0 output

## Graph Freshness
- Built from commit: `44e3a2fa`
- Run `git rev-parse HEAD` and compare to check if the graph is stale.
- Run `graphify update .` after code changes (no API cost).

## Community Hubs (Navigation)
- .species
- CreatureEntity
- DangerMapSync.java
- DinoDebugGameTests.java
- ArkData
- XaeroDangerOverlay
- .level
- FlyingCreatureEntity
- Ark Survival Returns
- BehaviorState
- import_creatures.py
- build_test_structure.py
- gradlew
- Land Ecosystem & Behavior Patch — proposal
- net.minecraft.server.level.ServerLevel
- ProgressionData.java
- flying-ecosystem.md
- net.minecraft.gametest.framework.GameTestHelper
- preview_danger_map.py
- Species.java
- NestBlock
- verify_client_pack.py
- ModContent
- NightEyesLayer.java
- net.minecraft.core.BlockPos
- DinoDebugClient
- ArkSurvivalReturns.java
- net.minecraft.world.phys.Vec3
- XaeroHabitatOverlay
- TargetHealthBar
- XaeroDangerOverlay.java
- HabitatPayload
- net.neoforged.bus.api.SubscribeEvent
- SpawnRules.java
- Land Ecosystem & Behavior Patch: flying habitats
- Wildlife behavior: research and implemented model
- org.junit.jupiter.api.Test
- FollowPackGoal
- Nighttime patch proposal
- WildlifeGoal
- DinoDebugSync
- Creature expansion — 2026-09-12
- Optional client pack and difficulty map
- Flying Ecosystem — 2026-09-11
- WildlifeSenses
- DangerMapClient
- Exploration
- .cells
- .load
- README.md

## God Nodes (most connected - your core abstractions)
1. `CreatureEntity` - 98 edges
2. `Species` - 74 edges
3. `FlyingCreatureEntity` - 37 edges
4. `WildlifeGoal` - 34 edges
5. `ArkSurvivalReturns` - 30 edges
6. `ModContent` - 30 edges
7. `LandHabitats` - 28 edges
8. `BehaviorState` - 27 edges
9. `Habitat` - 26 edges
10. `Config` - 25 edges

## Surprising Connections (you probably didn't know these)
- `View` --references--> `DangerMapPayload`  [EXTRACTED]
  src/main/java/dev/nez/arksurvivalreturns/client/XaeroDangerOverlay.java → src/main/java/dev/nez/arksurvivalreturns/feature/map/DangerMapPayload.java
- `Config` --references--> `Species`  [EXTRACTED]
  src/main/java/dev/nez/arksurvivalreturns/Config.java → src/main/java/dev/nez/arksurvivalreturns/feature/creature/Species.java
- `Config` --references--> `LandFamily`  [EXTRACTED]
  src/main/java/dev/nez/arksurvivalreturns/Config.java → src/main/java/dev/nez/arksurvivalreturns/feature/land/LandFamily.java
- `Config` --references--> `BiomeTier`  [EXTRACTED]
  src/main/java/dev/nez/arksurvivalreturns/Config.java → src/main/java/dev/nez/arksurvivalreturns/feature/spawn/BiomeTier.java
- `CreatureModel` --references--> `CreatureEntity`  [EXTRACTED]
  src/main/java/dev/nez/arksurvivalreturns/client/CreatureModel.java → src/main/java/dev/nez/arksurvivalreturns/feature/creature/CreatureEntity.java

## Import Cycles
- None detected.

## Communities (52 total, 4 thin omitted)

### Community 0 - ".species"
Cohesion: 0.19
Nodes (4): net.minecraft.world.entity.LivingEntity, Entity, Habitat, RemovalReason

### Community 1 - "CreatureEntity"
Cohesion: 0.08
Nodes (21): com.geckolib.animatable.GeoEntity, com.geckolib.animatable.instance.AnimatableInstanceCache, Goal, net.minecraft.world.DifficultyInstance, net.minecraft.world.level.ServerLevelAccessor, PathfinderMob, SoundEvent, SpawnGroupData (+13 more)

### Community 2 - "DangerMapSync.java"
Cohesion: 0.21
Nodes (7): CommandSourceStack, net.minecraft.server.level.ServerPlayer, net.neoforged.neoforge.event.RegisterCommandsEvent, DangerMapSync, PlayerChangedDimensionEvent, PlayerLoggedInEvent, PlayerRespawnEvent

### Community 3 - "DinoDebugGameTests.java"
Cohesion: 0.17
Nodes (8): net.minecraft.resources.ResourceKey, net.minecraft.world.InteractionHand, net.minecraft.world.item.Item, net.minecraft.world.item.Items, net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent, net.neoforged.neoforge.registries.RegisterEvent, DebugSpyglass, DinoDebugGameTests

### Community 4 - "ArkData"
Cohesion: 0.18
Nodes (7): CachedOutput, Client, DataProvider, JsonElement, PackOutput, ArkData, Override

### Community 5 - "XaeroDangerOverlay"
Cohesion: 0.22
Nodes (4): net.neoforged.fml.event.lifecycle.FMLClientSetupEvent, Post, View, XaeroDangerOverlay

### Community 7 - "FlyingCreatureEntity"
Cohesion: 0.06
Nodes (22): FlyingCreatureEntity, Builder, ControllerRegistrar, DamageSource, Entity, EntityDataAccessor, EntityType, Habitat (+14 more)

### Community 8 - "Ark Survival Returns"
Cohesion: 0.18
Nodes (11): Ark Survival Returns, Berries, Build and verification, Configuration, Difficulty and levels, Flying habitats and eggs, Play on Windows, Playable systems (+3 more)

### Community 9 - "BehaviorState"
Cohesion: 0.06
Nodes (21): alarm(), BehaviorState, ALERT, DEFEND, DRINK, FEED, FLEE, FORAGE (+13 more)

### Community 10 - "import_creatures.py"
Cohesion: 0.09
Nodes (18): berry(), main(), Author crisp 32px item sprites from pixel shapes, and an inventory review sheet., Asset/controller contracts for the September 12 creature expansion. Family…, main(), Import only runtime clips; normalize geometry and position tracks together.…, Authored quiet standing sleep: stable feet, lowered head, closed eyes and slow…, scale_track() (+10 more)

### Community 11 - "build_test_structure.py"
Cohesion: 0.60
Nodes (5): main(), named(), Create the small empty structure required by the headless GameTests (NBT, no…, string(), write_structure()

### Community 12 - "gradlew"
Cohesion: 0.83
Nodes (3): gradlew script, die(), warn()

### Community 13 - "Land Ecosystem & Behavior Patch — proposal"
Cohesion: 0.14
Nodes (12): Ark Survival Returns, Design precedents and scope choices, Existing behavior compared with the request, Families and initial distances, Group decisions and satiation, Habitat lifecycle and save migration, Implementation and acceptance checks after feedback, Land Ecosystem & Behavior Patch — proposal (+4 more)

### Community 16 - "net.minecraft.server.level.ServerLevel"
Cohesion: 0.06
Nodes (39): AABB, net.minecraft.server.level.ServerLevel, net.minecraft.util.RandomSource, Pre, EntityType, Species, ACROCANTHOSAURUS, ALLOSAURUS (+31 more)

### Community 17 - "ProgressionData.java"
Cohesion: 0.17
Nodes (5): net.minecraft.world.level.saveddata.SavedData, net.minecraft.world.level.saveddata.SavedDataType, net.neoforged.neoforge.event.server.ServerStartedEvent, MapUnlockData, ProgressionData

### Community 18 - "flying-ecosystem.md"
Cohesion: 0.29
Nodes (4): Gameplay coverage, Map and client pack coverage, Verification — 2026-09-07, World compatibility and scope

### Community 19 - "net.minecraft.gametest.framework.GameTestHelper"
Cohesion: 0.07
Nodes (25): net.minecraft.ChatFormatting, net.minecraft.core.Holder, net.minecraft.gametest.framework.GameTestHelper, net.minecraft.world.level.biome.Biome, net.neoforged.neoforge.registries.DeferredRegister, BiomeAnnouncements, PlayerLoggedOutEvent, Post (+17 more)

### Community 20 - "preview_danger_map.py"
Cohesion: 0.67
Nodes (3): danger(), main(), Plot the production danger-region formula and its discrete area shares.

### Community 21 - "Species.java"
Cohesion: 0.10
Nodes (20): additiveFood(), cohesionDistance(), defaultSprintRatio(), defensiveHerd(), family(), flyer(), foodClip(), groupHeightRange() (+12 more)

### Community 22 - "NestBlock"
Cohesion: 0.14
Nodes (21): Block, BlockHitResult, CollisionContext, com.mojang.serialization.MapCodec, Direction, InteractionHand, InteractionResult, LevelReader (+13 more)

### Community 24 - "ModContent"
Cohesion: 0.12
Nodes (17): Blocks, BooleanValue, DeferredBlock, DeferredHolder, DeferredItem, IntValue, Items, net.minecraft.world.entity.EntityType (+9 more)

### Community 25 - "NightEyesLayer.java"
Cohesion: 0.08
Nodes (24): com.geckolib.cache.model.GeoBone, com.geckolib.constant.dataticket.DataTicket, com.geckolib.model.GeoModel, com.geckolib.renderer.base.GeoRenderState, com.geckolib.renderer.base.RenderPassInfo, com.geckolib.renderer.GeoEntityRenderer, com.geckolib.renderer.layer.GeoRenderLayer, com.mojang.blaze3d.vertex.PoseStack (+16 more)

### Community 26 - "net.minecraft.core.BlockPos"
Cohesion: 0.06
Nodes (19): com.mojang.serialization.Codec, net.minecraft.core.BlockPos, net.minecraft.world.level.block.entity.BlockEntity, net.minecraft.world.level.block.state.BlockState, net.minecraft.world.level.BlockGetter, net.minecraft.world.level.material.FluidState, SavedData, Override (+11 more)

### Community 27 - "DinoDebugClient"
Cohesion: 0.18
Nodes (6): MouseScrollingEvent, net.minecraft.client.gui.GuiGraphicsExtractor, RegisterGuiLayersEvent, DinoDebugClient, LoggingOut, Post

### Community 28 - "ArkSurvivalReturns.java"
Cohesion: 0.20
Nodes (8): net.neoforged.bus.api.IEventBus, net.neoforged.fml.common.EventBusSubscriber, net.neoforged.fml.common.Mod, net.neoforged.fml.ModContainer, net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent, ArkSurvivalReturns, ArkClient, HabitatMapClient

### Community 29 - "net.minecraft.world.phys.Vec3"
Cohesion: 0.26
Nodes (5): net.minecraft.world.phys.Vec3, net.neoforged.neoforge.event.entity.player.AttackEntityEvent, net.neoforged.neoforge.event.level.block.BreakBlockEvent, Noise, WildlifeNoise

### Community 30 - "XaeroHabitatOverlay"
Cohesion: 0.19
Nodes (5): MapOverlayContext, Post, Visible, XaeroHabitatOverlay, Marker

### Community 31 - "TargetHealthBar"
Cohesion: 0.18
Nodes (7): net.minecraft.server.level.ServerBossEvent, ServerBossEvent, PlayerChangedDimensionEvent, PlayerLoggedOutEvent, PlayerRespawnEvent, Post, TargetHealthBar

### Community 32 - "XaeroDangerOverlay.java"
Cohesion: 0.31
Nodes (3): net.minecraft.network.chat.Component, DangerMapView, DangerBands

### Community 33 - "HabitatPayload"
Cohesion: 0.20
Nodes (13): net.minecraft.network.codec.StreamCodec, net.minecraft.network.protocol.common.custom.CustomPacketPayload, net.minecraft.network.RegistryFriendlyByteBuf, DinoDebugPayload, Override, Type, TurnPage, HabitatPayload (+5 more)

### Community 34 - "net.neoforged.bus.api.SubscribeEvent"
Cohesion: 0.11
Nodes (11): net.neoforged.bus.api.SubscribeEvent, net.neoforged.neoforge.event.server.ServerStoppedEvent, net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent, RegisterRenderers, LoggingOut, HabitatSync, PlayerChangedDimensionEvent, PlayerLoggedInEvent (+3 more)

### Community 35 - "SpawnRules.java"
Cohesion: 0.29
Nodes (6): net.minecraft.tags.TagKey, net.minecraft.world.level.block.Block, net.minecraft.world.level.Level, net.minecraft.world.phys.AABB, net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent, SpawnRules

### Community 36 - "Land Ecosystem & Behavior Patch: flying habitats"
Cohesion: 0.18
Nodes (11): Current implementation versus requested behavior, Design precedents, Egg-taking contract, Habitat and nest defaults, Implementation and validation sequence, Land Ecosystem & Behavior Patch: flying habitats, Performance estimates and bounds, Phantom and extracted-animation reuse (+3 more)

### Community 37 - "Wildlife behavior: research and implemented model"
Cohesion: 0.20
Nodes (10): Acceptance evidence, ARK methods, properties and registration concepts, Bounded simulation and limits, Difficulty and apex correction, Implemented behavior contract, Main finding, Player-facing development priorities after this model, What I inspected in the installed ARK files (+2 more)

### Community 38 - "org.junit.jupiter.api.Test"
Cohesion: 0.08
Nodes (10): org.junit.jupiter.api.Test, NighttimeCycle, Observation, Routine, LevelScaling, MovementTuning, NighttimeMindTest, WildlifeMindTest (+2 more)

### Community 40 - "Nighttime patch proposal"
Cohesion: 0.22
Nodes (8): Behavior rules and implementation boundaries, Existing foundation, Lessons from other projects, Mechanics to skip or defer, Nighttime patch proposal, Performance validation after direction feedback, Recommended mechanics, Species scope

### Community 41 - "WildlifeGoal"
Cohesion: 0.16
Nodes (4): net.minecraft.world.entity.ai.goal.Goal, net.minecraft.world.level.storage.ValueOutput, Override, WildlifeGoal

### Community 42 - "DinoDebugSync"
Cohesion: 0.19
Nodes (6): DinoDebugSync, PlayerChangedDimensionEvent, PlayerLoggedOutEvent, PlayerRespawnEvent, Post, View

### Community 43 - "Creature expansion — 2026-09-12"
Cohesion: 0.40
Nodes (4): Creature expansion — 2026-09-12, Scope, Sources and reproducibility, Validation

### Community 44 - "Optional client pack and difficulty map"
Cohesion: 0.29
Nodes (6): Difficulty map and unlock, Flying habitat markers, Installation and reproducibility, Iris development-run crash workaround, Optional client pack and difficulty map, Verification and playtest

### Community 45 - "Flying Ecosystem — 2026-09-11"
Cohesion: 0.33
Nodes (6): Animation and map assets, Behavior, Flying Ecosystem — 2026-09-11, Placement and persistence, Validation, Work limits

### Community 46 - "WildlifeSenses"
Cohesion: 0.29
Nodes (3): Detection, WildlifeSenses, NighttimeGameTests

### Community 47 - "DangerMapClient"
Cohesion: 0.29
Nodes (4): net.minecraft.client.gui.screens.Screen, Opening, DangerMapClient, LoggingOut

### Community 48 - "Exploration"
Cohesion: 0.33
Nodes (5): FunctionalInterface, Override, XaeroExploration, Exploration, xaero.map.MapProcessor

### Community 49 - ".cells"
Cohesion: 0.28
Nodes (3): io.github.billstark001.xaerobridge.api.MapOverlayContext, Cell, DangerMapViewTest

### Community 51 - "README.md"
Cohesion: 0.29
Nodes (3): Debug Spyglass, Verification, Movement tuning

## Knowledge Gaps
- **116 isolated node(s):** `ROAM`, `FORAGE`, `DRINK`, `SEEK_WATER`, `REST` (+111 more)
  These have ≤1 connection - possible missing edges or undocumented components.
- **4 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `CreatureEntity` connect `CreatureEntity` to `.species`, `SpawnRules.java`, `DinoDebugGameTests.java`, `org.junit.jupiter.api.Test`, `FlyingCreatureEntity`, `FollowPackGoal`, `WildlifeGoal`, `DinoDebugSync`, `WildlifeSenses`, `net.minecraft.server.level.ServerLevel`, `.load`, `net.minecraft.gametest.framework.GameTestHelper`, `ModContent`, `NightEyesLayer.java`, `net.minecraft.core.BlockPos`, `ArkSurvivalReturns.java`, `net.minecraft.world.phys.Vec3`, `TargetHealthBar`?**
  _High betweenness centrality (0.194) - this node is a cross-community bridge._
- **Why does `Species` connect `net.minecraft.server.level.ServerLevel` to `.species`, `CreatureEntity`, `HabitatPayload`, `SpawnRules.java`, `DinoDebugGameTests.java`, `org.junit.jupiter.api.Test`, `FlyingCreatureEntity`, `WildlifeGoal`, `WildlifeSenses`, `net.minecraft.gametest.framework.GameTestHelper`, `Species.java`, `ModContent`, `NightEyesLayer.java`, `net.minecraft.core.BlockPos`, `ArkSurvivalReturns.java`, `XaeroHabitatOverlay`?**
  _High betweenness centrality (0.150) - this node is a cross-community bridge._
- **Why does `ModContent` connect `ModContent` to `CreatureEntity`, `DinoDebugGameTests.java`, `SpawnRules.java`, `net.minecraft.server.level.ServerLevel`, `net.minecraft.gametest.framework.GameTestHelper`, `NestBlock`, `ArkSurvivalReturns.java`?**
  _High betweenness centrality (0.040) - this node is a cross-community bridge._
- **What connects `ROAM`, `FORAGE`, `DRINK` to the rest of the system?**
  _116 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `CreatureEntity` be split into smaller, more focused modules?**
  _Cohesion score 0.07729468599033816 - nodes in this community are weakly interconnected._
- **Should `FlyingCreatureEntity` be split into smaller, more focused modules?**
  _Cohesion score 0.06440677966101695 - nodes in this community are weakly interconnected._
- **Should `BehaviorState` be split into smaller, more focused modules?**
  _Cohesion score 0.056910569105691054 - nodes in this community are weakly interconnected._