package net.charter.logic;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.jetbrains.annotations.Nullable;

import com.mojang.authlib.GameProfile;

import net.charter.CharterMod;
import net.charter.body.Bodies;
import net.charter.config.CharterConfig;
import net.charter.fx.Fx;
import net.charter.registry.ModDamageTypes;
import net.charter.state.CharterState;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;

/**
 * The judgement sequence. A verdict is never instant: first Charter shows what it is about to do (a golden
 * sigil on the ground, or golden chains that seize the target), then a beam of light falls and the verdict is
 * carried out.
 */
public final class Judgement {
	private static final List<Sequence> ACTIVE = new ArrayList<>();
	private static final Set<UUID> CONDEMNED = ConcurrentHashMap.newKeySet();

	private Judgement() {
	}

	private static final class Sequence {
		final ServerWorld world;
		final Vec3d center;
		final double radius;
		final Verdict verdict;
		final UUID source;
		/** Set for a chained single target, null for a sigil that judges everyone standing in it. */
		final UUID target;
		final String targetName;
		final Runnable after;
		int ticksLeft;
		Vec3d anchor;

		Sequence(ServerWorld world, Vec3d center, double radius, Verdict verdict, UUID source, UUID target, String targetName, int ticks, Runnable after) {
			this.world = world;
			this.center = center;
			this.radius = radius;
			this.verdict = verdict;
			this.source = source;
			this.target = target;
			this.targetName = targetName;
			this.ticksLeft = ticks;
			this.after = after;
		}
	}

	/** True while the player is held in chains awaiting judgement: they cannot be hurt, killed or act. */
	public static boolean isCondemned(UUID id) {
		return CONDEMNED.contains(id);
	}

	public static void reset() {
		ACTIVE.clear();
		CONDEMNED.clear();
	}

	// ------------------------------------------------------------------ starting a sequence

	/**
	 * Lesser Divinity: a golden sigil is drawn at {@code center}. When the telegraph ends everyone still standing
	 * inside it (except the wielder) is judged. {@code afterStrike} runs once the strike has landed.
	 */
	public static void sigilStrike(ServerWorld world, Vec3d center, double radius, Verdict verdict, @Nullable ServerPlayerEntity source, @Nullable Runnable afterStrike) {
		int ticks = CharterConfig.get().sigilTelegraphTicks;
		Sequence sequence = new Sequence(world, center, radius, verdict, source == null ? null : source.getUuid(), null, "", ticks, afterStrike);
		ACTIVE.add(sequence);
		Fx.sigil(world, center, radius, ticks + 30);
		Fx.sound(world, center, SoundEvents.BLOCK_BEACON_ACTIVATE, 4.0f, 0.55f);
		Fx.sound(world, center, SoundEvents.BLOCK_CHAIN_PLACE, 3.0f, 0.5f);
	}

	/**
	 * Dusk's Epitaph: golden chains seize the target. They are held in place until the strike lands.
	 * Does nothing if the target is already condemned.
	 */
	public static void bindAndJudge(ServerPlayerEntity target, Verdict verdict, @Nullable ServerPlayerEntity source) {
		if (CONDEMNED.contains(target.getUuid())) {
			return;
		}
		MinecraftServer server = target.getServer();
		if (source != null && server != null && Contracts.protectsCreator(server, source.getUuid(), target.getUuid())) {
			// whoever signed a contract cannot turn Charter against the one who drew it up
			Bodies.nag(source, "You signed a contract with " + target.getGameProfile().getName() + ". You cannot harm them.");
			return;
		}
		ServerWorld world = target.getServerWorld();
		int ticks = CharterConfig.get().chainTelegraphTicks;
		Sequence sequence = new Sequence(world, target.getPos(), 0.0, verdict, source == null ? null : source.getUuid(), target.getUuid(), target.getGameProfile().getName(), ticks, null);
		sequence.anchor = target.getPos();
		ACTIVE.add(sequence);
		CONDEMNED.add(target.getUuid());
		Fx.chains(world, target, ticks + 15);
		Fx.sigil(world, target.getPos(), 2.6, ticks + 30);
		Fx.sound(world, target.getPos(), SoundEvents.BLOCK_CHAIN_PLACE, 4.0f, 0.6f);
		Fx.sound(world, target.getPos(), SoundEvents.BLOCK_BEACON_ACTIVATE, 3.0f, 0.8f);
	}

	// ------------------------------------------------------------------ ticking

	public static void tick(MinecraftServer server) {
		if (ACTIVE.isEmpty()) {
			return;
		}
		Iterator<Sequence> iterator = new ArrayList<>(ACTIVE).iterator();
		while (iterator.hasNext()) {
			Sequence sequence = iterator.next();
			sequence.ticksLeft--;
			if (sequence.ticksLeft <= 0) {
				ACTIVE.remove(sequence);
				strike(server, sequence);
			} else {
				telegraph(server, sequence);
			}
		}
	}

