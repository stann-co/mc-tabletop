# Tabletop art guide

Every texture and model in the mod is a plain file you can replace without touching code. All paths below are under
`src/main/resources/assets/tabletop/`. After replacing a file, rebuild the jar, or press **F3+T** in a running dev
client to reload resources.

The art in the repo is placeholder art made by `tools/GenerateAssets.java`. That tool only writes a texture if the
file doesn't exist, so running it again never overwrites your work. Delete a file to get its placeholder back.

To add new cards, chips or tokens rather than restyle Tabletop's own, see `ADDING_ITEMS.md`.

## Editing on sprite sheets

Instead of opening ninety-odd files, you can edit the textures on four sheets in `art/`:

| Sheet | What's on it | Layout |
|---|---|---|
| `cards.png` | Card faces and backs | One row per suit (spades, hearts, diamonds, clubs), ace to king; then two rows of backs in dye order |
| `decks.png` | Deck icons | Two rows, in the same dye order as the backs |
| `chips_and_tokens.png` | Chips and tokens | Chips 1, 5, 25, 100, 500; then small blind, big blind, dealer button |
| `blocks.png` | Table and playmat | Felt, wood, playmat edge |

Every tile is 32×32, so set your editor's grid to 32. Run these from the project folder with JDK 21:

- `java tools/TextureSheets.java unpack` cuts the sheets back into the texture files. It only writes a texture whose
  pixels changed. You can save a sheet at a bigger size, for example twice as wide and tall, and the tiles are then
  cut at that size.
- `java tools/TextureSheets.java pack` makes the sheets again from the texture files. It keeps any sheet that's
  already in `art/`, so your edits aren't lost; add `--force` to replace them.

Each sheet's `.txt` file says which texture each tile is. `unpack` reads it, so don't edit it. A texture added to one
of these folders later, such as a new card back, goes on an extra row the next time you pack.

## Textures

| What | File | Size and layout |
|---|---|---|
| Card faces (52) | `textures/item/card/<rank>_of_<suit>.png` | See "Cards" below |
| Jokers (4) | `textures/item/card/joker_1.png` … `joker_4.png` | Same layout as the faces; on the cards sheet they sit after the first row of backs |
| Card backs (16) | `textures/item/card_back/<colour>.png`, e.g. `red.png`, `light_blue.png` | Same layout as the faces; one per dye colour, white is the default. Card stock shows its back. |
| Decks (16) | `textures/item/deck/<colour>.png`, e.g. `red.png`, `light_blue.png` | 32×32 inventory icon, one per back colour; a deck shows the icon of its back |
| Chips | `textures/item/chip_1.png`, `chip_5`, `chip_25`, `chip_100`, `chip_500` | See "Chips and tokens" below |
| Tokens | `textures/item/small_blind.png`, `big_blind.png`, `dealer_button.png` | See "Chips and tokens" below |
| Table and playmat felt | `textures/block/poker_table_felt.png` | 32×32, tiles across the top |
| Table wood | `textures/block/poker_table_wood.png` | 32×32, used by the rail, legs and underside |
| Playmat edge | `textures/block/playmat_trim.png` | 32×32; only the bottom 2 rows show, along the mat's 1-pixel edge |

Card file names use `ace`, `2`–`10`, `jack`, `queen`, `king` and `spades`, `hearts`, `diamonds`, `clubs`, for
example `ace_of_spades.png`, `10_of_hearts.png`, `queen_of_diamonds.png`.

### Cards
- Square textures, 32×32 (any square size works; bigger is sharper).
- The card fills the **middle 24×32** of a 32×32 image (columns 4–27), or the middle 12×16 of a 16×16 image. Leave
  the columns on either side transparent.
- The top of the image is the top of the card. On the table, a card at its default rotation points its top away
  from the player who placed it.
- The same image is the inventory icon and the card lying on the table, where it covers 3×4 table pixels.
- The back is shown for face-down cards, for cards other players are holding, and for dropped cards. Which back
  depends on the card's theme; new themes are described in `ADDING_ITEMS.md`.

### Chips and tokens
- 32×32, a disc about **24 pixels across**, centred, seen from above. Leave the corners transparent.
- On the table a chip is a thin disc 2 table pixels across, stacked up to 20 high. The colours of the disc's
  outermost pixels become the side of the chip, so alternating colours around the rim give striped sides.
- A stack sorts itself, highest value at the bottom, and each run of like chips is drawn as one chip stretched to the
  run's height (one model per run keeps big stacks cheap to draw), so there are no lines between like chips.
- A token lies flat and is 3 table pixels across.

## Models

| What | File | Notes |
|---|---|---|
| Table top | `models/block/poker_table_top.json` | Felt surface at 12/16, always drawn |
| Table rail | `models/block/poker_table_rail.json` | North-side rail, 1 pixel wide with its top at 13/16; the game turns it for the other sides, and only draws it on sides with no table next to it |
| Table leg | `models/block/poker_table_leg.json` | North-west leg; drawn on corners where both sides are open |
| Table in inventory | `models/item/poker_table.json` | A whole single table |
| Playmat | `models/block/playmat.json` | One pixel tall, felt on top |
| Chips, tokens | `models/item/chip_*.json`, `small_blind.json`, ... | Flat item models; swap for 3D Blockbench models if you like. On the table a run of like chips is one chip model stretched to the run's height, so a 3D chip model should look right stretched |

Open and save the block models in Blockbench using the **Java Block/Item** format.

Two measurements in the code must match the table models. If you change them in Blockbench, change them in
`src/main/java/co/stann/tabletop/table/PokerTableBlock.java` too:
- `surfaceHeight()` is the height of the felt, now 12/16 (the top of the felt element in `poker_table_top.json`).
  The block's collision shapes in the same file follow the models.
- `edgeWidth()` is how far the rail reaches in from an open edge, now 1 pixel. Nothing can be placed on it.

The playmat's equivalents are in `PlaymatBlock.java` (felt at 1/16, no edge).

Don't edit the card item models (`models/item/<rank>_of_<suit>.json`, `card_stock.json`, `deck.json`) or the models in
`models/item/card_back/` and `models/item/deck/`: `GenerateAssets.java` rewrites them, and they just point each card,
back and deck icon at its texture.
