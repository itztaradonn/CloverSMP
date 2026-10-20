# Clover Blessing System: Concept Design

Oct 20, 2026 · @Luz

## 1. Concept Summary

Clover Blessing is a build-crafting system: each player carries a four-leaf clover whose leaves hold blessings they made themselves, and the clover grows as they fight. Power comes from the combination you choose, not from owning one rare item.

The clover is the whole identity. It is the progress bar, the loadout screen and the visual language (particles, sounds, UI) at once. The four leaves sit in a cross, so opposite leaves can interact, and each leaf blooms as Clover Charge rises.

What separates it from a legendary-weapon plugin:

- Power is spread across four slots the player controls, so two players with the same leaf count can fight very differently.
- Blessings are crafted and reconfigurable, not unique drops that one player owns.
- Every strong blessing carries a condition, cooldown or cost that an opponent can read and play around.

Design target: a survival or PvP server where a player can describe their build in one sentence ("a vine-trap archer who heals when it is quiet") and opponents can guess most of it by watching.

## 2. Core Gameplay Loop

A player moves from one weak leaf to a tuned four-leaf build in four stages, and each stage changes what they are trying to do. Target pacing: leaf 2 in the first session, leaf 3 within a few sessions, leaf 4 after roughly a week of regular play.

| Stage | Leaves | What the player does | What they learn |
| --- | --- | --- | --- |
| Seedling | 1 | Fights mobs, finds first recipes, presses a starter blessing | How to read a blessing's rule text and how charge feels |
| Sprout | 2 | First PvP skirmishes, gathers plants and mob drops for better cores | That blessings pair up; first loadout change |
| Bloom | 3 | Raids structures for rare cores, adds a first Graft | Opposite-leaf interaction and basic counter-building |
| Full Clover | 4 | Tunes loadouts per opponent, trades blessings, hunts rare mechanics | Scouting enemies and adapting mid-session |

The repeating loop inside every stage:

1. Fight mobs or players and earn Clover Charge.
2. Cross a threshold and a new leaf opens.
3. Gather materials and press a blessing at the Pressing Table, previewing its rules before spending anything.
4. Equip it in a leaf once out of combat.
5. Test it in real fights and note what worked and what got punished.
6. Adjust: swap, add a Graft, trade, or hunt the next rare core.

## 3. Clover Progression

The recommended rule is a persistent charge meter where the first two leaves are permanent and the last two can wilt. All numbers below are starting values to tune in playtests, not final.

### Where charge comes from

| Source | Charge | Notes |
| --- | --- | --- |
| Hostile mob | 2 | After 200 mob charge in a day, further mob kills give 25% |
| Elite or mini-boss | 10 | Counts toward the daily mob soft cap |
| Boss (Dragon, Wither, Warden) | 60 | Once per boss type per player per day, not capped |
| Player kill | 40 | Multiplied by the victim's leaves: x0.5 for 1 leaf, x1 for 2-3, x1.5 for 4 |
| Assist | Share of the kill | Only if the assister dealt at least 25% of the damage in the last 20 seconds |

Player kills are worth 20 mob kills because PvP is the core, but mobs and bosses alone can still reach leaf 3 and beyond, so PvE players are not locked out. PvE-only servers can switch the PvP weighting off.

### Abuse rules that belong in the core design

- Repeat victim: 100%, 50%, 25%, then 0% for the same victim inside a rolling hour. This also stops two friends trading kills.
- Kill stealing: charge is split by damage share, so a last-hit finisher with under 25% of the damage gets nothing.
- Fresh victims: a kill gives nothing if the victim respawned under 90 seconds ago or never dealt or took damage.
- Alts and teammates: victims with under 2 hours of playtime, or in the killer's party or clan, give 0.

### Thresholds and death

The meter runs 0 to 1000. Leaf 2 opens at 100 (about 3 player kills), leaf 3 at 350, leaf 4 at 800. Dying costs 35% of the meter, and it never drops below 100 once leaf 2 has been reached. A fully charged player who dies falls from 1000 to 650: they keep leaf 3 and must win back leaf 4. A wilted leaf pauses its blessing without unequipping it.

