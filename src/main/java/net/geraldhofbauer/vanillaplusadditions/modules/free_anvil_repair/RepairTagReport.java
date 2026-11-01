package net.geraldhofbauer.vanillaplusadditions.modules.free_anvil_repair;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.BiPredicate;

/**
 * The report behind {@code /vparepairtags}: every damageable item in the running game and every item
 * that repairs it on the anvil.
 * <p>
 * The {@code is_repairable_with_*} item tags ship with vanilla's repairs, this mod's own gear and
 * the extra materials. Other mods decide their repairs in code ({@code Item.isValidRepairItem}), often
 * per item and sometimes per config, so no file in their jar says what repairs what. Asking the game
 * is the only reliable source: this tries every registered item against every damageable one, which
 * also catches tag-based materials such as planks, because the tags are bound by then.
 */
final class RepairTagReport {

    static final String FILE = "vanillaplusadditions/repair_report.json";

    record Result(Path file, int damageable, int withMaterial) {
    }

    private RepairTagReport() {
    }

    /**
     * Writes the report to {@link #FILE} in the game directory.
     *
     * @param extraMaterial the module's configured extra materials, counted like vanilla ones
     * @return where it went and how many items it covered
     * @throws IOException if the file cannot be written
     */
    static Result write(BiPredicate<ItemStack, ItemStack> extraMaterial) throws IOException {
        List<Item> items = new ArrayList<>();
        List<ItemStack> materials = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) {
            if (item != Items.AIR) {
                items.add(item);
                materials.add(new ItemStack(item));
            }
        }

        Map<String, List<String>> repairs = new TreeMap<>();
        List<String> withoutMaterial = new ArrayList<>();
        int damageable = 0;
        for (Item item : items) {
            ItemStack stack = new ItemStack(item);
            if (!stack.isDamageableItem()) {
                continue;
            }
            damageable++;
            List<String> found = new ArrayList<>();
            for (ItemStack material : materials) {
                if (item.isValidRepairItem(stack, material) || extraMaterial.test(stack, material)) {
                    found.add(BuiltInRegistries.ITEM.getKey(material.getItem()).toString());
                }
            }
            String id = BuiltInRegistries.ITEM.getKey(item).toString();
            if (found.isEmpty()) {
                withoutMaterial.add(id);
            } else {
                repairs.put(id, found);
            }
        }

        JsonObject root = new JsonObject();
        JsonObject repairJson = new JsonObject();
        repairs.forEach((id, found) -> {
            JsonArray array = new JsonArray();
            found.forEach(array::add);
            repairJson.add(id, array);
        });
        root.add("repairs", repairJson);
        JsonArray none = new JsonArray();
        withoutMaterial.stream().sorted().forEach(none::add);
        root.add("no_repair_material", none);

        Path file = FMLPaths.GAMEDIR.get().resolve(FILE);
        Files.createDirectories(file.getParent());
        Files.writeString(file, new GsonBuilder().setPrettyPrinting().create().toJson(root));
        return new Result(file, damageable, repairs.size());
    }
}
