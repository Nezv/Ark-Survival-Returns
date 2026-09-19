# Graph Report - Ark  (2026-09-18)

## Corpus Check
- 615 files · ~188,275 words
- Verdict: corpus is large enough that graph structure adds value.

## Summary
- 1988 nodes · 5238 edges · 95 communities (81 shown, 14 thin omitted)
- Extraction: 97% EXTRACTED · 3% INFERRED · 0% AMBIGUOUS · INFERRED: 171 edges (avg confidence: 0.82)
- Token cost: 0 input · 0 output

## Graph Freshness
- Built from commit: `0406b43b`
- Run `git rev-parse HEAD` and compare to check if the graph is stale.
- Run `graphify update .` after code changes (no API cost).

## Community Hubs (Navigation)
- WildlifeGoal
- CreatureEntity
- net.minecraft.server.level.ServerPlayer
- XaeroLandHabitatOverlay
- ArkData
- DangerMapClient
- Snow Biome Patch — proposal
- FlyingCreatureEntity
- Ark Survival Returns
- WildlifeMind
- import_creatures.py
- build_test_structure.py
- gradlew
- Land Ecosystem & Behavior Patch — proposal
- net.minecraft.server.level.ServerLevel
- SpawnRules.java
- Verification — 2026-09-07
- BiomeTier
- preview_danger_map.py
- Species.java
- NestBlock
- verify_client_pack.py
- Config
- NightEyesLayer.java
- net.minecraft.core.BlockPos
- DinoDebugClient
- net.neoforged.fml.common.EventBusSubscriber
- .capture
- net.minecraft.network.chat.Component
- net.neoforged.bus.api.SubscribeEvent
- .level
- net.minecraft.network.RegistryFriendlyByteBuf
- HabitatPayload
- net.minecraft.world.level.Level
- Land Ecosystem & Behavior Patch: flying habitats
- README.md
- org.junit.jupiter.api.Test
- UnconsciousBehavior
- Nighttime patch proposal
- dev-dependencies.json
- DinoDebugSync
- Creature expansion — 2026-09-12
- Optional client pack and difficulty map
- net.minecraft.world.entity.LivingEntity
- CreatureMountMenu
- TamingState
- Exploration
- AquaticHabitats
- CreatureEntity.java
- Species
- net.minecraft.world.entity.player.Player
- ThemeGameTests.java
- .replenish
- HostileGuard.java
- net.minecraft.world.entity.Entity
- net.minecraft.world.item.ItemStack
- TorporState
- net.minecraft.world.phys.Vec3
- AquaticCreatureEntity.java
- TamingGameTests.java
- LandFamily
- build_taming_manifests.py
- WildlifeController
- BehaviorState
- LandWaterIndex
- Observation
- 2. Execution paths
- .of
- CreatureRideProfile
- Land Ecosystem & Behavior Patch
- .initializeLevel
- CreatureRenderer.java
- Taming patch proposal
- Theme alignment
- Phase
- MovementTuning
- .spawn
- build_taming_roster.py
- Collection ecosystem — ice, flying, aquatic and swamp
- taming-proposal.md
- TamingAttachments
- GroupNeeds
- CreatureSize
- Event
- LandSymbolsTest.java
- herd
- .hold
- BehaviorState.java
- Realm
- MovementProbe
- sleepClip
- .run
- CollectionGameTests

## God Nodes (most connected - your core abstractions)
1. `CreatureEntity` - 204 edges
2. `Species` - 140 edges
3. `TamingState` - 55 edges
4. `TorporState` - 51 edges
5. `FlyingCreatureEntity` - 49 edges
6. `ArkSurvivalReturns` - 48 edges
7. `Config` - 48 edges
8. `ModContent` - 45 edges
9. `LandHabitats` - 38 edges
10. `TorporService` - 37 edges

## Surprising Connections (you probably didn't know these)
- `View` --references--> `DangerMapPayload`  [EXTRACTED]
  src/main/java/dev/nez/arksurvivalreturns/client/XaeroDangerOverlay.java → src/main/java/dev/nez/arksurvivalreturns/feature/map/DangerMapPayload.java
- `Config` --references--> `Species`  [EXTRACTED]
  src/main/java/dev/nez/arksurvivalreturns/Config.java → src/main/java/dev/nez/arksurvivalreturns/feature/creature/Species.java
