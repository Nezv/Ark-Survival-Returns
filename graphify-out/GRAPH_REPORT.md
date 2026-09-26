# Graph Report - Ark-Survival-Returns  (2026-09-20)

## Corpus Check
- 432 files · ~628,836 words
- Verdict: corpus is large enough that graph structure adds value.

## Summary
- 3350 nodes · 9172 edges · 171 communities (144 shown, 27 thin omitted)
- Extraction: 96% EXTRACTED · 4% INFERRED · 0% AMBIGUOUS · INFERRED: 351 edges (avg confidence: 0.82)
- Token cost: 0 input · 0 output

## Graph Freshness
- Built from commit: `aaf66c08`
- Run `git rev-parse HEAD` and compare to check if the graph is stale.
- Run `graphify update .` after code changes (no API cost).

## Community Hubs (Navigation)
- net.minecraft.core.BlockPos
- skin_studio.py
- ModContent
- PlayerUnconsciousHandler
- Species
- FlyingCreatureEntity
- AquaticGoal
- TamingState
- net.minecraft.world.phys.Vec3
- Dinosaur Overview
- net.neoforged.bus.api.SubscribeEvent
- org.spongepowered.asm.mixin.Mixin
- .loaded
- WildlifeMind
- .registerControllers
- .of
- ClonedClientLevel
- ThemeGameTests.java
- import_creatures.py
- preview_dinosaurs.py
- net.minecraft.server.level.ServerPlayer
- NestBlock
- net.minecraft.world.entity.LivingEntity
- WildlifeGoal
- HostileGuard.java
- TorporState
- ArkData
- org.junit.jupiter.api.Test
- Wildlife Behavior Research and Implemented Model
- net.minecraft.world.item.ItemStack
- net.minecraft.world.item.Item
- Taming, Torpor and Riding Debugging Guide
- ArkAudioEngine
- .cells
- Species.java
- Land Ecosystem Proposal
- CompanionGoal
- ProgressionData
- SoundPhysics.java
- CookingPotBlockEntity
- DinoDebugPayload
- NaturalPopulations
- restore_batch_fitting.py
- ReverbParams
- DangerMapClient
- LandFamily
- DownedState
- Collection Ecosystem (ice, flying, aquatic, swamp)
- TaskProfiler
- CreatureMountMenu
- .strike
- WildlifeController
- .level
- build_dinosaurs.py
- NightEyesLayer.java
- .ecology
- net.minecraft.world.entity.Entity
- build_taming_manifests.py
- ark_geometry.py
- CreatureRideController
- Ark Survival Returns Changelog
- extract_dinosaurs.py
- EntityMixin.java
- MassEvents
- Observation
- CompanionOrder
- DinoDebugClient
- .refresh
- net.minecraft.client.multiplayer.ClientLevel
- Optional Client Pack and Difficulty Map
- Code Inspection Report 2026-09-19
- Survival Progression Roadmap
- CreatureEntity
- TargetHealthBar
- TribeService
- WorkGoal
- .capture
- CreatureTamingProfile
- CreatureRideProfile
- worker-result.schema.json
- JournalClient.java
- MassRules
- BehaviorState.java
- .initializeLevel
- net.minecraft.world.entity.player.Player
- Ark Survival Returns README
- .tamingInventory
- net.minecraft.client.Minecraft
- .progression
- build_camp_assets.py
- .think
- Verify
- net.minecraft.gametest.framework.GameTestHelper
- net.minecraft.server.level.ServerLevel
- net.minecraft.world.level.block.state.BlockState
- SedativeArrow
- .getPassengerAttachmentPoint
- BehaviorState
- build_taming_roster.py
- RecoveryPolicy
- WildlifeNoise
- DinoDebugSync
- Unified Audio Engine
- Spawn and Biome Rules Inspection 2026-09-19
- Placement
- ArkGameTests.java
- Camp and recovery
- BiomeTier
- LoadedBlocks
- DeepSeek Worker Skill
- BerryBushBlock.java
- LandWildlife
- DangerMapSync
- DownedPolicy
- CreatureSize
- CargoProfiles
- main
- Progression bypass audit
- net.minecraft.network.RegistryFriendlyByteBuf
- Creature source projects
- Theropod skin batch
- herd
- TamingFeedback
- Homestead Economy
- StarterKitService
- verify_client_pack.py
- CreatureRenderer
- Procedural creature textures
- TroughBlockEntity
- build_test_structure.py
- Result
- CreatureProfileRegistry
- SedativeArrowItem.java
- .getControllingPassenger
- LocomotionSignal
- BedrollBlock
- dev-dependencies.json
- Realm
- WildlifeCommand
- Load
- Weight and Working Tames
- build_item_assets.py
- gradlew
- sleepClip
- preview_danger_map.py
- inventory_ark_behavior.py
- .transfer
- ARK to GeckoLib dinosaur workflow README
- spawnCategory
- Assets
- Flying Habitats Proposal
- Mod integration plan: Ancient Remnants and Icy's Better Horses
- LibraryMixin.java
- NighttimeCycle
- RecoveryEvents
- WorkProfiles
- Python runtime requirements file
- .AmphibiousCreatureEntity
- Food
- ConcentratedSedativeItem
- Preset
- .meal
- Workspace Standards
- .deserialize

## God Nodes (most connected - your core abstractions)
1. `CreatureEntity` - 258 edges
2. `Species` - 145 edges
3. `ModContent` - 90 edges
4. `ArkSurvivalReturns` - 77 edges
5. `Config` - 77 edges
6. `TamingState` - 55 edges
7. `FlyingCreatureEntity` - 53 edges
8. `TorporState` - 51 edges
9. `Dinosaur Overview` - 47 edges
10. `ArkData` - 43 edges

## Surprising Connections (you probably didn't know these)
- `contact_sheet()` --references--> `Dinosaur Overview`  [INFERRED]
  scripts/preview_dinosaurs.py → dinosaur_overview.png
- `Standardized patch methodology` --conceptually_related_to--> `Ark Survival Returns Changelog`  [INFERRED]
  Standard.md → CHANGELOG.md
