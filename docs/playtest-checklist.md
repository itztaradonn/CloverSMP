# Milestone 1 playtest checklist

Server: Paper 1.21.11, Java 21. Drop `build/libs/CloverBlessing-1.0.0-SNAPSHOT.jar` into `plugins/`.
For testing with fresh accounts, set `anti-abuse.min-victim-playtime-hours: 0` (otherwise new victims give no PvP charge).

Setup: `/clover admin setcharge <you> 1000`, then `/clover admin menu` (click any blessing to get it) and equip from `/clover`.
Debug: `/clover admin debug` logs every blessing check you trigger to the server console (`logs/latest.log`).

## Core
- [ ✅ ] Plugin enables; log says "20 blessings and curses"; no errors on join, quit, `/clover admin reload`, server stop
- [ ✅] Action bar shows leaves, category icons, cooldown seconds, ⚡charge
- [ ]✅ Particle ring shows leaf count; a curse adds dark ink motes
- [ ] Equip blocked within 30 s of PvP; new blessing "wakes in 30s"
- [ ] Charge 790 + mob kills stays at 799; a player kill opens leaf 4
- [ ] Death at 1000 → 650; at 120 → 100 (floor); Wager Root doubles the loss
- [ ] Cooldowns survive relog and restart (Last Leaf)
- [ ] `/clover inspect <p>` shows leaf count + category icons only, curse in full
- [ ] Party: invite / accept (click) / leave / kick / list; party kills give 0 charge

## Blessings
- [ ] Thornlash: 3rd hit within 4 s on same target hits harder (gold flash)
- [ ] Stinger: arrow marks; melee on marked target hits harder; hiding behind a wall drops the mark
- [ ] Mossguard: small hits halved; crits not
- [ ] Last Leaf: fatal hit → 2 hearts, rooted 1.5 s; second fatal hit within 180 s kills
- [ ] Springleaf: hold sneak 1 s, release → ~6-block leap; taking damage while charging cancels it
- [ ] Fieldwalker: faster on grass/dirt/leaves; not on stone or in the Nether
- [ ] Quiet Dew: heals after 6 s of no damage
- [ ] Leechvine: melee heals, at most 3 hearts per 10 s; Wither blocks it
- [ ] Tangle Seed: arrow impact makes a slowing vine zone; doesn't slow you or your party
- [ ] Hobble: crit stops target sprinting 1 s; raised shield blocks it
- [ ] Waystem: sneak + right-click block plants a marker; sneak + F channels 3 s home; moving or damage cancels; hitting the marker breaks it
- [ ] Lanternleaf: party members nearby get Night Vision and see glow particles
- [ ] Glass Bloom: −3 hearts max health, more damage; unequip restores health cap
- [ ] Wager Root: kill charge ×1.5, death loss ×2
- [ ] Echo Leaf: copies the opposite leaf at half strength; won't copy Last Leaf or Waystem; only one Echo
- [ ] Turning Year: particle colour changes every 30 s; buffs follow the phase

## Curses (leaf 5)
- [ ] Opens with leaf 4; wilts with it
- [ ] Breaking or replacing needs shift-click and costs 15 % charge
- [ ] Bloodroot: no hunger regen; blessing heals bigger
- [ ] Thorncrown: deal more, take more from players
- [ ] Hollow Bell: you glow; cooldowns shorter (never below 70 %)
- [ ] Hungering Clover: hunger drains twice as fast; blessings stronger

## Phase 2: crafting
Setup: `/clover admin seed <you> 16`, `/clover admin scrap <you> echo_leaf`, `/clover admin setcharge <you> 1000` and kill mobs for Pollen.
- [ ] Clover Seed shows in the recipe book; Wheat Seeds + Bone Meal + Emerald crafts one
- [ ] Seeds and claims can't be planted, crafted with, or used in a cartography table
- [ ] Right-click a Fletching Table opens the Pressing Table (sneak-click places blocks as normal)
- [ ] Seed + Rose Bush + Cactus + Iron Ingot previews Thornlash; Press adds it to the herbarium and the Field Guide
- [ ] Wrong ingredients show "No recipe matches"; no seed shows the reason; Press does nothing
- [ ] Echo Leaf ingredients say "haven't learned"; after right-clicking its scrap they press
- [ ] Closing the table (Esc, logout, /stop) returns everything in the input slots
- [ ] Graft tab: grafting costs 100 Pollen + 2 materials and binds; pruning refunds 50 Pollen + 1 material
- [ ] Herbarium right-click takes an unbound blessing out as an item; another player right-clicks it to redeem
- [ ] A duplicated claim (creative middle-click) is void on use and logged in `plugins/CloverBlessing/ledger.log`
- [ ] Field Guide shows discovered recipes in full, others as ??? with hints
- [ ] Every Curse recipe needs a Wither Rose
- [ ] A killed Ender Dragon drops a recipe scrap

## Clover icon (resource pack)
- [ ] On join the server offers the pack; after accepting, the action bar starts with the green clover
- [ ] The clover shows 1, 2, 3 or 4 leaves to match your open leaves (`/clover admin setcharge` 0 / 100 / 350 / 800)
- [ ] The action bar is just the clover and ⚡charge (cooldowns are in /clover)
- [ ] With a curse equipped (leaf 5 open) it switches to the red/black clover
- [ ] The /clover menu title shows the small clover icon

## Effects (VFX/SFX)
Sounds are placeholders until your ElevenLabs files are in `art/sfx/` (see `docs/sfx-prompts.md`; then `python -I tools/build_sounds.py` and `./gradlew resourcePack`).
- [ ] Each blessing has its own sound and particles when it fires (no more shared chime); nearby players see and hear it
- [ ] Echo Leaf plays the copied blessing's sound lower, with a sculk pop
- [ ] Opening a leaf: leaf burst + chime. Dying and losing a leaf: brown dust + crunch
- [ ] Taking a curse: soul burst; breaking one: smoke + shatter. Hollow Bell tolls every 30 s
- [ ] Press / graft / prune each have a sound; a kill plays the charge blip
- [ ] Clover ring at your feet: one green 3-dot cluster per open leaf (left/top/right/bottom), a grey-brown dot for a wilted leaf; with an active curse the clusters turn dark with red edges and a blood-red/black swirl rises round the body, with a rare soul/smoke wisp and a per-curse tell (slime, crit, drip, bell ring)
- [ ] A quiet private ding when a blessing comes off cooldown

## Balance stats
- [ ] `/clover admin stats` opens a menu: pick % and kill share % per blessing, with counts
- [ ] After 10 min with a 4-leaf player online, loadouts sampled goes up; a PvP kill by a 4-leaf player adds a kill
- [ ] Over 40% pick or 55% kill share (with 20+ samples) shows red with a glint; `/clover admin stats reset` clears it
