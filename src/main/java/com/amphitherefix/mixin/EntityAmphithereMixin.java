package com.amphitherefix.mixin;

import com.github.alexthe666.iceandfire.entity.EntityAmphithere;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Fixes Ice and Fire Amphitheres becoming unreachable after a midair dismount.
 *
 * <p>Root cause: {@code EntityAmphithere.getPositionRelativetoGround} computes its ground
 * target with inverted math (it returns a point ABOVE the Amphithere's current Y instead of
 * just above the ground it found), and the flying branch of {@code travel()} applies no
 * gravity. A dismounted Amphithere therefore stays in "flying" mode with nothing reliably
 * pulling it down: its fly AI can drive it upward / keep it hovering at altitude, and once it
 * does reach the ground its own AI can launch it back into the sky.</p>
 *
 * <p>The fix: (1) correct the ground target so the fly AI glides toward the ground, (2)
 * reliably force a dismounted Amphithere down and keep it grounded until it is ridden again
 * or re-commanded, and (3) as a safety net, gently descend any tamed, riderless Amphithere
 * that is idly hovering with no active flight waypoint.</p>
 */
@Mixin(EntityAmphithere.class)
public abstract class EntityAmphithereMixin {

    // Set on the server when a rider leaves the Amphithere; cleared once it is ridden again or
    // its command is changed (re-commanded). While set the Amphithere is forced down and kept
    // grounded so a midair dismount can never leave it floating / climbing into the sky.
    @Unique
    private boolean amphitherefix$landing = false;
    @Unique
    private boolean amphitherefix$wasRidden = false;
    @Unique
    private int amphitherefix$lastCommand = -1;

    // Ice and Fire keeps its own field name for this (not SRG-renamed). Overriding it while a
    // dismounted Amphithere is landing stops the fly AI (follow/wander/circle) from keeping it
    // airborne; aiStep's FlightBehavior.NONE branch then glides it toward the ground instead.
    @Shadow(remap = false)
    private EntityAmphithere.FlightBehavior flightBehavior;

    @Inject(method = "getPositionRelativetoGround", at = @At("HEAD"), cancellable = true, remap = false)
    private static void amphitherefix$correctGroundTarget(Entity entity, Level world, int x, int z, RandomSource rand, CallbackInfoReturnable<BlockPos> cir) {
        BlockPos pos = new BlockPos(x, entity.getBlockY(), z);
        // Start the downward scan at 1 (not 0) so an Amphithere embedded in solid terrain
        // (e.g. a forest canopy) never gets a target ABOVE its own position, which would make
        // it climb instead of settling.
        for (int i = 1; i < 6 + rand.nextInt(6); ++i) {
            if (world.isEmptyBlock(pos.below(i))) {
                continue;
            }
            cir.setReturnValue(pos.below(i).above());
            return;
        }
        // No ground within reach: drift gently downward instead of hovering at altitude.
        cir.setReturnValue(pos.below(2));
    }

    // "tickRidden" is the official (dev) name; "m_274498_" is the SRG name in the released Ice and Fire jar.
    // Ice and Fire's shipped beta jar calls setRot(vec2.x, vec2.y) (upstream is setRot(vec2.y, vec2.x)), which
    // drives the ridden Amphithere's body yaw from the player's pitch (and pitch from yaw). That makes the whole
    // body yaw-twitch during flight as the player's pitch wobbles. Correct it here to match upstream: yaw from
    // the player's yaw, pitch from half the player's pitch, keeping the renderer's prev/body/head yaw consistent.
    @Inject(method = {"tickRidden", "m_274498_"}, at = @At("TAIL"), remap = false)
    private void amphitherefix$fixRiddenRotation(Player player, Vec3 travelVector, CallbackInfo ci) {
        EntityAmphithere self = (EntityAmphithere) (Object) this;
        self.setYRot(player.getYRot());
        self.setXRot(player.getXRot() * 0.5f);
        self.yRotO = self.yBodyRot = self.yHeadRot = self.getYRot();
    }