- `ARK to GeckoLib dinosaur workflow README` --shares_data_with--> `Creatures distribution document`  [INFERRED]
  README.md → Creatures.md
- `corners()` --calls--> `pose()`  [INFERRED]
  Ark/tools/verify_expansion.py → scripts/preview_dinosaurs.py
- `main()` --calls--> `decode_geometry()`  [INFERRED]
  Ark/tools/verify_expansion.py → scripts/preview_dinosaurs.py

## Import Cycles
- None detected.

## Hyperedges (group relationships)
- **Ark creature conversion pipeline** — readme_conversion_pipeline, scripts_readme_workflow, scripts_readme_extract_dinosaurs, scripts_readme_ark_geometry, scripts_readme_build_dinosaurs, scripts_readme_ark_animation, scripts_readme_preview_dinosaurs, scripts_readme_validate_dinosaurs [INFERRED 0.85]
- **September 2026 code inspection and audit reports** — ark_docs_inspection_report_2026_09_19_code_inspection_report, ark_docs_spawn_inspection_2026_09_19_spawn_biome_rules_inspection, ark_docs_deepseek_v4_1_audio_audit_audio_loader_conflict_audit [INFERRED 0.85]
- **Aquatic and Semi-Aquatic Roster** — dinosaur_overview_tusoteuthis, dinosaur_overview_cnidaria, dinosaur_overview_mosasaurus, dinosaur_overview_megalodon, dinosaur_overview_plesiosaur, dinosaur_overview_liopleurodon, dinosaur_overview_kaprosuchus, dinosaur_overview_deinosuchus, dinosaur_overview_sarco, dinosaur_overview_titanoboa [INFERRED 0.85]
- **Flying Creature Roster** — dinosaur_overview_argentavis, dinosaur_overview_piterodon, dinosaur_overview_quetzal, dinosaur_overview_archaeopteryx, dinosaur_overview_dragon [INFERRED 0.85]
- **Bipedal Theropod Roster** — dinosaur_overview_giganotosaur, dinosaur_overview_velociraptor, dinosaur_overview_therezinosaur, dinosaur_overview_tyranosaur, dinosaur_overview_spinosaurus, dinosaur_overview_ceratosaurus, dinosaur_overview_dilophosaur, dinosaur_overview_acrochantosaur, dinosaur_overview_allosaurus, dinosaur_overview_carnotaurus [INFERRED 0.85]
- **Land, flying, collection and snow habitat design** — ark_docs_land_ecosystem_land_ecosystem, ark_docs_flying_ecosystem_flying_ecosystem, ark_docs_collection_ecosystem_collection_ecosystem, ark_docs_snow_ecosystem_proposal_snow_biome_proposal [INFERRED 0.95]
- **Taming, torpor, riding and inspection subsystem** — ark_docs_taming_proposal_taming_patch_proposal, ark_docs_taming_roster_taming_roster, ark_docs_taming_debugging_taming_debugging_guide, ark_docs_debug_spyglass_debug_spyglass [INFERRED 0.95]

## Communities (171 total, 27 thin omitted)

### Community 0 - "net.minecraft.core.BlockPos"
Cohesion: 0.10
Nodes (18): ClonedLevelChunk, Override, Override, UnsafeClientLevel, LevelChunkTicks, net.minecraft.core.BlockPos, net.minecraft.nbt.CompoundTag, net.minecraft.world.level.block.entity.BlockEntity (+10 more)

### Community 1 - "skin_studio.py"
Cohesion: 0.07
Nodes (53): add_details(), assign_uvs(), base_skin(), BodyField, build_quant_palette(), build_skin(), choose_tex_size(), compose() (+45 more)

### Community 2 - "ModContent"
Cohesion: 0.06
Nodes (41): Config, BooleanValue, DoubleValue, CompanionService, CookingPotMenu, MassCalculator, MassService, SpawnRules (+33 more)

### Community 3 - "PlayerUnconsciousHandler"
Cohesion: 0.13
Nodes (10): ItemTossEvent, Player, RightClickBlock, RightClickItem, PlayerUnconsciousHandler, EntityInteract, EntityInteractSpecific, LeftClickBlock (+2 more)

### Community 4 - "Species"
Cohesion: 0.04
Nodes (46): EntityType, EntityType, Species, ACROCANTHOSAURUS, ALLOSAURUS, ANKYLOSAURUS, ARCHAEOPTERYX, ARGENTAVIS (+38 more)

### Community 5 - "FlyingCreatureEntity"
Cohesion: 0.07
Nodes (19): FlyingCreatureEntity, Builder, ControllerRegistrar, DamageSource, Entity, EntityDataAccessor, Override, ValueInput (+11 more)

### Community 6 - "AquaticGoal"
Cohesion: 0.17
Nodes (3): AquaticGoal, BlockPos, Override

### Community 8 - "net.minecraft.world.phys.Vec3"
Cohesion: 0.12
Nodes (11): Ray, RaycastRenderer, Entry, Vec3, ReflectedAudio, Vec3, SoundPhysics, net.minecraft.resources.Identifier (+3 more)

### Community 9 - "Dinosaur Overview"
Cohesion: 0.05
Nodes (47): Acrochantosaur (Acrocanthosaurus), Allosaurus, Ankylosaurus, Archaeopteryx, Argentavis, Bind Pose Previews, Brontosaur (Brontosaurus), Carnotaurus (+39 more)

### Community 10 - "net.neoforged.bus.api.SubscribeEvent"
Cohesion: 0.04
Nodes (40): ArkSurvivalReturns, DownedClient, LoggingOut, KitchenClient, LoggingOut, MassClient, RegisterRenderers, TamingClient (+32 more)

### Community 11 - "org.spongepowered.asm.mixin.Mixin"
Cohesion: 0.15
Nodes (13): ChannelAccessor, ClientPacketListenerMixin, LocalPlayerMixin, SourceMixin, com.mojang.blaze3d.audio.Channel, net.minecraft.client.multiplayer.ClientPacketListener, net.minecraft.client.player.LocalPlayer, net.minecraft.network.protocol.game.ClientboundLoginPacket (+5 more)

