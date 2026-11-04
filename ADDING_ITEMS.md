# Adding your own cards, chips and tokens

Anything players can lay on a poker table or playmat is decided by **item tags**, not by code. Put an item in one of
these tags and it works on every table:

| Tag | How the item behaves on the table |
|---|---|
| `tabletop:cards` | A card: piles up, lies face-up or face-down, flips, goes into decks and the deck screen. Only the player holding it sees its face. |
| `tabletop:chips` | A chip: stacks up to 20 high, several can be placed at once, and stack labels add up its value. A stack sorts itself by `chip_value`, highest at the bottom. |
| `tabletop:tokens` | A token: a single marker like the dealer button. Placed and picked up one at a time, never stacks, can be rotated. |

The table draws the item's own model lying flat, so a texture is all a new item needs. The art layout is the same as
Tabletop's own cards and chips, described in `ASSETS.md`.

Two **data maps** add details:

| Data map | File | What it sets |
|---|---|---|
| `tabletop:chip_value` | `data/tabletop/data_maps/item/chip_value.json` | What a chip is worth, e.g. `{"values": {"mypack:gold_chip": {"value": 1000}}}`. Chips without one still stack; their label then shows only the count. |
| `tabletop:card_sort` | `data/tabletop/data_maps/item/card_sort.json` | Where a card goes when a deck is sorted, e.g. `{"values": {"mypack:joker": {"code": "0"}}}` |

**Card sort codes** are a rank number then a suit letter. Ranks run 1 (ace) to 13 (king); suits are a = spades,
b = hearts, c = diamonds, d = clubs, so the ace of spades is `1a` and the king of clubs `13d`. Sorting goes by suit,
then rank. Rank `0` sorts in front of everything, in random order (handy for jokers). Suit letters after `d`
(`e`, `f`, ...) sort after clubs. Cards without a code go last.

Data map files from different packs merge, so your file only needs your own entries.

## Card backs (themes)

Every card has a **back theme**, and a whole deck shares one. A theme is a card back and a deck icon, with no code:

| File | What it is |
|---|---|
| `assets/<namespace>/textures/item/card_back/<name>.png` | The back art, laid out like a card face (see `ASSETS.md`) |
| `assets/<namespace>/models/item/card_back/<name>.json` | `{"parent": "minecraft:item/generated", "textures": {"layer0": "<namespace>:item/card_back/<name>"}}` |
| `assets/<namespace>/textures/item/deck/<name>.png` | The inventory icon of a deck with this back, 16×16 |
| `assets/<namespace>/models/item/deck/<name>.json` | `{"parent": "minecraft:item/generated", "textures": {"layer0": "<namespace>:item/deck/<name>"}}` |

Tabletop finds every model in the `models/item/card_back/` and `models/item/deck/` folders by itself, so theme
`<namespace>:<name>` is ready as soon as those files exist. A theme without a deck icon uses Tabletop's default one, and
the game log warns about it. Give the theme a name with the lang key `card_back.<namespace>.<name>` (e.g.
`"Steampunk"`).

To let players use it, say which item applies it in `data/tabletop/data_maps/item/card_back.json`:
```json
{"values": {"mypack:brass_gear": {"back": "mypack:steampunk"}}}
```
Crafting that item with card stock, any card, or a deck gives it that back. A deck changes every card in it. Tabletop's
own themes are the 16 dye colours this way (`minecraft:blue_dye` gives `tabletop:blue`), and white is the default. JEI
lists card stock and decks in every theme and shows these crafting recipes, your themes included.

How the back carries through:
- Card stock is cut into a card design in a stonecutter, and the card keeps the stock's back. New card designs should
  use the `tabletop:card_cutting` recipe type (same JSON as `minecraft:stonecutting`) so their back carries over too.
  Create's mechanical saw can't carry a back over, so it is told to skip these recipes (Create's
  `create:automation_ignore` recipe serializer tag).
- A deck only takes cards with its own back. Cards drawn from it get its back.
- On the table every card keeps its back, and everyone sees it.
- Renaming a card or deck in an anvil gives it a label (`tabletop:label`) instead of a name. It works like a second
  back: cards from a labelled deck carry the label, and only cards with the same back and label go together.
