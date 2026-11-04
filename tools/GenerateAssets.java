import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import javax.imageio.ImageIO;

/**
 * Writes placeholder art and the files for Tabletop's 52 standard cards and 4 jokers.
 *
 * <p>Run from the project folder with JDK 21: {@code java tools/GenerateAssets.java}
 *
 * <p>Textures are only written when the file doesn't exist yet, so after you replace one with your own art,
 * running this again keeps your version. Delete a texture to get its placeholder back. Always rewritten: the card
 * item models, the card back and deck icon models, the #tabletop:cards tag, the card sort codes, the dye-to-back data map
 * and the stonecutter recipes.
 */
public class GenerateAssets {
    static final Path ASSETS = Path.of("src/main/resources/assets/tabletop");
    static final Path DATA = Path.of("src/main/resources/data/tabletop");
    static final int TEXTURE_SCALE = 2;
    // Same order as co.stann.tabletop.Card: card = suit * 13 + rank.
    static final String[] RANKS = {"ace", "2", "3", "4", "5", "6", "7", "8", "9", "10", "jack", "queen", "king"};
    static final String[] SUITS = {"spades", "hearts", "diamonds", "clubs"};
    // Same as co.stann.tabletop.Card.JOKERS: joker_1 to joker_4.
    static final int JOKERS = 4;
    static final int[] CHIP_VALUES = {1, 5, 25, 100, 500};
    // Chip body and edge-spot colours, by value.
    static final int[][] CHIP_COLORS = {{0xE8E6E1, 0x2B4C9B}, {0xC62828, 0xF2F2F2}, {0x2E7D32, 0xF2F2F2}, {0x262626, 0xF2F2F2}, {0x6A1B9A, 0xF2F2F2}};
    static final String SUIT_LETTERS = "abcd";
    // Tabletop's card backs, one per dye (Minecraft's dye colours); each dye gives cards its back when crafted together.
    static final String[] BACK_NAMES = {"white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
            "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black"};
    static final int[] BACK_COLORS = {0xF9FFFE, 0xF9801D, 0xC74EBD, 0x3AB3DA, 0xFED83D, 0x80C71F, 0xF38BAA, 0x474F52,
            0x9D9D97, 0x169C9C, 0x8932B8, 0x3C44AA, 0x835432, 0x5E7C16, 0xB02E26, 0x1D1D21};

    static final int CARD_PAPER = 0xF7F3E8;
    static final int CARD_EDGE = 0x6E6A60;
    static final int RED = 0xC8202A;
    static final int BLACK = 0x1E1E1E;

    // 3x5 rank glyphs ("10" is 5 wide), '#' = ink.
    static final String[][] RANK_GLYPHS = {
            {".#.", "#.#", "###", "#.#", "#.#"},
            {"##.", "..#", ".#.", "#..", "###"},
            {"##.", "..#", ".#.", "..#", "##."},
            {"#.#", "#.#", "###", "..#", "..#"},
            {"###", "#..", "##.", "..#", "##."},
            {".##", "#..", "###", "#.#", "###"},
            {"###", "..#", ".#.", ".#.", ".#."},
            {"###", "#.#", "###", "#.#", "###"},
            {"###", "#.#", "###", "..#", "##."},
            {"#.###", "#.#.#", "#.#.#", "#.#.#", "#.###"},
            {"..#", "..#", "..#", "#.#", ".#."},
            {".#.", "#.#", "#.#", "##.", ".##"},
            {"#.#", "#.#", "##.", "#.#", "#.#"},
    };
    // 3x5 letters for the tokens.
    static final String[] LETTER_S = {"###", "#..", "###", "..#", "###"};
    static final String[] LETTER_B = {"##.", "#.#", "##.", "#.#", "##."};
    static final String[] LETTER_D = {"##.", "#.#", "#.#", "#.#", "##."};
    // 5x5 suit pips.
    static final String[][] SUIT_GLYPHS = {
            {"..#..", ".###.", "#####", "#####", "..#.."},
            {"##.##", "#####", "#####", ".###.", "..#.."},
            {"..#..", ".###.", "#####", ".###.", "..#.."},
            {".###.", "#####", ".###.", "#####", "..#.."},
    };

