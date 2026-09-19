# Graph Report - Ark  (2026-09-14)

## Corpus Check
- 269 files · ~121,231 words
- Verdict: corpus is large enough that graph structure adds value.

## Summary
- 1118 nodes · 2698 edges · 52 communities (48 shown, 4 thin omitted)
- Extraction: 96% EXTRACTED · 4% INFERRED · 0% AMBIGUOUS · INFERRED: 113 edges (avg confidence: 0.82)
- Token cost: 0 input · 0 output

## Graph Freshness
- Built from commit: `ddf39929`
- Run `git rev-parse HEAD` and compare to check if the graph is stale.
- Run `graphify update .` after code changes (no API cost).

## Community Hubs (Navigation)
- WildlifeGoal
- CreatureEntity
- net.minecraft.server.level.ServerPlayer
- XaeroLandHabitatOverlay
- ArkData
- XaeroDangerOverlay
- Snow Biome Patch — proposal
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
- DinoDebugSnapshot
- DangerMapClient
- TargetHealthBar
- XaeroDangerOverlay.java
- HabitatPayload
- net.neoforged.bus.api.SubscribeEvent
- SpawnRules.java
- Land Ecosystem & Behavior Patch: flying habitats
- Wildlife behavior: research and implemented model
- org.junit.jupiter.api.Test
- WildlifeGoal.java
- Nighttime patch proposal
- dev-dependencies.json
- DinoDebugSync.java
- Creature expansion — 2026-09-12
- Optional client pack and difficulty map
- Flying Ecosystem — 2026-09-11
- WildlifeSenses
- HabitatMapClient
- DangerMapView
- DangerMapPayload
- CreatureEntity.java
- README.md

## God Nodes (most connected - your core abstractions)
1. `CreatureEntity` - 98 edges
2. `Species` - 77 edges
3. `FlyingCreatureEntity` - 37 edges
4. `WildlifeGoal` - 35 edges
5. `LandHabitats` - 32 edges
6. `ArkSurvivalReturns` - 31 edges
7. `ModContent` - 31 edges
8. `BehaviorState` - 27 edges
9. `Habitat` - 27 edges
10. `Config` - 26 edges

## Surprising Connections (you probably didn't know these)
- `Config` --references--> `Species`  [EXTRACTED]
  src/main/java/dev/nez/arksurvivalreturns/Config.java → src/main/java/dev/nez/arksurvivalreturns/feature/creature/Species.java
- `Config` --references--> `LandFamily`  [EXTRACTED]
  src/main/java/dev/nez/arksurvivalreturns/Config.java → src/main/java/dev/nez/arksurvivalreturns/feature/land/LandFamily.java
- `Config` --references--> `BiomeTier`  [EXTRACTED]
  src/main/java/dev/nez/arksurvivalreturns/Config.java → src/main/java/dev/nez/arksurvivalreturns/feature/spawn/BiomeTier.java
- `CreatureModel` --references--> `CreatureEntity`  [EXTRACTED]
  src/main/java/dev/nez/arksurvivalreturns/client/CreatureModel.java → src/main/java/dev/nez/arksurvivalreturns/feature/creature/CreatureEntity.java
- `CreatureModel` --references--> `Species`  [EXTRACTED]
  src/main/java/dev/nez/arksurvivalreturns/client/CreatureModel.java → src/main/java/dev/nez/arksurvivalreturns/feature/creature/Species.java

## Import Cycles
- None detected.

## Communities (52 total, 4 thin omitted)

### Community 0 - "WildlifeGoal"
Cohesion: 0.16
Nodes (6): net.minecraft.world.entity.LivingEntity, net.minecraft.world.phys.Vec3, Habitat, Override, WildlifeGoal, Noise

### Community 1 - "CreatureEntity"
Cohesion: 0.08
Nodes (14): com.geckolib.animatable.instance.AnimatableInstanceCache, PathfinderMob, SoundEvent, CreatureEntity, BlockPos, BlockState, Builder, ControllerRegistrar (+6 more)

### Community 2 - "net.minecraft.server.level.ServerPlayer"
Cohesion: 0.10
Nodes (13): CommandSourceStack, net.minecraft.server.level.ServerPlayer, Marker, PlayerChangedDimensionEvent, PlayerLoggedInEvent, PlayerLoggedOutEvent, PlayerRespawnEvent, Post (+5 more)

