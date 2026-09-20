# Graph Report - Ark  (2026-09-19)

## Corpus Check
- 660 files · ~192,956 words
- Verdict: corpus is large enough that graph structure adds value.

## Summary
- 1951 nodes · 4932 edges · 106 communities (88 shown, 18 thin omitted)
- Extraction: 96% EXTRACTED · 4% INFERRED · 0% AMBIGUOUS · INFERRED: 176 edges (avg confidence: 0.82)
- Token cost: 0 input · 0 output

## Graph Freshness
- Built from commit: `0da82507`
- Run `git rev-parse HEAD` and compare to check if the graph is stale.
- Run `graphify update .` after code changes (no API cost).

## Community Hubs (Navigation)
- WildlifeGoal
- Override
- DangerMapSync
- Low
- ArkData
- net.minecraft.core.BlockPos
- Snow Biome Patch — proposal
- net.minecraft.server.level.ServerLevel
- Ark Survival Returns
- WildlifeMind
- import_creatures.py
- build_test_structure.py
- gradlew
- Land Ecosystem & Behavior Patch — proposal
- Ark Survival Returns — Spawn & Biome Rules Inspection
- ProgressionData
- PlayerUnconsciousHandler
- .get
- preview_danger_map.py
- Species.java
- NestBlock
- verify_client_pack.py
- org.jspecify.annotations.Nullable
- NightEyesLayer.java
- ModContent
- DinoDebugClient
- net.neoforged.fml.common.EventBusSubscriber
- .capture
- net.minecraft.world.entity.Entity
- ArkGameTests
- .level
- DinoDebugPayload
- net.minecraft.world.item.ItemStack
- LoadedBlocks
- Land Ecosystem & Behavior Patch: flying habitats
- Wildlife behavior: research and implemented model
- org.junit.jupiter.api.Test
- UnconsciousBehavior
- Nighttime patch proposal
- dev-dependencies.json
- TargetHealthBar
- Creature expansion — 2026-09-12
- Optional client pack and difficulty map
- net.minecraft.world.entity.LivingEntity
- ThemePolicy
- TamingState
- .tick
- CreatureMountMenu
- AquaticCreatureEntity
- Species
- net.minecraft.gametest.framework.GameTestHelper
- ThemeGameTests.java
- net.minecraft.network.RegistryFriendlyByteBuf
- .ecology
- BiomeAnnouncements
- Config
- TorporState
- net.minecraft.world.phys.Vec3
- LandWildlife
- TamingGameTests.java
- LandFamily
- build_taming_manifests.py
- Placement
- BehaviorState
- DinoDebugSync
- Observation
- 2. Execution paths
- CompanionGoal
- Phase
- README.md
- .initializeLevel
- CompanionOrder
- Taming patch proposal
- Theme alignment
- WildlifeCommand
- MovementTuning
- WildlifeController
- build_taming_roster.py
- Collection ecosystem — ice, flying, aquatic and swamp
- taming-proposal.md
- TamingAttachments
- Flying Ecosystem — 2026-09-18
- CreatureSize
- MechanicGuard
- net.minecraft.world.entity.player.Player
- herd
- .registerControllers
- .strike
- Realm
- CreatureEntity
- sleepClip
- Event
- spawnCategory
- NaturalPopulations
- Entity
- net.neoforged.bus.api.SubscribeEvent
- LocomotionSignal
- .hold
- .use
- BehaviorState.java
- FlyingGameTests
- Result
- Verification — 2026-09-07
- .run

## God Nodes (most connected - your core abstractions)
1. `CreatureEntity` - 210 edges
2. `Species` - 132 edges
3. `TamingState` - 55 edges
4. `FlyingCreatureEntity` - 52 edges
5. `TorporState` - 51 edges
6. `Config` - 48 edges
7. `ModContent` - 48 edges
8. `ArkSurvivalReturns` - 45 edges
9. `TorporService` - 39 edges
10. `WildlifeGoal` - 34 edges

## Surprising Connections (you probably didn't know these)
- `Config` --references--> `Species`  [EXTRACTED]
  src/main/java/dev/nez/arksurvivalreturns/Config.java → src/main/java/dev/nez/arksurvivalreturns/feature/creature/Species.java
