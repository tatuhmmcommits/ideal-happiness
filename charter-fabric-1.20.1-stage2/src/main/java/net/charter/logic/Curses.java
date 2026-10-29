package net.charter.logic;

import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import net.charter.CharterMod;
import net.charter.config.CharterConfig;
import net.charter.fx.Fx;
import net.charter.item.CharterWeapon;
import net.charter.registry.ModEnchantments;
import net.charter.state.CharterState;
import net.charter.state.ContractRecord;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypeFilter;

/**
 * The Cursed enchantment. A creator can curse a Charter weapon with a contract: from then on only the signer of
 * that contract can hold it. The weapon is delivered to the signer, anyone else who picks it up or is handed it
 * loses it again, and when the contract ends (the signer breaks it, or the creator releases them) the curse ends
 * with it.
 *
 * <p>The weapon carries the enchantment (so it can be seen in the tooltip) and a {@code CharterCurse} tag naming
 * the contract and the signer, which is what the checks go by.
 */
public final class Curses {
	public static final String TAG = "CharterCurse";
	private static final String CONTRACT = "Contract";
	private static final String SIGNER = "Signer";
	private static final String SIGNER_NAME = "SignerName";

	private Curses() {
	}

	// ------------------------------------------------------------------ reading a weapon

	public static boolean isCursed(ItemStack stack) {
		if (stack.isEmpty() || !(stack.getItem() instanceof CharterWeapon)) {
			return false;
		}
		NbtCompound nbt = stack.getNbt();
		return nbt != null && nbt.contains(TAG, NbtElement.COMPOUND_TYPE);
	}

	@Nullable
	public static UUID signerOf(ItemStack stack) {
		return isCursed(stack) && stack.getNbt().getCompound(TAG).containsUuid(SIGNER) ? stack.getNbt().getCompound(TAG).getUuid(SIGNER) : null;
	}

	@Nullable
	public static UUID contractOf(ItemStack stack) {
		return isCursed(stack) && stack.getNbt().getCompound(TAG).containsUuid(CONTRACT) ? stack.getNbt().getCompound(TAG).getUuid(CONTRACT) : null;
	}

	public static String signerNameOf(ItemStack stack) {
		return isCursed(stack) ? stack.getNbt().getCompound(TAG).getString(SIGNER_NAME) : "";
	}

	// ------------------------------------------------------------------ casting and lifting

	/**
	 * {@code creator} curses the weapon in their off hand with the contract {@code record}: it is marked, taken
	 * from them, and delivered to the signer. Returns false (with a message) when it cannot be done.
	 */
	public static boolean cast(ServerPlayerEntity creator, ContractRecord record) {
		MinecraftServer server = creator.getServer();
		ItemStack weapon = creator.getOffHandStack();
		if (server == null || !(weapon.getItem() instanceof CharterWeapon)) {
			return false;
		}
		if (!CharterConfig.get().cursedEnchantment) {
			creator.sendMessage(Text.literal("The Cursed enchantment has been switched off on this server.").formatted(Formatting.GRAY, Formatting.ITALIC), true);
			return false;
		}
		if (record.withCharter()) {
			creator.sendMessage(Text.literal("Only a contract with a player can curse a weapon.").formatted(Formatting.GRAY, Formatting.ITALIC), true);
			return false;
		}
		if (isCursed(weapon)) {
			creator.sendMessage(Text.literal("That weapon is already cursed.").formatted(Formatting.GRAY, Formatting.ITALIC), true);
			return false;
		}

		ItemStack cursed = weapon.copy();
		NbtCompound tag = new NbtCompound();
		tag.putUuid(CONTRACT, record.id);
		tag.putUuid(SIGNER, record.signer);
		tag.putString(SIGNER_NAME, record.signerName);
		cursed.getOrCreateNbt().put(TAG, tag);
		cursed.addEnchantment(ModEnchantments.CURSED, 1);
		creator.setStackInHand(Hand.OFF_HAND, ItemStack.EMPTY);

		ServerWorld world = creator.getServerWorld();
		Fx.sound(world, creator.getPos(), SoundEvents.ENTITY_ELDER_GUARDIAN_CURSE, 1.5f, 0.6f);
		Fx.sound(world, creator.getPos(), SoundEvents.BLOCK_AMETHYST_BLOCK_RESONATE, 2.0f, 0.5f);
		Fx.sparks(world, creator.getPos().add(0.0, 1.2, 0.0), 30, 0.4);

		ServerPlayerEntity signer = server.getPlayerManager().getPlayer(record.signer);
		double range = CharterConfig.get().curseDeliveryRange;
		if (signer != null && signer.getServerWorld() == world && signer.squaredDistanceTo(creator) <= range * range) {
			// the contract carries it to them
			signer.getInventory().insertStack(cursed);
			if (!cursed.isEmpty()) {
				signer.dropItem(cursed, false);
			}
			Fx.sparks(signer.getServerWorld(), signer.getPos().add(0.0, 1.2, 0.0), 30, 0.4);
			signer.sendMessage(Text.literal(record.creatorName + " has cursed a weapon to you. Only you can hold it.").formatted(Formatting.GOLD), false);
			creator.sendMessage(Text.literal("The weapon is drawn to " + record.signerName + ".").formatted(Formatting.GOLD), true);
		} else {
			// they are too far away: it waits, on the ground, for them and nobody else
			creator.dropItem(cursed, false);
			creator.sendMessage(Text.literal("The weapon waits for " + record.signerName + ". Only they can pick it up.").formatted(Formatting.GOLD), true);
		}
		CharterMod.LOGGER.info("{} cursed a weapon to {}", record.creatorName, record.signerName);
		return true;
	}

