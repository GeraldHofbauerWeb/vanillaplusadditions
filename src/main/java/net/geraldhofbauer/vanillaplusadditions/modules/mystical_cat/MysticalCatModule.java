package net.geraldhofbauer.vanillaplusadditions.modules.mystical_cat;

import com.mojang.serialization.Codec;
import net.geraldhofbauer.vanillaplusadditions.VanillaPlusAdditions;
import net.geraldhofbauer.vanillaplusadditions.core.AbstractModule;
import net.geraldhofbauer.vanillaplusadditions.core.VanillaPlusCreativeTabs;
import net.geraldhofbauer.vanillaplusadditions.modules.mystical_cat.client.MysticalCatClientHooks;
import net.geraldhofbauer.vanillaplusadditions.modules.mystical_cat.config.MysticalCatConfig;
import net.geraldhofbauer.vanillaplusadditions.modules.mystical_cat.entity.MysticalCatEntity;
import net.geraldhofbauer.vanillaplusadditions.modules.mystical_cat.command.MysticalCatCommand;
import net.geraldhofbauer.vanillaplusadditions.modules.mystical_cat.game.GameManager;
import net.geraldhofbauer.vanillaplusadditions.modules.mystical_cat.spot.SpotSpawner;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cat;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

/**
 * Mystical Cat module: open-world collectible mini-games. A special {@link MysticalCatEntity} sleeps
 * at generated cat spots; right-clicking it starts a random mini-game, and winning awards a
 * <em>Mystical Paw</em> collectible that can be traded back to any cat for rewards.
 */
public class MysticalCatModule extends AbstractModule<MysticalCatModule, MysticalCatConfig> {

    private static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, VanillaPlusAdditions.MODID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(VanillaPlusAdditions.MODID);
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, VanillaPlusAdditions.MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<MysticalCatEntity>> MYSTICAL_CAT =
            ENTITY_TYPES.register("mystical_cat",
                    () -> EntityType.Builder.of(MysticalCatEntity::new, MobCategory.MISC)
                            .sized(0.6F, 0.7F)
                            .clientTrackingRange(10)
                            .build(VanillaPlusAdditions.MODID + ":mystical_cat"));

    public static final DeferredItem<Item> MYSTICAL_PAW = ITEMS.register("mystical_paw",
            () -> new Item(new Item.Properties()
                    .rarity(Rarity.EPIC)
                    .component(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true)));

    public static final DeferredItem<DeferredSpawnEggItem> MYSTICAL_CAT_SPAWN_EGG =
            ITEMS.register("mystical_cat_spawn_egg",
                    () -> new DeferredSpawnEggItem(MYSTICAL_CAT, 0x2E1A47, 0xD9C6F2, new Item.Properties()));

    /** Lifetime count of mini-games this player has won (drives milestone messages). */
    public static final Supplier<AttachmentType<Integer>> GAMES_COMPLETED =
            ATTACHMENT_TYPES.register("mystical_games_completed",
                    () -> AttachmentType.<Integer>builder(() -> 0).serialize(Codec.INT).copyOnDeath().build());

    private static volatile boolean contentRegistered;
    private static MysticalCatModule instance;

    public MysticalCatModule() {
        super(
                "mystical_cat",
                "Mystical Cat",
                "A mysterious cat that sleeps at hidden spots and challenges you to open-world mini-games "
                        + "for a collectible reward.",
                MysticalCatConfig::new
        );
        instance = this;
    }

    public static boolean isContentRegistered() {
        return contentRegistered;
    }

    public static MysticalCatConfig config() {
        return instance != null ? instance.getConfig() : null;
    }

    public static boolean isActive() {
        return instance != null && instance.isModuleEnabled();
    }

    @Override
    protected void onInitialize() {
        ENTITY_TYPES.register(getModEventBus());
        ITEMS.register(getModEventBus());
        ATTACHMENT_TYPES.register(getModEventBus());

        getModEventBus().addListener(this::onEntityAttributeCreation);

        VanillaPlusCreativeTabs.addAllToMainTab(MYSTICAL_PAW, MYSTICAL_CAT_SPAWN_EGG);

        if (FMLEnvironment.dist == Dist.CLIENT) {
            getModEventBus().addListener(MysticalCatClientHooks::onRegisterRenderers);
        }

        NeoForge.EVENT_BUS.register(this);
        GameManager.init();
        SpotSpawner.init();
        contentRegistered = true;

        getLogger().info("Mystical Cat module initialized");
    }

    @SubscribeEvent
    public void onRegisterCommands(net.neoforged.neoforge.event.RegisterCommandsEvent event) {
        if (!isModuleEnabled()) {
            return;
        }
        MysticalCatCommand.register(event.getDispatcher());
    }

    // Mod-bus event: registered via getModEventBus().addListener in onInitialize (no @SubscribeEvent).
    public void onEntityAttributeCreation(EntityAttributeCreationEvent event) {
        event.put(
                MYSTICAL_CAT.get(),
                Cat.createAttributes()
                        .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D)
                        .build()
        );
    }

    /**
     * Right-click dispatch for a Mystical Cat. On the server it hands off to the {@link GameManager}
     * (active game → trade → start game); on the client it just acknowledges so vanilla never runs.
     */
    public static InteractionResult handleCatInteract(MysticalCatEntity cat, Player player, InteractionHand hand) {
        if (cat.level().isClientSide()) {
            return InteractionResult.sidedSuccess(true);
        }
        // A ghost-escort cat is scenery, not an interactable cat — never start a game on it.
        if (cat.isGhost() || !isActive() || !(player instanceof net.minecraft.server.level.ServerPlayer serverPlayer)) {
            return InteractionResult.sidedSuccess(false);
        }
        return GameManager.get().handleInteract(cat, serverPlayer, hand);
    }
}
