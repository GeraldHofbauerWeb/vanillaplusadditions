package net.geraldhofbauer.vanillaplusadditions.modules.end_conduit.client;

import net.geraldhofbauer.vanillaplusadditions.VanillaPlusAdditions;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.model.Material;
import net.minecraft.resources.ResourceLocation;

/**
 * The End Conduit's own sprite materials — violet/endstone-gold-tinted copies of the vanilla
 * conduit textures. They live under {@code textures/block/end_conduit/} so the vanilla blocks
 * atlas ({@code TextureAtlas.LOCATION_BLOCKS}) stitches them automatically (its {@code block/}
 * directory source scans every namespace), exactly like the vanilla conduit sprites. Referenced by
 * both {@link EndConduitBER} (placed block) and {@link EndConduitItemRenderer} (inventory item), so
 * block and item look identical.
 */
public final class EndConduitTextures {

    private EndConduitTextures() {
    }

    private static Material material(String name) {
        return new Material(TextureAtlas.LOCATION_BLOCKS,
                ResourceLocation.fromNamespaceAndPath(VanillaPlusAdditions.MODID, "block/end_conduit/" + name));
    }

    public static final Material SHELL = material("base");
    public static final Material CAGE = material("cage");
    public static final Material WIND = material("wind");
    public static final Material VERTICAL_WIND = material("wind_vertical");
    public static final Material OPEN_EYE = material("open_eye");
    public static final Material CLOSED_EYE = material("closed_eye");
}
