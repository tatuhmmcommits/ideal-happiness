package net.charter.item;

import java.util.List;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import net.charter.config.CharterConfig;
import net.charter.logic.Confirm;
import net.charter.logic.Contracts;
import net.charter.logic.Curses;
import net.charter.registry.ModItems;
import net.charter.state.ContractRecord;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SwordItem;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

/**
 * A contract with Charter: a leather book with gold on it, and, once it has been used on someone, a red wax
 * signet mark.
 *
 * <p>A <b>blank</b> contract is used on a player who crouches to accept it: the user becomes the creator, the
 * other the signer, and each receives a signed copy. Crouching and using it twice in the air signs it with Charter
 * itself. A <b>signed</b> copy refers to its contract by id; what it can do depends on who holds it:
 * <ul>
 * <li>the creator can attack the signer with it (and a sword in the off hand) to take an arm, use it with a
 * Charter weapon in the off hand to curse the weapon, or crouch and use it to release the signer;</li>
 * <li>the signer can crouch and use it to break the contract, which costs a part of their body.</li>
 * </ul>
 */
public class ContractItem extends Item {
	public static final String ID = "ContractId";
	public static final String ROLE = "Role";
	public static final String CREATOR_NAME = "CreatorName";
	public static final String SIGNER_NAME = "SignerName";
	private static final String CREATOR_ROLE = "creator";
	private static final String SIGNER_ROLE = "signer";

	private final boolean signed;

	public ContractItem(Settings settings, boolean signed) {
		super(settings);
		this.signed = signed;
	}

	public boolean isSigned() {
		return signed;
	}

	// ------------------------------------------------------------------ the written copies

	/** A written copy of a contract, for its creator or its signer. */
	public static ItemStack copyOf(ContractRecord record, boolean creatorCopy) {
		ItemStack stack = new ItemStack(ModItems.SIGNED_CONTRACT);
		NbtCompound nbt = stack.getOrCreateNbt();
		nbt.putUuid(ID, record.id);
		nbt.putString(ROLE, creatorCopy ? CREATOR_ROLE : SIGNER_ROLE);
		nbt.putString(CREATOR_NAME, record.creatorName);
		nbt.putString(SIGNER_NAME, record.signerName);
		return stack;
	}

	/** The contract a written copy refers to. */
	@Nullable
	public static UUID contractIdOf(ItemStack stack) {
		NbtCompound nbt = stack.getNbt();
		if (nbt != null && nbt.containsUuid(ID)) {
			return nbt.getUuid(ID);
		}
		return null;
	}

	private static String text(ItemStack stack, String key) {
		NbtCompound nbt = stack.getNbt();
		return nbt != null && nbt.contains(key) ? nbt.getString(key) : "";
	}

	@Override
	public boolean hasGlint(ItemStack stack) {
		return signed;
	}

	private static void give(ServerPlayerEntity player, ItemStack stack) {
		player.getInventory().insertStack(stack);
		if (!stack.isEmpty()) {
			player.dropItem(stack, false);
		}
	}

	private static void tell(PlayerEntity player, String text, Formatting... formats) {
		player.sendMessage(Text.literal(text).formatted(formats), true);
	}

