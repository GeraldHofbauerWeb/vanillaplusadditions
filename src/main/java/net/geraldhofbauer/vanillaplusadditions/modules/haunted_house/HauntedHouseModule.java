package net.geraldhofbauer.vanillaplusadditions.modules.haunted_house;

import it.unimi.dsi.fastutil.longs.LongSet;
import net.geraldhofbauer.vanillaplusadditions.core.AbstractModule;
import net.geraldhofbauer.vanillaplusadditions.modules.haunted_house.config.HauntedHouseConfig;
import net.geraldhofbauer.vanillaplusadditions.util.MessageBroadcaster;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

import java.util.*;
import java.util.function.Predicate;

public class HauntedHouseModule extends AbstractModule<
        HauntedHouseModule,
        HauntedHouseConfig
        > {

    private static final Random RANDOM = new Random();
    private static final int REVEAL_CHECK_INTERVAL_TICKS = 10;
    private static final int REVEAL_CHECK_BACKOFF_TICKS = 40;
    // Track only replacement entities that are still invisible and waiting to be spotted.
    private final HashSet<UUID> pendingInvisibleReplacementEntities = new HashSet<>();
    private final HashMap<UUID, Integer> nextRevealCheckTick = new HashMap<>();

    // Track players inside target structures for fog effect
    private final HashMap<UUID, Long> playersInStructure = new HashMap<>();

    // Track last player positions to transfer movement paths into spawn-spot cache.
    private final HashMap<UUID, BlockPos> lastTrackedPlayerPositions = new HashMap<>();

    // Track last expensive cache refresh tick per player.
    private final HashMap<UUID, Long> lastCacheRefreshTickByPlayer = new HashMap<>();

    // Track fog persistence so indoor exposure lasts longer than outside/garden exposure.
    private final HashMap<UUID, Integer> playerFogTrailTicks = new HashMap<>();

    // Cache discovered indoor/garden spawn spots per dimension for direct haunted spawning.
    private final Map<ResourceKey<Level>, Map<Long, CachedSpawnSpot>> cachedSpawnSpotsByLevel = new HashMap<>();

    // Chunk index for faster nearby cached-spot queries.
    private final Map<ResourceKey<Level>, Map<Long, Set<Long>>> cachedSpawnSpotChunkIndexByLevel = new HashMap<>();

    // Cache expensive direct-spot validations for a short interval.
    private final Map<ResourceKey<Level>, Map<Long, CachedSpotValidation>> cachedDirectSpotValidationByLevel = new HashMap<>();

    private static final class CachedSpawnSpot {
        private final BlockPos pos;
        private final boolean skyAccess;
        private long expiresAtGameTick;

        private CachedSpawnSpot(BlockPos pos, boolean skyAccess, long expiresAtGameTick) {
            this.pos = pos;
            this.skyAccess = skyAccess;
            this.expiresAtGameTick = expiresAtGameTick;
        }
    }

    private static final class CachedSpotValidation {
        private final boolean blocked;
        private final boolean nearbyMobs;
        private final long validUntilTick;

        private CachedSpotValidation(boolean blocked, boolean nearbyMobs, long validUntilTick) {
            this.blocked = blocked;
            this.nearbyMobs = nearbyMobs;
            this.validUntilTick = validUntilTick;
        }
    }

    private enum FogZone {
        NONE,
        GARDEN,
        INDOOR
    }

    public enum PlayerLocationState {
        INSIDE,
        OUTSIDE_IN_STRUCTURE,
        OUTSIDE_STRUCTURE
    }

    public HauntedHouseModule() {
        super("haunted_house",
                "Haunted House",
                "Replaces configured mob spawns with an invisible replacement entity in haunted structures",
                HauntedHouseConfig::new
        );
    }

    @Override
    public boolean isEnabledByDefault() {
        return false;
    }

    @Override
    protected boolean shouldInitialize() {
        // Check for required mod: Dungeons and Taverns
        final boolean modsFound = ModList.get().isLoaded("mr_dungeons_andtaverns");

        if (!modsFound) {
            getLogger().warn("Haunted House module not initialized - Missing required mod: Dungeons and Taverns (mr_dungeons_andtaverns)");
        }

        return modsFound;
    }

    @Override
    protected void onInitialize() {
        // Register event listeners for this module
        NeoForge.EVENT_BUS.register(this);

        getLogger().info("Haunted House module initialized - Replacement entity configured as {}",
                getConfig().getReplacementEntityId());
    }

    @Override
    protected void onCommonSetup() {
        if (getConfig().shouldDebugLog()) {
            getLogger().debug("Haunted House module common setup complete");
        }
    }

    /**
     * Event handler that boosts witch spawn rates in target structures.
     * Uses HIGHEST priority to run before the replacement handler.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onBoostWitchSpawns(FinalizeSpawnEvent event) {
        if (!isModuleEnabled()) {
            return;
        }

        // Only process on server side
        if (event.getLevel().isClientSide()) {
            return;
        }

        // Cast to ServerLevel
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) {
            return;
        }

        // Check if witch spawn boost is enabled
        double boostChance = getConfig().getWitchSpawnBoostChance();
        if (boostChance <= 0) {
            return;
        }

        // Get the entity type
        EntityType<?> entityType = event.getEntity().getType();
        ResourceLocation entityTypeId = BuiltInRegistries.ENTITY_TYPE.getKey(entityType);

        String mobId = entityTypeId.toString();

        // Don't boost witches themselves
        if (mobId.equals("minecraft:witch")) {
            return;
        }

        // Check if we're in a target structure
        BlockPos spawnPos = event.getEntity().blockPosition();
        Map<Structure, LongSet> allStructures = serverLevel.structureManager().getAllStructuresAt(spawnPos);

        if (allStructures.isEmpty()) {
            return;
        }

        // Check if any structure is a target
        boolean insideTargetStructure = false;
        for (Structure structure : allStructures.keySet()) {
            ResourceLocation structureLocation = serverLevel.registryAccess()
                    .registryOrThrow(net.minecraft.core.registries.Registries.STRUCTURE)
                    .getKey(structure);

            if (structureLocation != null && getConfig().isTargetStructure(structureLocation.toString())) {
                insideTargetStructure = true;
                break;
            }
        }

        if (!insideTargetStructure) {
            return;
        }

        if (isBlockedHauntedSpawnLocation(serverLevel, spawnPos)) {
            return;
        }

        Optional<BlockPos> distributedSpawnPos = findDistributedSpawnPos(serverLevel, spawnPos);
        if (distributedSpawnPos.isEmpty()) {
            return;
        }

        // Roll for witch spawn boost
        double randomValue = RANDOM.nextDouble();
        if (randomValue < boostChance) {
            // Cancel current spawn and replace with witch (or replacement)
            event.setSpawnCancelled(true);

            // Decide if we should directly replace the boosted witch with the configured replacement (e.g., zombie/murmur)
            double witchReplacementRate = getConfig().getReplacementRate("minecraft:witch");
            boolean replaceBoostedWitch = witchReplacementRate > 0 && RANDOM.nextDouble() < witchReplacementRate;

            if (replaceBoostedWitch) {
                MessageBroadcaster.broadcastDebugWithLocation(
                        serverLevel,
                        getConfig().shouldDebugLog(),
                        "🧙➡️👻 Boosted witch spawn replaced with configured entity due to replacement rate",
                        distributedSpawnPos.get(),
                        getLogger()
                );
                // Spawn configured replacement entity directly
                spawnReplacementEntity(serverLevel, event.getEntity(), distributedSpawnPos.get());
            } else {
                MessageBroadcaster.broadcastDebugWithLocation(
                        serverLevel,
                        getConfig().shouldDebugLog(),
                        "🧙 Boosted witch spawn: Replaced " + mobId + " with witch (roll: "
                                + String.format("%.2f", randomValue) + " < " + String.format("%.2f", boostChance) + ")",
                        distributedSpawnPos.get(),
                        getLogger()
                );
                spawnWitch(serverLevel, event.getEntity(), distributedSpawnPos.get());
            }
        }
    }

    /**
     * Spawns a witch at the given location.
     */
    private void spawnWitch(ServerLevel level, Entity originalEntity, BlockPos spawnPos) {
        try {
            EntityType<?> witchType = EntityType.WITCH;
            Entity witch = witchType.create(level);
            
            if (witch == null) {
                getLogger().error("Failed to create witch entity");
                return;
            }

            // Position the witch at the same location as the original mob
            witch.moveTo(spawnPos.getX() + 0.5D, spawnPos.getY(), spawnPos.getZ() + 0.5D,
                    originalEntity.getYRot(), originalEntity.getXRot());

            // Add the witch to the world
            level.addFreshEntity(witch);

        } catch (Exception e) {
            getLogger().error("Failed to spawn witch", e);
        }
    }

    /**
     * Event handler that replaces configured mob spawns in configured structures.
     * Uses HIGH priority to ensure we can cancel the spawn before other mods process it.
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onShouldSpawnHauntedEntity(FinalizeSpawnEvent event) {
        if (!isModuleEnabled()) {
            return;
        }

        // Only process on server side
        if (event.getLevel().isClientSide()) {
            return;
        }

        // Cast to ServerLevel for broadcasting
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) {
            return;
        }

        // Get the entity type as a ResourceLocation
        EntityType<?> entityType = event.getEntity().getType();
        ResourceLocation entityTypeId = BuiltInRegistries.ENTITY_TYPE.getKey(entityType);

        // Check if this mob type should be replaced according to configuration
        String mobId = entityTypeId.toString();

        MessageBroadcaster.broadcastDebug(
                serverLevel,
                getConfig().shouldDebugLog(),
                "🔍 Step 1: Detected mob spawn: " + mobId,
                getLogger()
        );

        if (!getConfig().shouldReplaceMob(mobId)) {
            return;
        }

        MessageBroadcaster.broadcastDebug(
                serverLevel,
                getConfig().shouldDebugLog(),
                "✅ Step 2: Mob " + mobId + " is in replacement list",
                getLogger()
        );

        // Check if the mob is spawning inside a structure
        BlockPos spawnPos = event.getEntity().blockPosition();
        Map<Structure, LongSet> allStructures = serverLevel.structureManager().getAllStructuresAt(spawnPos);

        if (allStructures.isEmpty()) {
            MessageBroadcaster.broadcastDebugWithLocation(
                    serverLevel,
                    getConfig().shouldDebugLog(),
                    "❌ Step 3: No structures found at spawn location",
                    spawnPos,
                    getLogger()
            );
            return;
        }

        MessageBroadcaster.broadcastDebugWithLocation(
                serverLevel,
                getConfig().shouldDebugLog(),
                "✅ Step 3: Found " + allStructures.size() + " structure(s) at location",
                spawnPos,
                getLogger()
        );

        // Check if any of the structures is in the target structure list
        boolean insideTargetStructure = false;
        StringBuilder structureList = new StringBuilder();

        for (Structure structure : allStructures.keySet()) {
            ResourceLocation structureLocation = serverLevel.registryAccess()
                    .registryOrThrow(net.minecraft.core.registries.Registries.STRUCTURE)
                    .getKey(structure);

            if (structureLocation != null) {
                if (!structureList.isEmpty()) {
                    structureList.append(", ");
                }
                structureList.append(structureLocation);

                if (getConfig().isTargetStructure(structureLocation.toString())) {
                    insideTargetStructure = true;
                    MessageBroadcaster.broadcastDebug(
                            serverLevel,
                            getConfig().shouldDebugLog(),
                            "✅ Step 4: Found target structure: " + structureLocation,
                            getLogger()
                    );
                    break;
                }
            }
        }

        if (!insideTargetStructure) {
            MessageBroadcaster.broadcastDebug(
                    serverLevel,
                    getConfig().shouldDebugLog(),
                    "❌ Step 4: None of the structures match target list. Found: " + structureList,
                    getLogger()
            );
            return;
        }

        if (isBlockedHauntedSpawnLocation(serverLevel, spawnPos)) {
            MessageBroadcaster.broadcastDebugWithLocation(
                    serverLevel,
                    getConfig().shouldDebugLog(),
                    "❌ Step 4b: Spawn location is not a house-like area (or is likely cave/too open)",
                    spawnPos,
                    getLogger()
            );
            return;
        }

        Optional<BlockPos> distributedSpawnPos = findDistributedSpawnPos(serverLevel, spawnPos);
        if (distributedSpawnPos.isEmpty()) {
            MessageBroadcaster.broadcastDebugWithLocation(
                    serverLevel,
                    getConfig().shouldDebugLog(),
                    "❌ Step 4c: No suitable distributed spawn position in target structure found",
                    spawnPos,
                    getLogger()
            );
            return;
        }

        // Check if this spawn should be replaced based on configured replacement rate
        double replacementRate = getConfig().getReplacementRate(mobId);
        double randomValue = RANDOM.nextDouble();

        MessageBroadcaster.broadcastDebug(
                serverLevel,
                getConfig().shouldDebugLog(),
                String.format("🎲 Step 5: Replacement roll: %.2f < %.2f = %s",
                        randomValue, replacementRate, randomValue < replacementRate),
                getLogger()
        );

        if (randomValue >= replacementRate) {
            return;
        }

        // Cancel the mob spawn and replace it with the configured replacement entity
        event.setSpawnCancelled(true);

        // Broadcast debug message about cancelled spawn
        MessageBroadcaster.broadcastDebugWithLocation(
                serverLevel,
                getConfig().shouldDebugLog(),
                "❌ Cancelled " + mobId + " spawn in target structure",
                distributedSpawnPos.get(),
                getLogger()
        );

        // Spawn configured replacement entity in place of the cancelled mob
        spawnReplacementEntity(serverLevel, event.getEntity(), distributedSpawnPos.get());
    }

    /**
     * Spawns the configured replacement entity at the position of the cancelled spawn.
     */
    private void spawnReplacementEntity(ServerLevel level, Entity originalEntity, BlockPos spawnPos) {
        try {
            String configuredEntityId = getConfig().getReplacementEntityId();
            String[] entityIdParts = configuredEntityId.split(":", 2);
            if (entityIdParts.length != 2) {
                getLogger().error("Invalid replacement entity ID configured: {}", configuredEntityId);
                return;
            }

            ResourceLocation replacementEntityId = ResourceLocation.fromNamespaceAndPath(entityIdParts[0], entityIdParts[1]);
            Optional<EntityType<?>> replacementEntityType = BuiltInRegistries.ENTITY_TYPE.getOptional(replacementEntityId);

            if (replacementEntityType.isEmpty()) {
                getLogger().error("Failed to find configured replacement entity type: {}", configuredEntityId);
                return;
            }

            // Create the replacement entity
            Entity replacementEntity = replacementEntityType.get().create(level);
            if (replacementEntity == null) {
                getLogger().error("Failed to create replacement entity: {}", configuredEntityId);
                return;
            }

            // Position the replacement entity at the same location as the original mob
            replacementEntity.moveTo(spawnPos.getX() + 0.5D, spawnPos.getY(), spawnPos.getZ() + 0.5D,
                    originalEntity.getYRot(), originalEntity.getXRot());

            // Make living replacements invisible by default and track reveal state.
            if (replacementEntity instanceof LivingEntity livingEntity) {
                livingEntity.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, Integer.MAX_VALUE, 0, false, false));
                UUID replacementEntityUuid = replacementEntity.getUUID();
                pendingInvisibleReplacementEntities.add(replacementEntityUuid);
                // Stagger checks a bit so many entities do not all evaluate on the same tick.
                nextRevealCheckTick.put(replacementEntityUuid, replacementEntity.tickCount + RANDOM.nextInt(REVEAL_CHECK_INTERVAL_TICKS));
            } else {
                getLogger().warn(
                        "Configured replacement entity {} is not a LivingEntity - invisibility cannot be applied",
                        configuredEntityId
                );
            }
            
            // Add the replacement entity to the world
            level.addFreshEntity(replacementEntity);

            // Broadcast debug message to all players if debug logging is enabled
            MessageBroadcaster.broadcastDebugWithLocation(
                    level,
                    getConfig().shouldDebugLog(),
                    "👻 Spawned invisible replacement entity: " + configuredEntityId,
                    replacementEntity.blockPosition(),
                    getLogger()
            );

        } catch (Exception e) {
            getLogger().error("Failed to spawn configured replacement entity", e);
        }
    }

    private boolean isBlockedHauntedSpawnLocation(ServerLevel level, BlockPos pos) {
        if (isLikelyUndergroundCave(level, pos)) {
            return true;
        }

        boolean hasStructureMaterials = hasStructureMaterialsNearby(level, pos,
                getConfig().getMaterialScanHorizontalRadius(),
                getConfig().getMaterialScanVerticalRadius(),
                getConfig().getStructureMaterialThreshold());

        // In winding structures, material lists can miss some interior palette variants.
        // Keep a permissive fallback for roofed/interior-adjacent spots.
        if (!hasStructureMaterials && !hasRoofNearby(level, pos) && !isNearStructureGarden(level, pos)) {
            return true;
        }

        if (!level.canSeeSky(pos)) {
            return false;
        }

        return !isNearStructureGarden(level, pos)
                || RANDOM.nextDouble() >= getConfig().getSkyAccessAroundBuildingChance();
    }

    private boolean isBlockedFogArea(ServerLevel level, BlockPos pos) {
        if (isLikelyUndergroundCave(level, pos)) {
            return true;
        }

        if (hasStructureMaterialsNearby(level, pos,
                getConfig().getMaterialScanHorizontalRadius(),
                getConfig().getMaterialScanVerticalRadius(),
                getConfig().getStructureMaterialThreshold())) {
            return false;
        }

        if (hasRoofNearby(level, pos)) {
            return false;
        }

        return level.canSeeSky(pos) && !isNearStructureGarden(level, pos);
    }

    private boolean isOutsideTargetStructure(ServerLevel level, BlockPos pos) {
        Map<Structure, LongSet> structures = level.structureManager().getAllStructuresAt(pos);
        for (Structure structure : structures.keySet()) {
            ResourceLocation structureLocation = level.registryAccess()
                    .registryOrThrow(net.minecraft.core.registries.Registries.STRUCTURE)
                    .getKey(structure);

            if (structureLocation != null && getConfig().isTargetStructure(structureLocation.toString())) {
                return false;
            }
        }
        return true;
    }

    /**
     * Classifies player position into three states for debugging/commands.
     */
    public PlayerLocationState getPlayerLocationState(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel serverLevel)) {
            return PlayerLocationState.OUTSIDE_STRUCTURE;
        }

        BlockPos pos = player.blockPosition();
        if (isOutsideTargetStructure(serverLevel, pos)) {
            return PlayerLocationState.OUTSIDE_STRUCTURE;
        }

        if (isBlockedFogArea(serverLevel, pos)) {
            return PlayerLocationState.OUTSIDE_IN_STRUCTURE;
        }

        // Sky access without overhead cover = garden/courtyard, not truly indoor
        if (!hasRoofNearby(serverLevel, pos) && serverLevel.canSeeSky(pos)) {
            return PlayerLocationState.OUTSIDE_IN_STRUCTURE;
        }

        return PlayerLocationState.INSIDE;
    }

    private boolean isLikelyUndergroundCave(ServerLevel level, BlockPos pos) {
        int nearbyCaveMaterials = countNearbyMatchingBlocks(
                level,
                pos,
                getConfig().getMaterialScanHorizontalRadius(),
                getConfig().getMaterialScanVerticalRadius(),
                this::isUndergroundMaterial
        );

        int nearbyStructureMaterials = countNearbyMatchingBlocks(
                level,
                pos,
                getConfig().getMaterialScanHorizontalRadius(),
                getConfig().getMaterialScanVerticalRadius(),
                this::isStructureMaterial
        );

        int structureThreshold = getConfig().getStructureMaterialThreshold();
        int caveThreshold = getConfig().getCaveMaterialThreshold();

        // Strong structure signal always wins over cave heuristics.
        if (nearbyStructureMaterials >= structureThreshold) {
            return false;
        }

        if (nearbyCaveMaterials >= caveThreshold) {
            return true;
        }

        int surfaceYMotionBlocking = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos.getX(), pos.getZ());
        int surfaceYWorldSurface = level.getHeight(Heightmap.Types.WORLD_SURFACE, pos.getX(), pos.getZ());
        int surfaceY = Math.max(surfaceYMotionBlocking, surfaceYWorldSurface);
        return pos.getY() < surfaceY - getConfig().getCaveDepthTolerance();
    }

    private boolean hasRoofNearby(ServerLevel level, BlockPos pos) {
        for (int dy = 1; dy <= 6; dy++) {
            BlockPos above = pos.above(dy);
            BlockState state = level.getBlockState(above);
            if (!state.isAir() && state.isFaceSturdy(level, above, Direction.DOWN)) {
                return true;
            }
        }
        return false;
    }

    private boolean isNearStructureGarden(ServerLevel level, BlockPos origin) {
        int radius = getConfig().getSkyAccessNearBuildingRadius();
        int requiredMaterials = getConfig().getStructureMaterialThreshold() * 2;
        return hasStructureMaterialsNearby(level, origin, radius, 2, requiredMaterials);
    }

    private boolean hasStructureMaterialsNearby(ServerLevel level, BlockPos origin,
                                                int horizontalRadius, int verticalRadius, int threshold) {
        int structureMaterials = countNearbyMatchingBlocks(
                level,
                origin,
                horizontalRadius,
                verticalRadius,
                this::isStructureMaterial
        );
        return structureMaterials >= threshold;
    }

    private int countNearbyMatchingBlocks(ServerLevel level, BlockPos origin,
                                          int horizontalRadius, int verticalRadius,
                                          Predicate<BlockState> matcher) {
        int matches = 0;
        for (int dx = -horizontalRadius; dx <= horizontalRadius; dx++) {
            for (int dy = -verticalRadius; dy <= verticalRadius; dy++) {
                for (int dz = -horizontalRadius; dz <= horizontalRadius; dz++) {
                    BlockPos sample = origin.offset(dx, dy, dz);
                    BlockState state = level.getBlockState(sample);
                    if (matcher.test(state)) {
                        matches++;
                    }
                }
            }
        }
        return matches;
    }

    private boolean isUndergroundMaterial(BlockState state) {
        return isConfiguredMaterial(state, getConfig().getConfiguredUndergroundMaterialBlockIds());
    }

    private boolean isStructureMaterial(BlockState state) {
        return state.is(BlockTags.LOGS)
                || state.is(BlockTags.PLANKS)
                || state.is(BlockTags.WOODEN_STAIRS)
                || state.is(BlockTags.WOODEN_SLABS)
                || state.is(BlockTags.FENCES)
                || state.is(BlockTags.FENCE_GATES)
                || isConfiguredMaterial(state, getConfig().getConfiguredStructureMaterialBlockIds());
    }

    private boolean isConfiguredMaterial(BlockState state, Set<ResourceLocation> configuredMaterialBlockIds) {
        ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        return configuredMaterialBlockIds.contains(blockId);
    }

    private Optional<BlockPos> findDistributedSpawnPos(ServerLevel level, BlockPos origin) {
        int baseRadius = Math.max(1, getConfig().getDistributionRadius());
        int attempts = Math.max(1, getConfig().getDistributionAttempts());
        BlockPos fallbackInsideStructure = null;

        for (int attempt = 0; attempt < attempts; attempt++) {
            int adaptiveRadius = Math.min(baseRadius * 3,
                    baseRadius + (attempt * baseRadius) / Math.max(1, attempts / 2));
            int offsetX = RANDOM.nextInt(adaptiveRadius * 2 + 1) - adaptiveRadius;
            int offsetZ = RANDOM.nextInt(adaptiveRadius * 2 + 1) - adaptiveRadius;
            BlockPos sampled = origin.offset(offsetX, 0, offsetZ);

            if (isOutsideTargetStructure(level, sampled)) {
                continue;
            }

            BlockPos aligned = alignToWalkablePosition(level, sampled, origin.getY());

            if (isOutsideTargetStructure(level, aligned)) {
                continue;
            }

            if (isBlockedHauntedSpawnLocation(level, aligned)) {
                continue;
            }

            if (hasNearbyLivingMobs(level, aligned)) {
                if (fallbackInsideStructure == null) {
                    fallbackInsideStructure = aligned;
                }
                continue;
            }

            return Optional.of(aligned);
        }

        return Optional.ofNullable(fallbackInsideStructure);
    }

    private BlockPos alignToWalkablePosition(ServerLevel level, BlockPos pos, int baseY) {
        for (int dy = 2; dy >= -2; dy--) {
            BlockPos candidate = new BlockPos(pos.getX(), baseY + dy, pos.getZ());
            BlockPos below = candidate.below();
            if (level.isEmptyBlock(candidate)
                    && level.isEmptyBlock(candidate.above())
                    && level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)) {
                return candidate;
            }
        }
        return new BlockPos(pos.getX(), baseY, pos.getZ());
    }

    private boolean hasNearbyLivingMobs(ServerLevel level, BlockPos pos) {
        AABB searchArea = new AABB(pos).inflate(getConfig().getMinDistanceToOtherMobs());
        return !level.getEntitiesOfClass(LivingEntity.class, searchArea,
                entity -> !(entity instanceof Player)).isEmpty();
    }

    private CachedSpotValidation getDirectSpotValidation(ServerLevel level, BlockPos pos, long now) {
        ResourceKey<Level> levelKey = level.dimension();
        Map<Long, CachedSpotValidation> validationCache = cachedDirectSpotValidationByLevel
                .computeIfAbsent(levelKey, ignored -> new HashMap<>());

        long packedPos = pos.asLong();
        CachedSpotValidation cached = validationCache.get(packedPos);
        if (cached != null && cached.validUntilTick >= now) {
            return cached;
        }

        boolean blocked = isBlockedDirectSpawnSpot(level, pos);
        boolean nearbyMobs = !blocked && hasNearbyLivingMobs(level, pos);
        long validationTtl = Math.max(1, getConfig().getDirectSpotValidationIntervalTicks());
        CachedSpotValidation computed = new CachedSpotValidation(blocked, nearbyMobs, now + validationTtl);
        validationCache.put(packedPos, computed);
        return computed;
    }

    private void pruneExpiredDirectSpotValidation(ServerLevel level, long now) {
        Map<Long, CachedSpotValidation> validationCache = cachedDirectSpotValidationByLevel.get(level.dimension());
        if (validationCache == null || validationCache.isEmpty()) {
            return;
        }

        validationCache.entrySet().removeIf(entry -> entry.getValue().validUntilTick < now);
    }

    private boolean isWalkableSpawnCandidate(ServerLevel level, BlockPos candidate) {
        BlockPos below = candidate.below();
        return level.isEmptyBlock(candidate)
                && level.isEmptyBlock(candidate.above())
                && level.getBlockState(below).isFaceSturdy(level, below, Direction.UP);
    }

    private boolean isBlockedDirectSpawnSpot(ServerLevel level, BlockPos pos) {
        if (isLikelyUndergroundCave(level, pos)) {
            return true;
        }

        if (!hasStructureMaterialsNearby(
                level,
                pos,
                getConfig().getMaterialScanHorizontalRadius(),
                getConfig().getMaterialScanVerticalRadius(),
                getConfig().getStructureMaterialThreshold()
        )) {
            return true;
        }

        if (level.canSeeSky(pos) && !isNearStructureGarden(level, pos)) {
            return true;
        }

        return !isWalkableSpawnCandidate(level, pos);
    }

    private void refreshSpawnSpotCacheAround(ServerLevel level, BlockPos origin) {
        ResourceKey<Level> levelKey = level.dimension();
        Map<Long, CachedSpawnSpot> levelCache = cachedSpawnSpotsByLevel.computeIfAbsent(levelKey, ignored -> new HashMap<>());
        Map<Long, Set<Long>> chunkIndex = cachedSpawnSpotChunkIndexByLevel.computeIfAbsent(levelKey, ignored -> new HashMap<>());

        long now = level.getGameTime();
        long ttlTicks = Math.max(20L, getConfig().getCacheTtlSeconds() * 20L);
        pruneExpiredSpots(levelKey, levelCache, chunkIndex, now);
        pruneExpiredDirectSpotValidation(level, now);

        int radius = getConfig().getAreaScanRadius();
        int scanStep = Math.max(1, getConfig().getCacheScanStep());
        for (int dx = -radius; dx <= radius; dx += scanStep) {
            for (int dz = -radius; dz <= radius; dz += scanStep) {
                for (int dy = -2; dy <= 2; dy++) {
                    BlockPos candidate = origin.offset(dx, dy, dz);
                    long packedPos = candidate.asLong();
                    CachedSpawnSpot existing = levelCache.get(packedPos);
                    if (existing != null) {
                        existing.expiresAtGameTick = now + ttlTicks;
                        continue;
                    }

                    if (isOutsideTargetStructure(level, candidate)) {
                        continue;
                    }

                    CachedSpotValidation validation = getDirectSpotValidation(level, candidate, now);
                    if (validation.blocked || validation.nearbyMobs) {
                        continue;
                    }

                    boolean skyAccess = level.canSeeSky(candidate);
                    levelCache.put(packedPos, new CachedSpawnSpot(candidate.immutable(), skyAccess, now + ttlTicks));
                    long chunkKey = ChunkPos.asLong(candidate.getX() >> 4, candidate.getZ() >> 4);
                    chunkIndex.computeIfAbsent(chunkKey, ignored -> new HashSet<>()).add(packedPos);
                }
            }
        }

        int maxCachedSpots = getConfig().getMaxCachedSpawnSpotsPerLevel();
        if (levelCache.size() > maxCachedSpots) {
            List<Map.Entry<Long, CachedSpawnSpot>> entries = new ArrayList<>(levelCache.entrySet());
            entries.sort(Comparator.comparingLong(entry -> entry.getValue().expiresAtGameTick));
            int overflow = levelCache.size() - maxCachedSpots;
            for (int i = 0; i < overflow; i++) {
                long packedPos = entries.get(i).getKey();
                CachedSpawnSpot removed = levelCache.remove(packedPos);
                if (removed != null) {
                    long chunkKey = ChunkPos.asLong(removed.pos.getX() >> 4, removed.pos.getZ() >> 4);
                    removeFromChunkIndex(chunkIndex, chunkKey, packedPos);
                    removeDirectSpotValidation(levelKey, packedPos);
                }
            }
        }
    }

    private void pruneExpiredSpots(ResourceKey<Level> levelKey,
                                   Map<Long, CachedSpawnSpot> levelCache,
                                   Map<Long, Set<Long>> chunkIndex,
                                   long now) {
        Iterator<Map.Entry<Long, CachedSpawnSpot>> iterator = levelCache.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Long, CachedSpawnSpot> entry = iterator.next();
            CachedSpawnSpot spot = entry.getValue();
            if (spot.expiresAtGameTick >= now) {
                continue;
            }
            iterator.remove();
            long chunkKey = ChunkPos.asLong(spot.pos.getX() >> 4, spot.pos.getZ() >> 4);
            removeFromChunkIndex(chunkIndex, chunkKey, entry.getKey());
            removeDirectSpotValidation(levelKey, entry.getKey());
        }
    }

    private void removeDirectSpotValidation(ResourceKey<Level> levelKey, long packedPos) {
        Map<Long, CachedSpotValidation> validationCache = cachedDirectSpotValidationByLevel.get(levelKey);
        if (validationCache != null) {
            validationCache.remove(packedPos);
        }
    }

    private void removeFromChunkIndex(Map<Long, Set<Long>> chunkIndex, long chunkKey, long packedPos) {
        Set<Long> chunkSpots = chunkIndex.get(chunkKey);
        if (chunkSpots == null) {
            return;
        }
        chunkSpots.remove(packedPos);
        if (chunkSpots.isEmpty()) {
            chunkIndex.remove(chunkKey);
        }
    }

    private List<CachedSpawnSpot> getNearbyCachedSpots(ServerLevel level, BlockPos centerPos) {
        ResourceKey<Level> levelKey = level.dimension();
        Map<Long, CachedSpawnSpot> levelCache = cachedSpawnSpotsByLevel.get(levelKey);
        Map<Long, Set<Long>> chunkIndex = cachedSpawnSpotChunkIndexByLevel.get(levelKey);
        if (levelCache == null || levelCache.isEmpty() || chunkIndex == null || chunkIndex.isEmpty()) {
            return List.of();
        }

        int chunkRadius = Math.max(0, getConfig().getCacheQueryChunkRadius());
        int centerChunkX = centerPos.getX() >> 4;
        int centerChunkZ = centerPos.getZ() >> 4;

        List<CachedSpawnSpot> spots = new ArrayList<>();
        for (int dx = -chunkRadius; dx <= chunkRadius; dx++) {
            for (int dz = -chunkRadius; dz <= chunkRadius; dz++) {
                long chunkKey = ChunkPos.asLong(centerChunkX + dx, centerChunkZ + dz);
                Set<Long> chunkSpots = chunkIndex.get(chunkKey);
                if (chunkSpots == null || chunkSpots.isEmpty()) {
                    continue;
                }
                for (long packedPos : chunkSpots) {
                    CachedSpawnSpot spot = levelCache.get(packedPos);
                    if (spot != null) {
                        spots.add(spot);
                    }
                }
            }
        }
        return spots;
    }

    private void tryDirectAreaSpawn(ServerLevel level, Player anchorPlayer) {
        if (!getConfig().isDirectAreaSpawningEnabled()) {
            return;
        }

        int interval = Math.max(20, getConfig().getDirectSpawnIntervalTicks());
        if (anchorPlayer.tickCount % interval != 0) {
            return;
        }

        if (RANDOM.nextDouble() >= getConfig().getDirectSpawnAttemptChance()) {
            return;
        }

        ResourceKey<Level> levelKey = level.dimension();
        Map<Long, CachedSpawnSpot> levelCache = cachedSpawnSpotsByLevel.get(levelKey);
        Map<Long, Set<Long>> chunkIndex = cachedSpawnSpotChunkIndexByLevel.get(levelKey);
        if (levelCache == null || levelCache.isEmpty() || chunkIndex == null || chunkIndex.isEmpty()) {
            return;
        }

        long now = level.getGameTime();
        pruneExpiredSpots(levelKey, levelCache, chunkIndex, now);
        pruneExpiredDirectSpotValidation(level, now);
        if (levelCache.isEmpty()) {
            return;
        }

        double minDistance = getConfig().getDirectSpawnMinPlayerDistance();
        double maxDistance = Math.max(minDistance + 2.0, getConfig().getDirectSpawnMaxPlayerDistance());
        double minDistanceSqr = minDistance * minDistance;
        double maxDistanceSqr = maxDistance * maxDistance;

        List<CachedSpawnSpot> spots = getNearbyCachedSpots(level, anchorPlayer.blockPosition());
        if (spots.isEmpty()) {
            return;
        }

        int samples = Math.max(1, getConfig().getDirectSpawnCandidateSamples());
        Set<Integer> sampledIndexes = new HashSet<>();
        for (int i = 0; i < samples; i++) {
            int selectedIndex = RANDOM.nextInt(spots.size());
            if (!sampledIndexes.add(selectedIndex)) {
                continue;
            }
            CachedSpawnSpot selected = spots.get(selectedIndex);

            if (isOutsideTargetStructure(level, selected.pos)) {
                continue;
            }

            CachedSpotValidation validation = getDirectSpotValidation(level, selected.pos, now);
            if (validation.blocked || validation.nearbyMobs) {
                continue;
            }

            Vec3 spotCenter = Vec3.atCenterOf(selected.pos);
            double distanceSqr = anchorPlayer.distanceToSqr(spotCenter);
            if (distanceSqr < minDistanceSqr || distanceSqr > maxDistanceSqr) {
                continue;
            }

            if (!selected.skyAccess || isNearStructureGarden(level, selected.pos)) {
                if (RANDOM.nextDouble() < getConfig().getDirectSpawnReplacementChance()) {
                    spawnReplacementEntity(level, anchorPlayer, selected.pos);
                } else {
                    spawnWitch(level, anchorPlayer, selected.pos);
                }
                return;
            }
        }
    }

    private void updateCacheFromPlayerMovement(ServerLevel level, Player player) {
        UUID playerId = player.getUUID();
        long now = level.getGameTime();
        long refreshInterval = Math.max(1L, getConfig().getCacheRefreshIntervalTicks());
        long lastRefresh = lastCacheRefreshTickByPlayer.getOrDefault(playerId, Long.MIN_VALUE);

        if (now - lastRefresh < refreshInterval) {
            lastTrackedPlayerPositions.put(playerId, player.blockPosition().immutable());
            return;
        }

        BlockPos currentPos = player.blockPosition();
        BlockPos previousPos = lastTrackedPlayerPositions.put(playerId, currentPos.immutable());

        // First observation in structure: refresh around current location only.
        if (previousPos == null) {
            refreshSpawnSpotCacheAround(level, currentPos);
            lastCacheRefreshTickByPlayer.put(playerId, now);
            return;
        }

        int dx = currentPos.getX() - previousPos.getX();
        int dy = currentPos.getY() - previousPos.getY();
        int dz = currentPos.getZ() - previousPos.getZ();
        int maxDelta = Math.max(Math.abs(dx), Math.max(Math.abs(dy), Math.abs(dz)));

        // Ignore tiny/no movement to reduce redundant scans.
        if (maxDelta <= 1) {
            refreshSpawnSpotCacheAround(level, currentPos);
            lastCacheRefreshTickByPlayer.put(playerId, now);
            return;
        }

        // Clamp the interpolation cost in case of teleports or lag spikes.
        int steps = Math.min(getConfig().getMovementInterpolationMaxSteps(), maxDelta);
        for (int i = 0; i <= steps; i++) {
            double t = (double) i / (double) steps;
            int sampleX = previousPos.getX() + (int) Math.round(dx * t);
            int sampleY = previousPos.getY() + (int) Math.round(dy * t);
            int sampleZ = previousPos.getZ() + (int) Math.round(dz * t);
            BlockPos samplePos = new BlockPos(sampleX, sampleY, sampleZ);

            if (isOutsideTargetStructure(level, samplePos)) {
                continue;
            }

            if (isBlockedFogArea(level, samplePos)) {
                continue;
            }

            refreshSpawnSpotCacheAround(level, samplePos);
        }

        lastCacheRefreshTickByPlayer.put(playerId, now);
    }

    private FogZone resolveFogZoneFromCache(ServerLevel level, BlockPos origin) {
        ResourceKey<Level> levelKey = level.dimension();
        Map<Long, CachedSpawnSpot> levelCache = cachedSpawnSpotsByLevel.get(levelKey);
        Map<Long, Set<Long>> chunkIndex = cachedSpawnSpotChunkIndexByLevel.get(levelKey);
        if (levelCache == null || levelCache.isEmpty() || chunkIndex == null || chunkIndex.isEmpty()) {
            return FogZone.NONE;
        }

        long now = level.getGameTime();
        int indoorMatches = 0;
        int gardenMatches = 0;
        int fogCacheProximityRadius = getConfig().getFogCacheProximityRadius();
        int maxDistanceSqr = fogCacheProximityRadius * fogCacheProximityRadius;

        pruneExpiredSpots(levelKey, levelCache, chunkIndex, now);

        List<CachedSpawnSpot> nearbySpots = getNearbyCachedSpots(level, origin);
        for (CachedSpawnSpot spot : nearbySpots) {

            if (spot.pos.distSqr(origin) > maxDistanceSqr) {
                continue;
            }

            if (spot.skyAccess) {
                gardenMatches++;
            } else {
                indoorMatches++;
            }
        }

        if (indoorMatches > 0 && indoorMatches >= gardenMatches) {
            return FogZone.INDOOR;
        }
        if (gardenMatches > 0) {
            return FogZone.GARDEN;
        }
        return FogZone.NONE;
    }

    /**
     * Event handler that checks if players are looking at invisible replacement entities and makes them visible.
     */
    @SubscribeEvent
    public void onEntityTick(EntityTickEvent.Pre event) {
        if (!isModuleEnabled()) {
            return;
        }

        // Only check every 10 ticks (0.5 seconds) for performance
        if (event.getEntity().tickCount % REVEAL_CHECK_INTERVAL_TICKS != 0) {
            return;
        }

        // Check if this is a living replacement entity we're tracking
        if (!(event.getEntity() instanceof LivingEntity replacementEntity)) {
            return;
        }
        
        UUID replacementEntityUuid = replacementEntity.getUUID();
        if (!pendingInvisibleReplacementEntities.contains(replacementEntityUuid)) {
            return;
        }

        // Cleanup stale tracking if entity is gone.
        if (!replacementEntity.isAlive() || replacementEntity.isRemoved()) {
            pendingInvisibleReplacementEntities.remove(replacementEntityUuid);
            nextRevealCheckTick.remove(replacementEntityUuid);
            return;
        }

        int nextCheckTick = nextRevealCheckTick.getOrDefault(replacementEntityUuid, 0);
        if (replacementEntity.tickCount < nextCheckTick) {
            return;
        }
        
        // Only process on server side
        //noinspection resource
        if (replacementEntity.level().isClientSide) {
            return;
        }
        
        //noinspection resource
        if (!(replacementEntity.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        List<ServerPlayer> nearbyPlayers = serverLevel.getEntitiesOfClass(
                ServerPlayer.class,
                replacementEntity.getBoundingBox().inflate(32.0D),
                player -> !player.isSpectator()
        );

        if (nearbyPlayers.isEmpty()) {
            nextRevealCheckTick.put(replacementEntityUuid, replacementEntity.tickCount + REVEAL_CHECK_BACKOFF_TICKS);
            return;
        }

        nextRevealCheckTick.put(replacementEntityUuid, replacementEntity.tickCount + REVEAL_CHECK_INTERVAL_TICKS);
        Vec3 replacementEntityPos = replacementEntity.getEyePosition();

        // Check if any nearby non-spectator player is looking at the entity.
        for (ServerPlayer player : nearbyPlayers) {

            // Check if player is within reasonable distance (32 blocks)
            if (player.distanceToSqr(replacementEntity) > 32 * 32) {
                continue;
            }
            
            // Get player's look vector
            Vec3 playerEyePos = player.getEyePosition();
            Vec3 playerLookVec = player.getLookAngle();
            
            // Calculate vector from player to replacement entity
            Vec3 toReplacementEntity = replacementEntityPos.subtract(playerEyePos).normalize();

            // Check if player is looking roughly in the direction of the murmur
            // (dot product > 0.95 means within about 18 degrees)
            double dotProduct = playerLookVec.dot(toReplacementEntity);
            if (dotProduct < 0.95) {
                continue;
            }

            if (!player.hasLineOfSight(replacementEntity)) {
                continue;
            }
            
            // Player is looking at the replacement entity! Make it visible.
            replacementEntity.removeEffect(MobEffects.INVISIBILITY);
            pendingInvisibleReplacementEntities.remove(replacementEntityUuid);
            nextRevealCheckTick.remove(replacementEntityUuid);

            if (getConfig().shouldDebugLog()) {
                getLogger().debug("Player {} spotted replacement entity at {}",
                        player.getName().getString(), replacementEntity.blockPosition());
            }

            break; // No need to check other players
        }
    }

    /**
     * Event handler that applies fog effect to players inside target structures.
     */
    @SubscribeEvent
    public void onPlayerTick(EntityTickEvent.Pre event) {
        if (!isModuleEnabled()) {
            return;
        }

        // Only check players
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        // Only process on server side
        //noinspection resource
        if (player.level().isClientSide) {
            return;
        }

        // Only check every 20 ticks (1 second) for performance
        if (player.tickCount % 20 != 0) {
            return;
        }

        // Check if fog effect is enabled
        if (!getConfig().isFogEffectEnabled()) {
            return;
        }

        //noinspection resource
        if (!(player.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        BlockPos playerPos = player.blockPosition();

        // Check if player is in a target structure
        Map<Structure, LongSet> allStructures = serverLevel.structureManager().getAllStructuresAt(playerPos);

        boolean insideTargetStructure = false;

        // Skip the detection if in creative or spectator mode
        if (!allStructures.isEmpty() && !player.isCreative() && !player.isSpectator()) {
            outerLoop:
            for (Map.Entry<Structure, LongSet> structureEntry : allStructures.entrySet()) {
                Structure structure = structureEntry.getKey();
                ResourceLocation structureLocation = serverLevel.registryAccess()
                        .registryOrThrow(net.minecraft.core.registries.Registries.STRUCTURE)
                        .getKey(structure);

                if (structureLocation == null || !getConfig().isTargetStructure(structureLocation.toString())) {
                    continue;
                }

                // getStructureAt(playerPos, structure) only finds the start when the player is in the
                // structure's START chunk. For multi-chunk structures the player is usually elsewhere.
                // Instead, iterate the LongSet (start-chunk keys) and look up the start there.
                for (long chunkKey : structureEntry.getValue()) {
                    int startChunkX = ChunkPos.getX(chunkKey);
                    int startChunkZ = ChunkPos.getZ(chunkKey);
                    BlockPos posInStartChunk = new BlockPos(
                            startChunkX * 16 + 8, playerPos.getY(), startChunkZ * 16 + 8);

                    var structureStart = serverLevel.structureManager()
                            .getStructureAt(posInStartChunk, structure);

                    if (!structureStart.isValid()) {
                        continue;
                    }

                    var boundingBox = structureStart.getBoundingBox();
                    int playerY = playerPos.getY();
                    int minY = boundingBox.minY();
                    int maxY = boundingBox.maxY();

                    if (playerY >= minY && playerY <= maxY) {
                        insideTargetStructure = true;

                        if (getConfig().shouldDebugLog()) {
                            getLogger().debug("Player {} inside structure Y-range: {} (structure Y: {} - {})",
                                    player.getName().getString(), playerY, minY, maxY);
                        }
                        break outerLoop;
                    } else {
                        if (getConfig().shouldDebugLog()) {
                            getLogger().debug("Player {} outside structure Y-range: {} (structure Y: {} - {})",
                                    player.getName().getString(), playerY, minY, maxY);
                        }
                    }
                }
            }
        }

        UUID playerId = player.getUUID();
        int fogTrail = playerFogTrailTicks.getOrDefault(playerId, 0);

        if (insideTargetStructure && isBlockedFogArea(serverLevel, playerPos)) {
            insideTargetStructure = false;
        }

        if (insideTargetStructure) {
            updateCacheFromPlayerMovement(serverLevel, player);
        }

        if (insideTargetStructure && getConfig().isDirectAreaSpawningEnabled()) {
            tryDirectAreaSpawn(serverLevel, player);
        }

        if (insideTargetStructure) {
            FogZone fogZone = resolveFogZoneFromCache(serverLevel, playerPos);
            if (fogZone == FogZone.NONE) {
                fogZone = serverLevel.canSeeSky(playerPos) ? FogZone.GARDEN : FogZone.INDOOR;
            }

            int baseDuration;
            if (fogZone == FogZone.INDOOR) {
                fogTrail = Math.min(getConfig().getFogTrailMaxTicks(), fogTrail + 50);
                baseDuration = getConfig().getFogIndoorBaseDurationTicks();
            } else {
                fogTrail = Math.max(0, fogTrail - getConfig().getFogTrailDecayTicks());
                baseDuration = getConfig().getFogGardenBaseDurationTicks();
            }

            playerFogTrailTicks.put(playerId, fogTrail);

            // Indoor cache zones extend fog noticeably longer than garden/open zones.
            int amplifier = getConfig().getFogEffectAmplifier();
            player.addEffect(new MobEffectInstance(MobEffects.DARKNESS,
                    baseDuration + fogTrail,
                    amplifier,
                    false,
                    false));

            // Track first entry for debug message
            if (!playersInStructure.containsKey(playerId)) {
                playersInStructure.put(playerId, System.currentTimeMillis());

                MessageBroadcaster.broadcastDebug(
                        serverLevel,
                        getConfig().shouldDebugLog(),
                        "🌫️ Player " + player.getName().getString() + " entered haunted structure - applying fog effect",
                        getLogger()
                );
            }
        } else {
            lastTrackedPlayerPositions.remove(playerId);
            lastCacheRefreshTickByPlayer.remove(playerId);

            if (fogTrail > 0) {
                int amplifier = getConfig().getFogEffectAmplifier();
                player.addEffect(new MobEffectInstance(MobEffects.DARKNESS,
                        Math.min(40, fogTrail),
                        amplifier,
                        false,
                        false));

                int reducedTrail = Math.max(0, fogTrail - getConfig().getFogTrailDecayTicks());
                if (reducedTrail == 0) {
                    playerFogTrailTicks.remove(playerId);
                } else {
                    playerFogTrailTicks.put(playerId, reducedTrail);
                }
            }

            // Player left structure - remove from tracking
            if (playersInStructure.remove(playerId) != null) {
                MessageBroadcaster.broadcastDebug(
                        serverLevel,
                        getConfig().shouldDebugLog(),
                        "☀️ Player " + player.getName().getString() + " left haunted structure - fog will dissipate",
                        getLogger()
                );
            }
        }
    }

}
