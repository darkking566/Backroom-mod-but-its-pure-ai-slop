package dev.noclip.backrooms.level3;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
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
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class Level3Blocks {
	static final List<Block> ALL = new ArrayList<>();

	private static final VoxelShape[] FIXTURE = Level3Shapes.axis(0, 11, 4.5, 16, 16, 11.5);
	private static final VoxelShape[] BULB = Level3Shapes.axis(4.5, 9, 4.5, 11.5, 16, 11.5);
	private static final VoxelShape[] BARS = Level3Shapes.axis(0, 0, 7, 16, 16, 9);
	private static final VoxelShape[] TRAY = Level3Shapes.axis(0, 10, 2, 16, 14, 14);
	private static final VoxelShape[] CHAIN = Level3Shapes.axis(7, 0, 7, 9, 16, 9);
	private static final VoxelShape[] HOOK = Level3Shapes.axis(5, 2, 5, 11, 16, 11);
	private static final VoxelShape[] LOW = Level3Shapes.axis(0, 8, 0, 16, 16, 16);

	// The tunnels
	public static final Block RED_BRICK_WALL = register("red_brick_wall", brick(MapColor.COLOR_ORANGE));
	public static final Block BROWN_BRICK_WALL = register("brown_brick_wall", brick(MapColor.COLOR_BROWN));
	public static final Block GLAZED_TILE_WALL = register("glazed_tile_wall", brick(MapColor.SAND));
	public static final Block GLAZED_TILE_WAINSCOT = register("glazed_tile_wainscot", brick(MapColor.COLOR_GREEN));
	public static final Block CINDER_BLOCK_WALL = register("cinder_block_wall", brick(MapColor.STONE));
	public static final Block PURPIFIED_BRICK = register("purpified_brick", brick(MapColor.COLOR_PURPLE).lightLevel(state -> 2));
	public static final Block MEG_WALL = register("meg_wall", brick(MapColor.COLOR_LIGHT_GRAY));
	public static final Block DUSTY_FLOOR = register("dusty_floor", brick(MapColor.STONE));
	public static final Block STATION_CEILING = register("station_ceiling", brick(MapColor.COLOR_GRAY));
	/** A ceiling so low you have to crouch: the top half of the block. */
	public static final Block LOW_CEILING = register("low_ceiling", brick(MapColor.COLOR_ORANGE).noOcclusion(), p -> new Level3AxisBlock(p, LOW));

	// On the walls
	public static final Block WALL_CONDUIT = register("wall_conduit", mounted(), p -> new Level3FacingBlock(p, Level3Shapes.facing(0, 6, 0, 16, 16, 5), Level3FacingBlock.Effect.NONE));
	public static final Block SLUDGE_PIPE = register("sludge_pipe", mounted(), p -> new Level3FacingBlock(p, Level3Shapes.facing(0, 5, 0, 16, 14, 7), Level3FacingBlock.Effect.NONE));
	public static final Block LEAKING_SLUDGE_PIPE = register("leaking_sludge_pipe", mounted(), p -> new Level3FacingBlock(p, Level3Shapes.facing(0, 5, 0, 16, 14, 7), Level3FacingBlock.Effect.DRIP));
	public static final Block ELECTRICAL_BOX = register("electrical_box", mounted(), p -> new Level3FacingBlock(p, Level3Shapes.facing(3, 0, 0, 13, 14, 5), Level3FacingBlock.Effect.WHIR));
	public static final Block SPARKING_OUTLET = register("sparking_outlet", brick(MapColor.COLOR_ORANGE), Level3HazardBlocks.Outlet::new);

	// Underfoot
	public static final Block LIVE_WIRE = register("live_wire", Properties.of().mapColor(MapColor.COLOR_BLACK).strength(0.5F)
			.sound(SoundType.WOOL).noCollision().noOcclusion().lightLevel(state -> 3), Level3HazardBlocks.LiveWire::new);
	public static final Block SLUDGE_PUDDLE = register("sludge_puddle", Properties.of().mapColor(MapColor.COLOR_BLACK).strength(0.5F)
			.sound(SoundType.SLIME_BLOCK).noCollision().noOcclusion(), Level3HazardBlocks.Sludge::new);

	// Bars and gates, which nothing breaks
	public static final Block WHITE_BARS = register("white_bars", unbreakable(MapColor.SNOW), p -> new Level3AxisBlock(p, BARS));
	public static final Block BLACK_BARS = register("black_bars", unbreakable(MapColor.COLOR_BLACK), p -> new Level3AxisBlock(p, BARS));
	public static final Block CAGE_GATE = register("cage_gate", unbreakable(MapColor.SNOW), Level3GateBlock::new);

	// Lights
	public static final Block STATION_LIGHT = register("station_light", fixture(10), p -> new Level3AxisBlock(p, FIXTURE));
	public static final Block FLICKERING_STATION_LIGHT = register("flickering_station_light", fixture(8), p -> new Level3AxisBlock(p, FIXTURE));
	public static final Block BROKEN_STATION_LIGHT = register("broken_station_light", fixture(0), p -> new Level3AxisBlock(p, FIXTURE));
	public static final Block CAGE_BULB = register("cage_bulb", fixture(9), p -> new Level3AxisBlock(p, BULB));
	public static final Block DEAD_CAGE_BULB = register("dead_cage_bulb", fixture(0), p -> new Level3AxisBlock(p, BULB));
	public static final Block FLOODLIGHT = register("station_floodlight", fixture(14), p -> new Level3AxisBlock(p, FIXTURE));
	public static final Block CABLE_TRAY = register("hanging_cable_tray", fixture(0), p -> new Level3AxisBlock(p, TRAY));
	public static final Block CEILING_SLUDGE_PIPE = register("ceiling_sludge_pipe", fixture(0), p -> new Level3AxisBlock(p, TRAY));

	// The halls
	public static final Block GENERATOR = register("station_generator", machine(), p -> new Level3PartBlock(p, null));
	public static final Block SWITCHGEAR = register("switchgear", machine(), p -> new Level3PartBlock(p, null));
	public static final Block BOILER = register("brass_boiler", machine().mapColor(MapColor.GOLD), p -> new Level3PartBlock(p, null));
	public static final Block BOILER_VALVE = register("boiler_valve", machine().mapColor(MapColor.GOLD).noOcclusion(),
			p -> new Level3FacingBlock(p, Level3Shapes.facing(3, 0, 3, 13, 12, 13), Level3FacingBlock.Effect.WHIR));
	public static final Block CONVEYOR = register("conveyor_belt", machine().noOcclusion(), Level3HazardBlocks.Conveyor::new);
	public static final Block PARTS_CRATE = register("parts_crate", Properties.of().mapColor(MapColor.WOOD).strength(2.0F).sound(SoundType.WOOD));
	public static final Block PRESS = register("assembly_press", machine(), p -> new Level3PartBlock(p, null));
	public static final Block HOIST_CHAIN = register("hoist_chain", Properties.of().mapColor(MapColor.METAL).strength(2.0F)
			.sound(SoundType.CHAIN).noOcclusion().noCollision(), p -> new Level3AxisBlock(p, CHAIN));
	public static final Block HOIST_HOOK = register("hoist_hook", Properties.of().mapColor(MapColor.METAL).strength(2.0F)
			.sound(SoundType.CHAIN).noOcclusion().noCollision(), p -> new Level3AxisBlock(p, HOOK));
	public static final Block PILLAR_BASE = register("sanctum_pillar_base", brick(MapColor.COLOR_BROWN));
	public static final Block PILLAR = register("sanctum_pillar", brick(MapColor.COLOR_BROWN));
	public static final Block PILLAR_TOP = register("sanctum_pillar_top", brick(MapColor.COLOR_BROWN));
	public static final Block PEDESTAL = register("angel_pedestal", brick(MapColor.COLOR_BROWN));
	/** The angel statue, two blocks tall on its pedestal. Entities that see one are afraid and leave. */
	public static final Block ANGEL = register("angel_statue", Properties.of().mapColor(MapColor.COLOR_BROWN).strength(-1.0F, 3600000.0F)
			.noLootTable().sound(SoundType.METAL).noOcclusion(), p -> new Level3PartBlock(p, null));
	public static final Block STAINED_GLASS = register("sanctum_stained_glass", Properties.of().mapColor(MapColor.COLOR_RED)
			.strength(-1.0F, 3600000.0F).noLootTable().sound(SoundType.GLASS).lightLevel(state -> 7));
	public static final Block COT = register("meg_cot", Properties.of().mapColor(MapColor.COLOR_GREEN).strength(1.0F)
			.sound(SoundType.WOOL).noOcclusion(), p -> new Level3FacingBlock(p, Level3Shapes.facing(1, 0, 0, 15, 7, 16), Level3FacingBlock.Effect.NONE));
	public static final Block SUPPLY_CRATE = register("meg_supply_crate", Properties.of().mapColor(MapColor.COLOR_GREEN).strength(2.0F).sound(SoundType.WOOD));
	public static final Block TABLE = register("meg_table", Properties.of().mapColor(MapColor.METAL).strength(2.0F).sound(SoundType.METAL).noOcclusion(),
			p -> new Level3FacingBlock(p, Level3Shapes.full(), Level3FacingBlock.Effect.NONE));
	public static final Block MEG_SIGN = register("meg_sign", brick(MapColor.COLOR_LIGHT_GRAY));
	/** The M.E.G. signs on passage walls: an arrow towards Base Gamma, and warnings at the paranoia hotspots. */
	public static final Block MEG_ARROW_SIGN = register("meg_arrow_sign", sign(), Level3SignBlock::new);
	public static final Block MEG_WARNING_SIGN = register("meg_warning_sign", sign(), Level3SignBlock::new);

	// Ways out, which nothing breaks either
	public static final Block ELEVATOR = register("station_elevator", unbreakable(MapColor.GOLD).lightLevel(state -> 3),
			p -> new Level3PartBlock(p, Level3Exit.ELEVATOR));
	public static final Block BASE_GAMMA_DOOR = register("base_gamma_door", unbreakable(MapColor.COLOR_LIGHT_GRAY),
			p -> new Level3PartBlock(p, Level3Exit.BASE_GAMMA_DOOR));
	public static final Block CAR = register("rusted_car", unbreakable(MapColor.COLOR_RED).noOcclusion(), p -> new Level3PartBlock(p, Level3Exit.CAR));

	private Level3Blocks() {
	}

	static void register() {
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.BUILDING_BLOCKS).register(output -> ALL.forEach(output::accept));
	}

	private static Properties brick(MapColor color) {
		return Properties.of().mapColor(color).strength(2.0F, 6.0F).sound(SoundType.STONE);
	}

	private static Properties mounted() {
		return Properties.of().mapColor(MapColor.METAL).strength(1.5F).sound(SoundType.METAL).noOcclusion().noCollision();
	}

	private static Properties machine() {
		return Properties.of().mapColor(MapColor.COLOR_GREEN).strength(3.0F, 6.0F).sound(SoundType.METAL);
	}

	private static Properties fixture(int light) {
		return Properties.of().mapColor(MapColor.METAL).strength(0.5F).sound(SoundType.GLASS).noOcclusion().noCollision().lightLevel(state -> light);
	}

	private static Properties sign() {
		return Properties.of().mapColor(MapColor.COLOR_BLUE).strength(1.0F).sound(SoundType.METAL).noCollision().noOcclusion();
	}

	private static Properties unbreakable(MapColor color) {
		return Properties.of().mapColor(color).strength(-1.0F, 3600000.0F).noLootTable().sound(SoundType.METAL).noOcclusion()
				.pushReaction(PushReaction.BLOCK);
	}

	private static Block register(String name, Properties properties) {
		return register(name, properties, Block::new);
	}

	private static Block register(String name, Properties properties, Function<Properties, Block> factory) {
		Identifier id = Level3.id(name);
		Block block = Blocks.register(ResourceKey.create(Registries.BLOCK, id), factory, properties);
		ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, id);
		BlockItem item = new BlockItem(block, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix());
		item.registerBlocks(Item.BY_BLOCK, item);
		Registry.register(BuiltInRegistries.ITEM, itemKey, item);
		ALL.add(block);
		return block;
	}
}
