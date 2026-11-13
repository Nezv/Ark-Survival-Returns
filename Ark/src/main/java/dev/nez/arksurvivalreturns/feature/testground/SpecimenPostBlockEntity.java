package dev.nez.arksurvivalreturns.feature.testground;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import dev.nez.arksurvivalreturns.feature.creature.CreatureEntity;
import dev.nez.arksurvivalreturns.feature.creature.CreatureVariants;
import dev.nez.arksurvivalreturns.feature.creature.LevelScaling;
import dev.nez.arksurvivalreturns.feature.creature.Species;
import dev.nez.arksurvivalreturns.feature.taming.TamingService;
import dev.nez.arksurvivalreturns.feature.taming.TorporService;
import dev.nez.arksurvivalreturns.feature.taming.TorporState;
import dev.nez.arksurvivalreturns.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A Specimen Post: the creature it is set to (species, colour, level, hunger, how it may move) and the one it put out.
 *
 * <p>It spawns that creature on a rising redstone signal, on {@code /arktest post}, or from its screen, standing at the
 * post's anchor (on top of the post, or where the testing ground set it, e.g. inside the arena). It then watches it and
 * times what happens: from the first hit to death, how long the death lasts until the body is gone, from going down
 * (or the first meal of a passive tame) to tamed, with hits, damage and meals. Each result is told to the players
 * near the post and kept on it for its screen.
 *
 * <p>FROZEN takes the creature's mind away (no AI: it stands in its idle pose and only takes hits); ROOTED leaves it
 * awake but pins it to its anchor, for taming and attack tests; FREE lets it go, for fights in the arena.
 */
public final class SpecimenPostBlockEntity extends BlockEntity {
    public enum Mode {
        FROZEN("Frozen"), ROOTED("Rooted"), FREE("Free");
        public final String label;
        Mode(String label) { this.label = label; }
        public Mode next() { return values()[(ordinal() + 1) % values().length]; }
    }

    /** What a command or the screen asks of the post; APPLY changes the settings only. */
    public enum Action { APPLY, SPAWN, CLEAR, KILL, KNOCK_OUT, WAKE, TAME, FIGHT }

    /** The creature tag every specimen carries, so the testing ground can find and clear them. */
    public static final String TAG = "ark_specimen";
    /** Where on a specimen its post is kept (block position, packed). */
    public static final String POST_KEY = "ArkSpecimenPost";
    private static final int REPORTS = 4, ABSENT_LIMIT = 100, RESPAWN_DELAY = 60;

    private String species = Species.TYRANNOSAURUS.id;
    private int variant, creatureLevel = 1, hunger = 50;
    private Mode mode = Mode.FROZEN;
    private boolean respawn;
    private float yaw;
    private double offsetX, offsetY = 1, offsetZ;

    private @Nullable UUID specimen;
    private int absentTicks;
    private long respawnAt = -1;
    private boolean powered;
    /** Stopwatch, in game ticks; -1 when it has not happened yet. */
    private long spawnedAt = -1, firstHitAt = -1, downedAt = -1, firstMealAt = -1, tamedAt = -1, diedAt = -1;
    private int hits, meals;
    private float damage, lastHealth = -1;
    private String status = "No specimen";
    private final List<String> reports = new ArrayList<>();

    public SpecimenPostBlockEntity(BlockPos pos, BlockState state) {
        super(TestGroundContent.SPECIMEN_POST_BLOCK_ENTITY.get(), pos, state);
    }

    // ------------------------------------------------------------------ settings

    public Species species() {
        for (Species s : Species.values()) if (s.id.equals(species)) return s;
        return Species.TYRANNOSAURUS;
    }
    public int variant() { return Math.floorMod(variant, Math.max(1, CreatureVariants.of(species()).size())); }
    public String variantName() {
        var names = CreatureVariants.of(species());
        return names.isEmpty() ? "-" : names.get(variant());
    }
    public int creatureLevel() { return creatureLevel; }
    public int hunger() { return hunger; }
    public Mode mode() { return mode; }
    public boolean respawn() { return respawn; }
    public String status() { return status; }
    public List<String> reports() { return List.copyOf(reports); }
    public boolean hasSpecimen() { return specimen != null; }
    /** Stopwatch marks for the screen's running timers (game ticks, -1 when not yet). */
    public long spawnedAt() { return spawnedAt; }
    public long downedAt() { return tamedAt < 0 ? downedAt : -1; }
    public long diedAt() { return diedAt; }

