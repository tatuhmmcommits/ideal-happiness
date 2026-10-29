# Charter (Fan Recreation) for Minecraft 1.20.1 (Fabric)

An **unofficial fan recreation** of the *Charter* mod from the ContentSMP: golden contracts that bind a soul, the
golden sigils, beams and chains of a judgement, the weapons that can **permanently ban** a player, and the
exile that comes after the patch.

> **Read this first.** This is a clean-room recreation written from the ContentSMP videos (the five transcripts you
> supplied) and the public ContentSMP wiki page for Charter. The original mod is private, none of its code was seen,
> and this project is not affiliated with its authors or with the ContentSMP. Everything the videos show is
> implemented; where they leave a gap, the gap was filled with a reasonable guess and is marked **(inferred)**
> below. The project could not be compiled in the environment it was written in, so see
> [If the build fails](#if-the-build-fails).

## Getting the jar

### Easiest: let GitHub build it (no Java needed)

1. Unzip the download. Create a new, empty repository on GitHub and upload **everything inside the unzipped
   folder** (`src`, `gradle`, `gradlew`, `build.gradle`, ... and the hidden `.github` folder) so that `gradlew` and
   `build.gradle` sit at the top of the repository. (If you upload the folder itself by mistake the workflow still
   finds the project one level down, so that no longer breaks the build.)
2. Open the repository's **Actions** tab. The `build` workflow starts by itself on every push (or press
   *Run workflow*).
3. When it turns green, open the run and download the artifact **`charter-jar`**. Inside is
   `charter-fabric-mc1.20.1-<version>.jar`.
4. A second job, `smoke-test`, starts a dedicated server with the mod and attaches its log as the artifact
   **`server-log`**. It never fails the workflow; if the game crashes on start-up, that log is what to send back.
5. To publish the jar as a release instead, push a tag such as `v1.0.0`: the jar is attached to the release.

### Or build it yourself

You need **JDK 25** (the current Fabric Loom needs it to *run*; the mod itself is compiled for Java 17):

```
./gradlew build            # Windows: gradlew.bat build
```

The jar appears in `build/libs/`. Use the one without `-dev` or `-sources` in its name.

## Installing

* Minecraft **1.20.1**, Fabric Loader **0.14.21 or newer**, **Fabric API** (built against 0.92.12+1.20.1), Java 17+.
* Put the jar in `mods/`. **Install it on the server *and* on every client**: the effects, the items and the
  missing-arm model need the mod on both sides.
* Nothing needs a resource pack. The first launch writes `config/charter.json`.

## What is in it

| Item / block | What it is |
| --- | --- |
| **Charter Contract** | A leather book with gold on it. Use it on a crouching player to draw up a contract with them, or crouch and use it twice to sign with Charter itself. |
| **Signed Contract** | The written copy of a contract, with a red signet-ring seal. The creator's copy and the signer's copy do different things. |
| **Cursed** *(enchantment)* | Can only go on Charter weapons, and only through a contract: only the contract's signer can hold the weapon. |
| **Lesser Divinity** | Three uses of a permanent ban. The third destroys it and leaves a crater. |
| **Lesser Divinity Fragment** | A shard brushed out of the crater. |
| **Fractured Lesser Divinity** | The patched, rebuilt version: it exiles instead of banning and never runs out. |
| **Dusk's Epitaph** | A huge rusted greatsword. One full-strength blow on a player chains and bans them. |
| **Divine Dominance** | A tablet that raises a golden cage which slowly closes in. |

Everything except the Signed Contract (you get that by signing) is in the creative tab **Charter**, and every item works with `/give @s charter:<item>` (the ids are `charter_contract`, `signed_contract`, `lesser_divinity`, `lesser_divinity_fragment`, `fractured_lesser_divinity`, `dusk_epitaph` and `divine_dominance`).

### Crafting

* **Charter Contract**: paper + ink sac + gold nugget (shapeless).
* **Fractured Lesser Divinity**: eight Lesser Divinity Fragments in a ring around a **Signed Contract**. *(inferred)*
* The Lesser Divinity, Dusk's Epitaph and Divine Dominance cannot be crafted; on the ContentSMP they were obtained by
  deals and trades. Hand them out with `/give`, or have the crater give you the fragments.

## How it plays

### Contracts, the Cursed enchantment and lost limbs
A contract has two sides: the **creator** draws it up, the **signer** puts their name to it.

