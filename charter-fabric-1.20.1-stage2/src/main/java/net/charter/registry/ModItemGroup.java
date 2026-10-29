package net.charter.registry;

import net.charter.CharterMod;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.text.Text;

public final class ModItemGroup {
	private ModItemGroup() {
	}

	public static void register() {
		ItemGroup group = FabricItemGroup.builder()
				.displayName(Text.translatable("itemGroup.charter.main"))
				.icon(() -> new ItemStack(ModItems.LESSER_DIVINITY))
				.entries((context, entries) -> {
					entries.add(ModItems.CHARTER_CONTRACT);
					entries.add(ModItems.LESSER_DIVINITY);
					entries.add(ModItems.FRACTURED_LESSER_DIVINITY);
					entries.add(ModItems.LESSER_DIVINITY_FRAGMENT);
					entries.add(ModItems.DUSK_EPITAPH);
					entries.add(ModItems.DIVINE_DOMINANCE);
				})
				.build();
		Registry.register(Registries.ITEM_GROUP, CharterMod.id("main"), group);
	}
}
