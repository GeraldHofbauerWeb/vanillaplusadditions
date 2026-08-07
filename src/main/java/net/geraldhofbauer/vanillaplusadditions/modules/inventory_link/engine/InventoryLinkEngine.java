package net.geraldhofbauer.vanillaplusadditions.modules.inventory_link.engine;

import com.mojang.logging.LogUtils;
import net.geraldhofbauer.vanillaplusadditions.modules.inventory_link.InventoryLinkModule;
import net.geraldhofbauer.vanillaplusadditions.modules.inventory_link.data.InventoryLink;
import net.geraldhofbauer.vanillaplusadditions.modules.inventory_link.data.InventoryLinkData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import org.slf4j.Logger;

import java.util.HashSet;
import java.util.Set;

/**
 * Moves items along every established link, once every {@code transfer_interval_ticks}.
 *
 * <p>Runs off the parent level's tick, which also covers Sable physics platforms: their blocks are
 * plot chunks of that very level and tick inside it.</p>
 */
public class InventoryLinkEngine {

    private static final Logger LOGGER = LogUtils.getLogger();

    private final InventoryLinkModule module;

    /** Links that threw during transfer; logged once, then skipped silently. */
    private final Set<String> faultedLinks = new HashSet<>();

    public InventoryLinkEngine(InventoryLinkModule module) {
        this.module = module;
    }

    public void tick(ServerLevel level) {
        int interval = module.getConfig().getTransferIntervalTicks();
        if (level.getGameTime() % interval != 0) {
            return;
        }
        InventoryLinkData data = InventoryLinkData.get(level);
        int budget = module.getConfig().getItemsPerTransfer();
        for (InventoryLink link : data.all()) {
            try {
                tickLink(level, data, link, budget);
            } catch (RuntimeException | LinkageError ex) {
                String key = link.first() + "/" + link.second();
                if (faultedLinks.add(key)) {
                    LOGGER.warn("Inventory link {} threw during transfer, skipping it from now on",
                            key, ex);
                }
            }
        }
    }

    private void tickLink(ServerLevel level, InventoryLinkData data, InventoryLink link, int budget) {
        // Unloaded endpoints are normal (a platform flew away): stay quiet and keep the link.
        if (!level.isLoaded(link.first()) || !level.isLoaded(link.second())) {
            return;
        }
        IItemHandler from = InventoryAccess.resolve(level, link.first());
        IItemHandler to = InventoryAccess.resolve(level, link.second());
        if (from == null || to == null) {
            // Loaded but no inventory left — the block was replaced or broken.
            data.remove(link.first(), link.second());
            LOGGER.info("Inventory link removed: no inventory at {} <-> {}",
                    link.first().toShortString(), link.second().toShortString());
            return;
        }

        boolean forward = link.firstMode().canOutput() && link.secondMode().canInput();
        boolean backward = link.secondMode().canOutput() && link.firstMode().canInput();
        if (forward) {
            // Click order wins when both ends allow both directions.
            move(from, to, budget);
        } else if (backward) {
            move(to, from, budget);
        }
    }

    /** Standard hopper-style pass: simulate, insert what fits, then extract exactly that much. */
    private static void move(IItemHandler source, IItemHandler target, int budget) {
        int remaining = budget;
        for (int slot = 0; slot < source.getSlots() && remaining > 0; slot++) {
            ItemStack simulated = source.extractItem(slot, remaining, true);
            if (simulated.isEmpty()) {
                continue;
            }
            ItemStack leftover = ItemHandlerHelper.insertItemStacked(target, simulated.copy(), false);
            int moved = simulated.getCount() - leftover.getCount();
            if (moved > 0) {
                source.extractItem(slot, moved, false);
                remaining -= moved;
            }
        }
    }

    /** Drops the fault memo for a link, so a re-created link gets a fresh chance. */
    public void forget(BlockPos first, BlockPos second) {
        faultedLinks.remove(first + "/" + second);
        faultedLinks.remove(second + "/" + first);
    }
}
