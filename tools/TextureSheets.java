import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import javax.imageio.ImageIO;

/**
 * Packs Tabletop's textures into a few sprite sheets for editing, and splits the sheets back into the texture files.
 *
 * <p>Run from the project folder with JDK 21:
 * <ul>
 *   <li>{@code java tools/TextureSheets.java pack} writes the sheets to {@code art/}. It won't replace a sheet that is
 *       already there, so edits aren't lost; add {@code --force} to replace them anyway.</li>
 *   <li>{@code java tools/TextureSheets.java unpack} cuts the sheets in {@code art/} back into the textures. Only
 *       textures whose pixels changed are written.</li>
 *   <li>{@code java tools/TextureSheets.java scale 2} makes every texture that many times bigger, pixel for pixel.</li>
 * </ul>
 *
 * <p>Next to each sheet, a {@code .txt} file says which texture each tile is, so unpack cuts the sheet the way pack laid
 * it out. A sheet can be saved at a bigger size than it was packed at (twice as wide and tall, say): the tiles are
 * then cut at that size.
 */
public class TextureSheets {
    static final Path TEXTURES = Path.of("src/main/resources/assets/tabletop/textures");
    static final Path ART = Path.of("art");

    static final String[] RANKS = {"ace", "2", "3", "4", "5", "6", "7", "8", "9", "10", "jack", "queen", "king"};
    static final String[] SUITS = {"spades", "hearts", "diamonds", "clubs"};
    static final int JOKERS = 4;
    static final String[] BACK_NAMES = {"white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
            "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black"};
    static final String[] CHIPS_AND_TOKENS = {"chip_1", "chip_5", "chip_25", "chip_100", "chip_500", "small_blind",
            "big_blind", "dealer_button"};
    static final String[] BLOCKS = {"poker_table_felt", "poker_table_wood", "playmat_trim"};

    /** A sheet: rows of texture paths (relative to the textures folder), laid out top to bottom. */
    record Sheet(String name, List<List<String>> rows) {}

    public static void main(String[] args) throws IOException {
        String command = args.length > 0 ? args[0] : "";
        switch (command) {
            case "pack" -> pack(Arrays.asList(args).contains("--force"));
            case "unpack" -> unpack();
            case "scale" -> scale(args.length > 1 ? Integer.parseInt(args[1]) : 2);
            default -> System.out.println("Usage: java tools/TextureSheets.java pack [--force] | unpack | scale <factor>");
        }
    }

    // --- Layout ---

    /**
     * The sheets and what goes on them. Tabletop's own textures come first in a fixed order; any other texture in the
     * same folder (a theme someone added, say) follows in extra rows.
     */
    static List<Sheet> sheets() throws IOException {
        List<List<String>> cards = new ArrayList<>();
        Set<String> faces = new LinkedHashSet<>();
        for (String suit : SUITS) {
            List<String> row = new ArrayList<>();
            for (String rank : RANKS) {
                row.add("item/card/" + rank + "_of_" + suit + ".png");
            }
            cards.add(row);
            faces.addAll(row);
        }
        // The backs in rows of 8, with the jokers after the first row of them.
        List<List<String>> backs = rowsOf("item/card_back", BACK_NAMES, 8);
        for (int joker = 1; joker <= JOKERS; joker++) {
            String path = "item/card/joker_" + joker + ".png";
            backs.get(0).add(path);
            faces.add(path);
        }
        cards.addAll(backs);
        addExtras(cards, faces, "item/card", RANKS.length);

        // Chips on one row, tokens on the next.
        List<List<String>> chips = new ArrayList<>();
        Set<String> known = new LinkedHashSet<>();
        for (String name : CHIPS_AND_TOKENS) {
            known.add("item/" + name + ".png");
        }
        List<String> chipsThenTokens = new ArrayList<>(known);
        addRows(chips, chipsThenTokens.subList(0, 5), 5);
        addRows(chips, chipsThenTokens.subList(5, chipsThenTokens.size()), 5);
        addExtras(chips, known, "item", 5);

        return List.of(
                new Sheet("cards", cards),
                new Sheet("decks", rowsOf("item/deck", BACK_NAMES, 8)),
                new Sheet("chips_and_tokens", chips),
                new Sheet("blocks", rowsOf("block", BLOCKS, BLOCKS.length)));
    }

