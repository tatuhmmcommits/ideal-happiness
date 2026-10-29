package net.charter.net;

import java.util.Map;
import java.util.UUID;

import net.charter.CharterMod;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

/** The only thing the client needs to be told: which body parts every player has lost. */
public final class CharterNetworking {
	/** Server to client: varint count, then that many pairs of (UUID, varint mask of lost parts). */
	public static final Identifier BODY = CharterMod.id("body");

	private CharterNetworking() {
	}

	public static void sendBody(ServerPlayerEntity player, Map<UUID, Integer> masks) {
		ServerPlayNetworking.send(player, BODY, write(masks));
	}

	public static void broadcastBody(MinecraftServer server, Map<UUID, Integer> masks) {
		for (ServerPlayerEntity player : PlayerLookup.all(server)) {
			ServerPlayNetworking.send(player, BODY, write(masks));
		}
	}

	private static PacketByteBuf write(Map<UUID, Integer> masks) {
		PacketByteBuf buf = PacketByteBufs.create();
		buf.writeVarInt(masks.size());
		for (Map.Entry<UUID, Integer> entry : masks.entrySet()) {
			buf.writeUuid(entry.getKey());
			buf.writeVarInt(entry.getValue());
		}
		return buf;
	}
}
