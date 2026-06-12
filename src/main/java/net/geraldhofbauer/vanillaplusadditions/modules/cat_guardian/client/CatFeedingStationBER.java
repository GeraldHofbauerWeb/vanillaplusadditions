package net.geraldhofbauer.vanillaplusadditions.modules.cat_guardian.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.geraldhofbauer.vanillaplusadditions.modules.cat_guardian.block.CatFeedingStationBlock;
import net.geraldhofbauer.vanillaplusadditions.modules.cat_guardian.blockentity.CatFeedingStationBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class CatFeedingStationBER implements BlockEntityRenderer<CatFeedingStationBlockEntity> {

    public CatFeedingStationBER(BlockEntityRendererProvider.Context context) { }

    @Override
    public void render(CatFeedingStationBlockEntity be, float partialTick,
                       PoseStack ps, MultiBufferSource buf, int light, int overlay) {

        ItemStackHandler inv = be.getInventory();

        // Active slot: its first fish is in the bowl, only show it in the tank if count >= 2.
        // All other slots always show their fish in the tank.
        int activeSlot = be.getActiveSlot();
        List<ItemStack> unique = new ArrayList<>();
        Set<Item> seen = new HashSet<>();
        for (int i = 0; i < inv.getSlots(); i++) {
            ItemStack s = inv.getStackInSlot(i);
            if (s.isEmpty()) continue;
            if (i == activeSlot && s.getCount() < 2) continue;
            if (seen.add(s.getItem())) unique.add(s);
        }

        // Rotate coordinate space to match block's facing direction.
        // Model is defined with eating side at north (z=0); blockstate rotates the geometry,
        // so we apply the same Y rotation here so fish positions track the model.
        Direction facing = be.getBlockState().getValue(CatFeedingStationBlock.FACING);
        float yRot = getFacingYRot(facing);

        ps.pushPose();
        ps.translate(0.5, 0.0, 0.5);
        ps.mulPose(Axis.YP.rotationDegrees(-yRot)); // blockstate uses CW, PoseStack uses CCW → negate
        ps.translate(-0.5, 0.0, -0.5);

        var ir = Minecraft.getInstance().getItemRenderer();

        // --- Fish in glass container (front half, z≈0.28 — interior of z=2..7) ---
        float[] ySlots = {
            0.52f, 0.64f, 0.76f, 0.88f
        };
        for (int i = 0; i < Math.min(unique.size(), ySlots.length); i++) {
            ps.pushPose();
            ps.translate(0.5, ySlots[i], 0.28);
            ps.mulPose(Axis.YP.rotationDegrees(45));
            ps.scale(0.28f, 0.28f, 0.28f);
            ir.renderStatic(unique.get(i), ItemDisplayContext.FIXED,
                    light, overlay, ps, buf, be.getLevel(), i);
            ps.popPose();
        }

        // --- Active fish lying flat in bowl (back half, z≈0.72 — interior of z=9..14) ---
        if (activeSlot >= 0) {
            ps.pushPose();
            ps.translate(0.5, 0.175, 0.72);
            ps.mulPose(Axis.XP.rotationDegrees(90));
            ps.scale(0.32f, 0.32f, 0.32f);
            ir.renderStatic(inv.getStackInSlot(activeSlot), ItemDisplayContext.FIXED,
                    light, overlay, ps, buf, be.getLevel(), 0);
            ps.popPose();
        }

        ps.popPose(); // undo facing rotation
    }

    /** Returns the blockstate Y rotation (CW from above) for the given facing direction. */
    private static float getFacingYRot(Direction facing) {
        return switch (facing) {
            case NORTH -> 0f;
            case EAST  -> 90f;
            case SOUTH -> 180f;
            case WEST  -> 270f;
            default    -> 0f;
        };
    }

    @Override
    public boolean shouldRenderOffScreen(CatFeedingStationBlockEntity be) {
        return false;
    }
}
