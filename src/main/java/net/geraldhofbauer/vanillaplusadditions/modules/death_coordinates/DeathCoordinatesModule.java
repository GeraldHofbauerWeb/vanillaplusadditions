package net.geraldhofbauer.vanillaplusadditions.modules.death_coordinates;

import net.geraldhofbauer.vanillaplusadditions.core.AbstractModule;
import net.geraldhofbauer.vanillaplusadditions.core.AbstractModuleConfig;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

import java.util.Objects;

public class DeathCoordinatesModule extends AbstractModule<
        DeathCoordinatesModule,
        AbstractModuleConfig.DefaultModuleConfig<DeathCoordinatesModule>
        > {
    public DeathCoordinatesModule() {
        super("death_coordinates",
                "Death Coordinates Announcer",
                "Announces the coordinates of player deaths in chat",
                AbstractModuleConfig::createDefault
        );
    }

    @Override
    protected void onInitialize() {
        // Register event listeners for this module
        NeoForge.EVENT_BUS.register(this);

        getLogger().info("Death Coordinates module initialized - Player death coordinates will be announced in chat!");
    }

    @Override
    protected void onCommonSetup() {
        if (getConfig().shouldDebugLog()) {
            getLogger().debug("Death Coordinates module common setup complete");
        }
    }

    @SubscribeEvent(priority = EventPriority.NORMAL)
    public void onPlayerDeath(LivingDeathEvent event) {
        if (!isModuleEnabled()) {
            return;
        }

        // Only process on server side
        Level level = event.getEntity().level();
        if (level.isClientSide()) {
            return;
        }

        // Check if the entity is a player
        if (event.getEntity() instanceof net.minecraft.world.entity.player.Player player) {
            // Get the death coordinates
            net.minecraft.core.BlockPos deathPos = player.blockPosition();
            ResourceLocation location = level.dimension().location();
            MutableComponent deathMessage = Component.literal("Player ")
                    .append(Component.literal(player.getName().getString())
                            .withStyle(net.minecraft.ChatFormatting.BOLD, net.minecraft.ChatFormatting.GOLD))
                    .append(Component.literal(" died at coordinates: "))
                    .append(Component.literal(String.format("X=%d, Y=%d, Z=%d",
                                    deathPos.getX(), deathPos.getY(), deathPos.getZ()))
                            .withStyle(net.minecraft.ChatFormatting.AQUA))
                    .append(Component.literal(" in dimension "))
                    .append(Component.literal(location.toString() + location.getNamespace())
                            .withStyle(net.minecraft.ChatFormatting.LIGHT_PURPLE));
            // TODO: Make the permission level configurable aka make it a config option to enable for spectators
            //  (and/or ops) or all players
            if (player.hasPermissions(2)) {
                deathMessage = deathMessage.withStyle(style -> style
                        .withHoverEvent(new HoverEvent(
                                HoverEvent.Action.SHOW_TEXT,
                                Component.literal("Click to teleport to death location")
                        ))
                        .withClickEvent(
                                new net.minecraft.network.chat.ClickEvent(
                                        net.minecraft.network.chat.ClickEvent.Action.RUN_COMMAND,
                                        // "/execute in %s as @p run tp @p %d %d %d" // permission detection included
                                        "/execute in %s as @p run tp @p %d %d %d"
                                                .formatted(
                                                        location.toString(),
                                                        deathPos.getX(),
                                                        deathPos.getY(),
                                                        deathPos.getZ()
                                                )
                                )
                        ));
            }
            // Broadcast the death message to all players
            MinecraftServer server = Objects.requireNonNull(level.getServer());
            for (ServerPlayer serverPlayer : server.getPlayerList().getPlayers()) {
                serverPlayer.sendSystemMessage(deathMessage);
            }
            // Log the death event
            if (getConfig().shouldDebugLog()) {
                getLogger().info("Announced death of player {} at coordinates X={}, Y={}, Z={}",
                        player.getName().getString(),
                        deathPos.getX(), deathPos.getY(), deathPos.getZ());
            }
            // Send a copy of the message to the server console
            server.sendSystemMessage(deathMessage);
        }
    }
}