    /** Sets everything the screen and the builder choose; a creature already out takes the change at once. */
    public void configure(Species species, int variant, int level, Mode mode, int hunger, boolean respawn) {
        configure(species, variant, level, mode, hunger, respawn, true);
    }

    /** As above; {@code live} false leaves a creature already out as it is (a Spawn replaces it anyway). */
    public void configure(Species species, int variant, int level, Mode mode, int hunger, boolean respawn, boolean live) {
        boolean body = !species.id.equals(this.species) || variant != this.variant || LevelScaling.clamp(level) != creatureLevel;
        this.species = species.id;
        this.variant = Math.floorMod(variant, Math.max(1, CreatureVariants.of(species).size()));
        this.creatureLevel = LevelScaling.clamp(level);
        this.mode = mode;
        this.hunger = Math.clamp(hunger, 0, 100);
        this.respawn = respawn;
        var creature = creature();
        if (creature != null && live) {
            if (body) spawn();
            else { applyMode(creature); applyHunger(creature); }
        }
        changed();
    }

    /** Where the specimen stands, from the post block's bottom centre, and which way it faces. */
    public void place(double x, double y, double z, float yaw) {
        offsetX = x; offsetY = y; offsetZ = z; this.yaw = yaw;
        changed();
    }

    public void setYaw(float yaw) { this.yaw = yaw; changed(); }

    /** The specimen comes out on the post's first tick: when a player is near enough for the land to tick. */
    public void arm() { respawnAt = 0; setChanged(); }

    private Vec3 anchor() { return Vec3.atBottomCenterOf(worldPosition).add(offsetX, offsetY, offsetZ); }

    // ------------------------------------------------------------------ actions

    /** Runs an action; the player is the new owner of a tame and is told why an action did nothing. */
    public void act(Action action, @Nullable Player player) {
        if (!(level instanceof ServerLevel world)) return;
        var creature = creature();
        switch (action) {
            case APPLY -> {}
            case SPAWN -> spawn();
            case CLEAR -> clear();
            case KILL -> { if (creature != null) creature.kill(world); else tell(player, "No specimen to kill"); }
            case KNOCK_OUT -> {
                if (creature == null) tell(player, "No specimen to knock out");
                // A dose four times its torpor, as a volley of darts would give: the real knock-out, delay and all.
                else if (!TorporService.sedate(creature, TorporService.maximumFor(creature) * 4, player, "testing ground"))
                    tell(player, "This specimen cannot be knocked out (taming off, or not a sedation target)");
            }
            case WAKE -> { if (creature != null) TorporService.clear(creature); }
            case TAME -> {
                if (creature == null || player == null) tell(player, "No specimen to tame");
                else TamingService.tameNow(creature, player);
            }
            case FIGHT -> fight(world, creature, player);
        }
        changed();
    }

    /** A rising redstone edge puts out a fresh specimen. */
    public void redstone(boolean signal) {
        if (signal && !powered && level instanceof ServerLevel) spawn();
        if (signal != powered) { powered = signal; setChanged(); }
    }

