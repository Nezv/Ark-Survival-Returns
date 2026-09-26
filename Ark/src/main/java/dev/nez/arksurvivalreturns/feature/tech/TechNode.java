package dev.nez.arksurvivalreturns.feature.tech;

import java.util.List;
import java.util.Optional;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * One technology node. Everything the future screen needs (age, lane, design box, icon, kind, note)
 * lives here so the tree can be laid out from data alone.
 */
public record TechNode(
        String id,
        String title,
        String task,
        String age,
        int lane,
        List<Integer> box,
        Optional<String> icon,
        TechNodeKind kind,
        Optional<String> note,
        List<String> requires,
        TechTrigger trigger
) {
    public static final Codec<TechNode> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("id").forGetter(TechNode::id),
            Codec.STRING.fieldOf("title").forGetter(TechNode::title),
            Codec.STRING.optionalFieldOf("task", "").forGetter(TechNode::task),
            Codec.STRING.fieldOf("age").forGetter(TechNode::age),
            Codec.INT.optionalFieldOf("lane", 0).forGetter(TechNode::lane),
            Codec.INT.listOf().optionalFieldOf("box", List.of()).forGetter(TechNode::box),
            Codec.STRING.optionalFieldOf("icon").forGetter(TechNode::icon),
            TechNodeKind.CODEC.optionalFieldOf("kind", TechNodeKind.MAIN).forGetter(TechNode::kind),
            Codec.STRING.optionalFieldOf("note").forGetter(TechNode::note),
            Codec.STRING.listOf().optionalFieldOf("requires", List.of()).forGetter(TechNode::requires),
            TechTrigger.CODEC.fieldOf("trigger").forGetter(TechNode::trigger)
    ).apply(instance, TechNode::new));
}