- `Config` --references--> `LandFamily`  [EXTRACTED]
  src/main/java/dev/nez/arksurvivalreturns/Config.java → src/main/java/dev/nez/arksurvivalreturns/feature/land/LandFamily.java
- `Config` --references--> `BiomeTier`  [EXTRACTED]
  src/main/java/dev/nez/arksurvivalreturns/Config.java → src/main/java/dev/nez/arksurvivalreturns/feature/spawn/BiomeTier.java
- `Config` --references--> `CreatureSize`  [EXTRACTED]
  src/main/java/dev/nez/arksurvivalreturns/Config.java → src/main/java/dev/nez/arksurvivalreturns/feature/taming/CreatureSize.java

## Import Cycles
- None detected.

## Communities (95 total, 14 thin omitted)

### Community 0 - "WildlifeGoal"
Cohesion: 0.05
Nodes (16): net.neoforged.neoforge.event.level.block.BreakBlockEvent, AquaticGoal, BlockPos, Habitat, Override, NighttimeCycle, BlockPos, Habitat (+8 more)

### Community 1 - "CreatureEntity"
Cohesion: 0.06
Nodes (17): com.geckolib.animatable.instance.AnimatableInstanceCache, MoveFunction, PathfinderMob, SoundEvent, CreatureEntity, BlockPos, BlockState, Builder (+9 more)

### Community 2 - "net.minecraft.server.level.ServerPlayer"
Cohesion: 0.10
Nodes (14): net.minecraft.server.level.ServerPlayer, net.neoforged.neoforge.event.RegisterCommandsEvent, Marker, PlayerChangedDimensionEvent, PlayerLoggedInEvent, PlayerLoggedOutEvent, PlayerRespawnEvent, Post (+6 more)

### Community 3 - "XaeroLandHabitatOverlay"
Cohesion: 0.23
Nodes (5): MapOverlayContext, Marker, Post, Visible, XaeroLandHabitatOverlay

### Community 4 - "ArkData"
Cohesion: 0.17
Nodes (7): CachedOutput, Client, DataProvider, PackOutput, ArkData, JsonElement, Override

### Community 5 - "DangerMapClient"
Cohesion: 0.12
Nodes (9): io.github.billstark001.xaerobridge.api.MapOverlayContext, net.minecraft.client.gui.screens.Screen, net.neoforged.fml.event.lifecycle.FMLClientSetupEvent, Opening, DangerMapClient, LoggingOut, Post, View (+1 more)

### Community 6 - "Snow Biome Patch — proposal"
Cohesion: 0.22
Nodes (9): Current assets and integration gap, Direction, Habitats: rivers plus shelter, Implementation after feedback, Optional freezing-mod integration, Population, map and performance, Recommended species behavior, Routines and survival gameplay (+1 more)

### Community 7 - "FlyingCreatureEntity"
Cohesion: 0.08
Nodes (19): FlyingCreatureEntity, Builder, ControllerRegistrar, DamageSource, Entity, EntityDataAccessor, Habitat, Override (+11 more)

### Community 8 - "Ark Survival Returns"
Cohesion: 0.15
Nodes (13): Ark Survival Returns, Berries, Build and verification, Configuration, Difficulty and levels, Flying habitats and eggs, Play on Windows, Playable systems (+5 more)

### Community 10 - "import_creatures.py"
Cohesion: 0.08
Nodes (27): berry(), main(), Author crisp 32px item sprites from pixel shapes, and an inventory review sheet., import_clips(), Asset/controller contracts for the ice, flying, aquatic and swamp collection.…, Ordered clip names the importer must copy. Index 0 is idle, 2 is the attack…, Asset/controller contracts for the September 12 creature expansion. Family…, main() (+19 more)

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
Cohesion: 0.19
Nodes (8): net.minecraft.server.level.ServerLevel, Budget, BlockPos, Vec3, LandHabitats, Shore, Site, BlockPos

### Community 17 - "SpawnRules.java"
Cohesion: 0.10
Nodes (8): net.minecraft.world.level.saveddata.SavedData, net.minecraft.world.level.saveddata.SavedDataType, net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent, net.neoforged.neoforge.event.server.ServerStartedEvent, net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent, MapUnlockData, ProgressionData, SpawnRules

