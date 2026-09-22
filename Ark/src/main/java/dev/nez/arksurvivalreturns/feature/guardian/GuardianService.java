package dev.nez.arksurvivalreturns.feature.guardian;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import dev.nez.arksurvivalreturns.Config;
import dev.nez.arksurvivalreturns.feature.creature.CreatureEntity;
import dev.nez.arksurvivalreturns.feature.creature.Species;
import dev.nez.arksurvivalreturns.feature.recovery.RecoveryAttachments;
import dev.nez.arksurvivalreturns.feature.taming.TamingService;
import dev.nez.arksurvivalreturns.feature.taming.TorporService;
import dev.nez.arksurvivalreturns.feature.tribe.TribeService;
import dev.nez.arksurvivalreturns.registry.ModContent;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Server-authoritative First Guardian encounter.
 *
 * <p>Activation happens only through a verified block inside a configured Ancient Remnants structure,
 * close to its floating monolith, while holding the Allosaur Heart. The heart is consumed after the
 * boss exists and the record is saved; a failure retains it. The tribe owns the unlocked attempt, so
 * a retreat costs supplies but never another heart.
 */
public final class GuardianService {
    public static final String ADVANCEMENT = "journal/first_guardian";
    private static final double TAME_WIDTH = 5.25;
    private static final double TAME_HEIGHT = 15.0;

    /** The encounter that owns this guardian entity, if any. */
    public static Optional<GuardianEncounter> encounterFor(GuardianGiganotosaurusEntity boss) {
        if (!(boss.level() instanceof ServerLevel level)) return Optional.empty();
        if (boss.guardianKey().isEmpty()) return Optional.empty();
        return GuardianData.get(level).find(boss.guardianKey()).filter(encounter -> encounter.owns(boss.getUUID()));
    }

    /** Participants and registered tames may be targeted by the boss. */
    public static boolean isEncounterTarget(GuardianGiganotosaurusEntity boss, LivingEntity candidate) {
        Optional<GuardianEncounter> encounter = encounterFor(boss);
        if (encounter.isEmpty()) return false;
        if (!encounter.get().participants().contains(candidate.getUUID())
                && !encounter.get().tames().contains(candidate.getUUID())) return false;
        BlockPos anchor = encounter.get().anchor();
        double radius = Config.GUARDIAN_ARENA_RADIUS.get();
        return candidate.blockPosition().distSqr(anchor) <= radius * radius;
    }

    /** True when the player belongs to the encounter tribe; a missing FTB manager falls back to UUIDs. */
    public static boolean inTribe(UUID player, UUID tribe) {
        if (player.equals(tribe)) return true;
        return TribeService.team(player).map(team -> team.getId().equals(tribe)).orElse(false);
    }

    // -------------------------------------------------------------------------------- activation