    /** Replaces the specimen with a new one of the set species, colour and level, and restarts the stopwatch. */
    public void spawn() {
        if (!(level instanceof ServerLevel world)) return;
        discard();
        Species kind = species();
        var creature = ModContent.CREATURES.get(kind).get().create(world, EntitySpawnReason.COMMAND);
        if (creature == null) { report("Could not create a " + kind.displayName); return; }
        // The colour is a fixed pick from the UUID, so the post picks a UUID that lands on the chosen one.
        int wanted = variant();
        UUID id = UUID.randomUUID();
        for (int i = 0; i < 512 && CreatureVariants.pick(kind, id) != wanted; i++) id = UUID.randomUUID();
        creature.setUUID(id);
        creature.initializeLevel(creatureLevel);
        Vec3 at = anchor();
        creature.snapTo(at.x, at.y, at.z, yaw, 0);
        creature.yBodyRot = yaw; creature.yHeadRot = yaw;
        creature.setPersistenceRequired();
        creature.addTag(TAG);
        creature.getPersistentData().putLong(POST_KEY, worldPosition.asLong());
        applyMode(creature);
        var taming = TamingService.of(creature);
        taming.initialize(hunger, taming.ticks());
        applyHunger(creature);
        if (!world.addFreshEntity(creature)) { report("The world refused the " + kind.displayName); return; }
        specimen = creature.getUUID();
        long now = world.getGameTime();
        spawnedAt = now; firstHitAt = downedAt = firstMealAt = tamedAt = diedAt = -1; respawnAt = -1;
        hits = meals = 0; damage = 0; lastHealth = creature.getHealth(); absentTicks = 0;
        changed();
    }

    /** Takes the specimen away without a death. */
    public void clear() {
        discard();
        respawnAt = -1;
        changed();
    }

    private void discard() {
        var creature = creature();
        if (creature != null) creature.discard();
        specimen = null;
    }

    private void fight(ServerLevel world, @Nullable CreatureEntity creature, @Nullable Player player) {
        if (creature == null || !creature.isAlive()) { tell(player, "No specimen to fight with"); return; }
        CreatureEntity rival = null;
        double best = 48 * 48;
        for (var other : world.getEntitiesOfClass(CreatureEntity.class, creature.getBoundingBox().inflate(48),
                c -> c != creature && c.isAlive() && c.entityTags().contains(TAG))) {
            double d = other.distanceToSqr(creature);
            if (d < best) { best = d; rival = other; }
        }
        if (rival == null) { tell(player, "No other specimen within 48 blocks"); return; }
        // Both are let go, the rival's post too, so it does not freeze or pin it again.
        mode = Mode.FREE;
        applyMode(creature);
        if (world.getBlockEntity(BlockPos.of(rival.getPersistentData().getLongOr(POST_KEY, 0))) instanceof SpecimenPostBlockEntity post) {
            post.mode = Mode.FREE; post.applyMode(rival); post.changed();
        } else rival.setNoAi(false);
        // Each is struck by the other as far as its mind knows: it answers, or a timid one runs until cornered.
        creature.setLastHurtByMob(rival);
        rival.setLastHurtByMob(creature);
        creature.setTarget(rival);
        rival.setTarget(creature);
        report(creature.species().displayName + " set on " + rival.species().displayName);
    }

    private void applyMode(CreatureEntity creature) {
        creature.setNoAi(mode == Mode.FROZEN);
        if (mode != Mode.FREE) creature.getNavigation().stop();
    }

    private void applyHunger(CreatureEntity creature) {
        var mind = creature.wildlife().mind();
        mind.restoreNeeds(hunger / 100.0, mind.thirst(), mind.fatigue());
        TamingService.of(creature).setHunger(hunger);
    }

    // ------------------------------------------------------------------ watching

    public static void tick(Level level, BlockPos pos, BlockState state, SpecimenPostBlockEntity post) {
        if (level instanceof ServerLevel world) post.watch(world);
    }