### Community 18 - "Verification — 2026-09-07"
Cohesion: 0.40
Nodes (4): Gameplay coverage, Map and client pack coverage, Verification — 2026-09-07, World compatibility and scope

### Community 19 - "BiomeTier"
Cohesion: 0.23
Nodes (10): net.minecraft.core.Holder, net.minecraft.world.level.biome.Biome, at(), BiomeTier, EASY, EXTREME, HARD, MODERATE (+2 more)

### Community 20 - "preview_danger_map.py"
Cohesion: 0.67
Nodes (3): danger(), main(), Plot the production danger-region formula and its discrete area shares.

### Community 21 - "Species.java"
Cohesion: 0.10
Nodes (9): additiveFood(), defaultSprintRatio(), eyeBones(), FlyerProfile, foodClip(), glowingEyes(), runClip(), sprintRatioDefault() (+1 more)

### Community 22 - "NestBlock"
Cohesion: 0.12
Nodes (20): Block, BlockHitResult, CollisionContext, com.mojang.serialization.MapCodec, LevelReader, net.minecraft.world.level.block.state.properties.BooleanProperty, ScheduledTickAccess, BlockGetter (+12 more)

### Community 24 - "Config"
Cohesion: 0.11
Nodes (19): Blocks, BooleanValue, DeferredBlock, DeferredItem, IntValue, Item, Items, net.minecraft.world.entity.SpawnGroupData (+11 more)

### Community 25 - "NightEyesLayer.java"
Cohesion: 0.15
Nodes (11): com.geckolib.cache.model.GeoBone, com.geckolib.constant.dataticket.DataTicket, com.geckolib.renderer.layer.GeoRenderLayer, GeoRenderer, net.neoforged.neoforge.common.ModConfigSpec, PerBoneRender, Override, RenderPassInfo (+3 more)

### Community 26 - "net.minecraft.core.BlockPos"
Cohesion: 0.14
Nodes (9): com.mojang.serialization.Codec, net.minecraft.core.BlockPos, SavedData, Habitat, HabitatData, SavedDataType, Habitat, SavedDataType (+1 more)

### Community 27 - "DinoDebugClient"
Cohesion: 0.12
Nodes (9): MouseScrollingEvent, net.minecraft.resources.ResourceKey, net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent, net.neoforged.neoforge.registries.RegisterEvent, RegisterGuiLayersEvent, DinoDebugClient, LoggingOut, Post (+1 more)

### Community 28 - "net.neoforged.fml.common.EventBusSubscriber"
Cohesion: 0.11
Nodes (14): net.neoforged.bus.api.IEventBus, net.neoforged.fml.common.EventBusSubscriber, net.neoforged.fml.common.Mod, net.neoforged.fml.ModContainer, net.neoforged.neoforge.client.event.RegisterMenuScreensEvent, net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent, net.neoforged.neoforge.event.ModifyRecipeJsonsEvent, org.jspecify.annotations.Nullable (+6 more)

### Community 30 - "net.minecraft.network.chat.Component"
Cohesion: 0.24
Nodes (6): net.minecraft.network.chat.Component, Marker, Post, Visible, XaeroHabitatOverlay, message()

### Community 31 - "net.neoforged.bus.api.SubscribeEvent"
Cohesion: 0.10
Nodes (13): net.minecraft.server.level.ServerBossEvent, net.neoforged.bus.api.SubscribeEvent, net.neoforged.neoforge.event.entity.player.AttackEntityEvent, net.neoforged.neoforge.event.server.ServerStoppedEvent, ServerBossEvent, RegisterRenderers, LoggingOut, RegisterRenderers (+5 more)

### Community 32 - ".level"
Cohesion: 0.16
Nodes (5): Cell, DangerMapView, DangerBands, DangerMapViewTest, DangerBandsTest

### Community 33 - "net.minecraft.network.RegistryFriendlyByteBuf"
Cohesion: 0.19
Nodes (13): net.minecraft.network.codec.StreamCodec, net.minecraft.network.protocol.common.custom.CustomPacketPayload, net.minecraft.network.RegistryFriendlyByteBuf, DinoDebugPayload, Override, Type, TurnPage, Override (+5 more)

### Community 34 - "HabitatPayload"
Cohesion: 0.15
Nodes (10): HabitatPayload, Override, Type, Marker, HabitatSync, PlayerChangedDimensionEvent, PlayerLoggedInEvent, PlayerLoggedOutEvent (+2 more)

