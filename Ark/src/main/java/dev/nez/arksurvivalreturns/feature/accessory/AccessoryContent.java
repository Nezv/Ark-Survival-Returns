package dev.nez.arksurvivalreturns.feature.accessory;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.mojang.serialization.Codec;
import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import dev.nez.arksurvivalreturns.registry.ModContent;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Accessories (Curios gear), the creature parts they are made from, and their runtime state. Items go into
 * the shared {@link ModContent#ITEMS} register; the accessories get their own creative tab.
 */
public final class AccessoryContent {
    public static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, ArkSurvivalReturns.MOD_ID);
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, ArkSurvivalReturns.MOD_ID);

    /** Game time at which a recharging relic (Amber Amulet, Herbal Pouch) is ready again. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Long>> RECHARGE =
            COMPONENTS.registerComponentType("recharge", b -> b.persistent(Codec.LONG).networkSynchronized(ByteBufCodecs.VAR_LONG));

    /** Per-player worn set, refreshed once per tick on each side; never saved. */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Worn.State>> WORN = ATTACHMENTS.register("worn",
            () -> AttachmentType.builder(Worn.State::new).build());
    /** True while the player glides on a Membrane Glider; synced so every client spreads the wings. */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Boolean>> GLIDING = ATTACHMENTS.register("gliding",
            () -> AttachmentType.builder(() -> false).sync(ByteBufCodecs.BOOL).build());

    public static final Map<Accessory, DeferredItem<AccessoryItem>> ITEMS = new EnumMap<>(Accessory.class);
    /** Creature parts, keyed by id: the raw materials of the gadgets and relics. */
    public static final Map<String, DeferredItem<Item>> TROPHIES = new LinkedHashMap<>();
    public static final List<String> TROPHY_IDS = List.of("thick_pelt", "giant_antler", "fang", "wing_membrane",
            "sickle_claw", "scythe_claw", "croc_scute", "marine_flipper", "megalodon_tooth", "unicorn_horn",
            "quetzal_feather", "venom_fang", "tyrant_tooth", "argentavis_talon", "amber", "red_ochre");
    private static final List<String> RARE_TROPHIES = List.of("unicorn_horn", "quetzal_feather", "amber", "tyrant_tooth");

    static {
        for (Accessory accessory : Accessory.values()) {
            Rarity rarity = accessory == Accessory.GUARDIAN_CROWN ? Rarity.EPIC
                    : accessory.group == Accessory.Group.RELIC ? Rarity.RARE : Rarity.COMMON;
            ITEMS.put(accessory, ModContent.ITEMS.registerItem(accessory.id,
                    p -> new AccessoryItem(p, accessory), p -> p.stacksTo(1).rarity(rarity)));
        }
        for (String id : TROPHY_IDS) {
            Rarity rarity = RARE_TROPHIES.contains(id) ? Rarity.UNCOMMON : Rarity.COMMON;
            TROPHIES.put(id, ModContent.ITEMS.registerSimpleItem(id, p -> p.stacksTo(64).rarity(rarity)));
        }
        ModContent.TABS.register("accessories", () -> CreativeModeTab.builder()
                .title(Component.translatable("itemGroup.arksurvivalreturns.accessories"))
                .icon(() -> ITEMS.get(Accessory.PACK_FRAME).get().getDefaultInstance())
                .displayItems((parameters, output) -> {
                    ITEMS.values().forEach(item -> output.accept(item.get()));
                    TROPHIES.values().forEach(item -> output.accept(item.get()));
                }).build());
    }

    public static Item trophy(String id) { return TROPHIES.get(id).get(); }

    public static void register(IEventBus bus) {
        AccessoryAttributes.ATTRIBUTES.register(bus);
        COMPONENTS.register(bus);
        ATTACHMENTS.register(bus);
        dev.nez.arksurvivalreturns.gametest.AccessoryGameTests.FUNCTIONS.register(bus);
    }

    private AccessoryContent() {}
}