### Community 12 - ".loaded"
Cohesion: 0.16
Nodes (8): BlockPos, Nests, AABB, BlockPos, EntitySpawnReason, EntityType, net.minecraft.world.level.ServerLevelAccessor, net.minecraft.world.phys.AABB

### Community 14 - ".registerControllers"
Cohesion: 0.13
Nodes (5): AquaticCreatureEntity, Builder, ControllerRegistrar, Override, ControllerRegistrar

### Community 16 - "ClonedClientLevel"
Cohesion: 0.10
Nodes (12): ClientLevelMixin, CachingClientLevel, ClientLevelProxy, ClonedClientLevel, ChunkPos, Override, ClonedLevelHeightAccessor, Override (+4 more)

### Community 17 - "ThemeGameTests.java"
Cohesion: 0.05
Nodes (23): Override, UnconsciousBehavior, DimensionGuard, PlayerLoggedInEvent, PlayerRespawnEvent, LootGuard, EntityPlaceEvent, Registry (+15 more)

### Community 18 - "import_creatures.py"
Cohesion: 0.10
Nodes (26): import_clips(), Asset/controller contracts for the ice, flying, aquatic and swamp collection.…, Ordered clip names the importer must copy. Index 0 is idle, 2 is the attack…, Asset/controller contracts for the September 12 creature expansion. Family…, main(), Import only runtime clips; normalize geometry and position tracks together.…, Authored quiet standing sleep: stable feet, lowered head, closed eyes and slow…, Resolve the read-only originals the importer reads. Keys match the report's… (+18 more)

### Community 19 - "preview_dinosaurs.py"
Cohesion: 0.15
Nodes (17): main(), Offline preview of the imported runtime resources, using the repository's…, njit, _draw(), rasterize(), Optional accelerated depth-buffer rasterizer for dense model previews., decode_geometry(), DecodedGeometry (+9 more)

### Community 20 - "net.minecraft.server.level.ServerPlayer"
Cohesion: 0.12
Nodes (9): Owned, RecoveryData, RecoveryEntry, Fallback, RecoveryService, ServerPlayer, RecoveryGameTests, net.minecraft.resources.ResourceKey (+1 more)

### Community 21 - "NestBlock"
Cohesion: 0.10
Nodes (19): BlockGetter, BlockHitResult, BlockPos, BlockState, Builder, CollisionContext, Direction, InteractionHand (+11 more)

### Community 22 - "net.minecraft.world.entity.LivingEntity"
Cohesion: 0.13
Nodes (5): Pre, Clone, Pre, TorporService, net.minecraft.world.entity.LivingEntity

### Community 23 - "WildlifeGoal"
Cohesion: 0.18
Nodes (3): BlockPos, Override, WildlifeGoal

### Community 24 - "HostileGuard.java"
Cohesion: 0.08
Nodes (16): HostileGuard, Pre, Pre, RightClickBlock, RightClickItem, MechanicGuard, net.minecraft.world.item.SpawnEggItem, net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent (+8 more)

### Community 25 - "TorporState"
Cohesion: 0.05
Nodes (15): Event, COLLAPSE_STARTED, NONE, TORPID_ENTERED, WAKE_REQUESTED, WOKE, Override, Vec3 (+7 more)

### Community 26 - "ArkData"
Cohesion: 0.09
Nodes (9): ArkData, JsonElement, Override, CampAssetsData, CachedOutput, Client, DataProvider, Gson (+1 more)

### Community 27 - "org.junit.jupiter.api.Test"
Cohesion: 0.14
Nodes (5): WildlifeMindTest, LandModelTest, CreatureSizeTest, TribePermissionTest, org.junit.jupiter.api.Test

### Community 28 - "Wildlife Behavior Research and Implemented Model"
Cohesion: 0.12
Nodes (19): ASE ARK Server API Controller Interfaces, Implemented Behavior Contract, ark-behavior-inventory.json (213 package records), Bounded Simulation Limits, Horizon: Zero Dawn (inspiration), Hunt: Showdown (inspiration), theHunter: Call of the Wild (inspiration), Unreal AI Perception and Behavior Trees (+11 more)

### Community 29 - "net.minecraft.world.item.ItemStack"
Cohesion: 0.07
Nodes (11): DryingRackBlockEntity, ItemStack, Override, Override, Override, Override, SedativeItem, FarmGameTests (+3 more)

### Community 30 - "net.minecraft.world.item.Item"
Cohesion: 0.11
Nodes (11): CampMaterials, CompanionWhistleItem, FarmCrops, RightClickBlock, FarmTags, MassTags, TamingTags, WorkTags (+3 more)

### Community 31 - "Taming, Torpor and Riding Debugging Guide"
Cohesion: 0.10
Nodes (25): DangerMapSync Entitlement, Weight and Logistics System, /arktaming Operator Commands, CompanionGoal (FOLLOW/STAY/WANDER), CreatureAnimationBridge, CreatureEntity Riding and Tame State, CreatureInventory, CreatureEntity.strike Hit-Frame Timing (+17 more)

### Community 32 - "ArkAudioEngine"
Cohesion: 0.13
Nodes (13): ArkAudioEngine, LoggingOut, Post, Ambience, AudioCatalog, Data, Footstep, Override (+5 more)

### Community 33 - ".cells"
Cohesion: 0.14
Nodes (9): Override, XaeroExploration, Cell, DangerMapView, Exploration, DangerBands, DangerMapViewTest, FunctionalInterface (+1 more)

### Community 34 - "Species.java"
Cohesion: 0.09
Nodes (9): additiveFood(), defaultSprintRatio(), eyeBones(), FlyerProfile, foodClip(), glowingEyes(), runClip(), sprintRatioDefault() (+1 more)

### Community 35 - "Land Ecosystem Proposal"
Cohesion: 0.10
Nodes (24): Ark Repository Conventions (AGENTS.md), Bounded Collision Lookahead, Flyer Egg Collection and Defense, Flying Ecosystem Implementation, One Local Nest per Bird, NestBlock.disturb, Forced One-Shot Animation Clips, Shared Group Hunger Clock (+16 more)