### Community 35 - "net.minecraft.world.level.Level"
Cohesion: 0.07
Nodes (23): EntityPlaceEvent, net.minecraft.core.Direction, net.minecraft.core.Position, net.minecraft.world.entity.EntityType, net.minecraft.world.entity.projectile.arrow.AbstractArrow, net.minecraft.world.entity.projectile.Projectile, net.minecraft.world.item.ArrowItem, net.minecraft.world.level.block.Block (+15 more)

### Community 36 - "Land Ecosystem & Behavior Patch: flying habitats"
Cohesion: 0.11
Nodes (17): Animation and map assets, Behavior, Flying Ecosystem — 2026-09-11, Placement and persistence, Current implementation versus requested behavior, Design precedents, Egg-taking contract, Habitat and nest defaults (+9 more)

### Community 37 - "README.md"
Cohesion: 0.11
Nodes (13): Acceptance evidence, ARK methods, properties and registration concepts, Bounded simulation and limits, Difficulty and apex correction, Implemented behavior contract, Main finding, Player-facing development priorities after this model, What I inspected in the installed ARK files (+5 more)

### Community 38 - "org.junit.jupiter.api.Test"
Cohesion: 0.16
Nodes (4): org.junit.jupiter.api.Test, WildlifeMindTest, LandModelTest, CreatureSizeTest

### Community 39 - "UnconsciousBehavior"
Cohesion: 0.16
Nodes (6): net.minecraft.world.entity.ai.goal.Goal, net.minecraft.world.entity.Mob, FollowPackGoal, Override, Override, UnconsciousBehavior

### Community 40 - "Nighttime patch proposal"
Cohesion: 0.22
Nodes (8): Behavior rules and implementation boundaries, Existing foundation, Lessons from other projects, Mechanics to skip or defer, Nighttime patch proposal, Performance validation after direction feedback, Recommended mechanics, Species scope

### Community 41 - "dev-dependencies.json"
Cohesion: 0.40
Nodes (4): checked, dependencies, loader, minecraft

### Community 42 - "DinoDebugSync"
Cohesion: 0.18
Nodes (6): DinoDebugSync, PlayerChangedDimensionEvent, PlayerLoggedOutEvent, PlayerRespawnEvent, Post, View

### Community 43 - "Creature expansion — 2026-09-12"
Cohesion: 0.40
Nodes (4): Creature expansion — 2026-09-12, Scope, Sources and reproducibility, Validation

### Community 44 - "Optional client pack and difficulty map"
Cohesion: 0.29
Nodes (7): Difficulty map and unlock, Flying habitat markers, Installation and reproducibility, Iris development-run crash workaround, Land habitat markers and dev settings, Optional client pack and difficulty map, Verification and playtest

### Community 45 - "net.minecraft.world.entity.LivingEntity"
Cohesion: 0.05
Nodes (27): Clone, EntityInteract, EntityInteractSpecific, ItemTossEvent, LeftClickBlock, net.minecraft.gametest.framework.GameTestHelper, net.minecraft.world.entity.LivingEntity, net.neoforged.neoforge.event.entity.EntityMountEvent (+19 more)

### Community 46 - "CreatureMountMenu"
Cohesion: 0.05
Nodes (30): ArrowRenderState, com.geckolib.model.GeoModel, com.geckolib.renderer.base.GeoRenderState, net.minecraft.ChatFormatting, net.minecraft.client.gui.GuiGraphicsExtractor, net.minecraft.client.gui.screens.inventory.AbstractContainerScreen, net.minecraft.client.renderer.entity.ArrowRenderer, net.minecraft.client.renderer.entity.state.ArrowRenderState (+22 more)

### Community 47 - "TamingState"
Cohesion: 0.05
Nodes (11): Result, ACCEPTED, ALREADY_TAMED, COOLDOWN, EXCLUDED, NOT_CLAIMANT, NOT_HUNGRY, WRONG_FOOD (+3 more)

### Community 48 - "Exploration"
Cohesion: 0.22
Nodes (6): FunctionalInterface, Override, XaeroExploration, MapOverlayContext, Exploration, xaero.map.MapProcessor

