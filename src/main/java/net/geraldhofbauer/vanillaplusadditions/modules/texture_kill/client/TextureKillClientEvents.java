package net.geraldhofbauer.vanillaplusadditions.modules.texture_kill.client;

import net.geraldhofbauer.vanillaplusadditions.core.Module;
import net.geraldhofbauer.vanillaplusadditions.core.ModuleManager;
import net.geraldhofbauer.vanillaplusadditions.modules.texture_kill.TextureKillModule;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.event.AddPackFindersEvent;

import java.util.Optional;

@EventBusSubscriber(bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class TextureKillClientEvents {
    private TextureKillClientEvents() { }

    @SubscribeEvent
    public static void onAddPackFinders(AddPackFindersEvent event) {
        if (event.getPackType() != PackType.CLIENT_RESOURCES) {
            return;
        }

        Module mod = ModuleManager.getInstance().getModule("texture_kill");
        if (!(mod instanceof TextureKillModule module) || !module.isModuleEnabled()) {
            return;
        }

        Pack pack = Pack.readMetaAndCreate(
            new PackLocationInfo(
                "vanillaplusadditions:texture_kill",
                Component.literal("VPA Texture Kill"),
                PackSource.BUILT_IN,
                Optional.empty()
            ),
            new TransparentTexturePack.Supplier(module.getConfig()),
            PackType.CLIENT_RESOURCES,
            new PackSelectionConfig(true, Pack.Position.TOP, false)
        );

        if (pack != null) {
            event.addRepositorySource(consumer -> consumer.accept(pack));
        }
    }

    @SubscribeEvent
    public static void onRegisterReloadListeners(RegisterClientReloadListenersEvent event) {
        Module mod = ModuleManager.getInstance().getModule("texture_kill");
        if (!(mod instanceof TextureKillModule module) || !module.isModuleEnabled()) {
            return;
        }
        event.registerReloadListener(new TextureRegionEraser(module.getConfig()));
    }
}
