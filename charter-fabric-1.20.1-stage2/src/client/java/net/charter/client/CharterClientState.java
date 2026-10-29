package net.charter.client;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.charter.body.BodyMirror;
import net.charter.body.BodyShape;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.player.PlayerEntity;

/** What the client has been told by the server: which body parts every player has lost. */
public final class CharterClientState {
	/** The masks that the players' hitboxes were last brought in line with. */
	private static Map<UUID, Integer> applied = new HashMap<>();

	private CharterClientState() {
	}

	/** The server sent the complete picture: store it and give every player whose body changed a new hitbox. */
	public static void applyBody(MinecraftClient client, Map<UUID, Integer> masks) {
		BodyMirror.replaceAll(masks);

		Set<UUID> changed = new HashSet<>();
		for (Map.Entry<UUID, Integer> entry : masks.entrySet()) {
			Integer before = applied.get(entry.getKey());
			if (before == null || !before.equals(entry.getValue())) {
				changed.add(entry.getKey());
			}
		}
		for (UUID id : applied.keySet()) {
			if (!masks.containsKey(id)) {
				changed.add(id);
			}
		}
		applied = new HashMap<>(masks);

		ClientWorld world = client.world;
		if (world != null) {
			for (UUID id : changed) {
				PlayerEntity player = world.getPlayerByUuid(id);
				if (player != null) {
					player.calculateDimensions();
				}
			}
		}
	}

	/**
	 * Players who come into view are created with an ordinary hitbox, and nothing tells them to change it: every
	 * half second, anyone whose body is not an ordinary one is checked.
	 */
	public static void tick(ClientWorld world) {
		if (world.getTime() % 10L != 0L) {
			return;
		}
		for (AbstractClientPlayerEntity player : world.getPlayers()) {
			int mask = BodyMirror.mask(player.getUuid());
			if (mask == 0 || BodyShape.dimensions(mask) == null) {
				continue;
			}
			EntityDimensions wanted = player.getDimensions(player.getPose());
			if (Math.abs(player.getBoundingBox().getYLength() - (double) wanted.height) > 1.0E-4) {
				player.calculateDimensions();
			}
		}
	}

	public static void clear() {
		applied = new HashMap<>();
		BodyMirror.clear();
	}
}