### Community 36 - "CompanionGoal"
Cohesion: 0.18
Nodes (3): CompanionGoal, Override, CompanionGameTests

### Community 38 - "SoundPhysics.java"
Cohesion: 0.13
Nodes (11): SoundPhysicsConfig, Value, Override, DebugRendererMixin, SimpleDebugRenderer, SoundPhysicsMod, RaycastUtils, net.minecraft.client.renderer.culling.Frustum (+3 more)

### Community 39 - "CookingPotBlockEntity"
Cohesion: 0.09
Nodes (9): CookingPotBlockEntity, Override, CookingRecipes, ItemStack, CreatureInventory, Override, ServerLevel, Vec3 (+1 more)

### Community 40 - "DinoDebugPayload"
Cohesion: 0.19
Nodes (7): DinoDebugPayload, Override, Type, TurnPage, DinoDebugGameTests, Inventory, RegistryFriendlyByteBuf

### Community 41 - "NaturalPopulations"
Cohesion: 0.15
Nodes (4): BlockPos, Post, NaturalPopulations, SpawnerGameTests

### Community 42 - "restore_batch_fitting.py"
Cohesion: 0.36
Nodes (10): digest(), outline_skeleton(), Project an outliner onto its bone hierarchy, ignoring cube UUID leaves., bones_only(), main(), prepare(), protected_hashes(), Restore the earlier cuboid fitter without regenerating skeletons or clips. Run… (+2 more)

### Community 44 - "DangerMapClient"
Cohesion: 0.15
Nodes (7): DangerMapClient, LoggingOut, Post, View, XaeroDangerOverlay, io.github.billstark001.xaerobridge.api.MapOverlayContext, Opening

### Community 45 - "LandFamily"
Cohesion: 0.11
Nodes (18): family(), LandProfile, SwimProfile, LandFamily, AMPHIBIOUS, AQUATIC, BIG_CARNIVORE, BIG_HERBIVORE (+10 more)

### Community 46 - "DownedState"
Cohesion: 0.13
Nodes (5): DownedHandler, Post, DownedState, Override, Vec3

### Community 47 - "Collection Ecosystem (ice, flying, aquatic, swamp)"
Cohesion: 0.11
Nodes (18): WildlifeMind Decision Model, LandWildlife.coldHydration, Collection Ecosystem (ice, flying, aquatic, swamp), Collection Group Settings Table, Realm System (WATER/AMPHIBIOUS/LAND/AIR), Acrocanthosaurus Rig, Ceratosaurus Native Bind Rotations, Creature Expansion (ten new creatures) (+10 more)

### Community 48 - "TaskProfiler"
Cohesion: 0.14
Nodes (6): Loggers, TaskProfiler, TaskProfilerHandle, java.lang.ref.WeakReference, org.apache.logging.log4j.Logger, WeakReference

### Community 49 - "CreatureMountMenu"
Cohesion: 0.10
Nodes (10): CookingPotScreen, Override, CreatureMountScreen, Override, CreatureMountMenu, message(), net.minecraft.client.gui.screens.inventory.AbstractContainerScreen, net.minecraft.client.input.MouseButtonEvent (+2 more)

### Community 51 - "WildlifeController"
Cohesion: 0.09
Nodes (3): WildlifeController, MovementTuning, MovementTuningTest

### Community 53 - "build_dinosaurs.py"
Cohesion: 0.15
Nodes (27): animation_tracks(), convert_clip(), is_loop(), PSA -> zero-rest-rotation bone tracks, with a source-FK round-trip check. The…, Bounded linear key reduction; all retained values remain source samples., read_clips(), reduce_linear(), blockbench() (+19 more)

### Community 54 - "NightEyesLayer.java"
Cohesion: 0.11
Nodes (16): CreatureModel, Override, Override, RenderPassInfo, NightEyesLayer, BooleanValue, DoubleValue, NighttimeClientConfig (+8 more)

### Community 55 - ".ecology"
Cohesion: 0.29
Nodes (4): AABB, BlockPos, Water, AquaticGameTests

### Community 56 - "net.minecraft.world.entity.Entity"
Cohesion: 0.42
Nodes (3): TamingCommands, net.minecraft.commands.CommandSourceStack, net.minecraft.world.entity.Entity

### Community 57 - "build_taming_manifests.py"
Cohesion: 0.19
Nodes (18): classify_torpor(), cube_top(), emit_java(), forward_axis(), geometry(), head_bone(), main(), mesh_bounds() (+10 more)

### Community 58 - "ark_geometry.py"
Cohesion: 0.16
Nodes (13): bone_names(), chunks(), ActorX/glTF readers and coordinate conversion shared by the offline build., Skeleton, palette_index(), font(), main(), overview() (+5 more)

### Community 59 - "CreatureRideController"
Cohesion: 0.14
Nodes (4): Vec3, CreatureRideController, Vec3, RiderInput

### Community 60 - "Ark Survival Returns Changelog"
Cohesion: 0.14
Nodes (20): Ark mod CI workflow, Gradle runData/build/runGameTestServer release gate, Ark Survival Returns Changelog, Anatomy-Driven Creature Textures patch, Apex Encounters and Night Eyes patch, Beta baseline, Creature Expansion patch, Debug Spyglass patch (+12 more)

### Community 61 - "extract_dinosaurs.py"
Cohesion: 0.21
Nodes (15): asset_directory(), extract(), normalize(), Extract installed ARK adults without modifying game files. Package adaptation:…, Decode ARK's chunked zlib workshop format into workspace copies., unpack_workshop(), main(), Build the source-only ice, flying, aquatic and swamp creature collection. (+7 more)

### Community 62 - "EntityMixin.java"
Cohesion: 0.25
Nodes (7): EntityMixin, SoundEventMixin, SoundUtils, java.util.regex.Pattern, net.minecraft.sounds.SoundEvent, org.spongepowered.asm.mixin.injection.ModifyConstant, org.spongepowered.asm.mixin.Shadow

