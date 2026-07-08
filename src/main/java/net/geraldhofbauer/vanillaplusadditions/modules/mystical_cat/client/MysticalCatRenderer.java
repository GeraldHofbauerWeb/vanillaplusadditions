package net.geraldhofbauer.vanillaplusadditions.modules.mystical_cat.client;

import net.geraldhofbauer.vanillaplusadditions.VanillaPlusAdditions;
import net.minecraft.client.renderer.entity.CatRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

/**
 * Renders the Mystical Cat with a custom starry texture. Extends the vanilla {@link CatRenderer}
 * (which bakes {@code ModelLayers.CAT}) so EMF / Fresh Animations geometry replacements still apply;
 * only the texture is overridden.
 */
public class MysticalCatRenderer extends CatRenderer {

    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            VanillaPlusAdditions.MODID, "textures/entity/mystical_cat/mystical_cat.png");

    public MysticalCatRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(net.minecraft.world.entity.animal.Cat entity) {
        return TEXTURE;
    }
}
