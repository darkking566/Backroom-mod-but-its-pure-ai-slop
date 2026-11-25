package dev.noclip.backrooms.level0p1;

import dev.noclip.backrooms.core.LevelExitBlock;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.object.builder.v1.block.type.BlockSetTypeBuilder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Every block of Level 0.1, Zenith Station. The zenith_ prefix is not used: the level's package is the namespace. */
public final class ZenithBlocks {
	static final List<Block> ALL = new ArrayList<>();

	private static final VoxelShape FULL = Block.box(0, 0, 0, 16, 16, 16);
	private static final VoxelShape BED = Block.box(0, 0, 0, 16, 9, 16);
	private static final VoxelShape TREADMILL = Block.box(0, 0, 0, 16, 7, 16);
	private static final VoxelShape WEIGHTS = Block.box(1, 0, 1, 15, 13, 15);
	private static final VoxelShape PLANTER = Block.box(0, 0, 0, 16, 10, 16);
	private static final VoxelShape BENCH = Block.box(1, 0, 3, 15, 8, 13);
	private static final VoxelShape TABLE = Block.box(0, 0, 0, 16, 13, 16);
	private static final VoxelShape CATWALK = Block.box(0, 12, 0, 16, 16, 16);
	private static final VoxelShape ORB = Block.box(4, 4, 4, 12, 12, 12);
	private static final VoxelShape DESK = Block.box(0, 0, 0, 16, 13, 16);
	private static final VoxelShape BENCH_PRESS_SHAPE = Block.box(0, 0, 1, 16, 15, 15);
	private static final VoxelShape THIN = Block.box(2, 2, 2, 14, 14, 14);
	private static final VoxelShape COLUMN = Block.box(3, 0, 3, 13, 16, 13);
	private static final VoxelShape MATS = Block.box(1, 0, 1, 15, 6, 15);
	private static final VoxelShape TETHER = Block.box(2, 0, 2, 14, 16, 14);

	private static final BlockSetType STATION_DOOR = BlockSetTypeBuilder.copyOf(BlockSetType.IRON).openableByHand(true)
			.register(Level0p1.id("station_door"));

	// The shell of the station. Hull and skin cannot be broken: nothing gets out.
	public static final Block HULL_PLATE = register("hull_plate", fixed(MapColor.METAL));
	public static final Block HULL_SEAM = register("hull_seam", fixed(MapColor.METAL));
	public static final Block INNER_HULL_PLATE = register("inner_hull_plate", fixed(MapColor.SNOW));
	public static final Block OBSERVATION_GLASS = register("observation_glass", fixed(MapColor.COLOR_LIGHT_BLUE).sound(SoundType.GLASS)
			.noOcclusion().isViewBlocking((s, l, p) -> false).isSuffocating((s, l, p) -> false).lightLevel(s -> 3), TransparentBlock::new);
	public static final Block BULKHEAD = register("bulkhead", steel(MapColor.COLOR_GRAY, 6.0F));

	// Decks
	public static final Block DECK_FLOOR = register("deck_floor", steel(MapColor.SNOW, 3.0F));
	public static final Block CORRIDOR_FLOOR = register("station_corridor_floor", steel(MapColor.COLOR_LIGHT_GRAY, 3.0F));
	public static final Block CORRIDOR_FLOOR_STRIPE = register("station_corridor_floor_stripe", steel(MapColor.COLOR_LIGHT_GRAY, 3.0F));
	public static final Block QUARTERS_CARPET = register("quarters_carpet", Properties.of().mapColor(MapColor.COLOR_BLUE).strength(1.0F).sound(SoundType.WOOL));
	public static final Block KITCHEN_TILE = register("kitchen_tile", steel(MapColor.SNOW, 2.5F).sound(SoundType.STONE));
	public static final Block GYM_MAT = register("gym_mat", Properties.of().mapColor(MapColor.COLOR_BLUE).strength(1.0F).sound(SoundType.WOOL));
	public static final Block GARDEN_FLOOR = register("garden_floor", Properties.of().mapColor(MapColor.COLOR_GREEN).strength(1.0F).sound(SoundType.MOSS));
	public static final Block WORN_FLOOR = register("worn_floor", steel(MapColor.COLOR_GRAY, 3.0F));
	public static final Block HAZARD_FLOOR = register("hazard_floor", steel(MapColor.COLOR_YELLOW, 3.0F));
	public static final Block DECK_CEILING = register("deck_ceiling", steel(MapColor.SNOW, 3.0F));
	public static final Block CEILING_LIGHT = register("ceiling_light", steel(MapColor.SNOW, 1.0F).sound(SoundType.GLASS).lightLevel(s -> 13));
	public static final Block DEAD_CEILING_LIGHT = register("dead_ceiling_light", steel(MapColor.COLOR_GRAY, 1.0F).sound(SoundType.GLASS));

