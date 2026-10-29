package net.charter.registry;

import net.charter.CharterMod;
import net.charter.enchantment.CursedEnchantment;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

public final class ModEnchantments {
	/** Only the signer of the contract it was cursed with can hold the weapon. */
	public static final Enchantment CURSED = register("cursed", new CursedEnchantment());

	private ModEnchantments() {
	}

	private static Enchantment register(String name, Enchantment enchantment) {
		return Registry.register(Registries.ENCHANTMENT, CharterMod.id(name), enchantment);
	}

	public static void register() {
		// loading the class registers the enchantments
	}
}