### Community 63 - "MassEvents"
Cohesion: 0.10
Nodes (12): Clone, PlayerChangedDimensionEvent, PlayerLoggedInEvent, PlayerRespawnEvent, Post, MassEvents, Close, ItemCraftedEvent (+4 more)

### Community 64 - "Observation"
Cohesion: 0.32
Nodes (4): Observation, Routine, CreatureExpansionGameTests, NighttimeMindTest

### Community 65 - "CompanionOrder"
Cohesion: 0.24
Nodes (7): byOrdinal(), CompanionOrder, FOLLOW, STAY, WANDER, WORK, next()

### Community 66 - "DinoDebugClient"
Cohesion: 0.17
Nodes (6): DinoDebugClient, LoggingOut, Post, RegisterGuiLayersEvent, DebugSpyglass, MouseScrollingEvent

### Community 67 - ".refresh"
Cohesion: 0.15
Nodes (3): PlayerLoggedOutEvent, MassSync, net.minecraft.server.MinecraftServer

### Community 68 - "net.minecraft.client.multiplayer.ClientLevel"
Cohesion: 0.23
Nodes (5): MinecraftMixin, LevelAccessUtils, SoundRateManager, net.minecraft.client.gui.screens.Screen, net.minecraft.client.multiplayer.ClientLevel

### Community 69 - "Optional Client Pack and Difficulty Map"
Cohesion: 0.18
Nodes (14): Complementary Reimagined, Difficulty Map and Unlock, Iris GL Validation Workaround, Install-Ark-Extras Installer, Iris, Optional Client Pack and Difficulty Map, Sodium, Xaero World Map Bridge (+6 more)

### Community 70 - "Code Inspection Report 2026-09-19"
Cohesion: 0.14
Nodes (14): Debug Spyglass, DinoDebugSnapshot.capture, Debug Spyglass Page Budget, Code Inspection Report 2026-09-19, Defect: Debug Spyglass Ships in Production, Defect: Land think() Scan Cost, Defect: Mount Screen Labels Double Offset, Defect: Tamed Creatures Keep Wild AI (+6 more)

### Community 71 - "Survival Progression Roadmap"
Cohesion: 0.14
Nodes (14): Guardian Encounter Ladder, Mod Integration Decisions, Recovery Policy, Self-Determination Theory Research, Four-Stage Campaign Architecture, Survival Progression Roadmap, Shared Tribe Journal, WHO Gaming Disorder Definition (+6 more)

### Community 72 - "CreatureEntity"
Cohesion: 0.06
Nodes (17): CreatureEntity, BlockPos, BlockState, Builder, DamageSource, Entity, EntityDataAccessor, EntitySpawnReason (+9 more)

### Community 73 - "TargetHealthBar"
Cohesion: 0.18
Nodes (7): PlayerChangedDimensionEvent, PlayerLoggedOutEvent, PlayerRespawnEvent, Post, TargetHealthBar, net.minecraft.server.level.ServerBossEvent, ServerBossEvent

### Community 74 - "TribeService"
Cohesion: 0.07
Nodes (15): TribeCommands, allows(), byName(), TribePermission, BREEDING, CARGO, COMMANDS, RIDE (+7 more)

### Community 75 - "WorkGoal"
Cohesion: 0.14
Nodes (4): ItemStack, Override, WorkGoal, WorkGameTests

### Community 77 - "CreatureTamingProfile"
Cohesion: 0.15
Nodes (5): CreatureTamingProfile, TamingMethod, AERIAL, KNOCKOUT, PASSIVE

### Community 78 - "CreatureRideProfile"
Cohesion: 0.18
Nodes (6): CreatureRideProfile, MovementMode, FLIGHT, GROUND, SWIM, CreatureSeats

### Community 79 - "worker-result.schema.json"
Cohesion: 0.17
Nodes (11): additionalProperties, properties, summary, unified_diff, required, $schema, type, type (+3 more)

### Community 80 - "JournalClient.java"
Cohesion: 0.11
Nodes (9): ArkClient, RegisterRenderers, Post, JournalClient, JournalOpener, Category, net.minecraft.client.KeyMapping, net.neoforged.fml.event.lifecycle.FMLClientSetupEvent (+1 more)

### Community 81 - "MassRules"
Cohesion: 0.23
Nodes (4): MassRules, Profile, MassGameTests, MassRulesTest

### Community 84 - "net.minecraft.world.entity.player.Player"
Cohesion: 0.14
Nodes (21): CampShapes, FiberBandageItem, LivingEntity, Override, FieldJournalItem, Override, net.minecraft.core.Direction, net.minecraft.util.RandomSource (+13 more)

### Community 85 - "Ark Survival Returns README"
Cohesion: 0.20
Nodes (11): Difficulty and Apex Correction, LocomotionSignal Hysteresis, Movement Tuning, Species sprintRatio Benchmark, strideScale Animation Cadence, waterRetention Swimming Compensation, Ark Survival Returns README, Berry Harvesting System (+3 more)

### Community 87 - "net.minecraft.client.Minecraft"
Cohesion: 0.29
Nodes (7): Entry, SoundSystemMixin, ChannelHandle, net.minecraft.client.Minecraft, net.minecraft.client.resources.sounds.SoundInstance, net.minecraft.client.sounds.SoundEngine, org.spongepowered.asm.mixin.injection.ModifyArg

### Community 88 - ".progression"
Cohesion: 0.24
Nodes (6): BiomeAnnouncements, PlayerLoggedOutEvent, Post, Region, Visit, net.minecraft.ChatFormatting

### Community 89 - "build_camp_assets.py"
Cohesion: 0.21
Nodes (18): bedroll(), box(), main(), material(), pot(), rack(), Author the camp collection as vanilla cuboid models and original pixel…, rot() (+10 more)

### Community 90 - ".think"
Cohesion: 0.22
Nodes (4): Detection, WildlifeSenses, NighttimeGameTests, NighttimeLoadProbe

