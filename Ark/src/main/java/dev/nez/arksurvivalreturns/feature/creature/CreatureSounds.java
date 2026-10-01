package dev.nez.arksurvivalreturns.feature.creature;

import com.google.gson.JsonParser;
import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.Locale;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.jspecify.annotations.Nullable;

/** Original ASE recordings. Absent source categories stay silent. No client classes are loaded. */
public final class CreatureSounds {
    public enum Role { AMBIENT, ATTACK, HURT, DEATH, SLEEP, WAKE, WARN, STEP, EAT, FLAP, TAKEOFF, LAND }
    private static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, ArkSurvivalReturns.MOD_ID);
    private record Entry(DeferredHolder<SoundEvent, SoundEvent> event, int durationTicks) {}
    private static final EnumMap<Species, EnumMap<Role, Entry>> EVENTS = new EnumMap<>(Species.class);
    static {
        String path = "/assets/arksurvivalreturns/audio/creature_catalog.json";
        try (var stream = CreatureSounds.class.getResourceAsStream(path)) {
            if (stream == null) throw new IllegalStateException("Missing original creature sound catalog");
            var catalog = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            for (var species : Species.values()) {
                var roles = new EnumMap<Role, Entry>(Role.class);
                var source = catalog.getAsJsonObject(species.id);
                for (var role : Role.values()) {
                    String key = role.name().toLowerCase(Locale.ROOT);
                    String name = "creature." + species.id + "." + key;
                    var event = SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(ArkSurvivalReturns.id(name)));
                    roles.put(role, new Entry(event, source.get(key).getAsInt()));
                }
                EVENTS.put(species, roles);
            }
        } catch (Exception error) {
            throw new IllegalStateException("Cannot load original creature sounds", error);
        }
    }
    public static void register(IEventBus bus) { SOUNDS.register(bus); }
    public static @Nullable SoundEvent of(Species species, Role role) {
        var entry = EVENTS.get(species).get(role);
        return entry.durationTicks() == 0 ? null : entry.event().get();
    }
    public static int durationTicks(Species species, Role role) { return EVENTS.get(species).get(role).durationTicks(); }
    private CreatureSounds() {}
}
