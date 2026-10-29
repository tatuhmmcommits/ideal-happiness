package net.charter.body;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Which body parts every player has lost, as a plain lookup that the game code and the renderer can both read
 * without a world at hand. The server keeps it in step with the saved data; clients fill it from the
 * {@code charter:body} packet. (In a single-player world both sides share it, which is harmless: they hold the
 * same values.)
 */
public final class BodyMirror {
	private static final Map<UUID, Integer> MASKS = new ConcurrentHashMap<>();

	private BodyMirror() {
	}

	/** The lost-parts mask of a player: bit {@code n} is set when {@code BodyPart.values()[n]} is gone. */
	public static int mask(UUID id) {
		Integer mask = MASKS.get(id);
		return mask == null ? 0 : mask;
	}

	public static boolean gone(UUID id, BodyPart part) {
		return BodyPart.gone(mask(id), part);
	}

	public static void set(UUID id, int mask) {
		if (mask == 0) {
			MASKS.remove(id);
		} else {
			MASKS.put(id, mask);
		}
	}

	public static Map<UUID, Integer> snapshot() {
		return new HashMap<>(MASKS);
	}

	/** Makes the lookup equal to {@code masks}, touching only what differs. */
	public static void replaceAll(Map<UUID, Integer> masks) {
		MASKS.keySet().removeIf(id -> !masks.containsKey(id) || masks.get(id) == 0);
		for (Map.Entry<UUID, Integer> entry : masks.entrySet()) {
			set(entry.getKey(), entry.getValue());
		}
	}

	public static void clear() {
		MASKS.clear();
	}
}