    public static InteractionResult activate(ServerPlayer player, InteractionHand hand, BlockPos pos) {
        if (!Config.GUARDIAN_ENABLED.get()) return InteractionResult.PASS;
        ServerLevel level = player.level();
        ItemStack held = player.getItemInHand(hand);
        boolean heart = held.is(ModContent.ALLOSAUR_HEART.get());
        if (!GuardianStructure.available()) {
            if (heart) player.sendSystemMessage(
                    Component.translatable("guardian.arksurvivalreturns.requires_mod").withStyle(ChatFormatting.GRAY),
                    true);
            return InteractionResult.PASS;
        }
        Optional<AnchorMatch> match = matchingAnchor(level, pos);
        if (match.isEmpty()) return InteractionResult.PASS;
        if (!GuardianStructure.monolithNear(level, pos, Config.GUARDIAN_ACTIVATION_RADIUS.get())) {
            if (heart) player.sendSystemMessage(
                    Component.translatable("guardian.arksurvivalreturns.no_monolith"), true);
            return InteractionResult.PASS;
        }
        GuardianData data = GuardianData.get(level);
        String key = GuardianEncounter.key(level.dimension(), match.get().structure(), match.get().start());
        GuardianEncounter existing = data.find(key).orElse(null);
        GuardianState state = existing == null ? GuardianState.LOCKED : existing.state();
        if (state == GuardianState.ACTIVE) {
            player.sendSystemMessage(Component.translatable("guardian.arksurvivalreturns.active"), true);
            return InteractionResult.SUCCESS;
        }
        if (state == GuardianState.RESETTING) {
            player.sendSystemMessage(Component.translatable("guardian.arksurvivalreturns.resetting"), true);
            return InteractionResult.SUCCESS;
        }
        boolean offering = state == GuardianState.LOCKED || state == GuardianState.DEFEATED;
        if (offering && !heart) return InteractionResult.PASS;
        if (player.getData(RecoveryAttachments.DOWNED).downed() || TorporService.restricted(player)) {
            player.sendSystemMessage(Component.translatable("guardian.arksurvivalreturns.downed"), true);
            return InteractionResult.SUCCESS;
        }
        if (!GuardianStructure.arenaLoaded(level, pos, Config.GUARDIAN_ARENA_RADIUS.get())) {
            player.sendSystemMessage(Component.translatable("guardian.arksurvivalreturns.chunks"), true);
            return InteractionResult.SUCCESS;
        }
        Optional<Vec3> spawn = findSpawn(level, pos);
        if (spawn.isEmpty()) {
            player.sendSystemMessage(Component.translatable("guardian.arksurvivalreturns.no_room"), true);
            return InteractionResult.SUCCESS;
        }
        UUID tribe = TribeService.team(player).map(team -> team.getId()).orElse(player.getUUID());
        Set<UUID> participants = nearbyParticipants(level.getServer(), level, tribe, pos);
        participants.add(player.getUUID());
        Set<UUID> tames = registerTames(level, tribe, pos);
        double health = GuardianPolicy.bossHealth(Config.GUARDIAN_BASE_HEALTH.get(), participants.size(),
                tames.size(), Config.GUARDIAN_HEALTH_PER_PLAYER.get(), Config.GUARDIAN_HEALTH_PER_TAME.get(),
                Config.GUARDIAN_MAX_TAME_CONTRIBUTION.get());
        var guardian = ModContent.GUARDIAN_GIGANOTOSAURUS.get().create(level, EntitySpawnReason.MOB_SUMMONED);
        if (guardian == null) {
            player.sendSystemMessage(Component.translatable("guardian.arksurvivalreturns.failed"), true);
            return InteractionResult.SUCCESS;
        }
        BlockPos home = BlockPos.containing(spawn.get());
        guardian.snapTo(spawn.get().x, spawn.get().y, spawn.get().z, level.getRandom().nextFloat() * 360.0f, 0.0f);
        level.addFreshEntity(guardian);
        guardian.initializeGuardian(key, home, health, Config.GUARDIAN_DAMAGE_MULTIPLIER.get(),
                Config.GUARDIAN_ARMOR.get());
        boolean rewardsIssued = existing != null && existing.rewardsIssued();
        GuardianEncounter encounter = new GuardianEncounter(key, level.dimension(), pos.immutable(), match.get().structure(),
                tribe, GuardianState.ACTIVE, Optional.of(guardian.getUUID()), participants, tames, health,
                rewardsIssued, 0L, 0L);
        data.put(encounter);
        if (offering) held.shrink(1);
        GuardianBar.show(key, guardian);
        announce(level, encounter, Component.translatable(offering
                ? "guardian.arksurvivalreturns.awakened"
                : "guardian.arksurvivalreturns.reawakened").withStyle(ChatFormatting.GOLD));
        return InteractionResult.SUCCESS;
    }

    private record AnchorMatch(Identifier structure, StructureStart start) {}

    private static Optional<AnchorMatch> matchingAnchor(ServerLevel level, BlockPos pos) {
        if (!level.isLoaded(pos)) return Optional.empty();
        for (Identifier id : GuardianStructure.structures()) {
            Optional<StructureStart> start = GuardianStructure.startAt(level, pos, id);
            if (start.isPresent()) return Optional.of(new AnchorMatch(id, start.get()));
        }
        return Optional.empty();
    }

