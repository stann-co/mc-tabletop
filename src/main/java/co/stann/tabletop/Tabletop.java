package co.stann.tabletop;

import co.stann.tabletop.api.TabletopApi;
import co.stann.tabletop.net.FlipHeldPayload;
import co.stann.tabletop.net.PeekPayload;
import co.stann.tabletop.net.ShuffleDeckPayload;
import co.stann.tabletop.net.TableActionPayload;
import co.stann.tabletop.table.PlaySurfaceBlock;
import co.stann.tabletop.table.PlaySurfaceBlockEntity;
import co.stann.tabletop.table.PlaymatBlock;
import co.stann.tabletop.table.PokerTableBlock;
import co.stann.tabletop.table.TableActions;
import com.mojang.serialization.Codec;
import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import net.minecraft.world.item.crafting.SingleItemRecipe;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.event.BlockEntityTypeAddBlocksEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.datamaps.RegisterDataMapTypesEvent;

@Mod(Tabletop.MODID)
public class Tabletop {
    public static final String MODID = TabletopApi.MODID;

    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MODID);
    private static final DeferredRegister.DataComponents COMPONENTS = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, MODID);
    private static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);
    private static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS = DeferredRegister.create(Registries.RECIPE_SERIALIZER, MODID);
    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, MODID);

    /** Whether a card will be laid face-up. Missing means face-up. Works on any card, see {@link TabletopApi#CARDS}. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Boolean>> FACE_UP = COMPONENTS.registerComponentType("face_up",
            builder -> builder.persistent(Codec.BOOL).networkSynchronized(ByteBufCodecs.BOOL));
    /** The back theme of a card or deck, e.g. tabletop:blue. Missing means {@link TabletopApi#DEFAULT_BACK}. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ResourceLocation>> BACK = COMPONENTS.registerComponentType("back",
            builder -> builder.persistent(ResourceLocation.CODEC).networkSynchronized(ResourceLocation.STREAM_CODEC));
    /**
     * A card's, deck's or chip's label, given by renaming it in an anvil (see {@link CardLabels}), so players can tell
     * their cards and chips from anyone else's. Missing means none. Cards from a deck share its label, and labels must
     * match to combine.
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<String>> LABEL = COMPONENTS.registerComponentType("label",
            builder -> builder.persistent(Codec.STRING).networkSynchronized(ByteBufCodecs.STRING_UTF8));
    /** The cards in a deck item, bottom to top. Missing means a full, fresh deck. They all have the deck's {@link Backing}. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<List<Item>>> DECK_CARDS = COMPONENTS.registerComponentType("deck_cards",
            builder -> builder.persistent(BuiltInRegistries.ITEM.byNameCodec().listOf())
                    .networkSynchronized(ByteBufCodecs.registry(Registries.ITEM).apply(ByteBufCodecs.list())));

    public static final DeferredBlock<PokerTableBlock> POKER_TABLE = BLOCKS.register("poker_table",
            () -> new PokerTableBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_GREEN).strength(2.0F).sound(SoundType.WOOD).noOcclusion().ignitedByLava()));
    public static final DeferredBlock<PlaymatBlock> PLAYMAT = BLOCKS.register("playmat",
            () -> new PlaymatBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_GREEN).strength(0.1F).sound(SoundType.WOOL).noOcclusion().ignitedByLava()
                    .pushReaction(PushReaction.DESTROY)));

    public static final DeferredItem<BlockItem> POKER_TABLE_ITEM = ITEMS.registerSimpleBlockItem(POKER_TABLE);
    public static final DeferredItem<BlockItem> PLAYMAT_ITEM = ITEMS.registerSimpleBlockItem(PLAYMAT);
    public static final DeferredItem<DeckItem> DECK = ITEMS.registerItem("deck", DeckItem::new, new Item.Properties().stacksTo(1));
    /**
     * A blank card showing only its back: playable as it is, dyed for another back, and cut into any other card in a
     * stonecutter (which keeps the back). Unlike named cards it stacks.
     */
    public static final DeferredItem<CardItem> CARD_STOCK = ITEMS.registerItem("card_stock", CardItem::new, new Item.Properties().stacksTo(16));
    /** The 52 standard cards in fresh-deck order, e.g. tabletop:ace_of_spades (see {@link Card}). */
    public static final List<DeferredItem<CardItem>> STANDARD_CARDS = IntStream.range(0, Card.COUNT)
            .mapToObj(i -> ITEMS.registerItem(Card.id(i), CardItem::new, new Item.Properties().stacksTo(1)))
            .toList();
    /** The jokers, tabletop:joker_1 to joker_4 (see {@link Card#jokerId}). */
    public static final List<DeferredItem<CardItem>> JOKERS = IntStream.range(0, Card.JOKERS)
            .mapToObj(i -> ITEMS.registerItem(Card.jokerId(i), CardItem::new, new Item.Properties().stacksTo(1)))
            .toList();
    /** One chip item per value in {@link ChipItem#VALUES}, e.g. tabletop:chip_25. */
    public static final List<DeferredItem<ChipItem>> CHIPS = Arrays.stream(ChipItem.VALUES)
            .mapToObj(value -> ITEMS.registerItem("chip_" + value, ChipItem::new))
            .toList();
    public static final DeferredItem<TokenItem> SMALL_BLIND = ITEMS.registerItem("small_blind", TokenItem::new, new Item.Properties().stacksTo(16));
    public static final DeferredItem<TokenItem> BIG_BLIND = ITEMS.registerItem("big_blind", TokenItem::new, new Item.Properties().stacksTo(16));
    public static final DeferredItem<TokenItem> DEALER_BUTTON = ITEMS.registerItem("dealer_button", TokenItem::new, new Item.Properties().stacksTo(16));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PlaySurfaceBlockEntity>> PLAY_SURFACE_ENTITY = BLOCK_ENTITIES.register("play_surface",
            () -> BlockEntityType.Builder.of(PlaySurfaceBlockEntity::new, POKER_TABLE.get(), PLAYMAT.get()).build(null));

    public static final DeferredHolder<RecipeSerializer<?>, SimpleCraftingRecipeSerializer<CombineCardsRecipe>> COMBINE_CARDS = RECIPE_SERIALIZERS.register("combine_cards",
            () -> new SimpleCraftingRecipeSerializer<>(CombineCardsRecipe::new));

    /** Stonecutter recipes for cards: like vanilla's, but the card keeps the back of the card stock it was cut from. */
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<CardCuttingRecipe>> CARD_CUTTING = RECIPE_SERIALIZERS.register("card_cutting",
            () -> new SingleItemRecipe.Serializer<CardCuttingRecipe>(CardCuttingRecipe::new) {});
    public static final DeferredHolder<RecipeSerializer<?>, SimpleCraftingRecipeSerializer<ApplyCardBackRecipe>> APPLY_CARD_BACK = RECIPE_SERIALIZERS.register("apply_card_back",
            () -> new SimpleCraftingRecipeSerializer<>(ApplyCardBackRecipe::new));

    public static final DeferredHolder<MenuType<?>, MenuType<DeckMenu>> DECK_MENU = MENUS.register("deck",
            () -> IMenuTypeExtension.create(DeckMenu::fromNetwork));

    static {
        TABS.register("tabletop", () -> CreativeModeTab.builder()
                .title(Component.translatable("itemGroup.tabletop"))
                .icon(() -> new ItemStack(DECK.get()))
                .displayItems((params, output) -> {
                    output.accept(POKER_TABLE_ITEM.get());
                    output.accept(PLAYMAT_ITEM.get());
                    CHIPS.forEach(chip -> output.accept(chip.get()));
                    output.accept(SMALL_BLIND.get());
                    output.accept(BIG_BLIND.get());
                    output.accept(DEALER_BUTTON.get());
                    // A fresh deck and card stock in each of Tabletop's back colours, one per dye.
                    for (DyeColor color : DyeColor.values()) {
                        ItemStack deck = new ItemStack(DECK.get());
                        TabletopApi.setBack(deck, id(color.getName()));
                        output.accept(deck);
                    }
                    for (DyeColor color : DyeColor.values()) {
                        output.accept(CardItem.stack(CARD_STOCK.get(), true, Backing.of(id(color.getName()))));
                    }
                    STANDARD_CARDS.forEach(card -> output.accept(card.get()));
                    JOKERS.forEach(card -> output.accept(card.get()));
                })
                .build());
    }

    public Tabletop(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        COMPONENTS.register(modBus);
        TABS.register(modBus);
        RECIPE_SERIALIZERS.register(modBus);
        MENUS.register(modBus);
        modBus.addListener(Tabletop::registerPayloads);
        modBus.addListener(Tabletop::registerDataMaps);
        modBus.addListener(Tabletop::addSurfaceBlocks);
        NeoForge.EVENT_BUS.addListener(InventoryClicks::onStackedOnOther);
        NeoForge.EVENT_BUS.addListener(CardLabels::onAnvilUpdate);
    }

    /** Any mod's play surface block gets the play surface block entity, so extending the block is all a new surface needs. */
    private static void addSurfaceBlocks(BlockEntityTypeAddBlocksEvent event) {
        Block[] surfaces = BuiltInRegistries.BLOCK.stream().filter(block -> block instanceof PlaySurfaceBlock).toArray(Block[]::new);
        event.modify(PLAY_SURFACE_ENTITY.get(), surfaces);
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToServer(TableActionPayload.TYPE, TableActionPayload.STREAM_CODEC, TableActions::handle);
        registrar.playToServer(FlipHeldPayload.TYPE, FlipHeldPayload.STREAM_CODEC, TableActions::flipHeld);
        registrar.playToServer(ShuffleDeckPayload.TYPE, ShuffleDeckPayload.STREAM_CODEC, TableActions::shuffleHeld);
        registrar.playToServer(PeekPayload.TYPE, PeekPayload.STREAM_CODEC, TableActions::peek);
    }

    private static void registerDataMaps(RegisterDataMapTypesEvent event) {
        event.register(TabletopApi.CHIP_VALUE);
        event.register(TabletopApi.CARD_SORT);
        event.register(TabletopApi.CARD_BACK);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }
}