    public static void main(String[] args) throws IOException {
        for (int suit = 0; suit < SUITS.length; suit++) {
            for (int rank = 0; rank < RANKS.length; rank++) {
                texture("item/card/" + cardId(suit * 13 + rank), cardFace(rank, suit));
            }
        }
        for (int joker = 0; joker < JOKERS; joker++) {
            texture("item/card/" + jokerId(joker), jokerFace());
        }
        for (int i = 0; i < BACK_NAMES.length; i++) {
            texture("item/card_back/" + BACK_NAMES[i], cardBack(BACK_COLORS[i]));
            texture("item/deck/" + BACK_NAMES[i], deck(BACK_COLORS[i]));
        }
        for (int i = 0; i < CHIP_VALUES.length; i++) {
            texture("item/chip_" + CHIP_VALUES[i], chip(CHIP_COLORS[i][0], CHIP_COLORS[i][1]));
        }
        texture("block/poker_table_felt", felt());
        texture("block/poker_table_wood", wood());
        texture("item/small_blind", token(0x2F6FD0, 0xF2F2F2, LETTER_S, LETTER_B));
        texture("item/big_blind", token(0xE08A1E, 0xF2F2F2, LETTER_B, LETTER_B));
        texture("item/dealer_button", token(0xEEEEEE, 0x262626, LETTER_D));
        texture("block/playmat_trim", trim());
        writeCardFiles();
    }

    static String cardId(int card) {
        return RANKS[card % 13] + "_of_" + SUITS[card / 13];
    }

    static String jokerId(int joker) {
        return "joker_" + (joker + 1);
    }

    // --- Textures ---

    static void texture(String name, BufferedImage image) throws IOException {
        Path file = ASSETS.resolve("textures/" + name + ".png");
        if (Files.exists(file)) {
            return;
        }
        Files.createDirectories(file.getParent());
        ImageIO.write(scaled(image), "png", file.toFile());
        System.out.println("wrote " + file);
    }

    /** The placeholders are drawn at 16x16 and written TEXTURE_SCALE times bigger, pixel for pixel. */
    static BufferedImage scaled(BufferedImage image) {
        BufferedImage out = new BufferedImage(image.getWidth() * TEXTURE_SCALE, image.getHeight() * TEXTURE_SCALE, BufferedImage.TYPE_INT_ARGB);
        for (int x = 0; x < out.getWidth(); x++) {
            for (int y = 0; y < out.getHeight(); y++) {
                out.setRGB(x, y, image.getRGB(x / TEXTURE_SCALE, y / TEXTURE_SCALE));
            }
        }
        return out;
    }