    // "tick" is the official (dev) name; "m_8119_" is the SRG name in the released Ice and Fire jar.
    // Detect a dismount server-side via the passenger transition so the Amphithere is reliably
    // forced down even when the client's dismount control bit was already cleared.
    @Inject(method = {"tick", "m_8119_"}, at = @At("HEAD"), remap = false)
    private void amphitherefix$trackDismount(CallbackInfo ci) {
        EntityAmphithere self = (EntityAmphithere) (Object) this;
        if (self.level().isClientSide) {
            return;
        }
        boolean ridden = !self.getPassengers().isEmpty();
        if (ridden) {
            amphitherefix$landing = false;
        } else if (amphitherefix$wasRidden) {
            amphitherefix$landing = true;
        }
        // A command change means the player re-commanded the Amphithere, releasing it to fly again.
        if (self.getCommand() != amphitherefix$lastCommand) {
            amphitherefix$lastCommand = self.getCommand();
            amphitherefix$landing = false;
        }
        amphitherefix$wasRidden = ridden;

        if (amphitherefix$landing && !ridden) {
            // Stop the fly AI (follow/wander/circle) from keeping a dismounted Amphithere airborne.
            if (this.flightBehavior != EntityAmphithere.FlightBehavior.NONE) {
                this.flightBehavior = EntityAmphithere.FlightBehavior.NONE;
            }
            // Aim its flight at a point below so it glides down instead of hovering/climbing.
            if (self.isFlying() && !self.onGround()) {
                self.getMoveControl().setWantedPosition(self.getX(), self.getY() - 10, self.getZ(), 0.5D);
            }
        } else if (!self.isFallen && this.flightBehavior == EntityAmphithere.FlightBehavior.NONE) {
            this.flightBehavior = EntityAmphithere.FlightBehavior.WANDER;
        }
    }

    // "aiStep" is the official (dev) name; "m_8107_" is the SRG name in the released Ice and Fire jar.
    // While landing, cancel any grounded re-takeoff the Amphithere's own AI tries to start.
    @Inject(method = {"aiStep", "m_8107_"}, at = @At("TAIL"), remap = false)
    private void amphitherefix$preventRetakeoff(CallbackInfo ci) {
        EntityAmphithere self = (EntityAmphithere) (Object) this;
        if (amphitherefix$landing && self.isFlying() && self.onGround() && self.getPassengers().isEmpty()) {
            self.setFlying(false);
        }
    }

    // While landing, never let the Amphithere accumulate upward velocity (e.g. from Ice and
    // Fire's own stale ride-control takeoff in tick()), so it can't be launched back into the
    // sky even for a moment.
    @Inject(method = {"tick", "m_8119_"}, at = @At("TAIL"), remap = false)
    private void amphitherefix$nullifyUpwardMotionWhileLanding(CallbackInfo ci) {
        EntityAmphithere self = (EntityAmphithere) (Object) this;
        if (amphitherefix$landing && !self.level().isClientSide && self.getPassengers().isEmpty()) {
            Vec3 motion = self.getDeltaMovement();
            if (motion.y > 0.0) {
                self.setDeltaMovement(motion.x, 0.0, motion.z);
            }
        }
    }

    // "travel" is the official (dev) name; "m_7023_" is the SRG name in the released Ice and Fire jar.
    @Inject(method = {"travel", "m_7023_"}, at = @At("TAIL"), remap = false)
    private void amphitherefix$glideDownAfterMidairDismount(Vec3 travelVector, CallbackInfo ci) {
        EntityAmphithere self = (EntityAmphithere) (Object) this;
        if (self.isFlying() && !self.onGround() && self.getPassengers().isEmpty()) {
            double descent = 0.0;
            if (amphitherefix$landing) {
                // Recently dismounted: firmly glide down until grounded.
                descent = 0.5;
            } else if (self.dismountIAF() || self.isOrderedToSit()) {
                // Racy dismount control bit or airborne sit-command: gentle glide down.
                descent = 0.2;
            } else if (self.isTame() && !self.getMoveControl().hasWanted()) {
                // Tamed and idle in the air (no active flight waypoint): settle toward the
                // ground instead of hovering in place forever.
                descent = 0.3;
            }
            if (descent > 0.0) {
                Vec3 motion = self.getDeltaMovement();
                self.setDeltaMovement(motion.x, motion.y - descent, motion.z);
            }
        }
    }
}