### Community 3 - "XaeroLandHabitatOverlay"
Cohesion: 0.25
Nodes (5): MapOverlayContext, Marker, Post, Visible, XaeroLandHabitatOverlay

### Community 4 - "ArkData"
Cohesion: 0.18
Nodes (7): CachedOutput, Client, DataProvider, JsonElement, PackOutput, ArkData, Override

### Community 5 - "XaeroDangerOverlay"
Cohesion: 0.22
Nodes (3): net.neoforged.fml.event.lifecycle.FMLClientSetupEvent, Post, XaeroDangerOverlay

### Community 6 - "Snow Biome Patch — proposal"
Cohesion: 0.20
Nodes (9): Current assets and integration gap, Direction, Habitats: rivers plus shelter, Implementation after feedback, Optional freezing-mod integration, Population, map and performance, Recommended species behavior, Routines and survival gameplay (+1 more)

### Community 7 - "FlyingCreatureEntity"
Cohesion: 0.08
Nodes (21): net.minecraft.world.entity.player.Player, FlyingCreatureEntity, Builder, ControllerRegistrar, DamageSource, Entity, EntityDataAccessor, EntityType (+13 more)

### Community 8 - "Ark Survival Returns"
Cohesion: 0.18
Nodes (11): Ark Survival Returns, Berries, Build and verification, Configuration, Difficulty and levels, Flying habitats and eggs, Play on Windows, Playable systems (+3 more)

### Community 9 - "BehaviorState"
Cohesion: 0.05
Nodes (22): alarm(), BehaviorState, ALERT, DEFEND, DRINK, FEED, FLEE, FORAGE (+14 more)

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
Cohesion: 0.08
Nodes (23): Ark Survival Returns, Design precedents and scope choices, Existing behavior compared with the request, Families and initial distances, Group decisions and satiation, Habitat lifecycle and save migration, Implementation and acceptance checks after feedback, Land Ecosystem & Behavior Patch — proposal (+15 more)

### Community 16 - "net.minecraft.server.level.ServerLevel"
Cohesion: 0.05
Nodes (47): AABB, EntityLeaveLevelEvent, net.minecraft.server.level.ServerLevel, net.minecraft.util.RandomSource, Pre, Species, ACROCANTHOSAURUS, ALLOSAURUS (+39 more)

### Community 17 - "ProgressionData.java"
Cohesion: 0.15
Nodes (6): net.minecraft.world.level.saveddata.SavedData, net.minecraft.world.level.saveddata.SavedDataType, net.neoforged.neoforge.event.RegisterCommandsEvent, net.neoforged.neoforge.event.server.ServerStartedEvent, MapUnlockData, ProgressionData

### Community 18 - "flying-ecosystem.md"
Cohesion: 0.29
Nodes (4): Gameplay coverage, Map and client pack coverage, Verification — 2026-09-07, World compatibility and scope

### Community 19 - "net.minecraft.gametest.framework.GameTestHelper"
Cohesion: 0.07
Nodes (24): net.minecraft.ChatFormatting, net.minecraft.core.Holder, net.minecraft.gametest.framework.GameTestHelper, net.minecraft.world.level.biome.Biome, net.neoforged.neoforge.registries.DeferredRegister, BiomeAnnouncements, PlayerLoggedOutEvent, Post (+16 more)

### Community 20 - "preview_danger_map.py"
Cohesion: 0.67
Nodes (3): danger(), main(), Plot the production danger-region formula and its discrete area shares.

### Community 21 - "Species.java"
Cohesion: 0.09
Nodes (21): additiveFood(), cohesionDistance(), defaultSprintRatio(), defensiveHerd(), family(), flyer(), foodClip(), groupHeightRange() (+13 more)

### Community 22 - "NestBlock"
Cohesion: 0.14
Nodes (19): Block, BlockHitResult, CollisionContext, com.mojang.serialization.MapCodec, Direction, InteractionHand, InteractionResult, LevelReader (+11 more)

### Community 24 - "ModContent"
Cohesion: 0.11
Nodes (18): Blocks, BooleanValue, DeferredBlock, DeferredHolder, DeferredItem, IntValue, Items, net.minecraft.world.entity.EntityType (+10 more)

