package net.geraldhofbauer.vanillaplusadditions.modules.glow_mushroom.block;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.MushroomBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.storage.loot.LootParams;

import java.util.List;

/**
 * The planted glow mushroom. Vanilla {@link MushroomBlock} in every respect but one: it drops
 * itself when broken.
 *
 * <p>That has to be done here rather than through a loot table, because this mod's datapack JSON
 * does not load reliably (project convention, see CLAUDE.md) - and a block without a loot table
 * silently yields nothing in survival, which is exactly how this was found.</p>
 */
public class GlowMushroomBlock extends MushroomBlock {

    /**
     * Creates the block.
     *
     * @param feature    The huge variant grown by bone meal
     * @param properties The block properties
     */
    public GlowMushroomBlock(ResourceKey<ConfiguredFeature<?, ?>> feature, Properties properties) {
        super(feature, properties);
    }

    @Override
    protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        return List.of(new ItemStack(this));
    }
}