- `Config` --references--> `LandFamily`  [EXTRACTED]
  src/main/java/dev/nez/arksurvivalreturns/Config.java → src/main/java/dev/nez/arksurvivalreturns/feature/land/LandFamily.java
- `Config` --references--> `BiomeTier`  [EXTRACTED]
  src/main/java/dev/nez/arksurvivalreturns/Config.java → src/main/java/dev/nez/arksurvivalreturns/feature/spawn/BiomeTier.java
- `Config` --references--> `CreatureSize`  [EXTRACTED]
  src/main/java/dev/nez/arksurvivalreturns/Config.java → src/main/java/dev/nez/arksurvivalreturns/feature/taming/CreatureSize.java
- `CreatureModel` --references--> `CreatureEntity`  [EXTRACTED]
  src/main/java/dev/nez/arksurvivalreturns/client/CreatureModel.java → src/main/java/dev/nez/arksurvivalreturns/feature/creature/CreatureEntity.java

## Import Cycles
- None detected.

## Communities (106 total, 18 thin omitted)

### Community 0 - "WildlifeGoal"
Cohesion: 0.05
Nodes (15): net.neoforged.neoforge.event.entity.player.AttackEntityEvent, net.neoforged.neoforge.event.level.block.BreakBlockEvent, AquaticGoal, BlockPos, Override, NighttimeCycle, BlockPos, Override (+7 more)

### Community 1 - "Override"
Cohesion: 0.15
Nodes (8): EntityDimensions, SoundEvent, BlockPos, BlockState, DamageSource, Override, Vec3, Vec3

### Community 2 - "DangerMapSync"
Cohesion: 0.21
Nodes (5): DangerMapSync, CommandSourceStack, PlayerChangedDimensionEvent, PlayerLoggedInEvent, PlayerRespawnEvent

### Community 3 - "Low"
Cohesion: 0.07
Nodes (27): 10. Xaero danger overlay rebuilds the whole cell grid on every pan/zoom frame, 11. Development inspector (debug spyglass) ships enabled in production builds, 12. Map legend names danger ranks 4 and 5 backwards, 13. Config-permitted zero recovery rate causes permanent unconsciousness, 14. Sedative item is consumed even when the dose is rejected, and its item cost is tied to the meal size config, 15. Taming completion hearts never spawn, 16. `WildlifeGoal.save` persists the transient prey-herd anchor, not the permanent home, 17. Disabled-structure exploration maps still drop a blank map (+19 more)

### Community 4 - "ArkData"
Cohesion: 0.16
Nodes (7): CachedOutput, Client, DataProvider, PackOutput, ArkData, JsonElement, Override

### Community 5 - "net.minecraft.core.BlockPos"
Cohesion: 0.15
Nodes (13): net.minecraft.core.BlockPos, net.minecraft.util.RandomSource, net.minecraft.world.level.ServerLevelAccessor, net.minecraft.world.phys.AABB, net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent, BlockPos, Nests, BlockPos (+5 more)

### Community 6 - "Snow Biome Patch — proposal"
Cohesion: 0.12
Nodes (15): Families and water-associated homes, Group behavior, Land Ecosystem & Behavior Patch, Verification and remaining playtest, Work bounds and configuration, Xaero habitats, Current assets and integration gap, Direction (+7 more)

### Community 7 - "net.minecraft.server.level.ServerLevel"
Cohesion: 0.08
Nodes (21): net.minecraft.server.level.ServerLevel, FlyingCreatureEntity, Builder, ControllerRegistrar, DamageSource, Entity, EntityDataAccessor, EntityType (+13 more)

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

### Community 16 - "Ark Survival Returns — Spawn & Biome Rules Inspection"
Cohesion: 0.11
Nodes (18): 1. Chunk-generation spawns are rejected because the world is not a `ServerLevel`, 2. Custom population director was removed; vanilla `CREATURE` cannot replace it, 3. Chunk-gen successes would despawn at the generation frontier, 4. Water-bound species never enter the water-creature spawn path, 5. Ground predicate requires the spawn Y to equal the *highest* block of a multi-column footprint, 6. Hitbox vs `noCollision` rejects almost all large species even on legal grass, 7. Headless tests never run the vanilla spawner, so an empty world cannot fail CI, 8. Stale server.toml still describes the deleted director (+10 more)

