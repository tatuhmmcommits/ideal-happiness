package net.charter.item;

import net.minecraft.item.ToolMaterial;
import net.minecraft.recipe.Ingredient;

/** The metal of Dusk's Epitaph: heavy, rusted, and it never wears out. */
public final class EpitaphMaterial implements ToolMaterial {
	public static final EpitaphMaterial INSTANCE = new EpitaphMaterial();

	private EpitaphMaterial() {
	}

	@Override
	public int getDurability() {
		// zero means "not damageable": the blade does not break
		return 0;
	}

	@Override
	public float getMiningSpeedMultiplier() {
		return 4.0f;
	}

	@Override
	public float getAttackDamage() {
		return 5.0f;
	}

	@Override
	public int getMiningLevel() {
		return 4;
	}

	@Override
	public int getEnchantability() {
		return 0;
	}

	@Override
	public Ingredient getRepairIngredient() {
		return Ingredient.EMPTY;
	}
}