    /** The named textures in a folder in rows of {@code width}, then any others there. */
    static List<List<String>> rowsOf(String folder, String[] names, int width) throws IOException {
        List<List<String>> rows = new ArrayList<>();
        Set<String> known = new LinkedHashSet<>();
        for (String name : names) {
            known.add(folder + "/" + name + ".png");
        }
        addRows(rows, new ArrayList<>(known), width);
        addExtras(rows, known, folder, width);
        return rows;
    }

    /** Adds rows for the textures directly in {@code folder} that aren't in {@code known}. */
    static void addExtras(List<List<String>> rows, Set<String> known, String folder, int width) throws IOException {
        Path dir = TEXTURES.resolve(folder);
        if (!Files.isDirectory(dir)) {
            return;
        }
        try (Stream<Path> files = Files.list(dir)) {
            List<String> extras = files.filter(file -> file.toString().endsWith(".png"))
                    .map(file -> folder + "/" + file.getFileName())
                    .filter(path -> !known.contains(path))
                    .sorted()
                    .toList();
            addRows(rows, extras, width);
        }
    }

    static void addRows(List<List<String>> rows, List<String> paths, int width) {
        for (int i = 0; i < paths.size(); i += width) {
            rows.add(new ArrayList<>(paths.subList(i, Math.min(i + width, paths.size()))));
        }
    }

    // --- Pack ---

    static void pack(boolean force) throws IOException {
        Files.createDirectories(ART);
        for (Sheet sheet : sheets()) {
            Path image = ART.resolve(sheet.name() + ".png");
            if (Files.exists(image) && !force) {
                System.out.println("kept " + image + " (already there; use --force to replace it)");
                continue;
            }
            int columns = sheet.rows().stream().mapToInt(List::size).max().orElse(0);
            int tile = -1;
            String first = null;
            List<String> layout = new ArrayList<>();
            List<BufferedImage> tiles = new ArrayList<>();
            for (int row = 0; row < sheet.rows().size(); row++) {
                for (int column = 0; column < sheet.rows().get(row).size(); column++) {
                    String path = sheet.rows().get(row).get(column);
                    Path file = TEXTURES.resolve(path);
                    if (!Files.exists(file)) {
                        System.out.println("missing " + file + ", leaving its tile empty");
                        continue;
                    }
                    BufferedImage texture = ImageIO.read(file.toFile());
                    if (texture.getWidth() != texture.getHeight()) {
                        throw new IOException(file + " isn't square");
                    }
                    if (tile < 0) {
                        tile = texture.getWidth();
                        first = path;
                    } else if (texture.getWidth() != tile) {
                        throw new IOException(path + " is " + texture.getWidth() + "px but " + first + " is " + tile
                                + "px; every texture on the " + sheet.name() + " sheet must be the same size");
                    }
                    layout.add(column + " " + row + " " + path);
                    tiles.add(texture);
                }
            }
            if (tile < 0) {
                continue;
            }
            BufferedImage out = new BufferedImage(columns * tile, sheet.rows().size() * tile, BufferedImage.TYPE_INT_ARGB);
            for (int i = 0; i < layout.size(); i++) {
                String[] parts = layout.get(i).split(" ", 3);
                out.createGraphics().drawImage(tiles.get(i), Integer.parseInt(parts[0]) * tile, Integer.parseInt(parts[1]) * tile, null);
            }
            ImageIO.write(out, "png", image.toFile());
            List<String> manifest = new ArrayList<>();
            manifest.add("# Which texture each tile of " + sheet.name() + ".png is: column, row, file under " + TEXTURES.toString().replace('\\', '/'));
            manifest.add("# Written by tools/TextureSheets.java pack; unpack reads it. Don't edit.");
            manifest.add("grid " + columns + " " + sheet.rows().size());
            manifest.addAll(layout);
            Files.write(ART.resolve(sheet.name() + ".txt"), manifest);
            System.out.println("wrote " + image + " (" + columns + "x" + sheet.rows().size() + " tiles of " + tile + "px)");
        }
    }