### Community 49 - "AquaticHabitats"
Cohesion: 0.12
Nodes (13): EntityLeaveLevelEvent, net.minecraft.world.phys.AABB, AquaticHabitats, Budget, AABB, BlockPos, Habitat, Vec3 (+5 more)

### Community 50 - "CreatureEntity.java"
Cohesion: 0.24
Nodes (7): com.geckolib.animatable.GeoEntity, Goal, net.minecraft.world.DifficultyInstance, net.minecraft.world.level.ServerLevelAccessor, SpawnGroupData, EntitySpawnReason, PackData

### Community 51 - "Species"
Cohesion: 0.05
Nodes (42): Species, ACROCANTHOSAURUS, ALLOSAURUS, ANKYLOSAURUS, ARCHAEOPTERYX, ARGENTAVIS, BRONTOSAURUS, CARNOTAURUS (+34 more)

### Community 52 - "net.minecraft.world.entity.player.Player"
Cohesion: 0.12
Nodes (8): net.minecraft.core.particles.ParticleOptions, net.minecraft.world.entity.player.Player, net.minecraft.world.InteractionHand, net.minecraft.world.InteractionResult, LivingEntity, Override, Component, TamingFeedback

### Community 53 - "ThemeGameTests.java"
Cohesion: 0.12
Nodes (13): com.google.gson.JsonElement, com.google.gson.JsonObject, net.minecraft.advancements.triggers.Criterion, net.minecraft.core.RegistryAccess, net.minecraft.resources.RegistryOps, net.minecraft.server.MinecraftServer, net.minecraft.world.item.crafting.RecipeManager, net.minecraft.world.item.trading.VillagerTrade (+5 more)

### Community 54 - ".replenish"
Cohesion: 0.13
Nodes (10): net.minecraft.util.RandomSource, Pre, Habitat, Post, PopulationDirector, AABB, EntitySpawnReason, EntityType (+2 more)

### Community 55 - "HostileGuard.java"
Cohesion: 0.08
Nodes (15): net.minecraft.world.item.SpawnEggItem, net.neoforged.neoforge.event.entity.EntityJoinLevelEvent, net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent, net.neoforged.neoforge.event.entity.living.LivingUseTotemEvent, net.neoforged.neoforge.event.entity.player.PlayerSpawnPhantomsEvent, net.neoforged.neoforge.event.level.ModifyCustomSpawnersEvent, net.neoforged.neoforge.event.village.VillageSiegeEvent, PositionCheck (+7 more)

### Community 56 - "net.minecraft.world.entity.Entity"
Cohesion: 0.16
Nodes (9): net.minecraft.commands.CommandSourceStack, net.minecraft.world.entity.Entity, net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent, PortalSpawnEvent, TamingCommands, DimensionGuard, PlayerLoggedInEvent, PlayerRespawnEvent (+1 more)

### Community 57 - "net.minecraft.world.item.ItemStack"
Cohesion: 0.13
Nodes (4): net.minecraft.world.item.ItemStack, CreatureInventory, Override, CreatureTamingProfile

### Community 59 - "net.minecraft.world.phys.Vec3"
Cohesion: 0.16
Nodes (6): EntityDimensions, net.minecraft.world.phys.Vec3, Vec3, CreatureRideController, Vec3, RiderInput

### Community 60 - "AquaticCreatureEntity.java"
Cohesion: 0.13
Nodes (6): com.geckolib.animation.RawAnimation, AquaticCreatureEntity, Builder, ControllerRegistrar, Override, CreatureAnimationBridge

### Community 61 - "TamingGameTests.java"
Cohesion: 0.19
Nodes (9): net.minecraft.tags.TagKey, net.minecraft.world.item.Item, net.minecraft.world.item.Items, CreatureProfileRegistry, TamingMethod, AERIAL, KNOCKOUT, PASSIVE (+1 more)

### Community 62 - "LandFamily"
Cohesion: 0.11
Nodes (18): family(), LandProfile, SwimProfile, LandFamily, AMPHIBIOUS, AQUATIC, BIG_CARNIVORE, BIG_HERBIVORE (+10 more)

### Community 63 - "build_taming_manifests.py"
Cohesion: 0.19
Nodes (18): classify_torpor(), cube_top(), emit_java(), forward_axis(), geometry(), head_bone(), main(), mesh_bounds() (+10 more)

