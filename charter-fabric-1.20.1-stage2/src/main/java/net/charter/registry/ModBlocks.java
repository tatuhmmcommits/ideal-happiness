package net.charter.registry;

import net.charter.CharterMod;
import net.charter.block.DivineDominanceBlock;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.MapColor;
import net.minecraft.block.piston.PistonBehavior;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.BlockSoundGroup;

public final class ModBlocks {
	/** The tablet of Divine Dominance. Nothing short of its owner or a bound soul takes it down. */
	public static final Block DIVINE_DOMINANCE = Registry.register(
			Registries.BLOCK,
			CharterMod.id("divine_dominance"),
			new DivineDominanceBlock(AbstractBlock.Settings.create()
					.mapColor(MapColor.GOLD)
					.strength(5.0f, 3_600_000.0f)
					.sounds(BlockSoundGroup.NETHERITE)
					.luminance(state -> 10)
					.nonOpaque()
					.pistonBehavior(PistonBehavior.BLOCK)));

	private ModBlocks() {
	}

	public static void register() {
		// loading the class registers the blocks
	}
}
