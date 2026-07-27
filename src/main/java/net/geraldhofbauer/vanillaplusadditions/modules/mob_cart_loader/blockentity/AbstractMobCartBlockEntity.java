package net.geraldhofbauer.vanillaplusadditions.modules.mob_cart_loader.blockentity;

import net.geraldhofbauer.vanillaplusadditions.modules.mob_cart_loader.MobCartLoaderModule;
import net.geraldhofbauer.vanillaplusadditions.modules.mob_cart_loader.block.AbstractMobCartBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.WaterAnimal;
import net.minecraft.world.entity.animal.axolotl.Axolotl;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Shared block-entity logic for the mob loader/unloader. The block <b>captures and stores</b> one
 * mob internally (as entity NBT): the loader pulls a mob from the adjacent pen, the unloader pulls a
 * passenger from a parked minecart. The stored mob is shown as the spinning mini-mob and is only
 * <b>released to the output side when that side is unobstructed</b> — a solid block in front (e.g. a
 * piston head) keeps the mob buffered inside, so releasing can be gated with redstone/pistons. The
 * whole block is disabled while redstone-powered (inverse control). Breaking the block releases the
 * stored mob so it is never lost.
 *
 * <p>Contains no Create references; the goggle panel that reads {@link #getDisplayType()} lives in a
 * separate client handler gated on Create's goggles.</p>
 */
public abstract class AbstractMobCartBlockEntity extends BlockEntity {

    /** Cart is considered "parked" below this squared horizontal speed. */
    protected static final double PARKED_EPSILON = 1.0E-4;

    /** The held mob as full entity NBT (server-authoritative, persisted); null when empty. */
    @Nullable
    private CompoundTag storedMob;

    // --- Synced display state (what the BER/goggles show) ---
    @Nullable
    private EntityType<?> displayType;
    private float mobHealth;
    private float mobMaxHealth;
    private boolean displayBaby;

    // --- Client-only render cache ---
    @Nullable
    private Entity cachedDisplayEntity;
    @Nullable
    private EntityType<?> cachedEntityType;

    protected AbstractMobCartBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    // ---- Tick dispatch ----

    public static void serverTick(Level level, BlockPos pos, BlockState state, AbstractMobCartBlockEntity be) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        int interval = Math.max(1, MobCartLoaderModule.getCheckIntervalTicks());
        if (serverLevel.getGameTime() % interval != 0) {
            return;
        }
        be.tickServer(serverLevel, pos, state);
    }

    /** Per-scan server logic: capture into storage and/or release to the output. */
    protected abstract void tickServer(ServerLevel level, BlockPos pos, BlockState state);

    // ---- Geometry ----

    protected Direction facing(BlockState state) {
        return state.getValue(AbstractMobCartBlock.FACING);
    }

    /** The input side (block front — toward the player at placement). */
    protected BlockPos inputPos(BlockState state) {
        return worldPosition.relative(facing(state));
    }

    /** The output side (opposite the input). */
    protected BlockPos outputPos(BlockState state) {
        return worldPosition.relative(facing(state).getOpposite());
    }

    /** True if a solid block occupies the position (blocks release; rails/air are not solid). */
    protected boolean outputBlocked(Level level, BlockPos pos) {
        return !level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
    }

    // ---- Storage ----

    public boolean hasStored() {
        return storedMob != null;
    }

    /** Captures a mob into storage: serialises it, updates the display, and removes it from the world. */
    protected void storeMob(LivingEntity mob) {
        CompoundTag tag = new CompoundTag();
        if (!mob.save(tag)) {
            return; // not persistable (shouldn't happen for Mobs)
        }
        this.storedMob = tag;
        setDisplay(mob.getType(), mob.getHealth(), mob.getMaxHealth(), mob.isBaby());
        mob.discard();
    }

    /**
     * Reconstructs the stored entity at (x,y,z) and clears the storage + display. The caller adds it
     * to the world (and optionally makes it ride a cart).
     *
     * @return the reconstructed entity, or null if nothing was stored / reconstruction failed
     */
    @Nullable
    protected Entity takeStoredEntity(ServerLevel level, double x, double y, double z) {
        if (storedMob == null) {
            return null;
        }
        CompoundTag tag = storedMob;
        Entity entity = EntityType.loadEntityRecursive(tag, level, e -> {
            e.moveTo(x, y, z, e.getYRot(), e.getXRot());
            return e;
        });
        storedMob = null;
        setDisplay(null, 0.0f, 0.0f, false);
        return entity;
    }

    /**
     * Reconstructs and spawns the stored mob near {@code pos}. Water-breathing mobs (axolotl, fish,
     * squid, …) are placed into a water block at/around {@code pos} so they don't suffocate.
     *
     * @return true if a mob was released
     */
    public boolean releaseStoredNear(ServerLevel level, BlockPos pos) {
        Entity entity = takeStoredEntity(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        if (entity == null) {
            return false;
        }
        if (needsWater(entity)) {
            BlockPos water = findWaterNear(level, pos);
            if (water != null) {
                entity.moveTo(water.getX() + 0.5, water.getY() + 0.5, water.getZ() + 0.5,
                        entity.getYRot(), entity.getXRot());
            }
        }
        level.addFreshEntity(entity);
        return true;
    }

    /** Releases the stored mob (used when the block is broken). */
    public void dropStored(ServerLevel level, BlockPos pos) {
        releaseStoredNear(level, pos);
    }

    private static boolean needsWater(Entity entity) {
        return entity instanceof WaterAnimal
                || entity instanceof Axolotl
                || (entity instanceof LivingEntity living && living.canBreatheUnderwater());
    }

    @Nullable
    private static BlockPos findWaterNear(Level level, BlockPos center) {
        if (level.getFluidState(center).is(FluidTags.WATER)) {
            return center;
        }
        for (Direction dir : Direction.values()) {
            BlockPos p = center.relative(dir);
            if (level.getFluidState(p).is(FluidTags.WATER)) {
                return p;
            }
        }
        return null;
    }

    // ---- Shared detection helpers ----

    @Nullable
    protected Mob findPenMob(ServerLevel level, BlockPos penPos) {
        AABB box = new AABB(penPos).expandTowards(0.0, 1.0, 0.0);
        List<Mob> mobs = level.getEntitiesOfClass(Mob.class, box,
                m -> m.isAlive() && !m.isPassenger());
        return nearest(mobs, penPos);
    }

    @Nullable
    protected AbstractMinecart findCart(ServerLevel level, BlockPos cartPos) {
        AABB box = new AABB(cartPos).inflate(0.4);
        List<AbstractMinecart> carts = level.getEntitiesOfClass(AbstractMinecart.class, box);
        return nearest(carts, cartPos);
    }

    protected static boolean isParked(AbstractMinecart cart) {
        return cart.getDeltaMovement().horizontalDistanceSqr() < PARKED_EPSILON;
    }

    @Nullable
    protected static Mob firstMobPassenger(AbstractMinecart cart) {
        for (Entity passenger : cart.getPassengers()) {
            if (passenger instanceof Mob mob) {
                return mob;
            }
        }
        return null;
    }

    @Nullable
    protected static <E extends Entity> E nearest(List<E> entities, BlockPos around) {
        E best = null;
        double bestDist = Double.MAX_VALUE;
        double cx = around.getX() + 0.5;
        double cy = around.getY() + 0.5;
        double cz = around.getZ() + 0.5;
        for (E entity : entities) {
            double d = entity.distanceToSqr(cx, cy, cz);
            if (d < bestDist) {
                bestDist = d;
                best = entity;
            }
        }
        return best;
    }

    // ---- Display state ----

    private void setDisplay(@Nullable EntityType<?> type, float health, float maxHealth, boolean baby) {
        boolean changed = type != displayType
                || Float.compare(health, mobHealth) != 0
                || Float.compare(maxHealth, mobMaxHealth) != 0
                || baby != displayBaby;
        if (!changed) {
            return;
        }
        displayType = type;
        mobHealth = health;
        mobMaxHealth = maxHealth;
        displayBaby = baby;
        setChanged();
        syncToClient();
    }

    protected void syncToClient() {
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
        }
    }

    @Nullable
    public EntityType<?> getDisplayType() {
        return displayType;
    }

    public float getMobHealth() {
        return mobHealth;
    }

    public float getMobMaxHealth() {
        return mobMaxHealth;
    }

    public boolean isDisplayBaby() {
        return displayBaby;
    }

    /**
     * Client-only: a cached, non-ticking entity of {@link #displayType} for the spinning mini-mob
     * (mirrors {@code BaseSpawner.getOrCreateDisplayEntity}). Rebuilt on type change.
     */
    @Nullable
    public Entity getOrCreateDisplayEntity() {
        if (level == null || displayType == null) {
            cachedDisplayEntity = null;
            cachedEntityType = null;
            return null;
        }
        if (cachedDisplayEntity == null || cachedEntityType != displayType) {
            cachedEntityType = displayType;
            cachedDisplayEntity = displayType.create(level);
            if (cachedDisplayEntity instanceof net.minecraft.world.entity.AgeableMob ageable) {
                ageable.setBaby(displayBaby);
            }
        }
        return cachedDisplayEntity;
    }

    // ---- NBT / sync ----

    private void writeDisplay(CompoundTag tag) {
        boolean hasMob = displayType != null;
        tag.putBoolean("HasMob", hasMob);
        if (hasMob) {
            tag.putString("DisplayMob", BuiltInRegistries.ENTITY_TYPE.getKey(displayType).toString());
            tag.putFloat("MobHealth", mobHealth);
            tag.putFloat("MobMaxHealth", mobMaxHealth);
            tag.putBoolean("MobBaby", displayBaby);
        }
    }

    private void readDisplay(CompoundTag tag) {
        ResourceLocation id = tag.getBoolean("HasMob")
                ? ResourceLocation.tryParse(tag.getString("DisplayMob")) : null;
        if (id != null) {
            displayType = BuiltInRegistries.ENTITY_TYPE.getOptional(id).orElse(null);
            mobHealth = tag.getFloat("MobHealth");
            mobMaxHealth = tag.getFloat("MobMaxHealth");
            displayBaby = tag.getBoolean("MobBaby");
        } else {
            displayType = null;
            mobHealth = 0.0f;
            mobMaxHealth = 0.0f;
            displayBaby = false;
        }
        cachedDisplayEntity = null;
        cachedEntityType = null;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        writeDisplay(tag);
        if (storedMob != null) {
            tag.put("StoredMob", storedMob.copy());
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        readDisplay(tag);
        // Full mob NBT is disk-only (never sent in the update packet), so it survives client syncs.
        storedMob = tag.contains("StoredMob") ? tag.getCompound("StoredMob") : storedMob;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        // Client only needs the lightweight display state, not the full stored-mob NBT.
        CompoundTag tag = new CompoundTag();
        writeDisplay(tag);
        return tag;
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
