package net.geraldhofbauer.vanillaplusadditions.modules.create_redstone_link_rebinder;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Set;

/**
 * Reflection-only bridge to Create's redstone link network.
 *
 * <p>Everything here could in principle be called directly - {@code addToNetwork}, {@code
 * getNetworkOf} and {@code Create.REDSTONE_LINK_NETWORK_HANDLER} are all public. The one piece that
 * is not is {@code RedstoneLinkBlockEntity.link}, and reaching that through a Mixin accessor is the
 * trap documented for this project: {@code SmartBlockEntity} drags in Ponder, which is not on the
 * dedicated-server classpath. So the whole bridge goes through reflection, exactly like
 * {@code WaterWheelKinetics} in the water-wheel module - no compile-time reference to any Create
 * block-entity type survives in the jar.</p>
 *
 * <p><strong>Why this module exists.</strong> {@code LinkBehaviour.initialize()} calls
 * {@code addToNetwork} unconditionally on the server - but {@code initialize()} is only ever
 * reached from {@code SmartBlockEntity.tick()}, behind an {@code initialized} flag that is set on
 * the block entity's <em>first tick</em> and never persisted. A link that loads but does not tick
 * therefore never joins the network, while still answering {@code neighborChanged} - so its
 * {@code Transmit} field updates and it looks perfectly healthy from the outside. That was measured
 * on the live server: a transmitter reading {@code Transmit: 15} whose receiver sat at
 * {@code Receive: 0} for as long as the lever was held.</p>
 */
final class RedstoneLinkNetworkAccess {

    private static final String CREATE_CLASS = "com.simibubi.create.Create";
    private static final String HANDLER_FIELD = "REDSTONE_LINK_NETWORK_HANDLER";
    private static final String BE_CLASS = "com.simibubi.create.content.redstone.link.RedstoneLinkBlockEntity";
    private static final String LINKABLE_CLASS = "com.simibubi.create.content.redstone.link.IRedstoneLinkable";
    private static final String LINK_FIELD = "link";

    private static final Class<?> BLOCK_ENTITY_TYPE;
    private static final Object HANDLER;
    private static final Field LINK;
    private static final Method GET_NETWORK_OF;
    private static final Method ADD_TO_NETWORK;
    private static final boolean AVAILABLE;

    static {
        Class<?> beType = null;
        Object handler = null;
        Field link = null;
        Method getNetworkOf = null;
        Method addToNetwork = null;
        boolean ok = false;
        try {
            beType = Class.forName(BE_CLASS);
            link = beType.getDeclaredField(LINK_FIELD);
            link.setAccessible(true);

            Field handlerField = Class.forName(CREATE_CLASS).getField(HANDLER_FIELD);
            handler = handlerField.get(null);

            Class<?> linkableType = Class.forName(LINKABLE_CLASS);
            Class<?> handlerType = handler.getClass();
            Class<?> levelAccessor = Class.forName("net.minecraft.world.level.LevelAccessor");
            getNetworkOf = handlerType.getMethod("getNetworkOf", levelAccessor, linkableType);
            addToNetwork = handlerType.getMethod("addToNetwork", levelAccessor, linkableType);
            ok = true;
        } catch (Throwable t) {
            ok = false;
        }
        BLOCK_ENTITY_TYPE = beType;
        HANDLER = handler;
        LINK = link;
        GET_NETWORK_OF = getNetworkOf;
        ADD_TO_NETWORK = addToNetwork;
        AVAILABLE = ok;
    }

    private RedstoneLinkNetworkAccess() {
    }

    /**
     * Whether every reflective handle resolved. If not, the module stays inert rather than
     * throwing - a Create update that renames something must not break the server.
     *
     * @return true if the bridge is usable
     */
    static boolean isAvailable() {
        return AVAILABLE;
    }

    /**
     * Whether the given block entity is one of Create's redstone links.
     *
     * @param blockEntity The block entity to test (may be null)
     * @return true for a redstone link block entity
     */
    static boolean isLink(BlockEntity blockEntity) {
        return AVAILABLE && blockEntity != null && BLOCK_ENTITY_TYPE.isInstance(blockEntity);
    }

    /**
     * Checks whether a link is registered in its frequency's network and, if not, registers it.
     *
     * <p>Create's own {@code addToNetwork} finishes with {@code updateNetworkOf}, so a transmitter
     * that is re-registered while holding a signal pushes that signal to its receivers straight
     * away - no second nudge needed from us.</p>
     *
     * @param level       The server level the link lives in
     * @param blockEntity The redstone link block entity
     * @return what was found, and what was done about it
     */
    static Result rebindIfMissing(ServerLevel level, BlockEntity blockEntity) {
        if (!isLink(blockEntity)) {
            return Result.NOT_A_LINK;
        }
        try {
            Object link = LINK.get(blockEntity);
            if (link == null) {
                return Result.NO_BEHAVIOUR;
            }
            Object network = GET_NETWORK_OF.invoke(HANDLER, level, link);
            if (network instanceof Set<?> members && members.contains(link)) {
                return Result.ALREADY_REGISTERED;
            }
            ADD_TO_NETWORK.invoke(HANDLER, level, link);
            return Result.REBOUND;
        } catch (Throwable t) {
            return Result.FAILED;
        }
    }

    /** Outcome of inspecting a single link. */
    enum Result {
        /** The block entity is not a redstone link (or Create is absent). */
        NOT_A_LINK,
        /** The link block entity carries no link behaviour yet. */
        NO_BEHAVIOUR,
        /** The link was already registered in its network - nothing to do. */
        ALREADY_REGISTERED,
        /** The link was missing from its network and has been registered. */
        REBOUND,
        /** Reflection failed; the link was left untouched. */
        FAILED
    }
}
