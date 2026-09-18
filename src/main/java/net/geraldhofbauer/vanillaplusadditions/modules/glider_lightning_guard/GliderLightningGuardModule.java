package net.geraldhofbauer.vanillaplusadditions.modules.glider_lightning_guard;

import net.geraldhofbauer.vanillaplusadditions.core.AbstractModule;
import net.geraldhofbauer.vanillaplusadditions.modules.glider_lightning_guard.compat.CuriosGate;
import net.geraldhofbauer.vanillaplusadditions.modules.glider_lightning_guard.compat.GliderCuriosAccess;
import net.geraldhofbauer.vanillaplusadditions.modules.glider_lightning_guard.config.GliderLightningGuardConfig;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityStruckByLightningEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/**
 * Glider Lightning Guard Module
 *
 * <p>The Gliders mod makes thunderstorms dangerous on purpose: glide through rain long enough and
 * {@code GliderUtil.lightningLogic} spawns a real bolt straight onto the player. Without the copper
 * upgrade the strike then marks the glider as <em>broken</em> — the icon turns into the charred
 * {@code damaged_glider} sprite, gliding is off, and the only way back is a workbench-made
 * Reinforced Paper of the matching tier, which most players do not carry.
 *
 * <p>This keeps the danger and drops the total loss: after the strike the broken flag is taken back
 * off and paid for in durability instead (a quarter of the bar by default). The glider stops one
 * point short of breaking, so there is always something left to repair, and the bolt itself — the
 * damage, the fire, the fright — is left entirely alone.
 *
 * <p>Nothing here is compiled against the Gliders mod. The broken flag is its
 * {@code vc_gliders:broken} data component, looked up in the registry at runtime, and gliders are
 * recognised by their registry namespace.
 */
public class GliderLightningGuardModule
        extends AbstractModule<GliderLightningGuardModule, GliderLightningGuardConfig> {

    private static final String GLIDER_MOD_ID = "vc_gliders";
    private static final String GLIDER_ITEM_PREFIX = "paraglider";
    private static final ResourceLocation BROKEN_COMPONENT_ID =
            ResourceLocation.fromNamespaceAndPath(GLIDER_MOD_ID, "broken");

    /** Gliders that were already broken when the bolt hit, so they are not repaired for free. */
    private final Set<ItemStack> brokenBeforeStrike = Collections.newSetFromMap(new IdentityHashMap<>());

    private DataComponentType<Boolean> brokenComponent;
    private boolean brokenComponentResolved;

    public GliderLightningGuardModule() {
        super("glider_lightning_guard",
                "Glider Lightning Guard",
                "A lightning strike costs the Gliders paraglider durability instead of wrecking it",
                GliderLightningGuardConfig::new
        );
    }

    @Override
    protected boolean shouldInitialize() {
        return ModList.get().isLoaded(GLIDER_MOD_ID);
    }

    @Override
    protected void onInitialize() {
        NeoForge.EVENT_BUS.register(this);
        getLogger().info("Glider Lightning Guard module initialized - lightning no longer wrecks paragliders");
    }

    /**
     * Runs before the Gliders mod and notes which gliders were already broken.
     *
     * @param event the lightning strike
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void rememberBrokenGliders(EntityStruckByLightningEvent event) {
        brokenBeforeStrike.clear();
        if (!isModuleEnabled() || !(event.getEntity() instanceof Player player)) {
            return;
        }
        for (ItemStack glider : findGliders(player)) {
            if (isBroken(glider)) {
                brokenBeforeStrike.add(glider);
            }
        }
    }

    /**
     * Runs after the Gliders mod and takes back the damage it did to an intact glider.
     *
     * @param event the lightning strike
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void undoLightningBreak(EntityStruckByLightningEvent event) {
        if (!isModuleEnabled() || !(event.getEntity() instanceof Player player)) {
            return;
        }
        for (ItemStack glider : findGliders(player)) {
            if (!isBroken(glider) || brokenBeforeStrike.contains(glider)) {
                continue;
            }
            glider.set(requireBrokenComponent(), false);
            chargeDurability(glider);
            getLogger().debug("Lightning damaged {} instead of breaking it", glider.getItem());
        }
        brokenBeforeStrike.clear();
    }

    /** Charges the strike to the durability bar, always stopping one point short of breaking. */
    private void chargeDurability(ItemStack glider) {
        int max = glider.getMaxDamage();
        double share = getConfig().getDurabilityCostValue();
        if (max <= 0 || share <= 0.0D) {
            return;
        }
        int cost = Math.max(1, (int) Math.round(max * share));
        glider.setDamageValue(Math.min(max - 1, glider.getDamageValue() + cost));
    }

    /** The gliders a player is wearing: chest armor slot first, then the Curios slots. */
    private List<ItemStack> findGliders(LivingEntity entity) {
        List<ItemStack> gliders = new ArrayList<>(2);
        ItemStack chest = entity.getItemBySlot(EquipmentSlot.CHEST);
        if (isGlider(chest)) {
            gliders.add(chest);
        }
        if (CuriosGate.isLoaded()) {
            gliders.addAll(GliderCuriosAccess.findWorn(entity, GliderLightningGuardModule::isGlider));
        }
        return gliders;
    }

    private static boolean isGlider(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return GLIDER_MOD_ID.equals(id.getNamespace()) && id.getPath().startsWith(GLIDER_ITEM_PREFIX);
    }

    private boolean isBroken(ItemStack stack) {
        DataComponentType<Boolean> type = requireBrokenComponent();
        return type != null && Boolean.TRUE.equals(stack.get(type));
    }

    /**
     * Looks up the Gliders mod's {@code broken} component the first time it is needed. Doing it
     * lazily rather than at startup keeps us independent of mod load order.
     */
    @SuppressWarnings("unchecked")
    private DataComponentType<Boolean> requireBrokenComponent() {
        if (!brokenComponentResolved) {
            brokenComponentResolved = true;
            brokenComponent = (DataComponentType<Boolean>)
                    BuiltInRegistries.DATA_COMPONENT_TYPE.get(BROKEN_COMPONENT_ID);
            if (brokenComponent == null) {
                getLogger().warn("Component {} not found - the Gliders mod may have renamed it; "
                        + "lightning protection is inactive", BROKEN_COMPONENT_ID);
            }
        }
        return brokenComponent;
    }
}
