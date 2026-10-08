package dev.nez.arksurvivalreturns.feature.spawn;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import dev.nez.arksurvivalreturns.feature.creature.CreatureEntity;
import dev.nez.arksurvivalreturns.feature.recorder.SessionRecorder;
import dev.nez.arksurvivalreturns.feature.taming.TamingService;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import org.jspecify.annotations.Nullable;

/**
 * Who lives: every wild animal of the overworld from the moment it appears to its end, whether its chunk is
 * loaded or not. An animal is entered when it first joins the world; its place is kept up while it is loaded
 * and when its chunk unloads; it leaves on its death (the day, the place and what killed it), on being tamed,
 * or on being taken out of the world. The last ends are kept, so a life can be traced after it is over.
 *
 * <p>An animal is marked as shown once the server has sent it to a client: from then on a player may know it,
 * and the population budget ({@link NaturalPopulations}) no longer deletes it. It lives until it dies.
 *
 * <p>Beyond the loaded land an animal is its record and nothing else, and the record lives on ({@link SilentLife}):
 * it may shift, feed, be born or die there. A record the rules have changed is marked silent until its body is back
 * in the world and brought to it; one born there has no body until its chunk loads; and of one that died there the
 * saved body is not let back in.
 */
@EventBusSubscriber(modid = ArkSurvivalReturns.MOD_ID)
public final class WildlifeRegister extends SavedData {
    /** Ends kept for looking back; older ones are dropped. */
    public static final int ENDS_KEPT = 4096;
    public static final String TAMED = "tamed", REMOVED = "removed";

    /**
     * A living wild animal: where and when it was last known, in block columns and game days, and how hungry (0 fed,
     * 1 starving). It has a body once an entity of it has been in the world; it is silent while the rules beyond the
     * loaded land have changed the record and the body has not been brought to it; it is claimed while somebody is
     * taming, riding or leading it, and then those rules leave it alone.
     */
    public record Life(UUID id, String species, UUID pack, int level, int x, int y, int z, double appeared, double seen, boolean shown,
                       double hunger, boolean body, boolean silent, boolean claimed) {
        static final Codec<Life> CODEC = RecordCodecBuilder.create(i -> i.group(
                UUIDUtil.CODEC.fieldOf("id").forGetter(Life::id),
                Codec.STRING.fieldOf("species").forGetter(Life::species),
                UUIDUtil.CODEC.fieldOf("pack").forGetter(Life::pack),
                Codec.INT.fieldOf("level").forGetter(Life::level),
                Codec.INT.fieldOf("x").forGetter(Life::x),
                Codec.INT.fieldOf("y").forGetter(Life::y),
                Codec.INT.fieldOf("z").forGetter(Life::z),
                Codec.DOUBLE.fieldOf("appeared").forGetter(Life::appeared),
                Codec.DOUBLE.fieldOf("seen").forGetter(Life::seen),
                Codec.BOOL.fieldOf("shown").forGetter(Life::shown),
                Codec.DOUBLE.optionalFieldOf("hunger", 0.55).forGetter(Life::hunger),
                Codec.BOOL.optionalFieldOf("body", true).forGetter(Life::body),
                Codec.BOOL.optionalFieldOf("silent", false).forGetter(Life::silent),
                Codec.BOOL.optionalFieldOf("claimed", false).forGetter(Life::claimed)
        ).apply(i, Life::new));

        /** The record after a round of the rules beyond the loaded land: its new column and hunger. */
        public Life after(int x, int z, double hunger) {
            return new Life(id, species, pack, level, x, y, z, appeared, seen, shown, hunger, body, true, claimed);
        }
    }

    /**
     * How a wild life ended: a death (the cause is "player", "tame:" and the tame's species, the species of the
     * wild animal that killed it, another attacker's type, or the kind of damage), {@link #TAMED}, or
     * {@link #REMOVED} for an animal taken out of the world without dying. A silent end came to the record beyond
     * the loaded land ({@link SilentLife}): of age, of hunger, or to the species named.
     */
    public record End(UUID id, String species, int level, int x, int y, int z, double appeared, double day, String cause, boolean shown,
                      boolean silent) {
        static final Codec<End> CODEC = RecordCodecBuilder.create(i -> i.group(
                UUIDUtil.CODEC.fieldOf("id").forGetter(End::id),
                Codec.STRING.fieldOf("species").forGetter(End::species),
                Codec.INT.fieldOf("level").forGetter(End::level),
                Codec.INT.fieldOf("x").forGetter(End::x),
                Codec.INT.fieldOf("y").forGetter(End::y),
                Codec.INT.fieldOf("z").forGetter(End::z),
                Codec.DOUBLE.fieldOf("appeared").forGetter(End::appeared),
                Codec.DOUBLE.fieldOf("day").forGetter(End::day),
                Codec.STRING.fieldOf("cause").forGetter(End::cause),
                Codec.BOOL.fieldOf("shown").forGetter(End::shown),
                Codec.BOOL.optionalFieldOf("silent", false).forGetter(End::silent)
        ).apply(i, End::new));
    }

