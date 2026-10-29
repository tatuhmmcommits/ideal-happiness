package net.charter.logic;

import java.util.Date;
import java.util.Optional;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import net.charter.CharterMod;
import net.charter.config.CharterConfig;
import net.charter.state.CharterState;
import net.minecraft.server.BannedPlayerEntry;
import net.minecraft.server.BannedPlayerList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.UserCache;

/** Carries out the permanent ban: the title screen, then never again. */
public final class Banisher {
	private Banisher() {
	}

	/** True when this profile may not be banned by Charter (the host of a local world, or an operator if configured). */
	public static boolean isExempt(MinecraftServer server, GameProfile profile) {
		CharterConfig config = CharterConfig.get();
		if (config.exemptSingleplayerHost && server.isHost(profile)) {
			return true;
		}
		return config.exemptOperators && server.getPlayerManager().isOperator(profile);
	}

	/** A profile the server has seen before, looked up by UUID (empty when the server keeps no profile cache). */
	public static Optional<GameProfile> cachedByUuid(MinecraftServer server, UUID id) {
		UserCache cache = server.getUserCache();
		return cache == null ? Optional.empty() : cache.getByUuid(id);
	}

	/** A profile the server has seen before, looked up by name (empty when the server keeps no profile cache). */
	public static Optional<GameProfile> cachedByName(MinecraftServer server, String name) {
		UserCache cache = server.getUserCache();
		return cache == null ? Optional.empty() : cache.findByName(name);
	}

	/** Look a profile up by UUID, online or not. */
	public static GameProfile profileOf(MinecraftServer server, UUID id, String fallbackName) {
		ServerPlayerEntity online = server.getPlayerManager().getPlayer(id);
		if (online != null) {
			return online.getGameProfile();
		}
		Optional<GameProfile> cached = cachedByUuid(server, id);
		if (cached.isPresent()) {
			return cached.get();
		}
		return new GameProfile(id, fallbackName);
	}

	/**
	 * Permanently bans the player. Their inventory is dropped where they stand, they are added to the ban list
	 * (and to Charter's own list of the judged), and they are disconnected with the Charter message.
	 */
	public static void banish(MinecraftServer server, GameProfile profile) {
		CharterConfig config = CharterConfig.get();
		CharterState state = CharterState.get(server);
		state.judge(profile.getId(), profile.getName());
		state.setPendingExile(profile.getId(), false);

		ServerPlayerEntity online = server.getPlayerManager().getPlayer(profile.getId());
		if (online != null && config.dropInventoryOnBan) {
			online.getInventory().dropAll();
		}

		if (config.useVanillaBanList) {
			BannedPlayerList list = server.getPlayerManager().getUserBanList();
			if (!list.contains(profile)) {
				list.add(new BannedPlayerEntry(profile, new Date(), config.banSource, null, config.banReason));
			}
		}

		if (online != null) {
			online.networkHandler.disconnect(Text.literal(config.kickMessage).formatted(Formatting.GOLD));
		}
		if (config.broadcastJudgements) {
			announce(server, profile.getName());
		}
		CharterMod.LOGGER.info("Charter has banished {} ({})", profile.getName(), profile.getId());
	}

	/** Disconnects the player with the Charter message without banning them. */
	public static void kick(MinecraftServer server, GameProfile profile) {
		CharterConfig config = CharterConfig.get();
		ServerPlayerEntity online = server.getPlayerManager().getPlayer(profile.getId());
		if (online != null) {
			online.networkHandler.disconnect(Text.literal(config.kickMessage).formatted(Formatting.GOLD));
		}
		CharterMod.LOGGER.info("Charter has sent {} away", profile.getName());
	}

	/** Lifts a Charter ban: removes the player from the ban list and from Charter's list of the judged. */
	public static boolean pardon(MinecraftServer server, GameProfile profile) {
		boolean lifted = CharterState.get(server).pardon(profile.getId());
		BannedPlayerList list = server.getPlayerManager().getUserBanList();
		if (list.contains(profile)) {
			list.remove(profile);
			lifted = true;
		}
		return lifted;
	}

	private static void announce(MinecraftServer server, String name) {
		String text = CharterConfig.get().broadcastMessage.replace("%s", name);
		server.getPlayerManager().broadcast(Text.literal(text).formatted(Formatting.GOLD), false);
	}
}
