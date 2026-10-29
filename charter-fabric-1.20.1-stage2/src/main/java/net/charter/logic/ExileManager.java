package net.charter.logic;

import java.util.ArrayList;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import net.charter.CharterMod;
import net.charter.config.CharterConfig;
import net.charter.fx.Fx;
import net.charter.state.CharterState;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;

/**
 * The exile: the patched, "broken" judgement. The player is not banned. They are thrown millions of blocks
 * away, beyond the world border, where they cannot die (they hover on half a heart) and have to find their
 * own way home.
 */
public final class ExileManager {
	private static final double WORLD_LIMIT = 29_999_000.0;

	private ExileManager() {
	}

	public static boolean isExiled(MinecraftServer server, UUID id) {
		return CharterState.get(server).isExiled(id);
	}

	/**
	 * True while the exile keeps this player from dying: they are exiled, the config says so, and they are still
	 * far from home ("now this can actually kill me" only happens in the last few thousand blocks).
	 */
	public static boolean protects(ServerPlayerEntity player) {
		CharterConfig config = CharterConfig.get();
		MinecraftServer server = player.getServer();
		if (server == null || !config.exileUnkillable) {
			return false;
		}
		CharterState.ExileRecord record = CharterState.get(server).exileRecord(player.getUuid());
		if (record == null) {
			return false;
		}
		String dimension = player.getServerWorld().getRegistryKey().getValue().toString();
		if (dimension.equals(record.originDimension)) {
			double dx = player.getX() - record.originX;
			double dz = player.getZ() - record.originZ;
			if (dx * dx + dz * dz <= (double) config.exileLethalRadius * (double) config.exileLethalRadius) {
				return false;
			}
		}
		return true;
	}

	/** Throws the player out. Safe to call on someone who is already exiled (they are thrown again). */
	public static void exile(ServerPlayerEntity player) {
		MinecraftServer server = player.getServer();
		if (server == null) {
			return;
		}
		CharterConfig config = CharterConfig.get();
		CharterState state = CharterState.get(server);
		UUID id = player.getUuid();
		ServerWorld from = player.getServerWorld();

		CharterState.ExileRecord record = state.exileRecord(id);
		if (record == null) {
			record = new CharterState.ExileRecord();
			record.name = player.getGameProfile().getName();
			record.originDimension = from.getRegistryKey().getValue().toString();
			record.originX = player.getX();
			record.originY = player.getY();
			record.originZ = player.getZ();
			record.originYaw = player.getYaw();
			record.originPitch = player.getPitch();
			record.since = from.getTime();
		}

		ServerWorld destination = destinationWorld(server, player);
		double angle = from.getRandom().nextDouble() * Math.PI * 2.0;
		double x = destination.getSpawnPos().getX() + Math.cos(angle) * config.exileDistance;
		double z = destination.getSpawnPos().getZ() + Math.sin(angle) * config.exileDistance;
		x = MathHelper.clamp(x, -WORLD_LIMIT, WORLD_LIMIT);
		z = MathHelper.clamp(z, -WORLD_LIMIT, WORLD_LIMIT);
		int blockX = MathHelper.floor(x);
		int blockZ = MathHelper.floor(z);

		// make sure the far-away chunk exists before arriving in it
		destination.getChunk(blockX >> 4, blockZ >> 4);
		int blockY = Math.max(destination.getSeaLevel(), destination.getTopY(Heightmap.Type.MOTION_BLOCKING, blockX, blockZ));

		record.destDimension = destination.getRegistryKey().getValue().toString();
		record.destX = blockX + 0.5;
		record.destZ = blockZ + 0.5;
		state.putExile(id, record);
		state.setPendingExile(id, false);

		// the farewell: a sigil and a beam where they stood
		Vec3d at = player.getPos();
		Fx.sigil(from, at, config.sigilRadius, 40);
		Fx.beam(from, at, 1.0, 30);
		Fx.sound(from, at, SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, 4.0f, 0.8f);
		Fx.sparks(from, at.add(0.0, 1.0, 0.0), 60, 0.6);

		player.stopRiding();
		player.teleport(destination, record.destX, blockY, record.destZ, player.getYaw(), player.getPitch());
		player.fallDistance = 0.0f;
		if (player.getHealth() < config.exileMinHealth) {
			player.setHealth(config.exileMinHealth);
		}
		if (config.exileMessages) {
			player.sendMessage(Text.literal("Charter has cast you out.").formatted(Formatting.GOLD, Formatting.BOLD), false);
			player.sendMessage(Text.literal("You are " + formatDistance(config.exileDistance) + " blocks from home. You cannot die here. Find your own way back.").formatted(Formatting.GRAY, Formatting.ITALIC), false);
		}
		CharterMod.LOGGER.info("Charter has exiled {} to {}, {}", record.name, blockX, blockZ);
	}

	/** Ends the exile. With {@code returnHome} the player is also brought back to where they were exiled from. */
	public static boolean release(ServerPlayerEntity player, boolean returnHome) {
		MinecraftServer server = player.getServer();
		if (server == null) {
			return false;
		}
		CharterState state = CharterState.get(server);
		CharterState.ExileRecord record = state.removeExile(player.getUuid());
		if (record == null) {
			return false;
		}
		if (returnHome) {
			Identifier dimensionId = Identifier.tryParse(record.originDimension);
			ServerWorld world = dimensionId == null ? null : server.getWorld(RegistryKey.of(RegistryKeys.WORLD, dimensionId));
			if (world == null) {
				world = server.getOverworld();
			}
			player.stopRiding();
			player.teleport(world, record.originX, record.originY, record.originZ, record.originYaw, record.originPitch);
			player.fallDistance = 0.0f;
		}
		if (CharterConfig.get().exileMessages) {
			player.sendMessage(Text.literal("Charter's exile has ended. You may die again.").formatted(Formatting.GOLD), false);
		}
		return true;
	}

	/** Runs once a second: frees exiles who found their way home and tells the others how far they have to go. */
	public static void tick(MinecraftServer server) {
		CharterConfig config = CharterConfig.get();
		CharterState state = CharterState.get(server);
		if (state.exiledPlayers().isEmpty()) {
			return;
		}
		for (Map.Entry<UUID, CharterState.ExileRecord> entry : new ArrayList<>(state.exiledPlayers().entrySet())) {
			ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.getKey());
			if (player == null) {
				continue;
			}
			CharterState.ExileRecord record = entry.getValue();
			String dimension = player.getServerWorld().getRegistryKey().getValue().toString();
			if (!dimension.equals(record.originDimension)) {
				continue;
			}
			double dx = player.getX() - record.originX;
			double dz = player.getZ() - record.originZ;
			double distance = Math.sqrt(dx * dx + dz * dz);
			if (distance <= (double) config.exileReturnRadius) {
				release(player, false);
				continue;
			}
			if (config.exileMessages) {
				player.sendMessage(Text.literal("Exiled: " + formatDistance(distance) + " blocks from home").formatted(Formatting.GOLD), true);
			}
		}
	}

	private static ServerWorld destinationWorld(MinecraftServer server, ServerPlayerEntity fallback) {
		Identifier dimensionId = Identifier.tryParse(CharterConfig.get().exileDimension);
		if (dimensionId != null) {
			RegistryKey<World> key = RegistryKey.of(RegistryKeys.WORLD, dimensionId);
			ServerWorld world = server.getWorld(key);
			if (world != null) {
				return world;
			}
		}
		return fallback.getServerWorld();
	}

	private static String formatDistance(double blocks) {
		return String.format(Locale.ROOT, "%,d", Math.round(blocks));
	}
}
