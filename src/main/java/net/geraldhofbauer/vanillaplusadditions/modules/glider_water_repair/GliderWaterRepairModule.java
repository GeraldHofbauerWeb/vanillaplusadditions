package net.geraldhofbauer.vanillaplusadditions.modules.glider_water_repair;

import net.geraldhofbauer.vanillaplusadditions.core.AbstractModule;
import net.geraldhofbauer.vanillaplusadditions.modules.glider_water_repair.config.GliderWaterRepairConfig;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/**
 * Glider Water Repair Module
 *
 * <p>The Gliders mod makes thunderstorms dangerous on purpose: glide through rain long enough and
 * {@code GliderUtil.lightningLogic} drops a real bolt on the player. Without the copper upgrade the
 * strike marks the paraglider as <em>broken</em> — and that is meant to hurt, so this module leaves
 * it completely alone.
 *
 * <p>What it changes is the way back. Vanilla Gliders only accepts an anvil plus the Reinforced
 * Paper of that glider's exact tier — for an iron paraglider that is five leather, four paper and
 * eight iron ingots, which nobody carries on a flight. Meanwhile the broken glider wears the charred
 * {@code damaged_glider} sprite and looks for all the world like it is on fire, so the first thing
 * anyone tries is to throw it in the water.
 *
 * <p>Here, that works: a broken glider lying in water becomes usable again. Only the broken flag
 * clears — the durability it lost stays lost, so the strike still costs something and the Reinforced
 * Paper repair keeps its purpose.
 *
 * <p>Nothing here is compiled against the Gliders mod. The broken flag is its
 * {@code vc_gliders:broken} data component, looked up in the registry at runtime, and gliders are
 * recognised by their registry namespace.
 */
public class GliderWaterRepairModule
        extends AbstractModule<GliderWaterRepairModule, GliderWaterRepairConfig> {

    private static final String GLIDER_MOD_ID = "vc_gliders";
    private static final String GLIDER_ITEM_PREFIX = "paraglider";
    private static final int WATER_CHECK_INTERVAL = 20;
    private static final ResourceLocation BROKEN_COMPONENT_ID =
            ResourceLocation.fromNamespaceAndPath(GLIDER_MOD_ID, "broken");

    private DataComponentType<Boolean> brokenComponent;
    private boolean brokenComponentResolved;

    public GliderWaterRepairModule() {
        super("glider_water_repair",
                "Glider Water Repair",
                "A broken Gliders paraglider thrown into water becomes usable again",
                GliderWaterRepairConfig::new
        );
    }

    @Override
    protected boolean shouldInitialize() {
        return ModList.get().isLoaded(GLIDER_MOD_ID);
    }

    @Override
    protected void onInitialize() {
        NeoForge.EVENT_BUS.register(this);
        getLogger().info("Glider Water Repair module initialized - water frees a broken paraglider");
    }

    /**
     * Frees a broken glider that is lying in water.
     *
     * <p>Checked once a second per dropped item rather than every tick — the thing has to sink and
     * bob first anyway, and a second of delay is exactly the beat the hiss wants.
     *
     * @param event the entity tick, filtered down to dropped items in water
     */
    @SubscribeEvent
    public void onItemEntityTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof ItemEntity itemEntity)
                || itemEntity.tickCount % WATER_CHECK_INTERVAL != 0
                || !isModuleEnabled()
                || !(itemEntity.level() instanceof ServerLevel level)
                || !itemEntity.isInWater()) {
            return;
        }
        ItemStack stack = itemEntity.getItem();
        if (!isGlider(stack) || !isBroken(stack)) {
            return;
        }

        stack.set(requireBrokenComponent(), false);
        level.playSound(null, itemEntity.getX(), itemEntity.getY(), itemEntity.getZ(),
                SoundEvents.FIRE_EXTINGUISH, SoundSource.NEUTRAL, 0.7F, 1.2F);
        level.sendParticles(ParticleTypes.CLOUD, itemEntity.getX(), itemEntity.getY() + 0.2,
                itemEntity.getZ(), 8, 0.2, 0.1, 0.2, 0.0);
        getLogger().debug("Water freed {} from its broken state", stack.getItem());
    }

    private static boolean isGlider(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return GLIDER_MOD_ID.equals(id.getNamespace()) && id.getPath().startsWith(GLIDER_ITEM_PREFIX);
    }

    private boolean isBroken(ItemStack stack) {
        DataComponentType<Boolean> type = requireBrokenComponent();
        return type != null && Boolean.TRUE.equals(stack.get(type));
    }

    /**
     * Looks up the Gliders mod's {@code broken} component the first time it is needed. Doing it
     * lazily rather than at startup keeps us independent of mod load order.
     */
    @SuppressWarnings("unchecked")
    private DataComponentType<Boolean> requireBrokenComponent() {
        if (!brokenComponentResolved) {
            brokenComponentResolved = true;
            brokenComponent = (DataComponentType<Boolean>)
                    BuiltInRegistries.DATA_COMPONENT_TYPE.get(BROKEN_COMPONENT_ID);
            if (brokenComponent == null) {
                getLogger().warn("Component {} not found - the Gliders mod may have renamed it; "
                        + "water repair is inactive", BROKEN_COMPONENT_ID);
            }
        }
        return brokenComponent;
    }
}
