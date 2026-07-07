package net.geraldhofbauer.vanillaplusadditions.modules.cat_guardian.block;

import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.HashMap;
import java.util.Map;

/**
 * Visual skins for the cat feeding station, selected by placing a matching material item
 * into the station's skin slot (see {@code CatFeedingStationBlockEntity#getSkinInventory()}).
 * Each non-original skin has its own framed block model + texture set
 * ({@code cat_feeding_station_<skin>[_trim|_glass].png}); {@code ORIGINAL} keeps the classic
 * frameless model. The blockstate JSON enumerates skin x facing via multipart.
 */
public enum CatStationSkin implements StringRepresentable {
    ORIGINAL("original"),
    GEMUETLICHES_HOLZ("gemuetliches_holz"),
    STEIN_BISTRO("stein_bistro"),
    KIRSCHHOLZ_CAFE("kirschholz_cafe"),
    EDLE_DUNKELEICHE("edle_dunkeleiche"),
    BAMBUS_LOUNGE("bambus_lounge"),
    DEEPSLATE_MODERN("deepslate_modern"),
    HOEHLE("hoehle"),
    WIESENGARTEN("wiesengarten"),
    DORF("dorf"),
    STEINMETZ("steinmetz"),
    CREATE_ANDESIT("create_andesit"),
    WOLLE_WEISS("wolle_weiss"),
    WOLLE_HELLGRAU("wolle_hellgrau"),
    WOLLE_GRAU("wolle_grau"),
    WOLLE_SCHWARZ("wolle_schwarz"),
    WOLLE_BRAUN("wolle_braun"),
    WOLLE_ROT("wolle_rot"),
    WOLLE_ORANGE("wolle_orange"),
    WOLLE_GELB("wolle_gelb"),
    WOLLE_HELLGRUEN("wolle_hellgruen"),
    WOLLE_GRUEN("wolle_gruen"),
    WOLLE_TUERKIS("wolle_tuerkis"),
    WOLLE_HELLBLAU("wolle_hellblau"),
    WOLLE_BLAU("wolle_blau"),
    WOLLE_VIOLETT("wolle_violett"),
    WOLLE_MAGENTA("wolle_magenta"),
    WOLLE_ROSA("wolle_rosa");

    private static final Map<Item, CatStationSkin> ITEM_TO_SKIN = new HashMap<>();

    static {
        ITEM_TO_SKIN.put(Items.COPPER_INGOT, DEEPSLATE_MODERN);
        ITEM_TO_SKIN.put(Items.GRASS_BLOCK, WIESENGARTEN);
        ITEM_TO_SKIN.put(Items.OAK_PLANKS, GEMUETLICHES_HOLZ);
        ITEM_TO_SKIN.put(Items.SPRUCE_PLANKS, GEMUETLICHES_HOLZ);
        ITEM_TO_SKIN.put(Items.STONE_BRICKS, STEINMETZ);
        ITEM_TO_SKIN.put(Items.ANDESITE, CREATE_ANDESIT);
        ITEM_TO_SKIN.put(Items.POLISHED_ANDESITE, CREATE_ANDESIT);
        ITEM_TO_SKIN.put(Items.BAMBOO, BAMBUS_LOUNGE);
        ITEM_TO_SKIN.put(Items.BAMBOO_PLANKS, BAMBUS_LOUNGE);
        ITEM_TO_SKIN.put(Items.CHERRY_PLANKS, KIRSCHHOLZ_CAFE);
        ITEM_TO_SKIN.put(Items.DEEPSLATE, HOEHLE);
        ITEM_TO_SKIN.put(Items.COBBLED_DEEPSLATE, HOEHLE);
        ITEM_TO_SKIN.put(Items.DEEPSLATE_BRICKS, HOEHLE);
        ITEM_TO_SKIN.put(Items.POLISHED_DEEPSLATE, HOEHLE);
        ITEM_TO_SKIN.put(Items.BRICKS, DORF);
        ITEM_TO_SKIN.put(Items.GOLD_BLOCK, EDLE_DUNKELEICHE);
        ITEM_TO_SKIN.put(Items.STONE, STEIN_BISTRO);
        ITEM_TO_SKIN.put(Items.SMOOTH_STONE, STEIN_BISTRO);
        ITEM_TO_SKIN.put(Items.WHITE_WOOL, WOLLE_WEISS);
        ITEM_TO_SKIN.put(Items.LIGHT_GRAY_WOOL, WOLLE_HELLGRAU);
        ITEM_TO_SKIN.put(Items.GRAY_WOOL, WOLLE_GRAU);
        ITEM_TO_SKIN.put(Items.BLACK_WOOL, WOLLE_SCHWARZ);
        ITEM_TO_SKIN.put(Items.BROWN_WOOL, WOLLE_BRAUN);
        ITEM_TO_SKIN.put(Items.RED_WOOL, WOLLE_ROT);
        ITEM_TO_SKIN.put(Items.ORANGE_WOOL, WOLLE_ORANGE);
        ITEM_TO_SKIN.put(Items.YELLOW_WOOL, WOLLE_GELB);
        ITEM_TO_SKIN.put(Items.LIME_WOOL, WOLLE_HELLGRUEN);
        ITEM_TO_SKIN.put(Items.GREEN_WOOL, WOLLE_GRUEN);
        ITEM_TO_SKIN.put(Items.CYAN_WOOL, WOLLE_TUERKIS);
        ITEM_TO_SKIN.put(Items.LIGHT_BLUE_WOOL, WOLLE_HELLBLAU);
        ITEM_TO_SKIN.put(Items.BLUE_WOOL, WOLLE_BLAU);
        ITEM_TO_SKIN.put(Items.PURPLE_WOOL, WOLLE_VIOLETT);
        ITEM_TO_SKIN.put(Items.MAGENTA_WOOL, WOLLE_MAGENTA);
        ITEM_TO_SKIN.put(Items.PINK_WOOL, WOLLE_ROSA);
    }

    private final String serializedName;

    CatStationSkin(String serializedName) {
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
    public static CatStationSkin forItem(ItemStack stack) {
        if (stack.isEmpty()) {
            return ORIGINAL;
        }
        return ITEM_TO_SKIN.getOrDefault(stack.getItem(), ORIGINAL);
    }
}
