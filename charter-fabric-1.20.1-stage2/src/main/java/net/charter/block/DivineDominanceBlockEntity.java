package net.charter.block;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import net.charter.config.CharterConfig;
import net.charter.fx.CharterFxEntity;
import net.charter.fx.Fx;
import net.charter.logic.Contracts;
import net.charter.logic.Judgement;
import net.charter.logic.Verdict;
import net.charter.registry.ModBlockEntities;
import net.charter.registry.ModDamageTypes;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * The brain of Divine Dominance. It keeps the golden wall alive, stops everyone who is not its owner or bound
 * to Charter from crossing it, and when the wall has closed it judges whoever is still inside.
 *
 * <p>The geometry is a square of half width {@code h} around the tablet, from {@code down} blocks below it to
 * {@code up} blocks above it. {@code h} stays at its starting value while the tablet arms, then shrinks to its
 * final value; see {@link CharterFxEntity#cageHalfWidth}.
 */
public class DivineDominanceBlockEntity extends BlockEntity {
	/** After closing, how long the tablet waits for the judgements to finish before it shatters anyway. */
	private static final long SHATTER_TIMEOUT = 900L;
	/** How far from the wall a creature is put when the wall turns it back. */
	private static final double MARGIN = 0.4;

	private UUID owner;
	private String ownerName = "";
	private long startTime = -1L;
	private double startHalf = 24.0;
	private double endHalf = 1.0;
	private int armTicks = 100;
	private int shrinkTicks = 2400;
	private int up = 48;
	private int down = 24;
	private boolean closed;
	private long closedAt;

	/** The visual entity. Not saved: it is respawned whenever it goes missing. */
	private UUID fxId;
	/** Which side of the wall each creature was on during the previous tick (true is inside). */
	private final Map<UUID, Boolean> inside = new HashMap<>();
	private final List<UUID> sentenced = new ArrayList<>();

	public DivineDominanceBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.DIVINE_DOMINANCE, pos, state);
	}

	public boolean isOwner(UUID id) {
		return owner != null && owner.equals(id);
	}

	private Vec3d center() {
		BlockPos pos = getPos();
		return new Vec3d((double) pos.getX() + 0.5, (double) pos.getY(), (double) pos.getZ() + 0.5);
	}

	/**
	 * The owner, everyone bound to Charter by a contract, and the creator of any contract the owner has signed are
	 * not touched by the wall.
	 */
	private boolean isExempt(MinecraftServer server, ServerPlayerEntity player) {
		if (isOwner(player.getUuid()) || Contracts.isBound(server, player.getUuid())) {
			return true;
		}
		return owner != null && Contracts.protectsCreator(server, owner, player.getUuid());
	}

	// ------------------------------------------------------------------ arming

	/** Called when the tablet is placed: the geometry is fixed from the config at this moment. */
	public void arm(ServerWorld world, @Nullable PlayerEntity placer) {
		CharterConfig config = CharterConfig.get();
		this.startHalf = config.dominanceStartRadius;
		this.endHalf = config.dominanceEndRadius;
		this.armTicks = config.dominanceArmTicks;
		this.shrinkTicks = config.dominanceShrinkTicks;
		this.up = config.dominanceHeightUp;
		this.down = config.dominanceHeightDown;
		this.startTime = world.getTime();
		this.closed = false;
		this.closedAt = 0L;
		this.inside.clear();
		this.sentenced.clear();
		if (placer != null) {
			this.owner = placer.getUuid();
			this.ownerName = placer.getGameProfile().getName();
		}
		markDirty();

		Vec3d center = center();
		Fx.sound(world, center, SoundEvents.BLOCK_BEACON_ACTIVATE, 8.0f, 0.5f);
		Fx.sound(world, center, SoundEvents.BLOCK_CHAIN_PLACE, 6.0f, 0.5f);
		Fx.sparks(world, center.add(0.0, 1.0, 0.0), 60, 0.8);
		if (placer instanceof ServerPlayerEntity) {
			placer.sendMessage(Text.literal("Divine Dominance is set. The walls begin to close in " + (armTicks / 20) + " seconds.").formatted(Formatting.GOLD), true);
		}
	}

	/** Right-clicking the tablet tells how long is left. */
	public void describe(PlayerEntity player) {
		World world = getWorld();
		if (world == null || startTime < 0L) {
			return;
		}
		long age = world.getTime() - startTime;
		String who = ownerName.isEmpty() ? "an unknown hand" : ownerName;
		Text text;
		if (closed) {
			text = Text.literal("Divine Dominance, set by " + who + ". The wall has closed.");
		} else if (age < (long) armTicks) {
			text = Text.literal("Divine Dominance, set by " + who + ". The walls begin to close in " + (((long) armTicks - age) / 20L + 1L) + "s.");
		} else {
			long left = Math.max(0L, (long) armTicks + (long) shrinkTicks - age) / 20L;
			text = Text.literal(String.format(Locale.ROOT, "Divine Dominance, set by %s. The walls meet in %d:%02d.", who, left / 60L, left % 60L));
		}
		player.sendMessage(text.copy().formatted(Formatting.GOLD), true);
	}

	// ------------------------------------------------------------------ ticking

	public static void tick(World world, BlockPos pos, BlockState state, DivineDominanceBlockEntity entity) {
		if (world instanceof ServerWorld) {
			entity.serverTick((ServerWorld) world);
		}
	}

	private void serverTick(ServerWorld world) {
		if (startTime < 0L) {
			// placed by a command or a structure rather than by a player: arm with the current config
			arm(world, null);
		}
		long age = world.getTime() - startTime;
		ensureFx(world);

		if (age >= (long) armTicks) {
			double half = (double) CharterFxEntity.cageHalfWidth((float) startHalf, (float) endHalf, armTicks, shrinkTicks, (float) age);
			enforce(world, half, !closed);
			if (!closed && age >= (long) armTicks + (long) shrinkTicks) {
				close(world);
			}
		}
		if (closed) {
			boolean pending = false;
			for (UUID id : sentenced) {
				if (Judgement.isCondemned(id)) {
					pending = true;
					break;
				}
			}
			if (!pending || world.getTime() - closedAt > SHATTER_TIMEOUT) {
				shatter(world);
			}
		}
	}

	private void ensureFx(ServerWorld world) {
		if (fxId != null) {
			if (world.getTime() % 40L != 0L) {
				return;
			}
			Entity existing = world.getEntity(fxId);
			if (existing != null && !existing.isRemoved()) {
				return;
			}
		}
		CharterFxEntity fx = Fx.cage(world, center(), startHalf, endHalf, armTicks, shrinkTicks, up, down, startTime);
		fxId = fx.getUuid();
	}

	/**
	 * Keeps everyone on the side of the wall they were on: those inside are held in (and are carried along as it
	 * closes), those outside are kept out. Spectators, the owner and bound souls ignore the wall entirely.
	 */
	private void enforce(ServerWorld world, double half, boolean squeezing) {
		MinecraftServer server = world.getServer();
		Vec3d center = center();
		CharterConfig config = CharterConfig.get();
		boolean crush = squeezing && half <= endHalf + 3.0 && world.getTime() % 20L == 0L && config.dominanceCrushDamage > 0.0f;

		Map<UUID, Boolean> now = new HashMap<>();
		for (ServerPlayerEntity player : world.getPlayers()) {
			if (player.isSpectator() || isExempt(server, player)) {
				continue;
			}
			double height = player.getY() - center.y;
			if (height < -((double) down) - 2.0 || height > (double) up + 2.0) {
				continue;
			}
			UUID id = player.getUuid();
			double dx = player.getX() - center.x;
			double dz = player.getZ() - center.z;
			boolean in = Math.abs(dx) < half && Math.abs(dz) < half;

			Boolean was = inside.get(id);
			if (was != null && was.booleanValue() != in) {
				push(world, player, center, half, was.booleanValue());
				in = was.booleanValue();
			}
			now.put(id, in);

			if (in && crush) {
				player.damage(ModDamageTypes.source(world, ModDamageTypes.CRUSH), config.dominanceCrushDamage);
			}
		}
		inside.clear();
		inside.putAll(now);
	}

	/** Puts a creature back on the side of the wall it belongs on. */
	private void push(ServerWorld world, ServerPlayerEntity player, Vec3d center, double half, boolean shouldBeInside) {
		double dx = player.getX() - center.x;
		double dz = player.getZ() - center.z;
		double x = player.getX();
		double z = player.getZ();
		if (shouldBeInside) {
			double limit = Math.max(0.0, half - MARGIN);
			x = center.x + MathHelper.clamp(dx, -limit, limit);
			z = center.z + MathHelper.clamp(dz, -limit, limit);
		} else {
			// leave through whichever wall is closest
			double depthX = half - Math.abs(dx);
			double depthZ = half - Math.abs(dz);
			if (depthX <= depthZ) {
				x = center.x + (dx >= 0.0 ? 1.0 : -1.0) * (half + MARGIN);
			} else {
				z = center.z + (dz >= 0.0 ? 1.0 : -1.0) * (half + MARGIN);
			}
		}
		player.stopRiding();
		player.setVelocity(0.0, Math.min(0.0, player.getVelocity().y), 0.0);
		player.velocityModified = true;
		player.networkHandler.requestTeleport(x, player.getY(), z, player.getYaw(), player.getPitch());
		Fx.sound(world, player.getPos(), SoundEvents.BLOCK_CHAIN_HIT, 1.5f, 0.6f);
		Fx.sparks(world, player.getPos().add(0.0, 1.0, 0.0), 8, 0.4);
	}

	/** The wall has met: everyone left inside is seized and judged. */
	private void close(ServerWorld world) {
		closed = true;
		closedAt = world.getTime();
		markDirty();

		CharterConfig config = CharterConfig.get();
		Verdict verdict = "KILL".equals(config.dominanceVerdict) ? Verdict.KILL : Verdict.BAN;
		MinecraftServer server = world.getServer();
		ServerPlayerEntity ownerPlayer = owner == null ? null : server.getPlayerManager().getPlayer(owner);

		sentenced.clear();
		for (Map.Entry<UUID, Boolean> entry : inside.entrySet()) {
			if (!entry.getValue().booleanValue()) {
				continue;
			}
			ServerPlayerEntity target = server.getPlayerManager().getPlayer(entry.getKey());
			if (target == null) {
				continue;
			}
			sentenced.add(entry.getKey());
			Judgement.bindAndJudge(target, verdict, ownerPlayer);
		}
		Fx.sound(world, center(), SoundEvents.BLOCK_BEACON_DEACTIVATE, 8.0f, 0.5f);
		Fx.sound(world, center(), SoundEvents.ENTITY_ELDER_GUARDIAN_CURSE, 4.0f, 0.6f);
	}

	/** The tablet is spent. */
	private void shatter(ServerWorld world) {
		Vec3d center = center();
		Fx.sound(world, center, SoundEvents.BLOCK_BEACON_DEACTIVATE, 6.0f, 0.4f);
		Fx.sound(world, center, SoundEvents.BLOCK_AMETHYST_BLOCK_BREAK, 4.0f, 0.5f);
		Fx.sparks(world, center.add(0.0, 0.8, 0.0), 80, 0.8);
		world.removeBlock(getPos(), false);
	}

	// ------------------------------------------------------------------ life cycle and saving

	@Override
	public void markRemoved() {
		super.markRemoved();
		discardFx();
	}

	private void discardFx() {
		World world = getWorld();
		if (fxId != null && world instanceof ServerWorld) {
			Entity fx = ((ServerWorld) world).getEntity(fxId);
			if (fx != null) {
				fx.discard();
			}
		}
		fxId = null;
	}

	@Override
	protected void writeNbt(NbtCompound nbt) {
		super.writeNbt(nbt);
		if (owner != null) {
			nbt.putUuid("Owner", owner);
		}
		nbt.putString("OwnerName", ownerName);
		nbt.putLong("StartTime", startTime);
		nbt.putDouble("StartHalf", startHalf);
		nbt.putDouble("EndHalf", endHalf);
		nbt.putInt("ArmTicks", armTicks);
		nbt.putInt("ShrinkTicks", shrinkTicks);
		nbt.putInt("Up", up);
		nbt.putInt("Down", down);
		nbt.putBoolean("Closed", closed);
		nbt.putLong("ClosedAt", closedAt);
	}

	@Override
	public void readNbt(NbtCompound nbt) {
		super.readNbt(nbt);
		owner = nbt.containsUuid("Owner") ? nbt.getUuid("Owner") : null;
		ownerName = nbt.getString("OwnerName");
		startTime = nbt.contains("StartTime") ? nbt.getLong("StartTime") : -1L;
		if (nbt.contains("StartHalf")) {
			startHalf = nbt.getDouble("StartHalf");
			endHalf = nbt.getDouble("EndHalf");
			armTicks = nbt.getInt("ArmTicks");
			shrinkTicks = Math.max(1, nbt.getInt("ShrinkTicks"));
			up = nbt.getInt("Up");
			down = nbt.getInt("Down");
		}
		closed = nbt.getBoolean("Closed");
		closedAt = nbt.getLong("ClosedAt");
	}
}