    // --- Unpack ---

    static void unpack() throws IOException {
        if (!Files.isDirectory(ART)) {
            System.out.println("No art/ folder; run pack first.");
            return;
        }
        List<Path> manifests;
        try (Stream<Path> files = Files.list(ART)) {
            manifests = files.filter(file -> file.toString().endsWith(".txt")).sorted().toList();
        }
        int written = 0;
        for (Path manifest : manifests) {
            String name = manifest.getFileName().toString().replace(".txt", "");
            Path imageFile = ART.resolve(name + ".png");
            if (!Files.exists(imageFile)) {
                System.out.println("no " + imageFile + " for " + manifest + ", skipped");
                continue;
            }
            BufferedImage sheet = ImageIO.read(imageFile.toFile());
            int columns = 0;
            int rows = 0;
            for (String line : Files.readAllLines(manifest)) {
                if (line.isBlank() || line.startsWith("#")) {
                    continue;
                }
                String[] parts = line.split(" ", 3);
                if (parts[0].equals("grid")) {
                    columns = Integer.parseInt(parts[1]);
                    rows = Integer.parseInt(parts[2]);
                    if (sheet.getWidth() % columns != 0 || sheet.getHeight() % rows != 0
                            || sheet.getWidth() / columns != sheet.getHeight() / rows) {
                        throw new IOException(imageFile + " is " + sheet.getWidth() + "x" + sheet.getHeight()
                                + ", which doesn't split into " + columns + "x" + rows + " square tiles");
                    }
                    continue;
                }
                int tile = sheet.getWidth() / columns;
                BufferedImage texture = new BufferedImage(tile, tile, BufferedImage.TYPE_INT_ARGB);
                texture.createGraphics().drawImage(sheet.getSubimage(Integer.parseInt(parts[0]) * tile, Integer.parseInt(parts[1]) * tile, tile, tile), 0, 0, null);
                Path file = TEXTURES.resolve(parts[2]);
                if (Files.exists(file) && samePixels(ImageIO.read(file.toFile()), texture)) {
                    continue;
                }
                Files.createDirectories(file.getParent());
                ImageIO.write(texture, "png", file.toFile());
                System.out.println("wrote " + file);
                written++;
            }
        }
        System.out.println(written + " texture(s) changed");
    }

    static boolean samePixels(BufferedImage a, BufferedImage b) {
        if (a.getWidth() != b.getWidth() || a.getHeight() != b.getHeight()) {
            return false;
        }
        for (int x = 0; x < a.getWidth(); x++) {
            for (int y = 0; y < a.getHeight(); y++) {
                int pa = a.getRGB(x, y);
                int pb = b.getRGB(x, y);
                // Fully transparent pixels count as the same whatever colour they hold.
                if (pa != pb && !((pa >>> 24) == 0 && (pb >>> 24) == 0)) {
                    return false;
                }
            }
        }
        return true;
    }

    // --- Scale ---

    static void scale(int factor) throws IOException {
        List<Path> files;
        try (Stream<Path> walk = Files.walk(TEXTURES)) {
            files = walk.filter(file -> file.toString().endsWith(".png")).sorted().toList();
        }
        for (Path file : files) {
            BufferedImage in = ImageIO.read(file.toFile());
            BufferedImage out = new BufferedImage(in.getWidth() * factor, in.getHeight() * factor, BufferedImage.TYPE_INT_ARGB);
            for (int x = 0; x < out.getWidth(); x++) {
                for (int y = 0; y < out.getHeight(); y++) {
                    out.setRGB(x, y, in.getRGB(x / factor, y / factor));
                }
            }
            ImageIO.write(out, "png", file.toFile());
            System.out.println(in.getWidth() + "px -> " + out.getWidth() + "px  " + file);
        }
    }
}
