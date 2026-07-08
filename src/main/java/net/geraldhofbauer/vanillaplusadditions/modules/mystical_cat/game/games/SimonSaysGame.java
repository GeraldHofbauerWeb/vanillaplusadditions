package net.geraldhofbauer.vanillaplusadditions.modules.mystical_cat.game.games;

import net.geraldhofbauer.vanillaplusadditions.modules.mystical_cat.entity.MysticalCatEntity;
import net.geraldhofbauer.vanillaplusadditions.modules.mystical_cat.game.GameSession;
import net.geraldhofbauer.vanillaplusadditions.modules.mystical_cat.game.MiniGame;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Echoing Lights (Simon Says): four coloured tiles appear around the cat and flash a growing
 * sequence with tones; the player repeats it by right-clicking the tiles. Three rounds of length
 * 3, 4 and 5 — one wrong tile ends the trial. The tiles are temporary and always restored.
 */
public class SimonSaysGame implements MiniGame {

    private static final int[] OFF_X = {2, 0, -2, 0};
    private static final int[] OFF_Z = {0, 2, 0, -2};
    private static final float[] PITCH = {0.6f, 0.9f, 1.2f, 1.5f};
    private static final Vector3f[] COLOR = {
            new Vector3f(1.0f, 0.2f, 0.2f), new Vector3f(0.3f, 1.0f, 0.3f),
            new Vector3f(0.3f, 0.6f, 1.0f), new Vector3f(1.0f, 1.0f, 0.3f)
    };
    private static final int[] ROUND_LENGTHS = {3, 4, 5};

    private enum Phase { SHOW, INPUT }

    private static final class State {
        private final BlockPos[] pads = new BlockPos[4];
        private final List<Integer> sequence = new ArrayList<>();
        private int round;
        private Phase phase = Phase.SHOW;
        private int showStep;
        private int inputIndex;
        private long nextStepTick;
    }

    @Override
    public String id() {
        return "simon_says";
    }

    @Override
    public boolean canRunAt(ServerLevel level, MysticalCatEntity cat) {
        BlockPos c = cat.blockPosition();
        for (int i = 0; i < 4; i++) {
            int x = c.getX() + OFF_X[i];
            int z = c.getZ() + OFF_Z[i];
            int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            if (!level.getBlockState(new BlockPos(x, y, z)).canBeReplaced()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void start(GameSession ctx) {
        State state = new State();
        BlockPos c = ctx.center();
        Block[] blocks = {
                Blocks.RED_GLAZED_TERRACOTTA, Blocks.LIME_GLAZED_TERRACOTTA,
                Blocks.LIGHT_BLUE_GLAZED_TERRACOTTA, Blocks.YELLOW_GLAZED_TERRACOTTA
        };
        for (int i = 0; i < 4; i++) {
            int x = c.getX() + OFF_X[i];
            int z = c.getZ() + OFF_Z[i];
            int y = ctx.groundY(x, z) - 1;
            BlockPos pad = new BlockPos(x, y, z);
            state.pads[i] = pad;
            ctx.placeTempBlock(pad, blocks[i].defaultBlockState());
        }
        for (int i = 0; i < ROUND_LENGTHS[0]; i++) {
            state.sequence.add(ctx.random().nextInt(4));
        }
        state.nextStepTick = ctx.level().getGameTime() + 25;
        ctx.setState(state);
        ctx.gameMessage("start", ROUND_LENGTHS.length);
    }

    @Override
    public void tick(GameSession ctx) {
        State state = ctx.state();
        if (state == null || state.phase != Phase.SHOW) {
            return;
        }
        long now = ctx.level().getGameTime();
        if (now < state.nextStepTick) {
            return;
        }
        if (state.showStep < state.sequence.size()) {
            flash(ctx, state, state.sequence.get(state.showStep));
            state.showStep++;
            state.nextStepTick = now + 14;
        } else {
            state.phase = Phase.INPUT;
            state.inputIndex = 0;
            ctx.gameActionBar("start", ROUND_LENGTHS.length);
        }
    }

    @Override
    public void onBlockRightClick(GameSession ctx, ServerPlayer player, BlockPos pos) {
        State state = ctx.state();
        if (state == null || state.phase != Phase.INPUT) {
            return;
        }
        int pad = -1;
        for (int i = 0; i < 4; i++) {
            if (state.pads[i].equals(pos)) {
                pad = i;
                break;
            }
        }
        if (pad < 0) {
            return;
        }
        if (pad != state.sequence.get(state.inputIndex)) {
            flash(ctx, state, pad);
            ctx.lose();
            return;
        }
        flash(ctx, state, pad);
        state.inputIndex++;
        if (state.inputIndex < state.sequence.size()) {
            return;
        }
        // Round complete.
        state.round++;
        if (state.round >= ROUND_LENGTHS.length) {
            ctx.win();
            return;
        }
        while (state.sequence.size() < ROUND_LENGTHS[state.round]) {
            state.sequence.add(ctx.random().nextInt(4));
        }
        state.phase = Phase.SHOW;
        state.showStep = 0;
        state.nextStepTick = ctx.level().getGameTime() + 20;
    }

    private void flash(GameSession ctx, State state, int pad) {
        Vec3 above = new Vec3(state.pads[pad].getX() + 0.5, state.pads[pad].getY() + 1.1,
                state.pads[pad].getZ() + 0.5);
        ctx.particles(new DustParticleOptions(COLOR[pad], 1.6f), above, 18, 0.25, 0.0);
        ctx.soundAt(above, SoundEvents.NOTE_BLOCK_HARP.value(), 1.0f, PITCH[pad]);
    }
}
