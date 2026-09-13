package dev.nez.arksurvivalreturns.feature.debug;

import java.lang.reflect.Modifier;
import java.util.*;
import dev.nez.arksurvivalreturns.feature.creature.CreatureEntity;
import net.minecraft.nbt.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.storage.TagValueOutput;

/** Bounded, stable key/value listing. Reflection is limited to this mod's own state objects. */
public final class DinoDebugSnapshot {
    public static final int PAGE_LINES = 12, LINE_LENGTH = 56, MAX_LINES = 4096;
    private final List<String> lines = new ArrayList<>();

    public static List<String> capture(CreatureEntity dino) {
        var out = new DinoDebugSnapshot();
        out.add("species", dino.species().id);
        out.add("level", dino.creatureLevel());
        out.add("health", dino.getHealth() + " / " + dino.getMaxHealth());
        out.add("behavior", dino.behavior());
        out.add("nightActive", dino.nightActive());
        out.add("position", dino.position());
        out.add("velocity", dino.getDeltaMovement());
        out.add("target", dino.getTarget());
        out.add("attackDamage", dino.getAttributeValue(Attributes.ATTACK_DAMAGE));
        out.add("movementSpeed", dino.getAttributeValue(Attributes.MOVEMENT_SPEED));
        out.add("navigation.done", dino.getNavigation().isDone());
        out.add("tickCount", dino.tickCount);
        out.add("onGround", dino.onGround());
        out.add("inWater", dino.isInWater());
        out.add("noAI", dino.isNoAi());
        out.add("naturalWildlife", dino.isNaturalWildlife());
        BuiltInRegistries.ATTRIBUTE.listElements().forEach(holder -> {
            // getAttribute/getInstance materializes defaults and changes the serialized attribute map.
            if (dino.getAttributes().hasAttribute(holder)) out.add("attributes." + holder.key().identifier(), dino.getAttributeValue(holder));
        });
        out.fields("runtime", dino);
        out.fields("speciesConfig", dino.species());
        var saved = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, dino.registryAccess());
        dino.saveWithoutId(saved);
        out.tag("saved", saved.buildResult(), 0);
        if (out.lines.size() == MAX_LINES) out.lines.set(MAX_LINES - 1, "[snapshot limit reached: remaining data omitted]");
        return List.copyOf(out.lines);
    }

    private void fields(String prefix, Object object) {
        for (Class<?> type = object.getClass(); type != null && type.getPackageName().startsWith("dev.nez.arksurvivalreturns"); type = type.getSuperclass()) {
            var fields = type.getDeclaredFields();
            Arrays.sort(fields, Comparator.comparing(java.lang.reflect.Field::getName));
            for (var field : fields) {
                if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic() || field.getName().equals("cache")) continue;
                String key = prefix + "." + field.getName();
                try {
                    if (!field.trySetAccessible()) { add(key, "[unavailable]"); continue; }
                    Object value = field.get(object);
                    // These are the only owned child objects. Never traverse a mob/world/target reference.
                    if (value != null && (field.getName().equals("wildlife") || field.getName().equals("mind"))) fields(key, value);
                    else add(key, value);
                } catch (IllegalAccessException e) { add(key, "[unavailable]"); }
            }
        }
    }
    private void tag(String path, Tag value, int depth) {
        if (lines.size() >= MAX_LINES) return;
        if (depth > 16) { add(path, "[nesting limit reached]"); return; }
        if (value instanceof CompoundTag compound && !compound.isEmpty()) {
            for (String key : new TreeSet<>(compound.keySet())) {
                tag(path + "." + key, compound.get(key), depth + 1);
                if (lines.size() >= MAX_LINES) break;
            }
        } else if (value instanceof ListTag list && !list.isEmpty()) {
            for (int i = 0; i < list.size() && lines.size() < MAX_LINES; i++) tag(path + "[" + i + "]", list.get(i), depth + 1);
        } else add(path, value);
    }
    private void add(String key, Object value) {
        String text = key + " = " + (value instanceof Entity e ? e.getUUID() + " (" + e.getType().toShortString() + ")" : String.valueOf(value));
        text = text.replaceAll("[\\p{Cntrl}§]", " ");
        for (int offset = 0; offset < text.length() && lines.size() < MAX_LINES; offset += LINE_LENGTH)
            lines.add((offset == 0 ? "" : "  ") + text.substring(offset, Math.min(text.length(), offset + LINE_LENGTH)));
    }
    private DinoDebugSnapshot() {}
}