	private static void telegraph(MinecraftServer server, Sequence sequence) {
		ServerWorld world = sequence.world;
		if (sequence.target == null) {
			if (sequence.ticksLeft % 10 == 0) {
				Fx.ring(world, sequence.center, sequence.radius, 28);
				Fx.sound(world, sequence.center, SoundEvents.BLOCK_BEACON_AMBIENT, 2.0f, 0.5f + 0.5f * (1.0f - (float) sequence.ticksLeft / (float) CharterConfig.get().sigilTelegraphTicks));
			}
			if (sequence.ticksLeft % 4 == 0) {
				world.spawnParticles(ParticleTypes.END_ROD, sequence.center.x, sequence.center.y + 0.2, sequence.center.z, 2, sequence.radius * 0.5, 0.1, sequence.radius * 0.5, 0.01);
			}
			return;
		}
		ServerPlayerEntity target = server.getPlayerManager().getPlayer(sequence.target);
		if (target == null || target.isRemoved()) {
			return;
		}
		// hold them where the chains caught them
		Vec3d anchor = sequence.anchor;
		target.setVelocity(0.0, Math.min(0.0, target.getVelocity().y), 0.0);
		target.velocityModified = true;
		double dx = target.getX() - anchor.x;
		double dz = target.getZ() - anchor.z;
		if (dx * dx + dz * dz > 0.01) {
			target.networkHandler.requestTeleport(anchor.x, target.getY(), anchor.z, target.getYaw(), target.getPitch());
		}
		if (sequence.ticksLeft % 5 == 0) {
			Fx.sparks(world, target.getPos().add(0.0, 1.0, 0.0), 6, 0.5);
		}
		if (sequence.ticksLeft % 12 == 0) {
			Fx.sound(world, target.getPos(), SoundEvents.ITEM_ARMOR_EQUIP_CHAIN, 2.0f, 0.6f);
		}
	}

	private static void strike(MinecraftServer server, Sequence sequence) {
		CharterConfig config = CharterConfig.get();
		ServerWorld world = sequence.world;
		Vec3d at = sequence.center;
		if (sequence.target != null) {
			ServerPlayerEntity target = server.getPlayerManager().getPlayer(sequence.target);
			if (target != null) {
				at = target.getPos();
			}
		}

		double beamRadius = sequence.target != null ? 1.0 : Math.max(1.0, sequence.radius * 0.45);
		Fx.beam(world, at, beamRadius, config.strikeBeamTicks);
		Fx.sound(world, at, SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, 10.0f, 0.7f);
		Fx.sound(world, at, SoundEvents.ITEM_TRIDENT_THUNDER, 8.0f, 0.6f);
		Fx.sound(world, at, SoundEvents.BLOCK_BEACON_DEACTIVATE, 4.0f, 0.5f);
		Fx.sparks(world, at.add(0.0, 1.0, 0.0), 120, 1.2);

		List<UUID> victims = new ArrayList<>();
		if (sequence.target != null) {
			victims.add(sequence.target);
		} else {
			for (ServerPlayerEntity player : world.getPlayers()) {
				if (player.isSpectator() || player.getUuid().equals(sequence.source)) {
					continue;
				}
				if (sequence.source != null && Contracts.protectsCreator(server, sequence.source, player.getUuid())) {
					// the creator of the wielder's contract stands untouched in the middle of the sigil
					continue;
				}
				double dx = player.getX() - at.x;
				double dz = player.getZ() - at.z;
				if (dx * dx + dz * dz <= sequence.radius * sequence.radius && Math.abs(player.getY() - at.y) <= 8.0) {
					victims.add(player.getUuid());
				}
			}
		}
		for (UUID id : victims) {
			CONDEMNED.remove(id);
			String name = id.equals(sequence.target) ? sequence.targetName : "";
			deliver(server, id, name, sequence.verdict);
		}
		if (sequence.after != null) {
			sequence.after.run();
		}
	}

	// ------------------------------------------------------------------ carrying out a verdict

	/**
	 * Carries out a verdict at once, with no telegraph. A ban that is switched off in the config, or aimed at an
	 * exempt player (the host of a local world), turns into the configured fallback verdict instead.
	 */
	public static void deliver(MinecraftServer server, UUID id, String fallbackName, Verdict verdict) {
		CharterConfig config = CharterConfig.get();
		GameProfile profile = Banisher.profileOf(server, id, fallbackName);
		ServerPlayerEntity online = server.getPlayerManager().getPlayer(id);

		Verdict actual = verdict;
		if (actual == Verdict.BAN && (!config.permanentBans || Banisher.isExempt(server, profile))) {
			actual = "KICK".equals(config.fallbackVerdict) ? Verdict.KICK : Verdict.EXILE;
		}
		switch (actual) {
			case BAN:
				Banisher.banish(server, profile);
				break;
			case KICK:
				Banisher.kick(server, profile);
				break;
			case EXILE:
				if (online != null) {
					ExileManager.exile(online);
				} else {
					CharterState.get(server).setPendingExile(id, true);
				}
				break;
			case KILL:
				if (online != null) {
					online.damage(ModDamageTypes.source(online.getWorld(), ModDamageTypes.JUDGEMENT), 1000.0f);
				}
				break;
			default:
				CharterMod.LOGGER.warn("Unknown verdict {}", actual);
				break;
		}
	}
}
