package dev.noclip.backrooms.level2;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.object.builder.v1.block.type.BlockSetTypeBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class Level2Blocks {
	static final List<Block> ALL = new ArrayList<>();

	private static final VoxelShape[] FIXTURE = Level2AxisBlock.turned(0, 10, 4.5, 16, 16, 11.5);
	private static final VoxelShape[] BULB = Level2AxisBlock.turned(5, 4, 5, 11, 16, 11);
	private static final VoxelShape DRUM = Block.box(2.5, 0, 2.5, 13.5, 15, 13.5);
	private static final VoxelShape SCRAP = Block.box(1, 0, 1, 15, 6, 15);
	private static final VoxelShape TOOLBOX_SHAPE = Block.box(2, 0, 4, 14, 7.5, 12);
	private static final VoxelShape REEL_SHAPE = Block.box(2, 0, 2, 14, 12, 14);
	private static final VoxelShape LUMBER_SHAPE = Block.box(1, 0, 1, 15, 8.5, 15);

	private static final BlockSetType STEEL = BlockSetTypeBuilder.copyOf(BlockSetType.IRON).openableByHand(true).register(Level2.id("steel"));
	private static final BlockSetType STEEL_LOCKED = BlockSetTypeBuilder.copyOf(BlockSetType.IRON).register(Level2.id("steel_locked"));

	// The tunnels
	public static final Block UTILITY_WALL = register("utility_wall", concrete(MapColor.QUARTZ));
	public static final Block STAINED_UTILITY_WALL = register("stained_utility_wall", concrete(MapColor.QUARTZ));
	public static final Block MUDDY_BRICK_WALL = register("muddy_brick_wall", brick(MapColor.DIRT));
	public static final Block BLACK_BRICK_WALL = register("black_brick_wall", brick(MapColor.COLOR_BLACK));
	public static final Block PAINTED_BRICK_WALL = register("painted_brick_wall", brick(MapColor.SNOW));
	public static final Block UTILITY_FLOOR = register("utility_floor", concrete(MapColor.STONE));
	public static final Block DEBRIS_UTILITY_FLOOR = register("debris_utility_floor", concrete(MapColor.STONE));
	public static final Block DAMP_UTILITY_FLOOR = register("damp_utility_floor", concrete(MapColor.STONE));
	public static final Block UTILITY_CEILING = register("utility_ceiling", concrete(MapColor.COLOR_GRAY));
	public static final Block VOID_CONCRETE = register("void_concrete", concrete(MapColor.COLOR_BLACK));

	// The pipe banks, bottom to top
	public static final Block INSULATED_PIPE = register("insulated_pipe", pipe(MapColor.SAND), p -> new Level2BankBlock(p, Level2BankBlock.Effect.NONE));
	public static final Block GREEN_PIPE = register("green_pipe", pipe(MapColor.COLOR_LIGHT_GREEN), p -> new Level2BankBlock(p, Level2BankBlock.Effect.NONE));
	public static final Block STEEL_PIPES = register("steel_pipes", pipe(MapColor.METAL), p -> new Level2BankBlock(p, Level2BankBlock.Effect.NONE));
	public static final Block VALVE_PIPES = register("valve_pipes", pipe(MapColor.METAL), p -> new Level2BankBlock(p, Level2BankBlock.Effect.NONE));
	public static final Block LEAKING_PIPES = register("leaking_pipes", pipe(MapColor.METAL), p -> new Level2BankBlock(p, Level2BankBlock.Effect.LEAK));
	public static final Block STEAM_PIPES = register("steam_pipes", pipe(MapColor.METAL), p -> new Level2BankBlock(p, Level2BankBlock.Effect.STEAM));
	public static final Block BLUE_LIGHT_PIPES = register("blue_light_pipes", pipe(MapColor.COLOR_BLUE).lightLevel(state -> 5),
			p -> new Level2BankBlock(p, Level2BankBlock.Effect.NONE));
	public static final Block CONDUIT_TRAY = register("conduit_tray", pipe(MapColor.COLOR_GRAY), p -> new Level2BankBlock(p, Level2BankBlock.Effect.NONE));
	public static final Block SMALL_PIPES = register("small_pipes", pipe(MapColor.METAL), p -> new Level2BankBlock(p, Level2BankBlock.Effect.NONE));
	public static final Block MACHINE_CABINET = register("machine_cabinet", machine(), p -> new Level2FacingBlock(p, true));
	public static final Block MACHINE_PANEL = register("machine_panel", machine(), p -> new Level2FacingBlock(p, true));

	// Lights
	public static final Block BROKEN_UTILITY_LIGHT = register("broken_utility_light", fixture(0), p -> new Level2AxisBlock(p, FIXTURE[0], FIXTURE[1]));
	public static final Block UTILITY_LIGHT = register("utility_light", fixture(12), p -> new Level2LightBlock(p, FIXTURE, () -> BROKEN_UTILITY_LIGHT));
	public static final Block WARM_UTILITY_LIGHT = register("warm_utility_light", fixture(11), p -> new Level2LightBlock(p, FIXTURE, () -> BROKEN_UTILITY_LIGHT));
	public static final Block BROKEN_CAGED_BULB = register("broken_caged_bulb", fixture(0), p -> new Level2AxisBlock(p, BULB[0], BULB[1]));
	public static final Block CAGED_BULB = register("caged_bulb", fixture(11), p -> new Level2LightBlock(p, BULB, () -> BROKEN_CAGED_BULB));
	public static final Block EMERGENCY_LIGHT = register("emergency_light", fixture(7), p -> new Level2LightBlock(p, BULB, () -> BROKEN_CAGED_BULB));

	// Doors into rooms
	public static final Block STEEL_DOOR = register("steel_door", door(5.0F), p -> new Level2DoorBlock(STEEL, p, false));
	public static final Block LOCKED_STEEL_DOOR = register("locked_steel_door", door(25.0F), p -> new Level2DoorBlock(STEEL_LOCKED, p, true));

	// What lies around in the rooms
	public static final Block WOODEN_CRATE = register("wooden_crate", Properties.of().mapColor(MapColor.WOOD).strength(2.0F).sound(SoundType.WOOD));
	public static final Block OIL_DRUM = register("oil_drum", Properties.of().mapColor(MapColor.COLOR_GRAY).strength(2.5F)
			.sound(SoundType.METAL).noOcclusion(), p -> new Level2ShapedBlock(p, DRUM));
	public static final Block SCRAP_HEAP = register("scrap_heap", Properties.of().mapColor(MapColor.METAL).strength(1.0F)
			.sound(SoundType.CHAIN).noOcclusion(), p -> new Level2ShapedBlock(p, SCRAP));
	public static final Block TOOLBOX = register("toolbox", Properties.of().mapColor(MapColor.COLOR_RED).strength(1.5F)
			.sound(SoundType.METAL).noOcclusion(), p -> new Level2ShapedBlock(p, TOOLBOX_SHAPE));
	public static final Block CABLE_REEL = register("cable_reel", Properties.of().mapColor(MapColor.WOOD).strength(1.8F)
			.sound(SoundType.WOOD).noOcclusion(), p -> new Level2ShapedBlock(p, REEL_SHAPE));
	public static final Block LUMBER_STACK = register("lumber_stack", Properties.of().mapColor(MapColor.WOOD).strength(1.6F)
			.sound(SoundType.WOOD).noOcclusion(), p -> new Level2ShapedBlock(p, LUMBER_SHAPE));

	/** Spray-painted marks earlier wanderers left near the standard exits. */
	public static final Block WANDERER_MARK = register("wanderer_mark", Properties.of().mapColor(MapColor.COLOR_RED).strength(0.2F)
			.noLootTable().sound(SoundType.WOOL).noCollision().noOcclusion(), Level2MarkBlock::new);

	/** Windows at the ends of dead-end tunnels, bright as day. Do not go near them (see Level2Windows). */
	public static final Block TUNNEL_WINDOW = register("tunnel_window", Properties.of().mapColor(MapColor.SNOW)
			.strength(-1.0F, 3600000.0F).noLootTable().sound(SoundType.GLASS).lightLevel(state -> 9));

	// Ways to other levels, one block per kind of door
	static final Map<Level2Exit, Block> EXITS = new EnumMap<>(Level2Exit.class);
	public static final Block MARKED_DOOR_1 = exit("marked_door_1", Level2Exit.MARKED_1, SoundType.METAL, 0);
	public static final Block MARKED_DOOR_3 = exit("marked_door_3", Level2Exit.MARKED_3, SoundType.METAL, 0);
	public static final Block MARKED_DOOR_4 = exit("marked_door_4", Level2Exit.MARKED_4, SoundType.METAL, 0);
	public static final Block PLYWOOD_DOOR = exit("plywood_door", Level2Exit.PLYWOOD, SoundType.WOOD, 0);
	public static final Block ENGRAVED_GIANT_DOOR = exit("engraved_giant_door", Level2Exit.ENGRAVED_GIANT, SoundType.METAL, 0);
	public static final Block BOILER_HATCH = exit("boiler_hatch", Level2Exit.BOILER_HATCH, SoundType.METAL, 10);
	public static final Block AGED_WOODEN_DOOR = exit("aged_wooden_door", Level2Exit.AGED_WOOD, SoundType.WOOD, 0);
	public static final Block PLAIN_METAL_DOOR = exit("plain_metal_door", Level2Exit.METAL, SoundType.METAL, 0);
	public static final Block BLUE_DOOR = exit("blue_door", Level2Exit.BLUE, SoundType.METAL, 0);
	public static final Block RAINBOW_GIANT_DOOR = exit("rainbow_giant_door", Level2Exit.RAINBOW_GIANT, SoundType.WOOD, 0);
	public static final Block BLADE_DOOR = exit("blade_door", Level2Exit.BLADE, SoundType.METAL, 0);
	public static final Block BROKEN_KNOB_DOOR = exit("broken_knob_door", Level2Exit.BROKEN_KNOB, SoundType.WOOD, 0);
	public static final Block PINK_DOOR = exit("pink_door", Level2Exit.PINK, SoundType.WOOD, 0);
	public static final Block ELEVATOR_DOOR = exit("level2_elevator_door", Level2Exit.ELEVATOR, SoundType.METAL, 2);

	public static final Block BREAKER_BOX = register("breaker_box", wallDetail(),
			p -> new Level2WallDetailBlock(p, 3, 2, 13, 13, 14, 16));
	public static final Block PRESSURE_GAUGE = register("pressure_gauge", wallDetail(),
			p -> new Level2WallDetailBlock(p, 4, 0, 14, 12, 12, 16));
	public static final Block HAZARD_SIGN = register("hazard_sign", wallDetail(),
			p -> new Level2WallDetailBlock(p, 2, 3, 15, 14, 13, 16));
	public static final Block DRAIN_GRATE = register("drain_grate",
			Properties.of().mapColor(MapColor.METAL).strength(2.0F, 6.0F).sound(SoundType.METAL));
	public static final Block FLOOR_CABLE_DUCT = register("floor_cable_duct",
			Properties.of().mapColor(MapColor.METAL).strength(2.0F, 6.0F).sound(SoundType.METAL),
			Level2AxisBlock::new);
	public static final Block CEILING_VENT = register("ceiling_vent", ceilingDetail(),
			p -> shaped(p, Block.box(2, 14.5, 2, 14, 16, 14)));
	public static final Block SNAPPED_CABLES = register("snapped_cables", ceilingDetail(),
			p -> shaped(p, Block.box(3, 5, 3, 13, 16, 13)));

	private Level2Blocks() {
	}

	static void register() {
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.BUILDING_BLOCKS).register(output -> ALL.forEach(output::accept));
	}

	private static Properties concrete(MapColor color) {
		return Properties.of().mapColor(color).strength(1.8F, 6.0F).sound(SoundType.STONE);
	}

	private static Properties brick(MapColor color) {
		return Properties.of().mapColor(color).strength(2.0F, 6.0F).sound(SoundType.STONE);
	}

	// A bank of pipes fills its column: you cannot squeeze past it.
	private static Properties pipe(MapColor color) {
		return Properties.of().mapColor(color).strength(2.0F, 6.0F).sound(SoundType.METAL).noOcclusion();
	}

	private static Properties machine() {
		return Properties.of().mapColor(MapColor.COLOR_GRAY).strength(3.0F, 6.0F).sound(SoundType.METAL);
	}

	// Hung out of reach, so nothing bumps into them.
	private static Properties fixture(int light) {
		return Properties.of().mapColor(MapColor.METAL).strength(0.5F).sound(SoundType.GLASS).noOcclusion().noCollision()
				.lightLevel(state -> light);
	}

	private static Properties wallDetail() {
		return Properties.of().mapColor(MapColor.METAL).strength(0.8F).sound(SoundType.METAL).noOcclusion().noCollision();
	}

	private static Properties ceilingDetail() {
		return Properties.of().mapColor(MapColor.METAL).strength(0.5F).sound(SoundType.METAL).noOcclusion().noCollision();
	}

	private static Block shaped(Properties properties, VoxelShape shape) {
		return new Block(properties) {
			@Override
			protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
				return shape;
			}
		};
	}

	private static Properties door(float strength) {
		return Properties.of().mapColor(MapColor.SNOW).strength(strength, 1200.0F).sound(SoundType.METAL).noOcclusion()
				.pushReaction(PushReaction.DESTROY);
	}

	// Ways to other levels cannot be broken or carried off.
	private static Block exit(String name, Level2Exit exit, SoundType sound, int light) {
		Block block = register(name, Properties.of().mapColor(MapColor.METAL).strength(-1.0F, 3600000.0F).noLootTable()
				.sound(sound).lightLevel(state -> light), p -> new Level2ExitBlock(p, exit));
		EXITS.put(exit, block);
		return block;
	}

	private static Block register(String name, Properties properties) {
		return register(name, properties, Block::new);
	}

	private static Block register(String name, Properties properties, Function<Properties, Block> factory) {
		Identifier id = Level2.id(name);
		Block block = Blocks.register(ResourceKey.create(Registries.BLOCK, id), factory, properties);
		ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, id);
		BlockItem item = new BlockItem(block, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix());
		item.registerBlocks(Item.BY_BLOCK, item);
		Registry.register(BuiltInRegistries.ITEM, itemKey, item);
		ALL.add(block);
		return block;
	}
}
