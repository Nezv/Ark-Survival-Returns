package dev.nez.arksurvivalreturns.feature.accessory;

import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityAttributeModificationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Player attributes read by Ark systems, so gear (and anything else) can tune them and their tooltips come
 * for free: how visible, noisy and smelly a player is to wild creatures (WildlifeSenses), and how much they
 * carry before slowing down (MassService). All start at 1.0 and scale their system's base value.
 */
@EventBusSubscriber(modid = ArkSurvivalReturns.MOD_ID)
public final class AccessoryAttributes {
    public static final DeferredRegister<Attribute> ATTRIBUTES = DeferredRegister.create(Registries.ATTRIBUTE, ArkSurvivalReturns.MOD_ID);

    /** Multiplies how far creatures can see this player. */
    public static final DeferredHolder<Attribute, Attribute> VISIBILITY = ATTRIBUTES.register("visibility",
            () -> sense("visibility"));
    /** Multiplies how far creatures hear this player move. */
    public static final DeferredHolder<Attribute, Attribute> NOISE = ATTRIBUTES.register("noise", () -> sense("noise"));
    /** Multiplies how far creatures smell this player downwind; zero hides the scent entirely. */
    public static final DeferredHolder<Attribute, Attribute> SCENT = ATTRIBUTES.register("scent", () -> sense("scent"));
    /** Multiplies the carried-mass capacity before the load slows the player. */
    public static final DeferredHolder<Attribute, Attribute> CARRY_CAPACITY = ATTRIBUTES.register("carry_capacity",
            () -> new RangedAttribute("attribute.name." + ArkSurvivalReturns.MOD_ID + ".carry_capacity", 1.0, 0.0, 16.0)
                    .setSyncable(true));

    private static Attribute sense(String name) {
        return new RangedAttribute("attribute.name." + ArkSurvivalReturns.MOD_ID + "." + name, 1.0, 0.0, 4.0)
                .setSyncable(true).setSentiment(Attribute.Sentiment.NEGATIVE);
    }

    @SubscribeEvent static void attach(EntityAttributeModificationEvent event) {
        for (var attribute : java.util.List.of(VISIBILITY, NOISE, SCENT, CARRY_CAPACITY)) {
            if (!event.has(EntityType.PLAYER, attribute)) event.add(EntityType.PLAYER, attribute);
        }
    }

    /** The attribute's value on this entity, or 1.0 for entities that do not carry it. */
    public static double value(LivingEntity entity, Holder<Attribute> attribute) {
        var instance = entity.getAttribute(attribute);
        return instance == null ? 1.0 : instance.getValue();
    }

    private AccessoryAttributes() {}
}