### Community 65 - "BehaviorState"
Cohesion: 0.12
Nodes (17): BehaviorState, ALERT, DEFEND, DRINK, FEED, FLEE, FORAGE, HUNT (+9 more)

### Community 66 - "LandWaterIndex"
Cohesion: 0.21
Nodes (3): BlockPos, LandWaterIndex, Samples

### Community 67 - "Observation"
Cohesion: 0.32
Nodes (4): Observation, Routine, CreatureExpansionGameTests, NighttimeMindTest

### Community 68 - "2. Execution paths"
Cohesion: 0.14
Nodes (14): 1. Where the state lives, 2.1 Sedative hit, validation, torpor, knockout, synchronization, 2.2 Feeding interaction, food and hunger validation, consumption, progress, ownership, 2.3 Inventory feeding tick, eligibility, one meal, progress, 2.4 Mount request, permission, passenger attachment, rider input, movement, 2.5 Movement and state to animation, and rider positioning, 2.6 Save, load, restoration, 2. Execution paths (+6 more)

### Community 69 - ".of"
Cohesion: 0.21
Nodes (3): Nullable Path, Clips, CreatureTorporClips

### Community 70 - "CreatureRideProfile"
Cohesion: 0.18
Nodes (6): CreatureRideProfile, MovementMode, FLIGHT, GROUND, SWIM, CreatureSeats

### Community 71 - "Land Ecosystem & Behavior Patch"
Cohesion: 0.18
Nodes (8): Dev-run dependencies and settings, Runtime defaults, Families and water-associated homes, Group behavior, Land Ecosystem & Behavior Patch, Verification and remaining playtest, Work bounds and configuration, Xaero habitats

### Community 73 - "CreatureRenderer.java"
Cohesion: 0.27
Nodes (8): com.geckolib.renderer.base.RenderPassInfo, com.geckolib.renderer.GeoEntityRenderer, com.mojang.blaze3d.vertex.PoseStack, net.minecraft.client.renderer.entity.state.EntityRenderState, CreatureRenderer, Context, Override, SuppressWarnings

### Community 74 - "Taming patch proposal"
Cohesion: 0.18
Nodes (11): Animation audit and implementation contract, Familiarity and a more dangerous feeding alternative, Feedback needed before execution, Inventory, ownership and riding, Lessons from other games and mods, Multiplayer budgets and verification plan, Proposed species and food rules, Taming patch proposal (+3 more)

### Community 75 - "Theme alignment"
Cohesion: 0.18
Nodes (10): Compatibility and known limitations, Disabled or replaced (rating 4), Enforcement summary, Materials that survive, Removed (rating 5), Step 1 — the Nether and the End, Step 2 — fantasy hostile creatures, Step 3 — content decisions (+2 more)

### Community 76 - "Phase"
Cohesion: 0.18
Nodes (7): Override, Phase, AWAKE, COLLAPSING, TORPID, WAKING_TAMED, WAKING_WILD

### Community 79 - "build_taming_roster.py"
Cohesion: 0.29
Nodes (9): main(), profiles(), Render the taming roster and rider-seat tables from the code and the generated…, Registry id, display name and already size-multiplied hitbox from Species.java., One entry per `add(...)` call in the registry, keyed by the Species constant…, Realm per species, so the table can state where each method came from., realm_source(), size_band() (+1 more)

### Community 80 - "Collection ecosystem — ice, flying, aquatic and swamp"
Cohesion: 0.22
Nodes (9): Behavior and status, Collection ecosystem — ice, flying, aquatic and swamp, Compatibility and known limitations, Configuration, Day and night, Geolocation, Group settings, Realms (+1 more)

### Community 81 - "taming-proposal.md"
Cohesion: 0.22
Nodes (6): How to read this table, Known model defects that affect seating, Per-meal progress, Rider seat manifest, Taming roster and rider seats, Torpor assets

### Community 82 - "TamingAttachments"
Cohesion: 0.33
Nodes (5): net.neoforged.neoforge.attachment.AttachmentType, net.neoforged.neoforge.registries.DeferredHolder, net.neoforged.neoforge.registries.DeferredRegister, IEventBus, TamingAttachments

### Community 84 - "CreatureSize"
Cohesion: 0.29
Nodes (6): CreatureSize, GIANT, LARGE, MEDIUM, SMALL, of()