### Community 17 - "ProgressionData"
Cohesion: 0.14
Nodes (7): com.mojang.serialization.Codec, Load, net.minecraft.world.level.saveddata.SavedData, net.minecraft.world.level.saveddata.SavedDataType, MapUnlockData, ProgressionData, Snapshot

### Community 18 - "PlayerUnconsciousHandler"
Cohesion: 0.12
Nodes (11): EntityInteract, EntityInteractSpecific, ItemTossEvent, LeftClickBlock, net.neoforged.neoforge.event.entity.EntityMountEvent, net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent, Player, RightClickBlock (+3 more)

### Community 19 - ".get"
Cohesion: 0.19
Nodes (10): net.minecraft.core.Holder, net.minecraft.world.level.biome.Biome, at(), BiomeTier, EASY, EXTREME, HARD, MODERATE (+2 more)

### Community 20 - "preview_danger_map.py"
Cohesion: 0.67
Nodes (3): danger(), main(), Plot the production danger-region formula and its discrete area shares.

### Community 21 - "Species.java"
Cohesion: 0.09
Nodes (9): additiveFood(), defaultSprintRatio(), eyeBones(), FlyerProfile, foodClip(), glowingEyes(), runClip(), sprintRatioDefault() (+1 more)

### Community 22 - "NestBlock"
Cohesion: 0.14
Nodes (19): Block, BlockHitResult, CollisionContext, com.mojang.serialization.MapCodec, LevelReader, net.minecraft.world.level.block.state.properties.BooleanProperty, ScheduledTickAccess, BlockGetter (+11 more)

### Community 24 - "org.jspecify.annotations.Nullable"
Cohesion: 0.20
Nodes (8): com.geckolib.animation.RawAnimation, Goal, net.minecraft.network.codec.StreamCodec, net.minecraft.world.InteractionHand, net.minecraft.world.level.storage.ValueInput, net.minecraft.world.level.storage.ValueOutput, net.neoforged.neoforge.common.util.ValueIOSerializable, org.jspecify.annotations.Nullable

### Community 25 - "NightEyesLayer.java"
Cohesion: 0.10
Nodes (20): com.geckolib.cache.model.GeoBone, com.geckolib.constant.dataticket.DataTicket, com.geckolib.renderer.base.RenderPassInfo, com.geckolib.renderer.GeoEntityRenderer, com.geckolib.renderer.layer.GeoRenderLayer, com.mojang.blaze3d.vertex.PoseStack, GeoRenderer, net.minecraft.client.renderer.entity.state.EntityRenderState (+12 more)

### Community 26 - "ModContent"
Cohesion: 0.14
Nodes (12): Blocks, DeferredBlock, DeferredItem, Item, Items, net.minecraft.world.entity.MobCategory, net.minecraft.world.inventory.MenuType, net.minecraft.world.item.CreativeModeTab (+4 more)

### Community 27 - "DinoDebugClient"
Cohesion: 0.21
Nodes (4): RegisterGuiLayersEvent, DinoDebugClient, LoggingOut, Post

### Community 28 - "net.neoforged.fml.common.EventBusSubscriber"
Cohesion: 0.12
Nodes (15): net.minecraft.server.level.ServerPlayer, net.minecraft.world.item.SpawnEggItem, net.neoforged.bus.api.IEventBus, net.neoforged.fml.common.EventBusSubscriber, net.neoforged.fml.common.Mod, net.neoforged.fml.ModContainer, net.neoforged.neoforge.client.event.RegisterMenuScreensEvent, net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent (+7 more)

### Community 30 - "net.minecraft.world.entity.Entity"
Cohesion: 0.14
Nodes (9): net.minecraft.commands.CommandSourceStack, net.minecraft.world.entity.Entity, CreatureRideProfile, MovementMode, FLIGHT, GROUND, SWIM, CreatureSeats (+1 more)

### Community 31 - "ArkGameTests"
Cohesion: 0.19
Nodes (4): ArkGameTests, ItemStack, ServerLevel, MovementProbe

### Community 32 - ".level"
Cohesion: 0.06
Nodes (23): FunctionalInterface, io.github.billstark001.xaerobridge.api.MapOverlayContext, net.minecraft.client.gui.screens.Screen, net.neoforged.fml.event.lifecycle.FMLClientSetupEvent, net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent, Opening, DangerMapClient, LoggingOut (+15 more)

