package net.geraldhofbauer.vanillaplusadditions.modules.cat_guardian;

import com.mojang.serialization.Codec;
import net.geraldhofbauer.vanillaplusadditions.VanillaPlusAdditions;
import net.geraldhofbauer.vanillaplusadditions.core.AbstractModule;
import net.geraldhofbauer.vanillaplusadditions.core.VanillaPlusCreativeTabs;
import net.geraldhofbauer.vanillaplusadditions.modules.cat_guardian.block.CatBowlBlock;
import net.geraldhofbauer.vanillaplusadditions.modules.cat_guardian.block.CatFeedingStationBlock;
import net.geraldhofbauer.vanillaplusadditions.modules.cat_guardian.blockentity.AbstractCatBowlBlockEntity;
import net.geraldhofbauer.vanillaplusadditions.modules.cat_guardian.blockentity.CatBowlBlockEntity;
import net.geraldhofbauer.vanillaplusadditions.modules.cat_guardian.blockentity.CatFeedingStationBlockEntity;
import net.geraldhofbauer.vanillaplusadditions.modules.cat_guardian.config.CatGuardianConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.animal.Cat;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.EnumSet;
import java.util.List;
import java.util.function.Supplier;

public class CatGuardianModule extends AbstractModule<CatGuardianModule, CatGuardianConfig> {

    // ---- Deferred registers ----

    private static final DeferredRegister.Blocks BLOCKS =
            DeferredRegister.createBlocks(VanillaPlusAdditions.MODID);

