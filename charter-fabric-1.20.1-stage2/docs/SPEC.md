# Design notes

These notes explain how the mod is put together, where each mechanic comes from, and how to check it in game.

## 1. Provenance

* **Sources.** Five ContentSMP video transcripts supplied with the commission (referred to below by the first
  eight characters of their file names) and the ContentSMP wiki page for Charter
  (`content-smp.fandom.com/wiki/Charter`, which the commissioner said is not fully up to date with the lore).
  No video could be watched; only the transcripts were available.
* **Clean room.** The original Charter is a private project. None of its code, assets or data was seen. Every name,
  number and texture here was made for this project.
* **Confidence labels** used below: **[seen]** stated in a transcript, **[wiki]** from the wiki only,
  **[guess]** filled in by the author because the sources are silent.

## 2. What the transcripts establish

| Fact | Where |
| --- | --- |
| Charter makes golden/yellow beams and chains, and is "the only mod on the server that can permanently ban people" | 9943c364 |
| A banned player is fully removed from the server | 9943c364 |
| Mouthpiece (a spirit) runs Charter and sells powerful items through deals; one of them is the Lesser Divinity | e9870b00, d2c25a8d, f7ce434e |
| A previous holder of the contract was "trapped in golden chains and permanently disappeared in a ravaging explosion" | f7ce434e |
| The Lesser Divinity bans with a beam; its last use "scattered into a million pieces, leaving a giant crater"; the crater holds suspicious blocks hiding something | d2c25a8d |
| The Lesser Divinity can be rebuilt as a "half broken" version | d2c25a8d |
| The patched version throws its victim **7,777,840 blocks** away, the victim cannot die (half a heart), has to cover the distance, and "now this can actually kill me" only for the **last ~25,000 blocks** | f7ce434e |
| Severing a contract frees the bearer and costs them the left arm (this version generalises it, see section 4: every break costs the next body part) | f7ce434e |
| Dusk's Epitaph (transcribed "dusk epot") is a weapon that permanently bans people; a very old, rusty gift | df7b0692, d2c25a8d |

## 3. Architecture

```
net.charter
  CharterMod                 entry point, registration order
  config/CharterConfig       every number, JSON file config/charter.json
  state/CharterState         saved data: contracts, lost body parts, exiled, judged, pending exile
  state/ContractRecord       one contract: creator, signer, ids
  body/*                     BodyPart (the order parts are lost in), BodyMirror (who lost what, readable from both
                             sides), BodyShape (hitbox / eye height / render offset), Bodies (taking and giving back
                             parts, speed, jumping, hands that cannot be used)
  enchantment/CursedEnchantment, registry/ModEnchantments
  registry/Mod*              items, block, block entity, entity type, creative tab, damage types
  item/*                     ContractItem, LesserDivinityItem, DuskEpitaphItem, DivineDominanceItem
  block/*                    DivineDominanceBlock (+ BlockEntity: the cage logic)
  fx/CharterFxEntity, Fx     one invisible entity + server helpers that spawn the visuals
  logic/*                    Judgement, Banisher, ExileManager, Contracts, Curses, Confirm, Detonation, Targeting,
                             CharterRuntime
  command/CharterCommand     /charter ...
  net/CharterNetworking      the one packet: which body parts every player has lost
net.charter.client
  CharterClient              renderer registration, packet receiver
  CharterFxRenderer          draws sigil, beam, chains and cage
  mixin/*                    hide lost parts (player model, armour layer, held items, first person hand), sit a
                             limbless player on the ground, and the two-handed Epitaph pose
net.charter.mixin
  PlayerEntityMixin          the hitbox and eye height of a player who is only a torso or a head (common)
```

Design choices worth knowing:

* **Almost no gameplay mixins.** Everything that changes rules uses Fabric API events (attack, use, block break,
  damage, death, join, respawn, tick). The one common mixin only changes a player's hitbox and eye height when a
  body is down to a torso or a head; the others are cosmetic and client-side. All mixin configs are
  `required: false`, so a mixin that fails to apply costs a feature, never the game.
* **One effect entity (`charter:fx`).** The server sets a few tracked values (kind, start time, lifetime, size...)
  and the client derives the whole animation from the world time, so nothing is streamed every tick. Kinds: sigil,
  beam, chains, cage. It is never saved and is drawn with the fullbright translucent layer.
