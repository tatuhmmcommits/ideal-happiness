package net.charter.logic;

import net.charter.CharterMod;
import net.charter.config.CharterConfig;
import net.charter.fx.Fx;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BrushableBlockEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;

/**
 * The last use of Lesser Divinity destroys it: the item bursts into a million pieces and leaves a giant crater.
 * Suspicious blocks are scattered over the crater, and each hides a fragment that can be brushed out.
 */
public final class Detonation {
	/** Archaeology loot table that holds the fragments (data/charter/loot_tables/archaeology/). */
	public static final Identifier CRATER_LOOT = CharterMod.id("archaeology/lesser_divinity_crater");

	private Detonation() {
	}

	public static void detonate(ServerWorld world, BlockPos center) {
		CharterConfig config = CharterConfig.get();
		Vec3d middle = Vec3d.ofCenter(center);

		Fx.sound(world, middle, SoundEvents.ENTITY_GENERIC_EXPLODE, 10.0f, 0.5f);
		Fx.sound(world, middle, SoundEvents.ENTITY_WARDEN_SONIC_BOOM, 10.0f, 0.6f);
		world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, middle.x, middle.y + 1.0, middle.z, 3, 2.0, 1.0, 2.0, 0.0);
		Fx.sparks(world, middle.add(0.0, 1.5, 0.0), 200, 3.0);
		if (config.detonationPower > 0.0f) {
			// hurts and throws creatures but does not break blocks: the crater below is carved by hand
			world.createExplosion(null, middle.x, middle.y, middle.z, config.detonationPower, World.ExplosionSourceType.NONE);
		}
		carve(world, center, config.craterRadius, config.craterDepth, config.craterSuspiciousChance);
	}

	private static void carve(ServerWorld world, BlockPos center, int radius, int depth, double suspiciousChance) {
		Random random = world.getRandom();
		BlockPos.Mutable pos = new BlockPos.Mutable();
		int placed = 0;
		for (int dx = -radius; dx <= radius; dx++) {
			for (int dz = -radius; dz <= radius; dz++) {
				double distance = Math.sqrt((double) (dx * dx + dz * dz));
				if (distance > (double) radius) {
					continue;
				}
				double t = distance / (double) radius;
				int carve = (int) Math.round((double) depth * (1.0 - t * t));
				int x = center.getX() + dx;
				int z = center.getZ() + dz;
				int top = world.getTopY(Heightmap.Type.OCEAN_FLOOR, x, z) - 1;
				if (top <= world.getBottomY() + 2) {
					continue;
				}

				BlockState floorBefore = world.getBlockState(pos.set(x, top - Math.max(carve, 0), z));
				for (int i = 0; i < carve; i++) {
					pos.set(x, top - i, z);
					BlockState state = world.getBlockState(pos);
					if (state.isAir() || state.isOf(Blocks.BEDROCK)) {
						continue;
					}
					world.setBlockState(pos, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
				}

				// suspicious blocks on the new floor, and a thinner scatter over the rim
				double chance = carve > 0 ? suspiciousChance : suspiciousChance * 0.45;
				if (t >= 0.97 || random.nextDouble() >= chance) {
					continue;
				}
				pos.set(x, top - carve, z);
				BlockState target = world.getBlockState(pos);
				if (target.isAir() || target.isOf(Blocks.BEDROCK) || target.isOf(Blocks.WATER) || target.isOf(Blocks.LAVA)) {
					continue;
				}
				BlockState suspicious = (floorBefore.isIn(BlockTags.SAND) || target.isIn(BlockTags.SAND))
						? Blocks.SUSPICIOUS_SAND.getDefaultState()
						: Blocks.SUSPICIOUS_GRAVEL.getDefaultState();
				world.setBlockState(pos, suspicious, Block.NOTIFY_ALL);
				BlockEntity entity = world.getBlockEntity(pos);
				if (entity instanceof BrushableBlockEntity) {
					((BrushableBlockEntity) entity).setLootTable(CRATER_LOOT, random.nextLong());
					placed++;
				}
			}
		}
		CharterMod.LOGGER.info("Lesser Divinity left a crater at {} with {} suspicious blocks", center, placed);
	}
}