Charge above 1000 turns into Pollen, a crafting currency for Grafts. This gives overflow kills a use without letting the meter run past four leaves.

### Alternatives compared

| Model | How it works | Gameplay consequence | Main risk |
| --- | --- | --- | --- |
| A. Permanent ladder | Lifetime charge; leaves unlock forever | Zero frustration, but everyone ends at four leaves and kills lose meaning | Veterans permanently ahead; no reason to fight after leaf 4 |
| B. Per-life ladder | Meter resets on death | Strong snowball tension and a level start every life | Brutal for new players; farming becomes the whole game |
| C. Roots and blooms (recommended) | Leaves 1-2 permanent; leaves 3-4 depend on the meter | Leaf 4 is a status you defend, and 4-leaf players are valuable targets | Needs tuning so one death is a setback, not a spiral |

Why C: it keeps new players competent from leaf 2, gives veterans a reason to keep fighting, and builds a natural catch-up loop because the leader is the biggest prize. Server owners should be able to set the death penalty, the floor and the thresholds, but the roots-and-blooms split should stay a core rule.

## 4. Blessing System

A blessing is one readable rule, not a stat block: a trigger, an effect, a cooldown and a weakness, all stated in about 25 words. Most blessings change combat rules (conditions, marks, zones, phases) rather than apply plain potion effects, so a player can learn them by watching one fight.

Each blessing has a category and a few hidden tags used for balance (Heal, Speed, Damage Boost, Control). The four leaves sit in a cross, so every leaf has exactly one opposite leaf (1 and 3, 2 and 4). Only a few blessings use that, which keeps slot order meaningful without making every loadout a puzzle.

### Example blessings (16 across all 8 categories)

| Blessing | Category | Effect | Works well with | Weakness and counterplay |
| --- | --- | --- | --- | --- |
| Thornlash | Offense | The third hit on the same target within 4s deals +25% damage | Hobble, Stinger | Chain breaks on target switches; retreating or shielding resets it |
| Stinger | Offense | Arrow hits mark a target for 5s; your melee hits on a marked target deal +15% | Tangle Seed, Thornlash | Needs a bow and a melee weapon; breaking line of sight loses the mark |
| Mossguard | Defense | Hits under 4 damage are halved | Quiet Dew, Lanternleaf | Does nothing against crits, strength or burst weapons |
| Last Leaf | Defense | Once per 180s a fatal hit leaves you at 2 hearts, rooted for 1.5s | Springleaf, Wager Root | The root is a free finishing window; opponents who saw it fire know the cooldown |
| Springleaf | Mobility | Hold sneak 1s, release to leap about 6 blocks; 12s cooldown | Stinger, Waystem | Charge cue is visible and cancels on damage; misplaced leaps cause fall damage |
| Fieldwalker | Mobility | +15% speed on natural blocks (grass, dirt, leaves, flowers) | Springleaf, Waystem | No bonus on stone, wood or in the Nether; fight on built ground |
| Quiet Dew | Recovery | After 6s without dealing or taking damage, heal 1 heart every 2s | Mossguard, Lanternleaf | No in-combat value; constant poke damage turns it off |
| Leechvine | Recovery | Heal 10% of melee damage dealt, capped at 3 hearts per 10s | Thornlash, Glass Bloom | Ranged kiting and burst beat it; anti-heal effects shut it down |
| Tangle Seed | Crowd control | Arrow impact makes a 2-block vine zone for 3s (Slowness II); one zone at a time | Stinger, Echo Leaf | Zone is visible and avoidable; stepping out clears the slow fast |
| Hobble | Crowd control | A critical hit stops the target sprinting for 1s; 6s cooldown | Thornlash, Leechvine | Needs crit timing and melee range; shields block it |
| Waystem | Utility | Plant a marker, hold use 3s to return to it; 5-minute cooldown, one marker | Fieldwalker, Springleaf | Channel cancels on damage, and the marker can be broken |
| Lanternleaf | Utility | Party members within 10 blocks get night vision; you glow faintly to them | Mossguard, Quiet Dew | Only helps parties; the glow also marks you at night |
| Glass Bloom | Risk and reward | +25% damage dealt, max health reduced by 3 hearts | Leechvine, Thornlash | Dies fast to burst and ambush |
| Wager Root | Risk and reward | Kills give +50% charge; deaths cost double charge | Glass Bloom, Last Leaf | Makes you a priority target; one bad streak wipes progress |
| Echo Leaf | Experimental | Copies the blessing in the opposite leaf at 50% strength | Any blessing flagged echoable | Dead slot if the opposite leaf is empty; only one Echo can be equipped |
| Turning Year | Experimental | Every 30s cycles a small buff: spring regen, summer damage, autumn speed, winter resistance | Springleaf, Thornlash | Rhythm is predictable; opponents attack in your weak phase, and particle color gives the phase away |