	// Walls and doors
	public static final Block WALL_PANEL = register("wall_panel", steel(MapColor.SNOW, 4.0F));
	public static final Block QUARTERS_WALL = register("quarters_wall", steel(MapColor.COLOR_LIGHT_BLUE, 4.0F));
	public static final Block KITCHEN_WALL = register("kitchen_wall", steel(MapColor.SNOW, 4.0F).sound(SoundType.STONE));
	public static final Block GYM_WALL = register("gym_wall", steel(MapColor.COLOR_BLUE, 3.0F).sound(SoundType.WOOL));
	public static final Block DAMAGED_WALL = register("damaged_wall", steel(MapColor.COLOR_BLACK, 4.0F));
	public static final Block FROST_WALL = register("frost_wall", steel(MapColor.ICE, 4.0F).sound(SoundType.GLASS));
	public static final Block FROST_FLOOR = register("frost_floor", steel(MapColor.ICE, 3.0F).sound(SoundType.GLASS));
	public static final Block SCORCHED_FLOOR = register("scorched_floor", steel(MapColor.COLOR_BLACK, 3.0F).lightLevel(s -> 2));
	public static final Block FROST_CEILING = register("frost_ceiling", steel(MapColor.ICE, 2.0F).sound(SoundType.GLASS));
	public static final Block SCORCHED_CEILING = register("scorched_ceiling", steel(MapColor.COLOR_BLACK, 2.0F));
	public static final Block SCORCHED_WALL = register("scorched_wall", steel(MapColor.COLOR_BLACK, 4.0F).lightLevel(s -> 3));
	public static final Block STATION_DOOR_BLOCK = register("station_door", steel(MapColor.SNOW, 5.0F).noOcclusion()
			.pushReaction(PushReaction.DESTROY), p -> new ZenithDoorBlock(STATION_DOOR, p));

	// Stairs, rails, lights
	public static final Block STATION_STAIRS = register("station_stairs", steel(MapColor.COLOR_GRAY, 4.0F), p -> new StairBlock(Blocks.IRON_BLOCK.defaultBlockState(), p));
	public static final Block STAIR_CORE = register("stair_core", steel(MapColor.COLOR_GRAY, 4.0F).lightLevel(s -> 9));
	public static final Block DEAD_STAIR_CORE = register("dead_stair_core", steel(MapColor.COLOR_GRAY, 4.0F));
	public static final Block SAFETY_RAIL = register("safety_rail", steel(MapColor.COLOR_GRAY, 2.0F).noOcclusion(), FenceBlock::new);
	public static final Block RAIL_LAMP = register("rail_lamp", steel(MapColor.COLOR_LIGHT_BLUE, 2.0F).noOcclusion().lightLevel(s -> 13), FenceBlock::new);
	public static final Block GLOW_ORB = register("glow_orb", Properties.of().mapColor(MapColor.COLOR_LIGHT_BLUE).strength(0.5F).sound(SoundType.GLASS)
			.noOcclusion().lightLevel(s -> 15), p -> new ZenithShapedBlock(p, ORB, false));

