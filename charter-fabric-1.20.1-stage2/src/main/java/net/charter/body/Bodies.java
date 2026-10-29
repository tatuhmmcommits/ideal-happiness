package net.charter.body;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import net.charter.config.CharterConfig;
import net.charter.fx.Fx;
import net.charter.net.CharterNetworking;
import net.charter.state.CharterState;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Arm;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;

/**
 * Taking body parts away and giving them back, and everything that follows from it on the server: the hands that
 * can no longer be used, the legs that can no longer carry, and the hitbox of whatever is left.
 */
public final class Bodies {
	private static final UUID SPEED_ID = UUID.fromString("5e1c6a3e-7d1b-4a0e-9a63-0c8f4b2d11a1");
	/** The jump boost this mod applies is recognisable by its negative amplifier. */
	private static final int NO_JUMP = -8;
	private static final Map<UUID, Long> NAGGED = new HashMap<>();
	/** Players whose hitbox this mod has changed, so that it can be put back when they are restored. */
	private static final Set<UUID> SHAPED = new HashSet<>();

	private Bodies() {
	}

	public static void init() {
		ServerLifecycleEvents.SERVER_STARTED.register(server -> BodyMirror.replaceAll(CharterState.get(server).bodyMasks()));
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			BodyMirror.clear();
			NAGGED.clear();
			SHAPED.clear();
		});
	}

	// ------------------------------------------------------------------ reading

	public static int mask(MinecraftServer server, UUID id) {
		return CharterState.get(server).bodyMask(id);
	}

	/** True when the player has no arm left to use {@code hand} with. */
	public static boolean handGone(PlayerEntity player, Hand hand) {
		int mask = BodyMirror.mask(player.getUuid());
		if (mask == 0) {
			return false;
		}
		Arm arm = hand == Hand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
		return BodyPart.gone(mask, BodyPart.ofArm(arm));
	}

	/** True when the player may not use {@code hand}: the arm is gone and the config says that matters. */
	public static boolean handLocked(PlayerEntity player, Hand hand) {
		return CharterConfig.get().lostArmsLockHands && handGone(player, hand);
	}

	/** Tells a player what they cannot do, but not more than about once a second. */
	public static void nag(ServerPlayerEntity player, String text) {
		long now = player.getServerWorld().getTime();
		Long last = NAGGED.get(player.getUuid());
		if (last != null && now - last < 30L) {
			return;
		}
		NAGGED.put(player.getUuid(), now);
		player.sendMessage(Text.literal(text).formatted(Formatting.RED, Formatting.ITALIC), true);
	}

	// ------------------------------------------------------------------ changing

	/** Takes one part. Returns false when the player had already lost it. */
	public static boolean lose(MinecraftServer server, UUID id, BodyPart part) {
		CharterState state = CharterState.get(server);
		int old = state.bodyMask(id);
		if (BodyPart.gone(old, part)) {
			return false;
		}
		int now = old | part.bit();
		state.setBodyMask(id, now);
		applied(server, id, now);

		ServerPlayerEntity online = server.getPlayerManager().getPlayer(id);
		if (online != null) {
			ServerWorld world = online.getServerWorld();
			Vec3d at = online.getPos().add(0.0, 1.0, 0.0);
			Fx.sound(world, at, SoundEvents.ENTITY_PLAYER_HURT, 1.0f, 0.6f);
			Fx.sound(world, at, SoundEvents.BLOCK_CHAIN_BREAK, 1.5f, 0.6f);
			Fx.sparks(world, at, 30, 0.4);
			online.sendMessage(Text.literal(lossMessage(part)).formatted(Formatting.RED, Formatting.ITALIC), false);
		}
		return true;
	}

	/** Takes the next part in line (left arm, right arm, left leg, right leg, body). Null when only a head is left. */
	@Nullable
	public static BodyPart loseNext(MinecraftServer server, UUID id) {
		BodyPart part = BodyPart.next(CharterState.get(server).bodyMask(id));
		if (part != null) {
			lose(server, id, part);
		}
		return part;
	}

	/** Gives one part back, or everything when {@code part} is null. Returns whether anything changed. */
	public static boolean restore(MinecraftServer server, UUID id, @Nullable BodyPart part) {
		CharterState state = CharterState.get(server);
		int old = state.bodyMask(id);
		int now = part == null ? 0 : old & ~part.bit();
		if (now == old) {
			return false;
		}
		state.setBodyMask(id, now);
		applied(server, id, now);

		ServerPlayerEntity online = server.getPlayerManager().getPlayer(id);
		if (online != null) {
			String text = now == 0 ? "You are whole again." : "Your " + (part == null ? "body" : part.label) + " has been restored.";
			online.sendMessage(Text.literal(text).formatted(Formatting.GOLD), false);
		}
		return true;
	}

	private static String lossMessage(BodyPart part) {
		switch (part) {
			case LEFT_ARM:
				return "Your left arm is gone. You can no longer use your off hand.";
			case RIGHT_ARM:
				return "Your right arm is gone. You can no longer use your main hand.";
			case LEFT_LEG:
				return "Your left leg is gone. You limp.";
			case RIGHT_LEG:
				return "Your right leg is gone. You limp.";
			default:
				return "Everything but your head is gone. All that is left is to hop.";
		}
	}

	private static void applied(MinecraftServer server, UUID id, int mask) {
		BodyMirror.set(id, mask);
		CharterNetworking.broadcastBody(server, CharterState.get(server).bodyMasks());
		ServerPlayerEntity online = server.getPlayerManager().getPlayer(id);
		if (online != null) {
			refresh(online);
		}
	}

	// ------------------------------------------------------------------ effects of a missing body

	/** Called when somebody joins or respawns: they learn what everyone has lost, and their own body is set up. */
	public static void onEnter(ServerPlayerEntity player) {
		MinecraftServer server = player.getServer();
		if (server == null) {
			return;
		}
		CharterNetworking.sendBody(player, CharterState.get(server).bodyMasks());
		refresh(player);
	}

	/** Puts the player's speed, jumping and hitbox in line with the parts they have lost. */
	public static void refresh(ServerPlayerEntity player) {
		int mask = BodyMirror.mask(player.getUuid());
		CharterConfig config = CharterConfig.get();

		double penalty = 0.0;
		if (config.lostLegsSlow) {
			if (BodyShape.headOnly(mask)) {
				// no ground speed at all: the head can only hop, and steer a little in the air
				penalty = -1.0;
			} else if (BodyShape.legless(mask)) {
				penalty = -0.8;
			} else if (BodyPart.gone(mask, BodyPart.LEFT_LEG) || BodyPart.gone(mask, BodyPart.RIGHT_LEG)) {
				penalty = -0.35;
			}
		}
		applySpeed(player, penalty);
		applyNoJump(player, config.lostLegsSlow && BodyShape.legless(mask));

		// the hitbox follows the body (the common PlayerEntityMixin supplies the numbers)
		boolean shaped = BodyShape.dimensions(mask) != null;
		boolean wasShaped = SHAPED.contains(player.getUuid());
		if (shaped || wasShaped) {
			EntityDimensions wanted = player.getDimensions(player.getPose());
			if (Math.abs(player.getBoundingBox().getYLength() - (double) wanted.height) > 1.0E-4) {
				player.calculateDimensions();
			}
			if (shaped) {
				SHAPED.add(player.getUuid());
			} else {
				SHAPED.remove(player.getUuid());
			}
		}
	}

	private static void applySpeed(ServerPlayerEntity player, double penalty) {
		EntityAttributeInstance speed = player.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
		if (speed == null) {
			return;
		}
		EntityAttributeModifier current = speed.getModifier(SPEED_ID);
		if (penalty == 0.0) {
			if (current != null) {
				speed.removeModifier(SPEED_ID);
			}
			return;
		}
		if (current != null && current.getValue() == penalty) {
			return;
		}
		if (current != null) {
			speed.removeModifier(SPEED_ID);
		}
		speed.addTemporaryModifier(new EntityAttributeModifier(SPEED_ID, "Charter: lost limbs", penalty, EntityAttributeModifier.Operation.MULTIPLY_TOTAL));
	}

	/** Without legs there is no jumping: a short, hidden jump boost with a negative amplifier cancels it. */
	private static void applyNoJump(ServerPlayerEntity player, boolean noJump) {
		if (noJump) {
			player.addStatusEffect(new StatusEffectInstance(StatusEffects.JUMP_BOOST, 100, NO_JUMP, true, false, false));
			return;
		}
		StatusEffectInstance current = player.getStatusEffect(StatusEffects.JUMP_BOOST);
		if (current != null && current.getAmplifier() < 0) {
			player.removeStatusEffect(StatusEffects.JUMP_BOOST);
		}
	}

	// ------------------------------------------------------------------ per tick

	/** Runs every tick; the heavier work only once a second. */
	public static void tick(MinecraftServer server, long tickCount) {
		boolean second = tickCount % 20L == 0L;
		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			int mask = BodyMirror.mask(player.getUuid());
			if (mask == 0) {
				if (second) {
					// cheap: removes a leftover penalty after a respawn or a restore
					refresh(player);
				}
				continue;
			}
			if (second) {
				refresh(player);
			}
			if (CharterConfig.get().lostArmsLockHands && handGone(player, Hand.OFF_HAND)) {
				putAwayOffhand(player);
			}
		}
	}

	/** A player without an off-hand arm cannot hold anything there: whatever lands in the slot is put away. */
	private static void putAwayOffhand(ServerPlayerEntity player) {
		PlayerInventory inventory = player.getInventory();
		ItemStack held = inventory.offHand.get(0);
		if (held.isEmpty()) {
			return;
		}
		ItemStack moved = held.copy();
		inventory.offHand.set(0, ItemStack.EMPTY);
		inventory.insertStack(moved);
		if (!moved.isEmpty()) {
			player.dropItem(moved, false);
		}
		nag(player, "You have no arm to hold that with.");
	}
}
