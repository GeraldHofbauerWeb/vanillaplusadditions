package net.geraldhofbauer.vanillaplusadditions.modules.free_anvil_repair;

import net.geraldhofbauer.vanillaplusadditions.core.AbstractModule;
import net.geraldhofbauer.vanillaplusadditions.modules.free_anvil_repair.config.FreeAnvilRepairConfig;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.StringUtil;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AnvilUpdateEvent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Free Anvil Repair Module
 * <p>
 * Makes pure repairs at the anvil cost no XP levels. Only plain repairing is free:
 * <ul>
 *     <li>Material repair — a damaged item plus its repair material (e.g. diamond pickaxe + diamonds)</li>
 *     <li>Sacrifice repair — two items of the same type where the sacrifice carries no enchantments</li>
 *     <li>Extra materials (configurable, Quark-style) — e.g. netherite gear + diamonds,
 *         Create diving gear + copper/diamonds</li>
 * </ul>
 * Everything else keeps vanilla costs: combining enchanted items, applying enchanted books and
 * renaming (also renaming while repairing). Because the repair is computed by this module, even
 * items whose accumulated prior-work penalty made them "Too Expensive!" in vanilla can be repaired.
 * <p>
 * Implementation: {@link AnvilUpdateEvent} fires before vanilla computes the anvil result; when a
 * pure repair is detected, the module computes the vanilla repair result itself and sets cost 0
 * (otherwise it leaves the event untouched so vanilla runs). Vanilla's
 * {@code AnvilMenu.mayPickup} refuses zero-cost results, so {@code AnvilMenuFreeRepairMixin}
 * allows pickup when the cost is 0 and this module produced an output (vanilla never creates a
 * non-empty result with cost 0).
 */