### Community 85 - "Event"
Cohesion: 0.25
Nodes (6): Event, COLLAPSE_STARTED, NONE, TORPID_ENTERED, WAKE_REQUESTED, WOKE

### Community 87 - "herd"
Cohesion: 0.33
Nodes (7): cohesionDistance(), defensiveHerd(), flyer(), groupHeightRange(), groupRadius(), herd(), timid()

### Community 90 - "Realm"
Cohesion: 0.40
Nodes (5): Realm, AIR, AMPHIBIOUS, LAND, WATER

### Community 91 - "MovementProbe"
Cohesion: 0.40
Nodes (3): ServerLevel, Vec3, MovementProbe

### Community 92 - "sleepClip"
Cohesion: 0.50
Nodes (4): landHabitat(), restClip(), sleepClip(), sleeps()

## Knowledge Gaps
- **240 isolated node(s):** `minecraft`, `loader`, `checked`, `dependencies`, `ROAM` (+235 more)
  These have ≤1 connection - possible missing edges or undocumented components.
- **14 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `CreatureEntity` connect `CreatureEntity` to `WildlifeGoal`, `FlyingCreatureEntity`, `net.minecraft.server.level.ServerLevel`, `SpawnRules.java`, `Config`, `NightEyesLayer.java`, `net.minecraft.core.BlockPos`, `net.neoforged.fml.common.EventBusSubscriber`, `.capture`, `net.neoforged.bus.api.SubscribeEvent`, `net.minecraft.world.level.Level`, `UnconsciousBehavior`, `net.minecraft.world.entity.LivingEntity`, `CreatureMountMenu`, `TamingState`, `AquaticHabitats`, `CreatureEntity.java`, `Species`, `net.minecraft.world.entity.player.Player`, `.replenish`, `net.minecraft.world.item.ItemStack`, `net.minecraft.world.phys.Vec3`, `AquaticCreatureEntity.java`, `TamingGameTests.java`, `WildlifeController`, `.initializeLevel`, `CreatureRenderer.java`, `.spawn`, `MovementProbe`, `.run`?**
  _High betweenness centrality (0.234) - this node is a cross-community bridge._
- **Why does `Species` connect `Species` to `WildlifeGoal`, `CreatureEntity`, `net.minecraft.server.level.ServerPlayer`, `ArkData`, `FlyingCreatureEntity`, `net.minecraft.server.level.ServerLevel`, `SpawnRules.java`, `Species.java`, `NestBlock`, `Config`, `NightEyesLayer.java`, `net.minecraft.core.BlockPos`, `net.neoforged.fml.common.EventBusSubscriber`, `net.minecraft.network.RegistryFriendlyByteBuf`, `HabitatPayload`, `net.minecraft.world.level.Level`, `net.minecraft.world.entity.LivingEntity`, `CreatureMountMenu`, `AquaticHabitats`, `.replenish`, `net.minecraft.world.item.ItemStack`, `AquaticCreatureEntity.java`, `TamingGameTests.java`, `LandFamily`, `.of`, `CreatureRideProfile`, `CreatureRenderer.java`, `MovementTuning`, `.spawn`, `GroupNeeds`, `MovementProbe`, `.run`, `CollectionGameTests`?**
  _High betweenness centrality (0.135) - this node is a cross-community bridge._
- **Why does `TorporState` connect `TorporState` to `net.minecraft.network.RegistryFriendlyByteBuf`, `Phase`, `net.minecraft.world.entity.LivingEntity`, `TamingAttachments`, `Event`, `.hold`, `Config`, `AquaticCreatureEntity.java`, `TamingGameTests.java`?**
  _High betweenness centrality (0.039) - this node is a cross-community bridge._
- **What connects `minecraft`, `loader`, `checked` to the rest of the system?**
  _240 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `WildlifeGoal` be split into smaller, more focused modules?**
  _Cohesion score 0.050980392156862744 - nodes in this community are weakly interconnected._
- **Should `CreatureEntity` be split into smaller, more focused modules?**
  _Cohesion score 0.06203007518796992 - nodes in this community are weakly interconnected._
- **Should `net.minecraft.server.level.ServerPlayer` be split into smaller, more focused modules?**
  _Cohesion score 0.10080645161290322 - nodes in this community are weakly interconnected._