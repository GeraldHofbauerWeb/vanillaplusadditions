package net.geraldhofbauer.vanillaplusadditions.modules.battle_dogs;

import net.geraldhofbauer.vanillaplusadditions.VanillaPlusAdditions;
import net.geraldhofbauer.vanillaplusadditions.core.AbstractModule;
import net.geraldhofbauer.vanillaplusadditions.core.AbstractModuleConfig;
import net.geraldhofbauer.vanillaplusadditions.core.VanillaPlusCreativeTabs;
import net.geraldhofbauer.vanillaplusadditions.modules.battle_dogs.item.WolfArmorItem;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingEquipmentChangeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public class BattleDogsModule extends AbstractModule<
        BattleDogsModule,
        AbstractModuleConfig.DefaultModuleConfig<BattleDogsModule>> {

    private static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(VanillaPlusAdditions.MODID);

    private static final ResourceLocation ARMOR_MODIFIER_ID =
            ResourceLocation.fromNamespaceAndPath(VanillaPlusAdditions.MODID, "wolf_armor_bonus");

    public static final DeferredItem<WolfArmorItem> WOLF_ARMOR_IRON =
            ITEMS.register("wolf_armor_iron",
                    () -> new WolfArmorItem(WolfArmorItem.Tier.IRON, new Item.Properties()));

    public static final DeferredItem<WolfArmorItem> WOLF_ARMOR_GOLD =
            ITEMS.register("wolf_armor_gold",
                    () -> new WolfArmorItem(WolfArmorItem.Tier.GOLD, new Item.Properties()));

    public static final DeferredItem<WolfArmorItem> WOLF_ARMOR_DIAMOND =
            ITEMS.register("wolf_armor_diamond",
                    () -> new WolfArmorItem(WolfArmorItem.Tier.DIAMOND, new Item.Properties()));

    public static final DeferredItem<WolfArmorItem> WOLF_ARMOR_NETHERITE =
            ITEMS.register("wolf_armor_netherite",
                    () -> new WolfArmorItem(WolfArmorItem.Tier.NETHERITE, new Item.Properties().fireResistant()));

    public BattleDogsModule() {
        super(
                "battle_dogs",
                "Battle Dogs",
                "Adds iron, gold, diamond, and netherite armor for tamed wolves.",
                AbstractModuleConfig::createDefault
        );
    }

    @Override
    protected void onInitialize() {
        ITEMS.register(getModEventBus());

        VanillaPlusCreativeTabs.addAllToMainTab(
                WOLF_ARMOR_IRON, WOLF_ARMOR_GOLD, WOLF_ARMOR_DIAMOND, WOLF_ARMOR_NETHERITE);

        NeoForge.EVENT_BUS.register(this);

        getLogger().info("Battle Dogs module initialized");
    }

    @SubscribeEvent
    public void onPlayerInteractEntity(PlayerInteractEvent.EntityInteract event) {
        if (!isModuleEnabled()) {
            return;
        }
        if (!(event.getTarget() instanceof Wolf wolf)) {
            return;
        }
        if (!wolf.isTame() || !wolf.isOwnedBy(event.getEntity())) {
            return;
        }

        ItemStack stack = event.getItemStack();
        boolean isClient = event.getLevel().isClientSide();

        if (stack.getItem() instanceof WolfArmorItem && wolf.getBodyArmorItem().isEmpty() && !wolf.isBaby()) {
            if (!isClient) {
                wolf.setBodyArmorItem(stack.copyWithCount(1));
                if (!event.getEntity().isCreative()) {
                    stack.shrink(1);
                }
                wolf.playSound(SoundEvents.ARMOR_EQUIP_WOLF.value());
            }
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.sidedSuccess(isClient));
            return;
        }

        if (stack.canPerformAction(ItemAbilities.SHEARS_REMOVE_ARMOR)
                && wolf.getBodyArmorItem().getItem() instanceof WolfArmorItem) {
            if (!isClient) {
                ItemStack armor = wolf.getBodyArmorItem();
                wolf.setBodyArmorItem(ItemStack.EMPTY);
                wolf.spawnAtLocation(armor);
                wolf.playSound(SoundEvents.ARMOR_UNEQUIP_WOLF);
                EquipmentSlot handSlot = event.getHand() == InteractionHand.MAIN_HAND
                        ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND;
                stack.hurtAndBreak(1, event.getEntity(), handSlot);
            }
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.sidedSuccess(isClient));
        }
    }

    @SubscribeEvent
    public void onEquipmentChange(LivingEquipmentChangeEvent event) {
        if (!isModuleEnabled()) {
            return;
        }
        if (!(event.getEntity() instanceof Wolf wolf)) {
            return;
        }
        if (event.getSlot() != EquipmentSlot.BODY) {
            return;
        }

        updateArmorAttribute(wolf, event.getTo());
    }

    private void updateArmorAttribute(Wolf wolf, ItemStack stack) {
        var attr = wolf.getAttribute(Attributes.ATTACK_DAMAGE);
        if (attr == null) {
            return;
        }
        attr.removeModifier(ARMOR_MODIFIER_ID);
        if (stack.getItem() instanceof WolfArmorItem wolfArmor) {
            attr.addPermanentModifier(new AttributeModifier(
                    ARMOR_MODIFIER_ID,
                    wolfArmor.getTier().getAttackBonus(),
                    AttributeModifier.Operation.ADD_VALUE));
        }
    }
}