    /** Searches loaded terrain for a floor with room for the full Giga hitbox and no blockers. */
    public static Optional<Vec3> findSpawn(ServerLevel level, BlockPos around) {
        int radius = Config.GUARDIAN_SPAWN_RADIUS.get();
        for (int attempt = 0; attempt < 64; attempt++) {
            int x = around.getX() + level.getRandom().nextInt(radius * 2 + 1) - radius;
            int z = around.getZ() + level.getRandom().nextInt(radius * 2 + 1) - radius;
            for (int y = around.getY() + 32; y >= around.getY() - 32; y--) {
                BlockPos base = new BlockPos(x, y, z);
                if (!level.isLoaded(base)) break;
                BlockState state = level.getBlockState(base);
                if (!state.isFaceSturdy(level, base, Direction.UP)) continue;
                BlockPos stand = base.above();
                if (!columnClear(level, stand)) continue;
                AABB box = new AABB(stand.getX() - TAME_WIDTH, stand.getY(), stand.getZ() - TAME_WIDTH,
                        stand.getX() + TAME_WIDTH, stand.getY() + TAME_HEIGHT, stand.getZ() + TAME_WIDTH);
                if (!level.noCollision(box)) continue;
                if (!level.getEntitiesOfClass(Entity.class, box, Entity::isAlive).isEmpty()) continue;
                return Optional.of(Vec3.atBottomCenterOf(stand));
            }
        }
        return Optional.empty();
    }

    private static boolean columnClear(ServerLevel level, BlockPos stand) {
        for (int dy = 0; dy < 16; dy++) {
            BlockPos pos = stand.above(dy);
            if (!level.isLoaded(pos) || !level.isEmptyBlock(pos)) return false;
        }
        return true;
    }

    // ----------------------------------------------------------------------------------- runtime

    /** Bounded encounter tick: only active and resetting records are visited, never the world. */
    public static void tick(MinecraftServer server) {
        long now = server.overworld().getGameTime();
        GuardianData data = GuardianData.get(server.overworld());
        for (GuardianEncounter encounter : data.all()) {
            ServerLevel level = server.getLevel(encounter.dimension());
            if (level == null) continue;
            switch (encounter.state()) {
                case ACTIVE -> tickActive(server, level, data, encounter, now);
                case RESETTING -> {
                    if (GuardianPolicy.retryReady(now, encounter.resetAt())) {
                        data.put(encounter.withState(GuardianState.READY).withResetAt(0L).withBoss(null));
                    }
                }
                default -> {
                }
            }
        }
    }

