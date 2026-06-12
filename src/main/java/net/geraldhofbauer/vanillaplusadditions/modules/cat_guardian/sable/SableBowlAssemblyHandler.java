package net.geraldhofbauer.vanillaplusadditions.modules.cat_guardian.sable;

import dev.ryanhcode.sable.api.SubLevelHelper;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.geraldhofbauer.vanillaplusadditions.modules.cat_guardian.CatGuardianModule;
import net.geraldhofbauer.vanillaplusadditions.modules.cat_guardian.blockentity.AbstractCatBowlBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Cat;

import java.util.List;
import java.util.UUID;

public final class SableBowlAssemblyHandler {

    private SableBowlAssemblyHandler() {}

    /**
     * Called after a bowl/station block moves during a Sable contraption assembly or disassembly.
     * On assembly (worldTo is a sublevel's inner level), teleports associated cats onto the ship
     * and updates their bowl position to the new block pos inside the sublevel.
     * On disassembly the existing self-healing in CatGuardianModule handles re-association.
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
        if (!(worldTo.getBlockEntity(toPos) instanceof AbstractCatBowlBlockEntity bowl)) {
            return;
        }

        List<UUID> catUUIDs = bowl.getAssociatedCats();
        if (catUUIDs.isEmpty()) {
            return;
        }

        long newBowlLong = toPos.asLong();
        for (UUID catUUID : catUUIDs) {
            Entity entity = worldFrom.getEntity(catUUID);
            if (!(entity instanceof Cat cat) || !cat.isAlive()) {
                continue;
            }
            // Point the cat at the sublevel-local block position before teleporting
            cat.setData(CatGuardianModule.CAT_BOWL_POS.get(), newBowlLong);
            cat.setOrderedToSit(false);
            SubLevelHelper.pushEntityLocal(targetSubLevel, cat);
        }
    }
}
