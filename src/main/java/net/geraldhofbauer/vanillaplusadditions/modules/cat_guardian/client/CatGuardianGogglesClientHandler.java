package net.geraldhofbauer.vanillaplusadditions.modules.cat_guardian.client;

import com.simibubi.create.content.equipment.goggles.GogglesItem;
import net.geraldhofbauer.vanillaplusadditions.VanillaPlusAdditions;
import net.geraldhofbauer.vanillaplusadditions.modules.cat_guardian.CatGuardianModule;
import net.geraldhofbauer.vanillaplusadditions.modules.cat_guardian.blockentity.AbstractCatBowlBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

import java.util.ArrayList;
import java.util.List;

@EventBusSubscriber(modid = VanillaPlusAdditions.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME)
public class CatGuardianGogglesClientHandler {

    private static final TagKey<Item> ARM_GOGGLES_TAG = TagKey.create(
            Registries.ITEM, ResourceLocation.fromNamespaceAndPath(VanillaPlusAdditions.MODID, "arm_goggles"));

    private static List<Component> activeTooltip = null;

    public static void onClientTick(Minecraft mc) {
        activeTooltip = null;

        if (mc.player == null || mc.level == null || mc.screen != null) {
            return;
        }

        if (!isWearingGoggles(mc.player)) {
            return;
        }

        HitResult hitResult = mc.hitResult;
        if (hitResult instanceof BlockHitResult blockHitResult) {
            BlockEntity be = mc.level.getBlockEntity(blockHitResult.getBlockPos());
            if (be instanceof AbstractCatBowlBlockEntity bowl) {
                int count = bowl.getAssociatedCats().size();
                int max = CatGuardianModule.getMaxCatsPerStation();

                List<Component> tooltip = new ArrayList<>();
                tooltip.add(Component.translatable("gui.vanillaplusadditions.cat_guardian.associated_cats", count, max));
                activeTooltip = tooltip;
            }
        }
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        if (activeTooltip != null) {
            int x = event.getGuiGraphics().guiWidth() / 2 + 10;
            int y = event.getGuiGraphics().guiHeight() / 2 + 10;
            event.getGuiGraphics().renderComponentTooltip(Minecraft.getInstance().font, activeTooltip, x, y);
        }
    }

    private static boolean isWearingGoggles(Player player) {
        if (GogglesItem.isWearingGoggles(player)) {
            return true;
        }
        return player.getItemBySlot(EquipmentSlot.HEAD).is(ARM_GOGGLES_TAG);
    }
}