### Combinations worth watching

- Interesting: Thornlash + Hobble + Leechvine makes a crit-chain duelist; Last Leaf + Springleaf makes an emergency escape; Tangle Seed + Echo Leaf paints a wide control field.
- Likely too strong: Leechvine + Glass Bloom + Thornlash + Hobble stacks damage, healing and control in one kit. Fieldwalker + Springleaf + Waystem would dominate movement on natural terrain.
- Needs a flag: Echo Leaf must not copy Last Leaf or Waystem, so those carry a non-echoable tag.

Section 7 covers how the stacking rules prevent these from becoming the default.

## 5. Crafting Design

The recommended model is a Core plus an optional Graft: one standalone rule, tuned by one sidegrade. It gives players two clear decisions (which core, which graft) instead of a recipe maze.

### Approaches compared

| Approach | Strength | Weakness | Complexity | Balance risk |
| --- | --- | --- | --- | --- |
| Standalone blessings | Easy to read and test | Becomes a shopping list with little to discover | Low | Low |
| Multi-effect blessings | Rich, memorable identity | Hidden bundles hide which part matters; one bundle tends to dominate | Medium | High |
| Base plus modifiers (fully modular) | Huge variety, real experimentation | Combinatorial explosion that cannot be fully tested | High | High |
| Trigger recipes | Creates distinct playstyles (on kill, on block) | Hard to communicate; risk of looping triggers | Medium-high | Medium-high |
| Rare-mechanic recipes | Strong identity and moments of surprise | Each one needs handwritten rules and testing | Medium | Medium |

### Recommended: Core + Graft

- **Cores:** about 20 at launch, each a single standalone rule (the 16 above plus a few more). Pressed at a Pressing Table from a Clover Seed plus up to 3 ingredients.
- **Grafts:** one optional sidegrade per blessing. Quick (-25% cooldown, -15% strength), Deep (+25% strength, +35% cooldown), Hunter (only after you landed a hit in the last 3s, +15% strength), Vengeful (only after you took damage in the last 3s, +15% strength), Ward (a party member within 6 blocks gets half the effect).
- **Rare cores:** Echo Leaf, Turning Year and similar mechanics come from recipes found in structures and boss rewards. They are different, not stronger.
- **Permanence:** a graft is fixed when pressed. Pruning removes it and returns half the materials, which is also an economy sink.

### Keeping experimentation cheap and simple

- **Preview before spending:** the table shows the final rule text before it consumes anything, so no one wastes materials on a blind guess.
- **Field Guide:** each first press records the recipe. Undiscovered recipes show silhouettes with a category and an ingredient family (for example, a plant and a bone item), so discovery is a hint, not a riddle.
- **Cheap cores, costly grafts:** cores use farmable materials (plants, mob drops, common ores). Grafts need Pollen from overflow charge plus one contested material, so power tuning costs fights.
- **Hard cap on stacking:** total cooldown reduction from grafts is capped at 30% across a loadout.

Full modularity is rejected because the test burden grows with every module, while a two-axis system can be playtested one core at a time. Trigger-style recipes survive as the Hunter and Vengeful grafts, which keeps their appeal without separate recipe trees.

## 6. Build Examples

Six builds cover offense, defense, mobility, control, risk and a rhythm-based oddity, and each has a clear answer elsewhere in the list. Blessings are listed in leaf order (leaf 1 to leaf 4), so leaf 1 sits opposite leaf 3 and leaf 2 opposite leaf 4.

