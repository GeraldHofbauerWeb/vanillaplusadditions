package net.geraldhofbauer.vanillaplusadditions.modules.mystical_cat.spot;

import net.geraldhofbauer.vanillaplusadditions.modules.mystical_cat.MysticalCatModule;
import net.geraldhofbauer.vanillaplusadditions.modules.mystical_cat.entity.MysticalCatEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.levelgen.Heightmap;

import javax.annotation.Nullable;

/**
 * Builds a cat spot: a small mossy pad with a carpet the cat sleeps on and a couple of lit candles
 * for ambience, then spawns the sleeping {@link MysticalCatEntity} on it and records the spot in
 * {@link MysticalCatSpotData}. The layout is purely decorative — the mini-games place and restore
 * all of their own gameplay blocks, so a spot is not required for a game to run.
 */
public final class SpotLayoutBuilder {

    private SpotLayoutBuilder() {
    }

    /**
     * Decorates the spot around the surface at {@code x,z} and spawns the sleeping cat.
     *
     * @return the spawned cat, or null if it could not be created.
     */
    @Nullable
    public static MysticalCatEntity buildSpot(ServerLevel level, int x, int z) {
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        BlockPos center = new BlockPos(x, y, z);

        // 3×3 mossy pad one block below the standing surface.
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                level.setBlock(new BlockPos(x + dx, y - 1, z + dz), Blocks.MOSS_BLOCK.defaultBlockState(), 3);
            }
        }
        // Carpet the cat rests on, plus two lit candles at opposite corners.
        level.setBlock(center, Blocks.PURPLE_CARPET.defaultBlockState(), 3);
        level.setBlock(new BlockPos(x - 1, y, z - 1),
                Blocks.CANDLE.defaultBlockState().setValue(CandleBlock.LIT, true), 3);
        level.setBlock(new BlockPos(x + 1, y, z + 1),
                Blocks.CANDLE.defaultBlockState().setValue(CandleBlock.LIT, true), 3);

        MysticalCatEntity cat = MysticalCatModule.MYSTICAL_CAT.get().create(level);
        if (cat == null) {
            return null;
        }
        cat.moveTo(x + 0.5, y, z + 0.5, level.getRandom().nextFloat() * 360f, 0f);
        cat.setSpotCenter(center);
        cat.setSleepingPose();
        level.addFreshEntity(cat);

        MysticalCatSpotData.get(level).addSpot(center);
        return cat;
    }
}