### Community 25 - "NightEyesLayer.java"
Cohesion: 0.08
Nodes (24): com.geckolib.cache.model.GeoBone, com.geckolib.constant.dataticket.DataTicket, com.geckolib.model.GeoModel, com.geckolib.renderer.base.GeoRenderState, com.geckolib.renderer.base.RenderPassInfo, com.geckolib.renderer.GeoEntityRenderer, com.geckolib.renderer.layer.GeoRenderLayer, com.mojang.blaze3d.vertex.PoseStack (+16 more)

### Community 26 - "net.minecraft.core.BlockPos"
Cohesion: 0.07
Nodes (13): com.mojang.serialization.Codec, net.minecraft.core.BlockPos, SavedData, Habitat, HabitatData, SavedDataType, GroupNeeds, Habitat (+5 more)

### Community 27 - "DinoDebugClient"
Cohesion: 0.11
Nodes (11): MouseScrollingEvent, net.minecraft.client.gui.GuiGraphicsExtractor, net.minecraft.resources.ResourceKey, net.minecraft.world.item.Item, net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent, net.neoforged.neoforge.registries.RegisterEvent, RegisterGuiLayersEvent, DinoDebugClient (+3 more)

### Community 28 - "ArkSurvivalReturns.java"
Cohesion: 0.15
Nodes (11): net.minecraft.world.entity.SpawnGroupData, net.neoforged.bus.api.IEventBus, net.neoforged.fml.common.EventBusSubscriber, net.neoforged.fml.common.Mod, net.neoforged.fml.ModContainer, net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent, net.neoforged.neoforge.event.entity.player.AttackEntityEvent, net.neoforged.neoforge.event.level.block.BreakBlockEvent (+3 more)

### Community 30 - "DangerMapClient"
Cohesion: 0.14
Nodes (9): net.minecraft.client.gui.screens.Screen, Opening, DangerMapClient, LoggingOut, MapOverlayContext, Marker, Post, Visible (+1 more)

### Community 31 - "TargetHealthBar"
Cohesion: 0.20
Nodes (7): net.minecraft.server.level.ServerBossEvent, ServerBossEvent, PlayerChangedDimensionEvent, PlayerLoggedOutEvent, PlayerRespawnEvent, Post, TargetHealthBar

### Community 33 - "HabitatPayload"
Cohesion: 0.19
Nodes (13): net.minecraft.network.codec.StreamCodec, net.minecraft.network.protocol.common.custom.CustomPacketPayload, net.minecraft.network.RegistryFriendlyByteBuf, DinoDebugPayload, Override, Type, TurnPage, HabitatPayload (+5 more)

### Community 34 - "net.neoforged.bus.api.SubscribeEvent"
Cohesion: 0.11
Nodes (10): net.neoforged.bus.api.SubscribeEvent, net.neoforged.neoforge.event.server.ServerStoppedEvent, net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent, RegisterRenderers, HabitatSync, PlayerChangedDimensionEvent, PlayerLoggedInEvent, PlayerLoggedOutEvent (+2 more)

### Community 35 - "SpawnRules.java"
Cohesion: 0.33
Nodes (5): net.minecraft.tags.TagKey, net.minecraft.world.level.block.Block, net.minecraft.world.phys.AABB, net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent, SpawnRules

### Community 36 - "Land Ecosystem & Behavior Patch: flying habitats"
Cohesion: 0.18
Nodes (11): Current implementation versus requested behavior, Design precedents, Egg-taking contract, Habitat and nest defaults, Implementation and validation sequence, Land Ecosystem & Behavior Patch: flying habitats, Performance estimates and bounds, Phantom and extracted-animation reuse (+3 more)

### Community 37 - "Wildlife behavior: research and implemented model"
Cohesion: 0.20
Nodes (10): Acceptance evidence, ARK methods, properties and registration concepts, Bounded simulation and limits, Difficulty and apex correction, Implemented behavior contract, Main finding, Player-facing development priorities after this model, What I inspected in the installed ARK files (+2 more)

### Community 38 - "org.junit.jupiter.api.Test"
Cohesion: 0.06
Nodes (13): org.junit.jupiter.api.Test, LandHabitatSymbols, NighttimeCycle, Observation, LevelScaling, MovementTuning, NighttimeMindTest, WildlifeMindTest (+5 more)

### Community 39 - "WildlifeGoal.java"
Cohesion: 0.27
Nodes (3): net.minecraft.world.entity.ai.goal.Goal, FollowPackGoal, Override

