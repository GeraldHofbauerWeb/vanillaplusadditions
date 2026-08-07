package net.geraldhofbauer.vanillaplusadditions.modules.inventory_link.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Resolves the full extent of a multiblock inventory (a Create Item Vault or Fluid Tank) that a
 * given block belongs to.
 *
 * <p>Used for two things: highlighting the <em>whole</em> vault instead of the single linked block,
 * and answering "which links does this block have?" for any block of the vault, not just the one
 * that happened to be clicked.</p>
 *
 * <p>Membership is decided by Create's own {@code getControllerBE()} — reached by reflection,
 * because Create block entities extend {@code SmartBlockEntity}, whose hierarchy references the
 * Ponder API that is not on our compile classpath. Anything that does not answer that call is
 * treated as a plain single block.</p>
 */
public final class VaultMultiblockResolver {

    /** Safety net for pathological structures; a 6x6x6 vault has 216 blocks. */
    private static final int MAX_MEMBERS = 512;

    private VaultMultiblockResolver() {
    }

    /**
     * @return every block position belonging to the same multiblock as {@code pos}; always contains
     *         {@code pos} itself
     */
    public static Set<BlockPos> members(ServerLevel level, BlockPos pos) {
        Set<BlockPos> result = new LinkedHashSet<>();
        result.add(pos.immutable());
        try {
            BlockEntity start = level.getBlockEntity(pos);
            if (start == null) {
                return result;
            }
            Object controller = controllerOf(start);
            if (controller == null) {
                return result;
            }
            Block block = level.getBlockState(pos).getBlock();

            Deque<BlockPos> frontier = new ArrayDeque<>();
            frontier.add(pos.immutable());
            while (!frontier.isEmpty() && result.size() < MAX_MEMBERS) {
                BlockPos current = frontier.poll();
                for (Direction direction : Direction.values()) {
                    BlockPos next = current.relative(direction).immutable();
                    if (result.contains(next) || !level.isLoaded(next)) {
                        continue;
                    }
                    if (level.getBlockState(next).getBlock() != block) {
                        continue;
                    }
                    BlockEntity neighbour = level.getBlockEntity(next);
                    if (neighbour == null || controllerOf(neighbour) != controller) {
                        continue;
                    }
                    result.add(next);
                    frontier.add(next);
                }
            }
        } catch (RuntimeException | LinkageError ignored) {
            // Any surprise from a foreign block entity: fall back to the single block.
        }
        return result;
    }

    /** Bounding box covering the whole multiblock the given position belongs to. */
    public static AABB bounds(ServerLevel level, BlockPos pos) {
        return bounds(members(level, pos));
    }

    public static AABB bounds(Set<BlockPos> members) {
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (BlockPos member : members) {
            minX = Math.min(minX, member.getX());
            minY = Math.min(minY, member.getY());
            minZ = Math.min(minZ, member.getZ());
            maxX = Math.max(maxX, member.getX());
            maxY = Math.max(maxY, member.getY());
            maxZ = Math.max(maxZ, member.getZ());
        }
        return new AABB(minX, minY, minZ, maxX + 1.0, maxY + 1.0, maxZ + 1.0);
    }

    private static Object controllerOf(BlockEntity blockEntity) {
        try {
            return blockEntity.getClass().getMethod("getControllerBE").invoke(blockEntity);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ex) {
            return null;
        }
    }
}
