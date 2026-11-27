package dev.noclip.backrooms.level0.film;

import dev.noclip.backrooms.BackroomsMod;
import dev.noclip.backrooms.level0.film.FilmPropBlock.Mount;
import dev.noclip.backrooms.props.BackroomsProps;
import dev.noclip.backrooms.props.MultiPartPropBlock;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityType;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

/**
 * The objects of the film's Level 0 (A24's Backrooms, 2026): the 1991 bargain furniture store the Backrooms copied
 * without understanding it, and the VHS tapes of the games. Plan: docs/企劃書/5-樓層/Level0-大廳/README.md, section 8.
 *
 * Every prop is a Blender mesh with a baked texture (tools/film/), drawn through the props' shared block entity
 * (props.PropBlockEntity). Multi-block ones use the props' MultiPartPropBlock (every cell solid, breaking one breaks
 * all); the rest are FilmPropBlock (on the floor, flat, on a wall, from the ceiling). Their ids, sizes and drops are
 * listed once in tools/film/film_list.py. Claude makes the objects; Level 0's generator places them (Gemini).
 */
public final class FilmProps {
	private static final Map<String, Block> BLOCKS = new LinkedHashMap<>();

	/** A tape from the floor: picked up and sold to the buyer. Its label is a lore line set by the loot table. */
	public static final Item VHS_TAPE = item("vhs_tape");