    private void watch(ServerLevel world) {
        long now = world.getGameTime();
        if (specimen == null) {
            if (respawnAt >= 0 && now >= respawnAt) spawn();
            else if (now % 20 == 0) status(respawnAt >= 0 ? "Respawning" : "No specimen");
            return;
        }
        var creature = creature();
        if (creature == null || creature.isRemoved()) {
            if (diedAt >= 0) {
                report(name() + " was gone " + seconds(now - diedAt) + " after it died (death animation)");
                specimen = null;
                if (respawn) respawnAt = now + RESPAWN_DELAY;
                changed();
            } else if (++absentTicks > ABSENT_LIMIT) {
                specimen = null;
                status("Specimen lost (unloaded or removed)");
            }
            return;
        }
        absentTicks = 0;
        if (creature.isAlive()) {
            if (mode == Mode.FROZEN && !creature.isNoAi()) creature.setNoAi(true);
            if (mode == Mode.ROOTED) pin(creature);
        }
        float health = creature.getHealth();
        if (lastHealth >= 0 && health < lastHealth - 1.0E-3f) {
            if (firstHitAt < 0) firstHitAt = now;
            hits++;
            damage += lastHealth - health;
        }
        lastHealth = health;
        var taming = TamingService.of(creature);
        if (taming.meals() > meals) {
            if (firstMealAt < 0) firstMealAt = now;
            meals = taming.meals();
        }
        // Down from the first tick of the collapse: from then on it can no longer act.
        var phase = creature.torpor().phase();
        boolean torpid = creature.isAlive() && (phase == TorporState.Phase.COLLAPSING || phase == TorporState.Phase.TORPID);
        if (torpid && downedAt < 0) {
            downedAt = now;
            report(name() + " went down" + (firstHitAt >= 0 ? " " + seconds(now - firstHitAt) + " after the first hit" : "")
                    + " (" + hits + " hits)");
        } else if (!torpid && downedAt >= 0 && tamedAt < 0 && creature.isAlive()) {
            report(name() + " woke after " + seconds(now - downedAt) + ", " + meals + " meals, not tamed");
            downedAt = -1;
        }
        if (creature.isTamed() && tamedAt < 0) {
            tamedAt = now;
            long from = downedAt >= 0 ? downedAt : firstMealAt;
            report(name() + " tamed" + (from >= 0 ? " " + seconds(now - from) + (downedAt >= 0 ? " after it went down" : " after the first meal") : "")
                    + ", " + meals + " meals");
        }
        if (creature.isDeadOrDying() && diedAt < 0) {
            diedAt = now;
            report(name() + " died" + (firstHitAt >= 0 ? " " + seconds(now - firstHitAt) + " after the first hit" : "")
                    + " (" + hits + " hits, " + Math.round(damage) + " damage)");
        }
        if (now % 20 == 0) {
            // Only what changes with events: the running timers are the screen's own, so a frozen gallery sends nothing.
            String state = creature.isDeadOrDying() ? "dying" : creature.isTamed() ? "tamed"
                    : torpid ? "down, torpor " + Math.round(creature.torpor().ratio() * 10) * 10 + "%"
                    : mode == Mode.FROZEN ? "frozen" : creature.behavior().name().toLowerCase(Locale.ROOT);
            status(name() + " | " + Math.round(health) + "/" + Math.round(creature.getMaxHealth()) + " hp | " + state
                    + (mode == Mode.FROZEN ? "" : " | hunger " + Math.round(creature.wildlife().mind().hunger() * 10) * 10 + "%"));
        }
    }

    /** A rooted specimen keeps its mind and turns, but never leaves its anchor. */
    private void pin(CreatureEntity creature) {
        Vec3 at = anchor();
        if (creature.position().distanceToSqr(at) > 0.0625) {
            creature.setPos(at.x, at.y, at.z);
            creature.getNavigation().stop();
        }
        var motion = creature.getDeltaMovement();
        creature.setDeltaMovement(0, Math.min(0, motion.y), 0);
    }

    private @Nullable CreatureEntity creature() {
        if (specimen == null || !(level instanceof ServerLevel world)) return null;
        return world.getEntity(specimen) instanceof CreatureEntity creature ? creature : null;
    }

    private String name() {
        return species().displayName + " (" + variantName() + ", lvl " + creatureLevel + ")";
    }

    private static String seconds(long ticks) {
        double s = ticks / 20.0;
        return s >= 60 ? (int) (s / 60) + "m " + String.format(Locale.ROOT, "%.1f", s % 60) + "s" : String.format(Locale.ROOT, "%.1fs", s);
    }

