# Phase 2: Crafting — Design

Builds the concept doc's crafting loop (sections 5 and 10, Direction A "Pressed Blessings") on top of
milestone 1 (`2026-10-20-milestone1-playable-core-design.md`). After this phase, blessings and curses come
from play instead of `/clover admin give` (which stays for testing).

## Decisions (owner, 2026-10-21)

| Topic | Decision |
| --- | --- |
| Pressing Table | Any Fletching Table (no Java right-click use, vanilla recipe, nothing to track) |
| Clover Seeds | Shapeless crafting recipe |
| Physical blessings | Yes — claim items backed by a server ledger (approach A) |
| Rare cores | Recipe scraps from structure loot and bosses |
| Graft material | One rare vanilla item per graft, plus Pollen |
| Curses | Pressed like cores, every curse recipe includes a Wither Rose |

## Out of scope

Resource-pack art (the clover pixel art is parked), a trade window, a custom Pressing Table block,
PvP-dropped graft materials, mutations, dominance metrics.

## Items

Three plugin items, each a vanilla `PAPER` stack with a display name, lore and a persistent-data tag
(`cloverblessing:item` = `seed` | `claim` | `scrap`). Paper has no place/plant/compost/fuel use. An item
guard cancels vanilla crafting (`PrepareItemCraftEvent`) and cartography when a tagged item is in the grid,
so tagged items can't be spent as plain paper. The tag is the only identity; name and look are cosmetic.

| Item | Made by | Use |
| --- | --- | --- |
| Clover Seed | Shapeless recipe: Wheat Seeds + Bone Meal + Emerald → 1 seed (`crafting.seed-recipe`) | Consumed by every press |
| Blessing claim | "Take out" on a blessing in the herbarium | Right-click: the blessing moves into your herbarium |
| Recipe scrap | Structure loot, boss drops | Right-click: learns one rare recipe in your Field Guide, scrap consumed |

## Ledger (approach A)

A blessing record is always in exactly one place: a player's herbarium/leaves, or **out as an item**.

- `plugins/CloverBlessing/ledger.json` holds every blessing currently out as an item: blessing id → record.
- **Take out** (herbarium menu, shift-click): only unbound, unequipped blessings. Removes it from the herbarium,
  adds it to the ledger, gives a claim item carrying only the blessing id. Fails with "inventory full"
  instead of dropping it.
- **Redeem** (right-click the claim): if the id is in the ledger, remove it from the ledger, set the owner to
  the redeemer and add it to their herbarium; the item is consumed. If the id is **not** in the ledger the
  item is a duplicate or already used: it is removed and the event is logged as `VOID`.
- The ledger is saved with the same snapshot/atomic-write pattern as player data, and on every change
  (a take-out or redeem must never be lost to a crash).
- Bound (grafted) blessings never leave the herbarium.

## Pressing Table (any Fletching Table)

Right-click a Fletching Table (not sneaking) opens a 54-slot menu with two tabs.

**Press tab**
- Slots: 1 seed slot, 3 ingredient slots, a preview slot, a Press button. These four input slots are the
  only slots players may place items in; every other click is cancelled.
- When the inputs match a recipe, the preview shows the result's rule text, weakness and category. No match
  shows "No recipe matches"; a rare recipe the player hasn't learned shows "You haven't learned this recipe".
- Press consumes 1 seed + exactly one of each ingredient, creates the blessing in the herbarium (existing
  `BlessingManager.give`), records the recipe as discovered, plays a sound and refreshes the preview.
- Common recipes work without discovery (experimentation is the point); rare recipes need a scrap first.
- Closing the menu returns every item in the input slots (to inventory, or dropped at the player's feet if
  full) — never lost.

**Graft tab**
- Lists herbarium blessings. Clicking an ungrafted, non-curse blessing then a graft option grafts it if the
  player has the Pollen and the materials in their inventory. Clicking a grafted blessing offers Prune.
- Graft: costs `graft.pollen-cost` (100) Pollen + `graft.material-count` (2) of the graft's material. The
  blessing gets the graft (new `Blessing` with the same id, cooldown and owner) and becomes bound.
- Prune: removes the graft, refunds `crafting.prune-refund-percent` (50) % of the Pollen and
  `material-count × 50 %` (rounded down, so 1) of the material, and unbinds the blessing (it is a plain core again).
- Equipped blessings can't be grafted or pruned (unequip first) so cooldowns and slots stay simple.