	/** Takes the curse off a weapon. */
	public static void strip(ItemStack stack) {
		stack.removeSubNbt(TAG);
		Map<Enchantment, Integer> enchantments = EnchantmentHelper.get(stack);
		if (enchantments.remove(ModEnchantments.CURSED) != null) {
			EnchantmentHelper.set(enchantments, stack);
		}
	}

	/**
	 * A contract has ended: every weapon cursed with it, in an inventory or lying on the ground, is free again.
	 * (Weapons in unloaded chunks, containers or offline players' inventories lose the curse the next time
	 * somebody holds them.)
	 */
	public static void lift(MinecraftServer server, UUID contractId) {
		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			PlayerInventory inventory = player.getInventory();
			for (int slot = 0; slot < inventory.size(); slot++) {
				ItemStack stack = inventory.getStack(slot);
				if (contractId.equals(contractOf(stack))) {
					strip(stack);
					player.sendMessage(Text.literal("The curse on your " + stack.getName().getString() + " fades.").formatted(Formatting.GOLD), true);
				}
			}
		}
		for (ServerWorld world : server.getWorlds()) {
			for (ItemEntity item : world.getEntitiesByType(TypeFilter.instanceOf(ItemEntity.class), entity -> contractId.equals(contractOf(entity.getStack())))) {
				ItemStack copy = item.getStack().copy();
				strip(copy);
				item.setStack(copy);
				item.setOwner(null);
			}
		}
	}

	// ------------------------------------------------------------------ keeping it from everyone else

	/** A cursed weapon that has been dropped or thrown can only be picked up by its signer, and never despawns. */
	public static void onItemLoaded(ItemEntity item) {
		UUID signer = signerOf(item.getStack());
		if (signer != null) {
			item.setOwner(signer);
			item.setNeverDespawn();
		}
	}

	/**
	 * Once every half second: any cursed weapon in somebody's inventory (or on their cursor) that does not belong
	 * to them is thrown out of it, and any curse whose contract is gone is lifted.
	 */
	public static void tick(MinecraftServer server) {
		CharterState state = CharterState.get(server);
		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			PlayerInventory inventory = player.getInventory();
			for (int slot = 0; slot < inventory.size(); slot++) {
				ItemStack stack = inventory.getStack(slot);
				if (!isCursed(stack)) {
					continue;
				}
				if (check(player, state, stack)) {
					continue;
				}
				inventory.setStack(slot, ItemStack.EMPTY);
				reject(player, stack);
			}
			ScreenHandler handler = player.currentScreenHandler;
			if (handler != null) {
				ItemStack held = handler.getCursorStack();
				if (isCursed(held) && !check(player, state, held)) {
					handler.setCursorStack(ItemStack.EMPTY);
					reject(player, held);
				}
			}
		}
	}

	/** True when the player may keep the (cursed) stack. A curse whose contract has ended is lifted on the spot. */
	private static boolean check(PlayerEntity player, CharterState state, ItemStack stack) {
		UUID contract = contractOf(stack);
		ContractRecord record = contract == null ? null : state.contract(contract);
		if (record == null) {
			strip(stack);
			return true;
		}
		return record.signer.equals(player.getUuid());
	}

	private static void reject(ServerPlayerEntity player, ItemStack stack) {
		String owner = signerNameOf(stack);
		player.dropItem(stack, false);
		player.sendMessage(Text.literal("The weapon will not stay in your hands. It answers only to " + (owner.isEmpty() ? "its signer" : owner) + ".").formatted(Formatting.RED, Formatting.ITALIC), true);
		Fx.sound(player.getServerWorld(), player.getPos(), SoundEvents.BLOCK_CHAIN_BREAK, 1.0f, 0.8f);
	}
}
