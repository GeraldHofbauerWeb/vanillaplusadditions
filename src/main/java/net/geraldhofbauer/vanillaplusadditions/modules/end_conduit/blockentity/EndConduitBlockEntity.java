package net.geraldhofbauer.vanillaplusadditions.modules.end_conduit.blockentity;

import com.google.common.collect.Lists;
import net.geraldhofbauer.vanillaplusadditions.modules.end_conduit.EndConduitModule;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Block entity for the End Conduit — a simplified, End-only variant of the vanilla
 * {@link net.minecraft.world.level.block.entity.ConduitBlockEntity}. The tick logic mirrors vanilla
 * closely (same frame ring geometry, activation cadence, ambient sounds, nautilus particles, active
 * rotation) but with three deliberate changes:
 * <ul>
 *     <li><b>Frame blocks:</b> Glowstone, End Stone, End Stone Bricks, Sea Lantern — not prismarine.</li>
 *     <li><b>No water:</b> the inner 3×3×3 "must be water" requirement is dropped; the conduit forms
 *         and grants Conduit Power on dry land.</li>
 *     <li><b>End-only + no hunting:</b> only activates in {@code minecraft:the_end}; it never attacks
 *         mobs (the {@code isHunting} flag survives purely to open the render eye at full size).</li>
 * </ul>
 *
 * <p>Both sides recompute the shape independently (like vanilla), so activation state, rotation and
 * hunting flag need no packet sync — the renderer reads the live values this ticker maintains.
 */
public class EndConduitBlockEntity extends BlockEntity {

    private static final int MIN_HUNT_SIZE = 42; // cosmetic only: opens the render eye at full size
    private static final Block[] VALID_FRAME = {
            Blocks.GLOWSTONE, Blocks.END_STONE, Blocks.END_STONE_BRICKS, Blocks.SEA_LANTERN
    };

    private int tickCount;
    private float activeRotation;
    private boolean isActive;
    private boolean isHunting;
    private final List<BlockPos> effectBlocks = Lists.newArrayList();
    private long nextAmbientSoundActivation;

    public EndConduitBlockEntity(BlockPos pos, BlockState state) {
        super(EndConduitModule.END_CONDUIT_BE.get(), pos, state);
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state, EndConduitBlockEntity blockEntity) {
        blockEntity.tickCount++;
        long time = level.getGameTime();
        List<BlockPos> list = blockEntity.effectBlocks;
        if (time % 40L == 0L) {
            blockEntity.isActive = updateShape(level, pos, list) && isEnabledHere(level);
            updateHunting(blockEntity, list);
        }

        if (blockEntity.isActive()) {
            animationTick(level, pos, list, blockEntity.tickCount);
            blockEntity.activeRotation++;
        }
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, EndConduitBlockEntity blockEntity) {
        blockEntity.tickCount++;
        long time = level.getGameTime();
        List<BlockPos> list = blockEntity.effectBlocks;
        if (time % 40L == 0L) {
            boolean active = updateShape(level, pos, list) && isEnabledHere(level);
            if (active != blockEntity.isActive) {
                SoundEvent sound = active ? SoundEvents.CONDUIT_ACTIVATE : SoundEvents.CONDUIT_DEACTIVATE;
                level.playSound(null, pos, sound, SoundSource.BLOCKS, 1.0F, 1.0F);
            }
            blockEntity.isActive = active;
            updateHunting(blockEntity, list);
            if (active) {
                applyEffects(level, pos, list);
            }
        }

        if (blockEntity.isActive()) {
            if (time % 80L == 0L) {
                level.playSound(null, pos, SoundEvents.CONDUIT_AMBIENT, SoundSource.BLOCKS, 1.0F, 1.0F);
            }
            if (time > blockEntity.nextAmbientSoundActivation) {
                blockEntity.nextAmbientSoundActivation = time + 60L + level.getRandom().nextInt(40);
                level.playSound(null, pos, SoundEvents.CONDUIT_AMBIENT_SHORT, SoundSource.BLOCKS, 1.0F, 1.0F);
            }
        }
    }

