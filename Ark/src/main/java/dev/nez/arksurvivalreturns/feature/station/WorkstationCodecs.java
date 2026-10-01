package dev.nez.arksurvivalreturns.feature.station;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import dev.nez.arksurvivalreturns.feature.station.WorkstationDefinition.*;

public final class WorkstationCodecs {
    private static final Codec<Integer> POSITIVE = Codec.intRange(1, 65536);
    private static final Codec<Integer> LEVEL = Codec.intRange(0, 1000000);
    public static final Codec<Map<String, Integer>> COST = Codec.unboundedMap(Codec.STRING, POSITIVE);
    public static final Codec<Variant> VARIANT = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("item").forGetter(Variant::item), POSITIVE.optionalFieldOf("count", 1).forGetter(Variant::count),
            COST.fieldOf("cost").forGetter(Variant::cost), Codec.STRING.optionalFieldOf("recipe").forGetter(v -> Optional.ofNullable(v.recipe())),
            Codec.BOOL.optionalFieldOf("apply", false).forGetter(Variant::apply),
            Codec.STRING.optionalFieldOf("name").forGetter(v -> Optional.ofNullable(v.name())),
            Codec.STRING.optionalFieldOf("output").forGetter(v -> Optional.ofNullable(v.output()))
    ).apply(i, (item, count, cost, recipe, apply, name, output) -> new Variant(item, count, cost, recipe.orElse(null), apply, name.orElse(null), output.orElse(null))));
    public static final Codec<Entry> ENTRY = Codec.recursive("workstation_entry", self -> RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.optionalFieldOf("group").forGetter(e -> Optional.ofNullable(e.group())),
            Codec.STRING.optionalFieldOf("family").forGetter(e -> Optional.ofNullable(e.family())),
            Codec.STRING.optionalFieldOf("title").forGetter(e -> Optional.ofNullable(e.title())),
            Codec.STRING.optionalFieldOf("icon").forGetter(e -> Optional.ofNullable(e.icon())),
            LEVEL.optionalFieldOf("level").forGetter(e -> Optional.ofNullable(e.level())),
            Codec.BOOL.optionalFieldOf("planned", false).forGetter(Entry::planned),
            self.listOf().optionalFieldOf("items", List.of()).forGetter(Entry::items),
            VARIANT.listOf().optionalFieldOf("variants", List.of()).forGetter(Entry::variants),
            Codec.STRING.optionalFieldOf("item").forGetter(e -> Optional.ofNullable(e.item())),
            POSITIVE.optionalFieldOf("count", 1).forGetter(Entry::count), COST.optionalFieldOf("cost", Map.of()).forGetter(Entry::cost)
    ).apply(i, (group, family, title, icon, level, planned, items, variants, item, count, cost) ->
            new Entry(group.orElse(null), family.orElse(null), title.orElse(null), icon.orElse(null), level.orElse(null),
                    planned, items, variants, item.orElse(null), count, cost))));
    public static final Codec<Category> CATEGORY = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("id").forGetter(Category::id), Codec.STRING.fieldOf("title").forGetter(Category::title),
            Codec.STRING.fieldOf("icon").forGetter(Category::icon), Codec.STRING.fieldOf("color").forGetter(Category::color),
            LEVEL.optionalFieldOf("level", 0).forGetter(Category::level),
            Codec.STRING.optionalFieldOf("after").forGetter(c -> Optional.ofNullable(c.after())),
            Codec.BOOL.optionalFieldOf("planned", false).forGetter(Category::planned),
            ENTRY.listOf().optionalFieldOf("items", List.of()).forGetter(Category::items)
    ).apply(i, (id, title, icon, color, level, after, planned, items) -> new Category(id, title, icon, color, level, after.orElse(null), planned, items)));
    public static final Codec<WorkstationDefinition> STATION = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("station").forGetter(WorkstationDefinition::station),
            Codec.STRING.fieldOf("title").forGetter(WorkstationDefinition::title),
            Codec.STRING.optionalFieldOf("verb", "Craft").forGetter(WorkstationDefinition::verb),
            Codec.STRING.optionalFieldOf("accent", "#e6e8e1").forGetter(WorkstationDefinition::accent),
            Codec.STRING.optionalFieldOf("well", "dots").forGetter(WorkstationDefinition::well),
            CATEGORY.listOf().fieldOf("categories").forGetter(WorkstationDefinition::categories)
    ).apply(i, WorkstationDefinition::new));
    private WorkstationCodecs() {}
}