### Community 91 - "Verify"
Cohesion: 0.11
Nodes (19): Anatomy-driven creature textures — added 2026-09-19, Beta baseline systems — added 2026-09-20 (retroactively listed), Camp and Recovery (P02) — added 2026-09-20, Collection ecosystems (Unreleased) — added 2026-09-16, Cozy camp assets � added 2026-09-20, Creature expansion — added 2026-09-12, Debug spyglass — added 2026-09-13, Flying ecosystem — added 2026-09-11 (+11 more)

### Community 92 - "net.minecraft.gametest.framework.GameTestHelper"
Cohesion: 0.22
Nodes (5): ArkGameTests, ItemStack, JournalGameTests, dev.ftb.mods.ftbquests.quest.ServerQuestFile, net.minecraft.gametest.framework.GameTestHelper

### Community 93 - "net.minecraft.server.level.ServerLevel"
Cohesion: 0.12
Nodes (9): StarterKitData, MapUnlockData, WorkProtection, com.mojang.serialization.Codec, net.minecraft.server.level.ServerLevel, net.minecraft.world.level.saveddata.SavedData, net.minecraft.world.level.saveddata.SavedDataType, net.neoforged.neoforge.common.util.FakePlayer (+1 more)

### Community 94 - "net.minecraft.world.level.block.state.BlockState"
Cohesion: 0.12
Nodes (13): DryingRackBlock, getSerializedName(), Builder, Override, Builder, Override, TroughBlock, CookingPotBlock (+5 more)

### Community 95 - "SedativeArrow"
Cohesion: 0.18
Nodes (9): Context, Override, SedativeArrowRenderer, ItemStack, Override, SedativeArrow, ArrowRenderState, net.minecraft.client.renderer.entity.ArrowRenderer (+1 more)

### Community 96 - ".getPassengerAttachmentPoint"
Cohesion: 0.22
Nodes (5): Vec3, ServerLevel, Vec3, MovementProbe, EntityDimensions

### Community 97 - "BehaviorState"
Cohesion: 0.11
Nodes (17): BehaviorState, ALERT, DEFEND, DRINK, FEED, FLEE, FORAGE, HUNT (+9 more)

### Community 98 - "build_taming_roster.py"
Cohesion: 0.29
Nodes (9): main(), profiles(), Render the taming roster and rider-seat tables from the code and the generated…, Registry id, display name and already size-multiplied hitbox from Species.java., One entry per `add(...)` call in the registry, keyed by the Species constant…, Realm per species, so the table can state where each method came from., realm_source(), size_band() (+1 more)

### Community 99 - "RecoveryPolicy"
Cohesion: 0.18
Nodes (3): RecoveryMath, RecoveryPolicy, RecoveryMathTest

### Community 101 - "DinoDebugSync"
Cohesion: 0.18
Nodes (6): DinoDebugSync, PlayerChangedDimensionEvent, PlayerLoggedOutEvent, PlayerRespawnEvent, Post, View

### Community 102 - "Unified Audio Engine"
Cohesion: 0.31
Nodes (9): AmbientSounds, Presence Footsteps, Audio Resource-Pack API (catalog.json), Sound Physics Remastered, Unified Audio Engine, Audio Loader Conflict Audit (DeepSeek V4.1), installedPack Jar Exclusions, Commit 31d5225 Sound Physics Loader Conflict Fix (+1 more)

### Community 103 - "Spawn and Biome Rules Inspection 2026-09-19"
Cohesion: 0.25
Nodes (9): Defect: Chunk-Gen Spawns Despawn at Frontier, Defect: Footprint Y Equals Highest Column, NaturalPopulations Budget, Spawn Placement Resolution (current build), Defect: Population Director Removed, Defect: Spawns Rejected on WorldGenRegion, Spawn and Biome Rules Inspection 2026-09-19, Defect: Water Species on Wrong Spawn Category (+1 more)

### Community 104 - "Placement"
Cohesion: 0.22
Nodes (9): Placement, BOUNDS, CLEARANCE, CONFIG, DANGER, FLUID, FOOTPRINT, LOADED (+1 more)

### Community 105 - "ArkGameTests.java"
Cohesion: 0.19
Nodes (8): IEventBus, RecoveryAttachments, IEventBus, TamingAttachments, Item, net.neoforged.neoforge.attachment.AttachmentType, net.neoforged.neoforge.registries.DeferredHolder, net.neoforged.neoforge.registries.DeferredRegister

### Community 106 - "Camp and recovery"
Cohesion: 0.13
Nodes (13): Authoring the quest pack, Discovery records, Installed stack, Installing elsewhere, Journal and tribe stack, Playing, Camp and recovery, Downed state and revive (+5 more)

### Community 107 - "BiomeTier"
Cohesion: 0.22
Nodes (10): at(), BiomeTier, EASY, EXTREME, HARD, MODERATE, SEVERE, of() (+2 more)

### Community 109 - "DeepSeek Worker Skill"
Cohesion: 0.36
Nodes (8): DeepSeek Worker Skill, DeepSeek V4.1 Flash (deepseek-flash alias), Disposable Codex worktree, Schema-constrained Git patch deliverable, Invoke-DeepSeekWorker.ps1 wrapper, DeepSeek Responses API endpoint (api.deepseek.com), Repository Agent Instructions (AGENTS.md), DeepSeek delegation trigger sentence

### Community 110 - "BerryBushBlock.java"
Cohesion: 0.15
Nodes (10): BerryBushBlock, Builder, Override, Override, RecoveryCacheBlock, net.minecraft.world.level.block.state.properties.IntegerProperty, net.minecraft.world.level.block.VegetationBlock, net.minecraft.world.level.BlockGetter (+2 more)

### Community 111 - "LandWildlife"
Cohesion: 0.14
Nodes (4): Budget, LandWildlife, CollectionGameTests, LandGameTests

### Community 112 - "DangerMapSync"
Cohesion: 0.23
Nodes (5): DangerMapSync, CommandSourceStack, PlayerChangedDimensionEvent, PlayerLoggedInEvent, PlayerRespawnEvent

