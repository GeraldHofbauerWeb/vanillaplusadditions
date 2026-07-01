package net.geraldhofbauer.vanillaplusadditions.core;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Central creative-tab registry for the mod.
 * Modules can register items here without touching vanilla tab wiring.
 */
public final class VanillaPlusCreativeTabs {
    private static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Vpa.NAMESPACE);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN_TAB = CREATIVE_TABS.register(
            "main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.vanillaplusadditions.main"))
                    .icon(() -> Items.TROPICAL_FISH.getDefaultInstance())
                    .build()
    );

    private static final List<Supplier<? extends ItemLike>> MAIN_TAB_ITEMS = new ArrayList<>();

    private VanillaPlusCreativeTabs() {
    }

    public static void register(IEventBus modEventBus) {
        CREATIVE_TABS.register(modEventBus);
        modEventBus.addListener(VanillaPlusCreativeTabs::onBuildCreativeModeTabContents);
    }

    public static void addToMainTab(Supplier<? extends ItemLike> itemSupplier) {
        MAIN_TAB_ITEMS.add(itemSupplier);
    }

    @SafeVarargs
    public static void addAllToMainTab(Supplier<? extends ItemLike>... itemSuppliers) {
        for (Supplier<? extends ItemLike> supplier : itemSuppliers) {
            MAIN_TAB_ITEMS.add(supplier);
        }
    }

    private static void onBuildCreativeModeTabContents(BuildCreativeModeTabContentsEvent event) {
        if (!event.getTabKey().location().equals(ResourceLocation.fromNamespaceAndPath(Vpa.NAMESPACE, "main"))) {
            return;
        }

        for (Supplier<? extends ItemLike> supplier : MAIN_TAB_ITEMS) {
            event.accept(supplier.get());
        }
    }
}