    private static void tickActive(MinecraftServer server, ServerLevel level, GuardianData data,
            GuardianEncounter encounter, long now) {
        Entity entity = encounter.boss().map(level::getEntity).orElse(null);
        if (!(entity instanceof GuardianGiganotosaurusEntity boss) || !boss.isAlive()) {
            // The boss chunk is loaded but the Guardian is gone: end the attempt without rewards.
            if (level.isLoaded(encounter.anchor())) reset(level, data, encounter, false);
            return;
        }
        Set<UUID> participants = nearbyParticipants(server, level, encounter.tribe(), encounter.anchor());
        participants.addAll(encounter.participants());
        GuardianEncounter updated = encounter.withParticipants(participants);
        boolean present = false;
        double joinRadius = Config.GUARDIAN_JOIN_RADIUS.get();
        for (UUID id : participants) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player == null || !player.isAlive() || player.isSpectator() || player.level() != level) continue;
            if (player.blockPosition().distSqr(encounter.anchor()) <= joinRadius * joinRadius) {
                present = true;
                break;
            }
        }
        if (present) updated = updated.withEmptySince(0L);
        else if (updated.emptySince() == 0L) updated = updated.withEmptySince(now);
        if (GuardianPolicy.resetDue(now, updated.emptySince(), Config.GUARDIAN_GRACE_TICKS.get())) {
            reset(level, data, updated, true);
            return;
        }
        GuardianBar.update(encounter.key(), boss);
        GuardianBar.sync(encounter.key(), barViewers(server, level, updated, boss));
        if (!updated.equals(encounter)) data.put(updated);
    }

    private static List<ServerPlayer> barViewers(MinecraftServer server, ServerLevel level,
            GuardianEncounter encounter, GuardianGiganotosaurusEntity boss) {
        double range = Config.GUARDIAN_BAR_RANGE.get();
        List<ServerPlayer> viewers = new ArrayList<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.level() != level) continue;
            boolean participant = encounter.participants().contains(player.getUUID());
            if (participant || player.distanceToSqr(boss) <= range * range) viewers.add(player);
        }
        return viewers;
    }

    private static void reset(ServerLevel level, GuardianData data, GuardianEncounter encounter, boolean announce) {
        encounter.boss().map(level::getEntity).ifPresent(Entity::discard);
        GuardianBar.hide(encounter.key());
        data.put(encounter.withState(GuardianState.RESETTING).withBoss(null)
                .withResetAt(level.getGameTime() + Config.GUARDIAN_RESET_DELAY_TICKS.get()).withEmptySince(0L));
        if (announce) {
            announce(level, encounter, Component.translatable("guardian.arksurvivalreturns.retreated")
                    .withStyle(ChatFormatting.GRAY));
        }
    }

    // ------------------------------------------------------------------------------------- death

    public static void onBossDeath(GuardianGiganotosaurusEntity boss) {
        if (!(boss.level() instanceof ServerLevel level)) return;
        GuardianData data = GuardianData.get(level);
        Optional<GuardianEncounter> found = data.find(boss.guardianKey());
        if (found.isEmpty() || !found.get().owns(boss.getUUID())) return;
        GuardianEncounter encounter = found.get();
        boolean firstVictory = !encounter.rewardsIssued();
        // Persist the defeat before any reward is issued, so a crash cannot double the payout.
        encounter = encounter.withState(GuardianState.DEFEATED).withBoss(null).withRewardsIssued(true)
                .withEmptySince(0L).withResetAt(0L);
        data.put(encounter);
        GuardianBar.hide(encounter.key());
        drop(level, boss.position(), new ItemStack(ModContent.GUARDIAN_TROPHY.get()));
        if (firstVictory) {
            TribeProgressData.get(level).grant(encounter.tribe(), TribeProgressData.WORKSHOP_SCHEMATIC);
            drop(level, boss.position(), new ItemStack(ModContent.WORKSHOP_SCHEMATIC.get()));
        }
        announce(level, encounter, Component.translatable("guardian.arksurvivalreturns.victory")
                .withStyle(ChatFormatting.GOLD));
        for (UUID id : encounter.participants()) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(id);
            if (player == null) continue;
            if (firstVictory) TamingService.discovery(player, ADVANCEMENT);
        }
    }

    private static void drop(ServerLevel level, Vec3 pos, ItemStack stack) {
        var item = new ItemEntity(level, pos.x, pos.y + 0.5, pos.z, stack);
        item.setDefaultPickUpDelay();
        level.addFreshEntity(item);
    }

    // ------------------------------------------------------------------------------------ tribes

    private static Set<UUID> nearbyParticipants(MinecraftServer server, ServerLevel level, UUID tribe, BlockPos anchor) {
        Set<UUID> participants = new LinkedHashSet<>();
        double radius = Config.GUARDIAN_JOIN_RADIUS.get();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.level() != level || !player.isAlive()) continue;
            if (!inTribe(player.getUUID(), tribe)) continue;
            if (player.blockPosition().distSqr(anchor) <= radius * radius) participants.add(player.getUUID());
        }
        return participants;
    }

    private static Set<UUID> registerTames(ServerLevel level, UUID tribe, BlockPos anchor) {
        Set<UUID> tames = new LinkedHashSet<>();
        int cap = Config.GUARDIAN_MAX_TAME_CONTRIBUTION.get();
        if (cap <= 0) return tames;
        double radius = Config.GUARDIAN_TAME_RADIUS.get();
        AABB box = new AABB(anchor).inflate(radius);
        for (CreatureEntity creature : level.getEntitiesOfClass(CreatureEntity.class, box,
                candidate -> candidate.isAlive() && candidate.isTamed())) {
            if (tames.size() >= cap) break;
            UUID owner = creature.taming().owner();
            if (owner == null || !inTribe(owner, tribe)) continue;
            tames.add(creature.getUUID());
        }
        return tames;
    }

    // ---------------------------------------------------------------------------------- commands

    public static List<Component> status(ServerPlayer player) {
        List<Component> lines = new ArrayList<>();
        GuardianData data = GuardianData.get(player.level());
        for (GuardianEncounter encounter : data.all()) {
            if (!inTribe(player.getUUID(), encounter.tribe())) continue;
            BlockPos anchor = encounter.anchor();
            Component line = Component.translatable("guardian.arksurvivalreturns.status_line",
                    encounter.state().getSerializedName(), anchor.getX(), anchor.getY(), anchor.getZ());
            Entity bossEntity = encounter.boss().map(id -> player.level().getEntity(id)).orElse(null);
            if (bossEntity instanceof GuardianGiganotosaurusEntity boss) {
                line = line.copy().append(Component.literal(" "))
                        .append(Component.translatable("guardian.arksurvivalreturns.status_health",
                                (int) Math.ceil(boss.getHealth()), (int) Math.ceil(boss.getMaxHealth())));
            }
            lines.add(line);
        }
        if (lines.isEmpty()) lines.add(Component.translatable("guardian.arksurvivalreturns.status_none"));
        return lines;
    }

    /** Operator recovery: force the nearest tribe encounter back into a free retry window. */
    public static boolean forceReset(ServerPlayer player) {
        GuardianEncounter encounter = nearest(player);
        if (encounter == null) return false;
        ServerLevel level = player.level().getServer().getLevel(encounter.dimension());
        if (level == null) return false;
        encounter.boss().map(level::getEntity).ifPresent(Entity::discard);
        GuardianBar.hide(encounter.key());
        GuardianData.get(level).put(encounter.withState(GuardianState.READY).withBoss(null)
                .withResetAt(0L).withEmptySince(0L));
        return true;
    }

    /** Operator cleanup: remove the nearest tribe encounter record and its boss entirely. */
    public static boolean clear(ServerPlayer player) {
        GuardianEncounter encounter = nearest(player);
        if (encounter == null) return false;
        ServerLevel level = player.level().getServer().getLevel(encounter.dimension());
        if (level == null) return false;
        encounter.boss().map(level::getEntity).ifPresent(Entity::discard);
        GuardianBar.hide(encounter.key());
        GuardianData.get(level).remove(encounter.key());
        return true;
    }

    /** Operator recovery: re-grant the shared schematic flag and the physical item. */
    public static boolean grantSchematic(ServerPlayer target) {
        ServerLevel level = target.level();
        UUID tribe = TribeService.team(target).map(team -> team.getId()).orElse(target.getUUID());
        TribeProgressData.get(level).grant(tribe, TribeProgressData.WORKSHOP_SCHEMATIC);
        drop(level, target.position(), new ItemStack(ModContent.WORKSHOP_SCHEMATIC.get()));
        return true;
    }

    private static @Nullable GuardianEncounter nearest(ServerPlayer player) {
        GuardianData data = GuardianData.get(player.level());
        GuardianEncounter best = null;
        double bestDistance = Double.MAX_VALUE;
        for (GuardianEncounter encounter : data.all()) {
            if (!inTribe(player.getUUID(), encounter.tribe())) continue;
            double distance = encounter.anchor().distSqr(player.blockPosition());
            if (distance < bestDistance) {
                bestDistance = distance;
                best = encounter;
            }
        }
        return best;
    }

    private static void announce(ServerLevel level, GuardianEncounter encounter, Component text) {
        if (!Config.GUARDIAN_ANNOUNCE.get()) return;
        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            if (inTribe(player.getUUID(), encounter.tribe())) player.sendSystemMessage(text, false);
        }
    }

    private GuardianService() {}
}
