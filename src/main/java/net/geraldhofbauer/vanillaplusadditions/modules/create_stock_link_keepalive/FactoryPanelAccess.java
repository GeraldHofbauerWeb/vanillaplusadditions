package net.geraldhofbauer.vanillaplusadditions.modules.create_stock_link_keepalive;

import net.minecraft.world.level.block.entity.BlockEntity;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

/**
 * Reflection-only bridge to Create's factory panels (the four gauges on a Factory Gauge block).
 *
 * <p>The two handles used here - the public {@code panels} map on {@code FactoryPanelBlockEntity}
 * and the public {@code resetTimer()} on {@code FactoryPanelBehaviour} - would both be reachable
 * directly, but the enclosing types descend from {@code SmartBlockEntity}, which drags in Ponder and
 * is not safe to reference from a dedicated server build (see {@code project_create_integration}).
 * So this stays reflective, like the sibling Create modules.</p>
 *
 * <p><strong>Why hold the timer.</strong> {@code FactoryPanelBehaviour.tickRequests()} bails out
 * while {@code timer > 0}, before it ever looks at stock levels. Calling {@code resetTimer()} once
 * per tick therefore parks the panel without touching a single piece of Create's own bookkeeping -
 * no field is overwritten, no network state is faked, and the moment we stop, the panel resumes
 * exactly as Create intended.</p>
 */
final class FactoryPanelAccess {

    private static final String BE_CLASS =
            "com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlockEntity";
    private static final String PANELS_FIELD = "panels";
    private static final String RESET_TIMER = "resetTimer";
    private static final String GET_LEVEL_IN_STORAGE = "getLevelInStorage";
    private static final String SATISFIED_FIELD = "satisfied";
    private static final String NETWORK_FIELD = "network";

    private static final Class<?> BLOCK_ENTITY_TYPE;
    private static final Field PANELS;
    private static final Method RESET;
    private static final Method LEVEL_IN_STORAGE;
    private static final Field SATISFIED;
    private static final Field NETWORK;
    private static final Method SUMMARY_OF_NETWORK;
    private static final Field CONTRIBUTING_LINKS;
    private static final boolean AVAILABLE;

    static {
        Class<?> beType = null;
        Field panels = null;
        Method reset = null;
        Method levelInStorage = null;
        Field satisfied = null;
        Field network = null;
        Method summaryOfNetwork = null;
        Field contributingLinks = null;
        boolean ok = false;
        try {
            beType = Class.forName(BE_CLASS);
            panels = beType.getField(PANELS_FIELD);

            Class<?> behaviourType = Class.forName(
                    "com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBehaviour");
            reset = behaviourType.getMethod(RESET_TIMER);
            levelInStorage = behaviourType.getMethod(GET_LEVEL_IN_STORAGE);
            satisfied = behaviourType.getField(SATISFIED_FIELD);
            network = behaviourType.getField(NETWORK_FIELD);

            Class<?> logisticsManager = Class.forName(
                    "com.simibubi.create.content.logistics.packagerLink.LogisticsManager");
            summaryOfNetwork = logisticsManager.getMethod(
                    "getSummaryOfNetwork", java.util.UUID.class, boolean.class);
            contributingLinks = Class.forName(
                    "com.simibubi.create.content.logistics.packager.InventorySummary")
                    .getField("contributingLinks");
            ok = true;
        } catch (Throwable t) {
            ok = false;
        }
        BLOCK_ENTITY_TYPE = beType;
        PANELS = panels;
        RESET = reset;
        LEVEL_IN_STORAGE = levelInStorage;
        SATISFIED = satisfied;
        NETWORK = network;
        SUMMARY_OF_NETWORK = summaryOfNetwork;
        CONTRIBUTING_LINKS = contributingLinks;
        AVAILABLE = ok;
    }

    private FactoryPanelAccess() {
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
     * Whether the given block entity is a Create factory panel block.
     *
     * @param blockEntity The block entity to test (may be null)
     * @return true for a factory panel block entity
     */
    static boolean isPanelBlock(BlockEntity blockEntity) {
        return AVAILABLE && blockEntity != null && BLOCK_ENTITY_TYPE.isInstance(blockEntity);
    }

    /**
     * Pushes every panel's request timer back to the full interval, which keeps
     * {@code tickRequests()} from reaching the point where it reads stock levels.
     *
     * @param blockEntity The factory panel block entity
     * @return how many panels were held, or 0 if the block entity is not a panel block
     */
    static int holdTimers(BlockEntity blockEntity) {
        if (!isPanelBlock(blockEntity)) {
            return 0;
        }
        try {
            Object value = PANELS.get(blockEntity);
            if (!(value instanceof Map<?, ?> panels)) {
                return 0;
            }
            int held = 0;
            for (Object behaviour : panels.values()) {
                if (behaviour != null) {
                    RESET.invoke(behaviour);
                    held++;
                }
            }
            return held;
        } catch (Throwable t) {
            return 0;
        }
    }

    /**
     * Whether the logistics network behind this gauge has not reported in yet.
     *
     * <p>This is the real condition the module cares about, and it beats any fixed waiting time.
     * {@code InventorySummary.contributingLinks} counts how many links actually fed the summary. At
     * zero the network has said nothing at all - which is NOT the same as "the network holds none of
     * this item", even though {@code getLevelInStorage()} reports 0 for both. Acting on that number
     * is what makes a gauge re-order a full vault.</p>
     *
     * <p>The summary comes from Create's twenty-tick cache, so this can stay true for up to a second
     * after the links have really returned. Holding a moment too long costs nothing; letting go a
     * moment too early costs a crafting run.</p>
     *
     * @param blockEntity The factory panel block entity
     * @return true if no link contributed to the network summary (or the state cannot be read)
     */
    static boolean networkHasNotReported(BlockEntity blockEntity) {
        if (!isPanelBlock(blockEntity)) {
            return false;
        }
        try {
            Object value = PANELS.get(blockEntity);
            if (!(value instanceof Map<?, ?> panels)) {
                return false;
            }
            for (Object behaviour : panels.values()) {
                if (behaviour == null) {
                    continue;
                }
                Object network = NETWORK.get(behaviour);
                if (network == null) {
                    continue;
                }
                Object summary = SUMMARY_OF_NETWORK.invoke(null, network, false);
                return summary == null || CONTRIBUTING_LINKS.getInt(summary) <= 0;
            }
            return false;
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Reads what the panels currently believe about their stock, for the debug log only.
     *
     * <p>Note that this calls Create's {@code getLevelInStorage()}, which builds (or reads the
     * one-second cache of) the network's inventory summary - cheap, but not free. It is therefore
     * only ever called when debug logging is on.</p>
     *
     * @param blockEntity The factory panel block entity
     * @return a compact {@code slot=level/satisfied} description, or an empty string on failure
     */
    static String describe(BlockEntity blockEntity) {
        if (!isPanelBlock(blockEntity)) {
            return "";
        }
        try {
            Object value = PANELS.get(blockEntity);
            if (!(value instanceof Map<?, ?> panels)) {
                return "";
            }
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<?, ?> entry : panels.entrySet()) {
                if (entry.getValue() == null) {
                    continue;
                }
                if (sb.length() > 0) {
                    sb.append(' ');
                }
                sb.append(entry.getKey())
                        .append('=')
                        .append(LEVEL_IN_STORAGE.invoke(entry.getValue()))
                        .append('/')
                        .append(SATISFIED.getBoolean(entry.getValue()) ? "sat" : "unsat");
            }
            return sb.toString();
        } catch (Throwable t) {
            return "";
        }
    }
}
