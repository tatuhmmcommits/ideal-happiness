package net.charter.registry;

import net.charter.CharterMod;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageType;
import net.minecraft.entity.Entity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.world.World;

/** Damage types are data driven: see data/charter/damage_type/. */
public final class ModDamageTypes {
	public static final RegistryKey<DamageType> JUDGEMENT = RegistryKey.of(RegistryKeys.DAMAGE_TYPE, CharterMod.id("judgement"));
	public static final RegistryKey<DamageType> CRUSH = RegistryKey.of(RegistryKeys.DAMAGE_TYPE, CharterMod.id("crush"));

	private ModDamageTypes() {
	}

	public static DamageSource source(World world, RegistryKey<DamageType> key) {
		return world.getDamageSources().create(key);
	}

	public static DamageSource source(World world, RegistryKey<DamageType> key, Entity attacker) {
		return world.getDamageSources().create(key, attacker);
	}
}
