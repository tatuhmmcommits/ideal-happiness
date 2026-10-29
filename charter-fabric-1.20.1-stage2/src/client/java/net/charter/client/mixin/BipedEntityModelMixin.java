package net.charter.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.charter.client.TwoHanded;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.entity.LivingEntity;

/**
 * Holds Dusk's Epitaph with both hands. Runs at the end of the vanilla pose so it only has to overwrite the arms;
 * the player model copies the arms to the sleeves afterwards, so the sleeves follow.
 */
@Mixin(BipedEntityModel.class)
public abstract class BipedEntityModelMixin {
	@Inject(method = "setAngles(Lnet/minecraft/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
	private void charter$holdGreatswordWithBothHands(LivingEntity entity, float limbAngle, float limbDistance, float animationProgress, float headYaw, float headPitch, CallbackInfo ci) {
		BipedEntityModel<?> model = (BipedEntityModel<?>) (Object) this;
		if (model.leaningPitch > 0.0F || !TwoHanded.is(entity)) {
			return;
		}
		TwoHanded.pose(model.rightArm, model.leftArm, model.head, model.body, model.sneaking, model.handSwingProgress, animationProgress);
	}
}