### Community 40 - "Nighttime patch proposal"
Cohesion: 0.22
Nodes (8): Behavior rules and implementation boundaries, Existing foundation, Lessons from other projects, Mechanics to skip or defer, Nighttime patch proposal, Performance validation after direction feedback, Recommended mechanics, Species scope

### Community 41 - "dev-dependencies.json"
Cohesion: 0.40
Nodes (4): checked, dependencies, loader, minecraft

### Community 42 - "DinoDebugSync.java"
Cohesion: 0.11
Nodes (13): net.minecraft.world.level.block.entity.BlockEntity, net.minecraft.world.level.block.state.BlockState, net.minecraft.world.level.BlockGetter, net.minecraft.world.level.Level, net.minecraft.world.level.material.FluidState, DinoDebugSync, Override, PlayerChangedDimensionEvent (+5 more)

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
Cohesion: 0.26
Nodes (3): Detection, WildlifeSenses, NighttimeGameTests

### Community 48 - "DangerMapView"
Cohesion: 0.18
Nodes (8): FunctionalInterface, Override, XaeroExploration, Cell, DangerMapView, Exploration, DangerMapViewTest, xaero.map.MapProcessor

### Community 49 - "DangerMapPayload"
Cohesion: 0.29
Nodes (5): io.github.billstark001.xaerobridge.api.MapOverlayContext, View, DangerMapPayload, Override, Type

### Community 50 - "CreatureEntity.java"
Cohesion: 0.14
Nodes (11): com.geckolib.animatable.GeoEntity, Goal, net.minecraft.world.DifficultyInstance, net.minecraft.world.level.ServerLevelAccessor, net.minecraft.world.level.storage.ValueInput, net.minecraft.world.level.storage.ValueOutput, SpawnGroupData, BlockPos (+3 more)

### Community 51 - "README.md"
Cohesion: 0.29
Nodes (3): Debug Spyglass, Verification, Movement tuning

## Knowledge Gaps
- **138 isolated node(s):** `minecraft`, `loader`, `checked`, `dependencies`, `ROAM` (+133 more)
  These have ≤1 connection - possible missing edges or undocumented components.
- **4 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `CreatureEntity` connect `CreatureEntity` to `WildlifeGoal`, `SpawnRules.java`, `WildlifeGoal.java`, `FlyingCreatureEntity`, `DinoDebugSync.java`, `WildlifeSenses`, `net.minecraft.server.level.ServerLevel`, `CreatureEntity.java`, `net.minecraft.gametest.framework.GameTestHelper`, `ModContent`, `NightEyesLayer.java`, `net.minecraft.core.BlockPos`, `ArkSurvivalReturns.java`, `DinoDebugSnapshot`, `TargetHealthBar`?**
  _High betweenness centrality (0.172) - this node is a cross-community bridge._
- **Why does `Species` connect `net.minecraft.server.level.ServerLevel` to `WildlifeGoal`, `CreatureEntity`, `HabitatPayload`, `net.minecraft.server.level.ServerPlayer`, `SpawnRules.java`, `org.junit.jupiter.api.Test`, `WildlifeGoal.java`, `FlyingCreatureEntity`, `WildlifeSenses`, `net.minecraft.gametest.framework.GameTestHelper`, `Species.java`, `NestBlock`, `ModContent`, `NightEyesLayer.java`, `net.minecraft.core.BlockPos`, `ArkSurvivalReturns.java`?**
  _High betweenness centrality (0.114) - this node is a cross-community bridge._
- **Why does `FlyingCreatureEntity` connect `FlyingCreatureEntity` to `net.minecraft.server.level.ServerLevel`, `CreatureEntity`, `net.minecraft.core.BlockPos`, `SpawnRules.java`?**
  _High betweenness centrality (0.049) - this node is a cross-community bridge._
- **What connects `minecraft`, `loader`, `checked` to the rest of the system?**
  _138 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `CreatureEntity` be split into smaller, more focused modules?**
  _Cohesion score 0.08362369337979095 - nodes in this community are weakly interconnected._
- **Should `net.minecraft.server.level.ServerPlayer` be split into smaller, more focused modules?**
  _Cohesion score 0.1010752688172043 - nodes in this community are weakly interconnected._
- **Should `FlyingCreatureEntity` be split into smaller, more focused modules?**
  _Cohesion score 0.08078431372549019 - nodes in this community are weakly interconnected._