	public static final Block SOFA_TAN = multi("film_sofa_tan", 3, 1, 1, SoundType.WOOL, 1.4F, 0);
	public static final Block SOFA_BROWN = multi("film_sofa_brown", 3, 1, 1, SoundType.WOOL, 1.4F, 0);
	public static final Block SOFA_TROPICAL = multi("film_sofa_tropical", 3, 1, 1, SoundType.WOOL, 1.4F, 0);
	public static final Block SOFA_QUILTED = multi("film_sofa_quilted", 3, 1, 1, SoundType.WOOL, 1.4F, 0);
	public static final Block LOVESEAT_FLORAL = multi("film_loveseat_floral", 2, 1, 1, SoundType.WOOL, 1.4F, 0);
	public static final Block RECLINER_LEATHER = single("film_recliner_leather", Mount.FLOOR, SoundType.WOOL, 1.4F, 0);
	public static final Block RECLINER_MAUVE = single("film_recliner_mauve", Mount.FLOOR, SoundType.WOOL, 1.4F, 0);
	public static final Block RECLINER_TAN = single("film_recliner_tan", Mount.FLOOR, SoundType.WOOL, 1.4F, 0);
	public static final Block RECLINER_BLUEGREY = single("film_recliner_bluegrey", Mount.FLOOR, SoundType.WOOL, 1.4F, 0);
	public static final Block SWIVEL_CHAIR_TEAL = single("film_swivel_chair_teal", Mount.FLOOR, SoundType.WOOL, 1.4F, 0);
	public static final Block ARMCHAIR_WOOD_TRIM = single("film_armchair_wood_trim", Mount.FLOOR, SoundType.WOOL, 1.4F, 0);
	public static final Block ACCENT_CHAIR_DIAMOND = single("film_accent_chair_diamond", Mount.FLOOR, SoundType.WOOL, 1.4F, 0);
	public static final Block OTTOMAN_DIAMOND = single("film_ottoman_diamond", Mount.SMALL, SoundType.WOOL, 1.4F, 0);
	public static final Block OTTOMAN_TUFTED = single("film_ottoman_tufted", Mount.SMALL, SoundType.WOOL, 1.4F, 0);
	public static final Block LADDERBACK_CHAIR = single("film_ladderback_chair", Mount.FLOOR, SoundType.WOOD, 1.6F, 0);
	public static final Block SCHOOL_CHAIR = single("film_school_chair", Mount.FLOOR, SoundType.METAL, 2.0F, 0);
	public static final Block RESIN_CHAIR = single("film_resin_chair", Mount.FLOOR, SoundType.STONE, 1.0F, 0);
	public static final Block WICKER_CHAIR = single("film_wicker_chair", Mount.FLOOR, SoundType.WOOD, 1.6F, 0);
	public static final Block THRONE = multi("film_throne", 1, 2, 1, SoundType.WOOD, 1.6F, 0);
	public static final Block FIVE_LEGGED_STOOL = single("film_five_legged_stool", Mount.FLOOR, SoundType.WOOD, 1.6F, 0);
	public static final Block BISTRO_SET = multi("film_bistro_set", 2, 1, 2, SoundType.METAL, 2.0F, 0);
	public static final Block DINING_SET = multi("film_dining_set", 2, 1, 2, SoundType.WOOD, 1.6F, 0);
	public static final Block COFFEE_TABLE_GLASS = single("film_coffee_table_glass", Mount.SMALL, SoundType.WOOD, 1.6F, 0);
	public static final Block TV_TRAY = single("film_tv_tray", Mount.FLOOR, SoundType.METAL, 2.0F, 0);
	public static final Block END_TABLE_LAMP = multi("film_end_table_lamp", 1, 2, 1, SoundType.GLASS, 1.0F, 11);
	public static final Block ARMOIRE = multi("film_armoire", 1, 2, 1, SoundType.WOOD, 1.6F, 0);
	public static final Block ENTERTAINMENT_CENTER = multi("film_entertainment_center", 2, 2, 1, SoundType.GLASS, 1.6F, 4);
	public static final Block BOOKCASE_HUTCH = multi("film_bookcase_hutch", 1, 2, 1, SoundType.WOOD, 1.6F, 0);
	public static final Block DRESSER = multi("film_dresser", 2, 2, 1, SoundType.WOOD, 1.6F, 0);
	public static final Block NIGHTSTAND = single("film_nightstand", Mount.FLOOR, SoundType.GLASS, 1.0F, 9);
	public static final Block CHECKOUT_COUNTER = multi("film_checkout_counter", 2, 1, 1, SoundType.METAL, 2.2F, 2);
	public static final Block SHOWROOM_BED = multi("film_showroom_bed", 2, 2, 2, SoundType.WOOL, 1.4F, 0);
	public static final Block BARE_MATTRESS = single("film_bare_mattress", Mount.LOW, SoundType.WOOL, 1.4F, 0);
	public static final Block MATTRESS_STACK = multi("film_mattress_stack", 2, 2, 2, SoundType.WOOL, 1.4F, 0);
	public static final Block TORCHIERE = multi("film_torchiere", 1, 2, 1, SoundType.GLASS, 1.0F, 13);
	public static final Block CHANDELIER = single("film_chandelier", Mount.CEILING, SoundType.GLASS, 1.0F, 13);
	public static final Block WATERCOLOR_PRINT = single("film_watercolor_print", Mount.WALL, SoundType.WOOL, 0.6F, 0);
	public static final Block WICKER_SCREEN = multi("film_wicker_screen", 2, 2, 1, SoundType.WOOD, 1.6F, 0);
	public static final Block CARPET_ROLLS = multi("film_carpet_rolls", 2, 3, 1, SoundType.WOOL, 0.8F, 0);
	public static final Block CARDBOARD_STANDEE = multi("film_cardboard_standee", 1, 2, 1, SoundType.WOOL, 0.6F, 0);
	public static final Block SHIP_WHEEL = multi("film_ship_wheel", 1, 2, 1, SoundType.WOOD, 1.6F, 0);
	public static final Block COAT_RACK = multi("film_coat_rack", 1, 2, 1, SoundType.WOOD, 1.6F, 0);
	public static final Block ORIENTAL_RUG = single("film_oriental_rug", Mount.LOW, SoundType.WOOL, 0.8F, 0);
	public static final Block PATIO_UMBRELLA_SET = multi("film_patio_umbrella_set", 2, 3, 2, SoundType.STONE, 1.0F, 0);
	public static final Block SUN_LOUNGER = multi("film_sun_lounger", 1, 1, 2, SoundType.STONE, 1.0F, 0);
	public static final Block SIGN_SALE75 = single("film_sign_sale75", Mount.CEILING, SoundType.WOOL, 0.6F, 0);
	public static final Block SIGN_MUST_GO = single("film_sign_must_go", Mount.CEILING, SoundType.WOOL, 0.6F, 0);
	public static final Block SIGN_ON_SALE = single("film_sign_on_sale", Mount.CEILING, SoundType.WOOL, 0.6F, 0);
	public static final Block BANNER_NO_CREDIT = single("film_banner_no_credit", Mount.WALL, SoundType.WOOL, 0.6F, 0);
	public static final Block SIGN_PIRATE = single("film_sign_pirate", Mount.WALL, SoundType.WOOL, 0.6F, 0);
	public static final Block FURNITURE_PILE = multi("film_furniture_pile", 3, 4, 3, SoundType.WOOD, 2.4F, 0);
	public static final Block CHAIR_PILE = multi("film_chair_pile", 2, 5, 2, SoundType.METAL, 2.4F, 0);
	public static final Block SOFA_PILE = multi("film_sofa_pile", 2, 3, 2, SoundType.WOOL, 1.4F, 0);
	public static final Block OTTOMAN_PILE = multi("film_ottoman_pile", 2, 3, 2, SoundType.WOOL, 1.4F, 0);
	public static final Block BOX_STACK = single("film_box_stack", Mount.FLOOR, SoundType.WOOL, 0.6F, 0);
	public static final Block FAX_MACHINE = single("film_fax_machine", Mount.SMALL, SoundType.GLASS, 1.6F, 0);
	public static final Block TV_VCR = single("film_tv_vcr", Mount.FLOOR, SoundType.GLASS, 1.4F, 4);
	public static final Block TV_WALL = multi("film_tv_wall", 2, 2, 1, SoundType.GLASS, 1.6F, 6);
	public static final Block MANNEQUIN = multi("film_mannequin", 1, 2, 1, SoundType.STONE, 1.0F, 0);
	public static final Block MANNEQUIN_WAVING = multi("film_mannequin_waving", 1, 2, 1, SoundType.STONE, 1.0F, 0);
	public static final Block LAUNDRY_PILE = single("film_laundry_pile", Mount.SMALL, SoundType.WOOL, 0.4F, 0);
	public static final Block LAUNDRY_BASKET = single("film_laundry_basket", Mount.SMALL, SoundType.WOOL, 0.4F, 0);
	public static final Block VHS_TAPE_ON_FLOOR = single("film_vhs_tape", Mount.LOW, SoundType.STONE, 0.2F, 0);
	public static final Block VHS_SCATTER = single("film_vhs_scatter", Mount.LOW, SoundType.STONE, 0.3F, 0);
	public static final Block VHS_PILE_SMALL = single("film_vhs_pile_small", Mount.SMALL, SoundType.STONE, 0.6F, 0);
	public static final Block VHS_PILE_LARGE = multi("film_vhs_pile_large", 2, 2, 2, SoundType.STONE, 1.2F, 0);
	public static final Block VHS_BOX = single("film_vhs_box", Mount.SMALL, SoundType.WOOD, 0.6F, 0);
	public static final Block VHS_SHELF = multi("film_vhs_shelf", 1, 2, 1, SoundType.WOOD, 1.4F, 0);

