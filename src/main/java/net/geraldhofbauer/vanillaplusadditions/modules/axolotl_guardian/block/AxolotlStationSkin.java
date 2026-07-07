package net.geraldhofbauer.vanillaplusadditions.modules.axolotl_guardian.block;

import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.HashMap;
import java.util.Map;

/**
 * Visual skins for the axolotl feeding station, selected by placing a matching material item
 * into the station's skin slot (see {@code AxolotlFeedingStationBlockEntity#getSkinInventory()}).
 * Each non-original skin has its own framed block model + texture set
 * ({@code axolotl_feeding_station_<skin>[_trim|_glass].png}); {@code ORIGINAL} keeps the classic
 * frameless model. The blockstate JSON enumerates skin x facing via multipart.
 */
public enum AxolotlStationSkin implements StringRepresentable {
    ORIGINAL("original"),
    PRISMARIN_AQUARIUM("prismarin_aquarium"),
    KUPFER_LABOR("kupfer_labor"),
    KORALLENRIFF("korallenriff"),
    WIRRWALD("wirrwald"),
    AMETHYST_GROTTE("amethyst_grotte"),
    FROGLIGHT_STRAND("froglight_strand"),
    KORALLENGARTEN("korallengarten"),
    OZEANMONUMENT("ozeanmonument"),
    LAGUNE("lagune"),
    KORALLE_BLAU("koralle_blau"),
    KORALLE_ROSA("koralle_rosa"),
    KORALLE_LILA("koralle_lila"),
    KORALLE_ROT("koralle_rot"),
    KORALLE_GELB("koralle_gelb");

    private static final Map<Item, AxolotlStationSkin> ITEM_TO_SKIN = new HashMap<>();

    static {
        ITEM_TO_SKIN.put(Items.COPPER_INGOT, KUPFER_LABOR);
        ITEM_TO_SKIN.put(Items.PRISMARINE, PRISMARIN_AQUARIUM);
        ITEM_TO_SKIN.put(Items.PRISMARINE_BRICKS, OZEANMONUMENT);
        ITEM_TO_SKIN.put(Items.PEARLESCENT_FROGLIGHT, FROGLIGHT_STRAND);
        ITEM_TO_SKIN.put(Items.OCHRE_FROGLIGHT, FROGLIGHT_STRAND);
        ITEM_TO_SKIN.put(Items.VERDANT_FROGLIGHT, FROGLIGHT_STRAND);
        ITEM_TO_SKIN.put(Items.AMETHYST_BLOCK, AMETHYST_GROTTE);
        ITEM_TO_SKIN.put(Items.AMETHYST_SHARD, AMETHYST_GROTTE);
        ITEM_TO_SKIN.put(Items.WARPED_PLANKS, WIRRWALD);
        ITEM_TO_SKIN.put(Items.SAND, LAGUNE);
        ITEM_TO_SKIN.put(Items.SEA_PICKLE, KORALLENGARTEN);
        ITEM_TO_SKIN.put(Items.SEA_LANTERN, KORALLENRIFF);
        ITEM_TO_SKIN.put(Items.TUBE_CORAL_BLOCK, KORALLE_BLAU);
        ITEM_TO_SKIN.put(Items.TUBE_CORAL, KORALLE_BLAU);
        ITEM_TO_SKIN.put(Items.TUBE_CORAL_FAN, KORALLE_BLAU);
        ITEM_TO_SKIN.put(Items.BRAIN_CORAL_BLOCK, KORALLE_ROSA);
        ITEM_TO_SKIN.put(Items.BRAIN_CORAL, KORALLE_ROSA);
        ITEM_TO_SKIN.put(Items.BRAIN_CORAL_FAN, KORALLE_ROSA);
        ITEM_TO_SKIN.put(Items.BUBBLE_CORAL_BLOCK, KORALLE_LILA);
        ITEM_TO_SKIN.put(Items.BUBBLE_CORAL, KORALLE_LILA);
        ITEM_TO_SKIN.put(Items.BUBBLE_CORAL_FAN, KORALLE_LILA);
        ITEM_TO_SKIN.put(Items.FIRE_CORAL_BLOCK, KORALLE_ROT);
        ITEM_TO_SKIN.put(Items.FIRE_CORAL, KORALLE_ROT);
        ITEM_TO_SKIN.put(Items.FIRE_CORAL_FAN, KORALLE_ROT);
        ITEM_TO_SKIN.put(Items.HORN_CORAL_BLOCK, KORALLE_GELB);
        ITEM_TO_SKIN.put(Items.HORN_CORAL, KORALLE_GELB);
        ITEM_TO_SKIN.put(Items.HORN_CORAL_FAN, KORALLE_GELB);
    }

    private final String serializedName;

    AxolotlStationSkin(String serializedName) {
        this.serializedName = serializedName;
    }

    @Override
    public String getSerializedName() {
        return serializedName;
    }

    /** True if this item selects a (non-original) skin when placed in the skin slot. */
    public static boolean isSkinItem(ItemStack stack) {
        return !stack.isEmpty() && ITEM_TO_SKIN.containsKey(stack.getItem());
    }

    /** Skin for the given slot content; empty or unknown items map to {@link #ORIGINAL}. */
    public static AxolotlStationSkin forItem(ItemStack stack) {
        if (stack.isEmpty()) {
            return ORIGINAL;
        }
        return ITEM_TO_SKIN.getOrDefault(stack.getItem(), ORIGINAL);
    }
}
