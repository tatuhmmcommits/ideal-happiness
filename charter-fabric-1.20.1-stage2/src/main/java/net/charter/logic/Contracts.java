package net.charter.logic;

import java.util.List;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import net.charter.CharterMod;
import net.charter.body.BodyPart;
import net.charter.body.Bodies;
import net.charter.config.CharterConfig;
import net.charter.fx.Fx;
import net.charter.item.ContractItem;
import net.charter.state.CharterState;
import net.charter.state.ContractRecord;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;

/**
 * Contracts bind two parties. The <em>creator</em> draws one up and the <em>signer</em> puts their name to it.
 *
 * <ul>
 * <li>The signer can never harm the creator.</li>
 * <li>The creator may take the signer's arms (contract in the main hand, a sword in the off hand).</li>
 * <li>The creator may curse a Charter weapon so that only the signer can hold it.</li>
 * <li>The signer may break the contract, and every break costs a part of their body: the left arm, the right
 * arm, the left leg, the right leg and finally everything but the head. They may sign again, and break again,
 * for as long as there is something left to give.</li>
 * <li>The creator may also release the signer, at no price.</li>
 * </ul>
 *
 * A contract signed with Charter itself has no player as its creator, so nobody can be harmed through it; it can
 * only be broken, and breaking it costs the same.
 */
public final class Contracts {
	private Contracts() {
	}

	// ------------------------------------------------------------------ questions

	/** True while the player is the signer of at least one contract. */
	public static boolean isBound(MinecraftServer server, UUID id) {
		return CharterState.get(server).isBound(id);
	}

	/** The contract a written copy refers to, or null when the copy is blank or its contract no longer exists. */
	@Nullable
	public static ContractRecord recordOf(MinecraftServer server, ItemStack stack) {
		UUID id = ContractItem.contractIdOf(stack);
		return id == null ? null : CharterState.get(server).contract(id);
	}

	/**
	 * True when {@code attacker} has signed a contract that {@code victim} created: the attacker may not harm them.
	 * Always false when the config lets signers fight their creators.
	 */
	public static boolean protectsCreator(MinecraftServer server, UUID attacker, UUID victim) {
		if (!CharterConfig.get().creatorProtected || attacker.equals(victim)) {
			return false;
		}
		return CharterState.get(server).contractBetween(victim, attacker) != null;
	}

	/** What breaking a contract would cost this player right now, as a phrase such as "your left arm". */
	public static String price(MinecraftServer server, UUID id) {
		if (!CharterConfig.get().breakingCostsBody) {
			return "nothing";
		}
		BodyPart next = BodyPart.next(Bodies.mask(server, id));
		return next == null ? "nothing more: only your head is left" : "your " + next.label;
	}

	// ------------------------------------------------------------------ signing

	/**
	 * {@code creator} draws up a contract and {@code signer} signs it. Returns the new record, or null when the
	 * two are already bound by one.
	 */
	@Nullable
	public static ContractRecord sign(ServerPlayerEntity creator, ServerPlayerEntity signer) {
		MinecraftServer server = creator.getServer();
		if (server == null) {
			return null;
		}
		CharterState state = CharterState.get(server);
		if (state.contractBetween(creator.getUuid(), signer.getUuid()) != null) {
			return null;
		}
		ContractRecord record = new ContractRecord(UUID.randomUUID(), creator.getUuid(), creator.getGameProfile().getName(),
				signer.getUuid(), signer.getGameProfile().getName(), server.getOverworld().getTime());
		state.addContract(record);
		signedEffects(signer);
		CharterMod.LOGGER.info("{} signed a contract drawn up by {}", record.signerName, record.creatorName);
		return record;
	}

	/** {@code signer} signs a contract with Charter itself. Returns null when the player is not on a server. */
	@Nullable
	public static ContractRecord signWithCharter(ServerPlayerEntity signer) {
		MinecraftServer server = signer.getServer();
		if (server == null) {
			return null;
		}
		ContractRecord record = new ContractRecord(UUID.randomUUID(), ContractRecord.CHARTER, ContractRecord.CHARTER_NAME,
				signer.getUuid(), signer.getGameProfile().getName(), server.getOverworld().getTime());
		CharterState.get(server).addContract(record);
		signedEffects(signer);
		CharterMod.LOGGER.info("{} signed a contract with Charter", record.signerName);
		return record;
	}

	private static void signedEffects(ServerPlayerEntity signer) {
		ServerWorld world = signer.getServerWorld();
		Vec3d at = signer.getPos();
		Fx.sparks(world, at.add(0.0, 1.0, 0.0), 40, 0.5);
		Fx.sound(world, at, SoundEvents.ENTITY_ELDER_GUARDIAN_CURSE, 1.0f, 1.4f);
		Fx.sound(world, at, SoundEvents.BLOCK_CHAIN_PLACE, 2.0f, 0.7f);
		Fx.chains(world, signer, 50);
	}

	// ------------------------------------------------------------------ ending a contract