	// Furniture
	public static final Block CREW_BED = register("crew_bed", steel(MapColor.COLOR_BLUE, 1.0F).sound(SoundType.WOOL).noOcclusion(), p -> new ZenithFacingBlock(p, BED));
	public static final Block CREW_LOCKER = register("crew_locker", steel(MapColor.COLOR_LIGHT_GRAY, 3.0F), p -> new ZenithFacingBlock(p, FULL));
	public static final Block KITCHEN_COUNTER = register("kitchen_counter", steel(MapColor.COLOR_LIGHT_GRAY, 2.5F), p -> new ZenithFacingBlock(p, FULL));
	public static final Block STOVE = register("stove", steel(MapColor.COLOR_GRAY, 3.0F).lightLevel(s -> 3), p -> new ZenithFacingBlock(p, FULL));
	public static final Block TREADMILL_BLOCK = register("treadmill", steel(MapColor.COLOR_GRAY, 2.5F).noOcclusion(), p -> new ZenithFacingBlock(p, TREADMILL));
	public static final Block WEIGHT_RACK = register("weight_rack", steel(MapColor.COLOR_GRAY, 3.0F).noOcclusion(), p -> new ZenithFacingBlock(p, WEIGHTS));
	public static final Block HYDROPONIC_PLANTER = register("hydroponic_planter", steel(MapColor.COLOR_GREEN, 2.0F).noOcclusion().lightLevel(s -> 6),
			p -> new ZenithShapedBlock(p, PLANTER, true));
	public static final Block CONSOLE = register("console", steel(MapColor.COLOR_GRAY, 3.0F).lightLevel(s -> 7), p -> new ZenithFacingBlock(p, FULL));
	public static final Block BROKEN_CONSOLE = register("broken_console", steel(MapColor.COLOR_BLACK, 3.0F), p -> new ZenithFacingBlock(p, FULL));
	public static final Block SERVER_RACK = register("server_rack", steel(MapColor.COLOR_BLACK, 4.0F).lightLevel(s -> 4), p -> new ZenithFacingBlock(p, FULL));
	public static final Block SUPPLY_CRATE = register("station_crate", steel(MapColor.COLOR_GRAY, 2.0F));
	public static final Block HANGING_CABLES = register("hanging_cables", Properties.of().mapColor(MapColor.COLOR_BLACK).strength(0.3F).sound(SoundType.CHAIN)
			.noOcclusion().noCollision().lightLevel(s -> 6), p -> new ZenithShapedBlock(p, FULL, false)); // live wires spark: the only light in a wreck
	public static final Block CREW_TABLE = register("crew_table", steel(MapColor.COLOR_LIGHT_GRAY, 2.0F).noOcclusion(), p -> new ZenithShapedBlock(p, TABLE, true));
	public static final Block STATION_BENCH = register("station_bench", steel(MapColor.COLOR_LIGHT_GRAY, 2.0F).noOcclusion(), p -> new ZenithShapedBlock(p, BENCH, true));

