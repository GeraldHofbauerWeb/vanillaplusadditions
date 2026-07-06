package net.geraldhofbauer.vanillaplusadditions.modules.waystone_amethyst_repair;

import net.geraldhofbauer.vanillaplusadditions.core.AbstractModule;
import net.geraldhofbauer.vanillaplusadditions.core.ModuleManager;
import net.geraldhofbauer.vanillaplusadditions.modules.waystone_amethyst_repair.config.WaystoneAmethystRepairConfig;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.StringUtil;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AnvilUpdateEvent;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Waystone Amethyst Repair Module
 * <p>
 * Lets players repair the Waystones <em>Warp Stone</em> (a damageable item whose durability is
 * spent on teleporting) with <strong>amethyst</strong> in an anvil — a combination vanilla anvils
 * don't recognise. Each material unit restores a configurable share of the item's max durability
 * (25% by default, matching vanilla material repairs).
 * <p>
 * Cost: the repair costs XP levels like a normal anvil material repair, <em>unless</em> the
 * {@code free_anvil_repair} module is enabled — then it is free (0 levels), mirroring how that
 * module frees regular repairs. This is deliberately coupled to {@code free_anvil_repair}: its
 * {@code AnvilMenuFreeRepairMixin} only permits picking up a zero-cost anvil result while that
 * module is active, so this module only ever produces a zero-cost result under the same condition.
 * <p>
 * Soft dependency: the target item and materials are resolved by registry id at runtime, so no
 * Waystones jar is needed to compile and the module is simply inert when Waystones isn't installed.
 */
public class WaystoneAmethystRepairModule
        extends AbstractModule<WaystoneAmethystRepairModule, WaystoneAmethystRepairConfig> {

    private static final String FREE_REPAIR_MODULE_ID = "free_anvil_repair";

    /** Resolved target item, cached against the config string it was parsed from. */
    private Item targetItem;
    private String targetItemSource;

    /** Resolved repair materials, cached against the config list they were parsed from. */
    private Set<Item> repairMaterials = Set.of();
    private List<? extends String> repairMaterialsSource;

    public WaystoneAmethystRepairModule() {
        super("waystone_amethyst_repair",
                "Waystone Amethyst Repair",
                "Repair the Waystones Warp Stone with amethyst in an anvil "
                        + "(free while the Free Anvil Repair module is enabled)",
                WaystoneAmethystRepairConfig::new
        );
    }

    @Override
    protected void onInitialize() {
        NeoForge.EVENT_BUS.register(this);
        getLogger().info("Waystone Amethyst Repair module initialized - Warp Stone repairable with amethyst");
    }

    /**
     * Detects a Warp-Stone-plus-amethyst anvil combination and produces the repair result,
     * leaving the event untouched (→ vanilla behavior) for everything else.
     */
    @SubscribeEvent
    public void onAnvilUpdate(AnvilUpdateEvent event) {
        if (!isModuleEnabled()) {
            return;
        }

        ItemStack left = event.getLeft();
        ItemStack right = event.getRight();
        if (left.isEmpty() || right.isEmpty()
                || !left.isDamageableItem() || left.getDamageValue() <= 0) {
            return;
        }

        Item target = resolveTargetItem();
        if (target == null || !left.is(target) || !isRepairMaterial(right)) {
            return;
        }
        if (isRenaming(event.getName(), left)) {
            return; // renaming keeps vanilla behavior (repair the Warp Stone first, rename after)
        }

        applyRepair(event, left, right);
    }

    /**
     * Vanilla-style material repair: each unit restores {@code repair_percent_per_unit}% of max
     * durability; consumed units become the material cost. The XP level cost is the number of
     * units used — or 0 when the Free Anvil Repair module is enabled.
     */
    private void applyRepair(AnvilUpdateEvent event, ItemStack left, ItemStack right) {
        int perUnit = Math.max(1, left.getMaxDamage() * getConfig().getRepairPercentPerUnitValue() / 100);

        ItemStack result = left.copy();
        int unitsUsed = 0;
        while (result.getDamageValue() > 0 && unitsUsed < right.getCount()) {
            int repair = Math.min(result.getDamageValue(), perUnit);
            result.setDamageValue(result.getDamageValue() - repair);
            unitsUsed++;
        }
        if (unitsUsed == 0) {
            return; // nothing to repair
        }

        boolean free = ModuleManager.getInstance().isModuleEnabled(FREE_REPAIR_MODULE_ID);
        event.setOutput(result);
        event.setMaterialCost(unitsUsed);
        event.setCost(free ? 0 : unitsUsed);

        if (getConfig().shouldDebugLog()) {
            getLogger().debug("Warp Stone repair: {}x {} ({} -> {} damage), cost {}",
                    unitsUsed, right.getItem(), left.getDamageValue(), result.getDamageValue(),
                    free ? "FREE (free_anvil_repair on)" : unitsUsed);
        }
    }

    private boolean isRepairMaterial(ItemStack right) {
        List<? extends String> source = getConfig().getRepairMaterialsValue();
        if (!source.equals(repairMaterialsSource)) {
            repairMaterials = parseItems(source);
            repairMaterialsSource = source;
        }
        return repairMaterials.contains(right.getItem());
    }

    private Item resolveTargetItem() {
        String source = getConfig().getTargetItemValue();
        if (!source.equals(targetItemSource)) {
            ResourceLocation id = ResourceLocation.tryParse(source);
            targetItem = (id != null && BuiltInRegistries.ITEM.containsKey(id))
                    ? BuiltInRegistries.ITEM.get(id) : null;
            targetItemSource = source;
            if (targetItem == null && getConfig().shouldDebugLog()) {
                getLogger().debug("Target item '{}' not installed - module inert", source);
            }
        }
        return targetItem;
    }

    private Set<Item> parseItems(List<? extends String> entries) {
        Set<Item> parsed = new HashSet<>();
        for (String entry : entries) {
            ResourceLocation id = ResourceLocation.tryParse(entry.trim());
            if (id != null && BuiltInRegistries.ITEM.containsKey(id)) {
                parsed.add(BuiltInRegistries.ITEM.get(id));
            } else if (getConfig().shouldDebugLog()) {
                getLogger().debug("Skipping repair material '{}' (item not installed)", entry);
            }
        }
        return parsed;
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
}
