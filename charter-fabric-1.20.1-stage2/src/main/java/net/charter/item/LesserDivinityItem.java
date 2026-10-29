package net.charter.item;

import java.util.List;
import java.util.Locale;

import org.jetbrains.annotations.Nullable;

import net.charter.config.CharterConfig;
import net.charter.logic.Contracts;
import net.charter.logic.Detonation;
import net.charter.logic.Judgement;
import net.charter.logic.Targeting;
import net.charter.logic.Verdict;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * The Lesser Divinity. Point it at the ground or at someone and a golden sigil is drawn there; when the sigil
 * completes, a beam falls and everyone standing inside it is permanently banned.
 *
 * <p>It has only three uses. On the third it destroys itself, blowing up into a million pieces and leaving a
 * giant crater whose suspicious blocks each hide a fragment.
 *
 * <p>The fractured version (rebuilt from those fragments) is the "patched" one: it no longer bans, it casts its
 * victims out millions of blocks from home, and it does not run out.
 */
public class LesserDivinityItem extends Item implements CharterWeapon {
	private static final String USES = "Uses";

	private final boolean fractured;

	public LesserDivinityItem(Settings settings, boolean fractured) {
		super(settings);
		this.fractured = fractured;
	}

	public boolean isFractured() {
		return fractured;
	}

	public static int usesOf(ItemStack stack) {
		NbtCompound nbt = stack.getNbt();
		return nbt == null ? 0 : nbt.getInt(USES);
	}

	@Override
	public boolean hasGlint(ItemStack stack) {
		return true;
	}

	// the three charges are shown on the durability bar

	@Override
	public boolean isItemBarVisible(ItemStack stack) {
		return !fractured && usesOf(stack) > 0;
	}

	@Override
	public int getItemBarStep(ItemStack stack) {
		int charges = Math.max(1, CharterConfig.get().lesserDivinityCharges);
		int left = Math.max(0, charges - usesOf(stack));
		return MathHelper.clamp(Math.round(13.0f * (float) left / (float) charges), 0, 13);
	}

	@Override
	public int getItemBarColor(ItemStack stack) {
		return 0xFFC83C;
	}

	// ------------------------------------------------------------------ using it

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		ItemStack stack = user.getStackInHand(hand);
		if (world.isClient || !(user instanceof ServerPlayerEntity)) {
			return TypedActionResult.pass(stack);
		}
		ServerPlayerEntity player = (ServerPlayerEntity) user;
		MinecraftServer server = player.getServer();
		if (server == null) {
			return TypedActionResult.pass(stack);
		}
		CharterConfig config = CharterConfig.get();
		ServerWorld serverWorld = player.getServerWorld();

		if (config.weaponsRequireContract && !Contracts.isBound(server, player.getUuid())) {
			player.sendMessage(Text.literal("The Lesser Divinity does not answer to you.").formatted(Formatting.GRAY, Formatting.ITALIC), true);
			return TypedActionResult.fail(stack);
		}

		Vec3d target = Targeting.aim(player, config.lesserDivinityRange);
		if (target == null) {
			player.sendMessage(Text.literal("Nothing within Charter's sight.").formatted(Formatting.GRAY, Formatting.ITALIC), true);
			return TypedActionResult.fail(stack);
		}

		Runnable afterStrike = null;
		boolean spent = false;
		if (!fractured) {
			int uses = usesOf(stack) + 1;
			if (uses >= config.lesserDivinityCharges) {
				spent = true;
				final BlockPos craterCenter = BlockPos.ofFloored(target);
				afterStrike = () -> Detonation.detonate(serverWorld, craterCenter);
			} else {
				stack.getOrCreateNbt().putInt(USES, uses);
			}
		}

		Judgement.sigilStrike(serverWorld, target, config.sigilRadius, fractured ? Verdict.EXILE : Verdict.BAN, player, afterStrike);

		if (spent) {
			// the last charge: the item cannot be used again, and it will not outlive the judgement
			stack.decrement(1);
			serverWorld.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_ITEM_BREAK, SoundCategory.PLAYERS, 1.0f, 0.5f);
			serverWorld.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BLOCK_AMETHYST_BLOCK_RESONATE, SoundCategory.PLAYERS, 2.0f, 0.5f);
			player.sendMessage(Text.literal("The Lesser Divinity cracks. It will not survive this judgement.").formatted(Formatting.GOLD, Formatting.ITALIC), true);
		} else {
			int cooldown = fractured ? config.fracturedCooldownTicks : config.lesserDivinityCooldownTicks;
			if (cooldown > 0) {
				player.getItemCooldownManager().set(this, cooldown);
			}
		}
		return TypedActionResult.success(stack);
	}

	// ------------------------------------------------------------------ tooltip

	@Override
	public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
		CharterConfig config = CharterConfig.get();
		if (fractured) {
			tooltip.add(Text.translatable("tooltip.charter.fractured_lesser_divinity.1").formatted(Formatting.GRAY));
			tooltip.add(Text.translatable("tooltip.charter.fractured_lesser_divinity.2", String.format(Locale.ROOT, "%,d", Math.round(config.exileDistance))).formatted(Formatting.GRAY));
			tooltip.add(Text.translatable("tooltip.charter.fractured_lesser_divinity.3").formatted(Formatting.DARK_GRAY, Formatting.ITALIC));
		} else {
			int charges = Math.max(1, config.lesserDivinityCharges);
			int left = Math.max(0, charges - usesOf(stack));
			tooltip.add(Text.translatable("tooltip.charter.lesser_divinity.charges", left, charges).formatted(Formatting.GOLD));
			tooltip.add(Text.translatable("tooltip.charter.lesser_divinity.1").formatted(Formatting.GRAY));
			tooltip.add(Text.translatable("tooltip.charter.lesser_divinity.2").formatted(Formatting.DARK_GRAY, Formatting.ITALIC));
		}
	}
}
