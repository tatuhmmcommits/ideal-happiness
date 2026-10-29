package net.charter.body;

import java.util.Locale;

import org.jetbrains.annotations.Nullable;

import net.minecraft.util.Arm;

/**
 * The parts of a body that Charter can take, in the order they are given up: both arms, both legs, and finally
 * everything but the head. Losing {@link #BODY} takes every limb with it; what remains is a head that can only
 * hop about.
 */
public enum BodyPart {
	LEFT_ARM("left arm"),
	RIGHT_ARM("right arm"),
	LEFT_LEG("left leg"),
	RIGHT_LEG("right leg"),
	BODY("body");

	public final String label;

	BodyPart(String label) {
		this.label = label;
	}

	/** This part as a bit of a "lost parts" mask. */
	public int bit() {
		return 1 << ordinal();
	}

	/** The lower-case name used by commands and saved data. */
	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}

	/** True when a body with this mask no longer has the part. Without a body there are no limbs either. */
	public static boolean gone(int mask, BodyPart part) {
		if ((mask & part.bit()) != 0) {
			return true;
		}
		return part != BODY && (mask & BODY.bit()) != 0;
	}

	/** The next part to be taken from a body with this mask, or null when only the head is left. */
	@Nullable
	public static BodyPart next(int mask) {
		if ((mask & BODY.bit()) != 0) {
			return null;
		}
		for (BodyPart part : values()) {
			if ((mask & part.bit()) == 0) {
				return part;
			}
		}
		return null;
	}

	/** The next arm to be taken (left first, then right), or null when both are already gone. */
	@Nullable
	public static BodyPart nextArm(int mask) {
		if (!gone(mask, LEFT_ARM)) {
			return LEFT_ARM;
		}
		if (!gone(mask, RIGHT_ARM)) {
			return RIGHT_ARM;
		}
		return null;
	}

	/** The arm part that belongs to the given arm of a player model. */
	public static BodyPart ofArm(Arm arm) {
		return arm == Arm.LEFT ? LEFT_ARM : RIGHT_ARM;
	}

	@Nullable
	public static BodyPart byId(String id) {
		for (BodyPart part : values()) {
			if (part.id().equalsIgnoreCase(id)) {
				return part;
			}
		}
		return null;
	}

	/** A mask with every part lost. */
	public static int everything() {
		return (1 << values().length) - 1;
	}
}