    private static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(VanillaPlusAdditions.MODID);

    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, VanillaPlusAdditions.MODID);

    private static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, VanillaPlusAdditions.MODID);

    // ---- Blocks ----

    public static final DeferredBlock<CatBowlBlock> CAT_BOWL =
            BLOCKS.register("cat_bowl", () -> new CatBowlBlock(
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.STONE)
                            .strength(1.5F, 6.0F)
                            .sound(SoundType.STONE)
                            .noOcclusion()
            ));

    public static final DeferredBlock<CatFeedingStationBlock> CAT_FEEDING_STATION =
            BLOCKS.register("cat_feeding_station", () -> new CatFeedingStationBlock(
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.STONE)
                            .strength(3.5F, 12.0F)
                            .sound(SoundType.STONE)
                            .requiresCorrectToolForDrops()
            ));

    // ---- Block items ----

    public static final DeferredItem<BlockItem> CAT_BOWL_ITEM =
            ITEMS.register("cat_bowl", () -> new BlockItem(CAT_BOWL.get(), new Item.Properties()));

    public static final DeferredItem<BlockItem> CAT_FEEDING_STATION_ITEM =
            ITEMS.register("cat_feeding_station",
                    () -> new BlockItem(CAT_FEEDING_STATION.get(), new Item.Properties()));

    // ---- Block entity types ----

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CatBowlBlockEntity>> CAT_BOWL_BE =
            BLOCK_ENTITY_TYPES.register("cat_bowl",
                    () -> BlockEntityType.Builder.of(CatBowlBlockEntity::new, CAT_BOWL.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CatFeedingStationBlockEntity>> CAT_FEEDING_STATION_BE =
            BLOCK_ENTITY_TYPES.register("cat_feeding_station",
                    () -> BlockEntityType.Builder.of(CatFeedingStationBlockEntity::new,
                            CAT_FEEDING_STATION.get()).build(null));

    // ---- Entity attachment types (persisted in entity NBT) ----

    /**
     * Stores the associated bowl/station block position as a long (BlockPos.asLong()).
     * Long.MIN_VALUE means "no bowl".
     */
    public static final Supplier<AttachmentType<Long>> CAT_BOWL_POS =
            ATTACHMENT_TYPES.register("cat_bowl_pos", () ->
                    AttachmentType.<Long>builder(() -> Long.MIN_VALUE)
                            .serialize(Codec.LONG)
                            .build());

    /**
     * Countdown in ticks while the cat is in the fed/guarding state.
     * 0 means hungry — cat will pathfind to its bowl.
     */
    public static final Supplier<AttachmentType<Integer>> CAT_FED_TICKS =
            ATTACHMENT_TYPES.register("cat_fed_ticks", () ->
                    AttachmentType.<Integer>builder(() -> 0)
                            .serialize(Codec.INT)
                            .build());

    // ---- Singleton reference for config access from static context ----

    private static CatGuardianModule INSTANCE;

    public static double getAssociationRadius() {
        return INSTANCE != null ? INSTANCE.getConfig().getAssociationRadius() : 64.0D;
    }

    // ---- Constructor ----

    public CatGuardianModule() {
        super(
                "cat_guardian",
                "Cat Guardian",
                "Adds cat food bowls and feeding stations. Fed cats actively guard your base against hostile mobs.",
                CatGuardianConfig::new
        );
        INSTANCE = this;
    }

    // ---- Module lifecycle ----

    @Override
    protected void onInitialize() {
        BLOCKS.register(getModEventBus());
        ITEMS.register(getModEventBus());
        BLOCK_ENTITY_TYPES.register(getModEventBus());
        ATTACHMENT_TYPES.register(getModEventBus());

        getModEventBus().addListener(this::onRegisterCapabilities);

        VanillaPlusCreativeTabs.addAllToMainTab(CAT_BOWL_ITEM, CAT_FEEDING_STATION_ITEM);

        NeoForge.EVENT_BUS.register(this);

        getLogger().info("Cat Guardian module initialized");
    }

    private void onRegisterCapabilities(RegisterCapabilitiesEvent event) {
        // Expose IItemHandler on feeding station so hoppers, droppers and Create contraptions
        // can fill it without any extra wiring.
        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                CAT_FEEDING_STATION_BE.get(),
                (be, side) -> be.getInventory()
        );
    }

    // ---- Cat join level — inject guard target goal ----

    @SubscribeEvent
    public void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (!isModuleEnabled()) return;
        if (event.getLevel().isClientSide()) return;
        if (!(event.getEntity() instanceof Cat cat)) return;
        // Guard against duplicates when entity changes dimension
        boolean alreadyAdded = cat.targetSelector.getAvailableGoals().stream()
                .anyMatch(w -> w.getGoal() instanceof CatGuardTargetGoal);
        if (!alreadyAdded) {
            cat.targetSelector.addGoal(1, new CatGuardTargetGoal(cat));
        }
    }

    // ---- Cat tick logic ----

    @SubscribeEvent
    public void onEntityTick(EntityTickEvent.Post event) {
        if (!isModuleEnabled()) return;
        if (!(event.getEntity() instanceof Cat cat)) return;
        if (cat.level().isClientSide()) return;
        if (!cat.isTame() || cat.getOwnerUUID() == null) return;
        // Run every 10 ticks to reduce overhead
        if (cat.tickCount % 10 != 0) return;

        tickCat(cat);
    }

    private void tickCat(Cat cat) {
        CatGuardianConfig config = getConfig();

        long bowlPosLong = cat.getData(CAT_BOWL_POS.get());
        boolean hasBowl = bowlPosLong != Long.MIN_VALUE;

        int fedTicks = cat.getData(CAT_FED_TICKS.get());

        if (fedTicks > 0) {
            // Decrement fed timer — CatGuardTargetGoal handles target selection each tick
            cat.setData(CAT_FED_TICKS.get(), Math.max(0, fedTicks - 10));
            return;
        }

        // Hungry: try to get to bowl and eat
        if (!hasBowl) {
            tryAutoAssociate(cat, config.getAutoAssociateRadius());
            return;
        }

        BlockPos bowlPos = BlockPos.of(bowlPosLong);
        AbstractCatBowlBlockEntity bowl = getBowlEntity(cat, bowlPos);
        if (bowl == null) return; // clears stale CAT_BOWL_POS inside getBowlEntity

        if (!bowl.hasFish()) return;

        double distSq = cat.distanceToSqr(
                bowlPos.getX() + 0.5, bowlPos.getY(), bowlPos.getZ() + 0.5);

        if (distSq > 4.0 && !cat.isOrderedToSit()) {
            if (cat.getNavigation().isDone()) {
                cat.getNavigation().moveTo(
                        bowlPos.getX() + 0.5, bowlPos.getY(), bowlPos.getZ() + 0.5, 0.8);
            }
        } else if (distSq <= 4.0) {
            var fish = bowl.takeFish();
            if (!fish.isEmpty()) {
                cat.setData(CAT_FED_TICKS.get(), config.getFedDurationTicks());
                cat.playSound(SoundEvents.GENERIC_EAT, 0.5F, cat.getVoicePitch());
            }
        }
    }

    /**
     * Returns the bowl block entity at bowlPos, or null.
     * Clears the cat's CAT_BOWL_POS if the block entity is missing (bowl was destroyed).
     */
    private AbstractCatBowlBlockEntity getBowlEntity(Cat cat, BlockPos bowlPos) {
        var be = cat.level().getBlockEntity(bowlPos);
        if (be instanceof AbstractCatBowlBlockEntity bowl) return bowl;
        // Bowl gone — clear stale attachment
        cat.setData(CAT_BOWL_POS.get(), Long.MIN_VALUE);
        return null;
    }

    /**
     * If the cat is unassigned and wanders within autoRadius of any cat bowl/feeding station
     * belonging to its owner, automatically associate it with that bowl.
     */
    private void tryAutoAssociate(Cat cat, double autoRadius) {
        BlockPos catPos = cat.blockPosition();
        int r = (int) Math.ceil(autoRadius);
        double radiusSq = autoRadius * autoRadius;

        for (BlockPos checkPos : BlockPos.betweenClosed(
                catPos.offset(-r, -r, -r), catPos.offset(r, r, r))) {
            if (!(cat.level().getBlockEntity(checkPos) instanceof AbstractCatBowlBlockEntity bowl)) {
                continue;
            }
            double distSq = cat.distanceToSqr(
                    checkPos.getX() + 0.5, checkPos.getY() + 0.5, checkPos.getZ() + 0.5);
            if (distSq <= radiusSq) {
                cat.setData(CAT_BOWL_POS.get(), checkPos.asLong());
                bowl.addCat(cat.getUUID());
                break;
            }
        }
    }

    // ---- CatGuardTargetGoal — proper AI target goal for guarding ----

    /**
     * Added to every Cat's targetSelector on join. When the cat is fed and has a bowl,
     * it selects the nearest Monster within guardRadius of the bowl as its attack target.
     * OcelotAttackGoal (vanilla priority 9 in goalSelector) then handles pathfinding + damage.
     */
    private static final class CatGuardTargetGoal extends TargetGoal {

        private final Cat cat;

        CatGuardTargetGoal(Cat cat) {
            super(cat, false, false);
            this.cat = cat;
            this.setFlags(EnumSet.of(Goal.Flag.TARGET));
        }

        @Override
        public boolean canUse() {
            if (!cat.isTame() || cat.getOwnerUUID() == null) return false;
            int fedTicks = cat.getData(CAT_FED_TICKS.get());
            if (fedTicks <= 0) return false;
            long bowlLong = cat.getData(CAT_BOWL_POS.get());
            if (bowlLong == Long.MIN_VALUE) return false;
            return findAndSetTarget(BlockPos.of(bowlLong));
        }

        @Override
        public boolean canContinueToUse() {
            int fedTicks = cat.getData(CAT_FED_TICKS.get());
            if (fedTicks <= 0) return false;
            LivingEntity target = cat.getTarget();
            return target != null && target.isAlive();
        }

        @Override
        public void start() {
            // Stand up so OcelotAttackGoal can run (SitWhenOrderedToGoal blocks it at priority 2)
            cat.setOrderedToSit(false);
            super.start();
        }

        @Override
        public void stop() {
            cat.setTarget(null);
            this.targetMob = null;
        }

        private boolean findAndSetTarget(BlockPos bowlPos) {
            double radius = INSTANCE != null ? INSTANCE.getConfig().getGuardRadius() : 64.0;
            AABB searchBox = new AABB(bowlPos).inflate(radius);
            List<Monster> hostiles = cat.level().getEntitiesOfClass(
                    Monster.class, searchBox, m -> !m.isDeadOrDying());
            if (hostiles.isEmpty()) return false;

            LivingEntity nearest = null;
            double nearestDistSq = Double.MAX_VALUE;
            for (Monster m : hostiles) {
                double d = cat.distanceToSqr(m);
                if (d < nearestDistSq) {
                    nearestDistSq = d;
                    nearest = m;
                }
            }
            if (nearest == null) return false;
            this.targetMob = nearest;
            cat.setTarget(nearest);
            return true;
        }
    }
}