    public static final Codec<WildlifeRegister> CODEC = RecordCodecBuilder.create(i -> i.group(
            Life.CODEC.listOf().fieldOf("living").forGetter(register -> List.copyOf(register.living.values())),
            End.CODEC.listOf().fieldOf("ended").forGetter(register -> List.copyOf(register.ended)),
            UUIDUtil.CODEC.listOf().optionalFieldOf("gone", List.of()).forGetter(register -> List.copyOf(register.gone))
    ).apply(i, WildlifeRegister::new));
    public static final SavedDataType<WildlifeRegister> TYPE = new SavedDataType<>(
            ArkSurvivalReturns.id("wildlife_register"), () -> new WildlifeRegister(), CODEC);

    private final Map<UUID, Life> living = new HashMap<>();
    private final ArrayDeque<End> ended = new ArrayDeque<>();
    /** Animals that died as records while their body lay saved in an unloaded chunk: the body does not come back. */
    private final Set<UUID> gone = new HashSet<>();
    /** Bodies back in the world whose record lived on meanwhile; {@link SilentLife} brings them to it on the next tick. */
    private final Set<CreatureEntity> returning = new LinkedHashSet<>();

    public WildlifeRegister() {}

    private WildlifeRegister(List<Life> living, List<End> ended, List<UUID> gone) {
        for (Life life : living) this.living.put(life.id(), life);
        this.ended.addAll(ended);
        this.gone.addAll(gone);
    }

    /** The overworld's register; Ark wildlife lives there only. */
    public static WildlifeRegister get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    public @Nullable Life life(UUID id) { return living.get(id); }

    public Collection<Life> living() { return living.values(); }

    /** The ends on record, oldest first. */
    public Collection<End> ended() { return ended; }

    /** The last end on record for this animal, or null. */
    public @Nullable End end(UUID id) {
        End found = null;
        for (End end : ended) if (end.id().equals(id)) found = end;
        return found;
    }

    /** Whether the saved body of this animal is not to come back: it died as a record. */
    public boolean gone(UUID id) { return gone.contains(id); }

    /** The bodies waiting to be brought to their records. */
    public Set<CreatureEntity> returning() { return returning; }

    /** Enters the animal, or brings its place, level, hunger and day up to date: the record is the body's again. */
    public void keep(ServerLevel level, CreatureEntity creature) {
        double today = today(level);
        Life before = living.get(creature.getUUID());
        Life now = new Life(creature.getUUID(), creature.species().id, creature.packId(), creature.creatureLevel(),
                creature.getBlockX(), creature.getBlockY(), creature.getBlockZ(), before == null ? today : before.appeared(), today,
                creature.shown() || before != null && before.shown(), creature.wildlife().mind().hunger(), true, false,
                NaturalPopulations.inUse(creature));
        // The day and the hunger move on with every look; only a change worth saving marks the file.
        if (before == null || before.x() != now.x() || before.y() != now.y() || before.z() != now.z() || before.shown() != now.shown()
                || before.level() != now.level() || !before.pack().equals(now.pack()) || !before.body() || before.silent()
                || before.claimed() != now.claimed()) setDirty();
        living.put(now.id(), now);
    }

    /**
     * Brings every loaded animal up to date; the population budget calls it on each of its passes. A body whose
     * record lived on without it waits for {@link SilentLife}, and one whose record died is taken out: both when a
     * chunk came back into reach without its animals joining the world anew.
     */
    public void refresh(ServerLevel level, Collection<CreatureEntity> loaded) {
        for (CreatureEntity creature : loaded) {
            if (!wild(level, creature) || creature.isRemoved()) continue;
            Life life = living.get(creature.getUUID());
            if (life == null && gone.remove(creature.getUUID())) {
                SessionRecorder.refused(creature);
                creature.discard();
                setDirty();
            } else meet(level, creature);
        }
    }

    /** Brings the record up to date from the body, unless the record lived on without it: then the body waits to be brought to the record. */
    private void meet(ServerLevel level, CreatureEntity creature) {
        Life life = living.get(creature.getUUID());
        if (life != null && life.silent()) returning.add(creature);
        else keep(level, creature);
    }

    /** Puts a record in the place of the one it had, or enters one born beyond the loaded land. */
    public void put(Life life) {
        living.put(life.id(), life);
        setDirty();
    }

    /** The animal's wild life is over: it died of the cause, was tamed or was taken out of the world. */
    public void end(ServerLevel level, CreatureEntity creature, String cause) {
        Life life = living.remove(creature.getUUID());
        double today = today(level);
        End end = new End(creature.getUUID(), creature.species().id, creature.creatureLevel(), creature.getBlockX(), creature.getBlockY(),
                creature.getBlockZ(), life == null ? today : life.appeared(), today, cause, creature.shown() || life != null && life.shown(), false);
        ended.addLast(end);
        while (ended.size() > ENDS_KEPT) ended.removeFirst();
        setDirty();
        SessionRecorder.lifeEnded(end);
    }

