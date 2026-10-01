package net.geraldhofbauer.vanillaplusadditions.modules.pocket_crafting.config;

import net.geraldhofbauer.vanillaplusadditions.core.AbstractModuleConfig;
import net.geraldhofbauer.vanillaplusadditions.modules.pocket_crafting.PocketCraftingModule;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

/**
 * Configuration for the Pocket Crafting module.
 */
public class PocketCraftingConfig extends AbstractModuleConfig<PocketCraftingModule, PocketCraftingConfig> {

    private ModConfigSpec.BooleanValue inventoryClickEnabled;
    private ModConfigSpec.BooleanValue showTooltip;
    private ModConfigSpec.BooleanValue requireEmptyCarried;
    private ModConfigSpec.BooleanValue requireTableWhileOpen;
    private ModConfigSpec.BooleanValue returnToInventoryOnClose;
    private ModConfigSpec.ConfigValue<List<? extends String>> conflictingMods;

    public PocketCraftingConfig(PocketCraftingModule module) {
        super(module);
    }

    @Override
    protected void buildModuleSpecificConfig(ModConfigSpec.Builder builder) {
        inventoryClickEnabled = builder
                .comment("Whether right-clicking a crafting table in your inventory opens the grid. Turning this "
                        + "off leaves the module loaded - and with it the server side that answers the request - "
                        + "but takes away the trigger, which is what you want if another mod claims the same click.")
                .define("inventory_click_enabled", true);

        showTooltip = builder
                .comment("Add a \"Right-click to open\" line to a crafting table's tooltip. Only shown "
                        + "in the survival inventory, which is the one place the click actually does "
                        + "anything - a hint that appears where it does not work is worse than none.")
                .define("show_tooltip", true);

        requireEmptyCarried = builder
                .comment("Only open the grid when the mouse is not carrying an item. Two reasons: with something "
                        + "on the cursor you almost never mean \"open a window\", and it keeps us out of the one "
                        + "branch where Mouse Tweaks arms its right-click drag.")
                .define("require_empty_carried", true);

        requireTableWhileOpen = builder
                .comment("Close the grid again as soon as no crafting table is left in the inventory. Off by "
                        + "default: the crafting grid is not part of your inventory, so putting the table into the "
                        + "grid as an ingredient would read as \"gone\" and close the screen mid-craft. Nothing is "
                        + "lost either way - the grid is always emptied back into the inventory on close.")
                .define("require_table_while_open", false);

        returnToInventoryOnClose = builder
                .comment("Pressing Escape or the inventory key goes back to the inventory instead of straight into "
                        + "the game. Only applies to closing it yourself: a close forced by the server never runs "
                        + "through the screen at all.")
                .define("return_to_inventory_on_close", true);

        conflictingMods = builder
                .comment("Mod ids that switch the inventory trigger off when present. Left empty on purpose - "
                        + "nothing in this pack collides. The known candidate is 'iteminteractions' (the library "
                        + "behind Easy Shulker Boxes), which hooks the same mouse event on container screens. This "
                        + "is a list rather than a hard-coded set so a collision can be defused without a release.")
                .defineList(
                        "conflicting_mods",
                        List.of(),
                        () -> "iteminteractions",
                        o -> o instanceof String s && !s.isBlank()
                );
    }

    /**
     * Whether right-clicking a crafting table in the inventory opens the grid.
     *
     * @return true if the inventory trigger is armed (default true)
     */
    public boolean isInventoryClickEnabled() {
        return inventoryClickEnabled == null || inventoryClickEnabled.get();
    }

    /**
     * Whether a crafting table's tooltip advertises the grid.
     *
     * @return true if the hint line should be shown (default true)
     */
    public boolean showsTooltip() {
        return showTooltip == null || showTooltip.get();
    }

    /**
     * Whether the trigger only fires while the mouse carries nothing.
     *
     * @return true if an empty cursor is required (default true)
     */
    public boolean requiresEmptyCarried() {
        return requireEmptyCarried == null || requireEmptyCarried.get();
    }

    /**
     * Whether the grid closes once no crafting table is left in the inventory.
     *
     * @return true if a table must stay in the inventory (default false)
     */
    public boolean requiresTableWhileOpen() {
        return requireTableWhileOpen != null && requireTableWhileOpen.get();
    }

    /**
     * Whether closing the grid returns to the inventory screen.
     *
     * @return true if the inventory should reopen (default true)
     */
    public boolean returnsToInventoryOnClose() {
        return returnToInventoryOnClose == null || returnToInventoryOnClose.get();
    }

    /**
     * Mod ids that disable the inventory trigger when installed.
     *
     * @return the configured mod ids, never null
     */
    public List<? extends String> getConflictingMods() {
        return conflictingMods == null ? List.of() : conflictingMods.get();
    }
}
