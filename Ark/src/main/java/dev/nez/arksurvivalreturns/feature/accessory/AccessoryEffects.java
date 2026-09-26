package dev.nez.arksurvivalreturns.feature.accessory;

import java.util.Comparator;
import dev.nez.arksurvivalreturns.ArkSurvivalReturns;
import dev.nez.arksurvivalreturns.feature.creature.CreatureEntity;
import dev.nez.arksurvivalreturns.feature.primitive.StoneFireBlock;
import dev.nez.arksurvivalreturns.feature.primitive.StoneFireBlockEntity;
import dev.nez.arksurvivalreturns.feature.taming.TamingService;
import dev.nez.arksurvivalreturns.feature.tribe.TribeService;
import dev.nez.arksurvivalreturns.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.CandleCakeBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * The accessory effects that are not plain attributes. Every handler starts with a {@link Worn#has} check,
 * which is a field read after the first query of the tick.
 */
@EventBusSubscriber(modid = ArkSurvivalReturns.MOD_ID)
public final class AccessoryEffects {
    /** Amber Amulet recharge: one in-game day. */
    public static final int AMBER_RECHARGE = 24000;
    /** Herbal Pouch recharge: ninety seconds. */
    public static final int POUCH_RECHARGE = 1800;
    /** The Antler Frontlet disguise stays broken this long after the wearer strikes a creature. */
    public static final int DISGUISE_BREAK = 600;
    private static final Identifier SNOW_SPEED = ArkSurvivalReturns.id("accessory/snowshoes_on_snow");
    private static final Identifier ICE_SPEED = ArkSurvivalReturns.id("accessory/skates_on_ice");

    @SubscribeEvent static void setup(FMLCommonSetupEvent event) {
        if (Worn.CURIOS) event.enqueueWork(CuriosBridge::register);
    }

    // ------------------------------------------------------------------------------ ticking

    @SubscribeEvent static void tick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !player.isAlive()) return;
        Worn.State state = Worn.state(player);
        if (player.onGround() || player.isInWater() || player.onClimbable()) state.airJumpUsed = false;
        glide(player, state);
        int age = player.tickCount;
        if (age % 5 == 0) {
            terrainSpeed(player, Accessory.SNOWSHOES, SNOW_SPEED, 0.2, AccessoryEffects::snowy);
            terrainSpeed(player, Accessory.BONE_SKATES, ICE_SPEED, 0.8, s -> s.is(BlockTags.ICE));
        }
        if (age % 10 == 0 && Worn.has(player, Accessory.ARGENTAVIS_TOTEM)) keenEye(player);
        if (age % 20 != 0) return;
        if (Worn.has(player, Accessory.BONE_SNOW_GOGGLES)) {
            player.removeEffect(MobEffects.BLINDNESS);
            player.removeEffect(MobEffects.DARKNESS);
        }
        if (Worn.has(player, Accessory.ALICORN_PENDANT)) {
            for (Holder<MobEffect> effect : java.util.List.of(MobEffects.POISON, MobEffects.WITHER, MobEffects.NAUSEA, MobEffects.HUNGER))
                player.removeEffect(effect);
        }
        if (Worn.has(player, Accessory.AMBER_RING)) nightEye(player);
        if (Worn.has(player, Accessory.MEGALODON_TOTEM) && player.isEyeInFluid(net.minecraft.tags.FluidTags.WATER)) {
            player.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, 80, 0, true, false, true));
        }
        if (Worn.has(player, Accessory.HERBAL_POUCH) && player.getHealth() < player.getMaxHealth() / 3) herbs(player);
        if (age % 40 == 0 && Worn.has(player, Accessory.GUARDIAN_CROWN)) chieftain(player);
        if (age % 60 == 0 && Worn.has(player, Accessory.KINSHIP_BRACELET)) {
            for (CreatureEntity tame : tamesNear(player, 24)) if (tame.getHealth() < tame.getMaxHealth()) tame.heal(1.0f);
        }
    }

    /**
     * Membrane Glider, server half: the client flies the glide; the server marks it (so every client spreads
     * the wings) and keeps the fall distance at zero while the wearer holds jump in the air.
     */
    private static void glide(ServerPlayer player, Worn.State state) {
        boolean gliding = Worn.has(player, Accessory.MEMBRANE_GLIDER) && player.getLastClientInput().jump()
                && !player.onGround() && !player.isInWater() && !player.isFallFlying() && !player.isPassenger()
                && !player.getAbilities().flying && (state.glideTicks > 0 || player.fallDistance > 1.2);
        state.glideTicks = gliding ? state.glideTicks + 1 : 0;
        if (gliding) player.resetFallDistance();
        if (player.getData(AccessoryContent.GLIDING) != gliding) player.setData(AccessoryContent.GLIDING, gliding);
    }

    private static boolean snowy(BlockState state) {
        return state.is(BlockTags.SNOW) || state.is(Blocks.POWDER_SNOW);
    }

    /** Snowshoes and bone skates: a speed bonus only while standing on their ground. */
    private static void terrainSpeed(ServerPlayer player, Accessory accessory, Identifier id, double bonus,
            java.util.function.Predicate<BlockState> ground) {
        var speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) return;
        BlockPos feet = player.blockPosition();
        boolean on = Worn.has(player, accessory) && player.onGround()
                && (ground.test(player.level().getBlockState(feet)) || ground.test(player.level().getBlockState(feet.below())));
        if (on && !speed.hasModifier(id)) {
            speed.addTransientModifier(new AttributeModifier(id, bonus, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        } else if (!on && speed.hasModifier(id)) {
            speed.removeModifier(id);
        }
    }

    /** Amber Ring: night vision while the wearer stands in the dark; the ring's own dose ends in the light. */
    private static void nightEye(ServerPlayer player) {
        boolean dark = player.level().getMaxLocalRawBrightness(BlockPos.containing(player.getEyePosition())) < 7;
        MobEffectInstance current = player.getEffect(MobEffects.NIGHT_VISION);
        if (dark) {
            if (current == null || current.isAmbient() && current.getDuration() < 300)
                player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 400, 0, true, false, true));
        } else if (current != null && current.isAmbient() && current.getDuration() <= 400) {
            player.removeEffect(MobEffects.NIGHT_VISION);
        }
    }

    /** Herbal Pouch: chew the herbs when hurt badly, then wait for them to regrow. */
    private static void herbs(ServerPlayer player) {
        ItemStack pouch = Worn.stack(player, Accessory.HERBAL_POUCH);
        long now = player.level().getGameTime();
        if (pouch.isEmpty() || pouch.getOrDefault(AccessoryContent.RECHARGE.get(), 0L) > now) return;
        pouch.set(AccessoryContent.RECHARGE.get(), now + POUCH_RECHARGE);
        player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 1));
        player.level().playSound(null, player.blockPosition(), SoundEvents.GENERIC_EAT.value(), SoundSource.PLAYERS, 0.8f, 1.2f);
    }

    /** Guardian Crown: the tribe's tames fight harder and heal; tribe members nearby resist. */
    private static void chieftain(ServerPlayer player) {
        for (CreatureEntity tame : tamesNear(player, 24)) {
            tame.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 100, 0, true, false, true));
            tame.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 0, true, false, true));
        }
        for (Player other : player.level().getEntitiesOfClass(Player.class, player.getBoundingBox().inflate(24),
                p -> p != player && p.isAlive() && TribeService.sameTribe(p.getUUID(), player.getUUID()))) {
            other.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 100, 0, true, false, true));
        }
        player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 100, 0, true, false, true));
    }

    /** Tames this player's tribe may command, within range. */
    static java.util.List<CreatureEntity> tamesNear(Player player, double range) {
        return player.level().getEntitiesOfClass(CreatureEntity.class, player.getBoundingBox().inflate(range),
                c -> c.isAlive() && c.isTamed() && (TamingService.ownedBy(c, player) || TribeService.isTribeMember(c, player)));
    }

    /** Argentavis Totem: the creature the wearer looks at (within 48 blocks) is outlined for a few seconds. */
    private static void keenEye(ServerPlayer player) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0f);
        player.level().getEntitiesOfClass(CreatureEntity.class, player.getBoundingBox().inflate(48), c -> c.isAlive() && !c.isTamed())
                .stream()
                .filter(c -> {
                    Vec3 to = c.getBoundingBox().getCenter().subtract(eye);
                    double distance = to.length();
                    double spread = Math.max(0.02, (c.getBbWidth() * 0.6) / Math.max(distance, 1));
                    return distance < 48 && to.normalize().dot(look) > Math.cos(Math.atan(spread) + 0.03) && player.hasLineOfSight(c);
                })
                .min(Comparator.comparingDouble(c -> c.distanceToSqr(player)))
                .ifPresent(c -> c.addEffect(new MobEffectInstance(MobEffects.GLOWING, 60, 0, true, false, false)));
    }

    // ------------------------------------------------------------------------------ damage

    /** Amber Amulet runs before the downed state: a fatal blow cracks the amber instead. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    static void preserve(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || event.getAmount() < player.getHealth()) return;
        if (event.getSource().is(DamageTypes.GENERIC_KILL) || event.getSource().is(DamageTypes.FELL_OUT_OF_WORLD)) return;
        if (!Worn.has(player, Accessory.AMBER_AMULET)) return;
        ItemStack amulet = Worn.stack(player, Accessory.AMBER_AMULET);
        long now = player.level().getGameTime();
        if (amulet.isEmpty() || amulet.getOrDefault(AccessoryContent.RECHARGE.get(), 0L) > now) return;
        event.setCanceled(true);
        amulet.set(AccessoryContent.RECHARGE.get(), now + AMBER_RECHARGE);
        player.setHealth(Math.max(1.0f, player.getMaxHealth() * 0.4f));
        player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 100, 1));
        player.invulnerableTime = 20;
        player.level().playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.PLAYERS, 1.2f, 0.7f);
        if (player.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.WAX_OFF, player.getX(), player.getY(1.0), player.getZ(), 24, 0.4, 0.6, 0.4, 0.1);
        }
        player.sendSystemMessage(Component.translatable("accessory.arksurvivalreturns.amber_amulet.cracked"), true);
    }

    @SubscribeEvent static void incoming(LivingIncomingDamageEvent event) {
        LivingEntity target = event.getEntity();
        var source = event.getSource();
        if (target instanceof Player player) {
            if (Worn.has(player, Accessory.THORNPROOF_WRAPS) && (source.is(DamageTypes.SWEET_BERRY_BUSH) || source.is(DamageTypes.CACTUS))) {
                event.setCanceled(true);
                return;
            }
            if (Worn.has(player, Accessory.OBSIDIAN_BAND) && source.is(DamageTypeTags.IS_FIRE)) event.setAmount(event.getAmount() * 0.6f);
        }
        if (source.getEntity() instanceof Player attacker && target instanceof CreatureEntity creature) {
            Worn.state(attacker).disguiseBrokenUntil = attacker.level().getGameTime() + DISGUISE_BREAK;
            if (Worn.has(attacker, Accessory.FANG_NECKLACE) && unaware(creature, attacker)) {
                event.setAmount(event.getAmount() * 2.0f);
                if (attacker.level() instanceof ServerLevel level) level.sendParticles(ParticleTypes.CRIT,
                        creature.getX(), creature.getY(0.6), creature.getZ(), 12, creature.getBbWidth() / 3, 0.4, creature.getBbWidth() / 3, 0.2);
            }
        }
        if (target instanceof CreatureEntity tame && tame.isTamed() && tame.level() instanceof ServerLevel level) {
            var owner = TamingService.of(tame).owner();
            if (owner != null && level.getPlayerByUUID(owner) instanceof Player keeper && keeper.distanceToSqr(tame) < 24 * 24
                    && Worn.has(keeper, Accessory.KINSHIP_BRACELET)) event.setAmount(event.getAmount() * 0.85f);
        }
    }

    /** Fang Necklace ambush: the creature is not alarmed, not fighting and not after this attacker. */
    public static boolean unaware(CreatureEntity creature, Player attacker) {
        return !creature.isTamed() && !creature.behavior().alarm() && creature.getTarget() != attacker;
    }

    /** Serpent Ring: a melee hit leaves venom in the wound. */
    @SubscribeEvent static void struck(LivingDamageEvent.Post event) {
        if (event.getSource().getDirectEntity() instanceof Player attacker && Worn.has(attacker, Accessory.SERPENT_RING)
                && event.getHealthDamage() > 0 && event.getEntity() != attacker) {
            event.getEntity().addEffect(new MobEffectInstance(MobEffects.POISON, 80, 0), attacker);
        }
    }

    /** Snow goggles keep the eyes clear. */
    @SubscribeEvent static void effectApplicable(MobEffectEvent.Applicable event) {
        var effect = event.getEffectInstance().getEffect();
        if ((effect.is(MobEffects.BLINDNESS) || effect.is(MobEffects.DARKNESS)) && Worn.has(event.getEntity(), Accessory.BONE_SNOW_GOGGLES)) {
            event.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
        }
    }

    // --------------------------------------------------------------------------- archery

    /** Archer's Wristguard speeds the arrow; the Hide Quiver sometimes hands it back. */
    @SubscribeEvent static void arrow(EntityJoinLevelEvent event) {
        if (event.loadedFromDisk() || !(event.getEntity() instanceof AbstractArrow arrow) || arrow.tickCount > 0) return;
        if (!(arrow.getOwner() instanceof Player archer) || event.getLevel().isClientSide()) return;
        if (Worn.has(archer, Accessory.ARCHER_WRISTGUARD)) arrow.setDeltaMovement(arrow.getDeltaMovement().scale(1.25));
        if (Worn.has(archer, Accessory.HIDE_QUIVER) && arrow.pickup == AbstractArrow.Pickup.ALLOWED
                && archer.getRandom().nextFloat() < 0.3f) {
            ItemStack refund = arrow.getPickupItemStackOrigin().copyWithCount(1);
            arrow.pickup = AbstractArrow.Pickup.CREATIVE_ONLY;
            if (!archer.getInventory().add(refund)) archer.drop(refund, false);
        }
    }

    /** Bone Thumb Ring: bows and crossbows draw a third faster. */
    @SubscribeEvent static void draw(LivingEntityUseItemEvent.Tick event) {
        ItemStack item = event.getItem();
        if ((item.getItem() instanceof BowItem || item.getItem() instanceof CrossbowItem)
                && event.getEntity().tickCount % 3 == 0 && Worn.has(event.getEntity(), Accessory.THUMB_RING)) {
            event.setDuration(event.getDuration() - 1);
        }
    }

    // --------------------------------------------------------------------------- gathering

    /** Scythe Claws cut plants and leaves at a touch. */
    @SubscribeEvent static void breakSpeed(PlayerEvent.BreakSpeed event) {
        if (Worn.has(event.getEntity(), Accessory.SCYTHE_CLAWS) && plant(event.getState())) {
            event.setNewSpeed(event.getNewSpeed() * 6.0f);
        }
    }

    /** ...and leave more behind: fibre from grass, sticks from leaves. */
    @SubscribeEvent static void drops(BlockDropsEvent event) {
        if (!(event.getBreaker() instanceof Player player) || !Worn.has(player, Accessory.SCYTHE_CLAWS)) return;
        BlockState state = event.getState();
        var random = event.getLevel().getRandom();
        ItemStack extra = ItemStack.EMPTY;
        if (state.is(BlockTags.LEAVES) && random.nextFloat() < 0.5f) extra = new ItemStack(Items.STICK);
        else if ((state.is(Blocks.SHORT_GRASS) || state.is(Blocks.TALL_GRASS) || state.is(Blocks.FERN) || state.is(Blocks.LARGE_FERN))
                && random.nextFloat() < 0.6f) extra = new ItemStack(ModContent.PLANT_FIBER.get());
        if (!extra.isEmpty()) {
            BlockPos pos = event.getPos();
            event.getDrops().add(new ItemEntity(event.getLevel(), pos.getX() + 0.5, pos.getY() + 0.3, pos.getZ() + 0.5, extra));
        }
    }

    static boolean plant(BlockState state) {
        return state.is(BlockTags.LEAVES) || state.is(BlockTags.REPLACEABLE_BY_TREES) || state.is(BlockTags.FLOWERS)
                || state.is(BlockTags.CROPS) || state.is(BlockTags.SAPLINGS) || state.is(Blocks.SWEET_BERRY_BUSH)
                || state.is(Blocks.VINE) || state.is(Blocks.SUGAR_CANE) || state.is(Blocks.BAMBOO);
    }

    // ------------------------------------------------------------------------------ fire

    /** Ember Carrier: sneak and use an empty hand on a Stone Fire, campfire or candle to light it. */
    @SubscribeEvent static void kindle(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        if (event.getHand() != InteractionHand.MAIN_HAND || !player.isShiftKeyDown() || !player.getMainHandItem().isEmpty()
                || !Worn.has(player, Accessory.EMBER_CARRIER)) return;
        var level = event.getLevel();
        BlockPos pos = event.getPos();
        BlockState state = level.getBlockState(pos);
        boolean lit = false;
        if (state.getBlock() instanceof StoneFireBlock && !state.getValue(StoneFireBlock.LIT)
                && level.getBlockEntity(pos) instanceof StoneFireBlockEntity fire && fire.hasFuel()) {
            if (!level.isClientSide()) fire.light();
            lit = true;
        } else if (CampfireBlock.canLight(state) || CandleBlock.canLight(state) || CandleCakeBlock.canLight(state)) {
            if (!level.isClientSide()) level.setBlock(pos, state.setValue(BlockStateProperties.LIT, true), 11);
            lit = true;
        }
        if (!lit) return;
        level.playSound(player, pos, SoundEvents.FIRECHARGE_USE, SoundSource.BLOCKS, 0.6f, 1.4f);
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
    }

    // ------------------------------------------------------------------------ air jump

    /** Quetzal Mantle: the client asks for its mid-air jump; the server checks it and forgives the fall. */
    static void airJump(ServerPlayer player) {
        Worn.State state = Worn.state(player);
        if (state.airJumpUsed || player.onGround() || !Worn.has(player, Accessory.QUETZAL_MANTLE)) return;
        state.airJumpUsed = true;
        player.resetFallDistance();
        player.level().playSound(null, player.blockPosition(), SoundEvents.ENDER_DRAGON_FLAP, SoundSource.PLAYERS, 0.5f, 1.6f);
        if (player.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY(), player.getZ(), 10, 0.3, 0.05, 0.3, 0.02);
        }
    }

    // ------------------------------------------------------------------------ wildlife

    /** Antler Frontlet: herbivores take the wearer for one of the herd until they strike a creature. */
    public static boolean herdDisguise(CreatureEntity observer, LivingEntity target) {
        return target instanceof Player player && !observer.species().predator && Worn.has(player, Accessory.ANTLER_FRONTLET)
                && observer.getLastHurtByMob() != player
                && player.level().getGameTime() >= Worn.state(player).disguiseBrokenUntil;
    }

    /** Rex Totem: small wild creatures flee from the wearer instead of standing their ground. */
    public static boolean tyrant(CreatureEntity observer, Entity sensed) {
        return sensed instanceof Player player && !observer.isTamed() && observer.getBbWidth() <= 1.3f
                && !observer.species().apex() && Worn.has(player, Accessory.REX_TOTEM);
    }

    private AccessoryEffects() {}
}