### Community 33 - "DinoDebugPayload"
Cohesion: 0.36
Nodes (6): MouseScrollingEvent, net.minecraft.network.protocol.common.custom.CustomPacketPayload, DinoDebugPayload, Override, Type, TurnPage

### Community 34 - "net.minecraft.world.item.ItemStack"
Cohesion: 0.12
Nodes (15): net.minecraft.core.Direction, net.minecraft.core.Position, net.minecraft.world.entity.EntityType, net.minecraft.world.entity.projectile.arrow.AbstractArrow, net.minecraft.world.entity.projectile.Projectile, net.minecraft.world.item.ArrowItem, net.minecraft.world.item.ItemStack, net.minecraft.world.level.Level (+7 more)

### Community 35 - "LoadedBlocks"
Cohesion: 0.29
Nodes (5): net.minecraft.world.level.block.entity.BlockEntity, net.minecraft.world.level.BlockGetter, net.minecraft.world.level.material.FluidState, Override, LoadedBlocks

### Community 36 - "Land Ecosystem & Behavior Patch: flying habitats"
Cohesion: 0.17
Nodes (11): Current implementation versus requested behavior, Design precedents, Egg-taking contract, Habitat and nest defaults, Implementation and validation sequence, Land Ecosystem & Behavior Patch: flying habitats, Performance estimates and bounds, Phantom and extracted-animation reuse (+3 more)

### Community 37 - "Wildlife behavior: research and implemented model"
Cohesion: 0.18
Nodes (10): Acceptance evidence, ARK methods, properties and registration concepts, Bounded simulation and limits, Difficulty and apex correction, Implemented behavior contract, Main finding, Player-facing development priorities after this model, What I inspected in the installed ARK files (+2 more)

### Community 38 - "org.junit.jupiter.api.Test"
Cohesion: 0.17
Nodes (4): org.junit.jupiter.api.Test, WildlifeMindTest, LandModelTest, CreatureSizeTest

### Community 40 - "Nighttime patch proposal"
Cohesion: 0.22
Nodes (8): Behavior rules and implementation boundaries, Existing foundation, Lessons from other projects, Mechanics to skip or defer, Nighttime patch proposal, Performance validation after direction feedback, Recommended mechanics, Species scope

### Community 41 - "dev-dependencies.json"
Cohesion: 0.40
Nodes (4): checked, dependencies, loader, minecraft

### Community 42 - "TargetHealthBar"
Cohesion: 0.20
Nodes (7): net.minecraft.server.level.ServerBossEvent, ServerBossEvent, PlayerChangedDimensionEvent, PlayerLoggedOutEvent, PlayerRespawnEvent, Post, TargetHealthBar

### Community 43 - "Creature expansion — 2026-09-12"
Cohesion: 0.40
Nodes (4): Creature expansion — 2026-09-12, Scope, Sources and reproducibility, Validation

### Community 44 - "Optional client pack and difficulty map"
Cohesion: 0.29
Nodes (7): Dev settings, Difficulty map and unlock, Habitat markers removed, Installation and reproducibility, Iris development-run crash workaround, Optional client pack and difficulty map, Verification and playtest

### Community 45 - "net.minecraft.world.entity.LivingEntity"
Cohesion: 0.12
Nodes (7): Clone, net.minecraft.world.entity.LivingEntity, net.neoforged.neoforge.event.entity.living.LivingDeathEvent, Pre, Post, Pre, TorporService

### Community 46 - "ThemePolicy"
Cohesion: 0.23
Nodes (5): EntityPlaceEvent, net.minecraft.world.level.block.state.BlockState, RightClickBlock, Registry, ThemePolicy

### Community 47 - "TamingState"
Cohesion: 0.06
Nodes (4): CreatureTamingProfile, TamingService, Override, TamingState

### Community 48 - ".tick"
Cohesion: 0.16
Nodes (4): net.minecraft.world.DifficultyInstance, SpawnGroupData, EntitySpawnReason, PackData

### Community 49 - "CreatureMountMenu"
Cohesion: 0.05
Nodes (26): ArrowRenderState, com.geckolib.model.GeoModel, com.geckolib.renderer.base.GeoRenderState, net.minecraft.client.gui.GuiGraphicsExtractor, net.minecraft.client.gui.screens.inventory.AbstractContainerScreen, net.minecraft.client.renderer.entity.ArrowRenderer, net.minecraft.client.renderer.entity.state.ArrowRenderState, net.minecraft.resources.Identifier (+18 more)

