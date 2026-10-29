package net.charter.registry;

import net.charter.CharterMod;
import net.charter.item.ContractItem;
import net.charter.item.DivineDominanceItem;
import net.charter.item.DuskEpitaphItem;
import net.charter.item.LesserDivinityItem;
import net.fabricmc.fabric.api.item.v1.FabricItemSettings;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Rarity;

public final class ModItems {
	/** A blank contract. Using it binds the signer to Charter. */
	public static final Item CHARTER_CONTRACT = register("charter_contract",
			new ContractItem(new FabricItemSettings().maxCount(16).rarity(Rarity.UNCOMMON), false));

	/** A contract that has been signed; it remembers who signed it. */
	public static final Item SIGNED_CONTRACT = register("signed_contract",
			new ContractItem(new FabricItemSettings().maxCount(1).rarity(Rarity.RARE), true));

	/** Three uses of a permanent ban, then it destroys itself in a crater. */
	public static final Item LESSER_DIVINITY = register("lesser_divinity",
			new LesserDivinityItem(new FabricItemSettings().maxCount(1).rarity(Rarity.EPIC).fireproof(), false));

	/** The rebuilt, patched Lesser Divinity: it exiles instead of banning. */
	public static final Item FRACTURED_LESSER_DIVINITY = register("fractured_lesser_divinity",
			new LesserDivinityItem(new FabricItemSettings().maxCount(1).rarity(Rarity.RARE).fireproof(), true));

	/** A shard of the Lesser Divinity, brushed out of the crater it left. */
	public static final Item LESSER_DIVINITY_FRAGMENT = register("lesser_divinity_fragment",
			new Item(new FabricItemSettings().rarity(Rarity.UNCOMMON).fireproof()));

	/** The huge rusted greatsword that bans with one blow. */
	public static final Item DUSK_EPITAPH = register("dusk_epitaph",
			new DuskEpitaphItem(new FabricItemSettings().rarity(Rarity.EPIC).fireproof()));

	/** The tablet that raises a shrinking golden cage. */
	public static final Item DIVINE_DOMINANCE = register("divine_dominance",
			new DivineDominanceItem(ModBlocks.DIVINE_DOMINANCE, new FabricItemSettings().maxCount(1).rarity(Rarity.EPIC).fireproof()));

	private ModItems() {
	}

	private static Item register(String name, Item item) {
		return Registry.register(Registries.ITEM, CharterMod.id(name), item);
	}

	public static void register() {
		// loading the class registers the items
	}
}
