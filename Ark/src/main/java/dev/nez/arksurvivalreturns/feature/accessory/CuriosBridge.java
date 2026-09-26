package dev.nez.arksurvivalreturns.feature.accessory;

import java.util.EnumSet;
import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import dev.nez.arksurvivalreturns.feature.creature.CreatureEntity;
import dev.nez.arksurvivalreturns.feature.mass.MassService;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import org.jspecify.annotations.Nullable;
import top.theillusivec4.curios.api.CurioAttributeModifiers;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.SlotResult;
import top.theillusivec4.curios.api.type.capability.ICurio;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

/**
 * Everything that touches the Curios API. Only loaded when Curios is installed ({@link Worn#CURIOS}).
 */
final class CuriosBridge {
    static void register() {
        for (Accessory accessory : Accessory.values()) CuriosApi.registerCurio(accessory.item(), new ArkCurio(accessory));
    }

    static void collect(LivingEntity entity, EnumSet<Accessory> out) {
        CuriosApi.getCuriosInventory(entity).ifPresent(handler -> {
            for (SlotResult result : handler.findCurios(stack -> stack.getItem() instanceof AccessoryItem)) {
                out.add(((AccessoryItem) result.stack().getItem()).accessory());
            }
        });
    }

    static ItemStack find(LivingEntity entity, Item item) {
        return CuriosApi.getCuriosInventory(entity).flatMap(handler -> handler.findFirstCurio(item))
                .map(SlotResult::stack).orElse(ItemStack.EMPTY);
    }

    /** Curio behaviour shared by every Ark accessory. */
    private record ArkCurio(Accessory accessory) implements ICurioItem {
        @Override
        public CurioAttributeModifiers getDefaultCurioAttributeModifiers(ItemStack stack) {
            var builder = CurioAttributeModifiers.builder();
            int k = 0;
            for (Accessory.Bonus bonus : accessory.bonuses) {
                Identifier id = ArkSurvivalReturns.id("accessory/" + accessory.id + "/" + k++);
                builder.addModifier(bonus.attribute().get(), new AttributeModifier(id, bonus.amount(), bonus.operation()));
            }
            return builder.build();
        }

        /** One of each: a second copy in the universal curio slot would stack the bonus. */
        @Override
        public boolean canEquip(SlotContext slot, ItemStack stack) {
            return CuriosApi.getCuriosInventory(slot.entity()).map(handler -> handler.findCurios(stack.getItem()).stream()
                    .allMatch(worn -> worn.slotContext().identifier().equals(slot.identifier())
                            && worn.slotContext().index() == slot.index())).orElse(true);
        }

        @Override
        public boolean canEquipFromUse(SlotContext slot, ItemStack stack) {
            return true;
        }

        @Override
        public ICurio.SoundInfo getEquipSound(SlotContext slot, ItemStack stack) {
            var sound = switch (accessory) {
                case BONE_VEST, BONE_SKATES, FANG_NECKLACE, TALLY_BONE, THUMB_RING -> SoundEvents.ARMOR_EQUIP_CHAIN;
                case GUARDIAN_CROWN, ALICORN_PENDANT, AMBER_AMULET, AMBER_RING, OBSIDIAN_BAND -> SoundEvents.ARMOR_EQUIP_GOLD;
                default -> SoundEvents.ARMOR_EQUIP_LEATHER;
            };
            return new ICurio.SoundInfo(sound.value(), 1.0f, 1.0f);
        }

        /** Tally Bone: one extra Looting level against creatures, so carcasses give more meat, hide and parts. */
        @Override
        public int getLootingLevel(SlotContext slot, @Nullable LootContext loot, ItemStack stack) {
            if (accessory != Accessory.TALLY_BONE || loot == null) return 0;
            return loot.getOptionalParameter(LootContextParams.THIS_ENTITY) instanceof CreatureEntity ? 1 : 0;
        }

        @Override
        public boolean canWalkOnPowderedSnow(SlotContext slot, ItemStack stack) {
            return accessory == Accessory.SNOWSHOES;
        }

        @Override
        public void onEquip(SlotContext slot, ItemStack previous, ItemStack stack) {
            changed(slot);
        }

        @Override
        public void onUnequip(SlotContext slot, ItemStack next, ItemStack stack) {
            changed(slot);
        }

        private void changed(SlotContext slot) {
            if (slot.entity() instanceof Player player) {
                Worn.invalidate(player);
                if (accessory == Accessory.PACK_FRAME) MassService.markDirty(player);
            }
        }
    }

    private CuriosBridge() {}
}