    /** A life ends as a record, beyond the loaded land; a body it left in a saved chunk is not let back in. */
    public void end(ServerLevel level, Life life, String cause) {
        if (living.remove(life.id()) == null) return;
        if (life.body()) gone.add(life.id());
        End end = new End(life.id(), life.species(), life.level(), life.x(), life.y(), life.z(), life.appeared(), today(level), cause,
                life.shown(), true);
        ended.addLast(end);
        while (ended.size() > ENDS_KEPT) ended.removeFirst();
        setDirty();
        SessionRecorder.lifeEnded(end);
    }

    /** A wild animal became somebody's: called before it stops counting as wildlife. */
    public static void tamed(CreatureEntity creature) {
        if (creature.level() instanceof ServerLevel level && wild(level, creature)) get(level).end(level, creature, TAMED);
    }

    /** What ended a life, in a word a log line and a later tracker can both use. */
    public static String cause(DamageSource source) {
        Entity by = source.getEntity();
        if (by instanceof Player) return "player";
        if (by instanceof CreatureEntity other) return (TamingService.of(other).tamed() ? "tame:" : "") + other.species().id;
        if (by != null) return BuiltInRegistries.ENTITY_TYPE.getKey(by.getType()).toString();
        return source.getMsgId();
    }

    private static boolean wild(ServerLevel level, CreatureEntity creature) {
        return level.dimension() == Level.OVERWORLD && creature.isNaturalWildlife();
    }

    private static double today(ServerLevel level) {
        return level.getServer().overworld().getGameTime() / 24000.0;
    }

    /**
     * Only the register is touched here: the chunk of a body read from the save may not be whole yet. The body of an
     * animal that died as a record stays out; one whose record lived on is brought to it on the next tick.
     */
    @SubscribeEvent public static void joined(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getEntity() instanceof CreatureEntity creature) || !wild(level, creature)) return;
        WildlifeRegister register = get(level);
        Life life = register.living.get(creature.getUUID());
        if (event.loadedFromDisk() && life == null && register.gone.remove(creature.getUUID())) {
            SessionRecorder.refused(creature);
            event.setCanceled(true);
            register.setDirty();
        } else if (event.loadedFromDisk() && life != null && life.silent()) register.returning.add(creature);
        else register.keep(level, creature);
    }

    /**
     * Unloading keeps the last place, unless the record has lived on without the body; an animal taken out of the
     * world without a death on record, or gone to another dimension, is entered as removed.
     */
    @SubscribeEvent public static void left(EntityLeaveLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getEntity() instanceof CreatureEntity creature) || !wild(level, creature)) return;
        WildlifeRegister register = get(level);
        register.returning.remove(creature);
        Life life = register.living.get(creature.getUUID());
        if (life == null) return;
        var reason = creature.getRemovalReason();
        if (reason == Entity.RemovalReason.DISCARDED || reason == Entity.RemovalReason.KILLED || reason == Entity.RemovalReason.CHANGED_DIMENSION)
            register.end(level, creature, REMOVED);
        else if (!life.silent()) register.keep(level, creature);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST) public static void died(LivingDeathEvent event) {
        if (event.getEntity() instanceof CreatureEntity creature && creature.level() instanceof ServerLevel level && wild(level, creature))
            get(level).end(level, creature, cause(event.getSource()));
    }

    /** Sent to a client: from now on a player may know this animal. */
    @SubscribeEvent public static void shown(PlayerEvent.StartTracking event) {
        if (!(event.getTarget() instanceof CreatureEntity creature) || creature.shown() || !(creature.level() instanceof ServerLevel level)) return;
        creature.markShown();
        if (wild(level, creature)) get(level).meet(level, creature);
    }

    /** The lines of /arkwildlife register: who lives, how many of them beyond the loaded land, how many a player may know, and the latest ends. */
    public List<String> report(ServerLevel level, int latest) {
        long known = living.values().stream().filter(Life::shown).count();
        long away = living.values().stream().filter(life -> level.getEntity(life.id()) == null).count();
        long unborn = living.values().stream().filter(life -> !life.body()).count();
        List<String> lines = new ArrayList<>();
        lines.add("register: living=" + living.size() + " beyond_the_loaded_land=" + away + " without_a_body=" + unborn + " shown_to_a_player="
                + known + " ends_on_record=" + ended.size());
        int skip = Math.max(0, ended.size() - latest), index = 0;
        for (End end : ended) {
            if (index++ < skip) continue;
            lines.add(String.format(java.util.Locale.ROOT, "  day %.2f %s level %d at %d %d %d: %s (lived %.2f days%s%s)", end.day(), end.species(), end.level(),
                    end.x(), end.y(), end.z(), end.cause(), end.day() - end.appeared(), end.shown() ? "" : ", never shown",
                    end.silent() ? ", beyond the loaded land" : ""));
        }
        return lines;
    }
}
