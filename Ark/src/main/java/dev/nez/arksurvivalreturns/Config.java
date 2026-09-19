package dev.nez.arksurvivalreturns;

import java.util.EnumMap;
import dev.nez.arksurvivalreturns.feature.spawn.BiomeTier;
import dev.nez.arksurvivalreturns.feature.creature.Species;
import net.neoforged.neoforge.common.ModConfigSpec;

/** Per-world server configuration; habitat preferences and protected biomes live in data packs. */
public final class Config {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue LAND_HABITATS;
    public static final ModConfigSpec.IntValue LAND_PROBES, LAND_WATER_PATCH, LAND_RECHECK, LAND_REPOPULATE;
    public static final ModConfigSpec.BooleanValue AQUATIC_HABITATS;
    public static final ModConfigSpec.IntValue AQUATIC_PROBES, AQUATIC_DEPTH, AQUATIC_COLUMNS, AQUATIC_RADIUS, AQUATIC_RECHECK, AQUATIC_REPOPULATE;
    public static final EnumMap<dev.nez.arksurvivalreturns.feature.land.LandFamily, ModConfigSpec.IntValue> LAND_ROAM = new EnumMap<>(dev.nez.arksurvivalreturns.feature.land.LandFamily.class);
    public static final EnumMap<dev.nez.arksurvivalreturns.feature.land.LandFamily, ModConfigSpec.IntValue> LAND_LEASH = new EnumMap<>(dev.nez.arksurvivalreturns.feature.land.LandFamily.class);
    public static final EnumMap<dev.nez.arksurvivalreturns.feature.land.LandFamily, ModConfigSpec.IntValue> LAND_WATER_MAX = new EnumMap<>(dev.nez.arksurvivalreturns.feature.land.LandFamily.class);
    public static final EnumMap<dev.nez.arksurvivalreturns.feature.land.LandFamily, ModConfigSpec.IntValue> LAND_WATER_PREFERRED = new EnumMap<>(dev.nez.arksurvivalreturns.feature.land.LandFamily.class);
    public static final ModConfigSpec.DoubleValue PLAYER_SPRINT_REFERENCE;
    public static final EnumMap<Species, ModConfigSpec.DoubleValue> SPRINT_RATIO = new EnumMap<>(Species.class);
    public static final EnumMap<Species, ModConfigSpec.DoubleValue> WATER_RETENTION = new EnumMap<>(Species.class);
    public static final EnumMap<Species, ModConfigSpec.DoubleValue> STRIDE_SCALE = new EnumMap<>(Species.class);
    public static final ModConfigSpec.BooleanValue NATURAL_SPAWNS;
    public static final ModConfigSpec.IntValue LOCAL_CAP;
    public static final ModConfigSpec.IntValue SOLITARY_SPACING;
    public static final ModConfigSpec.IntValue MIN_GROUPS;
    public static final ModConfigSpec.IntValue SPAWN_INTERVAL;
    public static final ModConfigSpec.IntValue GROUPS_PER_PASS;
    public static final ModConfigSpec.IntValue BAND_WIDTH;
    public static final ModConfigSpec.BooleanValue BIOME_MESSAGES;
    public static final ModConfigSpec.BooleanValue THEME_DIMENSIONS, THEME_MONSTERS, THEME_MECHANICS;
    public static final ModConfigSpec.BooleanValue MAP_REQUIRES_UNLOCK;
    public static final EnumMap<Species, ModConfigSpec.IntValue> WEIGHTS = new EnumMap<>(Species.class);
    public static final ModConfigSpec.DoubleValue HEALTH_GROWTH;
    public static final ModConfigSpec.DoubleValue DAMAGE_GROWTH;
    public static final ModConfigSpec.BooleanValue HEALTH_BAR;
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
    public static final ModConfigSpec.DoubleValue BASIC_SEDATIVE_POTENCY, TRANQUILIZER_ARROW_POTENCY;
    public static final ModConfigSpec.IntValue TORPOR_COLLAPSE_FALLBACK_TICKS, TORPOR_WAKE_FALLBACK_TICKS, TORPOR_FEED_FALLBACK_TICKS;
    public static final ModConfigSpec.DoubleValue FEED_HUNGER_THRESHOLD, WILD_HUNGER_MIN, WILD_HUNGER_MAX;
    public static final ModConfigSpec.DoubleValue HUNGER_INCREASE_PER_SECOND, HUNGER_REDUCTION_PER_MEAL;
    public static final ModConfigSpec.IntValue MINIMUM_FEED_INTERVAL, FOOD_UNITS_PER_MEAL, CLAIM_EXPIRY, FEEDING_TRUCE_TICKS;
    public static final ModConfigSpec.DoubleValue PREFERRED_FOOD_MULTIPLIER, DAMAGE_PROGRESS_PENALTY;
    public static final ModConfigSpec.IntValue PASSIVE_PROGRESS_GRACE, PASSIVE_PROGRESS_DECAY_PERIOD;
    public static final ModConfigSpec.DoubleValue PASSIVE_PROGRESS_DECAY, PLAYER_MOVEMENT_TOLERANCE;
    public static final ModConfigSpec.IntValue PLAYER_IMPULSE_TICKS;
    public static final ModConfigSpec.DoubleValue RIDDEN_FLIGHT_SPEED_MULTIPLIER, RIDDEN_SWIM_SPEED_MULTIPLIER;
    static {
        var b = new ModConfigSpec.Builder();
        b.push("spawning");
        NATURAL_SPAWNS = b.comment("Enable natural creature spawns. Spawn eggs and commands still work.").define("enabled", true);
        LOCAL_CAP = b.comment("Maximum Ark creatures within 96 blocks. Independent of vanilla animal caps.").defineInRange("localCap", 24, 1, 128);
        SOLITARY_SPACING = b.comment("Minimum distance between naturally spawning large creatures.").defineInRange("solitarySpacing", 24, 16, 128);
        MIN_GROUPS = b.comment("Target minimum wild groups per player within 96 blocks; retries when safe loaded ground is unavailable.").defineInRange("minimumGroups", 3, 1, 20);
        SPAWN_INTERVAL = b.comment("Ticks between population checks (20 ticks = 1 second).").defineInRange("checkIntervalTicks", 100, 20, 1200);
        GROUPS_PER_PASS = b.comment("Maximum groups added across a dimension per check; players are served round-robin.").defineInRange("groupsPerPass", 2, 1, 8);
        b.push("weights");
        for (var species : Species.values()) WEIGHTS.put(species, b.defineInRange(species.id, species.weight, 0, 100));
        b.pop().pop().push("progression");
        MAP_REQUIRES_UNLOCK = b.comment("Require the saved map entitlement. Disabled during development while taming is unavailable; restart/rejoin after changing.").define("mapRequiresUnlock", false);
        BAND_WIDTH = b.comment("Scale of repeating equal-area danger regions; tile period is four times this value. Saved per world. Legacy key retained for existing configs.").defineInRange("bandWidth", 256, 96, 1024);
        b.pop().push("movement");
        PLAYER_SPRINT_REFERENCE = b.comment("Normal player sprint benchmark in blocks/second; does not chase temporary player potion buffs.").defineInRange("playerSprintBlocksPerSecond", 5.612, 1.0, 20.0);
        for (var species : Species.values()) {
            b.push(species.id);
            SPRINT_RATIO.put(species, b.comment("Full pursuit speed relative to the player benchmark; normal wandering uses a slower gait.").defineInRange("sprintRatio", species.sprintRatioDefault(), 0.2, 4.0));
            WATER_RETENTION.put(species, b.comment("Fraction of land speed retained while swimming.").defineInRange("waterRetention", species.predator ? 1.0 : 0.65, 0.1, 1.5));
            STRIDE_SCALE.put(species, b.comment("Visual stride length multiplier; larger values slow the animation at the same ground speed.").defineInRange("strideScale", 1.0, 0.5, 2.0));
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
        TORPOR_RECOVERY_PER_SECOND = b.comment("Recovery per second as a share of the maximum, after the delay.")
                .defineInRange("torporRecoveryPerSecond", 0.005, 0.0, 1.0);
        TORPOR_SYNC_INTERVAL = b.comment("Maximum normal tick interval between relevant client updates.")
                .defineInRange("torporSyncIntervalTicks", 5, 1, 100);
        BASIC_SEDATIVE_POTENCY = b.comment("Dose of the baseline sedative in normalized torpor units.")
                .defineInRange("basicSedativePotency", 25.0, 0.1, 100000.0);
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
        b.pop().push("aquatic");
        AQUATIC_HABITATS = b.comment("Saved water bodies for water-bound species; solo groups that roam one home pool.").define("enabled", true);
        AQUATIC_PROBES = b.comment("Maximum water-column reads per dimension tick while planning a home pool.").defineInRange("waterProbesPerTick", 2048, 256, 8192);
        AQUATIC_DEPTH = b.comment("Minimum swimmable depth for a valid pool; shallow water is rejected or retried.").defineInRange("minimumDepth", 6, 3, 24);
        AQUATIC_COLUMNS = b.comment("Connected deep-water columns a pool needs before it can host a resident.").defineInRange("minimumColumns", 12, 4, 64);
        AQUATIC_RADIUS = b.comment("How far a pool is sampled from the spawn point, in blocks.").defineInRange("searchRadius", 32, 8, 64);
        AQUATIC_RECHECK = b.defineInRange("waterRecheckTicks", 1200, 200, 12000);
        AQUATIC_REPOPULATE = b.comment("Cooldown after a confirmed permanent removal; unloading does not free a slot.").defineInRange("replacementCooldownTicks", 12000, 200, 72000);
        b.pop().push("landHabitats");
        LAND_HABITATS = b.comment("Persistent water-associated land groups; legacy local behavior remains available when disabled.").define("enabled", true);
        LAND_PROBES = b.comment("Maximum surface-water planning block/height reads per dimension tick.").defineInRange("waterProbesPerTick", 256, 32, 2048);
        LAND_WATER_PATCH = b.comment("Connected surface water cells, including a 2x2 patch. Lower for narrow rivers.").defineInRange("minimumWaterCells", 8, 4, 16);
        LAND_RECHECK = b.defineInRange("waterRecheckTicks", 1200, 200, 12000);
        LAND_REPOPULATE = b.comment("Cooldown after a confirmed permanent member removal; unloading does not free a slot.").defineInRange("replacementCooldownTicks", 12000, 200, 72000);
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
        b.pop();
        SPEC = b.build();
    }
    private Config() {}
}