    private void report(String line) {
        reports.add(0, line);
        while (reports.size() > REPORTS) reports.removeLast();
        if (level instanceof ServerLevel world) {
            var message = Component.literal("[Specimen] ").withStyle(net.minecraft.ChatFormatting.LIGHT_PURPLE)
                    .append(Component.literal(line).withStyle(net.minecraft.ChatFormatting.WHITE));
            for (var player : world.players())
                if (player.distanceToSqr(Vec3.atCenterOf(worldPosition)) < 64 * 64) player.sendSystemMessage(message);
        }
        changed();
    }

    private void status(String line) {
        if (line.equals(status)) return;
        status = line;
        changed();
    }

    private static void tell(@Nullable Player player, String line) {
        if (player instanceof ServerPlayer server) server.sendSystemMessage(Component.literal(line), true);
    }

    private void changed() {
        setChanged();
        if (level != null && !level.isClientSide()) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    /** The post is gone: so is its specimen. */
    @Override public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        discard();
    }

    // ------------------------------------------------------------------ saving and sync

    @Override protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        out.putString("Species", species);
        out.putInt("Variant", variant);
        out.putInt("Level", creatureLevel);
        out.putInt("Hunger", hunger);
        out.putString("Mode", mode.name());
        out.putBoolean("Respawn", respawn);
        out.putFloat("Yaw", yaw);
        out.putDouble("OffsetX", offsetX); out.putDouble("OffsetY", offsetY); out.putDouble("OffsetZ", offsetZ);
        out.putBoolean("Powered", powered);
        if (specimen != null) out.store("Specimen", UUIDUtil.CODEC, specimen);
        out.putLong("SpawnedAt", spawnedAt); out.putLong("FirstHitAt", firstHitAt); out.putLong("DownedAt", downedAt);
        out.putLong("FirstMealAt", firstMealAt); out.putLong("TamedAt", tamedAt); out.putLong("DiedAt", diedAt);
        out.putLong("RespawnAt", respawnAt);
        out.putInt("Hits", hits); out.putInt("Meals", meals); out.putFloat("Damage", damage);
        out.putString("Status", status);
        out.putString("Reports", String.join("\n", reports));
    }

    @Override protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        species = in.getStringOr("Species", Species.TYRANNOSAURUS.id);
        variant = in.getIntOr("Variant", 0);
        creatureLevel = LevelScaling.clamp(in.getIntOr("Level", 1));
        hunger = Math.clamp(in.getIntOr("Hunger", 50), 0, 100);
        try { mode = Mode.valueOf(in.getStringOr("Mode", Mode.FROZEN.name())); } catch (IllegalArgumentException e) { mode = Mode.FROZEN; }
        respawn = in.getBooleanOr("Respawn", false);
        yaw = in.getFloatOr("Yaw", 0);
        offsetX = in.getDoubleOr("OffsetX", 0); offsetY = in.getDoubleOr("OffsetY", 1); offsetZ = in.getDoubleOr("OffsetZ", 0);
        powered = in.getBooleanOr("Powered", false);
        specimen = in.read("Specimen", UUIDUtil.CODEC).orElse(null);
        spawnedAt = in.getLongOr("SpawnedAt", -1); firstHitAt = in.getLongOr("FirstHitAt", -1); downedAt = in.getLongOr("DownedAt", -1);
        firstMealAt = in.getLongOr("FirstMealAt", -1); tamedAt = in.getLongOr("TamedAt", -1); diedAt = in.getLongOr("DiedAt", -1);
        respawnAt = in.getLongOr("RespawnAt", -1);
        hits = in.getIntOr("Hits", 0); meals = in.getIntOr("Meals", 0); damage = in.getFloatOr("Damage", 0);
        status = in.getStringOr("Status", "No specimen");
        reports.clear();
        String text = in.getStringOr("Reports", "");
        if (!text.isEmpty()) reports.addAll(List.of(text.split("\n")));
    }

    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) { return saveCustomOnly(registries); }

    @Override public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }

    /** The yaw a specimen faces when it looks from {@code from} towards {@code to}. */
    public static float yawTowards(Vec3 from, Vec3 to) {
        return (float) (Mth.atan2(-(to.x - from.x), to.z - from.z) * Mth.RAD_TO_DEG);
    }
}
