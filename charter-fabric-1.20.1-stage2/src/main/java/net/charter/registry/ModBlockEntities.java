package net.charter.registry;

import net.charter.CharterMod;
import net.charter.block.DivineDominanceBlockEntity;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

public final class ModBlockEntities {
	public static final BlockEntityType<DivineDominanceBlockEntity> DIVINE_DOMINANCE = Registry.register(
			Registries.BLOCK_ENTITY_TYPE,
			CharterMod.id("divine_dominance"),
			FabricBlockEntityTypeBuilder.create(DivineDominanceBlockEntity::new, ModBlocks.DIVINE_DOMINANCE).build());

	private ModBlockEntities() {
	}

	public static void register() {
		// loading the class registers the block entity types
	}
}