| Graft | Material |
| --- | --- |
| Quick | Phantom Membrane |
| Deep | Diamond |
| Hunter | Breeze Rod |
| Vengeful | Wither Skeleton Skull |
| Ward | Heart of the Sea |

## Recipes (`recipes.yml`, defaults below)

Recipes move from code to `recipes.yml` (saved on first start, editable, read on `/clover admin reload`).
Each entry: core id, up to 3 ingredient materials, `rare: true|false`. Matching is order-independent and
exact (the multiset of ingredients equals the recipe). Startup logs and skips invalid entries (unknown core or
material, more than 3 ingredients, duplicate ingredient sets). Defaults use farmable materials as the doc asks.

| Core | Ingredients | Rare |
| --- | --- | --- |
| Thornlash | Rose Bush, Cactus, Iron Ingot | |
| Stinger | Arrow, Spider Eye, String | |
| Mossguard | Moss Block, Iron Ingot, Leather | |
| Last Leaf | Golden Apple, Bone Block, Lily Pad | |
| Springleaf | Slime Ball, Feather, Sugar | |
| Fieldwalker | Dandelion, Wheat, Leather | |
| Quiet Dew | Glistering Melon Slice, Kelp, Sweet Berries | |
| Leechvine | Vine, Spider Eye, Rotten Flesh | |
| Tangle Seed | Vine, String, Arrow | |
| Hobble | Iron Chain, Cactus, Bone | |
| Waystem | Compass, Ender Pearl, Oak Sapling | |
| Lanternleaf | Lantern, Glow Berries, Glowstone Dust | |
| Glass Bloom | Glass, Blaze Powder, Poppy | |
| Wager Root | Gold Ingot, Emerald, Rabbit Foot | |
| Echo Leaf | Echo Shard, Amethyst Shard, Sculk | yes |
| Turning Year | Clock, Blue Orchid, Allium | yes |
| Bloodroot (curse) | Wither Rose, Beetroot, Rotten Flesh | |
| Thorncrown (curse) | Wither Rose, Cactus, Rose Bush | |
| Hollow Bell (curse) | Wither Rose, Glowstone Dust, Amethyst Shard | |
| Hungering Clover (curse) | Wither Rose, Bone, Spider Eye | |

## Field Guide

A `/clover` menu page listing every recipe:
- discovered: the result and its ingredients;
- undiscovered common: a silhouette (gray dye) with category and `BlessingCore.getIngredientHints()`;
- undiscovered rare: silhouette plus "Found in the world".

## Recipe scraps

- Structure chests (`LootGenerateEvent`): each loot table in `scraps.loot-tables` adds one scrap with its
  chance. Defaults (5 %): `chests/ancient_city`, `chests/end_city_treasure`, `chests/bastion_treasure`.
  Trial Chamber vaults are left out: vault rewards are dispensed, not generated into a chest, so they need a
  different event; add them later if wanted.
- Bosses (`EntityDeathEvent`, killed by a player): Ender Dragon 100 %, Wither 50 %, Warden 25 %
  (`scraps.bosses`).
- A scrap names one random rare recipe. Redeeming a scrap you already know still consumes it and says so
  (scraps can be traded instead).

## Logging

`plugins/CloverBlessing/ledger.log`, one line per event, appended off the main thread:
`time | PRESS|GRAFT|PRUNE|TAKE_OUT|REDEEM|VOID|SCRAP | player | blessing id | core | details`.

## Code layout

| Unit | Responsibility |
| --- | --- |
| `crafting/RecipeRegistry` | Load/validate `recipes.yml`; `match(List<Material>) → recipe or null` |
| `crafting/CloverItems` | Create and identify seed / claim / scrap items (PDC tags); the item guard listener |
| `crafting/BlessingLedger` | Out-as-item records: take out, redeem, void; persistence |
| `crafting/GraftRules` | Pure maths: graft cost, prune refund |
| `crafting/PressingTableMenu` | The two-tab table menu (MenuHolder kinds `PRESS`, `GRAFT`), input return on close |
| `crafting/ScrapListener` | Loot and boss scrap drops |
| `crafting/CraftLog` | Async append to `ledger.log` |
| `ui/Menus` | Add Field Guide page, herbarium shift-click "take out" |

## Testing

JUnit: recipe matching and validation (order-independent, exact, duplicate-set rejection, the defaults are
all valid and unique), ledger rules (take out → redeem once, second redeem voids, bound can't leave), graft
and prune maths. In game: `docs/playtest-checklist.md` gains a Phase 2 section.
