package net.charter.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.charter.body.BodyMirror;
import net.charter.body.BodyShape;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;

/**
 * A player who is only a torso and a head, or only a head, is drawn lower: their feet are gone, so the model sits
 * on the ground. This runs inside the model's own transform, so the name tag keeps following the (smaller) hitbox.
 */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {
	@Inject(method = "setupTransforms(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/client/util/math/MatrixStack;FFF)V", at = @At("HEAD"))
	private void charter$sitOnTheGround(LivingEntity entity, MatrixStack matrices, float animationProgress, float bodyYaw, float tickDelta, CallbackInfo ci) {
		if (!(entity instanceof PlayerEntity) || entity.isDead()) {
			return;
		}
		EntityPose pose = entity.getPose();
		if (pose != EntityPose.STANDING && pose != EntityPose.CROUCHING) {
			return;
		}
		float down = BodyShape.down(BodyMirror.mask(entity.getUuid()));
		if (down > 0.0F) {
			matrices.translate(0.0, (double) -down, 0.0);
		}
	}
}
