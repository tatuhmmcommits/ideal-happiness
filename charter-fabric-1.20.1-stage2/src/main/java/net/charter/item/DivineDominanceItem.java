package net.charter.item;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import net.charter.config.CharterConfig;
import net.charter.logic.Contracts;
import net.minecraft.block.Block;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;

/** The tablet of Divine Dominance, as an item. Placing it sets the golden cage. */
public class DivineDominanceItem extends BlockItem implements CharterWeapon {
	public DivineDominanceItem(Block block, Settings settings) {
		super(block, settings);
	}

	@Override
	public ActionResult place(ItemPlacementContext context) {
		PlayerEntity player = context.getPlayer();
		World world = context.getWorld();
		if (player != null && !world.isClient && CharterConfig.get().weaponsRequireContract) {
			MinecraftServer server = world.getServer();
			if (server != null && !Contracts.isBound(server, player.getUuid())) {
				player.sendMessage(Text.literal("The tablet does not answer to you.").formatted(Formatting.GRAY, Formatting.ITALIC), true);
				return ActionResult.FAIL;
			}
		}
		return super.place(context);
	}

	@Override
	public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
		CharterConfig config = CharterConfig.get();
		tooltip.add(Text.translatable("tooltip.charter.divine_dominance.1").formatted(Formatting.GRAY));
		tooltip.add(Text.translatable("tooltip.charter.divine_dominance.2", (int) Math.round(config.dominanceStartRadius * 2.0)).formatted(Formatting.GRAY));
		tooltip.add(Text.translatable("tooltip.charter.divine_dominance.3").formatted(Formatting.GRAY));
		tooltip.add(Text.translatable("tooltip.charter.divine_dominance.4").formatted(Formatting.DARK_GRAY, Formatting.ITALIC));
	}
}
