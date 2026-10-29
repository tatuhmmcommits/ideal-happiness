package net.charter.enchantment;

import net.charter.item.CharterWeapon;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentTarget;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;

/**
 * Cursed: a Charter weapon that carries it answers only to the signer of the contract it was cursed with. Nobody
 * else can pick it up, and anyone else who ends up holding it loses it. The curse cannot be put on by an
 * enchanting table or an anvil: it comes from a contract (see {@link net.charter.logic.Curses}) and ends when
 * that contract does.
 */
public class CursedEnchantment extends Enchantment {
	public CursedEnchantment() {
		super(Rarity.VERY_RARE, EnchantmentTarget.BREAKABLE, new EquipmentSlot[] {EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND});
	}

	@Override
	public int getMaxLevel() {
		return 1;
	}

	@Override
	public boolean isAcceptableItem(ItemStack stack) {
		return stack.getItem() instanceof CharterWeapon;
	}

	@Override
	public boolean isTreasure() {
		return true;
	}

	@Override
	public boolean isCursed() {
		return true;
	}

	@Override
	public boolean isAvailableForEnchantedBookOffer() {
		return false;
	}

	@Override
	public boolean isAvailableForRandomSelection() {
		return false;
	}
}