### Community 50 - "AquaticCreatureEntity"
Cohesion: 0.31
Nodes (3): AquaticCreatureEntity, Builder, Override

### Community 51 - "Species"
Cohesion: 0.04
Nodes (44): Species, ACROCANTHOSAURUS, ALLOSAURUS, ANKYLOSAURUS, ARCHAEOPTERYX, ARGENTAVIS, BRONTOSAURUS, CARNOTAURUS (+36 more)

### Community 52 - "net.minecraft.gametest.framework.GameTestHelper"
Cohesion: 0.28
Nodes (3): net.minecraft.gametest.framework.GameTestHelper, Nullable Path, TamingGameTests

### Community 53 - "ThemeGameTests.java"
Cohesion: 0.07
Nodes (22): com.google.gson.JsonElement, com.google.gson.JsonObject, net.minecraft.advancements.triggers.Criterion, net.minecraft.core.RegistryAccess, net.minecraft.resources.RegistryOps, net.minecraft.resources.ResourceKey, net.minecraft.server.MinecraftServer, net.minecraft.world.item.crafting.RecipeManager (+14 more)

### Community 54 - "net.minecraft.network.RegistryFriendlyByteBuf"
Cohesion: 0.18
Nodes (3): net.minecraft.network.RegistryFriendlyByteBuf, CompanionState, Override

### Community 55 - ".ecology"
Cohesion: 0.26
Nodes (4): AABB, BlockPos, Water, AquaticGameTests

### Community 56 - "BiomeAnnouncements"
Cohesion: 0.29
Nodes (6): net.minecraft.ChatFormatting, BiomeAnnouncements, PlayerLoggedOutEvent, Post, Region, Visit

### Community 57 - "Config"
Cohesion: 0.18
Nodes (7): IntValue, net.minecraft.world.entity.SpawnGroupData, net.minecraft.world.level.block.Block, net.minecraft.world.level.block.Blocks, Config, BooleanValue, DoubleValue

### Community 59 - "net.minecraft.world.phys.Vec3"
Cohesion: 0.17
Nodes (4): net.minecraft.world.phys.Vec3, CreatureRideController, Vec3, RiderInput

### Community 60 - "LandWildlife"
Cohesion: 0.14
Nodes (4): Budget, LandWildlife, CollectionGameTests, LandGameTests

### Community 61 - "TamingGameTests.java"
Cohesion: 0.14
Nodes (12): net.minecraft.tags.TagKey, net.minecraft.world.entity.ai.goal.Goal, net.minecraft.world.entity.Mob, net.minecraft.world.item.Item, net.minecraft.world.item.Items, CompanionWhistleItem, CreatureProfileRegistry, TamingMethod (+4 more)

### Community 62 - "LandFamily"
Cohesion: 0.11
Nodes (18): family(), LandProfile, SwimProfile, LandFamily, AMPHIBIOUS, AQUATIC, BIG_CARNIVORE, BIG_HERBIVORE (+10 more)

### Community 63 - "build_taming_manifests.py"
Cohesion: 0.19
Nodes (18): classify_torpor(), cube_top(), emit_java(), forward_axis(), geometry(), head_bone(), main(), mesh_bounds() (+10 more)

### Community 64 - "Placement"
Cohesion: 0.22
Nodes (9): Placement, BOUNDS, CLEARANCE, CONFIG, DANGER, FLUID, FOOTPRINT, LOADED (+1 more)

### Community 65 - "BehaviorState"
Cohesion: 0.11
Nodes (17): BehaviorState, ALERT, DEFEND, DRINK, FEED, FLEE, FORAGE, HUNT (+9 more)

### Community 66 - "DinoDebugSync"
Cohesion: 0.16
Nodes (6): DinoDebugSync, PlayerChangedDimensionEvent, PlayerLoggedOutEvent, PlayerRespawnEvent, Post, View

### Community 67 - "Observation"
Cohesion: 0.32
Nodes (4): Observation, Routine, CreatureExpansionGameTests, NighttimeMindTest

