package net.geraldhofbauer.vanillaplusadditions.modules.overpacked_backpack_keys.compat;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;

import java.util.Optional;

/**
 * Curios-only helper for the Overpacked backpack keybinds: locates the giant backpack the player
 * wears (any color variant, matched by the {@code #overpacked:giant_backpacks} item tag) and writes
 * an updated stack back into the same Curios slot.
 *
 * <p>All Curios types are referenced only inside method bodies and are only reached when Curios is
 * present (Overpacked hard-requires Curios, and callers gate on {@link OverpackedGuiBridge#isAvailable()}).
 */
public final class CuriosBackpackAccess {

    static final boolean CURIOS_LOADED = ModList.get().isLoaded("curios");

    private static final TagKey<Item> GIANT_BACKPACKS = TagKey.create(
            Registries.ITEM, ResourceLocation.fromNamespaceAndPath("overpacked", "giant_backpacks"));

    private CuriosBackpackAccess() {
    }

    /**
     * A giant backpack the player is currently wearing, plus the Curios slot coordinates needed to
     * write it back.
     *
     * @param stack      the worn backpack stack (live reference)
     * @param identifier the Curios slot type identifier (e.g. {@code "back"})
     * @param index      the slot index within that type
     */
    public record Worn(ItemStack stack, String identifier, int index) {
    }

    /**
     * The first worn giant backpack, or empty if the player wears none (or Curios is absent).
     */
    public static Optional<Worn> findWorn(ServerPlayer player) {
        if (!CURIOS_LOADED) {
            return Optional.empty();
        }
        return CuriosApi.getCuriosInventory(player)
                .flatMap(inv -> inv.findFirstCurio(stack -> stack.is(GIANT_BACKPACKS)))
                .map(result -> new Worn(
                        result.stack(),
                        result.slotContext().identifier(),
                        result.slotContext().index()));
    }

    /**
     * True if the given Curios slot still holds a giant backpack (guards against the player having
     * swapped the slot out while the GUI was open).
     */
    public static boolean isGiantBackpackInSlot(ServerPlayer player, String identifier, int index) {
        if (!CURIOS_LOADED) {
            return false;
        }
        Optional<ICuriosItemHandler> inv = CuriosApi.getCuriosInventory(player);
        return inv.flatMap(h -> h.getStacksHandler(identifier))
                .map(sh -> sh.getStacks())
                .map(stacks -> index >= 0 && index < stacks.getSlots()
                        && stacks.getStackInSlot(index).is(GIANT_BACKPACKS))
                .orElse(false);
    }

    /**
     * Writes {@code stack} into the given Curios slot (syncs to the client).
     */
    public static void setWorn(ServerPlayer player, String identifier, int index, ItemStack stack) {
        if (!CURIOS_LOADED) {
            return;
        }
        CuriosApi.getCuriosInventory(player)
                .flatMap(h -> h.getStacksHandler(identifier))
                .ifPresent(sh -> {
                    if (index >= 0 && index < sh.getStacks().getSlots()) {
                        sh.getStacks().setStackInSlot(index, stack);
                    }
                });
    }
}