| Build | Leaves 1 to 4 | Synergy | Weaknesses |
| --- | --- | --- | --- |
| Bramble Duelist (melee offense) | Thornlash, Leechvine, Hobble, Glass Bloom | Hobble's crit cuts off retreat, Thornlash punishes the third hit, Leechvine turns that damage into healing, Glass Bloom raises the numbers | Only 7 hearts; loses to ranged kiting, burst and anti-heal; if leaf 4 wilts, damage drops |
| Moss Bulwark (party anchor) | Mossguard, Quiet Dew, Last Leaf, Lanternleaf | Chip damage is halved, healing returns between skirmishes, one emergency save, allies see in the dark | Low kill pressure; crits and burst ignore Mossguard; constant poke keeps Quiet Dew off |
| Meadow Runner (raider) | Fieldwalker, Springleaf, Waystem, Stinger | Fast on natural ground, leaps to engage or escape, recalls home, marks for follow-up | Slow on stone and in the Nether; no sustain; Stinger needs both bow and melee |
| Snare Archer (control) | Tangle Seed, Stinger, Echo Leaf, Hobble | Echo (opposite Tangle Seed) adds a second, weaker vine zone; marked, slowed targets are easy to hit and stop | Needs line of sight; weak at close range once Hobble is on cooldown; zones are avoidable |
| Wager Climber (risk) | Wager Root, Last Leaf, Glass Bloom, Turning Year | Kills pay +50% charge, Last Leaf softens the first mistake, Glass Bloom adds damage in summer | Becomes the priority target; fragile; deaths cost double charge |
| Season Dancer (rhythm) | Turning Year, Thornlash, Springleaf, Hobble | Bursts in the summer phase, leaps in with Springleaf, uses Hobble to keep the chain on target | Predictable phases; weak in winter; Thornlash chain breaks easily |

How they check each other: Runners outpace Bulwarks but lose to Archers in open fields; Archers lose to Duelists who close the gap; Duelists struggle against Bulwarks whose chip reduction and heals blunt Thornlash; Climbers and Dancers are strong but exploitable once their patterns are read.

## 7. Balance Review

The biggest risks are stacked tags (healing, speed, damage) and progression abuse, and both need rules built into the design rather than left to server settings. The last column separates what must be a core rule from what a server owner can tune.

| Problem | Why it happens | Countermeasure | Level |
| --- | --- | --- | --- |
| Overpowered four-blessing combos | Damage, healing and control stack in one kit (for example Leechvine, Glass Bloom, Thornlash, Hobble) | Tag fatigue: each tag (Heal, Speed, Damage Boost, Control) applies fully once; a second source counts at 50%, a third at 25% | Core rule; percentages configurable |
| Infinite healing and survivability | Leechvine, Quiet Dew and Last Leaf cover every situation | Sap Cap: all blessing healing combined is capped at 6 hearts per 10s, and Last Leaf cannot be copied by Echo Leaf | Core rule; numbers configurable |
| Permanent movement advantages | Speed on natural ground, leaps and recall together | No blessing speed above Speed II equivalent; speed sources do not stack; every mobility blessing has a terrain, charge or cooldown limit | Core rule; values configurable |
| Kill farming and alt abuse | Repeat kills, friends trading kills, throwaway accounts | Per-victim diminishing returns, 90-second fresh-victim rule, damage-share splitting (section 3); victim playtime and IP or party links as extra checks | Diminishing returns core; playtime, IP and party checks configurable |
| Cooldown manipulation | Quick Grafts stacking, relogging, dying to reset | Cooldowns persist through logout and death; total cooldown reduction capped at 30%; a swapped blessing has a 30s lock | Core rule |
| Duplication and item exploits | Pressed blessings exist as items that chests, hoppers and glitches can copy | Each blessing is a unique server-side record and the item is only a claim on it; blessings never stack; duplicates are voided when found; every press, trade and prune is logged | Core rule |
| Established-player advantage | Veterans own more blessings and keep leaves longer | Four leaves for everyone, no stat scaling by leaf, grafts are sidegrades, and 4-leaf players are the most valuable kills | Core rule |
| Recipes too easy or too expensive | Material availability differs per server | Farmable materials for cores, contested materials for grafts, pruning loses half the cost, and a metric to watch: time to first graft | Server configuration |
| One blessing makes others useless | A blanket defense or heal removes the need for every other choice | No blessing cuts damage from any single source by more than 50%, and each defense answers one condition only (chip, lethal hit, out-of-combat time) | Core rule |

