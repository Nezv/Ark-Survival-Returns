package dev.nez.arksurvivalreturns;

import java.util.EnumMap;
import java.util.List;
import dev.nez.arksurvivalreturns.feature.spawn.BiomeTier;
import dev.nez.arksurvivalreturns.feature.creature.Species;
import dev.nez.arksurvivalreturns.feature.cargo.CargoProfiles;
import dev.nez.arksurvivalreturns.feature.mass.MassRules;
import net.neoforged.neoforge.common.ModConfigSpec;

/** Per-world server configuration; habitat preferences and protected biomes live in data packs. */
public final class Config {
    public static final ModConfigSpec SPEC;
    public static final EnumMap<dev.nez.arksurvivalreturns.feature.land.LandFamily, ModConfigSpec.IntValue> LAND_ROAM = new EnumMap<>(dev.nez.arksurvivalreturns.feature.land.LandFamily.class);
    public static final EnumMap<dev.nez.arksurvivalreturns.feature.land.LandFamily, ModConfigSpec.IntValue> LAND_LEASH = new EnumMap<>(dev.nez.arksurvivalreturns.feature.land.LandFamily.class);
    public static final EnumMap<dev.nez.arksurvivalreturns.feature.land.LandFamily, ModConfigSpec.IntValue> LAND_WATER_MAX = new EnumMap<>(dev.nez.arksurvivalreturns.feature.land.LandFamily.class);
    public static final EnumMap<dev.nez.arksurvivalreturns.feature.land.LandFamily, ModConfigSpec.IntValue> LAND_WATER_PREFERRED = new EnumMap<>(dev.nez.arksurvivalreturns.feature.land.LandFamily.class);
    public static final ModConfigSpec.DoubleValue PLAYER_SPRINT_REFERENCE;
    public static final EnumMap<Species, ModConfigSpec.DoubleValue> SPRINT_RATIO = new EnumMap<>(Species.class);
    public static final EnumMap<Species, ModConfigSpec.DoubleValue> WATER_RETENTION = new EnumMap<>(Species.class);
    public static final EnumMap<Species, ModConfigSpec.DoubleValue> STRIDE_SCALE = new EnumMap<>(Species.class);
    public static final ModConfigSpec.BooleanValue NATURAL_SPAWNS;
    public static final ModConfigSpec.IntValue WILDLIFE_WATER_DEPTH;
    public static final ModConfigSpec.BooleanValue POPULATION_BUDGET;
    public static final ModConfigSpec.IntValue POPULATION_TARGET, POPULATION_RADIUS, POPULATION_GLOBAL_CAP,
            POPULATION_INTERVAL, POPULATION_ATTEMPTS, POPULATION_CULL_MARGIN, POPULATION_MIN_DISTANCE;
    public static final ModConfigSpec.IntValue BAND_WIDTH;
    public static final ModConfigSpec.BooleanValue BIOME_MESSAGES;
    public static final ModConfigSpec.BooleanValue THEME_DIMENSIONS, THEME_MONSTERS, THEME_MECHANICS;
    public static final ModConfigSpec.BooleanValue MAP_REQUIRES_UNLOCK;
    public static final ModConfigSpec.BooleanValue PRIMITIVE_LOGS_NEED_AXE, PRIMITIVE_NO_WOODEN_TOOLS, PRIMITIVE_NO_FURNACES;
    public static final ModConfigSpec.DoubleValue PRIMITIVE_LEAF_STICKS, PRIMITIVE_FIRE_STARTER_CHANCE,
            PRIMITIVE_FIRE_COOK_FACTOR, PRIMITIVE_FIRE_FUEL_FACTOR, PRIMITIVE_FORGE_SPEED;
    public static final ModConfigSpec.IntValue PRIMITIVE_FIRE_MAX_FUEL, FARM_DRIED_TIER_TWO, FARM_DRIED_TIER_THREE;
    public static final ModConfigSpec.DoubleValue HEALTH_GROWTH;
    public static final ModConfigSpec.DoubleValue DAMAGE_GROWTH;
    public static final ModConfigSpec.BooleanValue HEALTH_BAR;
    public static final ModConfigSpec.BooleanValue MASS_GAUGE;
    public static final ModConfigSpec.BooleanValue NIGHTTIME;
    public static final ModConfigSpec.IntValue NIGHT_START, NIGHT_END, NIGHT_TRANSITION, SLEEP_CALM;
    public static final ModConfigSpec.DoubleValue NIGHT_HUNGER, NIGHT_VISION, DAY_SLEEP, WAKE_DISTANCE;
    public static final ModConfigSpec.IntValue HEALTH_BAR_RANGE;
    public static final ModConfigSpec.IntValue ARGENT_NEST_Y, NEST_WATER_RADIUS, PTERO_ROAM_RADIUS, ARGENT_ROAM_RADIUS, FLIGHT_LEASH, EGG_DEFENSE_TICKS;
    public static final ModConfigSpec.BooleanValue PERCHING;
    public static final EnumMap<BiomeTier, ModConfigSpec.IntValue> MIN_LEVEL = new EnumMap<>(BiomeTier.class);
    public static final EnumMap<BiomeTier, ModConfigSpec.IntValue> MAX_LEVEL = new EnumMap<>(BiomeTier.class);
    // ------------------------------------------------------------------------------- taming
    public static final ModConfigSpec.BooleanValue TAMING_ENABLED;
    public static final ModConfigSpec.BooleanValue TAMING_DEBUG_LOG;
    public static final ModConfigSpec.IntValue PLAYER_MAX_TORPOR;
    public static final ModConfigSpec.DoubleValue PLAYER_TORPOR_RESISTANCE;
    public static final EnumMap<dev.nez.arksurvivalreturns.feature.taming.CreatureSize, ModConfigSpec.IntValue> MAX_TORPOR =
            new EnumMap<>(dev.nez.arksurvivalreturns.feature.taming.CreatureSize.class);
    public static final ModConfigSpec.DoubleValue WAKE_THRESHOLD_RATIO, TORPOR_RECOVERY_PER_SECOND;
    public static final ModConfigSpec.IntValue TORPOR_RECOVERY_DELAY, TORPOR_SYNC_INTERVAL;
    public static final ModConfigSpec.DoubleValue BASIC_SEDATIVE_POTENCY, CONCENTRATED_SEDATIVE_POTENCY,
            TRANQUILIZER_ARROW_POTENCY;
    public static final ModConfigSpec.IntValue TORPOR_COLLAPSE_FALLBACK_TICKS, TORPOR_WAKE_FALLBACK_TICKS, TORPOR_FEED_FALLBACK_TICKS;
    public static final ModConfigSpec.DoubleValue FEED_HUNGER_THRESHOLD, WILD_HUNGER_MIN, WILD_HUNGER_MAX;
    public static final ModConfigSpec.DoubleValue HUNGER_INCREASE_PER_SECOND, HUNGER_REDUCTION_PER_MEAL;
    public static final ModConfigSpec.IntValue MINIMUM_FEED_INTERVAL, FOOD_UNITS_PER_MEAL, CLAIM_EXPIRY, FEEDING_TRUCE_TICKS;
    public static final ModConfigSpec.DoubleValue PREFERRED_FOOD_MULTIPLIER, DAMAGE_PROGRESS_PENALTY;
    public static final ModConfigSpec.IntValue PASSIVE_PROGRESS_GRACE, PASSIVE_PROGRESS_DECAY_PERIOD;
    public static final ModConfigSpec.DoubleValue PASSIVE_PROGRESS_DECAY, PLAYER_MOVEMENT_TOLERANCE;
    public static final ModConfigSpec.IntValue PLAYER_IMPULSE_TICKS;
    public static final ModConfigSpec.DoubleValue RIDDEN_FLIGHT_SPEED_MULTIPLIER, RIDDEN_SWIM_SPEED_MULTIPLIER;
    public static final ModConfigSpec.BooleanValue TRIBE_DEFAULT_RIDE, TRIBE_DEFAULT_CARGO, TRIBE_DEFAULT_COMMANDS,
            TRIBE_DEFAULT_BREEDING, TRIBE_DEFAULT_WORK;
    // ----------------------------------------------------------------------------- work
    public static final ModConfigSpec.BooleanValue WORK_ENABLED, WORK_RESPECT_PLACED;
    public static final ModConfigSpec.IntValue WORK_RADIUS, WORK_SUPERVISION_RADIUS, WORK_ACTION_INTERVAL,
            WORK_SCAN_ATTEMPTS;
    public static final ModConfigSpec.DoubleValue WORK_FIBER_PER_ACTION, WORK_BERRY_CHANCE, WORK_MINERAL_BONUS;
    // ----------------------------------------------------------------------------- farm
    public static final ModConfigSpec.BooleanValue FARM_ENABLED;
    public static final ModConfigSpec.IntValue FARM_BATCH_TICKS, FARM_TROUGH_RADIUS, FARM_TROUGH_CAPACITY,
            FARM_TROUGH_MAX_PER_BATCH, FARM_DRYING_BATCHES, FARM_DRYING_CAPACITY;
    public static final ModConfigSpec.DoubleValue FARM_TROUGH_HUNGER, FARM_TROUGH_FEED, FARM_TROUGH_HEAL,
            FARM_BERRY_GROWTH;
    // -------------------------------------------------------------------------- kitchen
    public static final ModConfigSpec.BooleanValue KITCHEN_ENABLED;
    public static final ModConfigSpec.IntValue KITCHEN_COOK_BATCHES;
    // ---------------------------------------------------------------------------- forge
    // ----------------------------------------------------------------------------- mass
    public static final ModConfigSpec.BooleanValue MASS_ENABLED;
    public static final ModConfigSpec.EnumValue<MassRules.Preset> MASS_PRESET;
    public static final ModConfigSpec.DoubleValue MASS_PLAYER_CAPACITY, MASS_MULTIPLIER, MASS_WARNING_RATIO,
            MASS_SLOW_RATIO, MASS_HEAVY_RATIO, MASS_SPEED_FLOOR, MASS_UNKNOWN_CONTAINER, MASS_CONTAINER_CONTENT_CAP,
            MASS_AUTOMATION_CEILING, MASS_BARE_CAPACITY;
    public static final ModConfigSpec.IntValue CARGO_TRANSFER_RADIUS;
    public static final EnumMap<Species, ModConfigSpec.DoubleValue> CARGO_CAPACITY = new EnumMap<>(Species.class);
    // --------------------------------------------------------------------------- combat
    public static final ModConfigSpec.DoubleValue COMBAT_HIT_FRACTION, COMBAT_RECOVERY_FRACTION;
    // ------------------------------------------------------------------------ companion
    public static final ModConfigSpec.IntValue COMPANION_FOLLOW_DISTANCE, COMPANION_WANDER_RADIUS,
            COMPANION_PET_COOLDOWN_TICKS;
    // ----------------------------------------------------------------------------- camp
    public static final ModConfigSpec.BooleanValue CAMP_STARTER_KIT, CAMP_BEDROLL_SETS_SPAWN, CAMP_BEDROLL_PICKUP;
    public static final ModConfigSpec.DoubleValue CAMP_BANDAGE_HEAL;
    public static final ModConfigSpec.IntValue CAMP_BANDAGE_COOLDOWN;
    // ------------------------------------------------------------------------- recovery
    public static final ModConfigSpec.BooleanValue RECOVERY_ENABLED, RECOVERY_NOTIFY_TRIBE;
    public static final ModConfigSpec.IntValue RECOVERY_MAX_CACHES, RECOVERY_SEARCH_RADIUS;
    // --------------------------------------------------------------------------- downed
    public static final ModConfigSpec.BooleanValue DOWNED_ENABLED, DOWNED_NOTIFY_TRIBE, DOWNED_REQUIRE_TRIBE,
            DOWNED_LAVA_LETHAL, DOWNED_VOID_LETHAL;
    public static final ModConfigSpec.IntValue DOWNED_WINDOW;
    public static final ModConfigSpec.DoubleValue DOWNED_REVIVE_FRACTION, DOWNED_BLEED_FACTOR, DOWNED_OVERKILL;
    // ------------------------------------------------------------------------- guardian
    public static final ModConfigSpec.BooleanValue GUARDIAN_ENABLED, GUARDIAN_ANNOUNCE;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> GUARDIAN_STRUCTURES;
    public static final ModConfigSpec.IntValue GUARDIAN_ACTIVATION_RADIUS, GUARDIAN_ARENA_RADIUS, GUARDIAN_JOIN_RADIUS,
            GUARDIAN_GRACE_TICKS, GUARDIAN_RESET_DELAY_TICKS, GUARDIAN_MAX_TAME_CONTRIBUTION, GUARDIAN_TAME_RADIUS,
            GUARDIAN_BAR_RANGE, GUARDIAN_SPAWN_RADIUS;
    public static final ModConfigSpec.DoubleValue GUARDIAN_BASE_HEALTH, GUARDIAN_HEALTH_PER_PLAYER,
            GUARDIAN_HEALTH_PER_TAME, GUARDIAN_DAMAGE_MULTIPLIER, GUARDIAN_ARMOR, GUARDIAN_TAME_DAMAGE_FACTOR;
    // ----------------------------------------------------------------------------- tech
    public static final ModConfigSpec.BooleanValue TECH_ENABLED, TECH_ANNOUNCE;
    public static final ModConfigSpec.IntValue TECH_SCAN_TICKS;
    static {
        var b = new ModConfigSpec.Builder();
        b.push("spawning");
        NATURAL_SPAWNS = b.comment("Enable natural creature spawns through the vanilla spawner. Spawn eggs and commands still work.").define("enabled", true);
        WILDLIFE_WATER_DEPTH = b.comment("Minimum swimmable depth for a water-bound creature spawn.").defineInRange("minimumWaterDepth", 6, 3, 24);
        POPULATION_BUDGET = b.comment("Independent budget that tops up mod wildlife near players without competing for the vanilla mob cap.")
                .define("populationBudget", true);
        POPULATION_TARGET = b.comment("Natural mod creatures kept around each player by the budget.")
                .defineInRange("populationTargetPerPlayer", 10, 0, 64);
        POPULATION_RADIUS = b.comment("Radius in blocks around a player that the budget counts and fills.")
                .defineInRange("populationRadius", 128, 32, 256);
        POPULATION_GLOBAL_CAP = b.comment("Maximum natural mod creatures loaded in one dimension.")
                .defineInRange("populationGlobalCap", 80, 16, 512);
        POPULATION_INTERVAL = b.comment("Ticks between budget checks (100 = 5 seconds).")
                .defineInRange("populationIntervalTicks", 100, 20, 2400);
        POPULATION_ATTEMPTS = b.comment("Group placement attempts per player per check while under target.")
                .defineInRange("populationAttempts", 2, 1, 8);
        POPULATION_CULL_MARGIN = b.comment("Extra creatures over target tolerated before the budget culls the farthest.")
                .defineInRange("populationCullMargin", 4, 0, 32);
        POPULATION_MIN_DISTANCE = b.comment("Never spawn natural creatures closer than this to a player.")
                .defineInRange("populationMinPlayerDistance", 32, 8, 128);
        b.pop().push("progression");
        MAP_REQUIRES_UNLOCK = b.comment("Require the saved map entitlement. A tame whose origin band is 5 grants it; restart/rejoin after changing.").define("mapRequiresUnlock", false);
        BAND_WIDTH = b.comment("Scale of repeating equal-area danger regions; tile period is four times this value. Saved per world. Legacy key retained for existing configs.").defineInRange("bandWidth", 256, 96, 1024);
        b.pop().push("primitive");
        PRIMITIVE_LOGS_NEED_AXE = b.comment("Logs drop nothing and break slowly without an axe; the stone hatchet is the first one.")
                .define("logsNeedAxe", true);
        PRIMITIVE_NO_WOODEN_TOOLS = b.comment("Remove the wooden tool recipes; loose rocks lead to cobblestone and stone tools instead. Applies on /reload.")
                .define("removeWoodenTools", true);
        PRIMITIVE_NO_FURNACES = b.comment("Remove the furnace, smoker and blast furnace recipes and block existing ones; food goes to the stone fire, "
                        + "smelting to the primitive forge. Recipes apply on /reload.")
                .define("replaceFurnaces", true);
        PRIMITIVE_LEAF_STICKS = b.comment("Chance that leaves broken by hand also drop a stick, so the first handle never needs a log.")
                .defineInRange("leafStickChance", 0.2, 0.0, 1.0);
        PRIMITIVE_FIRE_STARTER_CHANCE = b.comment("Chance a fire starter use lights a fueled stone fire or campfire.")
                .defineInRange("fireStarterChance", 0.5, 0.05, 1.0);
        PRIMITIVE_FIRE_COOK_FACTOR = b.comment("Stone fire cooking time as a fraction of the campfire recipe time.")
                .defineInRange("stoneFireCookFactor", 0.5, 0.1, 4.0);
        PRIMITIVE_FIRE_FUEL_FACTOR = b.comment("Stone fire burn time as a fraction of the item's furnace burn time.")
                .defineInRange("stoneFireFuelFactor", 1.0, 0.1, 4.0);
        PRIMITIVE_FIRE_MAX_FUEL = b.comment("Most burn ticks a stone fire stores at once.")
                .defineInRange("stoneFireMaxFuel", 6000, 200, 72000);
        PRIMITIVE_FORGE_SPEED = b.comment("Primitive forge smelting time as a fraction of the smelting recipe time.")
                .defineInRange("forgeTimeFactor", 1.0, 0.1, 4.0);
        b.pop().push("movement");
        PLAYER_SPRINT_REFERENCE = b.comment("Normal player sprint benchmark in blocks/second; does not chase temporary player potion buffs.").defineInRange("playerSprintBlocksPerSecond", 5.612, 1.0, 20.0);
        for (var species : Species.values()) {
            b.push(species.id);
            SPRINT_RATIO.put(species, b.comment("Full pursuit speed relative to the player benchmark; normal wandering uses a slower gait.").defineInRange("sprintRatio", species.sprintRatioDefault(), 0.2, 4.0));
            WATER_RETENTION.put(species, b.comment("Fraction of land speed retained while swimming.").defineInRange("waterRetention", species.predator ? 1.0 : 0.65, 0.1, 1.5));
            STRIDE_SCALE.put(species, b.comment("Visual stride length multiplier; larger values slow the animation at the same ground speed.").defineInRange("strideScale", 1.0, 0.5, 2.0));
            b.pop();
        }
        b.pop().push("combat");
        COMBAT_HIT_FRACTION = b.comment("Share of the authored attack clip that plays before the hit lands.")
                .defineInRange("hitFrameFraction", 0.4, 0.1, 0.9);
        COMBAT_RECOVERY_FRACTION = b.comment("Recovery after the attack clip, as a share of its length; larger "
                        + "values slow the attack rate instead of shortening the animation.")
                .defineInRange("recoveryFraction", 0.35, 0.0, 2.0);
        b.pop().push("companion");
        COMPANION_FOLLOW_DISTANCE = b.comment("A companion following its owner stops this close, in blocks.")
                .defineInRange("followStopDistance", 6, 2, 24);
        COMPANION_WANDER_RADIUS = b.comment("WANDER order: farthest offset from the anchor a destination may use.")
                .defineInRange("wanderRadius", 20, 4, 64);
        COMPANION_PET_COOLDOWN_TICKS = b.comment("Ticks between pet responses for one creature (40 = two seconds).")
                .defineInRange("petCooldownTicks", 40, 10, 200);
        b.pop().push("camp");
        CAMP_STARTER_KIT = b.comment("Grant each player a one-time kit (bedroll, bandages, fiber and a flint knife) "
                        + "on their first join in a world.")
                .define("starterKit", true);
        CAMP_BEDROLL_SETS_SPAWN = b.comment("Allow the bedroll to set the personal respawn point.")
                .define("bedrollSetsSpawn", true);
        CAMP_BEDROLL_PICKUP = b.comment("Allow sneak-use to roll the bedroll back into an item. The saved respawn "
                        + "point survives either way.")
                .define("bedrollPickup", true);
        CAMP_BANDAGE_HEAL = b.comment("Health restored by one fiber bandage.")
                .defineInRange("bandageHeal", 4.0, 1.0, 20.0);
        CAMP_BANDAGE_COOLDOWN = b.comment("Ticks before the same bandage stack can heal again (100 = five seconds).")
                .defineInRange("bandageCooldownTicks", 100, 0, 1200);
        b.pop().push("recovery");
        RECOVERY_ENABLED = b.comment("Replace player death drops with a recoverable cache at the death spot. "
                        + "Unplaced caches (void deaths) can be claimed with /arkrecover claim.")
                .define("enabled", true);
        RECOVERY_MAX_CACHES = b.comment("Outstanding caches per player; when exceeded, the oldest is folded into the "
                        + "newest instead of being lost.")
                .defineInRange("maxCaches", 3, 1, 10);
        RECOVERY_SEARCH_RADIUS = b.comment("Radius searched for safe ground when the death spot cannot hold a marker.")
                .defineInRange("searchRadius", 12, 4, 32);
        RECOVERY_NOTIFY_TRIBE = b.comment("Tell online FTB Teams tribe members where a cache appeared.")
                .define("notifyTribe", true);
        b.pop().push("downed");
        DOWNED_ENABLED = b.comment("Lethal damage leaves a player downed with a rescue window instead of killing "
                        + "them outright. Void, lava, /kill and overkill hits stay fatal.")
                .define("enabled", true);
        DOWNED_WINDOW = b.comment("Rescue window in ticks before a downed player bleeds out (1200 = one minute).")
                .defineInRange("windowTicks", 1200, 100, 6000);
        DOWNED_REVIVE_FRACTION = b.comment("Share of maximum health restored when a fiber bandage revives a player.")
                .defineInRange("reviveHealthFraction", 0.3, 0.1, 1.0);
        DOWNED_BLEED_FACTOR = b.comment("Rescue ticks lost per point of damage taken while downed (0 disables the "
                        + "escalating pressure).")
                .defineInRange("damageBleedFactor", 1.0, 0.0, 5.0);
        DOWNED_OVERKILL = b.comment("A single hit at or above this multiple of maximum health kills outright.")
                .defineInRange("overkillMultiplier", 1.5, 1.0, 5.0);
        DOWNED_LAVA_LETHAL = b.comment("Lava and fire kill outright instead of downing the player.")
                .define("lavaLethal", true);
        DOWNED_VOID_LETHAL = b.comment("Void damage kills outright instead of downing the player.")
                .define("voidLethal", true);
        DOWNED_NOTIFY_TRIBE = b.comment("Tell online FTB Teams tribe members when someone goes down.")
                .define("notifyTribe", true);
        DOWNED_REQUIRE_TRIBE = b.comment("Only tribe members may revive with a fiber bandage. Disable to let any "
                        + "player revive.")
                .define("requireTribe", true);
        b.pop().push("tribe");
        TRIBE_DEFAULT_RIDE = b.comment("Default riding permission for members of the tame owner's FTB Teams party. "
                        + "The owner always keeps every permission.")
                .define("defaultRide", true);
        TRIBE_DEFAULT_CARGO = b.comment("Default cargo and creature-inventory access for party members.")
                .define("defaultCargo", true);
        TRIBE_DEFAULT_COMMANDS = b.comment("Default permission to give companion orders to a teammate's tame.")
                .define("defaultCommands", true);
        TRIBE_DEFAULT_BREEDING = b.comment("Reserved for the husbandry work: breeding permission for party members.")
                .define("defaultBreeding", false);
        TRIBE_DEFAULT_WORK = b.comment("Default permission for party members to supervise a teammate's tame while "
                        + "it runs a harvest job. The owner always keeps every permission.")
                .define("defaultWork", true);
        b.pop().push("work");
        WORK_ENABLED = b.comment("Allow work orders: Triceratops forages plants, Ankylosaurus mines stone and ores.")
                .define("enabled", true);
        WORK_RADIUS = b.comment("Blocks around the work anchor a job searches for targets.")
                .defineInRange("radius", 12, 4, 32);
        WORK_SUPERVISION_RADIUS = b.comment("An authorized survivor must be this close for a job to run; walking "
                        + "away pauses it, so jobs never become unattended automation.")
                .defineInRange("supervisionRadius", 48, 16, 96);
        WORK_ACTION_INTERVAL = b.comment("Ticks between harvest actions once the worker reaches its target.")
                .defineInRange("actionIntervalTicks", 40, 10, 200);
        WORK_SCAN_ATTEMPTS = b.comment("Candidate samples per search; every sample checks loaded chunks only.")
                .defineInRange("scanAttempts", 24, 4, 64);
        WORK_FIBER_PER_ACTION = b.comment("Plant fiber gathered per forage action.")
                .defineInRange("fiberPerAction", 1.0, 1.0, 8.0);
        WORK_BERRY_CHANCE = b.comment("Chance a forage action also yields one mod berry.")
                .defineInRange("berryChance", 0.35, 0.0, 1.0);
        WORK_MINERAL_BONUS = b.comment("Chance a mined ore block yields one extra drop.")
                .defineInRange("mineralBonusChance", 0.25, 0.0, 1.0);
        WORK_RESPECT_PLACED = b.comment("Never harvest a block a player placed.")
                .define("respectPlacedBlocks", true);
        b.pop().push("farm");
        FARM_ENABLED = b.comment("Enable the homestead stations: troughs, drying racks and plantable Ark berry bushes.")
                .define("enabled", true);
        FARM_BATCH_TICKS = b.comment("Ticks between station batch updates. Stations never tick per tick and never "
                        + "process unloaded chunks, so there is no offline catch-up.")
                .defineInRange("batchTicks", 100, 20, 1200);
        FARM_TROUGH_RADIUS = b.comment("Blocks around a trough it feeds tamed creatures in.")
                .defineInRange("troughRadius", 8, 2, 24);
        FARM_TROUGH_CAPACITY = b.comment("Items a trough holds per feeding stack.")
                .defineInRange("troughCapacity", 32, 1, 64);
        FARM_TROUGH_MAX_PER_BATCH = b.comment("Creatures a single trough feeds per batch.")
                .defineInRange("troughMaxPerBatch", 4, 1, 16);
        FARM_TROUGH_HUNGER = b.comment("A tamed creature is fed when its hunger is at least this value.")
                .defineInRange("troughHungerThreshold", 60.0, 0.0, 1000.0);
        FARM_TROUGH_FEED = b.comment("Hunger removed by one trough feeding.")
                .defineInRange("troughFeedAmount", 30.0, 1.0, 1000.0);
        FARM_TROUGH_HEAL = b.comment("Health restored by one trough feeding, capped at the creature's maximum.")
                .defineInRange("troughHeal", 2.0, 0.0, 20.0);
        FARM_DRYING_BATCHES = b.comment("Batches a drying rack needs per item before it becomes a dried ration.")
                .defineInRange("dryingBatches", 6, 1, 60);
        FARM_DRYING_CAPACITY = b.comment("Raw food a drying rack accepts per load.")
                .defineInRange("dryingCapacity", 8, 1, 64);
        FARM_DRIED_TIER_TWO = b.comment("Batches dried meat must keep hanging on a rack to become Dried Meat II (240 = one in-game day at 100 batch ticks).")
                .defineInRange("driedTierTwoBatches", 240, 1, 10000);
        FARM_DRIED_TIER_THREE = b.comment("Total hanging batches for Dried Meat III (720 = three in-game days at 100 batch ticks).")
                .defineInRange("driedTierThreeBatches", 720, 2, 30000);
        FARM_BERRY_GROWTH = b.comment("Chance per random tick that a planted Ark berry bush advances one age.")
                .defineInRange("berryGrowthChance", 0.2, 0.01, 1.0);
        b.pop().push("kitchen");
        KITCHEN_ENABLED = b.comment("Enable the cooking pot and its prepared meals.")
                .define("enabled", true);
        KITCHEN_COOK_BATCHES = b.comment("Batches a matching set of ingredients needs to become a meal.")
                .defineInRange("cookBatches", 4, 1, 60);
        b.pop().push("mass");
        MASS_ENABLED = b.comment("Track carried mass for players and, later, tames. Nothing blocks item movement: "
                        + "capacity is a movement budget, so players may overload deliberately to rearrange or drop cargo.")
                .define("enabled", true);
        MASS_PRESET = b.comment("STANDARD uses the ratios below. RELAXED raises capacities by half and starts every "
                        + "penalty 25 points later. OFF disables mass and the gauge entirely.")
                .defineEnum("preset", MassRules.Preset.STANDARD);
        MASS_PLAYER_CAPACITY = b.comment("Player capacity in mass units. A working kit should use roughly a quarter "
                        + "to a third, leaving room for a useful haul.")
                .defineInRange("playerCapacity", 100.0, 10.0, 1000.0);
        MASS_MULTIPLIER = b.comment("Global multiplier for every item mass; lower makes all loads lighter.")
                .defineInRange("massMultiplier", 1.0, 0.1, 5.0);
        MASS_WARNING_RATIO = b.comment("Load ratio that warns and colors the gauge, with no movement penalty.")
                .defineInRange("warningRatio", 0.75, 0.25, 1.5);
        MASS_SLOW_RATIO = b.comment("Load ratio where sprint is denied and movement starts slowing. This is the "
                        + "overload line; the warning band below it stays penalty-free.")
                .defineInRange("slowRatio", 1.0, 0.5, 2.0);
        MASS_HEAVY_RATIO = b.comment("Load ratio where slowdown reaches its floor. Walking and dropping cargo always "
                        + "remain possible.")
                .defineInRange("heavyRatio", 1.25, 0.75, 3.0);
        MASS_SPEED_FLOOR = b.comment("Lowest fraction of normal movement speed; overload never immobilizes.")
                .defineInRange("speedFloor", 0.35, 0.1, 0.9);
        MASS_UNKNOWN_CONTAINER = b.comment("Flat mass for container items the model cannot inspect "
                        + "(tag arksurvivalreturns:mass/unknown_container).")
                .defineInRange("unknownContainerMass", 16.0, 1.0, 128.0);
        MASS_CONTAINER_CONTENT_CAP = b.comment("Most mass counted from one container's contents. Nested containers "
                        + "count only their own base mass, so reductions can never multiply recursively.")
                .defineInRange("containerContentCap", 128.0, 0.0, 1024.0);
        MASS_AUTOMATION_CEILING = b.comment("Fast Load and work jobs stop at this load ratio; manual loading may "
                        + "exceed it up to the heavy band.")
                .defineInRange("automationCeiling", 1.0, 1.0, 1.25);
        MASS_BARE_CAPACITY = b.comment("Capacity of a tame without its required harness; the harness raises it to "
                        + "the species value below.")
                .defineInRange("bareCapacity", 100.0, 10.0, 1000.0);
        CARGO_TRANSFER_RADIUS = b.comment("Radius the Load and Unload buttons search for containers in. Only loaded "
                        + "chunks are read; a larger radius never forces a chunk to load.")
                .defineInRange("transferRadius", 8, 2, 16);
        for (var species : Species.values()) {
            b.push(species.id);
            CARGO_CAPACITY.put(species, b.comment("Species cargo capacity with its required harness.")
                    .defineInRange("capacity", CargoProfiles.of(species).capacity(), 50.0, 2000.0));
            b.pop();
        }
        b.pop().push("levels");
        HEALTH_GROWTH = b.comment("HP = base HP * (1 + growth * (level - 1)^0.85). Applies on spawn.").defineInRange("healthGrowth", 0.10, 0.0, 0.20);
        DAMAGE_GROWTH = b.comment("Damage = base damage * (1 + growth * sqrt(level - 1)). Applies on spawn.").defineInRange("damageGrowth", 0.14, 0.0, 0.5);
        for (var tier : BiomeTier.values()) {
            b.push(tier.id);
            MIN_LEVEL.put(tier, b.defineInRange("min", tier.minLevel, 1, 100));
            MAX_LEVEL.put(tier, b.comment("Reversed endpoints are sorted automatically.").defineInRange("max", tier.maxLevel, 1, 100));
            b.pop();
        }
        b.pop().push("taming");
        TAMING_ENABLED = b.comment("Master switch for torpor, taming, riding and the creature inventory.")
                .define("enabled", true);
        TAMING_DEBUG_LOG = b.comment("Log sedation and feeding transitions with their reasons. Never logs every tick.")
                .define("debugLog", false);
        PLAYER_MAX_TORPOR = b.comment("Player knockout threshold in normalized torpor units.")
                .defineInRange("playerMaxTorpor", 100, 1, 10000);
        PLAYER_TORPOR_RESISTANCE = b.comment("Incoming sedative potency multiplier for players; lower is more resistant.")
                .defineInRange("playerSedativeResistance", 1.0, 0.05, 10.0);
        for (var size : dev.nez.arksurvivalreturns.feature.taming.CreatureSize.values()) {
            int fallback = switch (size) {
                case SMALL -> 60;
                case MEDIUM -> 150;
                case LARGE -> 350;
                case GIANT -> 700;
            };
            MAX_TORPOR.put(size, b.comment("Torpor ceiling for a " + size.name().toLowerCase(java.util.Locale.ROOT)
                    + " creature; sizes come from the registered hitbox height.")
                    .defineInRange(size.name().toLowerCase(java.util.Locale.ROOT) + "MaxTorpor", fallback, 1, 100000));
        }
        WAKE_THRESHOLD_RATIO = b.comment("Wake below this share of the maximum torpor.")
                .defineInRange("wakeThresholdRatio", 0.20, 0.01, 0.95);
        TORPOR_RECOVERY_DELAY = b.comment("Ticks of no further sedation before recovery starts (200 = 10 seconds).")
                .defineInRange("torporRecoveryDelayTicks", 200, 0, 24000);
        TORPOR_RECOVERY_PER_SECOND = b.comment("Recovery per second as a share of the maximum, after the delay. "
                        + "Zero wakes the entity as soon as the recovery delay expires.")
                .defineInRange("torporRecoveryPerSecond", 0.005, 0.0, 1.0);
        TORPOR_SYNC_INTERVAL = b.comment("Maximum normal tick interval between relevant client updates.")
                .defineInRange("torporSyncIntervalTicks", 5, 1, 100);
        BASIC_SEDATIVE_POTENCY = b.comment("Dose of the baseline sedative in normalized torpor units.")
                .defineInRange("basicSedativePotency", 25.0, 0.1, 100000.0);
        CONCENTRATED_SEDATIVE_POTENCY = b.comment("Dose of the concentrated sedative in normalized torpor units.")
                .defineInRange("concentratedSedativePotency", 100.0, 0.1, 100000.0);
        TRANQUILIZER_ARROW_POTENCY = b.comment("Dose delivered by one tranquilizer arrow. The ranged route has to be "
                        + "worth the crafting cost, because recovery continues during a knock-out.")
                .defineInRange("tranquilizerArrowPotency", 75.0, 0.1, 100000.0);
        TORPOR_COLLAPSE_FALLBACK_TICKS = b.comment("Collapse length for rigs with no imported torpor sequence.")
                .defineInRange("collapseFallbackTicks", 40, 1, 400);
        TORPOR_WAKE_FALLBACK_TICKS = b.comment("Wake length for rigs with no imported torpor sequence.")
                .defineInRange("wakeFallbackTicks", 40, 1, 400);
        TORPOR_FEED_FALLBACK_TICKS = b.comment("Feeding animation length for rigs with no imported feeding clip.")
                .defineInRange("feedFallbackTicks", 40, 1, 400);
        FEED_HUNGER_THRESHOLD = b.comment("Minimum feeding hunger needed to eat. 0 is full, 100 is very hungry.")
                .defineInRange("feedHungerThreshold", 40.0, 0.0, 100.0);
        WILD_HUNGER_MIN = b.comment("Lower bound of the feeding hunger a newly spawned wild creature starts with.")
                .defineInRange("wildHungerMinimum", 55.0, 0.0, 100.0);
        WILD_HUNGER_MAX = b.comment("Upper bound of that range; avoid every spawn behaving identically.")
                .defineInRange("wildHungerMaximum", 95.0, 0.0, 100.0);
        HUNGER_INCREASE_PER_SECOND = b.comment("Appetite recovered per second, so a meal takes about "
                        + "hungerReductionPerMeal / this value to become possible again.")
                .defineInRange("hungerIncreasePerSecond", 1.0, 0.05, 20.0);
        HUNGER_REDUCTION_PER_MEAL = b.comment("Satiation of one meal.")
                .defineInRange("hungerReductionPerMeal", 20.0, 0.1, 100.0);
        MINIMUM_FEED_INTERVAL = b.comment("Minimum ticks between meals (400 = 20 seconds). This is also the meal "
                        + "budget a profile spends its target duration on.")
                .defineInRange("minimumFeedIntervalTicks", 400, 20, 24000);
        FOOD_UNITS_PER_MEAL = b.comment("Items consumed by one successful meal.")
                .defineInRange("foodUnitsPerMeal", 1, 1, 16);
        PREFERRED_FOOD_MULTIPLIER = b.comment("Taming progress multiplier for a species' favourite food.")
                .defineInRange("preferredFoodMultiplier", 1.5, 1.0, 10.0);
        DAMAGE_PROGRESS_PENALTY = b.comment("Percentage points lost when a wild creature being tamed takes damage.")
                .defineInRange("damageProgressPenalty", 10.0, 0.0, 100.0);
        PASSIVE_PROGRESS_GRACE = b.comment("Ticks before an abandoned passive or aerial attempt starts to decay "
                        + "(2400 = 120 seconds).")
                .defineInRange("passiveProgressGraceTicks", 2400, 0, 24000);
        PASSIVE_PROGRESS_DECAY_PERIOD = b.comment("Decay period in ticks (200 = 10 seconds).")
                .defineInRange("passiveProgressDecayPeriodTicks", 200, 20, 24000);
        PASSIVE_PROGRESS_DECAY = b.comment("Percentage points lost per decay period.")
                .defineInRange("passiveProgressDecay", 1.0, 0.0, 100.0);
        CLAIM_EXPIRY = b.comment("Ticks of inactivity after which a claim on a wild creature expires "
                        + "(2400 = two minutes).")
                .defineInRange("claimExpiryTicks", 2400, 200, 24000);
        FEEDING_TRUCE_TICKS = b.comment("Feeder-specific truce granted by a successful aerial feeding "
                        + "(300 = 15 seconds).")
                .defineInRange("feedingTruceTicks", 300, 0, 2400);
        RIDDEN_FLIGHT_SPEED_MULTIPLIER = b.comment("Flight speed of a ridden flyer, as a multiple of its movement speed.")
                .defineInRange("riddenFlightSpeedMultiplier", 2.0, 0.2, 12.0);
        RIDDEN_SWIM_SPEED_MULTIPLIER = b.comment("Swim speed of a ridden water creature, as a multiple of its movement speed.")
                .defineInRange("riddenSwimSpeedMultiplier", 1.0, 0.2, 12.0);
        PLAYER_MOVEMENT_TOLERANCE = b.comment("Horizontal drift an unconscious player may accumulate before the "
                        + "server pulls them back to where they fell asleep, in blocks.")
                .defineInRange("playerMovementTolerance", 0.35, 0.05, 4.0);
        PLAYER_IMPULSE_TICKS = b.comment("Ticks of unimpeded movement granted to an unconscious player after taking "
                        + "damage, so knockback still works.")
                .defineInRange("playerImpulseTicks", 12, 0, 100);
        b.pop().push("nighttime");
        NIGHTTIME = b.comment("Land wildlife only; flying species retain their existing behavior. Requires a dimension with a normal sky clock.").define("enabled", true);
        NIGHT_START = b.defineInRange("nightStartTick", 13000, 0, 23999);
        NIGHT_END = b.comment("Equal start/end disables the night window.").defineInRange("nightEndTick", 23000, 0, 23999);
        NIGHT_TRANSITION = b.comment("Maximum individual dusk/dawn delay; 600 ticks = 30 seconds.").defineInRange("transitionTicks", 600, 0, 1200);
        NIGHT_HUNGER = b.defineInRange("carnivoreHungerMultiplier", 2.0, 1.0, 5.0);
        NIGHT_VISION = b.comment("Multiplier of daytime sight for land carnivores at night; cover, rain and crouching still apply.").defineInRange("carnivoreVisionMultiplier", 1.3, 1.0, 2.0);
        DAY_SLEEP = b.comment("Share of undisturbed daytime routine spent sleeping; urgent needs and danger override.").defineInRange("carnivoreDaySleepFraction", 0.7, 0.0, 1.0);
        WAKE_DISTANCE = b.comment("Distance from body bounds for ordinary player approach; noisy actions can wake from farther away.").defineInRange("playerWakeDistance", 8.0, 2.0, 24.0);
        SLEEP_CALM = b.comment("Simulated ticks without relevant danger before sleep is allowed again.").defineInRange("calmBeforeSleepTicks", 200, 20, 1200);
        b.pop().push("wildlife");
        for (var family : dev.nez.arksurvivalreturns.feature.land.LandFamily.values()) {
            b.push(family.name().toLowerCase(java.util.Locale.ROOT));
            LAND_ROAM.put(family, b.defineInRange("roamRadius", family.roam, 24, 160));
            LAND_LEASH.put(family, b.defineInRange("returnRadius", family.leash, 32, 224));
            LAND_WATER_PREFERRED.put(family, b.defineInRange("preferredWaterDistance", family.preferredWater, 0, 96));
            LAND_WATER_MAX.put(family, b.defineInRange("maximumWaterDistance", family.maximumWater, 16, 128));
            b.pop();
        }
        b.pop().push("flying");
        ARGENT_NEST_Y = b.comment("Minimum floor Y for natural Argentavis nests.").defineInRange("argentavisMinimumNestY", 96, 64, 256);
        NEST_WATER_RADIUS = b.comment("Maximum horizontal distance from a Pteranodon nest to exposed water.").defineInRange("shoreWaterRadius", 12, 4, 24);
        PTERO_ROAM_RADIUS = b.defineInRange("pteranodonRoamRadius", 32, 16, 48);
        ARGENT_ROAM_RADIUS = b.defineInRange("argentavisRoamRadius", 48, 24, 56);
        FLIGHT_LEASH = b.comment("Maximum horizontal chase distance from the habitat center.").defineInRange("defenseRadius", 64, 56, 80);
        EGG_DEFENSE_TICKS = b.comment("Maximum defense duration after each actual egg disturbance.").defineInRange("eggDefenseTicks", 600, 100, 1200);
        PERCHING = b.comment("Independent landing/rest/takeoff at safe nest perches; no hunger or thirst.").define("perching", true);
        b.pop().push("theme");
        THEME_DIMENSIONS = b.comment("Refuse Nether and End access, and return players already inside those dimensions to the Overworld. Portal structures, structures, loot and advancements are removed by the data pack either way.").define("dimensions", true);
        THEME_MONSTERS = b.comment("Block every survival creation route for removed creatures, including spawners, conversions and creative spawn eggs. Generated biome spawn lists are cleaned by the data pack either way.").define("monsters", true);
        THEME_MECHANICS = b.comment("Refuse enchanting, brewing, teleportation, magical infrastructure, sculk, totems, golems, the Wither build and elytra flight in worlds that already contain them.").define("mechanics", true);
        b.pop().push("display");
        BIOME_MESSAGES = b.define("biomeEntryMessages", true);
        HEALTH_BAR = b.define("targetHealthBar", true);
        HEALTH_BAR_RANGE = b.defineInRange("targetRange", 32, 8, 64);
        MASS_GAUGE = b.comment("Show the carried-load gauge in the top-left of the HUD.").define("massGauge", true);
        b.pop().push("guardian");
        GUARDIAN_ENABLED = b.comment("Enable the First Guardian ritual encounter at Ancient Remnants monoliths. "
                        + "When the mod is absent, the journal explains that the encounter is unavailable.")
                .define("enabled", true);
        GUARDIAN_ANNOUNCE = b.comment("Tell the tribe when the Guardian awakens, resets or falls.")
                .define("announce", true);
        GUARDIAN_STRUCTURES = b.comment("Ancient Remnants structures whose floating monolith accepts the Allosaur Heart.")
                .defineList("structures", List.of("ancient_remnants:sentinel_monolith"),
                        () -> "ancient_remnants:sentinel_monolith",
                        value -> value instanceof String text && net.minecraft.resources.Identifier.tryParse(text) != null);
        GUARDIAN_ACTIVATION_RADIUS = b.comment("The heart must be offered on a block this close to the structure's monolith.")
                .defineInRange("activationRadius", 16, 4, 48);
        GUARDIAN_ARENA_RADIUS = b.comment("The Guardian returns to its lair when farther than this from the ritual anchor.")
                .defineInRange("arenaRadius", 40, 16, 96);
        GUARDIAN_JOIN_RADIUS = b.comment("Tribe members inside this radius become encounter participants.")
                .defineInRange("joinRadius", 48, 16, 128);
        GUARDIAN_GRACE_TICKS = b.comment("Ticks without a living participant in the arena before the attempt resets "
                        + "(600 = thirty seconds).")
                .defineInRange("graceTicks", 600, 100, 12000);
        GUARDIAN_RESET_DELAY_TICKS = b.comment("Ticks between the retreat reset and the free retry window (200 = ten seconds).")
                .defineInRange("resetDelayTicks", 200, 20, 6000);
        GUARDIAN_MAX_TAME_CONTRIBUTION = b.comment("Registered combat tames that add health to the Guardian; further "
                        + "tames are still registered but add no health.")
                .defineInRange("maxTameContribution", 4, 0, 8);
        GUARDIAN_TAME_RADIUS = b.comment("Radius around the ritual anchor where tribe tames are registered at activation.")
                .defineInRange("tameRegistrationRadius", 24, 4, 64);
        GUARDIAN_BAR_RANGE = b.comment("Players within this radius of the Guardian see the encounter bar.")
                .defineInRange("barRange", 64, 16, 128);
        GUARDIAN_SPAWN_RADIUS = b.comment("Horizontal radius searched for clear, loaded ground when the Guardian rises.")
                .defineInRange("spawnRadius", 16, 4, 48);
        GUARDIAN_BASE_HEALTH = b.comment("Guardian health with one participant.")
                .defineInRange("baseHealth", 400.0, 20.0, 5000.0);
        GUARDIAN_HEALTH_PER_PLAYER = b.comment("Additional health for each participant after the first.")
                .defineInRange("healthPerExtraPlayer", 200.0, 0.0, 5000.0);
        GUARDIAN_HEALTH_PER_TAME = b.comment("Additional health for each registered combat tame, up to the contribution cap.")
                .defineInRange("healthPerTame", 60.0, 0.0, 2000.0);
        GUARDIAN_DAMAGE_MULTIPLIER = b.comment("Guardian attack damage as a multiple of the ordinary Giganotosaurus.")
                .defineInRange("damageMultiplier", 1.0, 0.1, 5.0);
        GUARDIAN_ARMOR = b.comment("Guardian armor points.")
                .defineInRange("armor", 8.0, 0.0, 30.0);
        GUARDIAN_TAME_DAMAGE_FACTOR = b.comment("Damage dealt by tames that were not registered for the encounter "
                        + "(0 disables their damage entirely).")
                .defineInRange("unregisteredTameDamageFactor", 0.1, 0.0, 1.0);
        b.pop().push("tech");
        TECH_ENABLED = b.comment("Enable the technology tree and its /arktech progression.")
                .define("enabled", true);
        TECH_ANNOUNCE = b.comment("Announce a completed technology node to the tribe.")
                .define("announce", true);
        TECH_SCAN_TICKS = b.comment("Ticks between possession scans for collection tasks (100 = five seconds; "
                        + "0 disables the periodic pass).")
                .defineInRange("scanTicks", 100, 0, 1200);
        b.pop();
        SPEC = b.build();
    }
    private Config() {}
}
