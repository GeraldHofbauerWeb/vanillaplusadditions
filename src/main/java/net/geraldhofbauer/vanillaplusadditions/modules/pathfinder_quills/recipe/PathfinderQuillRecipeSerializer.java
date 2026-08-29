package net.geraldhofbauer.vanillaplusadditions.modules.pathfinder_quills.recipe;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.RecipeSerializer;

/**
 * Hand-written serializer for {@link PathfinderQuillRecipe} — modeled on vanilla's
 * {@code SimpleCraftingRecipeSerializer}, which only carries a {@link CraftingBookCategory} and
 * so can't be reused directly here: this recipe also needs a {@code biomeId} field to round-trip
 * to the client (recipe book / JEI preview).
 */
public class PathfinderQuillRecipeSerializer implements RecipeSerializer<PathfinderQuillRecipe> {

    private static final MapCodec<PathfinderQuillRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                    CraftingBookCategory.CODEC.fieldOf("category").forGetter(PathfinderQuillRecipe::category),
                    Codec.STRING.fieldOf("biome").forGetter(PathfinderQuillRecipe::biomeId))
            .apply(instance, PathfinderQuillRecipe::new));

    private static final StreamCodec<RegistryFriendlyByteBuf, PathfinderQuillRecipe> STREAM_CODEC = StreamCodec.composite(
            CraftingBookCategory.STREAM_CODEC, PathfinderQuillRecipe::category,
            ByteBufCodecs.STRING_UTF8, PathfinderQuillRecipe::biomeId,
            PathfinderQuillRecipe::new);

    @Override
    public MapCodec<PathfinderQuillRecipe> codec() {
        return CODEC;
    }

    @Override
    public StreamCodec<RegistryFriendlyByteBuf, PathfinderQuillRecipe> streamCodec() {
        return STREAM_CODEC;
    }
}