public class FreeAnvilRepairModule
        extends AbstractModule<FreeAnvilRepairModule, FreeAnvilRepairConfig> {

    private static FreeAnvilRepairModule instance;

    /** Parsed {@code extra_repair_materials} entries, cached against the raw config list. */
    private Map<Item, Set<Item>> extraRepairMaterials = Map.of();
    private List<? extends String> extraRepairMaterialsSource;

    public FreeAnvilRepairModule() {
        super("free_anvil_repair",
                "Free Anvil Repair",
                "Makes pure anvil repairs (repair material or unenchanted sacrifice item) cost no XP levels",
                FreeAnvilRepairConfig::new
        );
        instance = this;
    }

    @Override
    protected void onInitialize() {
        NeoForge.EVENT_BUS.register(this);

        getLogger().info("Free Anvil Repair module initialized - pure repairs no longer cost XP");
    }

    /**
     * Mixin hook for {@code AnvilMenuFreeRepairMixin}: whether a zero-cost anvil result may be
     * picked up. Vanilla never produces a non-empty result with cost 0, so this only affects
     * outputs created by this module.
     *
     * @return true if the module is active and free pickups should be allowed
     */
    public static boolean allowFreePickup() {
        FreeAnvilRepairModule module = instance;
        return module != null && module.isModuleEnabled();
    }

    /**
     * Detects pure repairs and replaces them with a zero-cost result. Leaves the event untouched
     * (→ vanilla behavior and costs) for every other anvil operation.
     */
    @SubscribeEvent
    public void onAnvilUpdate(AnvilUpdateEvent event) {
        if (!isModuleEnabled()) {
            return;
        }

        ItemStack left = event.getLeft();
        ItemStack right = event.getRight();
        if (left.isEmpty() || right.isEmpty() || !left.isDamageableItem() || left.getDamageValue() <= 0) {
            return;
        }
        if (isRenaming(event.getName(), left)) {
            return; // renaming (even combined with a repair) keeps vanilla costs
        }

        if (left.getItem().isValidRepairItem(left, right) || isExtraRepairMaterial(left, right)) {
            if (getConfig().isFreeMaterialRepairValue()) {
                applyMaterialRepair(event, left, right);
            }
        } else if (isPlainSacrifice(left, right)) {
            if (getConfig().isFreeCombineRepairValue()) {
                applySacrificeRepair(event, left, right);
            }
        }
    }

    /**
     * A name was sent that would change the item's custom name — either setting a different one
     * or clearing an existing one (empty string).
     */
    private boolean isRenaming(String name, ItemStack left) {
        if (name == null) {
            return false;
        }
        if (StringUtil.isBlank(name)) {
            return left.has(DataComponents.CUSTOM_NAME);
        }
        return !name.equals(left.getHoverName().getString());
    }

    /**
     * Whether the configured {@code extra_repair_materials} accept the right stack as repair
     * material for the left item (Quark-style, e.g. netherite gear + diamonds). These
     * combinations are unknown to vanilla, so the module computes the repair itself — which
     * also means mods that patch the vanilla anvil code path (like Quark's diamond repair)
     * never run and can't charge XP for it.
     */
    private boolean isExtraRepairMaterial(ItemStack left, ItemStack right) {
        List<? extends String> source = getConfig().getExtraRepairMaterialsValue();
        if (!source.equals(extraRepairMaterialsSource)) {
            extraRepairMaterials = parseExtraRepairMaterials(source);
            extraRepairMaterialsSource = source;
        }
        Set<Item> materials = extraRepairMaterials.get(left.getItem());
        return materials != null && materials.contains(right.getItem());
    }

    private Map<Item, Set<Item>> parseExtraRepairMaterials(List<? extends String> entries) {
        Map<Item, Set<Item>> parsed = new HashMap<>();
        for (String entry : entries) {
            int eq = entry.indexOf('=');
            if (eq <= 0 || eq >= entry.length() - 1) {
                getLogger().warn("Ignoring malformed extra_repair_materials entry '{}'", entry);
                continue;
            }
            ResourceLocation itemId = ResourceLocation.tryParse(entry.substring(0, eq).trim());
            ResourceLocation materialId = ResourceLocation.tryParse(entry.substring(eq + 1).trim());
            if (itemId == null || materialId == null
                    || !BuiltInRegistries.ITEM.containsKey(itemId)
                    || !BuiltInRegistries.ITEM.containsKey(materialId)) {
                if (getConfig().shouldDebugLog()) {
                    getLogger().debug("Skipping extra_repair_materials entry '{}' (item not installed)", entry);
                }
                continue;
            }
            parsed.computeIfAbsent(BuiltInRegistries.ITEM.get(itemId), item -> new HashSet<>())
                    .add(BuiltInRegistries.ITEM.get(materialId));
        }
        return parsed;
    }

    /**
     * Sacrifice of the same item type that carries no enchantments (regular or stored) — combining
     * it transfers nothing, so the operation is a pure durability merge.
     */
    private boolean isPlainSacrifice(ItemStack left, ItemStack right) {
        return right.is(left.getItem())
                && right.isDamageableItem()
                && !right.has(DataComponents.STORED_ENCHANTMENTS)
                && EnchantmentHelper.getEnchantmentsForCrafting(right).isEmpty();
    }

    /**
     * Vanilla material repair (AnvilMenu.createResult): each material unit repairs up to 25% of
     * max durability; consumed units become the material cost. Level cost is set to 0.
     */
    private void applyMaterialRepair(AnvilUpdateEvent event, ItemStack left, ItemStack right) {
        ItemStack result = left.copy();
        int damagePerUnit = Math.min(result.getDamageValue(), result.getMaxDamage() / 4);
        if (damagePerUnit <= 0) {
            return; // nothing repairable (vanilla shows no result either)
        }

        int unitsUsed = 0;
        while (damagePerUnit > 0 && unitsUsed < right.getCount()) {
            result.setDamageValue(result.getDamageValue() - damagePerUnit);
            unitsUsed++;
            damagePerUnit = Math.min(result.getDamageValue(), result.getMaxDamage() / 4);
        }

        applyPriorWorkPenalty(result, right);
        event.setOutput(result);
        event.setCost(0);
        event.setMaterialCost(unitsUsed);

        if (getConfig().shouldDebugLog()) {
            getLogger().debug("Free material repair: {} using {}x {}",
                    result.getItem(), unitsUsed, right.getItem());
        }
    }

    /**
     * Vanilla sacrifice repair (AnvilMenu.createResult): merged durability is
     * {@code leftRemaining + rightRemaining + 12% of max}; the sacrifice is consumed entirely
     * (material cost 0 consumes the whole right stack). Level cost is set to 0.
     */
    private void applySacrificeRepair(AnvilUpdateEvent event, ItemStack left, ItemStack right) {
        int leftRemaining = left.getMaxDamage() - left.getDamageValue();
        int rightRemaining = right.getMaxDamage() - right.getDamageValue();
        int merged = leftRemaining + rightRemaining + left.getMaxDamage() * 12 / 100;
        int newDamage = Math.max(left.getMaxDamage() - merged, 0);
        if (newDamage >= left.getDamageValue()) {
            return; // no improvement — leave it to vanilla (which shows no result)
        }

        ItemStack result = left.copy();
        result.setDamageValue(newDamage);
        applyPriorWorkPenalty(result, right);
        event.setOutput(result);
        event.setCost(0);

        if (getConfig().shouldDebugLog()) {
            getLogger().debug("Free sacrifice repair: {} ({} -> {} damage)",
                    result.getItem(), left.getDamageValue(), newDamage);
        }
    }

    /**
     * Applies vanilla's prior-work penalty bump (repair cost doubles) only if configured; by
     * default free repairs leave the penalty untouched so later enchanting doesn't get pricier.
     */
    private void applyPriorWorkPenalty(ItemStack result, ItemStack right) {
        if (!getConfig().isIncreasePriorWorkPenaltyValue()) {
            return;
        }
        int base = Math.max(
                result.getOrDefault(DataComponents.REPAIR_COST, 0),
                right.getOrDefault(DataComponents.REPAIR_COST, 0)
        );
        result.set(DataComponents.REPAIR_COST, AnvilMenu.calculateIncreasedRepairCost(base));
    }
}
