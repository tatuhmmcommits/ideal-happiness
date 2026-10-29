package net.charter.fx;

import org.joml.Vector3f;

import net.charter.registry.ModEntities;
import net.minecraft.entity.Entity;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.math.Vec3d;

/** Server-side helpers that spawn the golden visuals and their particles and sounds. */
public final class Fx {
	/** The gold used by every particle of the mod. */
	public static final DustParticleEffect GOLD_DUST = new DustParticleEffect(new Vector3f(1.0f, 0.78f, 0.16f), 1.4f);
	public static final DustParticleEffect GOLD_DUST_SMALL = new DustParticleEffect(new Vector3f(1.0f, 0.86f, 0.35f), 0.8f);

	private Fx() {
	}

	private static CharterFxEntity create(ServerWorld world, int kind, Vec3d at, int lifetime) {
		CharterFxEntity entity = new CharterFxEntity(ModEntities.CHARTER_FX, world);
		entity.kind(kind).startAt(world.getTime()).lifetime(lifetime);
		entity.setPosition(at.x, at.y, at.z);
		return entity;
	}

	/** A golden charter sigil on the ground, {@code radius} blocks wide on each side of the centre. */
	public static CharterFxEntity sigil(ServerWorld world, Vec3d at, double radius, int lifetime) {
		CharterFxEntity entity = create(world, CharterFxEntity.SIGIL, at, lifetime);
		entity.size((float) radius);
		world.spawnEntity(entity);
		return entity;
	}

	/** A golden beam falling from the sky onto {@code at}. */
	public static CharterFxEntity beam(ServerWorld world, Vec3d at, double radius, int lifetime) {
		CharterFxEntity entity = create(world, CharterFxEntity.BEAM, at, lifetime);
		entity.size((float) radius);
		world.spawnEntity(entity);
		return entity;
	}

	/** Golden chains wound around {@code target} until the effect expires. */
	public static CharterFxEntity chains(ServerWorld world, Entity target, int lifetime) {
		CharterFxEntity entity = create(world, CharterFxEntity.CHAINS, target.getPos(), lifetime);
		entity.follow(target);
		world.spawnEntity(entity);
		return entity;
	}

	/** The golden square wall of Divine Dominance. It lives until its tablet removes it. */
	public static CharterFxEntity cage(ServerWorld world, Vec3d center, double startHalf, double endHalf, int armTicks, int shrinkTicks, int up, int down, long startTime) {
		CharterFxEntity entity = new CharterFxEntity(ModEntities.CHARTER_FX, world);
		entity.kind(CharterFxEntity.CAGE).startAt(startTime).lifetime(-1);
		entity.cage((float) startHalf, (float) endHalf, armTicks, shrinkTicks, (float) up, (float) down);
		entity.setPosition(center.x, center.y, center.z);
		world.spawnEntity(entity);
		return entity;
	}

	public static void sound(ServerWorld world, Vec3d at, SoundEvent sound, float volume, float pitch) {
		world.playSound(null, at.x, at.y, at.z, sound, SoundCategory.PLAYERS, volume, pitch);
	}

	/** A burst of golden sparks. */
	public static void sparks(ServerWorld world, Vec3d at, int count, double spread) {
		world.spawnParticles(GOLD_DUST, at.x, at.y, at.z, count, spread, spread, spread, 0.02);
		world.spawnParticles(ParticleTypes.END_ROD, at.x, at.y, at.z, Math.max(1, count / 4), spread, spread, spread, 0.04);
	}

	/** A ring of golden particles lying on the ground. */
	public static void ring(ServerWorld world, Vec3d at, double radius, int points) {
		for (int i = 0; i < points; i++) {
			double angle = Math.PI * 2.0 * (double) i / (double) points;
			double x = at.x + Math.cos(angle) * radius;
			double z = at.z + Math.sin(angle) * radius;
			world.spawnParticles(GOLD_DUST_SMALL, x, at.y + 0.1, z, 1, 0.0, 0.0, 0.0, 0.0);
		}
	}

	/** A rising column of golden particles. */
	public static void column(ServerWorld world, Vec3d at, double height, int points) {
		for (int i = 0; i < points; i++) {
			double y = at.y + height * (double) i / (double) points;
			world.spawnParticles(ParticleTypes.END_ROD, at.x, y, at.z, 1, 0.15, 0.0, 0.15, 0.0);
		}
	}
}