### Community 68 - "2. Execution paths"
Cohesion: 0.14
Nodes (14): 1. Where the state lives, 2.1 Sedative hit, validation, torpor, knockout, synchronization, 2.2 Feeding interaction, food and hunger validation, consumption, progress, ownership, 2.3 Inventory feeding tick, eligibility, one meal, progress, 2.4 Mount request, permission, passenger attachment, rider input, movement, 2.5 Movement and state to animation, and rider positioning, 2.6 Save, load, restoration, 2. Execution paths (+6 more)

### Community 69 - "CompanionGoal"
Cohesion: 0.18
Nodes (4): CompanionGoal, Override, CompanionService, CompanionGameTests

### Community 70 - "Phase"
Cohesion: 0.18
Nodes (7): Override, Phase, AWAKE, COLLAPSING, TORPID, WAKING_TAMED, WAKING_WILD

### Community 71 - "README.md"
Cohesion: 0.20
Nodes (5): Debug Spyglass, Verification, Dev-run dependencies and settings, Runtime defaults, Movement tuning

### Community 73 - "CompanionOrder"
Cohesion: 0.28
Nodes (6): byOrdinal(), CompanionOrder, FOLLOW, STAY, WANDER, next()

### Community 74 - "Taming patch proposal"
Cohesion: 0.18
Nodes (11): Animation audit and implementation contract, Familiarity and a more dangerous feeding alternative, Feedback needed before execution, Inventory, ownership and riding, Lessons from other games and mods, Multiplayer budgets and verification plan, Proposed species and food rules, Taming patch proposal (+3 more)

### Community 75 - "Theme alignment"
Cohesion: 0.18
Nodes (10): Compatibility and known limitations, Disabled or replaced (rating 4), Enforcement summary, Materials that survive, Removed (rating 5), Step 1 — the Nether and the End, Step 2 — fantasy hostile creatures, Step 3 — content decisions (+2 more)

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

### Community 83 - "Flying Ecosystem — 2026-09-18"
Cohesion: 0.33
Nodes (6): Animation and map assets, Behavior, Flying Ecosystem — 2026-09-18, Spawning and nesting, Validation, Work limits

### Community 84 - "CreatureSize"
Cohesion: 0.29
Nodes (6): CreatureSize, GIANT, LARGE, MEDIUM, SMALL, of()

### Community 85 - "MechanicGuard"
Cohesion: 0.25
Nodes (4): net.neoforged.neoforge.event.entity.living.LivingUseTotemEvent, Pre, RightClickItem, MechanicGuard

### Community 86 - "net.minecraft.world.entity.player.Player"
Cohesion: 0.12
Nodes (7): net.minecraft.core.particles.ParticleOptions, net.minecraft.network.chat.Component, net.minecraft.world.entity.player.Player, net.minecraft.world.InteractionResult, Component, message(), TamingFeedback

### Community 87 - "herd"
Cohesion: 0.33
Nodes (7): cohesionDistance(), defensiveHerd(), flyer(), groupHeightRange(), groupRadius(), herd(), timid()

### Community 90 - "Realm"
Cohesion: 0.40
Nodes (5): Realm, AIR, AMPHIBIOUS, LAND, WATER

### Community 91 - "CreatureEntity"
Cohesion: 0.10
Nodes (8): com.geckolib.animatable.GeoEntity, com.geckolib.animatable.instance.AnimatableInstanceCache, PathfinderMob, CreatureEntity, Builder, EntityDataAccessor, EntityType, LivingEntity

### Community 92 - "sleepClip"
Cohesion: 0.50
Nodes (4): landHabitat(), restClip(), sleepClip(), sleeps()

### Community 93 - "Event"
Cohesion: 0.25
Nodes (6): Event, COLLAPSE_STARTED, NONE, TORPID_ENTERED, WAKE_REQUESTED, WOKE

### Community 95 - "NaturalPopulations"
Cohesion: 0.16
Nodes (3): Post, NaturalPopulations, SpawnerGameTests

### Community 97 - "net.neoforged.bus.api.SubscribeEvent"
Cohesion: 0.09
Nodes (15): net.neoforged.bus.api.SubscribeEvent, net.neoforged.neoforge.event.entity.EntityJoinLevelEvent, net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent, net.neoforged.neoforge.event.entity.player.PlayerSpawnPhantomsEvent, net.neoforged.neoforge.event.level.ModifyCustomSpawnersEvent, net.neoforged.neoforge.event.server.ServerStartedEvent, net.neoforged.neoforge.event.server.ServerStoppedEvent, net.neoforged.neoforge.event.village.VillageSiegeEvent (+7 more)