	/**
	 * The signer breaks the contract. It is gone, every curse that depended on it is lifted, and if the config says
	 * so the signer pays with the next part of their body. Returns false when the contract no longer existed.
	 */
	public static boolean breakContract(MinecraftServer server, ContractRecord record) {
		CharterState state = CharterState.get(server);
		if (!state.removeContract(record.id)) {
			return false;
		}
		Curses.lift(server, record.id);

		ServerPlayerEntity signer = server.getPlayerManager().getPlayer(record.signer);
		BodyPart lost = null;
		if (CharterConfig.get().breakingCostsBody) {
			lost = Bodies.loseNext(server, record.signer);
		}
		if (signer != null) {
			String what = lost != null ? " It takes your " + lost.label + "." : (CharterConfig.get().breakingCostsBody ? " There is nothing left for it to take." : "");
			signer.sendMessage(Text.literal("You break your contract with " + record.creatorName + "." + what).formatted(Formatting.GOLD), false);
			ServerWorld world = signer.getServerWorld();
			Fx.sound(world, signer.getPos(), SoundEvents.ENTITY_ITEM_BREAK, 1.0f, 0.6f);
			Fx.sound(world, signer.getPos(), SoundEvents.BLOCK_AMETHYST_BLOCK_BREAK, 1.5f, 0.5f);
		}
		if (!record.withCharter()) {
			ServerPlayerEntity creator = server.getPlayerManager().getPlayer(record.creator);
			if (creator != null) {
				creator.sendMessage(Text.literal(record.signerName + " has broken their contract with you.").formatted(Formatting.GRAY, Formatting.ITALIC), false);
			}
		}
		CharterMod.LOGGER.info("{} broke their contract with {}", record.signerName, record.creatorName);
		return true;
	}

	/** The creator lets the signer go. It costs nobody anything. */
	public static boolean release(MinecraftServer server, ContractRecord record) {
		CharterState state = CharterState.get(server);
		if (!state.removeContract(record.id)) {
			return false;
		}
		Curses.lift(server, record.id);

		ServerPlayerEntity signer = server.getPlayerManager().getPlayer(record.signer);
		if (signer != null) {
			signer.sendMessage(Text.literal(record.creatorName + " has released you from your contract. It costs you nothing.").formatted(Formatting.GOLD), false);
		}
		ServerPlayerEntity creator = server.getPlayerManager().getPlayer(record.creator);
		if (creator != null) {
			creator.sendMessage(Text.literal("You release " + record.signerName + " from their contract.").formatted(Formatting.GOLD), false);
			Fx.sound(creator.getServerWorld(), creator.getPos(), SoundEvents.BLOCK_CHAIN_BREAK, 1.0f, 1.0f);
		}
		return true;
	}

	/** Operators: every contract the player has signed is lifted, at no price. Returns how many there were. */
	public static int unbind(MinecraftServer server, UUID signerId) {
		CharterState state = CharterState.get(server);
		List<ContractRecord> records = state.contractsSignedBy(signerId);
		for (ContractRecord record : records) {
			state.removeContract(record.id);
			Curses.lift(server, record.id);
		}
		if (!records.isEmpty()) {
			ServerPlayerEntity online = server.getPlayerManager().getPlayer(signerId);
			if (online != null) {
				online.sendMessage(Text.literal("Your contracts have been lifted.").formatted(Formatting.GOLD), false);
			}
		}
		return records.size();
	}

	/** Operators: the player breaks their oldest contract, and pays the price for it. */
	public static boolean sever(MinecraftServer server, UUID signerId) {
		List<ContractRecord> records = CharterState.get(server).contractsSignedBy(signerId);
		if (records.isEmpty()) {
			return false;
		}
		return breakContract(server, records.get(0));
	}

	// ------------------------------------------------------------------ the creator's blade

	/**
	 * The creator takes an arm from the signer. Returns false when the signer has no arms left to take.
	 */
	public static boolean maim(ServerPlayerEntity creator, ServerPlayerEntity signer) {
		MinecraftServer server = creator.getServer();
		if (server == null) {
			return false;
		}
		BodyPart arm = BodyPart.nextArm(Bodies.mask(server, signer.getUuid()));
		if (arm == null) {
			return false;
		}
		ServerWorld world = signer.getServerWorld();
		Vec3d at = signer.getPos().add(0.0, 1.2, 0.0);
		Fx.sound(world, at, SoundEvents.ENTITY_PLAYER_ATTACK_STRONG, 1.5f, 0.6f);
		Fx.sparks(world, at, 20, 0.3);
		Bodies.lose(server, signer.getUuid(), arm);
		creator.sendMessage(Text.literal("You take " + signer.getGameProfile().getName() + "'s " + arm.label + ".").formatted(Formatting.GOLD), true);
		CharterMod.LOGGER.info("{} took the {} of {}", creator.getGameProfile().getName(), arm.label, signer.getGameProfile().getName());
		return true;
	}
}