### Community 113 - "DownedPolicy"
Cohesion: 0.19
Nodes (3): DownedPolicy, DownedGameTests, DownedPolicyTest

### Community 114 - "CreatureSize"
Cohesion: 0.29
Nodes (6): CreatureSize, GIANT, LARGE, MEDIUM, SMALL, of()

### Community 115 - "CargoProfiles"
Cohesion: 0.27
Nodes (6): CargoProfiles, Harness, NONE, PACK, REINFORCED, Profile

### Community 116 - "main"
Cohesion: 0.60
Nodes (5): main(), roster(), settings_for(), write_output(), write_table()

### Community 117 - "Progression bypass audit"
Cohesion: 0.40
Nodes (4): Deferred work, Method, Progression bypass audit, Routes

### Community 118 - "net.minecraft.network.RegistryFriendlyByteBuf"
Cohesion: 0.11
Nodes (17): CargoTransferPayload, Override, Type, CompanionState, Override, DangerMapPayload, Override, Type (+9 more)

### Community 119 - "Creature source projects"
Cohesion: 0.18
Nodes (9): Ice, flying, aquatic and swamp model collection, Models and animation binding, Previews, Rebuild and verification, Catalog, Creature source projects, Directory contract, Rebuild (+1 more)

### Community 120 - "Theropod skin batch"
Cohesion: 0.40
Nodes (4): Known tuning items for the next pass, Roster and results, Theropod skin batch, Where the results are

### Community 121 - "herd"
Cohesion: 0.33
Nodes (7): cohesionDistance(), defensiveHerd(), flyer(), groupHeightRange(), groupRadius(), herd(), timid()

### Community 123 - "Homestead Economy"
Cohesion: 0.17
Nodes (11): Art and previews, Automated coverage, Berry bushes, Config quick reference, Cooking pot, Drying rack, Feeding trough, Field medicine (+3 more)

### Community 126 - "CreatureRenderer"
Cohesion: 0.27
Nodes (8): CreatureRenderer, Context, Override, com.geckolib.renderer.base.RenderPassInfo, com.geckolib.renderer.GeoEntityRenderer, com.mojang.blaze3d.vertex.PoseStack, net.minecraft.client.renderer.entity.state.EntityRenderState, SuppressWarnings

