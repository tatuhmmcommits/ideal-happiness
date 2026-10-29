package net.charter.config;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.charter.CharterMod;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Every tunable value of the mod. Stored as pretty-printed JSON in {@code config/charter.json}.
 * Missing keys fall back to the defaults below, so the file can be edited by hand or deleted to reset it.
 * Reload at runtime with {@code /charter reload}.
 */
public final class CharterConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private static CharterConfig instance = new CharterConfig();

	// ------------------------------------------------------------------ permanent ban
	/** Master switch. When false every "BAN" verdict is replaced by {@link #fallbackVerdict}. */
	public boolean permanentBans = true;
	/** What happens instead of a ban when a ban is switched off or the target is exempt: EXILE or KICK. */
	public String fallbackVerdict = "EXILE";
	/** Operators cannot be banned when true. Off by default: on the ContentSMP even the server owner was banned. */
	public boolean exemptOperators = false;
	/** The host of a singleplayer / LAN world is never banned (they would lose their own world). */
	public boolean exemptSingleplayerHost = true;
	/** Banned players drop their whole inventory where they stood, as if they had died. */
	public boolean dropInventoryOnBan = true;
	/** Add the player to the vanilla ban list (banned-players.json). */
	public boolean useVanillaBanList = true;
	/** Charter keeps its own list of judged players and kicks them on join even if the vanilla list was edited. */
	public boolean enforceOnJoin = true;
	public String banSource = "Charter";
	public String banReason = "Judged by Charter.";
	/** Shown on the disconnect screen. "\n" starts a new line. */
	public String kickMessage = "Charter has judged you.\nYour name has been struck from this world.";
	public boolean broadcastJudgements = true;
	/** %s is replaced by the player's name. */
	public String broadcastMessage = "%s has been judged by Charter.";

	// ------------------------------------------------------------------ telegraph
	/** Ticks (20 per second) between the golden sigil appearing and the strike. */
	public int sigilTelegraphTicks = 80;
	/** Ticks a target stays bound in golden chains before Dusk's Epitaph finishes the judgement. */
	public int chainTelegraphTicks = 40;
	/** How long the golden beam of the strike stays visible. */
	public int strikeBeamTicks = 30;
	public double sigilRadius = 5.0;

	// ------------------------------------------------------------------ Lesser Divinity
	public int lesserDivinityCharges = 3;
	public double lesserDivinityRange = 96.0;
	public int lesserDivinityCooldownTicks = 60;
	public int fracturedCooldownTicks = 60;
	public int craterRadius = 10;
	public int craterDepth = 5;
	/** Chance that a block of the crater becomes suspicious gravel holding a fragment. */
	public double craterSuspiciousChance = 0.4;
	public float detonationPower = 4.0f;

	// ------------------------------------------------------------------ Dusk's Epitaph
	/** Attack cooldown progress (0 to 1) needed for a blow to ban. 0.85 means "nearly a full swing". */
	public double epitaphMinCharge = 0.85;

	// ------------------------------------------------------------------ exile
	/** Blocks from the world spawn that an exiled player is thrown. */
	public double exileDistance = 7777840.0;
	public String exileDimension = "minecraft:overworld";
	/** An exiled player is freed on coming this close to where they were exiled from. */
	public int exileReturnRadius = 1000;
	/** Exiled players cannot die. Lethal damage leaves them on exileMinHealth. */
	public boolean exileUnkillable = true;
	/** The exile stops protecting from death this close to home ("now this can actually kill me"). */
	public int exileLethalRadius = 25000;
	public float exileMinHealth = 1.0f;
	/** Exiled players cannot break, place or use blocks: they may look, but not touch. */
	public boolean exileLocksBlocks = true;
	public boolean exileMessages = true;

	// ------------------------------------------------------------------ Divine Dominance
	/** Half width of the golden square when the cage closes (blocks from the tablet to each wall). */
	public double dominanceStartRadius = 24.0;
	/** Half width at which the cage has fully closed. */
	public double dominanceEndRadius = 1.0;
	/** Ticks between placing the tablet and the walls starting to move. */
	public int dominanceArmTicks = 100;
	/** Ticks the walls take to close in. */
	public int dominanceShrinkTicks = 2400;
	public int dominanceHeightUp = 48;
	public int dominanceHeightDown = 24;
	/** What happens to everyone still inside when the cage closes: BAN or KILL. */
	public String dominanceVerdict = "BAN";
	/** Damage per second once the walls are squeezing (only the last few blocks). */
	public float dominanceCrushDamage = 4.0f;
	/** The tablet can only be broken by its owner or a Charter-bound player. */
	public boolean dominanceProtectedTablet = true;

	// ------------------------------------------------------------------ contracts
	/** The player a contract is used on must crouch to accept it (that is their signature). */
	public boolean contractNeedsConsent = true;
	/** Crouching and using a blank contract twice in the air signs it with Charter itself. */
	public boolean contractCharterSigning = true;
	/** The signer of a contract cannot harm the player who created it. */
	public boolean creatorProtected = true;
	/** The creator may take the signer's arms: attack with the contract in the main hand and a sword in the off hand. */
	public boolean creatorMayMaim = true;
	/** Ticks before the contract can take another arm. */
	public int maimCooldownTicks = 600;
	/** The creator may release the signer from the contract, at no price (crouch and use the contract). */
	public boolean creatorMayRelease = true;
	/** The signer may break a contract (crouch and use it twice). */
	public boolean contractMayBeBroken = true;
	/**
	 * Breaking a contract costs a part of the body, in this order: left arm, right arm, left leg, right leg,
	 * and finally everything but the head. When false, breaking is free.
	 */
	public boolean breakingCostsBody = true;
	/** A player with no arm for a hand cannot use that hand. */
	public boolean lostArmsLockHands = true;
	/** Players who lost legs are slowed (one leg), crawl and cannot jump (both), or can only hop (head only). */
	public boolean lostLegsSlow = true;
	/** When true, lost body parts grow back on death. Off by default: in the lore only Charter can give them back. */
	public boolean bodyRestoredOnDeath = false;
	/** A creator can curse a Charter weapon with a contract so that only the signer can hold it. */
	public boolean cursedEnchantment = true;
	/** A cursed weapon is delivered straight into the signer's inventory when they are this close (blocks). */
	public double curseDeliveryRange = 64.0;
	/** Charter-bound players are wreathed in golden chain particles. */
	public boolean boundParticles = true;
	/** When true the Lesser Divinity, Dusk's Epitaph and Divine Dominance only answer to bound players. */
	public boolean weaponsRequireContract = false;

	// ------------------------------------------------------------------ access
	public static CharterConfig get() {
		return instance;
	}

	public static void load() {
		Path path = FabricLoader.getInstance().getConfigDir().resolve("charter.json");
		CharterConfig loaded = null;
		if (Files.exists(path)) {
			try (Reader reader = Files.newBufferedReader(path)) {
				loaded = GSON.fromJson(reader, CharterConfig.class);
			} catch (IOException | RuntimeException e) {
				CharterMod.LOGGER.error("Could not read {}; using the defaults instead", path, e);
			}
		}
		instance = loaded != null ? loaded : new CharterConfig();
		instance.sanitize();
		try (Writer writer = Files.newBufferedWriter(path)) {
			GSON.toJson(instance, writer);
		} catch (IOException e) {
			CharterMod.LOGGER.warn("Could not write {}", path, e);
		}
	}

	private void sanitize() {
		fallbackVerdict = pick(fallbackVerdict, "EXILE", "EXILE", "KICK");
		dominanceVerdict = pick(dominanceVerdict, "BAN", "BAN", "KILL");
		if (banSource == null || banSource.isEmpty()) {
			banSource = "Charter";
		}
		if (banReason == null) {
			banReason = "";
		}
		if (kickMessage == null) {
			kickMessage = "";
		}
		if (broadcastMessage == null || !broadcastMessage.contains("%s")) {
			broadcastMessage = "%s has been judged by Charter.";
		}
		if (exileDimension == null || exileDimension.isEmpty()) {
			exileDimension = "minecraft:overworld";
		}
		sigilTelegraphTicks = clamp(sigilTelegraphTicks, 1, 1200);
		chainTelegraphTicks = clamp(chainTelegraphTicks, 1, 1200);
		strikeBeamTicks = clamp(strikeBeamTicks, 5, 400);
		sigilRadius = clamp(sigilRadius, 1.0, 64.0);
		lesserDivinityCharges = clamp(lesserDivinityCharges, 1, 64);
		lesserDivinityRange = clamp(lesserDivinityRange, 4.0, 512.0);
		lesserDivinityCooldownTicks = clamp(lesserDivinityCooldownTicks, 0, 72000);
		fracturedCooldownTicks = clamp(fracturedCooldownTicks, 0, 72000);
		craterRadius = clamp(craterRadius, 2, 24);
		craterDepth = clamp(craterDepth, 1, 16);
		craterSuspiciousChance = clamp(craterSuspiciousChance, 0.0, 1.0);
		detonationPower = (float) clamp(detonationPower, 0.0, 16.0);
		epitaphMinCharge = clamp(epitaphMinCharge, 0.0, 1.0);
		exileDistance = clamp(exileDistance, 100.0, 29_000_000.0);
		exileReturnRadius = clamp(exileReturnRadius, 16, 1_000_000);
		exileLethalRadius = clamp(exileLethalRadius, exileReturnRadius, 29_000_000);
		exileMinHealth = (float) clamp(exileMinHealth, 0.5, 20.0);
		dominanceStartRadius = clamp(dominanceStartRadius, 3.0, 128.0);
		dominanceEndRadius = clamp(dominanceEndRadius, 0.5, dominanceStartRadius);
		dominanceArmTicks = clamp(dominanceArmTicks, 0, 24000);
		dominanceShrinkTicks = clamp(dominanceShrinkTicks, 20, 240000);
		dominanceHeightUp = clamp(dominanceHeightUp, 4, 320);
		dominanceHeightDown = clamp(dominanceHeightDown, 2, 320);
		dominanceCrushDamage = (float) clamp(dominanceCrushDamage, 0.0, 1000.0);
		maimCooldownTicks = clamp(maimCooldownTicks, 0, 72000);
		curseDeliveryRange = clamp(curseDeliveryRange, 0.0, 1024.0);
	}

	private static String pick(String value, String fallback, String... allowed) {
		if (value != null) {
			String upper = value.trim().toUpperCase(Locale.ROOT);
			for (String option : allowed) {
				if (option.equals(upper)) {
					return upper;
				}
			}
		}
		return fallback;
	}

	private static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}

	private static double clamp(double value, double min, double max) {
		return Math.max(min, Math.min(max, value));
	}
}