	// What makes the rooms look lived in: bedrooms, galleys, gyms, hydroponic greenhouses.
	public static final Block CREW_DESK = register("crew_desk", steel(MapColor.WOOD, 2.0F).sound(SoundType.WOOD).noOcclusion().lightLevel(s -> 6),
			p -> new ZenithShapedBlock(p, DESK, true));
	public static final Block OPEN_WARDROBE = register("open_wardrobe", steel(MapColor.COLOR_LIGHT_GRAY, 3.0F), p -> new ZenithFacingBlock(p, FULL));
	public static final Block BUNK_BED = register("bunk_bed", steel(MapColor.COLOR_BLUE, 2.0F).sound(SoundType.WOOL).noOcclusion(), p -> new ZenithFacingBlock(p, FULL));
	public static final Block WALL_FRAME = register("wall_frame", steel(MapColor.WOOD, 0.5F).sound(SoundType.WOOD).noOcclusion().noCollision(), p -> new ZenithFacingBlock(p, THIN));
	public static final Block PERSONAL_ITEMS = register("personal_items", steel(MapColor.COLOR_RED, 0.3F).sound(SoundType.WOOL).noOcclusion().noCollision(),
			p -> new ZenithShapedBlock(p, THIN, false));
	public static final Block KITCHEN_SINK = register("kitchen_sink", steel(MapColor.COLOR_LIGHT_GRAY, 2.5F), p -> new ZenithFacingBlock(p, FULL));
	public static final Block FRIDGE = register("fridge", steel(MapColor.SNOW, 3.0F), p -> new ZenithFacingBlock(p, FULL));
	public static final Block HANGING_POTS = register("hanging_pots", steel(MapColor.COLOR_GRAY, 0.5F).sound(SoundType.CHAIN).noOcclusion().noCollision(),
			p -> new ZenithFacingBlock(p, THIN));
	public static final Block DISH_STACK = register("dish_stack", steel(MapColor.SNOW, 0.3F).sound(SoundType.GLASS).noOcclusion().noCollision(),
			p -> new ZenithShapedBlock(p, THIN, false));
	public static final Block BENCH_PRESS = register("bench_press", steel(MapColor.COLOR_GRAY, 3.0F).noOcclusion(), p -> new ZenithFacingBlock(p, BENCH_PRESS_SHAPE));
	public static final Block PUNCHING_BAG = register("punching_bag", steel(MapColor.COLOR_RED, 2.0F).sound(SoundType.WOOL).noOcclusion(), p -> new ZenithShapedBlock(p, COLUMN, true));
	public static final Block WALL_MIRROR = register("wall_mirror", steel(MapColor.COLOR_LIGHT_BLUE, 0.5F).sound(SoundType.GLASS).noOcclusion().noCollision(),
			p -> new ZenithFacingBlock(p, THIN));
	public static final Block WATER_DISPENSER = register("water_dispenser", steel(MapColor.SNOW, 2.0F).noOcclusion(), p -> new ZenithFacingBlock(p, COLUMN));
	public static final Block MAT_STACK = register("mat_stack", steel(MapColor.COLOR_BLUE, 1.0F).sound(SoundType.WOOL).noOcclusion(), p -> new ZenithShapedBlock(p, MATS, true));
	public static final Block HYDROPONIC_RACK = register("hydroponic_rack", steel(MapColor.COLOR_GREEN, 2.0F).noOcclusion().lightLevel(s -> 7),
			p -> new ZenithShapedBlock(p, FULL, true));
	public static final Block GROW_LIGHT = register("grow_light", steel(MapColor.COLOR_PINK, 1.0F).sound(SoundType.GLASS).noOcclusion().noCollision().lightLevel(s -> 13),
			p -> new ZenithShapedBlock(p, Block.box(1, 14, 1, 15, 16, 15), false));
	public static final Block WATER_TANK = register("water_tank", steel(MapColor.COLOR_LIGHT_BLUE, 3.0F).noOcclusion(), p -> new ZenithFacingBlock(p, Block.box(2, 0, 2, 14, 16, 14)));
	public static final Block HANGING_VINES = register("hanging_vines", Properties.of().mapColor(MapColor.COLOR_GREEN).strength(0.2F).sound(SoundType.VINE)
			.noOcclusion().noCollision(), p -> new ZenithShapedBlock(p, FULL, false));
	public static final Block CEILING_SPEAKER = register("ceiling_speaker", steel(MapColor.COLOR_LIGHT_GRAY, 2.0F));
	public static final Block ICICLES = register("icicles", Properties.of().mapColor(MapColor.ICE).strength(0.2F).sound(SoundType.GLASS)
			.noOcclusion().noCollision(), p -> new ZenithShapedBlock(p, FULL, false));
	public static final Block ICE_DRIFT = register("ice_drift", Properties.of().mapColor(MapColor.ICE).strength(0.3F).sound(SoundType.GLASS)
			.noOcclusion().noCollision(), p -> new ZenithShapedBlock(p, Block.box(1, 0, 1, 15, 5, 15), false));
	public static final Block SLAG_PILE = register("slag_pile", Properties.of().mapColor(MapColor.COLOR_BLACK).strength(0.5F).sound(SoundType.BASALT)
			.noOcclusion().noCollision().lightLevel(s -> 5), p -> new ZenithShapedBlock(p, Block.box(2, 0, 2, 14, 4, 14), false));
	public static final Block HEAT_VENT = register("heat_vent", steel(MapColor.COLOR_BLACK, 2.0F).noOcclusion().noCollision().lightLevel(s -> 8),
			p -> new ZenithShapedBlock(p, Block.box(1, 0, 1, 15, 2, 15), false));
	public static final Block COUNTDOWN_SCREEN = register("countdown_screen", steel(MapColor.COLOR_BLACK, 1.0F).noOcclusion().noCollision().lightLevel(s -> 6),
			p -> new ZenithFacingBlock(p, THIN));
	public static final Block BRIDGE_COIL = register("bridge_coil", steel(MapColor.COLOR_PURPLE, 3.0F).noOcclusion().lightLevel(s -> 12),
			p -> new ZenithShapedBlock(p, Block.box(3, 0, 3, 13, 16, 13), true));
	public static final Block RESEARCH_NOTES = register("research_notes", steel(MapColor.SNOW, 0.2F).sound(SoundType.WOOL).noOcclusion().noCollision(),
			p -> new ZenithNotesBlock(p, Block.box(2, 0, 2, 14, 2, 14)));
	public static final Block LAB_COAT = register("lab_coat", steel(MapColor.SNOW, 0.2F).sound(SoundType.WOOL).noOcclusion().noCollision(), p -> new ZenithFacingBlock(p, THIN));

