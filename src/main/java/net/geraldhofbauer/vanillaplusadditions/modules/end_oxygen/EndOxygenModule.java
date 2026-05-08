package net.geraldhofbauer.vanillaplusadditions.modules.end_oxygen;

import com.simibubi.create.content.equipment.armor.BacktankUtil;
import net.geraldhofbauer.vanillaplusadditions.core.AbstractModule;
import net.geraldhofbauer.vanillaplusadditions.modules.end_oxygen.config.EndOxygenConfig;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingBreatheEvent;

import java.util.List;

/**
 * Module that removes oxygen from the End dimension.
 * Ported from handleOxygen logic.
 */
public class EndOxygenModule extends AbstractModule<EndOxygenModule, EndOxygenConfig> {
    public static final ResourceKey<DamageType> OUT_OF_OXYGEN = ResourceKey.create(
            Registries.DAMAGE_TYPE,
            ResourceLocation.fromNamespaceAndPath("create_gravity", "out_of_oxygen")
    );

    public static final TagKey<Item> BACKTANKS = TagKey.create(
            Registries.ITEM,
            ResourceLocation.fromNamespaceAndPath("create_gravity", "backtanks")
    );

    public static final TagKey<Item> DIVING_HELMETS = TagKey.create(
            Registries.ITEM,
            ResourceLocation.fromNamespaceAndPath("create_gravity", "diving_helmets")
    );

    private static final ResourceLocation AIR_LEVEL_LAYER = ResourceLocation.withDefaultNamespace("air_level");

    public EndOxygenModule() {
        super("end_oxygen",
                "End Oxygen",
                "Removes oxygen from the End dimension, requiring players to hold their breath or use gear.",
                EndOxygenConfig::new
        );
    }