	// ------------------------------------------------------------------ using it

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		ItemStack stack = user.getStackInHand(hand);
		if (world.isClient || !(user instanceof ServerPlayerEntity)) {
			return TypedActionResult.success(stack);
		}
		ServerPlayerEntity player = (ServerPlayerEntity) user;
		MinecraftServer server = player.getServer();
		if (server == null) {
			return TypedActionResult.pass(stack);
		}
		return signed ? useSigned(world, player, server, hand, stack) : useBlank(world, player, server, stack);
	}

	/** A blank contract in the air: crouch, and do it twice, to sign with Charter itself. */
	private TypedActionResult<ItemStack> useBlank(World world, ServerPlayerEntity player, MinecraftServer server, ItemStack stack) {
		CharterConfig config = CharterConfig.get();
		if (!player.isSneaking()) {
			tell(player, "Use it on a player who crouches to draw up a contract with them. Crouch and use it to sign with Charter.", Formatting.GRAY, Formatting.ITALIC);
			return TypedActionResult.fail(stack);
		}
		if (!config.contractCharterSigning) {
			tell(player, "Charter does not sign contracts on this server.", Formatting.GRAY, Formatting.ITALIC);
			return TypedActionResult.fail(stack);
		}
		if (!Confirm.again(player, "charter-sign", 100)) {
			tell(player, "Crouch and use it again to sign with Charter. Breaking that contract will cost you " + Contracts.price(server, player.getUuid()) + ".", Formatting.GOLD);
			return TypedActionResult.fail(stack);
		}
		ContractRecord record = Contracts.signWithCharter(player);
		if (record == null) {
			return TypedActionResult.fail(stack);
		}
		world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ITEM_BOOK_PAGE_TURN, SoundCategory.PLAYERS, 1.0f, 0.7f);
		tell(player, "You sign. You are bound to Charter.", Formatting.GOLD);
		stack.decrement(1);
		give(player, copyOf(record, false));
		return TypedActionResult.success(stack);
	}

	private TypedActionResult<ItemStack> useSigned(World world, ServerPlayerEntity player, MinecraftServer server, Hand hand, ItemStack stack) {
		ContractRecord record = Contracts.recordOf(server, stack);
		if (record == null) {
			tell(player, "The ink has faded: this contract was broken.", Formatting.GRAY, Formatting.ITALIC);
			world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_ITEM_BREAK, SoundCategory.PLAYERS, 1.0f, 0.6f);
			stack.decrement(1);
			return TypedActionResult.success(stack);
		}
		if (record.creator.equals(player.getUuid())) {
			return useAsCreator(world, player, server, hand, stack, record);
		}
		if (record.signer.equals(player.getUuid())) {
			return useAsSigner(world, player, server, stack, record);
		}
		tell(player, "A contract between " + record.creatorName + " and " + record.signerName + ".", Formatting.GOLD);
		return TypedActionResult.fail(stack);
	}

	private TypedActionResult<ItemStack> useAsCreator(World world, ServerPlayerEntity player, MinecraftServer server, Hand hand, ItemStack stack, ContractRecord record) {
		CharterConfig config = CharterConfig.get();
		if (hand == Hand.MAIN_HAND && !player.isSneaking() && player.getOffHandStack().getItem() instanceof CharterWeapon) {
			return Curses.cast(player, record) ? TypedActionResult.success(stack) : TypedActionResult.fail(stack);
		}
		if (player.isSneaking() && config.creatorMayRelease) {
			if (!Confirm.again(player, "release:" + record.id, 100)) {
				tell(player, "Crouch and use it again to release " + record.signerName + " from the contract. It costs nobody anything.", Formatting.GOLD);
				return TypedActionResult.fail(stack);
			}
			Contracts.release(server, record);
			world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_ITEM_BREAK, SoundCategory.PLAYERS, 1.0f, 0.8f);
			stack.decrement(1);
			return TypedActionResult.success(stack);
		}
		tell(player, "Your contract with " + record.signerName + ". Crouch and use it to release them.", Formatting.GOLD);
		return TypedActionResult.fail(stack);
	}

	private TypedActionResult<ItemStack> useAsSigner(World world, ServerPlayerEntity player, MinecraftServer server, ItemStack stack, ContractRecord record) {
		if (!CharterConfig.get().contractMayBeBroken) {
			tell(player, "This contract cannot be broken.", Formatting.GRAY, Formatting.ITALIC);
			return TypedActionResult.fail(stack);
		}
		String price = Contracts.price(server, player.getUuid());
		if (!player.isSneaking()) {
			tell(player, "Your contract with " + record.creatorName + ". Crouch and use it to break it: it will cost you " + price + ".", Formatting.GOLD);
			return TypedActionResult.fail(stack);
		}
		if (!Confirm.again(player, "break:" + record.id, 100)) {
			tell(player, "Crouch and use it again to break the contract. It will cost you " + price + ".", Formatting.RED);
			return TypedActionResult.fail(stack);
		}
		Contracts.breakContract(server, record);
		stack.decrement(1);
		return TypedActionResult.success(stack);
	}

	/** A blank contract used on a player who crouches to accept it: this player is the creator, the other signs. */
	@Override
	public ActionResult useOnEntity(ItemStack stack, PlayerEntity user, LivingEntity entity, Hand hand) {
		if (signed || !(entity instanceof PlayerEntity)) {
			return ActionResult.PASS;
		}
		if (user.getWorld().isClient) {
			return ActionResult.SUCCESS;
		}
		if (!(user instanceof ServerPlayerEntity) || !(entity instanceof ServerPlayerEntity)) {
			return ActionResult.PASS;
		}
		ServerPlayerEntity creator = (ServerPlayerEntity) user;
		ServerPlayerEntity signer = (ServerPlayerEntity) entity;
		MinecraftServer server = creator.getServer();
		if (server == null) {
			return ActionResult.PASS;
		}
		String signerName = signer.getGameProfile().getName();
		String creatorName = creator.getGameProfile().getName();

		if (CharterConfig.get().contractNeedsConsent && !signer.isSneaking()) {
			tell(creator, signerName + " must crouch to sign.", Formatting.GRAY, Formatting.ITALIC);
			tell(signer, creatorName + " offers you a contract. Crouch to sign it. If you ever break it, it will cost you " + Contracts.price(server, signer.getUuid()) + ".", Formatting.GOLD);
			creator.getItemCooldownManager().set(this, 20);
			return ActionResult.SUCCESS;
		}
		ContractRecord record = Contracts.sign(creator, signer);
		if (record == null) {
			tell(creator, "You already hold a contract on " + signerName + ".", Formatting.GRAY, Formatting.ITALIC);
			creator.getItemCooldownManager().set(this, 20);
			return ActionResult.SUCCESS;
		}
		creator.getServerWorld().playSound(null, signer.getX(), signer.getY(), signer.getZ(), SoundEvents.ITEM_BOOK_PAGE_TURN, SoundCategory.PLAYERS, 1.0f, 0.7f);
		stack.decrement(1);
		give(creator, copyOf(record, true));
		give(signer, copyOf(record, false));
		tell(creator, signerName + " has signed. You may not be harmed by them, and you may take their arms.", Formatting.GOLD);
		tell(signer, "You have signed " + creatorName + "'s contract. You cannot harm them.", Formatting.GOLD);
		return ActionResult.SUCCESS;
	}

	/**
	 * Called when a creator hits someone with a signed contract in their main hand. With a sword in the off hand
	 * and the contract's signer in front of them, the blow takes an arm instead of dealing damage.
	 */
	public static ActionResult maimAttack(ServerPlayerEntity attacker, Hand hand, ServerPlayerEntity target, ItemStack stack) {
		CharterConfig config = CharterConfig.get();
		MinecraftServer server = attacker.getServer();
		if (hand != Hand.MAIN_HAND || server == null || !config.creatorMayMaim) {
			return ActionResult.PASS;
		}
		ContractRecord record = Contracts.recordOf(server, stack);
		if (record == null || !record.creator.equals(attacker.getUuid()) || !record.signer.equals(target.getUuid())) {
			return ActionResult.PASS;
		}
		if (!(attacker.getOffHandStack().getItem() instanceof SwordItem)) {
			// without a sword in the other hand it is only a book hitting someone
			return ActionResult.PASS;
		}
		if (attacker.getItemCooldownManager().isCoolingDown(stack.getItem())) {
			tell(attacker, "The contract is not ready to be carried out again.", Formatting.GRAY, Formatting.ITALIC);
			return ActionResult.FAIL;
		}
		if (!Contracts.maim(attacker, target)) {
			tell(attacker, target.getGameProfile().getName() + " has no arms left to take.", Formatting.GRAY, Formatting.ITALIC);
			return ActionResult.FAIL;
		}
		attacker.getItemCooldownManager().set(stack.getItem(), config.maimCooldownTicks);
		attacker.swingHand(Hand.MAIN_HAND, true);
		return ActionResult.SUCCESS;
	}

	// ------------------------------------------------------------------ tooltip

	@Override
	public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
		if (!signed) {
			tooltip.add(Text.translatable("tooltip.charter.charter_contract.1").formatted(Formatting.GRAY));
			tooltip.add(Text.translatable("tooltip.charter.charter_contract.2").formatted(Formatting.GRAY));
			tooltip.add(Text.translatable("tooltip.charter.charter_contract.3").formatted(Formatting.GRAY));
			tooltip.add(Text.translatable("tooltip.charter.charter_contract.4").formatted(Formatting.DARK_GRAY, Formatting.ITALIC));
			return;
		}
		String creator = text(stack, CREATOR_NAME);
		String signer = text(stack, SIGNER_NAME);
		tooltip.add(Text.translatable("tooltip.charter.signed_contract.creator", creator.isEmpty() ? "?" : creator).formatted(Formatting.GOLD));
		tooltip.add(Text.translatable("tooltip.charter.signed_contract.signer", signer.isEmpty() ? "?" : signer).formatted(Formatting.GOLD));
		String role = text(stack, ROLE);
		if (CREATOR_ROLE.equals(role)) {
			tooltip.add(Text.translatable("tooltip.charter.signed_contract.creator.1").formatted(Formatting.GRAY));
			tooltip.add(Text.translatable("tooltip.charter.signed_contract.creator.2").formatted(Formatting.GRAY));
			tooltip.add(Text.translatable("tooltip.charter.signed_contract.creator.3").formatted(Formatting.GRAY));
		} else if (SIGNER_ROLE.equals(role)) {
			tooltip.add(Text.translatable("tooltip.charter.signed_contract.signer.1").formatted(Formatting.GRAY));
			tooltip.add(Text.translatable("tooltip.charter.signed_contract.signer.2").formatted(Formatting.GRAY));
		}
	}
}
