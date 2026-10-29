package net.charter.logic;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Asks for the same gesture twice before something that cannot be undone happens: the first time it only
 * returns false (and the caller tells the player what is about to happen), the second time within the window it
 * returns true.
 */
public final class Confirm {
	private static final Map<UUID, Pending> PENDING = new HashMap<>();

	private static final class Pending {
		final String key;
		final long expires;

		Pending(String key, long expires) {
			this.key = key;
			this.expires = expires;
		}
	}

	private Confirm() {
	}

	/** True when this player asked for {@code key} a moment ago as well. */
	public static boolean again(ServerPlayerEntity player, String key, int windowTicks) {
		long now = player.getServerWorld().getTime();
		Pending pending = PENDING.get(player.getUuid());
		if (pending != null && pending.key.equals(key) && now <= pending.expires) {
			PENDING.remove(player.getUuid());
			return true;
		}
		PENDING.put(player.getUuid(), new Pending(key, now + (long) windowTicks));
		return false;
	}

	public static void reset() {
		PENDING.clear();
	}
}