    /** A blank card: columns 2-13 of a 16x16 image, with clipped corners and a 1 pixel edge. */
    static BufferedImage card(int fill) {
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int x = 2; x <= 13; x++) {
            for (int y = 0; y <= 15; y++) {
                boolean corner = (x == 2 || x == 13) && (y == 0 || y == 15);
                boolean edge = x == 2 || x == 13 || y == 0 || y == 15;
                if (!corner) {
                    set(image, x, y, edge ? CARD_EDGE : fill);
                }
            }
        }
        return image;
    }

    static BufferedImage cardFace(int rank, int suit) {
        BufferedImage image = card(CARD_PAPER);
        int ink = suit == 1 || suit == 2 ? RED : BLACK;
        draw(image, RANK_GLYPHS[rank], 4, 2, ink);
        draw(image, SUIT_GLYPHS[suit], 6, 8, ink);
        return image;
    }

    /** A joker: a red and black "J" with a star under it. */
    static BufferedImage jokerFace() {
        BufferedImage image = card(CARD_PAPER);
        draw(image, RANK_GLYPHS[10], 4, 2, RED);
        draw(image, new String[] {"..#..", "#####", ".###.", ".#.#.", "#...#"}, 6, 8, BLACK);
        return image;
    }

    /** A card back: a checkered panel in {@code color} inside a paper border. */
    static BufferedImage cardBack(int color) {
        BufferedImage image = card(CARD_PAPER);
        int check = check(color);
        for (int x = 4; x <= 11; x++) {
            for (int y = 2; y <= 13; y++) {
                set(image, x, y, (x + y) % 2 == 0 ? color : check);
            }
        }
        return image;
    }

    /** A deck's inventory icon: the top card's back in {@code color} over the sides of the cards below. */
    static BufferedImage deck(int color) {
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int x = 3; x <= 14; x++) {
            for (int y = 2; y <= 15; y++) {
                set(image, x, y, y % 2 == 0 ? 0xE6E0D0 : 0xBDB6A6);
            }
        }
        int check = check(color);
        for (int x = 1; x <= 12; x++) {
            for (int y = 0; y <= 13; y++) {
                boolean edge = x == 1 || x == 12 || y == 0 || y == 13;
                set(image, x, y, edge ? CARD_PAPER : (x + y) % 2 == 0 ? color : check);
            }
        }
        return image;
    }

    /** The second colour of a back's check pattern. Very light colours get a darker one so the pattern still shows. */
    static int check(int color) {
        return (color >> 16 & 255) + (color >> 8 & 255) + (color & 255) > 600 ? shade(color, -50) : shade(color, -28);
    }

    /** A 12 pixel chip seen from above. Its outermost pixels alternate colours, which become the stripes on a stack. */
    static BufferedImage chip(int body, int spots) {
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int x = 0; x < 16; x++) {
            for (int y = 0; y < 16; y++) {
                double dx = x - 7.5;
                double dy = y - 7.5;
                double distance = Math.hypot(dx, dy);
                if (distance >= 6) {
                    continue;
                }
                int segment = (int) Math.floor((Math.atan2(dy, dx) + Math.PI) / (Math.PI / 4));
                int color = body;
                if (distance >= 4.9 && segment % 2 == 0) {
                    color = spots;
                } else if (distance >= 3 && distance < 3.8) {
                    color = shade(body, body == 0x262626 ? 40 : -30);
                }
                set(image, x, y, color);
            }
        }
        return image;
    }

    /** A 12 pixel disc with a contrasting rim and up to two letters in the middle, for the table tokens. */
    static BufferedImage token(int body, int ink, String[]... letters) {
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int x = 0; x < 16; x++) {
            for (int y = 0; y < 16; y++) {
                double distance = Math.hypot(x - 7.5, y - 7.5);
                if (distance < 6) {
                    set(image, x, y, distance >= 5 ? shade(body, -40) : body);
                }
            }
        }
        int width = letters.length * 4 - 1;
        for (int i = 0; i < letters.length; i++) {
            draw(image, letters[i], 8 - (width + 1) / 2 + i * 4, 5, ink);
        }
        return image;
    }

    /** The side of a playmat: dark felt with a thin gold line. */
    static BufferedImage trim() {
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int x = 0; x < 16; x++) {
            for (int y = 0; y < 16; y++) {
                set(image, x, y, y == 15 ? 0xC9A227 : 0x1E4A28);
            }
        }
        return image;
    }

    static BufferedImage felt() {
        Random random = new Random(1);
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int x = 0; x < 16; x++) {
            for (int y = 0; y < 16; y++) {
                set(image, x, y, shade(0x2F6B3B, random.nextInt(9) - 4));
            }
        }
        return image;
    }

    static BufferedImage wood() {
        Random random = new Random(2);
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 16; y++) {
            int grain = y % 4 == 3 ? -18 : (y % 4 == 0 ? 6 : 0);
            for (int x = 0; x < 16; x++) {
                set(image, x, y, shade(0x6B4428, grain + random.nextInt(7) - 3));
            }
        }
        return image;
    }

    static void draw(BufferedImage image, String[] glyph, int left, int top, int color) {
        for (int row = 0; row < glyph.length; row++) {
            for (int col = 0; col < glyph[row].length(); col++) {
                if (glyph[row].charAt(col) == '#') {
                    set(image, left + col, top + row, color);
                }
            }
        }
    }

    static void set(BufferedImage image, int x, int y, int rgb) {
        image.setRGB(x, y, 0xFF000000 | rgb);
    }

    static int shade(int rgb, int amount) {
        int r = Math.clamp((rgb >> 16 & 255) + amount, 0, 255);
        int g = Math.clamp((rgb >> 8 & 255) + amount, 0, 255);
        int b = Math.clamp((rgb & 255) + amount, 0, 255);
        return r << 16 | g << 8 | b;
    }

    // --- Models ---

    static void writeCardFiles() throws IOException {
        Path models = ASSETS.resolve("models/item");
        Files.createDirectories(models);
        // Each back theme has a card back model in models/item/card_back/ and a deck icon in models/item/deck/, found by
        // the mod when resources load.
        Files.createDirectories(models.resolve("card_back"));
        Files.createDirectories(models.resolve("deck"));
        List<String> dyeBacks = new ArrayList<>();
        for (String back : BACK_NAMES) {
            Files.writeString(models.resolve("card_back/" + back + ".json"), generated("tabletop:item/card_back/" + back));
            Files.writeString(models.resolve("deck/" + back + ".json"), generated("tabletop:item/deck/" + back));
            dyeBacks.add("    \"minecraft:%s_dye\": {\"back\": \"tabletop:%s\"}".formatted(back, back));
        }
        // Card stock and decks are drawn with their own theme by the mod; these defaults are what other places show.
        Files.writeString(models.resolve("card_stock.json"), "{\n  \"parent\": \"tabletop:item/card_back/white\"\n}\n");
        Files.writeString(models.resolve("deck.json"), "{\n  \"parent\": \"tabletop:item/deck/white\"\n}\n");
        List<String> tagged = new ArrayList<>(List.of("    \"tabletop:card_stock\""));
        List<String> sortCodes = new ArrayList<>();
        Path recipes = DATA.resolve("recipe/card");
        Files.createDirectories(recipes);
        // Card id, then its sort code: rank number then suit letter, e.g. 1a for the ace of spades. Jokers are rank 0.
        List<String[]> cards = new ArrayList<>();
        for (int card = 0; card < 52; card++) {
            cards.add(new String[] {cardId(card), "" + (card % 13 + 1) + SUIT_LETTERS.charAt(card / 13)});
        }
        for (int joker = 0; joker < JOKERS; joker++) {
            cards.add(new String[] {jokerId(joker), "0"});
        }
        for (String[] card : cards) {
            String id = card[0];
            Files.writeString(models.resolve(id + ".json"), generated("tabletop:item/card/" + id));
            tagged.add("    \"tabletop:" + id + "\"");
            sortCodes.add("    \"tabletop:%s\": {\"code\": \"%s\"}".formatted(id, card[1]));
            Files.writeString(recipes.resolve(id + ".json"), """
                    {
                      "type": "tabletop:card_cutting",
                      "ingredient": {"item": "tabletop:card_stock"},
                      "result": {"id": "tabletop:%s", "count": 1}
                    }
                    """.formatted(id));
        }
        Path tags = DATA.resolve("tags/item");
        Files.createDirectories(tags);
        Files.writeString(tags.resolve("cards.json"), "{\n  \"values\": [\n" + String.join(",\n", tagged) + "\n  ]\n}\n");
        Path dataMaps = DATA.resolve("data_maps/item");
        Files.createDirectories(dataMaps);
        Files.writeString(dataMaps.resolve("card_sort.json"), "{\n  \"values\": {\n" + String.join(",\n", sortCodes) + "\n  }\n}\n");
        Files.writeString(dataMaps.resolve("card_back.json"), "{\n  \"values\": {\n" + String.join(",\n", dyeBacks) + "\n  }\n}\n");
        System.out.println("wrote card, back and deck models, tag, sort codes, dye backs and stonecutter recipes");
    }

    static String generated(String texture) {
        return """
                {
                  "parent": "minecraft:item/generated",
                  "textures": {
                    "layer0": "%s"
                  }
                }
                """.formatted(texture);
    }
}
