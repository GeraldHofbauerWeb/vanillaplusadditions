package net.geraldhofbauer.vanillaplusadditions.modules.inventory_link.client;

import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.registration.PonderPlugin;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.createmod.ponder.foundation.PonderIndex;
import net.geraldhofbauer.vanillaplusadditions.VanillaPlusAdditions;
import net.geraldhofbauer.vanillaplusadditions.modules.inventory_link.InventoryLinkModule;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * Ponder entry ("W" on the Inventory Linker) walking through linking two inventories, the per-end
 * modes, and the goggle overlay. Client-only; registered from the module's {@code onClientSetup},
 * i.e. only once Create and Create: Aeronautics are confirmed present.
 *
 * <p>NOTE: outside Ponder's editing mode all scene text comes from lang keys
 * ({@code vanillaplusadditions.ponder.inventory_linker_linking.header/text_N}) — the literals below
 * are editing-mode fallbacks. Keep the lang files in sync with the order of {@code text()}
 * calls.</p>
 */
public final class InventoryLinkerPonder {

    private InventoryLinkerPonder() {
    }

    public static void register() {
        PonderIndex.addPlugin(new Plugin());
    }

    private static final class Plugin implements PonderPlugin {
        @Override
        public String getModId() {
            return VanillaPlusAdditions.MODID;
        }

        @Override
        public void registerScenes(PonderSceneRegistrationHelper<ResourceLocation> helper) {
            helper.forComponents(InventoryLinkModule.INVENTORY_LINKER.getId())
                    .addStoryBoard("inventory_linker/linking", InventoryLinkerPonder::linkingScene);
        }
    }

    private static void linkingScene(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("inventory_linker_linking", "Linking inventories");
        scene.configureBasePlate(0, 0, 9);
        scene.showBasePlate();
        scene.idle(5);
        scene.world().showSection(util.select().everywhere(), Direction.DOWN);
        scene.idle(15);

        Selection vault = util.select().fromTo(1, 1, 3, 2, 2, 4);
        Selection barrel = util.select().position(6, 1, 4);
        BlockPos vaultTop = util.grid().at(2, 2, 4);
        BlockPos barrelPos = util.grid().at(6, 1, 4);
        ItemStack linker = new ItemStack(InventoryLinkModule.INVENTORY_LINKER.get());

        scene.overlay().showText(110)
                .text("The Inventory Linker connects two inventories directly. Items then travel "
                        + "between them on their own — no funnels, chutes or hoppers in between.")
                .attachKeyFrame()
                .pointAt(util.vector().topOf(vaultTop))
                .placeNearTarget();
        scene.idleSeconds(6);

        scene.overlay().showControls(util.vector().topOf(vaultTop), Pointing.DOWN, 40)
                .rightClick()
                .withItem(linker);
        scene.overlay().showOutline(PonderPalette.WHITE, "first", vault, 80);
        scene.idle(10);
        scene.overlay().showText(90)
                .text("Right-click the first inventory. The tool remembers your choice — a vault is "
                        + "always taken as a whole, no matter which of its blocks you pick.")
                .attachKeyFrame()
                .pointAt(util.vector().topOf(vaultTop))
                .placeNearTarget();
        scene.idleSeconds(6);

        scene.overlay().showControls(util.vector().topOf(barrelPos), Pointing.DOWN, 40)
                .rightClick()
                .withItem(linker);
        scene.overlay().showOutline(PonderPalette.GREEN, "first_linked", vault, 120);
        scene.overlay().showOutline(PonderPalette.GREEN, "second_linked", barrel, 120);
        scene.overlay().showBigLine(PonderPalette.GREEN,
                util.vector().topOf(vaultTop).add(0, 0.5, 0),
                util.vector().topOf(barrelPos).add(0, 0.5, 0), 120);
        scene.idle(10);
        scene.overlay().showText(100)
                .text("Right-click a second inventory to link them. Items start moving immediately, "
                        + "from the inventory you clicked first into the one you clicked second.")
                .attachKeyFrame()
                .pointAt(util.vector().topOf(barrelPos))
                .placeNearTarget();
        scene.idleSeconds(7);

        scene.overlay().showControls(util.vector().topOf(barrelPos), Pointing.DOWN, 50)
                .rightClick()
                .whileCTRL()
                .withItem(linker);
        scene.idle(10);
        scene.overlay().showText(110)
                .text("Ctrl + right-click a linked block to open its settings: each end can be set "
                        + "to input only, output only, or both. Links are removed there too.")
                .attachKeyFrame()
                .pointAt(util.vector().topOf(barrelPos))
                .placeNearTarget();
        scene.idleSeconds(7);

        scene.overlay().showOutline(PonderPalette.BLUE, "overview_first", vault, 110);
        scene.overlay().showOutline(PonderPalette.BLUE, "overview_second", barrel, 110);
        scene.overlay().showText(110)
                .text("Holding the linker or wearing Engineer's Goggles, look at any linked block to "
                        + "see all of its links highlighted — which is what makes cramped Aeronautics "
                        + "platforms readable.")
                .attachKeyFrame()
                .pointAt(util.vector().topOf(vaultTop))
                .placeNearTarget();
        scene.idleSeconds(7);

        scene.markAsFinished();
    }
}
