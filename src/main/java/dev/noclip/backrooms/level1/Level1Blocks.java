package dev.noclip.backrooms.level1;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.ToIntFunction;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
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
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class Level1Blocks {
	static final List<Block> ALL = new ArrayList<>();

	private static final VoxelShape LIGHT_SHAPE = Block.box(0, 9, 4.5, 16, 16, 11.5);
	private static final VoxelShape PIPE_SHAPE = Block.box(0, 10, 5.5, 16, 16, 10.5);
	private static final VoxelShape BULB_SHAPE = Block.box(5.5, 3, 5.5, 10.5, 16, 10.5);
	private static final VoxelShape DESK_SHAPE = Block.box(0, 0, 0.5, 16, 14, 15.5);
	private static final VoxelShape COMPUTER_SHAPE = Block.box(2, 0, 2, 14, 11, 14);
	private static final VoxelShape FRAME_SHAPE = Block.box(0, 0, 6.5, 16, 16, 9.5);
	private static final VoxelShape ARM_SHAPE = Block.box(0, 6.5, 6.5, 16, 9.5, 9.5);
	private static final VoxelShape GENERATOR_SHAPE = Block.box(1, 0, 2, 15, 14, 14);
	private static final VoxelShape DRUM_SHAPE = Block.box(2.5, 0, 2.5, 13.5, 15, 13.5);
	private static final VoxelShape CAGED_LAMP_SHAPE = Block.box(5, 6, 5, 11, 16, 11);

	// The parking structure
	public static final Block GARAGE_FLOOR = register("garage_floor", concrete(MapColor.STONE));
	public static final Block GARAGE_FLOOR_LINE = register("garage_floor_line", concrete(MapColor.STONE));
	public static final Block DAMP_GARAGE_FLOOR = register("damp_garage_floor", concrete(MapColor.STONE));
	public static final Block PUDDLED_GARAGE_FLOOR = register("puddled_garage_floor", concrete(MapColor.WATER));
	public static final Block GARAGE_WALL = register("garage_wall", concrete(MapColor.STONE));
	public static final Block GARAGE_PILLAR = register("garage_pillar", concrete(MapColor.STONE));
	public static final Block GARAGE_PILLAR_BASE = register("garage_pillar_base", concrete(MapColor.COLOR_YELLOW));
	public static final Block GARAGE_PILLAR_MARKED = register("garage_pillar_marked", concrete(MapColor.STONE));
	public static final Block GARAGE_CEILING = register("garage_ceiling", concrete(MapColor.STONE));
	public static final Block GARAGE_LIGHT = register("garage_light", fixture().lightLevel(lit(15)), p -> new Level1LampBlock(p, LIGHT_SHAPE));
	public static final Block BROKEN_GARAGE_LIGHT = register("broken_garage_light", fixture(), p -> new ShapedBlock(p, LIGHT_SHAPE));
	public static final Block CEILING_PIPE = register("ceiling_pipe", pipe(), p -> new ShapedBlock(p, PIPE_SHAPE));
	public static final Block LEAKING_CEILING_PIPE = register("leaking_ceiling_pipe", pipe(), p -> new LeakingPipeBlock(p, PIPE_SHAPE));
	public static final Block METAL_SHELF = register("metal_shelf", Properties.of()
			.mapColor(MapColor.METAL).strength(2.0F).sound(SoundType.METAL).noOcclusion());
	public static final Block SUPPLY_CRATE = register("supply_crate", Properties.of()
			.mapColor(MapColor.WOOD).strength(1.5F).sound(SoundType.WOOD), SupplyCrateBlock::new);

	/** A lock hanging in a room's doorway; use it and the room is shut for good. */
	public static final Block NATURAL_LOCK = register("natural_lock", Properties.of()
			.mapColor(MapColor.METAL).strength(0.5F).sound(SoundType.CHAIN).noOcclusion().noCollision().noLootTable(),
			p -> new NaturalLockBlock(p, Block.box(5, 6, 7, 11, 16, 9)));
	/** What a natural lock leaves in the doorways of its room. */
	public static final Block SEALED_DOOR = register("sealed_door", Properties.of()
			.mapColor(MapColor.METAL).strength(25.0F, 3600000.0F).sound(SoundType.METAL).noLootTable());
	/** A gauge on a pillar where the pipes crowd in toward Level 2; no item of its own. */
	public static final Block PIPE_GAUGE = registerBlockOnly("pipe_gauge", Properties.of()
			.mapColor(MapColor.COLOR_ORANGE).instabreak().noCollision().noOcclusion().noLootTable().replaceable()
			.sound(SoundType.METAL), PhosphorMarkBlock::new);
	/** Wreckage on the floor: planks, scrap and, now and then, a crowbar. Opens like a crate. */
	public static final Block DEBRIS_PILE = register("debris_pile", Properties.of()
			.mapColor(MapColor.WOOD).strength(1.0F).sound(SoundType.WOOD).noOcclusion(), SupplyCrateBlock::new);

	// The corridors
	public static final Block PAINTED_CONCRETE_WALL = register("painted_concrete_wall", concrete(MapColor.QUARTZ));
	public static final Block CORRIDOR_FLOOR = register("corridor_floor", concrete(MapColor.COLOR_LIGHT_GRAY));
	public static final Block BARE_BULB = register("bare_bulb", bulb().lightLevel(lit(12)), p -> new Level1LampBlock(p, BULB_SHAPE));
	public static final Block BROKEN_BARE_BULB = register("broken_bare_bulb", bulb(), p -> new ShapedBlock(p, BULB_SHAPE));
	public static final Block GREEN_BARE_BULB = register("green_bare_bulb", bulb().lightLevel(state -> 11), p -> new ShapedBlock(p, BULB_SHAPE));
	public static final Block EMERGENCY_SIGN = register("emergency_sign", Properties.of()
			.mapColor(MapColor.COLOR_GREEN).strength(1.0F).sound(SoundType.METAL).lightLevel(state -> 9), AxisSignBlock::new);
	public static final Block METAL_EXIT_DOOR = register("metal_exit_door", exit(), Level1DoorBlock::new);
	public static final Block METAL_EXIT_DOOR_TOP = register("metal_exit_door_top", exit(), Level1DoorBlock::new);
	public static final Block OLD_BRICK = register("old_brick", Properties.of()
			.mapColor(MapColor.TERRACOTTA_RED).strength(2.0F).sound(SoundType.STONE));
	public static final Block PADDED_WALL = register("padded_wall", Properties.of()
			.mapColor(MapColor.WOOL).strength(0.8F).sound(SoundType.WOOL));
	public static final Block OFFICE_DESK = register("office_desk", Properties.of()
			.mapColor(MapColor.WOOD).strength(1.5F).sound(SoundType.WOOD).noOcclusion(), p -> new ShapedBlock(p, DESK_SHAPE));
	public static final Block OLD_COMPUTER = register("old_computer", Properties.of()
			.mapColor(MapColor.SAND).strength(1.0F).sound(SoundType.METAL).noOcclusion(), p -> new OldComputerBlock(p, COMPUTER_SHAPE));

	// The way in, from Level 0
	public static final Block FLICKERING_WALLPAPER = register("flickering_wallpaper", Properties.of()
			.mapColor(MapColor.COLOR_YELLOW).strength(-1.0F, 3600000.0F).noLootTable().noCollision()
			.sound(SoundType.WOOD).lightLevel(state -> 6).emissiveRendering(state -> true), FlickeringWallBlock::new);

	// Ways on to other levels (Level1Exit lists where each leads)
	public static final Block WHITE_DOOR = door("white_door", Level1Exit.WHITE_DOOR, SoundType.WOOD, 0);
	public static final Block WHITE_DOOR_TOP = door("white_door_top", Level1Exit.WHITE_DOOR, SoundType.WOOD, 0);
	public static final Block REINFORCED_DOOR = door("reinforced_door", Level1Exit.REINFORCED_DOOR, SoundType.METAL, 0);
	public static final Block REINFORCED_DOOR_TOP = door("reinforced_door_top", Level1Exit.REINFORCED_DOOR, SoundType.METAL, 0);
	public static final Block AUTOMATIC_DOOR = door("automatic_door", Level1Exit.AUTOMATIC_DOOR, SoundType.GLASS, 12);
	public static final Block AUTOMATIC_DOOR_TOP = door("automatic_door_top", Level1Exit.AUTOMATIC_DOOR, SoundType.GLASS, 12);
	public static final Block PICKAXE_DOOR = door("pickaxe_door", Level1Exit.PICKAXE_DOOR, SoundType.WOOD, 0);
	public static final Block PICKAXE_DOOR_TOP = door("pickaxe_door_top", Level1Exit.PICKAXE_DOOR, SoundType.WOOD, 0);
	public static final Block CONCRETE_DOOR = door("concrete_door", Level1Exit.CONCRETE_DOOR, SoundType.STONE, 0);
	public static final Block CONCRETE_DOOR_TOP = door("concrete_door_top", Level1Exit.CONCRETE_DOOR, SoundType.STONE, 0);
	public static final Block GLASS_DOOR = door("glass_door", Level1Exit.GLASS_DOOR, SoundType.GLASS, 0);
	public static final Block GLASS_DOOR_TOP = door("glass_door_top", Level1Exit.GLASS_DOOR, SoundType.GLASS, 0);
	public static final Block SYMBOL_DOOR = door("symbol_door", Level1Exit.SYMBOL_DOOR, SoundType.METAL, 8);
	public static final Block SYMBOL_DOOR_TOP = door("symbol_door_top", Level1Exit.SYMBOL_DOOR, SoundType.METAL, 8);
	public static final Block FROZEN_DOOR = door("frozen_door", Level1Exit.FROZEN_DOOR, SoundType.METAL, 0);
	public static final Block FROZEN_DOOR_TOP = door("frozen_door_top", Level1Exit.FROZEN_DOOR, SoundType.METAL, 0);
	public static final Block BUNKER_DOOR = door("bunker_door", Level1Exit.BUNKER_DOOR, SoundType.METAL, 0);
	public static final Block BUNKER_DOOR_TOP = door("bunker_door_top", Level1Exit.BUNKER_DOOR, SoundType.METAL, 0);
	public static final Block COLD_DOOR = door("cold_door", Level1Exit.COLD_DOOR, SoundType.METAL, 3);
	public static final Block COLD_DOOR_TOP = door("cold_door_top", Level1Exit.COLD_DOOR, SoundType.METAL, 3);
	public static final Block ELEVATOR_DOOR = door("elevator_door", Level1Exit.ELEVATOR_DOOR, SoundType.METAL, 0);
	public static final Block ELEVATOR_DOOR_TOP = door("elevator_door_top", Level1Exit.ELEVATOR_DOOR, SoundType.METAL, 0);
	public static final Block WHITE_WOOD_DOOR = door("white_wood_door", Level1Exit.WHITE_WOOD_DOOR, SoundType.WOOD, 0);
	public static final Block WHITE_WOOD_DOOR_TOP = door("white_wood_door_top", Level1Exit.WHITE_WOOD_DOOR, SoundType.WOOD, 0);
	public static final Block ATTIC_HATCH = register("attic_hatch", exitProperties(MapColor.STONE, SoundType.WOOD),
			p -> new ExitBlock(p, Level1Exit.ATTIC_HATCH.level));
	public static final Block OUTDOOR_GAP = register("outdoor_gap", exitProperties(MapColor.STONE, SoundType.STONE),
			p -> new ExitBlock(p, Level1Exit.OUTDOOR_GAP.level));
	public static final Block WALL_VENT = register("wall_vent", exitProperties(MapColor.STONE, SoundType.METAL),
			p -> new ExitBlock(p, Level1Exit.WALL_VENT.level));
	public static final Block ROOT_DOOR = door("root_door", Level1Exit.ROOT_TWO, SoundType.METAL, 4);
	public static final Block ROOT_DOOR_TOP = door("root_door_top", Level1Exit.ROOT_TWO, SoundType.METAL, 4);
	public static final Block INVERTED_WALL = register("inverted_wall", exitProperties(MapColor.STONE, SoundType.STONE),
			p -> new ExitBlock(p, Level1Exit.INVERTED_WALL.level));
	public static final Block PALACE_PAINTING = register("palace_painting", exitProperties(MapColor.COLOR_PINK, SoundType.WOOD),
			p -> new ExitBlock(p, Level1Exit.PAINTING.level));
	public static final Block OUTLET_WALL = register("outlet_wall", exitProperties(MapColor.STONE, SoundType.STONE),
			p -> new ExitBlock(p, Level1Exit.OUTLETS.level));
	public static final Block CRACKED_PILLAR = register("cracked_pillar", exitProperties(MapColor.STONE, SoundType.STONE),
			p -> new ExitBlock(p, Level1Exit.CRACKED_PILLAR.level));
	public static final Block CELLAR_HATCH = register("cellar_hatch", exitProperties(MapColor.METAL, SoundType.METAL),
			p -> new ExitBlock(p, Level1Exit.CELLAR_HATCH.level));
	public static final Block DOOR_FRAME = register("standalone_door_frame", exitProperties(MapColor.WOOD, SoundType.WOOD).noOcclusion().noCollision(),
			p -> new DoorFrameBlock(p, FRAME_SHAPE, Level1Exit.DOOR_FRAME.level));
	public static final Block DOOR_FRAME_TOP = register("standalone_door_frame_top", exitProperties(MapColor.WOOD, SoundType.WOOD).noOcclusion().noCollision(),
			p -> new ShapedBlock(p, FRAME_SHAPE));
	public static final Block CONCRETE_STAIRS = register("concrete_stairs", concrete(MapColor.STONE),
			p -> new ConcreteStairsBlock(GARAGE_FLOOR.defaultBlockState(), p));
	public static final Block DARKNESS_BELOW = register("darkness_below", darkness(), p -> new DarknessBlock(p, Level1Exit.STAIRS_DOWN.level));
	public static final Block DARKNESS_ABOVE = register("darkness_above", darkness(), p -> new DarknessBlock(p, Level1Exit.STAIRS_UP.level));

	// Things to find in the halls
	public static final Block BOOTH_WALL = register("booth_wall", Properties.of()
			.mapColor(MapColor.SNOW).strength(2.0F).sound(SoundType.METAL));
	public static final Block BOOTH_ROOF = register("booth_roof", Properties.of()
			.mapColor(MapColor.METAL).strength(2.0F).sound(SoundType.METAL));
	public static final Block BOOTH_DRAWER = register("booth_drawer", Properties.of()
			.mapColor(MapColor.SNOW).strength(2.0F).sound(SoundType.METAL), BoothDrawerBlock::new);
	public static final Block BARRIER_BASE = register("barrier_base", Properties.of()
			.mapColor(MapColor.COLOR_YELLOW).strength(2.0F).sound(SoundType.METAL));
	public static final Block BARRIER_ARM = register("barrier_arm", Properties.of()
			.mapColor(MapColor.COLOR_RED).strength(0.5F).sound(SoundType.METAL).noOcclusion().noCollision(),
			p -> new ShapedBlock(p, ARM_SHAPE));
	public static final Block GENERATOR = register("generator", Properties.of()
			.mapColor(MapColor.COLOR_GREEN).strength(3.0F).sound(SoundType.METAL).noOcclusion(),
			p -> new GeneratorBlock(p, GENERATOR_SHAPE));
	public static final Block FUEL_DRUM = register("fuel_drum", Properties.of()
			.mapColor(MapColor.COLOR_RED).strength(2.0F).sound(SoundType.METAL).noOcclusion(), p -> new ShapedBlock(p, DRUM_SHAPE));
	/** Wired to the generator, so a blackout never reaches it. */
	public static final Block CAGED_LAMP = register("caged_lamp", Properties.of()
			.mapColor(MapColor.COLOR_ORANGE).strength(0.5F).sound(SoundType.LANTERN).noOcclusion().noCollision()
			.lightLevel(state -> 12), p -> new ShapedBlock(p, CAGED_LAMP_SHAPE));
	public static final Block EYED_CRATE = register("eyed_crate", Properties.of()
			.mapColor(MapColor.WOOD).strength(1.5F).sound(SoundType.WOOD), EyedCrateBlock::new);
	public static final Block SHUT_EYED_CRATE = register("shut_eyed_crate", Properties.of()
			.mapColor(MapColor.WOOD).strength(1.5F).sound(SoundType.WOOD).noLootTable());

	// Settlements
	public static final Block JERSEY_BARRIER = register("jersey_barrier", concrete(MapColor.STONE).noOcclusion(),
			p -> new AxisShapedBlock(p, Block.box(0, 0, 3, 16, 14, 13), Block.box(3, 0, 0, 13, 14, 16)));
	public static final Block FIRST_LIGHT_SIGN = register("first_light_sign", Properties.of()
			.mapColor(MapColor.COLOR_YELLOW).strength(2.0F).sound(SoundType.METAL).lightLevel(state -> 4));
	public static final Block BLINK_SIGN = register("blink_sign", Properties.of()
			.mapColor(MapColor.WOOD).strength(1.5F).sound(SoundType.WOOD));
	/** On the settlement's own power: the Flickering never reaches it. */
	public static final Block FLOODLIGHT = register("floodlight", Properties.of()
			.mapColor(MapColor.METAL).strength(1.5F).sound(SoundType.METAL).noOcclusion().lightLevel(state -> 15),
			p -> new ShapedBlock(p, Block.box(3, 0, 3, 13, 16, 13)));
	public static final Block TRADER_SIGN = register("trader_sign", Properties.of().mapColor(MapColor.COLOR_CYAN).strength(2.0F).sound(SoundType.METAL));
	public static final Block CLINIC_SIGN = register("clinic_sign", Properties.of().mapColor(MapColor.SNOW).strength(2.0F).sound(SoundType.METAL));
	public static final Block TOM_SIGN = register("tom_sign", Properties.of().mapColor(MapColor.COLOR_RED).strength(2.0F).sound(SoundType.METAL).lightLevel(state -> 6));
	public static final Block SIGNUP_SIGN = register("signup_sign", Properties.of().mapColor(MapColor.COLOR_YELLOW).strength(2.0F).sound(SoundType.METAL));
	public static final Block JUSTICE_SIGN = register("justice_sign", Properties.of().mapColor(MapColor.CRIMSON_NYLIUM).strength(2.0F).sound(SoundType.METAL));
	/** Pay an alloy, get something from the Keep's stock. */
	public static final Block TRADE_COUNTER = register("trade_counter", Properties.of()
			.mapColor(MapColor.COLOR_CYAN).strength(-1.0F, 3600000.0F).sound(SoundType.METAL).noOcclusion(),
			p -> new CounterBlock(p, Block.box(0, 0, 1, 16, 14, 15), "trade"));
	/** Pay an alloy, get a hot meal. */
	public static final Block TOM_COUNTER = register("tom_counter", Properties.of()
			.mapColor(MapColor.COLOR_RED).strength(-1.0F, 3600000.0F).sound(SoundType.WOOD).noOcclusion(),
			p -> new CounterBlock(p, Block.box(0, 0, 1, 16, 14, 15), "meal"));
	/** Free care at Hippocrates-1. */
	public static final Block CLINIC_BED = register("clinic_bed", Properties.of()
			.mapColor(MapColor.SNOW).strength(-1.0F, 3600000.0F).sound(SoundType.METAL).noOcclusion(),
			p -> new ClinicBedBlock(p, Block.box(0, 0, 1, 16, 11, 15)));
	public static final Block SIGNUP_BOOTH = register("signup_booth", Properties.of()
			.mapColor(MapColor.COLOR_YELLOW).strength(-1.0F, 3600000.0F).sound(SoundType.WOOD), SignupBoothBlock::new);
	public static final Block GALLERY_WALL = register("gallery_wall", Properties.of()
			.mapColor(MapColor.QUARTZ).strength(2.0F).sound(SoundType.STONE));
	/** The chair in the rubber room, sunk into the floor. */
	public static final Block SUNKEN_CHAIR = register("sunken_chair", Properties.of()
			.mapColor(MapColor.COLOR_GRAY).strength(0.8F).sound(SoundType.WOOL).noOcclusion(),
			p -> new ShapedBlock(p, Block.box(4, 0, 4, 12, 13, 12)));
	/** A painting lying on the floor, where no one would put one. */
	public static final Block FLOOR_PAINTING = register("floor_painting", Properties.of()
			.mapColor(MapColor.COLOR_BROWN).instabreak().sound(SoundType.WOOD).noOcclusion().noCollision().noLootTable(),
			p -> new ShapedBlock(p, Block.box(2, 0, 2, 14, 0.5, 14)));
	public static final Block HOSPITAL_BED = register("hospital_bed", Properties.of()
			.mapColor(MapColor.METAL).strength(0.8F).sound(SoundType.METAL).noOcclusion(),
			p -> new ShapedBlock(p, Block.box(0, 0, 1, 16, 11, 15)));
	public static final Block COT = register("cot", Properties.of()
			.mapColor(MapColor.COLOR_GREEN).strength(0.8F).sound(SoundType.WOOL).noOcclusion(),
			p -> new RestBlock(p, Block.box(0, 0, 1, 16, 8, 15), "first_light"));
	public static final Block BEDROLL = register("bedroll", Properties.of()
			.mapColor(MapColor.COLOR_BROWN).strength(0.5F).sound(SoundType.WOOL).noOcclusion(),
			p -> new RestBlock(p, Block.box(1, 0, 1, 15, 3, 15), "blink"));
	public static final Block SUPPLY_LOCKER = register("supply_locker", Properties.of()
			.mapColor(MapColor.METAL).strength(-1.0F, 3600000.0F).sound(SoundType.METAL).noOcclusion(),
			p -> new LockerBlock(p, Block.box(1, 0, 2, 15, 16, 14)));
	public static final Block BARREL_FIRE = register("barrel_fire", Properties.of()
			.mapColor(MapColor.COLOR_BLACK).strength(2.0F).sound(SoundType.METAL).noOcclusion().lightLevel(state -> 14),
			p -> new BarrelFireBlock(p, Block.box(2.5, 0, 2.5, 13.5, 15, 13.5)));
	public static final Block TARP = register("tarp", Properties.of()
			.mapColor(MapColor.COLOR_BLUE).strength(0.8F).sound(SoundType.WOOL));

	/** Hung on pillars by the settlement teams; no item of its own, gives its supplies once. */
	public static final Block SETTLEMENT_MARKER = registerBlockOnly("settlement_marker", Properties.of()
			.mapColor(MapColor.COLOR_ORANGE).strength(0.2F).noCollision().noOcclusion().sound(SoundType.WOOL), SettlementMarkerBlock::new);

	/** Left by phosphor powder; no item of its own. */
	public static final Block PHOSPHOR_MARK = registerBlockOnly("phosphor_mark", Properties.of()
			.mapColor(MapColor.COLOR_LIGHT_GREEN).instabreak().noCollision().noOcclusion().noLootTable().replaceable()
			.sound(SoundType.SAND).lightLevel(state -> 5).emissiveRendering(state -> true), PhosphorMarkBlock::new);

	private Level1Blocks() {
	}

	// Calling this from onInitialize runs the registrations above before the registries freeze.
	static void register() {
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.BUILDING_BLOCKS)
				.register(output -> ALL.forEach(output::accept));
	}

	private static ToIntFunction<BlockState> lit(int level) {
		return state -> state.getValue(BlockStateProperties.LIT) ? level : 0;
	}

	private static Properties concrete(MapColor color) {
		return Properties.of().mapColor(color).strength(2.0F).sound(SoundType.STONE);
	}

	// Hung out of reach, so nothing bumps into them.
	private static Properties fixture() {
		return Properties.of().mapColor(MapColor.METAL).strength(0.5F).sound(SoundType.GLASS).noOcclusion().noCollision();
	}

	private static Properties pipe() {
		return Properties.of().mapColor(MapColor.METAL).strength(1.5F).sound(SoundType.METAL).noOcclusion().noCollision();
	}

	private static Properties bulb() {
		return Properties.of().mapColor(MapColor.SAND).strength(0.3F).sound(SoundType.GLASS).noOcclusion().noCollision();
	}

	// Like the Level 0 exit: neither destroyed nor carried away.
	private static Properties exit() {
		return Properties.of().mapColor(MapColor.METAL).strength(-1.0F, 3600000.0F).noLootTable().sound(SoundType.METAL);
	}

	// Ways to other levels cannot be broken or carried off either.
	private static Properties exitProperties(MapColor color, SoundType sound) {
		return Properties.of().mapColor(color).strength(-1.0F, 3600000.0F).noLootTable().sound(sound);
	}

	private static Block door(String name, Level1Exit exit, SoundType sound, int light) {
		return register(name, exitProperties(MapColor.METAL, sound).noOcclusion().lightLevel(state -> light),
				p -> new AxisExitBlock(p, exit.level));
	}

	private static Properties darkness() {
		return Properties.of().mapColor(MapColor.COLOR_BLACK).strength(-1.0F, 3600000.0F).noLootTable().noCollision().sound(SoundType.WOOL);
	}

	private static Block register(String name, Properties properties) {
		return register(name, properties, Block::new);
	}

	private static Block registerBlockOnly(String name, Properties properties, Function<Properties, Block> factory) {
		return Blocks.register(ResourceKey.create(Registries.BLOCK, Level1.id(name)), factory, properties);
	}

	private static Block register(String name, Properties properties, Function<Properties, Block> factory) {
		Identifier id = Level1.id(name);
		Block block = Blocks.register(ResourceKey.create(Registries.BLOCK, id), factory, properties);
		ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, id);
		BlockItem item = new BlockItem(block, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix());
		item.registerBlocks(Item.BY_BLOCK, item);
		Registry.register(BuiltInRegistries.ITEM, itemKey, item);
		ALL.add(block);
		return block;
	}
}
