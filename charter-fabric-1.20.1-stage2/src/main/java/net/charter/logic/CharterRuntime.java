package net.charter.logic;

import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import net.charter.block.DivineDominanceBlockEntity;
import net.charter.body.Bodies;
import net.charter.config.CharterConfig;
import net.charter.fx.Fx;
import net.charter.item.ContractItem;
import net.charter.item.DuskEpitaphItem;
import net.charter.registry.ModBlocks;
import net.charter.state.CharterState;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

/**
 * Wires the mod into the game: the per-tick work (judgements, exile, lost body parts, cursed weapons, golden
 * chains on the bound), the protection of players being judged and of the creators of contracts, the exile that
 * cannot be died out of, Dusk's Epitaph, and everything that happens when somebody joins or respawns.
 * Only the head-only hitbox needs a mixin (see {@code PlayerEntityMixin}); the rest is plain events.
 */
public final class CharterRuntime {
	private static long tickCount;

	private CharterRuntime() {
	}

	public static void init() {
		ServerLifecycleEvents.SERVER_STARTED.register(server -> Judgement.reset());
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			Judgement.reset();
			Confirm.reset();
		});
		ServerTickEvents.END_SERVER_TICK.register(CharterRuntime::tick);

		ServerPlayConnectionEvents.JOIN.register(CharterRuntime::onJoin);
		ServerPlayerEvents.AFTER_RESPAWN.register(CharterRuntime::onRespawn);

		ServerLivingEntityEvents.ALLOW_DAMAGE.register(CharterRuntime::allowDamage);
		ServerLivingEntityEvents.ALLOW_DEATH.register(CharterRuntime::allowDeath);

		// a cursed weapon that hits the ground can only be picked up by its signer
		ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
			if (entity instanceof ItemEntity) {
				Curses.onItemLoaded((ItemEntity) entity);
			}
		});

		AttackEntityCallback.EVENT.register(CharterRuntime::onAttackEntity);
		AttackBlockCallback.EVENT.register(CharterRuntime::onAttackBlock);
		UseItemCallback.EVENT.register(CharterRuntime::onUseItem);
		UseBlockCallback.EVENT.register(CharterRuntime::onUseBlock);
		UseEntityCallback.EVENT.register(CharterRuntime::onUseEntity);
		PlayerBlockBreakEvents.BEFORE.register(CharterRuntime::beforeBreak);
	}

	// ------------------------------------------------------------------ ticking

	private static void tick(MinecraftServer server) {
		tickCount++;
		Judgement.tick(server);
		if (tickCount % 20L == 0L) {
			ExileManager.tick(server);
		}
		if (server.getPlayerManager().getCurrentPlayerCount() == 0) {
			return;
		}
		Bodies.tick(server, tickCount);
		if (tickCount % 10L == 0L) {
			Curses.tick(server);
		}
		CharterConfig config = CharterConfig.get();
		if (config.boundParticles && tickCount % 5L == 0L) {
			CharterState state = CharterState.get(server);
			for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
				if (state.isBound(player.getUuid()) && !player.isSpectator()) {
					bindParticles(player);
				}
			}
		}
	}

	/** Three golden motes circling the body: the chains of the contract. */
	private static void bindParticles(ServerPlayerEntity player) {
		ServerWorld world = player.getServerWorld();
		double base = (double) (tickCount % 360L) * 0.3;
		// without a body the motes circle the head
		double top = player.getHeight();
		for (int i = 0; i < 3; i++) {
			double angle = base + (double) i * (Math.PI * 2.0 / 3.0);
			double height = 0.1 + (double) ((tickCount / 5L + (long) (i * 5)) % 16L) * 0.11 * Math.min(1.0, top / 1.8);
			world.spawnParticles(Fx.GOLD_DUST_SMALL, player.getX() + Math.cos(angle) * 0.6, player.getY() + height, player.getZ() + Math.sin(angle) * 0.6, 1, 0.0, 0.0, 0.0, 0.0);
		}
	}

	// ------------------------------------------------------------------ joining and respawning

	private static void onJoin(ServerPlayNetworkHandler handler, PacketSender sender, MinecraftServer server) {
		ServerPlayerEntity player = handler.player;
		CharterConfig config = CharterConfig.get();
		CharterState state = CharterState.get(server);
		UUID id = player.getUuid();

		if (config.enforceOnJoin && state.isJudged(id)) {
			if (Banisher.isExempt(server, player.getGameProfile())) {
				// the host of a local world cannot be thrown out of it; the old judgement is simply forgotten
				state.pardon(id);
			} else {
				player.networkHandler.disconnect(Text.literal(config.kickMessage).formatted(Formatting.GOLD));
				return;
			}
		}

		Bodies.onEnter(player);

		if (state.isPendingExile(id)) {
			server.execute(() -> {
				if (!player.isRemoved()) {
					ExileManager.exile(player);
				}
			});
		}
	}

	private static void onRespawn(ServerPlayerEntity oldPlayer, ServerPlayerEntity newPlayer, boolean alive) {
		MinecraftServer server = newPlayer.getServer();
		if (server == null) {
			return;
		}
		CharterState state = CharterState.get(server);
		if (!alive) {
			if (CharterConfig.get().bodyRestoredOnDeath) {
				Bodies.restore(server, newPlayer.getUuid(), null);
			}
			if (state.isExiled(newPlayer.getUuid())) {
				// they died close enough to home for death to count: the respawn brings them back
				ExileManager.release(newPlayer, false);
			}
		}
		Bodies.onEnter(newPlayer);
	}

	// ------------------------------------------------------------------ damage and death

	/**
	 * Someone held in golden chains cannot be touched until the strike lands, and the signer of a contract cannot
	 * harm the player who drew it up.
	 */
	private static boolean allowDamage(LivingEntity entity, DamageSource source, float amount) {
		if (!(entity instanceof ServerPlayerEntity)) {
			return true;
		}
		ServerPlayerEntity victim = (ServerPlayerEntity) entity;
		if (Judgement.isCondemned(victim.getUuid())) {
			return false;
		}
		Entity attacker = source.getAttacker();
		if (attacker instanceof ServerPlayerEntity && attacker != victim) {
			MinecraftServer server = victim.getServer();
			if (server != null && Contracts.protectsCreator(server, attacker.getUuid(), victim.getUuid())) {
				Bodies.nag((ServerPlayerEntity) attacker, "You signed a contract with " + victim.getGameProfile().getName() + ". You cannot harm them.");
				return false;
			}
		}
		return true;
	}

	/** An exiled player cannot die: lethal damage leaves them on half a heart. */
	private static boolean allowDeath(LivingEntity entity, DamageSource source, float amount) {
		if (!(entity instanceof ServerPlayerEntity)) {
			return true;
		}
		ServerPlayerEntity player = (ServerPlayerEntity) entity;
		if (ExileManager.protects(player)) {
			player.setHealth(CharterConfig.get().exileMinHealth);
			return false;
		}
		return true;
	}

	// ------------------------------------------------------------------ interaction

	private static boolean exileLocksBlocks(MinecraftServer server, UUID id) {
		return CharterConfig.get().exileLocksBlocks && CharterState.get(server).isExiled(id);
	}

	/** A player who has lost the arm for this hand cannot use it: the action is refused (and they are told so). */
	private static boolean noArm(PlayerEntity player, Hand hand) {
		if (!Bodies.handLocked(player, hand)) {
			return false;
		}
		if (player instanceof ServerPlayerEntity) {
			Bodies.nag((ServerPlayerEntity) player, "You have no arm to do that with.");
		}
		return true;
	}

	private static ActionResult onAttackEntity(PlayerEntity player, World world, Hand hand, Entity entity, @Nullable EntityHitResult hit) {
		if (world.isClient || !(player instanceof ServerPlayerEntity)) {
			return ActionResult.PASS;
		}
		ServerPlayerEntity attacker = (ServerPlayerEntity) player;
		MinecraftServer server = attacker.getServer();
		if (server == null) {
			return ActionResult.PASS;
		}
		if (Judgement.isCondemned(attacker.getUuid())) {
			return ActionResult.FAIL;
		}
		if (noArm(attacker, hand)) {
			return ActionResult.FAIL;
		}
		if (!(entity instanceof ServerPlayerEntity)) {
			return ActionResult.PASS;
		}
		ServerPlayerEntity target = (ServerPlayerEntity) entity;

		// the signer of a contract cannot lift a hand against the one who drew it up
		if (Contracts.protectsCreator(server, attacker.getUuid(), target.getUuid())) {
			Bodies.nag(attacker, "You signed a contract with " + target.getGameProfile().getName() + ". You cannot harm them.");
			return ActionResult.FAIL;
		}

		ItemStack stack = attacker.getStackInHand(hand);

		// the creator's contract and a sword in the off hand: the blow takes an arm instead of dealing damage
		if (stack.getItem() instanceof ContractItem && ((ContractItem) stack.getItem()).isSigned()) {
			ActionResult maimed = ContractItem.maimAttack(attacker, hand, target, stack);
			if (maimed != ActionResult.PASS) {
				return maimed;
			}
		}

		// Dusk's Epitaph: one blow, and the target is seized by chains and judged
		if (hand != Hand.MAIN_HAND || !(stack.getItem() instanceof DuskEpitaphItem)) {
			return ActionResult.PASS;
		}
		if (target.isSpectator() || Judgement.isCondemned(target.getUuid())) {
			return ActionResult.PASS;
		}
		CharterConfig config = CharterConfig.get();
		if (config.weaponsRequireContract && !Contracts.isBound(server, attacker.getUuid())) {
			attacker.sendMessage(Text.literal("The Epitaph does not answer to you.").formatted(Formatting.GRAY, Formatting.ITALIC), true);
			return ActionResult.PASS;
		}
		if ((double) attacker.getAttackCooldownProgress(0.5f) < config.epitaphMinCharge) {
			// a weak, half-charged swing is just a heavy sword
			return ActionResult.PASS;
		}

		attacker.resetLastAttackedTicks();
		attacker.swingHand(Hand.MAIN_HAND, true);
		attacker.getItemCooldownManager().set(stack.getItem(), 40);
		ServerWorld serverWorld = attacker.getServerWorld();
		Fx.sound(serverWorld, target.getPos(), SoundEvents.ENTITY_PLAYER_ATTACK_STRONG, 2.0f, 0.5f);
		Fx.sound(serverWorld, target.getPos(), SoundEvents.ITEM_TRIDENT_THUNDER, 3.0f, 0.5f);
		Fx.sparks(serverWorld, target.getPos().add(0.0, 1.0, 0.0), 30, 0.4);
		Judgement.bindAndJudge(target, Verdict.BAN, attacker);
		return ActionResult.SUCCESS;
	}

	private static ActionResult onAttackBlock(PlayerEntity player, World world, Hand hand, BlockPos pos, Direction direction) {
		if (world.isClient) {
			return ActionResult.PASS;
		}
		MinecraftServer server = world.getServer();
		if (server == null) {
			return ActionResult.PASS;
		}
		UUID id = player.getUuid();
		if (Judgement.isCondemned(id) || exileLocksBlocks(server, id) || noArm(player, hand)) {
			return ActionResult.FAIL;
		}
		return ActionResult.PASS;
	}

	private static TypedActionResult<ItemStack> onUseItem(PlayerEntity player, World world, Hand hand) {
		ItemStack stack = player.getStackInHand(hand);
		if (world.isClient) {
			return TypedActionResult.pass(stack);
		}
		MinecraftServer server = world.getServer();
		if (server == null) {
			return TypedActionResult.pass(stack);
		}
		if (Judgement.isCondemned(player.getUuid()) || noArm(player, hand)) {
			return TypedActionResult.fail(stack);
		}
		return TypedActionResult.pass(stack);
	}

	private static ActionResult onUseBlock(PlayerEntity player, World world, Hand hand, BlockHitResult hit) {
		if (world.isClient) {
			return ActionResult.PASS;
		}
		MinecraftServer server = world.getServer();
		if (server == null) {
			return ActionResult.PASS;
		}
		UUID id = player.getUuid();
		if (Judgement.isCondemned(id) || noArm(player, hand)) {
			return ActionResult.FAIL;
		}
		if (exileLocksBlocks(server, id)) {
			player.sendMessage(Text.literal("In exile you may look, but not touch.").formatted(Formatting.GRAY, Formatting.ITALIC), true);
			return ActionResult.FAIL;
		}
		return ActionResult.PASS;
	}

	private static ActionResult onUseEntity(PlayerEntity player, World world, Hand hand, Entity entity, @Nullable EntityHitResult hit) {
		if (world.isClient) {
			return ActionResult.PASS;
		}
		MinecraftServer server = world.getServer();
		if (server == null) {
			return ActionResult.PASS;
		}
		if (Judgement.isCondemned(player.getUuid()) || noArm(player, hand)) {
			return ActionResult.FAIL;
		}
		return ActionResult.PASS;
	}

	private static boolean beforeBreak(World world, PlayerEntity player, BlockPos pos, BlockState state, @Nullable BlockEntity blockEntity) {
		MinecraftServer server = world.getServer();
		if (world.isClient || server == null) {
			return true;
		}
		UUID id = player.getUuid();
		if (Judgement.isCondemned(id) || exileLocksBlocks(server, id) || noArm(player, Hand.MAIN_HAND)) {
			return false;
		}
		if (state.isOf(ModBlocks.DIVINE_DOMINANCE) && CharterConfig.get().dominanceProtectedTablet) {
			boolean owner = blockEntity instanceof DivineDominanceBlockEntity && ((DivineDominanceBlockEntity) blockEntity).isOwner(id);
			if (!owner && !Contracts.isBound(server, id) && !player.hasPermissionLevel(2)) {
				player.sendMessage(Text.literal("The tablet does not yield to you.").formatted(Formatting.GOLD, Formatting.ITALIC), true);
				return false;
			}
		}
		return true;
	}
}