### Community 129 - "build_test_structure.py"
Cohesion: 0.60
Nodes (5): main(), named(), Create the small empty structure required by the headless GameTests (NBT, no…, string(), write_structure()

### Community 130 - "Result"
Cohesion: 0.20
Nodes (9): Result, ACCEPTED, ALREADY_TAMED, COOLDOWN, EXCLUDED, NOT_CLAIMANT, NOT_HUNGRY, WRONG_FOOD (+1 more)

### Community 132 - "SedativeArrowItem.java"
Cohesion: 0.29
Nodes (7): Override, SedativeArrowItem, DoubleSupplier, net.minecraft.core.Position, net.minecraft.world.entity.projectile.arrow.AbstractArrow, net.minecraft.world.entity.projectile.Projectile, net.minecraft.world.item.ArrowItem

### Community 135 - "BedrollBlock"
Cohesion: 0.22
Nodes (6): BedrollBlock, Builder, ItemStack, Override, ServerLevel, net.minecraft.world.level.LevelReader

### Community 136 - "dev-dependencies.json"
Cohesion: 0.40
Nodes (4): checked, dependencies, loader, minecraft

### Community 137 - "Realm"
Cohesion: 0.40
Nodes (5): Realm, AIR, AMPHIBIOUS, LAND, WATER

### Community 139 - "Load"
Cohesion: 0.24
Nodes (6): Band, HEAVY, NORMAL, OVERLOAD, WARN, Load

### Community 140 - "Weight and Working Tames"
Cohesion: 0.20
Nodes (9): Automated coverage, Capacity is a movement budget, Cargo rigs and transfer, Config quick reference, Mass model, Overload safety, Thresholds, Weight and Working Tames (+1 more)

### Community 141 - "build_item_assets.py"
Cohesion: 0.67
Nodes (3): berry(), main(), Author crisp 32px item sprites from pixel shapes, and an inventory review sheet.

### Community 142 - "gradlew"
Cohesion: 0.83
Nodes (3): gradlew script, die(), warn()

### Community 143 - "sleepClip"
Cohesion: 0.50
Nodes (4): landHabitat(), restClip(), sleepClip(), sleeps()

### Community 144 - "preview_danger_map.py"
Cohesion: 0.67
Nodes (3): danger(), main(), Plot the production danger-region formula and its discrete area shares.

### Community 145 - "inventory_ark_behavior.py"
Cohesion: 0.67
Nodes (3): main(), name_table(), Read installed ASE package name tables and creature animation names; never…

### Community 146 - ".transfer"
Cohesion: 0.31
Nodes (3): CargoTransferService, BlockPos, net.neoforged.neoforge.network.handling.IPayloadContext

### Community 148 - "ARK to GeckoLib dinosaur workflow README"
Cohesion: 0.53
Nodes (10): ARK to GeckoLib dinosaur workflow README, Asset Pipeline Scripts README, ark_animation.py, ark_geometry.py, build_dinosaurs.py, extract_dinosaurs.py, mesh_detail.py, preview_dinosaurs.py (+2 more)

### Community 155 - "Assets"
Cohesion: 0.22
Nodes (8): 1. ARK-derived creature art (external IP, imported and converted), 2. Vanilla textures reused by Ark content (placeholder art), 3. Procedurally generated item art, 4. Vendored third-party audio libraries, 5. Third-party mods and their assets (downloaded or built, never distributed in the Ark jar), 6. Code-drawn graphics (no texture files), 7. Authored camp collection, Assets

### Community 156 - "Flying Habitats Proposal"
Cohesion: 0.32
Nodes (8): WildlifeGoal, Persistent Flying Colony Habitat, Egg-Taking Contract, Flyer Flight State Machine, Flying Habitats Proposal, FlyingCreatureEntity, Phantom Circle/Swoop Reuse, Xaero Nest Glyph

### Community 157 - "Mod integration plan: Ancient Remnants and Icy's Better Horses"
Cohesion: 0.25
Nodes (7): Acceptance checklist, Built artifacts (2026-09-20), Compatibility audit, Loading them in development, Mod integration plan: Ancient Remnants and Icy's Better Horses, Risks and open items, Runtime dependency closure

### Community 158 - "LibraryMixin.java"
Cohesion: 0.43
Nodes (5): LibraryMixin, com.mojang.blaze3d.audio.Library, java.nio.IntBuffer, org.spongepowered.asm.mixin.injection.invoke.arg.Args, org.spongepowered.asm.mixin.injection.ModifyArgs

### Community 160 - "RecoveryEvents"
Cohesion: 0.25
Nodes (4): PlayerLoggedInEvent, PlayerRespawnEvent, RecoveryEvents, net.neoforged.neoforge.event.entity.living.LivingDropsEvent

### Community 161 - "WorkProfiles"
Cohesion: 0.33
Nodes (6): Job, FORAGE, MINERAL, NONE, Profile, WorkProfiles

### Community 162 - "Python runtime requirements file"
Cohesion: 0.33
Nodes (7): Six-stage creature conversion pipeline, Python runtime requirements file, Delivered build requirements lock, Preview requirements (Numba), ARK Extractor Tools README, UModel / UE Viewer executable, UE Viewer MIT License

### Community 164 - "Food"
Cohesion: 0.40
Nodes (5): Food, BERRIES, FISH, MEAT, net.minecraft.util.StringRepresentable

### Community 167 - "Preset"
Cohesion: 0.50
Nodes (4): Preset, OFF, RELAXED, STANDARD

### Community 168 - ".meal"
Cohesion: 0.50
Nodes (3): Holder, Consumable, MobEffect

### Community 169 - "Workspace Standards"
Cohesion: 0.50
Nodes (4): Workspace Standards, Standardized change log policy, GitHub backup directive, Standardized patch methodology

## Knowledge Gaps
- **319 isolated node(s):** `$schema`, `type`, `additionalProperties`, `summary`, `unified_diff` (+314 more)
  These have ≤1 connection - possible missing edges or undocumented components.
- **27 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `CreatureEntity` connect `CreatureEntity` to `net.minecraft.core.BlockPos`, `ModContent`, `Result`, `Species`, `.getControllingPassenger`, `AquaticGoal`, `FlyingCreatureEntity`, `LocomotionSignal`, `net.minecraft.world.phys.Vec3`, `net.neoforged.bus.api.SubscribeEvent`, `.loaded`, `.registerControllers`, `.of`, `.transfer`, `.advance`, `net.minecraft.world.entity.LivingEntity`, `WildlifeGoal`, `.AmphibiousCreatureEntity`, `CompanionGoal`, `.claimCheck`, `CookingPotBlockEntity`, `DinoDebugPayload`, `NaturalPopulations`, `CreatureMountMenu`, `.strike`, `WildlifeController`, `.level`, `NightEyesLayer.java`, `CreatureRideController`, `MassEvents`, `.refresh`, `TargetHealthBar`, `TribeService`, `WorkGoal`, `.capture`, `CreatureTamingProfile`, `.initializeLevel`, `.tamingInventory`, `.think`, `net.minecraft.gametest.framework.GameTestHelper`, `.getPassengerAttachmentPoint`, `WildlifeNoise`, `LoadedBlocks`, `CargoProfiles`, `TamingFeedback`, `CreatureRenderer`?**
  _High betweenness centrality (0.128) - this node is a cross-community bridge._
- **Why does `Species` connect `Species` to `ModContent`, `CreatureProfileRegistry`, `FlyingCreatureEntity`, `net.neoforged.bus.api.SubscribeEvent`, `WildlifeCommand`, `.loaded`, `.registerControllers`, `.of`, `NestBlock`, `net.minecraft.world.entity.LivingEntity`, `ArkData`, `net.minecraft.world.item.Item`, `WorkProfiles`, `Species.java`, `.AmphibiousCreatureEntity`, `DinoDebugPayload`, `NaturalPopulations`, `LandFamily`, `.strike`, `WildlifeController`, `NightEyesLayer.java`, `.ecology`, `CreatureEntity`, `WorkGoal`, `CreatureTamingProfile`, `CreatureRideProfile`, `.tamingInventory`, `.progression`, `.think`, `net.minecraft.gametest.framework.GameTestHelper`, `.getPassengerAttachmentPoint`, `BiomeTier`, `LandWildlife`, `CargoProfiles`, `CreatureRenderer`?**
  _High betweenness centrality (0.060) - this node is a cross-community bridge._
- **Why does `ModContent` connect `ModContent` to `TroughBlockEntity`, `Species`, `SedativeArrowItem.java`, `BedrollBlock`, `net.neoforged.bus.api.SubscribeEvent`, `NestBlock`, `HostileGuard.java`, `net.minecraft.world.item.ItemStack`, `net.minecraft.world.item.Item`, `ConcentratedSedativeItem`, `CookingPotBlockEntity`, `.meal`, `DinoDebugPayload`, `CreatureMountMenu`, `CreatureEntity`, `net.minecraft.world.entity.player.Player`, `net.minecraft.server.level.ServerLevel`, `net.minecraft.world.level.block.state.BlockState`, `SedativeArrow`, `ArkGameTests.java`, `BerryBushBlock.java`?**
  _High betweenness centrality (0.057) - this node is a cross-community bridge._
- **What connects `$schema`, `type`, `additionalProperties` to the rest of the system?**
  _319 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `net.minecraft.core.BlockPos` be split into smaller, more focused modules?**
  _Cohesion score 0.10042283298097252 - nodes in this community are weakly interconnected._
- **Should `skin_studio.py` be split into smaller, more focused modules?**
  _Cohesion score 0.06605222734254992 - nodes in this community are weakly interconnected._
- **Should `ModContent` be split into smaller, more focused modules?**
  _Cohesion score 0.05953433539640436 - nodes in this community are weakly interconnected._