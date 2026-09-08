package net.geraldhofbauer.vanillaplusadditions.modules.battle_dogs.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.geraldhofbauer.vanillaplusadditions.modules.battle_dogs.BattleDogsModule;
import net.geraldhofbauer.vanillaplusadditions.modules.battle_dogs.network.BiteDirections;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.animal.Wolf;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLivingEvent;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Shoves the whole wolf forward and down while it bites.
 *
 * <p>Companion to {@code WolfBiteAnimationMixin}, and the half that survives contact with the
 * modpack. The mixin's head snap is correct and demonstrably runs — but Entity Model Features
 * re-applies its own part rotations from the Fresh Animations {@code wolf.jem} <em>after</em>
 * {@code setupAnim} returns, so on any client with that resource pack the snap is overwritten
 * before it ever reaches the screen. Confirmed the slow way: with Fresh Animations off the head
 * moves, with it on nothing does, while the log shows identical values either way.
 *
 * <p>This runs outside the model entirely, on the render pose, where EMF has nothing to say. It is
 * deliberately a translation and not a rotation: a rotation here would land in world space, before
 * {@code setupRotations} applies body yaw, so it would need the yaw undone and re-applied and would
 * tip the wrong way on half the compass. A lunge along the facing needs one direction vector and
 * cannot be subtly wrong.
 *
 * <p>The offset is scaled by the wolf's {@code generic.scale}, so a 3.25-scale mount lunges like a
 * mount rather than like a puppy, and it follows the <em>head</em> yaw so the jab goes at whatever
 * is being bitten rather than straight off the wolf's chest.
 */
@EventBusSubscriber(value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME)
public final class WolfBiteLunge {

    /** Forward travel at full swing, in blocks, before the entity's own scale is applied. */
    private static final float LUNGE_FORWARD = 0.32F;
    /** Downward dip at full swing, same units. Smaller: this is a jab, not a bow. */
    private static final float LUNGE_DOWN = 0.10F;

    private WolfBiteLunge() {
    }

    /**
     * Whether each bracketed render pushed a pose, so Post pops exactly what Pre pushed.
     *
     * <p>A plain boolean would do for one wolf at a time, but a deque costs nothing and survives a
     * nested render. Pre runs at LOWEST and Post at HIGHEST so that a cancellation by any other
     * handler happens before we push -- a leaked push would corrupt every entity drawn afterwards.
     */
    private static final Deque<Boolean> PUSHED = new ArrayDeque<>();

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRenderPre(RenderLivingEvent.Pre<?, ?> event) {
        if (!(event.getEntity() instanceof Wolf wolf)) {
            return;
        }
        float curve = biteCurve(wolf);
        if (curve <= 0.0F) {
            PUSHED.push(Boolean.FALSE);
            return;
        }
        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        PUSHED.push(Boolean.TRUE);

        float scale = wolf.getScale();
        // Head yaw, not body yaw: the look control keeps the head on the target, so this aims the
        // lunge at whatever is being bitten. getTarget() is no use here -- a mob's target is
        // server-side AI state and is never synced, so on the client it is simply null.
        // The server reports the real direction per bite. Head yaw is only the fallback, and a poor
        // one for the case that matters: while the wolf is ridden, tickRidden overwrites head and
        // body rotation from the rider's look every tick, so the head points where the rider looks
        // and never at the victim.
        float headYaw = Mth.rotLerp(partialTick(), wolf.yHeadRotO, wolf.yHeadRot);
        double yawRad = Math.toRadians(BiteDirections.yawOr(wolf.getId(), headYaw));
        poseStack.translate(
                -Math.sin(yawRad) * LUNGE_FORWARD * curve * scale,
                -LUNGE_DOWN * curve * scale,
                Math.cos(yawRad) * LUNGE_FORWARD * curve * scale);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRenderPost(RenderLivingEvent.Post<?, ?> event) {
        if (!(event.getEntity() instanceof Wolf) || PUSHED.isEmpty()) {
            return;
        }
        if (Boolean.TRUE.equals(PUSHED.pop())) {
            event.getPoseStack().popPose();
        }
    }

    /**
     * The 0 to 1 to 0 shape of a single bite, or 0 when none is in flight.
     *
     * <p>Reads the same {@code attackAnim} ramp the head snap uses, which only exists at all
     * because {@code WolfSwingTimeMixin} winds up a timer vanilla leaves stopped for wolves.
     */
    private static float biteCurve(Wolf wolf) {
        if (!BattleDogsModule.isModuleActive() || !BattleDogsModule.isBiteAnimation()) {
            return 0.0F;
        }
        if (BattleDogsModule.isBiteAnimationOnlyWhenRidden() && !wolf.isVehicle()) {
            return 0.0F;
        }
        float swing = wolf.getAttackAnim(partialTick());
        if (swing <= 0.0F) {
            return 0.0F;
        }
        return Mth.sin(swing * Mth.PI) * (float) BattleDogsModule.getBiteAnimationStrength();
    }

    private static float partialTick() {
        return Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
    }
}