* **Saved data** lives in the overworld's `data/charter_state.dat`, so it follows the world, not the config.
* **Bans** are real: the player is added to the vanilla ban list *and* to Charter's own list, which is enforced on
  join. `/charter pardon` removes both.

## 4. Mechanics

### Contracts (`ContractItem`, `Contracts`, `CharterState`)
A contract is a record in the saved data (creator, signer); the items are only copies of it.
* Blank contract used on a **crouching** player: the user is the creator, the target the signer; both get a signed
  copy. **[from the description]** (the crouch as consent is a **[guess]**)
* Blank contract used in the air while crouching, twice: the user signs with Charter itself (no creator).
  **[guess]**, so that the contract still has a use when nobody else is around.
* The signer cannot harm the creator: damage, the Epitaph, sigils and the Dominance wall all check it.
  **[from the description]**
* The creator takes the signer's arms: signed contract in the main hand, a sword in the off hand, strike the signer
  (left arm first, then right; 30 seconds between blows). **[from the description]**
* Breaking (signer crouches and uses the copy, twice) costs the next part: left arm, right arm, left leg, right
  leg, body. Contracts can be signed and broken again and again. **[from the description]**; the order and the
  confirmation are **[guess]**
* The creator can release the signer at no price. **[guess]**
* Lost parts (`Bodies`): a lost arm locks that hand (use, attack, break, place) and empties the off-hand slot; one
  lost leg: -35% speed; no legs: -80% speed, no jumping (a hidden jump boost with a negative amplifier), a 0.6x1.3
  hitbox and the model drawn lower; head only: 0.5x0.5 hitbox, no ground speed (it hops). **[from the description]**
  for "a head that can only jump around", numbers are **[guess]**

### The Cursed enchantment (`CursedEnchantment`, `Curses`)
* Only Charter weapons can carry it (`CharterWeapon` marker), and only a contract can put it on: the creator holds
  the signed contract in the main hand and the weapon in the off hand and uses it (not crouching).
* The weapon is tagged with the contract and the signer and delivered to the signer's inventory when they are near
  (otherwise it is dropped for them: `ItemEntity` owner = signer, never despawns).
* Anyone else holding it loses it every half second (inventory and cursor stack are scanned). The curse is lifted
  when the contract ends (online inventories and loaded item entities at once, anything else the next time somebody
  holds it).

### Lesser Divinity (`LesserDivinityItem`, `Judgement`, `Detonation`)
* Aim up to 96 blocks at a creature (its feet) or the block hit. A sigil of radius 5 is drawn; after 80 ticks a
  beam strikes and everyone inside the sigil, except the wielder, receives the verdict. **[seen]** sigil/beam,
  **[guess]** numbers.
* Three charges, shown on the item bar. The third destroys the item and calls `Detonation.detonate`: a blast that
  never breaks blocks, a hand-carved crater (radius 10, depth 5) and suspicious sand/gravel scattered over it that
  uses the loot table `charter:archaeology/lesser_divinity_crater` (mostly fragments). **[seen]**
* Eight fragments around a signed contract make the Fractured Lesser Divinity. **[guess]** recipe.

### Fractured Lesser Divinity
* Same sigil, verdict **exile**, unlimited uses with a 3 second cooldown. **[seen]**
* `ExileManager.exile`: remembers where the player stood, picks a random direction and teleports them
  `exileDistance` blocks (7,777,840) from the world spawn onto the surface, loading that chunk first.
* While exiled: lethal damage is cancelled and health set to `exileMinHealth` (half a heart) **unless** the player
  is within `exileLethalRadius` (25,000) of their origin; blocks cannot be broken, placed or used; the exile ends
  within `exileReturnRadius` (1,000) of the origin, or when the player really dies. **[seen]**, tail radii **[guess]**.

### Dusk's Epitaph (`DuskEpitaphItem`, `CharterRuntime.onAttackEntity`)
* A sword with damage 6 + 5 and attack speed 1.0/s that never breaks. Against non-players it is a heavy sword.
* A blow on a player with attack charge of at least 85% cancels the damage and calls
  `Judgement.bindAndJudge`: the target is held in chains (40 ticks), cannot be hurt or act, then the beam falls and
  they are banned. **[seen]** ban, **[guess]** chain telegraph.