### Community 104 - "Result"
Cohesion: 0.22
Nodes (9): Result, ACCEPTED, ALREADY_TAMED, COOLDOWN, EXCLUDED, NOT_CLAIMANT, NOT_HUNGRY, WRONG_FOOD (+1 more)

### Community 105 - "Verification — 2026-09-07"
Cohesion: 0.40
Nodes (4): Gameplay coverage, Map and client pack coverage, Verification — 2026-09-07, World compatibility and scope

### Community 109 - ".run"
Cohesion: 0.18
Nodes (5): net.neoforged.neoforge.registries.RegisterEvent, DebugSpyglass, DinoDebugGameTests, Inventory, RegistryFriendlyByteBuf

## Knowledge Gaps
- **286 isolated node(s):** `minecraft`, `loader`, `checked`, `dependencies`, `ROAM` (+281 more)
  These have ≤1 connection - possible missing edges or undocumented components.
- **18 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `CreatureEntity` connect `CreatureEntity` to `WildlifeGoal`, `Override`, `net.minecraft.core.BlockPos`, `net.minecraft.server.level.ServerLevel`, `org.jspecify.annotations.Nullable`, `NightEyesLayer.java`, `ModContent`, `DinoDebugClient`, `net.neoforged.fml.common.EventBusSubscriber`, `.capture`, `ArkGameTests`, `net.minecraft.world.item.ItemStack`, `TargetHealthBar`, `net.minecraft.world.entity.LivingEntity`, `TamingState`, `.tick`, `CreatureMountMenu`, `AquaticCreatureEntity`, `Species`, `net.minecraft.gametest.framework.GameTestHelper`, `Config`, `net.minecraft.world.phys.Vec3`, `LandWildlife`, `TamingGameTests.java`, `CompanionGoal`, `.initializeLevel`, `WildlifeController`, `net.minecraft.world.entity.player.Player`, `.registerControllers`, `.strike`, `NaturalPopulations`, `Entity`, `LocomotionSignal`, `.run`?**
  _High betweenness centrality (0.241) - this node is a cross-community bridge._
- **Why does `Species` connect `Species` to `WildlifeGoal`, `ArkData`, `net.minecraft.core.BlockPos`, `net.minecraft.server.level.ServerLevel`, `.get`, `Species.java`, `NestBlock`, `org.jspecify.annotations.Nullable`, `NightEyesLayer.java`, `ModContent`, `net.neoforged.fml.common.EventBusSubscriber`, `net.minecraft.world.entity.Entity`, `ArkGameTests`, `net.minecraft.world.item.ItemStack`, `net.minecraft.world.entity.LivingEntity`, `TamingState`, `CreatureMountMenu`, `net.minecraft.gametest.framework.GameTestHelper`, `.ecology`, `Config`, `LandWildlife`, `TamingGameTests.java`, `LandFamily`, `WildlifeCommand`, `MovementTuning`, `.strike`, `CreatureEntity`, `NaturalPopulations`, `FlyingGameTests`, `.run`?**
  _High betweenness centrality (0.103) - this node is a cross-community bridge._
- **Why does `TorporState` connect `TorporState` to `.hold`, `Phase`, `net.minecraft.server.level.ServerLevel`, `net.minecraft.world.entity.LivingEntity`, `TamingAttachments`, `TamingGameTests.java`, `net.minecraft.gametest.framework.GameTestHelper`, `net.minecraft.network.RegistryFriendlyByteBuf`, `org.jspecify.annotations.Nullable`, `Event`?**
  _High betweenness centrality (0.044) - this node is a cross-community bridge._
- **What connects `minecraft`, `loader`, `checked` to the rest of the system?**
  _286 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `WildlifeGoal` be split into smaller, more focused modules?**
  _Cohesion score 0.05093632958801498 - nodes in this community are weakly interconnected._
- **Should `Override` be split into smaller, more focused modules?**
  _Cohesion score 0.14705882352941177 - nodes in this community are weakly interconnected._
- **Should `Low` be split into smaller, more focused modules?**
  _Cohesion score 0.07142857142857142 - nodes in this community are weakly interconnected._