package dev.nez.arksurvivalreturns.feature.tech;

import java.util.List;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** Presentation metadata for one age: title, palette and the three lane headings from the design. */
public record TechAge(String id, String title, int order, String color, String laneSummary, List<Lane> lanes) {
    public static final Codec<TechAge> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("id").forGetter(TechAge::id),
            Codec.STRING.fieldOf("title").forGetter(TechAge::title),
            Codec.INT.optionalFieldOf("order", 0).forGetter(TechAge::order),
            Codec.STRING.optionalFieldOf("color", "#8a8a8a").forGetter(TechAge::color),
            Codec.STRING.optionalFieldOf("laneSummary", "").forGetter(TechAge::laneSummary),
            Lane.CODEC.listOf().optionalFieldOf("lanes", List.of()).forGetter(TechAge::lanes)
    ).apply(instance, TechAge::new));

    public record Lane(int index, String title) {
        public static final Codec<Lane> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.INT.fieldOf("index").forGetter(Lane::index),
                Codec.STRING.fieldOf("title").forGetter(Lane::title)
        ).apply(instance, Lane::new));
    }
}