### Dominance watch

Even with these rules, a pick-rate and win-rate summary per blessing is needed after launch. A blessing picked by most players, or one that never appears, is the signal to adjust a number before the next season.

Counterplay principle for every future blessing: an opponent must be able to see it working, name it, and beat it with a different approach.

## 8. Alternative Concepts

Three directions differ in where the fun comes from: the workshop, the world, or the fight. The recommendation is Direction A, with two borrowed pieces from the others.

|  | A. Pressed Blessings (crafting-led) | B. Wild Herbarium (discovery-led) | C. Combat Garden (fight-led) |
| --- | --- | --- | --- |
| Main loop | Gather materials, press a blessing, test, refine | Explore, find wild blessings, bring them back, specialize | Equip, complete a trial in combat, choose a mutation |
| Distinctive mechanic | Core plus Graft pressing with previews | Wild blessings found at bloom sites in the world; each has a random quirk | Trials that unlock mutation branches for a blessing |
| How players obtain and customize | Craft cores from recipes, add one graft, swap loadouts out of combat | Mostly found, not crafted; customize by choosing which finds to keep | Base blessings from simple recipes, then evolve them by playing |
| Advantages | Clear progress, strong economy, easy to explain, controllable testing | Exploration and stories, scarcity makes finds feel special | Rewards skill and style, no crafting economy needed |
| Disadvantages | Needs a curated recipe list to avoid a maze | Luck-driven, hard for new players, rich-get-richer | Trial farming, grindy objectives, more tracking |
| Balance and development complexity | Medium balance, medium development | High balance (quirk rolls), high content cost | High balance (branches), high development |
| Best for | Long-running survival and SMP servers with a player economy | Adventure and lore servers with seasonal resets | Competitive PvP servers with short seasons |

### Recommendation

Direction A fits the brief best. The stated goal is that collecting, crafting and combining blessings is the main attraction, and A is the only direction where that is the core loop. It also scales: adding a new core is one handwritten rule and one recipe, and an economy gives long-term players something to trade and optimize.

A is not automatically right. If the server is mostly short-season PvP with no player economy, C would suit it better, because pressing and trading would matter less than skilled use. If it is an adventure server, B's discovery fits more naturally.

Two pieces are worth borrowing. From B: rare cores found in structures (already in section 5), so exploration has a reason. From C: optional mutations as a post-launch expansion, once the base meta is stable and tracked.

## 9. Open Design Decisions

Twelve questions need your answer before the concept can be finalized, and the first three change the most. The lean column shows where the current proposal points, not a settled choice.

| # | Question | Options | Why it matters | Current lean |
| --- | --- | --- | --- | --- |
| 1 | What kind of server is this for? | PvP-first, mixed survival, PvE-focused | Sets charge weights, death penalty and which direction in section 8 fits | Mixed survival with optional PvP |
| 2 | Should leaf 4 require PvP or bosses? | PvP or bosses only; any source; PvE-capped | Decides whether PvE-only players can reach full power | Leaf 4 needs PvP or bosses |
| 3 | What does death cost? | Flat percentage, full reset, nothing | Sets how risky each fight feels | 35% with a floor at leaf 2 |
| 4 | Where can loadouts change? | Anywhere out of combat, only at altars, only at home | Anywhere means more experimenting; altars mean more commitment | Anywhere after 30s out of combat, with a swap lock |
| 5 | Are blessings tradable? | Free trade, bound on press, bound after first use | Drives the economy and the dupe-exploit surface | Cores tradable, grafted blessings bound |
| 6 | Can blessings drop on death? | Never, loose ones only, equipped ones too | Dropping brings back legendary-weapon stakes, which this concept avoids | Equipped ones never drop |
| 7 | Are grafts permanent? | Permanent, removable with a cost, free swaps | Free swaps remove the commitment that makes grafts meaningful | Prune with a 50% refund |
| 8 | How many cores at launch? | 12, 20, 30 | More cores means more testing and more chances of a dominant pick | About 20 |
| 9 | What can opponents see? | Leaf count only, category icons, full loadout on inspect | More information means more counterplay but less surprise | Leaf count and category icons only |
| 10 | Do parties and clans share charge? | No sharing, shared assists, full sharing | Sharing speeds teams up but invites boosting | Assist sharing within a party only |
| 11 | Do seasons reset the meter? | Never, per season, per wipe | Resets keep new players relevant but erase effort | Never reset the meter; reset leaderboards |
| 12 | What gear baseline do blessings assume? | Vanilla netherite, capped enchants, custom gear | Percent-based effects scale very differently on different gear | Vanilla gear with enchant limits |

