package net.charter.logic;

import org.jetbrains.annotations.Nullable;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

/** Manual targeting: whatever the wielder is looking at, up to a configured range. */
public final class Targeting {
	private Targeting() {
	}

	/**
	 * The point the user is aiming at: the feet of the first living creature in the line of sight, otherwise the
	 * point where the line of sight meets a block. Null when they are looking at nothing within range.
	 */
	@Nullable
	public static Vec3d aim(ServerPlayerEntity user, double range) {
		World world = user.getWorld();
		Vec3d start = user.getEyePos();
		Vec3d direction = user.getRotationVector();
		Vec3d end = start.add(direction.multiply(range));

		BlockHitResult blockHit = world.raycast(new RaycastContext(start, end, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, user));
		boolean blockFound = blockHit.getType() != HitResult.Type.MISS;
		Vec3d limit = blockFound ? blockHit.getPos() : end;

		Box box = user.getBoundingBox().stretch(direction.multiply(range)).expand(1.0, 1.0, 1.0);
		EntityHitResult entityHit = ProjectileUtil.raycast(user, start, limit, box, entity -> entity instanceof LivingEntity && !entity.isSpectator(), range * range);
		if (entityHit != null) {
			return entityHit.getEntity().getPos();
		}
		return blockFound ? blockHit.getPos() : null;
	}
}
