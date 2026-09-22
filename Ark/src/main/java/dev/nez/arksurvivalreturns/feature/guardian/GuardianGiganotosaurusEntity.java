package dev.nez.arksurvivalreturns.feature.guardian;

import java.util.UUID;
import dev.nez.arksurvivalreturns.Config;
import dev.nez.arksurvivalreturns.feature.creature.CreatureEntity;
import dev.nez.arksurvivalreturns.feature.creature.Species;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The encounter boss: an ordinary Giganotosaurus body with an authored encounter contract.
 *
 * <p>It is intentionally a dedicated type outside {@code ModContent.CREATURES}, so it never enters
 * biome spawn lists, the population budget or the taming registry. It cannot be tamed or sedated,
 * never despawns and returns to its ritual anchor when dragged away. Its damage uses the shared
 * hit-frame combat path, and it drops nothing by itself: the victory reward is issued once by
 * {@link GuardianService}.
 */
public class GuardianGiganotosaurusEntity extends CreatureEntity {
    private static final String ANCHOR_TAG = "GuardianAnchor";
    private static final String MAX_HEALTH_TAG = "GuardianMaxHealth";
    private static final String HOME_TAG = "GuardianHome";

    private String anchorKey = "";
    private double guardianMaxHealth;
    private @Nullable BlockPos home;
    private int leashCooldown;

    public GuardianGiganotosaurusEntity(EntityType<? extends GuardianGiganotosaurusEntity> type, Level level) {
        super(type, level, Species.GIGANOTOSAURUS);
    }

    /** Guardian attributes add armor on top of the ordinary Giga body. */
    public static net.minecraft.world.entity.ai.attributes.AttributeSupplier.Builder attributes() {
        return CreatureEntity.attributes(Species.GIGANOTOSAURUS).add(Attributes.ARMOR, 8.0);
    }

    @Override
    protected void registerGoals() {
        // A guardian does not sleep, feed, wander with companions or answer work orders.
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new GuardianCombatGoal(this));
    }

    /** Untamable: ordinary feeding, inventory and mounting interactions are refused. */
    @Override protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        return InteractionResult.PASS;
    }

    /** Guardians never leave the encounter; there is no tame state to apply. */
    @Override public void onTamed(UUID owner) {}

    /** Not wildlife: excluded from population accounting and culling. */
    @Override public boolean isNaturalWildlife() {
        return false;
    }

    @Override public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override public boolean isPersistenceRequired() {
        return true;
    }

    /** Called once by the service after the entity is in the world. */
    public void initializeGuardian(String anchorKey, BlockPos home, double maxHealth, double damageMultiplier,
            double armor) {
        this.anchorKey = anchorKey;
        this.home = home.immutable();
        this.guardianMaxHealth = maxHealth;
        initializeLevel(1);
        applyGuardianAttributes(damageMultiplier, armor);
        getAttribute(Attributes.MAX_HEALTH).setBaseValue(maxHealth);
        setHealth((float) maxHealth);
        setCustomName(Component.translatable("guardian.arksurvivalreturns.name"));
        setCustomNameVisible(true);
        setPersistenceRequired();
    }

    private void applyGuardianAttributes(double damageMultiplier, double armor) {
        getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(species().damage * damageMultiplier);
        var armorAttribute = getAttribute(Attributes.ARMOR);
        if (armorAttribute != null) armorAttribute.setBaseValue(armor);
    }

    public String guardianKey() {
        return anchorKey;
    }

    public @Nullable BlockPos guardianHome() {
        return home;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide() || !isAlive() || home == null) return;
        if (leashCooldown > 0) leashCooldown--;
        double arena = Config.GUARDIAN_ARENA_RADIUS.get();
        double distance = Math.sqrt(distanceToSqr(home.getX() + 0.5, home.getY() + 0.5, home.getZ() + 0.5));
        if (distance <= arena) return;
        setTarget(null);
        if (distance > arena * 2.0) {
            returnHome();
            return;
        }
        if (leashCooldown <= 0) {
            getNavigation().moveTo(home.getX() + 0.5, home.getY(), home.getZ() + 0.5, 1.0);
            leashCooldown = 40;
        }
    }

    /** Puts the guardian back on safe ground at its lair instead of teleporting into geometry. */
    private void returnHome() {
        if (!(level() instanceof net.minecraft.server.level.ServerLevel server) || home == null) return;
        for (int dy = 0; dy <= 24; dy++) {
            BlockPos candidate = home.above(dy);
            if (!server.isLoaded(candidate)) return;
            if (server.isEmptyBlock(candidate) && server.isEmptyBlock(candidate.above())
                    && !server.isEmptyBlock(candidate.below())) {
                snapTo(candidate.getX() + 0.5, candidate.getY(), candidate.getZ() + 0.5);
                setDeltaMovement(Vec3.ZERO);
                getNavigation().stop();
                return;
            }
        }
    }

    /**
     * The empty creature inventory path is inherited. Victory loot is issued exactly once by
     * {@link GuardianService#onBossDeath}; resets discard the boss without any drop at all.
     */
    @Override protected void dropCustomDeathLoot(net.minecraft.server.level.ServerLevel level,
            DamageSource source, boolean hitByPlayer) {
        super.dropCustomDeathLoot(level, source, hitByPlayer);
    }

    @Override protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putString(ANCHOR_TAG, anchorKey);
        output.putDouble(MAX_HEALTH_TAG, guardianMaxHealth);
        if (home != null) output.putLong(HOME_TAG, home.asLong());
    }

    @Override protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        anchorKey = input.getStringOr(ANCHOR_TAG, "");
        guardianMaxHealth = input.getDoubleOr(MAX_HEALTH_TAG, 0.0);
        long packedHome = input.getLongOr(HOME_TAG, Long.MIN_VALUE);
        if (packedHome != Long.MIN_VALUE) home = BlockPos.of(packedHome);
        // Vanilla persists attributes and current HP; re-assert the boss pool in case an older save
        // stored the ordinary species value, and never heal above it.
        if (guardianMaxHealth > 0) {
            getAttribute(Attributes.MAX_HEALTH).setBaseValue(guardianMaxHealth);
            if (getHealth() > guardianMaxHealth) setHealth((float) guardianMaxHealth);
        }
        if (anchorKey.isEmpty()) return;
        if (!hasCustomName()) {
            setCustomName(Component.translatable("guardian.arksurvivalreturns.name"));
            setCustomNameVisible(true);
        }
    }

    @Override
    public net.minecraft.world.entity.SpawnGroupData finalizeSpawn(ServerLevelAccessor level,
            net.minecraft.world.DifficultyInstance difficulty, net.minecraft.world.entity.EntitySpawnReason reason,
            net.minecraft.world.entity.SpawnGroupData data) {
        var result = super.finalizeSpawn(level, difficulty, reason, data);
        setPersistenceRequired();
        return result;
    }
}