### Divine Dominance (`DivineDominanceBlock`, `DivineDominanceBlockEntity`)
* Placing the tablet fixes the geometry from the config: a square of half width 24 that stays still for 100 ticks
  then shrinks to half width 1 over 2400 ticks, reaching 48 blocks up and 24 down. **[wiki]**
* Players inside cannot leave, players outside cannot enter (they are put back on their side); the owner, the bound
  and spectators ignore it. At the end everyone still inside is judged (`dominanceVerdict`). Squeezed players take
  crush damage in the last few blocks. The block is nearly indestructible, breakable only by owner, bound players
  and operators. **[wiki]** concept, **[guess]** details.

### Judgement sequence
Nothing is instant. A verdict is always: telegraph (sigil or chains, sounds, particles) -> beam and thunder ->
verdict. While chained, the target is protected from all damage, cannot attack, use items or interact, and is
held in place. Verdicts: `BAN`, `EXILE`, `KICK`, `KILL`. A ban that is switched off in the config, or aimed at an
exempt player (the host of a local world), becomes the configured fallback (`EXILE` or `KICK`).

## 5. Assets and data

* Item and block art was drawn procedurally (pixel-art for items, procedural patterns for effects) and checked as
  images. The scripts are not part of the project; the PNGs are final.
* Effect textures: `sigil.png` (256x256, rotating rune circle), `beam.png` (64x256, repeats every 24 blocks),
  `chain.png` (96x16, repeats every 1.3 blocks), `cage.png` (128x128, repeats every 4 blocks). They are white with
  alpha; the renderer tints them gold.
* Data: two damage types (`charter:judgement`, `charter:crush`) with the tags that make the first unblockable,
  two recipes, two loot tables (the tablet, the crater).
* Language: `assets/charter/lang/en_us.json`. Chat messages from the server are plain English literals.

## 6. In-game test checklist

Nothing here could be run while writing the mod, so it is worth a quick pass after the first build. Give yourself
operator rights and create a creative world with Fabric API installed.

1. **Start-up.** The game reaches the title screen; the log says "Charter has been signed". The creative tab
   *Charter* shows six items with their textures; hover each to read the tooltips.
2. **Contract.** With two players: `/give @s charter:charter_contract`; use it on the other player while they
   crouch: both get a signed contract. The signer cannot hit the creator. Hold the signed contract in the main hand
   and a sword in the off hand and strike the signer: their left arm disappears (check F5), and they cannot use their
   off hand. Alone: crouch and use a blank contract twice to sign with Charter, then crouch and use the signed copy
   twice to break it: it costs your left arm, then your right arm, and so on until only your head is left.
   `/charter body restore @s` brings everything back.
   **Cursed:** the creator holds the signed contract (main hand) and a Charter weapon (off hand) and uses it: the
   weapon goes to the signer, and nobody else can keep it.
3. **Sigil without a ban.** `/charter sigil ~ ~ ~ kick`: a golden sigil, a beam, and any other player standing in it
   is disconnected. (Aim it at a second account.)
4. **Lesser Divinity.** `/give @s charter:lesser_divinity`, aim at the ground: sigil -> beam. Use it three times:
   the third destroys it and digs a crater. Brush the suspicious blocks with a brush to find fragments.
   *Testing alone in singleplayer:* the host cannot be banned, so a ban aimed at you becomes an exile.
5. **Exile.** `/charter exile <name>`: they are teleported millions of blocks away and can look but not touch.
   `/charter release <name> home` brings them back.
6. **Epitaph.** `/give @s charter:dusk_epitaph`; on a second player, a full-strength swing chains them, then
   bans them. `/charter pardon <name>` lets them back in.
7. **Divine Dominance.** To see it quickly set `dominanceArmTicks` and `dominanceShrinkTicks` low in
   `config/charter.json`, `/charter reload`, place the tablet and watch the cage close. Right-click the tablet to
   read the time left.

## 7. Extending it

* New verdicts go in `Verdict` and `Judgement.deliver`.
* All numbers live in `CharterConfig`; add a field and it appears in the JSON.
* The public 1.19.2 Charter features (claims, vessels) would be new packages next to `logic`; the saved data class
  already has the place for them.
