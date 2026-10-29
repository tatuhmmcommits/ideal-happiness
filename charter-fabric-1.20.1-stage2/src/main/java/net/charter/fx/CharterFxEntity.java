package net.charter.fx;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.world.World;

/**
 * The single invisible entity behind every golden visual of the mod. The server only fills in the
 * tracked values; the client renderer turns them into the sigil, chains, beam or cage.
 * All animation is derived from the world time, so nothing has to be synced every tick.
 */
public class CharterFxEntity extends Entity {
	/** A flat golden charter sigil lying on the ground. */
	public static final int SIGIL = 0;
	/** A column of golden light from the ground to the sky. */
	public static final int BEAM = 1;
	/** Golden chains wound around the followed entity. */
	public static final int CHAINS = 2;
	/** The shrinking square wall of Divine Dominance. */
	public static final int CAGE = 3;

	private static final TrackedData<Integer> KIND = DataTracker.registerData(CharterFxEntity.class, TrackedDataHandlerRegistry.INTEGER);
	private static final TrackedData<Integer> START = DataTracker.registerData(CharterFxEntity.class, TrackedDataHandlerRegistry.INTEGER);
	private static final TrackedData<Integer> LIFETIME = DataTracker.registerData(CharterFxEntity.class, TrackedDataHandlerRegistry.INTEGER);
	private static final TrackedData<Integer> ARM = DataTracker.registerData(CharterFxEntity.class, TrackedDataHandlerRegistry.INTEGER);
	private static final TrackedData<Integer> SHRINK = DataTracker.registerData(CharterFxEntity.class, TrackedDataHandlerRegistry.INTEGER);
	private static final TrackedData<Integer> FOLLOW = DataTracker.registerData(CharterFxEntity.class, TrackedDataHandlerRegistry.INTEGER);
	private static final TrackedData<Float> SIZE = DataTracker.registerData(CharterFxEntity.class, TrackedDataHandlerRegistry.FLOAT);
	private static final TrackedData<Float> SIZE_END = DataTracker.registerData(CharterFxEntity.class, TrackedDataHandlerRegistry.FLOAT);
	private static final TrackedData<Float> UP = DataTracker.registerData(CharterFxEntity.class, TrackedDataHandlerRegistry.FLOAT);
	private static final TrackedData<Float> DOWN = DataTracker.registerData(CharterFxEntity.class, TrackedDataHandlerRegistry.FLOAT);

	public CharterFxEntity(EntityType<?> type, World world) {
		super(type, world);
		this.noClip = true;
		this.setInvulnerable(true);
		this.setNoGravity(true);
	}

	@Override
	protected void initDataTracker() {
		this.dataTracker.startTracking(KIND, SIGIL);
		this.dataTracker.startTracking(START, 0);
		this.dataTracker.startTracking(LIFETIME, -1);
		this.dataTracker.startTracking(ARM, 0);
		this.dataTracker.startTracking(SHRINK, 1);
		this.dataTracker.startTracking(FOLLOW, 0);
		this.dataTracker.startTracking(SIZE, 4.0f);
		this.dataTracker.startTracking(SIZE_END, 1.0f);
		this.dataTracker.startTracking(UP, 48.0f);
		this.dataTracker.startTracking(DOWN, 16.0f);
	}

	// ------------------------------------------------------------------ configuration (server side)
	public CharterFxEntity kind(int kind) {
		this.dataTracker.set(KIND, kind);
		return this;
	}

	/** World time (in ticks) at which the effect begins; every animation counts from here. */
	public CharterFxEntity startAt(long worldTime) {
		this.dataTracker.set(START, (int) worldTime);
		return this;
	}

	/** Ticks the entity lives for; negative means until it is removed by its owner. */
	public CharterFxEntity lifetime(int ticks) {
		this.dataTracker.set(LIFETIME, ticks);
		return this;
	}

	public CharterFxEntity size(float size) {
		this.dataTracker.set(SIZE, size);
		return this;
	}

	public CharterFxEntity cage(float startHalfWidth, float endHalfWidth, int armTicks, int shrinkTicks, float up, float down) {
		this.dataTracker.set(SIZE, startHalfWidth);
		this.dataTracker.set(SIZE_END, endHalfWidth);
		this.dataTracker.set(ARM, armTicks);
		this.dataTracker.set(SHRINK, Math.max(1, shrinkTicks));
		this.dataTracker.set(UP, up);
		this.dataTracker.set(DOWN, down);
		return this;
	}

	public CharterFxEntity follow(Entity entity) {
		this.dataTracker.set(FOLLOW, entity == null ? 0 : entity.getId());
		return this;
	}

	// ------------------------------------------------------------------ readers (both sides)
	public int getKind() {
		return this.dataTracker.get(KIND);
	}

	public int getStart() {
		return this.dataTracker.get(START);
	}

	public int getLifetime() {
		return this.dataTracker.get(LIFETIME);
	}

	public float getSize() {
		return this.dataTracker.get(SIZE);
	}

	public float getSizeEnd() {
		return this.dataTracker.get(SIZE_END);
	}

	public int getArm() {
		return this.dataTracker.get(ARM);
	}

	public int getShrink() {
		return this.dataTracker.get(SHRINK);
	}

	public int getFollowId() {
		return this.dataTracker.get(FOLLOW);
	}

	public float getUp() {
		return this.dataTracker.get(UP);
	}

	public float getDown() {
		return this.dataTracker.get(DOWN);
	}

	/** Ticks since the effect began, with the fraction supplied by the renderer. */
	public float age(float tickDelta) {
		return (float) (this.getWorld().getTime() - (long) getStart()) + tickDelta;
	}

	/** Half width of the cage at the given age (ticks since the effect began). */
	public static float cageHalfWidth(float startHalf, float endHalf, int armTicks, int shrinkTicks, float age) {
		float progress = (age - (float) armTicks) / (float) Math.max(1, shrinkTicks);
		if (progress < 0.0f) {
			progress = 0.0f;
		} else if (progress > 1.0f) {
			progress = 1.0f;
		}
		return startHalf + (endHalf - startHalf) * progress;
	}

	// ------------------------------------------------------------------ life cycle
	@Override
	public void tick() {
		super.tick();
		if (this.getWorld().isClient) {
			return;
		}
		int life = getLifetime();
		if (life >= 0 && this.getWorld().getTime() - (long) getStart() >= (long) life) {
			this.discard();
			return;
		}
		int followId = getFollowId();
		if (followId != 0) {
			Entity target = this.getWorld().getEntityById(followId);
			if (target == null || target.isRemoved()) {
				this.discard();
				return;
			}
			this.setPosition(target.getX(), target.getY(), target.getZ());
		}
	}

	@Override
	protected void readCustomDataFromNbt(NbtCompound nbt) {
		// never saved
	}

	@Override
	protected void writeCustomDataToNbt(NbtCompound nbt) {
		// never saved
	}

	@Override
	public Packet<ClientPlayPacketListener> createSpawnPacket() {
		return new EntitySpawnS2CPacket(this);
	}
}
