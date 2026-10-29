package net.charter.client;

import net.charter.body.BodyMirror;
import net.charter.body.BodyPart;
import net.charter.registry.ModItems;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.CrossbowPosing;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Arm;
import net.minecraft.util.math.MathHelper;

/**
 * Dusk's Epitaph is a greatsword: while the main hand holds it and the off hand is empty, the player holds it in
 * both hands, the blade rising in front of the chest.
 *
 * <p>The arm angles were solved so that the left hand lands on the grip of the sprite when the sword uses the
 * display transform of {@code dusk_epitaph_twohanded.json}. The model predicate {@code charter:two_handed}
 * (registered in {@link CharterClient}) switches to that transform while {@link #is} is true, so the pose and the
 * model always agree. Left-handed players keep the ordinary one-handed hold.
 */
public final class TwoHanded {
	public static final float RIGHT_PITCH = -1.0868F;
	public static final float RIGHT_YAW = -0.6196F;
	public static final float LEFT_PITCH = -0.9224F;
	public static final float LEFT_YAW = 0.6954F;

	private TwoHanded() {
	}

	/** True while this entity holds the Epitaph with both hands. */
	public static boolean is(LivingEntity entity) {
		if (!(entity instanceof PlayerEntity)) {
			return false;
		}
		PlayerEntity player = (PlayerEntity) entity;
		int lost = BodyMirror.mask(player.getUuid());
		return player.getMainArm() == Arm.RIGHT
				&& player.getMainHandStack().isOf(ModItems.DUSK_EPITAPH)
				&& player.getOffHandStack().isEmpty()
				&& !BodyPart.gone(lost, BodyPart.LEFT_ARM)
				&& !BodyPart.gone(lost, BodyPart.RIGHT_ARM)
				&& !player.isSleeping()
				&& !player.isFallFlying()
				&& !player.isSwimming();
	}

	/** Puts both arms on the grip; called after the vanilla pose has been computed. */
	public static void pose(ModelPart right, ModelPart left, ModelPart head, ModelPart body, boolean sneaking, float swing, float age) {
		float lift = sneaking ? 0.4F : 0.0F;
		right.pitch = RIGHT_PITCH + lift;
		right.yaw = RIGHT_YAW;
		right.roll = 0.0F;
		left.pitch = LEFT_PITCH + lift;
		left.yaw = LEFT_YAW;
		left.roll = 0.0F;
		// a slow sway, so the sword never looks nailed in place
		CrossbowPosing.swingArm(right, age, 1.0F);
		CrossbowPosing.swingArm(left, age, -1.0F);
		if (swing > 0.0F) {
			// an overhead chop with both arms: up quickly, then down through the target
			float f = 1.0F - swing;
			f *= f;
			f *= f;
			f = 1.0F - f;
			float up = MathHelper.sin(f * (float) Math.PI);
			float lean = MathHelper.sin(swing * (float) Math.PI) * -(head.pitch - 0.7F) * 0.75F;
			float chop = up * 1.2F + lean;
			right.pitch -= chop;
			left.pitch -= chop;
			right.yaw += body.yaw * 2.0F;
			left.yaw += body.yaw * 2.0F;
		}
	}
}
