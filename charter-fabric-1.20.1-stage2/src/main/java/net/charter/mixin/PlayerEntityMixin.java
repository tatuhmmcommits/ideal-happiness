package net.charter.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.charter.body.BodyMirror;
import net.charter.body.BodyShape;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.player.PlayerEntity;

/**
 * A player who has lost both legs sits on the ground; one who has lost everything but the head is only as big as
 * that head. The hitbox and the eye height are decided here, on both the server and the client, from the lost
 * parts that {@link BodyMirror} knows about.
 */
@Mixin(PlayerEntity.class)
public abstract class PlayerEntityMixin {
	@Inject(method = "getDimensions(Lnet/minecraft/entity/EntityPose;)Lnet/minecraft/entity/EntityDimensions;", at = @At("RETURN"), cancellable = true)
	private void charter$bodyDimensions(EntityPose pose, CallbackInfoReturnable<EntityDimensions> cir) {
		if (pose == EntityPose.SLEEPING || pose == EntityPose.DYING) {
			return;
		}
		int mask = BodyMirror.mask(((PlayerEntity) (Object) this).getUuid());
		if (mask == 0) {
			return;
		}
		EntityDimensions shaped = BodyShape.dimensions(mask);
		if (shaped != null) {
			cir.setReturnValue(shaped);
		}
	}

	// PlayerEntity declares getActiveEyeHeight; LivingEntity.getEyeHeight(pose, dimensions) is final and calls it
	@Inject(method = "getActiveEyeHeight(Lnet/minecraft/entity/EntityPose;Lnet/minecraft/entity/EntityDimensions;)F", at = @At("RETURN"), cancellable = true)
	private void charter$bodyEyeHeight(EntityPose pose, EntityDimensions dimensions, CallbackInfoReturnable<Float> cir) {
		if (pose == EntityPose.SLEEPING || pose == EntityPose.DYING) {
			return;
		}
		int mask = BodyMirror.mask(((PlayerEntity) (Object) this).getUuid());
		if (mask == 0) {
			return;
		}
		float eyes = BodyShape.eyeHeight(mask);
		if (eyes > 0.0F) {
			cir.setReturnValue(eyes);
		}
	}
}
