package net.charter.body;

import org.jetbrains.annotations.Nullable;

import net.minecraft.entity.EntityDimensions;

/**
 * What a body without its legs, or without anything but a head, looks like to the game: how big the hitbox is,
 * where the eyes are, and how far down the model has to be drawn to sit on the ground.
 *
 * <p>A body that is only missing arms, or one leg, keeps the ordinary player shape.
 */
public final class BodyShape {
	/** Torso and head, sitting on the ground. */
	private static final float TORSO_HEIGHT = 1.3F;
	private static final float TORSO_EYES = 0.92F;
	private static final float TORSO_DOWN = 0.7F;

	/** Only the head, resting on the ground. */
	private static final float HEAD_SIZE = 0.5F;
	private static final float HEAD_EYES = 0.3F;
	private static final float HEAD_DOWN = 1.5F;

	private static final EntityDimensions TORSO = EntityDimensions.changing(0.6F, TORSO_HEIGHT);
	private static final EntityDimensions HEAD = EntityDimensions.changing(HEAD_SIZE, HEAD_SIZE);

	private BodyShape() {
	}

	/** Nothing but a head is left. */
	public static boolean headOnly(int mask) {
		return (mask & BodyPart.BODY.bit()) != 0;
	}

	/** Both legs are gone (and the body is still there). */
	public static boolean legless(int mask) {
		return !headOnly(mask) && BodyPart.gone(mask, BodyPart.LEFT_LEG) && BodyPart.gone(mask, BodyPart.RIGHT_LEG);
	}

	/** The hitbox of a body with this mask, or null when it is an ordinary player's. */
	@Nullable
	public static EntityDimensions dimensions(int mask) {
		if (headOnly(mask)) {
			return HEAD;
		}
		if (legless(mask)) {
			return TORSO;
		}
		return null;
	}

	/** The height of the eyes above the feet, or a negative number for an ordinary player. */
	public static float eyeHeight(int mask) {
		if (headOnly(mask)) {
			return HEAD_EYES;
		}
		if (legless(mask)) {
			return TORSO_EYES;
		}
		return -1.0F;
	}

	/** How many blocks the player model is drawn lower than usual. */
	public static float down(int mask) {
		if (headOnly(mask)) {
			return HEAD_DOWN;
		}
		if (legless(mask)) {
			return TORSO_DOWN;
		}
		return 0.0F;
	}
}
