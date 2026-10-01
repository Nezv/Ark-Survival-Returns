package dev.nez.arksurvivalreturns.feature.station;

import java.util.ArrayList;
import java.util.List;
import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import dev.nez.arksurvivalreturns.Config;
import dev.nez.arksurvivalreturns.feature.levels.ArkLevels;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.SmithingTrimRecipe;
import net.neoforged.neoforge.event.EventHooks;

public final class WorkstationCrafting {
    public static boolean craft(ServerPlayer player, WorkstationPayload.Craft packet) {
        if (!(player.containerMenu instanceof WorkstationMenu menu) || menu.containerId != packet.containerId()
                || !menu.station.equals(packet.station()) || !menu.stillValid(player)) return false;
        if (packet.times() != -1 && (packet.times() <= 0 || packet.times() > 65536)) return false;
        var station = WorkstationCatalog.get(packet.station()); if (station == null) return false;
        WorkstationDefinition.Craft chosen = null;
        for (var candidate : station.crafts()) {
            String key = "i:" + candidate.category().id() + "/" + (candidate.group() == null ? "" : candidate.group().group() + "/") + candidate.entry().family();
            if (key.equals(packet.key()) && packet.variant() >= 0 && packet.variant() < candidate.entry().variants().size()
                    && candidate.variant() == candidate.entry().variants().get(packet.variant()) && candidate.variant().item().equals(packet.item())) {
                chosen = candidate; break;
            }
        }
        if (chosen == null || Config.WORKSTATION_LEVEL_GATE.get() && !ArkLevels.requires(player, chosen.level())) return false;
        var variant = chosen.variant();
        var inventory = WorkstationInventory.read(player.getInventory(), variant.cost());
        int times = packet.times() == -1 ? inventory.maxCrafts() : packet.times();
        var plan = inventory.plan(times); if (plan == null) return false;
        List<ItemStack> outputs = new ArrayList<>();
        if (variant.apply()) {
            // Each armour piece keeps its components. Applying the same trim twice is refused before payment.
            String patternId = variant.item().replace("_armor_trim_smithing_template", "");
            var pattern = player.registryAccess().lookupOrThrow(Registries.TRIM_PATTERN).get(Identifier.parse(patternId));
            if (pattern.isEmpty()) return false;
            int materialRow = inventory.selectors().indexOf("#minecraft:trim_materials"); if (materialRow < 0) return false;
            List<ItemStack> materials = new ArrayList<>();
            for (int slot = 0; slot < inventory.stacks().size(); slot++) for (int n = 0; n < plan.allocation()[materialRow][slot]; n++) materials.add(inventory.stacks().get(slot));
            int row = inventory.selectors().indexOf("#minecraft:trimmable_armor"); if (row < 0) return false;
            for (int slot = 0; slot < inventory.stacks().size(); slot++) for (int n = 0; n < plan.allocation()[row][slot]; n++) {
                if (outputs.size() >= materials.size()) return false;
                ItemStack trimmed = SmithingTrimRecipe.applyTrim(inventory.stacks().get(slot), materials.get(outputs.size()), pattern.get());
                if (trimmed.isEmpty()) return false; outputs.add(trimmed);
            }
        } else {
            var item = BuiltInRegistries.ITEM.getValue(Identifier.parse(variant.item()));
            if (item == null) return false;
            ItemStack prototype = variant.output() == null ? new ItemStack(item) : ItemStack.CODEC.parse(
                    player.registryAccess().createSerializationContext(com.mojang.serialization.JsonOps.INSTANCE),
                    com.google.gson.JsonParser.parseString(variant.output())).getOrThrow();
            if (!prototype.is(item)) return false;
            long count = (long) times * variant.count();
            if (count > 65536) return false;
            while (count > 0) {
                int size = (int) Math.min(count, prototype.getMaxStackSize());
                outputs.add(prototype.copyWithCount(size)); count -= size;
            }
        }
        Identifier recipe = variant.recipe() != null ? Identifier.parse(variant.recipe())
                : ArkSurvivalReturns.id("workstation/" + packet.station().replace(':', '/') + "/" + packet.key().substring(2)
                        + "/" + variant.item().replace(':', '/') + (variant.apply() ? "/apply" : ""));
        if (!inventory.consume(player.getInventory(), plan)) return false;
        SimpleContainer ingredients = new SimpleContainer(inventory.stacks().toArray(ItemStack[]::new));
        for (ItemStack output : outputs) {
            ItemStack eventStack = output.copy();
            output.onCraftedBy(player, eventStack.getCount());
            player.getInventory().add(output);
            if (!output.isEmpty()) player.drop(output, false);
            EventHooks.firePlayerCraftingEvent(player, eventStack, ingredients);
        }
        ArkLevels.crafted(player, recipe);
        if (player.connection != null && player.connection.hasChannel(WorkstationPayload.Result.TYPE)) {
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                    new WorkstationPayload.Result(menu.containerId, variant.item(), times * variant.count(), variant.apply()));
        }
        menu.broadcastChanges(); player.inventoryMenu.broadcastChanges(); return true;
    }
    private WorkstationCrafting() {}
}
