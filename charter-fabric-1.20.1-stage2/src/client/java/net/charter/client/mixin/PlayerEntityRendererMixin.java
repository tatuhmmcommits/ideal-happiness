package net.charter.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.charter.body.BodyMirror;
import net.charter.body.BodyPart;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.model.PlayerEntityModel;

/**
 * Hides the arms, legs and body (and their skin overlays) of players who have lost them, both in the world and in
 * first person. The pose is set up on every render, so hiding the parts once it is done is enough.
 */
@Mixin(PlayerEntityRenderer.class)
public abstract class PlayerEntityRendererMixin extends LivingEntityRenderer<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> {
	// never used: the mixin only needs the generic super class to reach getModel()
	protected PlayerEntityRendererMixin(EntityRendererFactory.Context context, PlayerEntityModel<AbstractClientPlayerEntity> model, float shadowRadius) {
		super(context, model, shadowRadius);
	}

	@Inject(method = "setModelPose", at = @At("TAIL"))
	private void charter$hideLostParts(AbstractClientPlayerEntity player, CallbackInfo ci) {
		int mask = BodyMirror.mask(player.getUuid());
		if (mask == 0) {
			return;
		}
		PlayerEntityModel<AbstractClientPlayerEntity> model = this.getModel();
		if (BodyPart.gone(mask, BodyPart.LEFT_ARM)) {
			model.leftArm.visible = false;
			model.leftSleeve.visible = false;
		}
		if (BodyPart.gone(mask, BodyPart.RIGHT_ARM)) {
			model.rightArm.visible = false;
			model.rightSleeve.visible = false;
		}
		if (BodyPart.gone(mask, BodyPart.LEFT_LEG)) {
			model.leftLeg.visible = false;
			model.leftPants.visible = false;
		}
		if (BodyPart.gone(mask, BodyPart.RIGHT_LEG)) {
			model.rightLeg.visible = false;
			model.rightPants.visible = false;
		}
		if (BodyPart.gone(mask, BodyPart.BODY)) {
			model.body.visible = false;
			model.jacket.visible = false;
		}
	}
}