	// Ways out. Neither can be broken or carried off.
	public static final Block YELLOW_DOORWAY = register("yellow_doorway", Properties.of().mapColor(MapColor.COLOR_YELLOW).strength(-1.0F, 3600000.0F)
			.noLootTable().sound(SoundType.WOOD).lightLevel(s -> 10), p -> new ZenithPortalBlock(p, LevelExitBlock.leave("0.1")));
	/** The way in from Level 0: a white steel hatch set into a wall. */
	public static final Block STATION_HATCH = register("station_hatch", Properties.of().mapColor(MapColor.SNOW).strength(-1.0F, 3600000.0F)
			.noLootTable().sound(SoundType.METAL).lightLevel(s -> 4), p -> new StationHatchBlock(p, LevelExitBlock.to(Level0p1.KEY)));
	public static final Block DOORWAY_FRAME = register("doorway_frame", fixed(MapColor.COLOR_YELLOW));
	public static final Block STATIC_TEAR = register("static_tear", Properties.of().mapColor(MapColor.COLOR_GRAY).strength(-1.0F, 3600000.0F)
			.noLootTable().sound(SoundType.AMETHYST).lightLevel(s -> 9), p -> new LevelExitBlock(p, LevelExitBlock.cutOut("0.1")));

	// The middle
	public static final Block CATWALK_GRATING = register("catwalk_grating", steel(MapColor.COLOR_GRAY, 4.0F).noOcclusion(), p -> new ZenithShapedBlock(p, CATWALK, true));
	public static final Block TRUSS = register("truss", steel(MapColor.COLOR_GRAY, 4.0F).noOcclusion());
	public static final Block TETHER_CABLE = register("tether_cable", steel(MapColor.COLOR_BLACK, 4.0F).noOcclusion(), p -> new ZenithShapedBlock(p, TETHER, true));
	public static final Block GIRDER = register("girder", steel(MapColor.COLOR_GRAY, 4.0F));
	public static final Block GIRDER_LAMP = register("girder_lamp", steel(MapColor.COLOR_LIGHT_BLUE, 4.0F).lightLevel(s -> 15));
	public static final Block SINGULARITY_CORE = register("singularity_core", fixed(MapColor.SNOW).lightLevel(s -> 15));
	public static final Block SINGULARITY_SHELL = register("singularity_shell", fixed(MapColor.COLOR_PURPLE).lightLevel(s -> 15));
	public static final Block PLASMA = register("plasma", fixed(MapColor.COLOR_ORANGE).lightLevel(s -> 15), ZenithPlasmaBlock::new);

	private ZenithBlocks() {
	}

	static void register() {
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.BUILDING_BLOCKS).register(output -> ALL.forEach(output::accept));
	}

	/** The shell and the singularity: not breakable, not explodable. */
	private static Properties fixed(MapColor color) {
		return Properties.of().mapColor(color).strength(-1.0F, 3600000.0F).noLootTable().sound(SoundType.METAL);
	}

	private static Properties steel(MapColor color, float strength) {
		return Properties.of().mapColor(color).strength(strength, 8.0F).sound(SoundType.METAL);
	}

	private static Block register(String name, Properties properties) {
		return register(name, properties, Block::new);
	}

	private static Block register(String name, Properties properties, Function<Properties, Block> factory) {
		Identifier id = Level0p1.id(name);
		Block block = Blocks.register(ResourceKey.create(Registries.BLOCK, id), factory, properties);
		ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, id);
		BlockItem item = new BlockItem(block, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix());
		item.registerBlocks(Item.BY_BLOCK, item);
		Registry.register(BuiltInRegistries.ITEM, itemKey, item);
		ALL.add(block);
		return block;
	}
}
