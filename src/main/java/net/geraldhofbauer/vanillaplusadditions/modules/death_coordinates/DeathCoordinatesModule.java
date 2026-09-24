package net.geraldhofbauer.vanillaplusadditions.modules.death_coordinates;

import net.geraldhofbauer.vanillaplusadditions.core.AbstractModule;
import net.geraldhofbauer.vanillaplusadditions.core.AbstractModuleConfig;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

public class DeathCoordinatesModule extends AbstractModule<
        DeathCoordinatesModule,
        AbstractModuleConfig.DefaultModuleConfig<DeathCoordinatesModule>
        > {

    /** Permission level required to use the click-to-teleport on a death message ({@code /tp}). */
    private static final int TELEPORT_PERMISSION_LEVEL = 2;

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

    /**
     * A readable name for the level's dimension, e.g. "the Overworld" instead of
     * {@code minecraft:overworld}.
     *
     * <p>Dimensions have no display name in vanilla — {@code DimensionType} carries only mechanical
     * settings, and 1.21.1 ships no {@code dimension.*} translation keys at all. So we
     * name the three vanilla ones ourselves and ask for the widely used modded convention
     * {@code dimension.<namespace>.<path>} on top: a dimension mod that does provide a name gets used
     * automatically (and localized), and anything else falls back to the plain id, which at least stays
     * actionable.
     *
     * <p>Display only. The teleport click event keeps using the raw id — that is what
     * {@code /execute in} expects.
     */
    private static MutableComponent dimensionName(Level level) {
        ResourceKey<Level> dimension = level.dimension();
        ResourceLocation id = dimension.location();
        String fallback;
        if (Level.OVERWORLD.equals(dimension)) {
            fallback = "the Overworld";
        } else if (Level.NETHER.equals(dimension)) {
            fallback = "the Nether";
        } else if (Level.END.equals(dimension)) {
            fallback = "the End";
        } else {
            fallback = id.toString();
        }
        return Component.translatableWithFallback(
                "dimension." + id.getNamespace() + "." + id.getPath(), fallback);
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
            MutableComponent baseMessage = Component.literal("Player ")
                    .append(Component.literal(player.getName().getString())
                            .withStyle(net.minecraft.ChatFormatting.BOLD, net.minecraft.ChatFormatting.GOLD))
                    .append(Component.literal(" died at coordinates: "))
                    .append(Component.literal(String.format("X=%d, Y=%d, Z=%d",
                                    deathPos.getX(), deathPos.getY(), deathPos.getZ()))
                            .withStyle(net.minecraft.ChatFormatting.AQUA))
                    .append(Component.literal(" in "))
                    .append(dimensionName(level)
                            .withStyle(net.minecraft.ChatFormatting.LIGHT_PURPLE));
            // TODO: Make the permission level configurable, so a server can offer the teleport to
            //  spectators or to everyone instead of just to operators.
            // The teleport is decided per RECIPIENT, not by the rank of the player who died. One
            // component broadcast to everyone would do exactly the wrong thing in both directions:
            // an operator death would hand every player a command the server then refuses, and an
            // ordinary player's death would stay unclickable even for the operators who may run it.
            MutableComponent teleportMessage = baseMessage.copy().withStyle(style -> style
                    .withHoverEvent(new HoverEvent(
                            HoverEvent.Action.SHOW_TEXT,
                            Component.literal("Click to teleport to death location")
                    ))
                    .withClickEvent(
                            new net.minecraft.network.chat.ClickEvent(
                                    net.minecraft.network.chat.ClickEvent.Action.RUN_COMMAND,
                                    // Use @s to teleport the player who clicked the message
                                    "/execute in %s as @s run tp @s %d %d %d"
                                            .formatted(
                                                    location.toString(),
                                                    deathPos.getX(),
                                                    deathPos.getY(),
                                                    deathPos.getZ()
                                            )
                            )
                    ));

            MinecraftServer server = level.getServer();
            if (server == null) {
                return;
            }
            // Broadcast the death message to all players
            for (ServerPlayer serverPlayer : server.getPlayerList().getPlayers()) {
                serverPlayer.sendSystemMessage(serverPlayer.hasPermissions(TELEPORT_PERMISSION_LEVEL)
                        ? teleportMessage : baseMessage);
            }
            // Log the death event
            if (getConfig().shouldDebugLog()) {
                getLogger().debug("Announced death of player {} at coordinates X={}, Y={}, Z={}",
                        player.getName().getString(),
                        deathPos.getX(), deathPos.getY(), deathPos.getZ());
            }
            // Send a copy of the message to the server console - no click target there
            server.sendSystemMessage(baseMessage);
        }
    }
}
