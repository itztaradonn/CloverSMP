# Milestone 1: Playable Core — Design

Source of truth for gameplay: `CLAUDEOLDSESSIONPROMPTMD/Clover Blessing System Concept Design.md` (the concept doc).
This spec records what milestone 1 builds, the decisions made with the owner, and the interpretations
the concept doc leaves open. Items marked **(proposal)** were not discussed and are open to change.

## Scope

In: the plugin compiles and loads on Paper 1.21.11 (Java 21); `/clover` works; charge unlocks leaves;
all 16 blessings work; leaf 5 curse pacts with 4 curses; `/clover party`; known bugs fixed.

Out (later milestones): Pressing Table, Field Guide, recipes, Pollen-priced grafting, pruning, trading,
dominance metrics. Until then blessings and curses come from `/clover admin give`.

## Decisions (owner)

| Topic | Decision |
| --- | --- |
| Server | Paper 1.21.11, Java 21 |
| Activation | Mix of passive and active blessings; Java-only inputs |
| Open questions (concept §9) | Adopt every "current lean"; configurable ones go in `config.yml` |
| Engine | One handler class per blessing/curse behind one dispatcher |
| Parties | Own `/clover party` system |
| Elite mobs | Config list of tough vanilla mob types |
| Leaf 5 | Curse pact slot, opens and wilts with leaf 4 (800 charge) |
| Curse reward | Each curse is one rule with its own downside and its own perk |
| Curse visibility | Fully visible: dark fifth leaf in the particle ring; `/clover inspect` shows its name and downside |
| Curse removal | Costs something to break |
| Getting curses | Admin give for now |

## Engine

- `LeafSlot` gains `CURSE` (leaf 5). It has no opposite; Echo Leaf cannot copy it.
- `BlessingHandler` interface; every hook is a no-op default: `onMeleeHit`, `onArrowHit`, `onDamaged`,
  `onFatalDamage`, `onKill`, `onSneak`, `onSwapHand`, `onInteractBlock`, `onSecond`, plus charge
  multipliers (`chargeGainMultiplier`, `deathPenaltyMultiplier`). Hooks return `true` when the blessing
  fired, so the dispatcher starts its cooldown and plays the trigger flash and sound.
- `BlessingTriggerListener` (the dispatcher) runs the shared checks once per active slot: leaf wilted,
  cooldown, strength = graft × tag fatigue (by the tag the effect uses) × 0.5 for an Echo copy.
- Tag fatigue counts only active (non-wilted) slots, in leaf order.
- Shared caps, enforced in one place: Sap Cap (6 hearts / 10 s) for all blessing healing; 50 % max
  reduction of any single hit; speed sources do not stack and cap at Speed II (+40 %).
- Stat changes (max health, speed) are rebuilt every second from the active handlers and applied as
  attribute modifiers under the plugin namespace, so they never leak; stale ones are removed on join.
- Timing uses wall-clock milliseconds. Cooldowns are stored as expiry times on the player's Clover and
  saved to disk, so they persist through death, logout and restarts.
- The herbarium becomes `blessing id → blessing`, so a player can own several copies of a core with
  different grafts; the graft survives equipping.
- "A newly equipped blessing starts with a 30-second lock" (concept §10) means the blessing cannot fire
  for 30 s after equipping (`loadout.swap-lock-seconds`). Loadout changes themselves are not locked, so
  setting up four leaves is quick, and swapping between two copies cannot dodge a cooldown.
- Leaves must be open to equip into; a blessing can always be taken out of a wilted leaf.
- Grafts: Quick and Deep always apply; Hunter and Vengeful apply only while their 3 s condition holds;
  Ward (**proposal**) sends half of any blessing healing to the nearest party member within 6 blocks and
  does nothing for non-healing blessings yet.

## Blessings (concept §4, with implementation details)

