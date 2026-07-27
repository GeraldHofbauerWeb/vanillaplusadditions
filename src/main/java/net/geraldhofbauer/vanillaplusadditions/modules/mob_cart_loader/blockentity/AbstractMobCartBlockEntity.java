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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
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
 * Shared block-entity logic for the mob loader/unloader: throttled scanning of the adjacent rail
 * (front / {@link AbstractMobCartBlock#FACING}) and pen (opposite side), plus tracking of the
 * relevant mob for the spinning mini-mob renderer and the goggle stats panel. The relevant-mob
 * source and the load/unload action differ per subclass ({@link #tickServer}).
 *
 * <p>Contains no Create references; the goggle panel that reads {@link #getDisplayType()} lives in a
 * separate client handler gated on Create's goggles.</p>
 */
public abstract class AbstractMobCartBlockEntity extends BlockEntity {

    /** Cart is considered "parked" below this squared horizontal speed. */
    protected static final double PARKED_EPSILON = 1.0E-4;

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

    /**
     * Server ticker entry point (registered by the block). Throttled by the module's configured
     * interval, then dispatched to the subclass {@link #tickServer}.
     */
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

    /** Per-scan server logic: update the display mob and (if active) load/unload. */
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

    // ---- Shared detection helpers ----

    /** Nearest living, non-riding mob standing in the pen column (feet block + one above). */
    @Nullable
    protected Mob findPenMob(ServerLevel level, BlockPos penPos) {
        AABB box = new AABB(penPos).expandTowards(0.0, 1.0, 0.0);
        List<Mob> mobs = level.getEntitiesOfClass(Mob.class, box,
                m -> m.isAlive() && !m.isPassenger());
        return nearest(mobs, penPos);
    }

    /** Any minecart occupying the cart position. */
    @Nullable
    protected AbstractMinecart findCart(ServerLevel level, BlockPos cartPos) {
        AABB box = new AABB(cartPos).inflate(0.4);
        List<AbstractMinecart> carts = level.getEntitiesOfClass(AbstractMinecart.class, box);
        return nearest(carts, cartPos);
    }

    protected static boolean isParked(AbstractMinecart cart) {
        return cart.getDeltaMovement().horizontalDistanceSqr() < PARKED_EPSILON;
    }

    /** First mob passenger of the cart, if any (players/other entities are ignored). */
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

    /** Updates the displayed mob from a living entity (or clears it when {@code mob} is null). */
    protected void updateDisplayFrom(@Nullable LivingEntity mob) {
        if (mob == null) {
            updateDisplay(null, 0.0f, 0.0f, false);
        } else {
            updateDisplay(mob.getType(), mob.getHealth(), mob.getMaxHealth(), mob.isBaby());
        }
    }

    private void updateDisplay(@Nullable EntityType<?> type, float health, float maxHealth, boolean baby) {
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
     * Client-only: a cached, non-ticking entity instance of {@link #displayType} for rendering the
     * spinning mini-mob (mirrors {@code BaseSpawner.getOrCreateDisplayEntity}). Rebuilt on type change.
     *
     * @return the display entity, or null if no mob is currently shown
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

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        // Always write an explicit presence flag so an emptied display is unambiguously synced to
        // the client (an absent key could otherwise leave a stale mob showing).
        boolean hasMob = displayType != null;
        tag.putBoolean("HasMob", hasMob);
        if (hasMob) {
            tag.putString("DisplayMob", BuiltInRegistries.ENTITY_TYPE.getKey(displayType).toString());
            tag.putFloat("MobHealth", mobHealth);
            tag.putFloat("MobMaxHealth", mobMaxHealth);
            tag.putBoolean("MobBaby", displayBaby);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
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
        // Invalidate the render cache so the BER rebuilds from the new type.
        cachedDisplayEntity = null;
        cachedEntityType = null;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
