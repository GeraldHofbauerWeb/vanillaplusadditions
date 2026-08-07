package net.geraldhofbauer.vanillaplusadditions.modules.inventory_link.engine;

import net.geraldhofbauer.vanillaplusadditions.modules.inventory_link.compat.SimulatedAugerAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * Resolves the item handler of a link endpoint. Deliberately re-resolved on every transfer pass
 * instead of caching a {@code BlockCapabilityCache}: Sable plot chunks invalidate capabilities
 * through their own chunk-holder path, so a long-lived cache is not known to be safe there.
 */
public final class InventoryAccess {

    private InventoryAccess() {
    }

    /**
     * @return the block's item handler, or null if it has no reachable inventory
     */
    public static IItemHandler resolve(ServerLevel level, BlockPos pos) {
        // Sideless first — Create vaults, barrels, chests and docking connectors all answer here.
        IItemHandler handler = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, null);
        if (handler != null) {
            return handler;
        }
        // Auger shafts only expose the faces perpendicular to their axis.
        for (Direction direction : Direction.values()) {
            handler = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, direction);
            if (handler != null) {
                return handler;
            }
        }
        // Auger cogs expose nothing at all — reach their inventory directly.
        return SimulatedAugerAccess.wrap(level.getBlockEntity(pos));
    }

    public static boolean hasInventory(ServerLevel level, BlockPos pos) {
        return resolve(level, pos) != null;
    }
}