| Blessing | Input / trigger | Implementation notes |
| --- | --- | --- |
| Thornlash | Passive, melee | Per-target hit chain within 4 s; 3rd hit +25 %, chain resets |
| Stinger | Passive, arrow then melee | Arrow mark 5 s; melee on marked target +15 %; mark drops when you lose line of sight |
| Mossguard | Passive, damaged | Incoming hit < 4 raw damage and not a critical → halved |
| Last Leaf | Passive, fatal hit | Cancels the hit, sets you to 2 hearts, roots you 1.5 s; 180 s cooldown; not echoable |
| Springleaf | **Active:** hold sneak ≥1 s on the ground, release | Leap ~6 blocks forward; charge particles visible; damage cancels the charge; 12 s cooldown; ground is checked server-side |
| Fieldwalker | Passive | +15 % speed while standing on natural blocks; never in the Nether |
| Quiet Dew | Passive | After 6 s without dealing or taking any damage, heal 1 heart every 2 s |
| Leechvine | Passive, melee | Heal 10 % of damage dealt, max 3 hearts per 10 s (and the Sap Cap) |
| Tangle Seed | Passive, arrow impact | 2-block vine zone for 3 s applying Slowness II; one zone per blessing; skips you and your party |
| Hobble | Passive, critical melee | Target cannot sprint for 1 s; blocked by a raised shield; 6 s cooldown |
| Waystem | **Active (proposal):** sneak + right-click a block with an empty hand to plant; sneak + swap-hand (F) to recall | Marker is a visible entity anyone can break by hitting it; recall is a 3 s channel cancelled by damage or moving away; 300 s cooldown (also blocks replanting); not echoable; marker saved with the player |
| Lanternleaf | Passive | Party members within 10 blocks get Night Vision; faint particles around you are shown only to them |
| Glass Bloom | Passive | +25 % damage dealt; −3 hearts max health |
| Wager Root | Passive, charge | Kill charge ×1.5; death penalty ×2 (floor still applies) |
| Echo Leaf | Passive | Runs the opposite leaf's blessing at 50 % with its own cooldown; one Echo per loadout |
| Turning Year | Passive | 30 s global phases: spring heal, summer +10 % damage, autumn +10 % speed, winter −10 % damage taken; particle colour shows phase |

Anti-heal (**proposal**): while you have the Wither effect, blessing healing does nothing.

## Curses (leaf 5) — all **(proposal)**

| Curse | Downside | Perk |
| --- | --- | --- |
| Bloodroot | No natural (hunger) regeneration | Blessing healing +50 % (still under the Sap Cap) |
| Thorncrown | Take +15 % damage from players | Deal +10 % damage |
| Hollow Bell | You glow to everyone | Blessing cooldowns −20 % (counts toward the 30 % cap) |
| Hungering Clover | Hunger drains twice as fast | All blessing strength +15 % |

Rules: equip like a blessing (out of combat). Breaking or replacing a curse costs 15 % of current charge
(`curse.break-cost-percent`, floor still applies) and needs a shift-click in the menu so it is never an
accident. Curses carry no balance tags: tag fatigue would gut Bloodroot in exactly the heal builds it is
for, and the downside already pays for the perk.

## Charge and anti-abuse changes

- Leaf 4 needs PvP or bosses: mob and elite charge cannot raise the meter past `leaf4 − 1`
  (`charge.leaf4-requires-pvp-or-boss`). Once above, mobs still count.
- Elites come from `charge-sources.elite-mobs` (mob type list).
- Party members give 0 charge to each other (replaces the scoreboard-team check).
- Combat timer for loadout changes counts PvP only (unchanged); Quiet Dew counts any damage.
- Fixed: the killed player's leaf multiplier was read after the death penalty wilted their leaves, and
  the repeat-victim check counted the current kill, so a first kill paid 50 %.

## Parties (proposal)

`/clover party invite|accept|leave|kick|list`; creator leads; max size `party.max-size` (4).
Shortcut: parties live in memory and reset on restart.

## Commands

| Command | Who |
| --- | --- |
| `/clover` | Opens the Clover menu (herbarium, equip, leaf 5); menus are identified by inventory holder |
| `/clover inspect <player>` | Leaf count, category icons, full curse |
| `/clover party …` | Parties |
| `/clover admin give <player> <core> [graft]` | `clover.admin` |
| `/clover admin setcharge <player> <amount>` | `clover.admin.setcharge` |
| `/clover admin reset <player>` | `clover.admin.reset` |
| `/clover admin reload` | `clover.admin.reload` |

## Testing

JUnit tests for the pure rules: charge math (death penalty, floor, leaf-4 gate, overflow), tag fatigue,
the Sap Cap window, the damage-reduction clamp and Turning Year phases. In-game behaviour is checked by
hand on a test server (`docs/playtest-checklist.md`).