* **Drawing one up:** use a blank contract on a player who is crouching (that is their consent). You become the
  creator and they become the signer; each of you receives a signed copy (a book with a red signet-ring seal).
  Crouch and use a blank contract in the air, twice, to sign with Charter itself (nobody is the creator then).
* **The signer can never harm the creator.** Swords, the Epitaph, Lesser Divinity sigils and the Divine Dominance
  wall all leave the creator alone.
* **The creator can take the signer's arms:** hold the signed contract in your main hand and a sword in your off
  hand, and strike the signer. The first blow takes their left arm, the next their right arm.
* **Curse a weapon:** hold the signed contract in your main hand and a Charter weapon (Lesser Divinity, Dusk's
  Epitaph, Divine Dominance) in your off hand, and use the contract without crouching. The weapon gains the
  **Cursed** enchantment and is delivered to the signer; **only the signer can pick it up or hold it**; anyone else
  who ends up with it loses it again. The curse ends with the contract.
* **Breaking a contract** (the signer crouches and uses their copy, twice) **costs a part of their body**: the left
  arm, then the right arm, then the left leg, the right leg, and finally everything but the head. They can sign again
  and break again, over and over, until they are only a head that hops.
* The creator can **release** the signer (crouch and use the creator's copy, twice) at no price.
* **What a missing part does:** it disappears from the model (third person, first person and the armour layer, for
  everyone who has the mod). A missing arm can no longer use, hit, break or place with that hand, and the off hand
  slot is emptied; one missing leg slows you; two missing legs and you sit on the ground with a smaller hitbox and
  cannot jump; with only a head left you are half a block wide and can only hop. `/charter body restore <player>`
  gives everything back; by default dying does not.
* A player who is the signer of any contract is "bound": golden motes circle them and they can walk through the
  walls of Divine Dominance.

### Lesser Divinity
Point it at the ground or at a creature (up to 96 blocks). A golden **sigil** is drawn, burns brighter for four
seconds, and then a **beam** falls: every player standing in the sigil (except the wielder) is **permanently
banned**: their inventory drops where they stood, they are added to the server's ban list, and they are disconnected
with the Charter message. Charter also keeps its own list and kicks the judged on join even if the ban list is edited.
The **third use destroys the item**: it blows up into a huge crater whose **suspicious sand and gravel** each hide a
**Lesser Divinity Fragment** (brush them out with a brush).

### Fractured Lesser Divinity
The patched version. It no longer bans: the victim is **exiled 7,777,840 blocks** from the world spawn. Exiled
players **cannot die** (lethal damage leaves them on half a heart) until they are within 25,000 blocks of home, **cannot
break, place or use blocks**, and the exile ends when they get back within 1,000 blocks of where they were thrown
from. It does not run out.

### Dusk's Epitaph
Held in **both hands** (main hand holds it, off hand empty; with something in the off hand it is a one-handed
hold). A slow, heavy greatsword. Against anything but a player it is just a heavy blade. A **fully charged** blow on a
player is not damage: golden chains seize them, they are held in place while a sigil burns under them, then the beam
falls and they are banned.

### Divine Dominance *(inferred from the wiki)*
Place the tablet and a golden square wall rises (48 blocks wide, 48 up and 24 down). After five seconds it starts to
close in and takes two minutes to meet in the middle. Whoever is inside is held in, whoever is outside is kept out;
the placer, Charter-bound players, and spectators pass freely. When the walls meet, everyone left inside is chained
and judged (ban by default). The tablet can only be broken by its owner, a bound player or an operator, and it
shatters once the judgements are done. Right-click it to read how long is left.

## Commands

All need permission level 2 (operator).

| Command | Effect |
| --- | --- |
| `/charter bind <players>` | Each player signs a contract with Charter itself. |
| `/charter unbind <players>` | Lift every contract the players have signed, at no price. |
| `/charter sever <players>` | Each player breaks their oldest contract and pays with the next body part. |
| `/charter body remove <players> [part]` | Take a body part (the next in line when none is named). Parts: `left_arm`, `right_arm`, `left_leg`, `right_leg`, `body`. |
| `/charter body restore <players> [part]` | Give a part back (everything when none is named). |
| `/charter judge <players> [ban\|exile\|kick\|kill]` | Chain and judge (default `ban`). |
| `/charter sigil <pos> <verdict> [radius]` | Draw a sigil that judges whoever stands in it. |
| `/charter exile <players>` | Exile at once. |
| `/charter release <players> [home]` | End an exile (optionally teleporting them home). |
| `/charter pardon <name>` | Remove a ban placed by Charter. |
| `/charter crater <pos>` | Test the crater of the Lesser Divinity. |
| `/charter list` | Every contract, who is missing which parts, who is exiled and who is judged. |
| `/charter reload` | Re-read `config/charter.json`. |

## Configuration

`config/charter.json`, re-read with `/charter reload`. Missing keys fall back to the defaults; delete the file to
reset it. Tick values are in game ticks (20 per second).

#### Permanent ban

| Key | Default | What it does |
| --- | --- | --- |
| `permanentBans` | `true` | Master switch. When false every "BAN" verdict is replaced by `fallbackVerdict`. |
| `fallbackVerdict` | `EXILE` | What happens instead of a ban when a ban is switched off or the target is exempt: EXILE or KICK. |
| `exemptOperators` | `false` | Operators cannot be banned when true. Off by default: on the ContentSMP even the server owner was banned. |
| `exemptSingleplayerHost` | `true` | The host of a singleplayer / LAN world is never banned (they would lose their own world). |
| `dropInventoryOnBan` | `true` | Banned players drop their whole inventory where they stood, as if they had died. |
| `useVanillaBanList` | `true` | Add the player to the vanilla ban list (banned-players.json). |
| `enforceOnJoin` | `true` | Charter keeps its own list of judged players and kicks them on join even if the vanilla list was edited. |
| `banSource` | `Charter` | Source name written to the vanilla ban list. |
| `banReason` | `Judged by Charter.` | Reason written to the vanilla ban list. |
| `kickMessage` | `Charter has judged you.\nYour name has been struck from this world.` | Shown on the disconnect screen. "\n" starts a new line. |
| `broadcastJudgements` | `true` | Announce every ban in chat. |
| `broadcastMessage` | `%s has been judged by Charter.` | %s is replaced by the player's name. |

#### Telegraph

| Key | Default | What it does |
| --- | --- | --- |
| `sigilTelegraphTicks` | `80` | Ticks (20 per second) between the golden sigil appearing and the strike. |
| `chainTelegraphTicks` | `40` | Ticks a target stays bound in golden chains before Dusk's Epitaph finishes the judgement. |
| `strikeBeamTicks` | `30` | How long the golden beam of the strike stays visible. |
| `sigilRadius` | `5.0` | Radius in blocks of the sigil the Lesser Divinity draws; everyone inside it is judged. |

#### Lesser Divinity

| Key | Default | What it does |
| --- | --- | --- |
| `lesserDivinityCharges` | `3` | Uses before the Lesser Divinity destroys itself. |
| `lesserDivinityRange` | `96.0` | How far (in blocks) the wielder can aim it. |
| `lesserDivinityCooldownTicks` | `60` | Item cooldown after each use. |
| `fracturedCooldownTicks` | `60` | Item cooldown after each use of the Fractured Lesser Divinity. |
| `craterRadius` | `10` | Radius in blocks of the crater left by the last use. |
| `craterDepth` | `5` | Depth in blocks at the centre of that crater. |
| `craterSuspiciousChance` | `0.4` | Chance that a block of the crater becomes suspicious gravel holding a fragment. |
| `detonationPower` | `4.0` | Strength of the blast of the last use. It hurts and throws creatures but never breaks blocks. 0 turns it off. |

#### Dusk's Epitaph

| Key | Default | What it does |
| --- | --- | --- |
| `epitaphMinCharge` | `0.85` | Attack cooldown progress (0 to 1) needed for a blow to ban. 0.85 means "nearly a full swing". |

#### Exile

| Key | Default | What it does |
| --- | --- | --- |
| `exileDistance` | `7777840.0` | Blocks from the world spawn that an exiled player is thrown. |
| `exileDimension` | `minecraft:overworld` | Dimension the exile takes place in. |
| `exileReturnRadius` | `1000` | An exiled player is freed on coming this close to where they were exiled from. |
| `exileUnkillable` | `true` | Exiled players cannot die. Lethal damage leaves them on exileMinHealth. |
| `exileLethalRadius` | `25000` | The exile stops protecting from death this close to home ("now this can actually kill me"). |
| `exileMinHealth` | `1.0` | Health an exiled player is left on instead of dying (1.0 is half a heart). |
| `exileLocksBlocks` | `true` | Exiled players cannot break, place or use blocks: they may look, but not touch. |
| `exileMessages` | `true` | Tell exiled players how far from home they are. |

#### Divine Dominance

| Key | Default | What it does |
| --- | --- | --- |
| `dominanceStartRadius` | `24.0` | Half width of the golden square when the cage closes (blocks from the tablet to each wall). |
| `dominanceEndRadius` | `1.0` | Half width at which the cage has fully closed. |
| `dominanceArmTicks` | `100` | Ticks between placing the tablet and the walls starting to move. |
| `dominanceShrinkTicks` | `2400` | Ticks the walls take to close in. |
| `dominanceHeightUp` | `48` | Blocks the walls reach above the tablet. |
| `dominanceHeightDown` | `24` | Blocks the walls reach below the tablet. |
| `dominanceVerdict` | `BAN` | What happens to everyone still inside when the cage closes: BAN or KILL. |
| `dominanceCrushDamage` | `4.0` | Damage per second once the walls are squeezing (only the last few blocks). |
| `dominanceProtectedTablet` | `true` | The tablet can only be broken by its owner or a Charter-bound player. |

#### Contracts and bodies

| Key | Default | What it does |
| --- | --- | --- |
| `contractNeedsConsent` | `true` | The player a contract is used on has to be crouching. |
| `contractCharterSigning` | `true` | A blank contract may be signed with Charter itself (crouch and use it twice). |
| `creatorProtected` | `true` | The signer of a contract cannot harm its creator. |
| `creatorMayMaim` | `true` | The creator may take the signer's arms (contract in the main hand, a sword in the off hand). |
| `maimCooldownTicks` | `600` | How long the contract needs before it can take another arm. |
| `creatorMayRelease` | `true` | The creator may release the signer at no price. |
| `contractMayBeBroken` | `true` | The signer may break the contract. |
| `breakingCostsBody` | `true` | Breaking a contract costs the next body part. |
| `lostArmsLockHands` | `true` | A hand whose arm is gone cannot be used, and the off-hand slot is emptied. |
| `lostLegsSlow` | `true` | Missing legs slow you down; with none you cannot jump. |
| `bodyRestoredOnDeath` | `false` | When true, dying grows everything back. Off by default: in the lore only Charter gives it back. |
| `cursedEnchantment` | `true` | Contracts may curse Charter weapons. |
| `curseDeliveryRange` | `64.0` | A cursed weapon is put straight into the signer's inventory when they are this close; otherwise it waits on the ground, and only they can pick it up. |
| `boundParticles` | `true` | Contract signers are wreathed in golden chain particles. |
| `weaponsRequireContract` | `false` | When true the Lesser Divinity, Dusk's Epitaph and Divine Dominance only answer to contract signers. |


## What is faithful and what is a guess

**Taken from the videos:** the golden beams and chains; the permanent ban (title screen, never again); contracts
whose breaking costs a body part; a player without an arm not showing it and not using that hand; the Lesser Divinity (a sigil, then a beam, three uses, the last
detonating into a giant crater of suspicious blocks hiding pieces); the patched version that exiles about
7,777,840 blocks, cannot die until about 25,000 blocks from home, and cannot touch blocks; Dusk's Epitaph as a rusted
greatsword that chains and bans.

**Taken from your description:** the creator / signer contract, the signer being unable to harm the creator, the
creator taking an arm with the contract and a sword, breaking a contract over and over until only a head is left,
and the Cursed enchantment that only the signer can hold.

**Inferred (the sources do not say):** exact timings, radii and damage numbers; the crafting recipes; the textures
and models (all drawn from scratch: no texture of the original mod was used); how Divine Dominance behaves in
detail (it comes from the wiki, not the videos); the order in which body parts go; the speed penalties; the
particle effects; the wording of every message.

**Not included:** the *Mark of the Benefactor*, the "Charter clothes", the Mouthpiece NPC, and the features of the
public 1.19.2 Charter mod (claims, vessels and the rest). The design is ready for them but they were out of scope.

## If the build fails

The code was checked as far as possible without a compiler (every import and every Minecraft call was checked
against the 1.20.1 Yarn mappings, and all sources pass a Java syntax check), but it could not be compiled here. If
GitHub's build goes red, open the failed run, copy the first few lines that contain `error:` and send them back:
a mistake of this kind is normally a one-line fix.

## Project layout

```
src/main/java/net/charter/     common code (items, block, commands, logic, state, config)
src/client/java/net/charter/   client code (renderer for the golden effects, missing-arm mixin)
src/main/resources/            mod metadata, assets (models, textures, lang), data (recipes, loot, damage types)
docs/SPEC.md                   the design notes behind each mechanic
```

## Credits

Original Charter mod and the ContentSMP belong to their creators. This fan recreation is MIT licensed (see
`LICENSE`); the original mod's own licence is not affected by it. Built with Fabric.
