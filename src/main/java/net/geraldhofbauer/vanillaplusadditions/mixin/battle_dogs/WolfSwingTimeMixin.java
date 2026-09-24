package net.geraldhofbauer.vanillaplusadditions.mixin.battle_dogs;

import net.geraldhofbauer.vanillaplusadditions.modules.battle_dogs.BattleDogsModule;
import net.minecraft.world.entity.animal.Wolf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Keeps a wolf's swing timer running, the way {@code Monster.aiStep} does for every hostile mob.
 *
 * <p>Without this the bite animation cannot work at all, and the reason is easy to miss:
 * {@code LivingEntity.updateSwingTime()} is called from exactly three places in vanilla --
 * {@code Player}, {@code RemotePlayer} and {@code Monster.aiStep}. A wolf is a
 * {@code TamableAnimal}, so none of them applies. Its {@code swingTime} never advances and
 * {@code attackAnim} is therefore pinned at 0 forever, which is also why vanilla ships no wolf
 * attack animation: the machinery an animation would read is never wound up.
 *
 * <p>What this does <em>not</em> fix is packet loss, and it is worth writing down because the
 * opposite is easy to assume: {@code swinging} is set by {@code swing()} and cleared only inside
 * {@code updateSwingTime()}, so on an untended wolf it stays up forever - but {@code swing()} does
 * not care. It also writes {@code swingTime = -1} (LivingEntity.java:1865) and its own guard accepts
 * {@code swingTime < 0} (LivingEntity.java:1864), so every bite re-enters and re-broadcasts its
 * {@code ClientboundAnimatePacket} regardless. Bites were never swallowed; they were invisible.
 * This still runs on <b>both</b> sides, because {@code attackAnim} is what the renderer reads and
 * the server side keeps the two in step.
 *
 * <p>Two mixin constraints shape this class, and both cost a debugging round to learn:
 * <ul>
 *   <li>The injection targets {@code Wolf.aiStep} rather than {@code LivingEntity.aiStep} because
 *       Mixin needs the target method declared in the target class, and {@code Wolf} declares its
 *       own {@code aiStep} for the wet-shake.</li>
 *   <li>The timer logic is inlined instead of {@code @Shadow}-ing {@code updateSwingTime()}:
 *       {@code @Shadow} resolves only against the target class, never its supertypes, so shadowing
 *       an inherited method fails the whole mixin -- silently as far as gameplay is concerned, with
 *       only an {@code InvalidMixinException} buried in the log. Every field this needs happens to
 *       be public on {@code LivingEntity}, so nothing is lost.</li>
 * </ul>
 * {@code oAttackAnim} needs no help: {@code LivingEntity.baseTick} carries it for every entity.
 */
@Mixin(Wolf.class)
public abstract class WolfSwingTimeMixin {

    /** Diagnostic latch: says once whether this mixin runs at all, bite or no bite. */
    private static boolean announced;

    @Inject(method = "aiStep", at = @At("HEAD"))
    private void maintainSwingTime(CallbackInfo ci) {
        if (!BattleDogsModule.isModuleActive()) {
            return;
        }
        Wolf self = (Wolf) (Object) this;
        if (!announced) {
            announced = true;
            BattleDogsModule.debug("[bite] Wolf.aiStep reached, swing timer is being maintained");
        }

        int duration = self.getCurrentSwingDuration();
        if (self.swinging) {
            self.swingTime++;
            if (self.swingTime >= duration) {
                self.swingTime = 0;
                self.swinging = false;
            }
        } else {
            self.swingTime = 0;
        }
        self.attackAnim = (float) self.swingTime / (float) duration;
    }
}