	private FilmProps() {
	}

	public static void register() {
		// The props' block entity was built with Gemini's list; let it live in these blocks too.
		FabricBlockEntityType type = (FabricBlockEntityType) (Object) BackroomsProps.PROP_BLOCK_ENTITY;
		for (Block block : BLOCKS.values()) type.addValidBlock(block);
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(output -> {
			for (Block block : BLOCKS.values()) output.accept(block);
		});
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.INGREDIENTS).register(output -> output.accept(VHS_TAPE));
	}

	/** Every film prop by id ("film_sofa_tan" ...), in the order of tools/film/film_list.py. */
	public static Map<String, Block> all() {
		return Collections.unmodifiableMap(BLOCKS);
	}

	private static Block multi(String id, int width, int height, int depth, SoundType sound, float strength, int light) {
		return block(id, p -> new MultiPartPropBlock(width, height, depth, p), sound, strength, light);
	}

	private static Block single(String id, Mount mount, SoundType sound, float strength, int light) {
		return block(id, p -> new FilmPropBlock(mount, p), sound, strength, light);
	}

	private static Block block(String id, Function<BlockBehaviour.Properties, Block> factory, SoundType sound, float strength, int light) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, BackroomsMod.id(id));
		BlockBehaviour.Properties properties = BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).sound(sound)
				.strength(strength, strength * 1.5F).lightLevel(s -> light).noOcclusion().setId(key);
		Block block = Registry.register(BuiltInRegistries.BLOCK, key, factory.apply(properties));
		ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, BackroomsMod.id(id));
		Registry.register(BuiltInRegistries.ITEM, itemKey, new BlockItem(block, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix()));
		BLOCKS.put(id, block);
		return block;
	}

	private static Item item(String id) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, BackroomsMod.id(id));
		return Registry.register(BuiltInRegistries.ITEM, key, new Item(new Item.Properties().setId(key)));
	}
}
