package dev.nez.arksurvivalreturns.feature.tech;

import java.util.List;
import java.util.Optional;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * The completion rule of one technology node.
 *
 * <p>{@link #observe} records what an event contributes under the node's mark prefix, and
 * {@link #satisfied} answers whether the task is done. State is historical: picking a berry up,
 * banking it and dropping it later still counts, while the inventory check catches items that were
 * moved by a menu instead of an event. Unknown content uses {@link Future} so the tree can ship
 * nodes before their mechanics exist.
 */
public interface TechTrigger {
    /**
     * Recursive dispatch: {@link AllOf} nests other triggers, so the element codec is built from the
     * proxy instead of a static field that would be null while the interface initializes.
     */
    Codec<TechTrigger> CODEC = Codec.recursive("TechTrigger",
            self -> Type.CODEC.dispatch("type", TechTrigger::type, type -> type.codec(self)));
    String DRIED_DAY_TAG = "ark_dried_day";

    Type type();

    /** Returns true when this event changed the stored progress. */
    default boolean observe(TechEvent event, TechTribeProgress progress, String prefix) {
        return false;
    }

    /** True when the node's task is done for this tribe; the player supplies inventory context. */
    boolean satisfied(TechTribeProgress progress, String prefix, @Nullable Player player);

    enum Type implements StringRepresentable {
        COLLECT("collect"),
        CRAFT("craft"),
        CONSUME("consume"),
        PLACE_BLOCK("place_block"),
        EVENT("event"),
        TAME("tame"),
        ALL_OF("all_of"),
        FUTURE("future");

        public static final Codec<Type> CODEC = StringRepresentable.fromEnum(Type::values);
        private final String name;

        Type(String name) {
            this.name = name;
        }

        public MapCodec<? extends TechTrigger> codec(Codec<TechTrigger> self) {
            return switch (this) {
                case COLLECT -> Collect.CODEC;
                case CRAFT -> Craft.CODEC;
                case CONSUME -> Consume.CODEC;
                case PLACE_BLOCK -> PlaceBlock.CODEC;
                case EVENT -> Event.CODEC;
                case TAME -> Tame.CODEC;
                case ALL_OF -> AllOf.codec(self);
                case FUTURE -> Future.CODEC;
            };
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    /** True when the stack is that registered item; item identity never depends on components. */
    static boolean matches(ItemStack stack, Identifier item) {
        return !stack.isEmpty() && BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(item);
    }

    /** An item the tribe must collect. Distinct means every listed item, otherwise any of them counts. */
    record Collect(List<Identifier> items, int count, boolean distinct) implements TechTrigger {
        public static final MapCodec<Collect> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Identifier.CODEC.listOf().fieldOf("items").forGetter(Collect::items),
                Codec.INT.optionalFieldOf("count", 1).forGetter(Collect::count),
                Codec.BOOL.optionalFieldOf("distinct", false).forGetter(Collect::distinct)
        ).apply(instance, Collect::new));

        @Override
        public Type type() {
            return Type.COLLECT;
        }

        @Override
        public boolean observe(TechEvent event, TechTribeProgress progress, String prefix) {
            if (event.kind() != TechEventKind.OBTAIN || event.stack() == null) return false;
            boolean changed = false;
            for (Identifier item : items) {
                if (matches(event.stack(), item)) changed |= progress.addCount(prefix + item, event.stack().getCount());
            }
            return changed;
        }

        @Override
        public boolean satisfied(TechTribeProgress progress, String prefix, @Nullable Player player) {
            if (distinct) {
                for (Identifier item : items) {
                    if (effective(progress, prefix + item, player, item) < count) return false;
                }
                return true;
            }
            for (Identifier item : items) {
                if (effective(progress, prefix + item, player, item) >= count) return true;
            }
            return false;
        }

        private static int effective(TechTribeProgress progress, String key, @Nullable Player player, Identifier item) {
            return Math.max(progress.count(key), player == null ? 0 : held(player, item));
        }

        private static int held(Player player, Identifier item) {
            var inventory = player.getInventory();
            int held = 0;
            for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
                if (matches(inventory.getItem(slot), item)) held += inventory.getItem(slot).getCount();
            }
            return held;
        }
    }

    /** Craft a matching item at a crafting grid; possession alone never counts. */
    record Craft(List<Identifier> items, int count) implements TechTrigger {
        public static final MapCodec<Craft> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Identifier.CODEC.listOf().fieldOf("items").forGetter(Craft::items),
                Codec.INT.optionalFieldOf("count", 1).forGetter(Craft::count)
        ).apply(instance, Craft::new));

        @Override
        public Type type() {
            return Type.CRAFT;
        }

        @Override
        public boolean observe(TechEvent event, TechTribeProgress progress, String prefix) {
            if (event.kind() != TechEventKind.CRAFT || event.stack() == null) return false;
            boolean changed = false;
            for (Identifier item : items) {
                if (matches(event.stack(), item)) changed |= progress.addCount(prefix + "crafted:" + item, Math.max(1, event.stack().getCount()));
            }
            return changed;
        }

        @Override
        public boolean satisfied(TechTribeProgress progress, String prefix, @Nullable Player player) {
            for (Identifier item : items) {
                if (progress.count(prefix + "crafted:" + item) >= count) return true;
            }
            return false;
        }
    }

    /**
     * Eat a matching item; a positive day count additionally requires that in-game age, stamped at creation,
     * and a positive dryness requires at least that dried meat tier.
     */
    record Consume(List<Identifier> items, int days, int dryness) implements TechTrigger {
        public static final MapCodec<Consume> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Identifier.CODEC.listOf().fieldOf("items").forGetter(Consume::items),
                Codec.INT.optionalFieldOf("days", 0).forGetter(Consume::days),
                Codec.INT.optionalFieldOf("dryness", 0).forGetter(Consume::dryness)
        ).apply(instance, Consume::new));

        public Consume(List<Identifier> items, int days) {
            this(items, days, 0);
        }

        @Override
        public Type type() {
            return Type.CONSUME;
        }

        @Override
        public boolean observe(TechEvent event, TechTribeProgress progress, String prefix) {
            if (event.kind() != TechEventKind.CONSUME || event.stack() == null) return false;
            boolean changed = false;
            for (Identifier item : items) {
                if (!matches(event.stack(), item)) continue;
                if (days > 0 && !aged(event.stack(), event.day(), days)) continue;
                if (dryness > 0 && dev.nez.arksurvivalreturns.feature.primitive.DriedMeatItem.tier(event.stack()) < dryness) continue;
                changed |= progress.mark(prefix + item);
            }
            return changed;
        }

        @Override
        public boolean satisfied(TechTribeProgress progress, String prefix, @Nullable Player player) {
            for (Identifier item : items) {
                if (progress.marked(prefix + item)) return true;
            }
            return false;
        }

        private static boolean aged(ItemStack stack, long day, int days) {
            CustomData data = stack.get(DataComponents.CUSTOM_DATA);
            if (data == null) return false;
            long dried = data.copyTag().getLongOr(DRIED_DAY_TAG, Long.MIN_VALUE);
            return dried != Long.MIN_VALUE && day - dried >= days;
        }
    }

    /** Place a matching block; {@code lit} additionally requires the campfire lit state. */
    record PlaceBlock(List<Identifier> blocks, boolean lit) implements TechTrigger {
        public static final MapCodec<PlaceBlock> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Identifier.CODEC.listOf().fieldOf("blocks").forGetter(PlaceBlock::blocks),
                Codec.BOOL.optionalFieldOf("lit", false).forGetter(PlaceBlock::lit)
        ).apply(instance, PlaceBlock::new));

        @Override
        public Type type() {
            return Type.PLACE_BLOCK;
        }

        @Override
        public boolean observe(TechEvent event, TechTribeProgress progress, String prefix) {
            if (event.kind() != TechEventKind.PLACE_BLOCK || event.state() == null) return false;
            if (lit && !isLit(event.state())) return false;
            Identifier id = BuiltInRegistries.BLOCK.getKey(event.state().getBlock());
            boolean changed = false;
            for (Identifier block : blocks) {
                if (block.equals(id)) changed |= progress.mark(prefix + block);
            }
            return changed;
        }

        @Override
        public boolean satisfied(TechTribeProgress progress, String prefix, @Nullable Player player) {
            for (Identifier block : blocks) {
                if (progress.marked(prefix + block)) return true;
            }
            return false;
        }

        private static boolean isLit(BlockState state) {
            return state.hasProperty(CampfireBlock.LIT) && state.getValue(CampfireBlock.LIT);
        }
    }

    /** A single event kind with no further context. */
    record Event(TechEventKind kind) implements TechTrigger {
        public static final MapCodec<Event> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                TechEventKind.CODEC.fieldOf("kind").forGetter(Event::kind)
        ).apply(instance, Event::new));

        @Override
        public Type type() {
            return Type.EVENT;
        }

        @Override
        public boolean observe(TechEvent event, TechTribeProgress progress, String prefix) {
            if (event.kind() != kind) return false;
            return progress.mark(prefix + kind.getSerializedName());
        }

        @Override
        public boolean satisfied(TechTribeProgress progress, String prefix, @Nullable Player player) {
            return progress.marked(prefix + kind.getSerializedName());
        }
    }

    /** Tame any dinosaur, or one specific species. */
    record Tame(Optional<Identifier> species) implements TechTrigger {
        public static final MapCodec<Tame> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Identifier.CODEC.optionalFieldOf("species").forGetter(Tame::species)
        ).apply(instance, Tame::new));

        public static Tame any() {
            return new Tame(Optional.empty());
        }

        @Override
        public Type type() {
            return Type.TAME;
        }

        @Override
        public boolean observe(TechEvent event, TechTribeProgress progress, String prefix) {
            if (event.kind() != TechEventKind.TAME || event.species() == null) return false;
            if (species.filter(id -> !id.equals(event.species())).isPresent()) return false;
            return progress.mark(prefix + "tamed:" + species.map(Identifier::toString).orElse("any"));
        }

        @Override
        public boolean satisfied(TechTribeProgress progress, String prefix, @Nullable Player player) {
            return progress.marked(prefix + "tamed:" + species.map(Identifier::toString).orElse("any"));
        }
    }

    /** Every part must complete; parts record their own history as the events arrive. */
    record AllOf(List<TechTrigger> parts) implements TechTrigger {
        public static MapCodec<AllOf> codec(Codec<TechTrigger> self) {
            return RecordCodecBuilder.mapCodec(instance -> instance.group(
                    Codec.list(self).fieldOf("parts").forGetter(AllOf::parts)
            ).apply(instance, AllOf::new));
        }

        @Override
        public Type type() {
            return Type.ALL_OF;
        }

        @Override
        public boolean observe(TechEvent event, TechTribeProgress progress, String prefix) {
            boolean changed = false;
            for (int index = 0; index < parts.size(); index++) {
                changed |= parts.get(index).observe(event, progress, prefix + index + "/");
            }
            return changed;
        }

        @Override
        public boolean satisfied(TechTribeProgress progress, String prefix, @Nullable Player player) {
            for (int index = 0; index < parts.size(); index++) {
                if (!parts.get(index).satisfied(progress, prefix + index + "/", player)) return false;
            }
            return true;
        }
    }

    /** A node whose mechanic does not exist yet; it can never complete on its own. */
    record Future() implements TechTrigger {
        public static final MapCodec<Future> CODEC = MapCodec.unit(new Future());

        @Override
        public Type type() {
            return Type.FUTURE;
        }

        @Override
        public boolean satisfied(TechTribeProgress progress, String prefix, @Nullable Player player) {
            return false;
        }
    }
}
