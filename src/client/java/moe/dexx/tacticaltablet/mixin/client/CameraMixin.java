package moe.dexx.tacticaltablet.mixin.client;

import moe.dexx.tacticaltablet.client.cinematic.Cinematic;
import moe.dexx.tacticaltablet.client.effects.StrikeEffects;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Lets the launch cinematic place the camera and lets nearby impacts shake it. */
@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow
    private boolean detached;
    @Shadow
    private float xRot;
    @Shadow
    private float yRot;
    @Shadow
    private Vec3 position;

    @Shadow
    protected abstract void setPosition(double x, double y, double z);

    @Shadow
    protected abstract void setRotation(float yRot, float xRot);

    @Inject(method = "setup", at = @At("TAIL"))
    private void tacticalTablet$afterSetup(BlockGetter level, Entity entity, boolean isDetached, boolean thirdPersonReverse,
                                           float partialTick, CallbackInfo callback) {
        Cinematic.Pose pose = Cinematic.worldPose(partialTick);
        if (pose != null) {
            setPosition(pose.position().x, pose.position().y, pose.position().z);
            setRotation(pose.yaw(), pose.pitch());
            // Detached, so the player's own body is drawn while the camera is away.
            detached = true;
        }
        float shake = StrikeEffects.shake(position, partialTick);
        if (shake > 0.0F) {
            double time = System.nanoTime() / 1.0e9;
            setRotation(yRot + shake * (float) Math.sin(time * 41.0), xRot + shake * (float) Math.cos(time * 33.0));
        }
    }
}