- Chips (anything in `tabletop:chips`) get a label in an anvil the same way. Each chip keeps its label on the table and
  when picked up, and a stack on the table only takes chips with its own label. Only Tabletop's own chips show the
  label in their tooltip; another mod's chip item would have to add that line itself.

## With KubeJS (what this pack uses)

**A new card**, e.g. a joker made from card stock in a stonecutter:

`kubejs/startup_scripts/tabletop_cards.js`
```js
StartupEvents.registry('item', event => {
  event.create('joker').displayName('Joker').maxStackSize(1)
})
```
Texture: `kubejs/assets/kubejs/textures/item/joker.png` (a card in the middle 12×16 of a 16×16 image, see `ASSETS.md`).

`kubejs/server_scripts/tabletop_cards.js`
```js
ServerEvents.tags('item', event => {
  event.add('tabletop:cards', 'kubejs:joker')
})
ServerEvents.recipes(event => {
  // tabletop:card_cutting is a stonecutter recipe that keeps the card stock's back.
  event.custom({
    type: 'tabletop:card_cutting',
    ingredient: {item: 'tabletop:card_stock'},
    result: {id: 'kubejs:joker', count: 1}
  })
})
```

`kubejs/data/tabletop/data_maps/item/card_sort.json` (optional; puts jokers on top when sorting)
```json
{"values": {"kubejs:joker": {"code": "0"}}}
```

**A new chip:** register it the same way, add it to `tabletop:chips`, and give it a value in
`kubejs/data/tabletop/data_maps/item/chip_value.json`:
```json
{"values": {"kubejs:gold_chip": {"value": 1000}}}
```

**A new token**, e.g. a "straddle" marker: register it and add it to `tabletop:tokens`.

**A new card back theme**, e.g. a steampunk back applied with a Create brass ingot:
- `kubejs/assets/kubejs/textures/item/card_back/steampunk.png`
- `kubejs/assets/kubejs/models/item/card_back/steampunk.json`:
  `{"parent": "minecraft:item/generated", "textures": {"layer0": "kubejs:item/card_back/steampunk"}}`
- `kubejs/assets/kubejs/textures/item/deck/steampunk.png`
- `kubejs/assets/kubejs/models/item/deck/steampunk.json`:
  `{"parent": "minecraft:item/generated", "textures": {"layer0": "kubejs:item/deck/steampunk"}}`
- `kubejs/assets/kubejs/lang/en_us.json`: `{"card_back.kubejs.steampunk": "Steampunk"}`
- `kubejs/data/tabletop/data_maps/item/card_back.json`:
  `{"values": {"create:brass_ingot": {"back": "kubejs:steampunk"}}}`

Then card stock crafted with a brass ingot becomes "Steampunk Card Stock", and so does anything cut from it.

**An existing item** from any mod can join a tag too. `event.add('tabletop:tokens', 'minecraft:paper')` lets players
lay paper on the table as a marker.

## With a datapack

A datapack can't add new items, but it can make existing ones placeable and set chip values and sort codes. Use the
same files with the same paths: `data/tabletop/tags/item/cards.json` (or `chips.json`, `tokens.json`) with
`{"values": ["somemod:some_item"]}`, plus the data map files above.

## From another mod (Java)

`co.stann.tabletop.api.TabletopApi` has the three tags (`CARDS`, `CHIPS`, `TOKENS`), the data maps (`CHIP_VALUE`,
`CARD_SORT`, `CARD_BACK`), `kindOf(ItemStack)`, which says how an item behaves on a table, and `backOf` / `setBack`
for a card's or deck's back theme, and `labelOf` / `setLabel` for a card's, deck's or chip's anvil label.

**A new kind of surface**, like a round table or a card mat for a desk: extend
`co.stann.tabletop.table.PlaySurfaceBlock` and give it:
- `surfaceHeight()`: where the playing surface is within the block (a poker table's is `12 / 16.0`);
- `edgeWidth()`: how many pixels in from an edge with no matching neighbour nothing can be placed (a table's rail is `1`);
- a shape, a model and a `codec()`, as for any block.

Everything else (placing, taking, the ghost preview, joining neighbouring blocks, dropping what's on it when broken)
comes with the class. Tabletop gives every `PlaySurfaceBlock` its block entity automatically.
