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