    @Override
    protected void onInitialize() {
        NeoForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onRenderGuiLayer(RenderGuiLayerEvent.Pre event) {
        if (!isModuleEnabled()) {
            return;
        }

        if (event.getName().equals(AIR_LEVEL_LAYER)) {
            Player player = Minecraft.getInstance().player;
            if (player != null && player.level().dimension() == Level.END && !player.isCreative() && !player.isSpectator()) {
                // Force the air bubbles layer to render by making it think air is not full
                if (player.getAirSupply() >= player.getMaxAirSupply()) {
                    player.setAirSupply(player.getMaxAirSupply() - 1);
                }
            }
        }
    }

    @SubscribeEvent
    public void onRenderGuiLayerPost(RenderGuiLayerEvent.Post event) {
        if (!isModuleEnabled()) {
            return;
        }

        // After vanilla air bubbles (or instead of them if they are hidden), render Create's backtank overlay
        if (event.getName().equals(AIR_LEVEL_LAYER)) {
            renderBacktankOverlay(event.getGuiGraphics(), event.getPartialTick());
        }
    }

    private void renderBacktankOverlay(GuiGraphics guiGraphics, DeltaTracker partialTick) {
        if (FMLLoader.getDist().isClient()) {
            Minecraft mc = Minecraft.getInstance();
            Player player = mc.player;
            if (player != null && player.level().dimension() == Level.END && !player.isCreative() && !player.isSpectator()) {
                List<ItemStack> backtanks = BacktankUtil.getAllWithAir(player);
                if (!backtanks.isEmpty()) {
                    ItemStack backtank = backtanks.get(0);
                    int air = BacktankUtil.getAir(backtank);

                    // Replicating Create's RemainingAirOverlay.render logic for the "small indicator next to hotbar"
                    // guiWidth / 2 + 90 is the standard position
                    int x = guiGraphics.guiWidth() / 2 + 90;
                    int y = guiGraphics.guiHeight() - 53;

                    // Offset if fire resistant (Create does this)
                    if (backtank.has(net.minecraft.core.component.DataComponents.FIRE_RESISTANT)) {
                        y += 9;
                    }

                    guiGraphics.pose().pushPose();
                    guiGraphics.pose().translate(x, y, 0);

                    // Render the backtank icon
                    guiGraphics.renderItem(backtank, 0, 0);

                    // Format and render the air duration text in mm:ss format
                    int ticksLeft = Math.max(0, air - 1);
                    int depletionRate = getConfig().getBacktankDepletionRate();
                    int totalSeconds;
                    if (depletionRate > 0) {
                        // depletionRate is ticks per 1 unit of air.
                        // total ticks until empty = ticksLeft * depletionRate.
                        // total seconds = (ticksLeft * depletionRate) / tickRate.
                        float tickRate = player.level().tickRateManager().tickrate();
                        totalSeconds = (int) (((long) ticksLeft * depletionRate) / tickRate);
                    } else {
                        // If depletion is disabled, show a very large time or something indicating infinite?
                        // Create usually just shows the time based on its default rate if I recall, 
                        // but here we should probably show that it's not depleting or just a very high number.
                        // Let's stick to a large number to avoid UI breakage, or maybe just 99:59.
                        totalSeconds = 5999; 
                    }
                    int minutes = totalSeconds / 60;
                    int seconds = totalSeconds % 60;
                    if (minutes > 99) {
                        minutes = 99;
                        seconds = 59;
                    }
                    String timeLeft = String.format("%02d:%02d", minutes, seconds);
                    int color = 0xFFFFFF;

                    // Red flash if air is low
                    if (air < 60 && air % 2 == 0) {
                        color = 0xFF0000;
                    }

                    guiGraphics.drawString(mc.font, timeLeft, 16, 5, color);

                    guiGraphics.pose().popPose();
                }
            }
        }
    }

    @SubscribeEvent
    public void onLivingBreathe(LivingBreatheEvent event) {
        if (!isModuleEnabled()) {
            return;
        }

        if (event.getEntity() instanceof Player player) {
            // Only affect players in the End
            if (player.level().dimension() == Level.END) {
                // If the player is in creative or spectator mode, they don't need to breathe
                if (player.isCreative() || player.isSpectator()) {
                    return;
                }

                if (player.getAirSupply() < 1) {
                    player.setAirSupply(1);
                }

                List<ItemStack> backtanks = BacktankUtil.getAllWithAir(player);
                boolean hasDivingHelmet = player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD).is(DIVING_HELMETS);

                if (!backtanks.isEmpty() && (!getConfig().requiresFullSet() || hasDivingHelmet)) {
                    event.setCanBreathe(true);
                    event.setRefillAirAmount(1);

                    int depletionRate = getConfig().getBacktankDepletionRate();
                    if (depletionRate > 0 && player.tickCount % depletionRate == 0) {
                        BacktankUtil.consumeAir(player, backtanks.get(0), 1);
                    }
                } else {
                    event.setCanBreathe(false);

                    // Consumption logic: instead of recovering air, we reduce the speed of depletion
                    int baseInterval = getConfig().getAirConsumptionInterval();
                    int effectBonus = 0;

                    if (player.hasEffect(MobEffects.WATER_BREATHING)) {
                        MobEffectInstance effect = player.getEffect(MobEffects.WATER_BREATHING);
                        if (effect != null) {
                            effectBonus = (effect.getAmplifier() + 1) * getConfig().getWaterBreathingEffectIntervalBonus();
                        }
                    }

                    int totalInterval = baseInterval + effectBonus;

                    // Only consume 1 air every 'totalInterval' ticks
                    if (player.tickCount % totalInterval == 0) {
                        event.setConsumeAirAmount(1);
                    } else {
                        event.setConsumeAirAmount(0);
                    }
                }

                // Damage logic: if air is low, apply damage
                if (player.getAirSupply() <= 1 && player.tickCount % getConfig().getDamageTick() == 0) {
                    DamageSource outOfOxygen = player.level().damageSources().source(OUT_OF_OXYGEN);
                    player.hurt(outOfOxygen, getConfig().getOutOfAirDamage());
                }
            }
        }
    }

    private boolean isClientPlayer(Player player) {
        return FMLLoader.getDist().isClient() && player == Minecraft.getInstance().player;
    }
}
