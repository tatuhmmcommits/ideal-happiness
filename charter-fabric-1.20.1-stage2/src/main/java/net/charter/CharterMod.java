package net.charter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.charter.command.CharterCommand;
import net.charter.config.CharterConfig;
import net.charter.logic.CharterRuntime;
import net.charter.registry.ModBlockEntities;
import net.charter.registry.ModBlocks;
import net.charter.registry.ModEnchantments;
import net.charter.registry.ModEntities;
import net.charter.registry.ModItemGroup;
import net.charter.registry.ModItems;
import net.fabricmc.api.ModInitializer;
import net.minecraft.util.Identifier;

/**
 * Charter (fan recreation). Unofficial: a clean-room fan project, not affiliated with the authors of the original
 * mod or with the ContentSMP.
 */
public class CharterMod implements ModInitializer {
	public static final String MOD_ID = "charter";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static Identifier id(String path) {
		return new Identifier(MOD_ID, path);
	}

	@Override
	public void onInitialize() {
		CharterConfig.load();

		// order matters: blocks, then block entities and items that refer to them
		ModBlocks.register();
		ModBlockEntities.register();
		ModItems.register();
		ModEnchantments.register();
		ModEntities.register();
		ModItemGroup.register();

		CharterRuntime.init();
		CharterCommand.register();

		LOGGER.info("Charter has been signed. Its judgements are final.");
	}
}
