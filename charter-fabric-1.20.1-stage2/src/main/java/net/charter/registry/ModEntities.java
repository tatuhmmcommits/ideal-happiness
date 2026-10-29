package net.charter.registry;

import net.charter.CharterMod;
import net.charter.fx.CharterFxEntity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

public final class ModEntities {
	/** One invisible entity that the client draws as a sigil, golden chains, a beam or the shrinking cage. */
	public static final EntityType<CharterFxEntity> CHARTER_FX = Registry.register(
			Registries.ENTITY_TYPE,
			CharterMod.id("fx"),
			EntityType.Builder.<CharterFxEntity>create(CharterFxEntity::new, SpawnGroup.MISC)
					.setDimensions(0.25f, 0.25f)
					.maxTrackingRange(24)
					.trackingTickInterval(2)
					.makeFireImmune()
					.disableSaving()
					.disableSummon()
					.build("fx"));

	private ModEntities() {
	}

	public static void register() {
		// loading the class registers the entity type
	}
}
