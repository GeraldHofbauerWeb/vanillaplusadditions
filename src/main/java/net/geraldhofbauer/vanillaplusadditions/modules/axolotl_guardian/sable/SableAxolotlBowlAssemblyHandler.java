package net.geraldhofbauer.vanillaplusadditions.modules.axolotl_guardian.sable;

import dev.ryanhcode.sable.api.SubLevelHelper;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.geraldhofbauer.vanillaplusadditions.modules.axolotl_guardian.AxolotlGuardianModule;
import net.geraldhofbauer.vanillaplusadditions.modules.axolotl_guardian.blockentity.AbstractAxolotlBowlBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.axolotl.Axolotl;

import java.util.List;
import java.util.UUID;

public final class SableAxolotlBowlAssemblyHandler {

    private SableAxolotlBowlAssemblyHandler() { }

    /**
     * Called after a bowl/station block moves during a Sable contraption assembly or disassembly.
     * On assembly (worldTo is a sublevel's inner level), teleports associated axolotls onto the
     * ship and updates their bowl position to the new block pos inside the sublevel.
     * On disassembly the existing self-healing in AxolotlGuardianModule handles re-association.
     */
    public static void afterMove(ServerLevel worldFrom, ServerLevel worldTo,
                                 BlockPos fromPos, BlockPos toPos) {
        // Find the ServerSubLevel whose inner level is worldTo — that's assembly
        ServerSubLevelContainer container = SubLevelContainer.getContainer(worldFrom);
        ServerSubLevel targetSubLevel = null;
        for (ServerSubLevel subLevel : container.getAllSubLevels()) {
            if (subLevel.getLevel() == worldTo) {
                targetSubLevel = subLevel;
                break;
            }
        }
        if (targetSubLevel == null) {
            return; // disassembly or unrelated move — normal behavior
        }

        // Block entity has already been moved to toPos in worldTo
        if (!(worldTo.getBlockEntity(toPos) instanceof AbstractAxolotlBowlBlockEntity bowl)) {
            return;
        }

        List<UUID> axolotlUUIDs = bowl.getAssociatedAxolotls();
        if (axolotlUUIDs.isEmpty()) {
            return;
        }

        long newBowlLong = toPos.asLong();
        for (UUID axolotlUUID : axolotlUUIDs) {
            Entity entity = worldFrom.getEntity(axolotlUUID);
            if (!(entity instanceof Axolotl axolotl) || !axolotl.isAlive()) {
                continue;
            }
            // Point the axolotl at the sublevel-local block position before teleporting
            axolotl.setData(AxolotlGuardianModule.AXOLOTL_BOWL_POS.get(), newBowlLong);
            SubLevelHelper.pushEntityLocal(targetSubLevel, axolotl);
        }
    }
}