    /** Active only in the End and while the module is enabled. */
    private static boolean isEnabledHere(Level level) {
        return level.dimension() == Level.END && EndConduitModule.isModuleActive();
    }

    private static void updateHunting(EndConduitBlockEntity blockEntity, List<BlockPos> positions) {
        blockEntity.setHunting(positions.size() >= MIN_HUNT_SIZE);
    }

    /**
     * Detects the conduit frame ring (identical geometry to vanilla) but accepting the End frame
     * block set and with no water requirement. Populates {@code positions} and returns whether the
     * count reaches the configured activation threshold.
     */
    private static boolean updateShape(Level level, BlockPos pos, List<BlockPos> positions) {
        positions.clear();

        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                for (int dz = -2; dz <= 2; dz++) {
                    int ax = Math.abs(dx);
                    int ay = Math.abs(dy);
                    int az = Math.abs(dz);
                    if ((ax > 1 || ay > 1 || az > 1)
                            && (dx == 0 && (ay == 2 || az == 2)
                                || dy == 0 && (ax == 2 || az == 2)
                                || dz == 0 && (ax == 2 || ay == 2))) {
                        BlockPos framePos = pos.offset(dx, dy, dz);
                        if (isFrameBlock(level.getBlockState(framePos))) {
                            positions.add(framePos);
                        }
                    }
                }
            }
        }

        return positions.size() >= EndConduitModule.minFrames();
    }

    private static boolean isFrameBlock(BlockState state) {
        for (Block block : VALID_FRAME) {
            if (state.is(block)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Grants Conduit Power to players within the (frame-scaled) radius — with no water/rain
     * requirement, so it works on dry End ground.
     */
    private static void applyEffects(Level level, BlockPos pos, List<BlockPos> positions) {
        int radius = EndConduitModule.effectRadius(positions.size());
        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();
        AABB aabb = new AABB(x, y, z, x + 1, y + 1, z + 1)
                .inflate(radius)
                .expandTowards(0.0, level.getHeight(), 0.0);
        List<Player> players = level.getEntitiesOfClass(Player.class, aabb);
        if (players.isEmpty()) {
            return;
        }
        for (Player player : players) {
            if (pos.closerThan(player.blockPosition(), radius)) {
                player.addEffect(new MobEffectInstance(MobEffects.CONDUIT_POWER, 260, 0, true, true));
            }
        }
    }

    private static void animationTick(Level level, BlockPos pos, List<BlockPos> positions, int tickCount) {
        RandomSource random = level.getRandom();
        double bob = Mth.sin((float) (tickCount + 35) * 0.1F) / 2.0F + 0.5F;
        bob = (bob * bob + bob) * 0.3F;
        Vec3 origin = new Vec3(pos.getX() + 0.5, pos.getY() + 1.5 + bob, pos.getZ() + 0.5);

        for (BlockPos framePos : positions) {
            if (random.nextInt(50) == 0) {
                BlockPos rel = framePos.subtract(pos);
                float dx = -0.5F + random.nextFloat() + (float) rel.getX();
                float dy = -2.0F + random.nextFloat() + (float) rel.getY();
                float dz = -0.5F + random.nextFloat() + (float) rel.getZ();
                level.addParticle(ParticleTypes.NAUTILUS, origin.x, origin.y, origin.z, dx, dy, dz);
            }
        }
    }

    public int getTickCount() {
        return this.tickCount;
    }

    public boolean isActive() {
        return this.isActive;
    }

    public boolean isHunting() {
        return this.isHunting;
    }

    private void setHunting(boolean hunting) {
        this.isHunting = hunting;
    }

    public float getActiveRotation(float partialTick) {
        return (this.activeRotation + partialTick) * -0.0375F;
    }
}