Two smaller questions follow from these: whether Bedrock players need an alternative to the sneak-hold input used by Springleaf, and whether other plugins on the server (combat tags, custom enchants) can interfere with blessing triggers.

## 10. Proposed Design Document

This is the preliminary specification for Direction A (Pressed Blessings), written so decisions in section 9 can be swapped in without rewriting it. Numbers are starting values for playtesting.

### Pillars

- Expression: players build their own kit; no fixed classes.
- Readable counterplay: every blessing can be seen, named and beaten.
- Earned power: leaves come from fighting, blessings come from crafting.
- Original identity: the four-leaf clover drives progression, UI and visuals.

### Clover and leaves

Every player has a Clover with four leaves in a cross. Leaf 1 is open from the start, leaf 2 at 100 charge, leaf 3 at 350, leaf 4 at 800. Leaves 1 and 2 are permanent; leaves 3 and 4 wilt when the meter falls below their threshold and pause their blessing. Opposite leaves (1 and 3, 2 and 4) are the only positional rule.

### Clover Charge

Meter 0 to 1000, kept through logout. Sources and abuse rules are in section 3. Death costs 35%, never below 100 once leaf 2 is reached. Overflow turns into Pollen.

### Blessings

Each blessing is a Core (one rule, about 25 words, with a category, hidden tags, a cooldown and a stated weakness) plus an optional Graft. Launch scope is about 20 cores (3 of them rare, found in structures and boss rewards) and 5 grafts.

### Crafting

Cores are pressed at a Pressing Table from a Clover Seed and up to 3 ingredients, with a rule preview before anything is consumed. The Field Guide records discovered recipes and shows silhouettes for the rest. Grafts need Pollen plus a contested material; pruning refunds half.

### Ownership and storage

A blessing is a unique server-side record, and the item is only a claim on it. Unequipped blessings live in the Herbarium, a personal book-style storage, or in inventory. Equipped blessings never drop on death. Cores are tradable; grafted blessings bind to their owner.

### Loadout rules

Loadouts change after 30 seconds out of combat, anywhere. A newly equipped blessing starts with a 30-second lock. Cooldowns persist through death and logout.

### Feedback and UI

- One action bar line shows the four leaves as icons whose fill shows cooldown; wilted leaves look greyed.
- A short flash and sound mark each trigger; nothing else stays on screen.
- Opponents see a ring of leaf particles showing leaf count, and can inspect category icons only, never exact blessings.
- Charge gains show as a brief pulse; the full meter lives in the Clover menu.

### Fixed rules versus configuration

- Fixed: roots and blooms, unique blessing records, tag fatigue, Sap Cap, 50% single-source damage reduction limit, equipped blessings never drop.
- Configurable: thresholds, charge weights, death percentage and floor, tag fatigue percentages, recipe costs, loadout lock times, party rules, per-blessing enable or disable.

### Success checks for early playtests

- No blessing appears in more than 40% of four-leaf loadouts.
- No build wins more than 55% against the field.
- At least 6 distinct builds appear among the weekly top 20 players.
- A new player reaches leaf 2 in their first session.

This stops at the concept stage, as requested: no code, commands, configuration files or implementation.
