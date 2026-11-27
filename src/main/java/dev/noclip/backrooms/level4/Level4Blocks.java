package dev.noclip.backrooms.level4;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.object.builder.v1.block.type.BlockSetTypeBuilder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class Level4Blocks {
	static final List<Block> ALL = new ArrayList<>();

	private static final VoxelShape TROFFER_SHAPE = Block.box(0, 12, 0, 16, 16, 16);
	private static final VoxelShape MUSHROOM_SHAPE = Block.box(3, 0, 3, 13, 10, 13);
	private static final BlockSetType FIRE_DOOR_SET = BlockSetTypeBuilder.copyOf(BlockSetType.OAK)
			.openableByHand(true).register(Level4.id("office_fire_door"));

	// Carpet & floors
	public static final Block OFFICE_CARPET = register("office_carpet", carpet(MapColor.COLOR_GRAY));
	public static final Block OFFICE_CARPET_DENT = register("office_carpet_dent", carpet(MapColor.COLOR_GRAY));
	public static final Block OFFICE_DAMP_CARPET = register("office_damp_carpet", carpet(MapColor.COLOR_CYAN));

	// Walls & ceiling
	public static final Block OFFICE_DRYWALL = register("office_drywall", wall(MapColor.QUARTZ));
	public static final Block OFFICE_DRYWALL_UPPER = register("office_drywall_upper", wall(MapColor.QUARTZ));
	public static final Block OFFICE_PILLAR = register("office_pillar", wall(MapColor.QUARTZ));
	public static final Block OFFICE_PILLAR_UPPER = register("office_pillar_upper", wall(MapColor.QUARTZ));
	public static final Block OFFICE_RADIATOR_WALL = register("office_radiator_wall", wall(MapColor.QUARTZ),
			p -> new Level4FacingBlock(p, Level4FacingBlock.full()));
	public static final Block OFFICE_ACOUSTIC_CEILING = register("office_acoustic_ceiling", wall(MapColor.COLOR_LIGHT_GRAY));
	public static final Block OFFICE_CEILING_VENT = register("office_ceiling_vent", wall(MapColor.COLOR_LIGHT_GRAY));

	// 4-state recessed fluorescent troffer lights
	public static final Block OFFICE_TROFFER_LIT = register("office_troffer_lit", fixture(13), p -> new Level4AxisBlock(p, TROFFER_SHAPE));
	public static final Block OFFICE_TROFFER_DIM = register("office_troffer_dim", fixture(7), p -> new Level4AxisBlock(p, TROFFER_SHAPE));
	public static final Block OFFICE_TROFFER_FLICKER = register("office_troffer_flicker", fixture(9), p -> new Level4AxisBlock(p, TROFFER_SHAPE));
	public static final Block OFFICE_TROFFER_BROKEN = register("office_troffer_broken", fixture(0), p -> new Level4AxisBlock(p, TROFFER_SHAPE));

	// Glass & Windows
	public static final Block OFFICE_GLASS_WALL = register("office_glass_wall", Properties.of().mapColor(MapColor.COLOR_LIGHT_BLUE)
			.strength(0.6F).sound(SoundType.GLASS).noOcclusion()
			.isViewBlocking((s, l, p) -> false).isSuffocating((s, l, p) -> false), TransparentBlock::new);
	public static final Block OFFICE_BLACKED_WINDOW = register("office_blacked_window", unbreakable(MapColor.COLOR_BLACK, SoundType.GLASS));
	public static final Block OFFICE_TRAP_WINDOW = register("office_trap_window",
			unbreakable(MapColor.SNOW, SoundType.GLASS).noOcclusion()
					.isViewBlocking((s, l, p) -> false).isSuffocating((s, l, p) -> false).lightLevel(s -> 2),
			TransparentBlock::new);
	public static final Block OFFICE_RAIN_WINDOW = register("office_rain_window",
			unbreakable(MapColor.COLOR_LIGHT_BLUE, SoundType.GLASS).noOcclusion()
					.isViewBlocking((s, l, p) -> false).isSuffocating((s, l, p) -> false),
			TransparentBlock::new);

	// Office doors & exits
	public static final Block OFFICE_FIRE_DOOR = register("office_fire_door", Properties.of().mapColor(MapColor.WOOD)
			.strength(2.5F).sound(SoundType.WOOD).noOcclusion().pushReaction(PushReaction.DESTROY),
			p -> new DoorBlock(FIRE_DOOR_SET, p));
	public static final Block OFFICE_EXIT_SIGN = register("office_exit_sign", Properties.of().mapColor(MapColor.COLOR_GREEN)
			.strength(0.6F).sound(SoundType.METAL).noOcclusion().noCollision().lightLevel(s -> 7),
			p -> new Level4FacingBlock(p, Level4FacingBlock.northBox(3, 10, 14, 13, 15, 16)));
	public static final Block OFFICE_ELEVATOR = register("office_elevator", unbreakable(MapColor.METAL, SoundType.METAL).lightLevel(s -> 3),
			p -> Level4ExitBlock.create(p, Level4Exit.ELEVATOR_3, false));
	public static final Block OFFICE_ELEVATOR_FRAME = register("office_elevator_frame",
			unbreakable(MapColor.COLOR_GRAY, SoundType.METAL));
	public static final Block OFFICE_STAIRWELL_5 = register("office_stairwell_5", unbreakable(MapColor.COLOR_RED, SoundType.METAL),
			p -> Level4ExitBlock.create(p, Level4Exit.STAIRS_5, false));
	public static final Block OFFICE_STAIRWELL_6 = register("office_stairwell_6", unbreakable(MapColor.COLOR_BLACK, SoundType.METAL),
			p -> Level4ExitBlock.create(p, Level4Exit.STAIRS_6, false));
	public static final Block OFFICE_BASEMENT_71 = register("office_basement_71", unbreakable(MapColor.STONE, SoundType.METAL),
			p -> Level4ExitBlock.create(p, Level4Exit.BASEMENT_71, false));
	public static final Block OFFICE_DOOR_4P3 = register("office_door_4p3", unbreakable(MapColor.SNOW, SoundType.WOOD),
			p -> Level4ExitBlock.create(p, Level4Exit.WHITE_DOOR_4P3, false));
	public static final Block OFFICE_DOOR_11 = register("office_door_11", unbreakable(MapColor.COLOR_LIGHT_BLUE, SoundType.METAL),
			p -> Level4ExitBlock.create(p, Level4Exit.CITY_DOOR_11, false));
	public static final Block OFFICE_DOOR_13 = register("office_door_13", unbreakable(MapColor.ICE, SoundType.GLASS),
			p -> Level4ExitBlock.create(p, Level4Exit.MIRROR_13, false));
	public static final Block OFFICE_HATCH_2 = register("office_hatch_2", unbreakable(MapColor.COLOR_ORANGE, SoundType.METAL).noOcclusion(),
			p -> Level4ExitBlock.create(p, Level4Exit.HATCH_2, true));

	// Supplies, factions & micro-events
	public static final Block OFFICE_WATER_COOLER = register("office_water_cooler", Properties.of().mapColor(MapColor.SNOW)
			.strength(1.5F).sound(SoundType.GLASS).noOcclusion(), p -> Level4WaterSourceBlock.create(p, true));
	public static final Block ALMOND_FOUNTAIN_BASIN = register("almond_fountain_basin", wall(MapColor.QUARTZ));
	public static final Block ALMOND_FOUNTAIN_POOL = register("almond_fountain_pool", Properties.of().mapColor(MapColor.SAND)
			.strength(-1.0F, 3600000.0F).noLootTable().sound(SoundType.HONEY_BLOCK).noOcclusion().lightLevel(s -> 2),
			p -> Level4WaterSourceBlock.create(p, false));
	public static final Block OFFICE_VENDING = register("office_vending", Properties.of().mapColor(MapColor.COLOR_BLUE)
			.strength(3.0F, 6.0F).sound(SoundType.METAL).noOcclusion().lightLevel(s -> 5),
			p -> new Level4FacingBlock(p, Level4FacingBlock.northBox(1, 0, 2, 15, 16, 14)));
	public static final Block OMEGA_WALL = register("omega_wall", unbreakable(MapColor.COLOR_LIGHT_GRAY, SoundType.METAL));
	public static final Block OMEGA_SIGN = register("omega_sign", unbreakable(MapColor.COLOR_BLUE, SoundType.METAL));
	public static final Block OMEGA_COT = register("omega_cot", Properties.of().mapColor(MapColor.COLOR_GREEN)
			.strength(1.0F).sound(SoundType.WOOL).noOcclusion(),
			p -> new Level4FacingBlock(p, Level4FacingBlock.northBox(1, 0, 0, 15, 7, 16)));
	public static final Block OMEGA_LOCKER = register("omega_locker", Properties.of().mapColor(MapColor.COLOR_CYAN)
			.strength(3.0F).sound(SoundType.METAL).noOcclusion(),
			p -> new Level4FacingBlock(p, Level4FacingBlock.northBox(1, 0, 2, 15, 16, 14)));
	public static final Block OMEGA_BARRICADE = register("omega_barricade", Properties.of().mapColor(MapColor.STONE)
			.strength(4.0F).sound(SoundType.STONE).noOcclusion(),
			p -> new Level4FacingBlock(p, Level4FacingBlock.northBox(0, 0, 2, 16, 14, 14)));
	public static final Block AMOR_PLANTER = register("amor_planter", Properties.of().mapColor(MapColor.WOOD)
			.strength(1.5F).sound(SoundType.WOOD).noOcclusion(), Block::new);
	public static final Block AMOR_PLANTER_L1 = register("amor_planter_l1", Properties.of().mapColor(MapColor.WOOD)
			.strength(1.5F).sound(SoundType.WOOD).noOcclusion(), Block::new);
	public static final Block AMOR_MUSHROOM = register("amor_mushroom", Properties.of().mapColor(MapColor.COLOR_YELLOW)
			.strength(0.2F).sound(SoundType.FUNGUS).noCollision().noOcclusion().lightLevel(s -> 5),
			p -> new Block(p) {
				@Override
				protected VoxelShape getShape(net.minecraft.world.level.block.state.BlockState s, net.minecraft.world.level.BlockGetter l,
						net.minecraft.core.BlockPos pos, net.minecraft.world.phys.shapes.CollisionContext ctx) {
					return MUSHROOM_SHAPE;
				}
			});
	public static final Block AMOR_MUSHROOM_MOSS = register("amor_mushroom_moss", Properties.of().mapColor(MapColor.COLOR_CYAN)
			.strength(0.2F).sound(SoundType.FUNGUS).noCollision().noOcclusion().lightLevel(s -> 4),
			p -> new Block(p) {
				@Override
				protected VoxelShape getShape(net.minecraft.world.level.block.state.BlockState s, net.minecraft.world.level.BlockGetter l,
						net.minecraft.core.BlockPos pos, net.minecraft.world.phys.shapes.CollisionContext ctx) {
					return MUSHROOM_SHAPE;
				}
			});
	public static final Block AMOR_MUSHROOM_VEIL = register("amor_mushroom_veil", Properties.of().mapColor(MapColor.COLOR_PURPLE)
			.strength(0.2F).sound(SoundType.FUNGUS).noCollision().noOcclusion().lightLevel(s -> 6),
			p -> new Block(p) {
				@Override
				protected VoxelShape getShape(net.minecraft.world.level.block.state.BlockState s, net.minecraft.world.level.BlockGetter l,
						net.minecraft.core.BlockPos pos, net.minecraft.world.phys.shapes.CollisionContext ctx) {
					return MUSHROOM_SHAPE;
				}
			});
	public static final Block TBD_CURIO_SHELF = register("tbd_curio_shelf", Properties.of().mapColor(MapColor.WOOD)
			.strength(2.0F).sound(SoundType.WOOD).noOcclusion(),
			p -> new Level4FacingBlock(p, Level4FacingBlock.northBox(1, 0, 3, 15, 16, 13)));
	public static final Block TBD_HOARD_CRATE = register("tbd_hoard_crate", Properties.of().mapColor(MapColor.WOOD)
			.strength(2.5F).sound(SoundType.WOOD).noOcclusion(),
			p -> new Level4FacingBlock(p, Level4FacingBlock.full()));
	public static final Block OFFICE_BREAKER = register("office_breaker", Properties.of().mapColor(MapColor.METAL)
			.strength(2.5F).sound(SoundType.METAL).noOcclusion(),
			p -> new Level4FacingBlock(p, Level4FacingBlock.northBox(2, 2, 12, 14, 14, 16)));
	public static final Block OFFICE_PHOTOCOPIER = register("office_photocopier", Properties.of().mapColor(MapColor.QUARTZ)
			.strength(2.2F).sound(SoundType.METAL).noOcclusion(),
			p -> new Level4FacingBlock(p, Level4FacingBlock.northBox(1, 0, 2, 15, 14, 14)));
	public static final Block OFFICE_DESK_PHONE = register("office_desk_phone", Properties.of().mapColor(MapColor.WOOD)
			.strength(1.5F).sound(SoundType.WOOD).noOcclusion(),
			p -> new Level4FacingBlock(p, Level4FacingBlock.northBox(1, 0, 2, 15, 14, 14)));
	public static final Block ALMOND_BOTTLE_CLUSTER = register("almond_bottle_cluster", Properties.of().mapColor(MapColor.SAND)
			.strength(0.2F).sound(SoundType.GLASS).noOcclusion(), AlmondBottleClusterBlock::new);

	// Batch 1 POI blocks
	public static final Block MEG_CHALK_SIGN = register("meg_chalk_sign", Properties.of().mapColor(MapColor.COLOR_LIGHT_BLUE)
			.strength(0.3F).sound(SoundType.STONE).noOcclusion().noCollision(), MegChalkSignBlock::new);
	public static final Block CAUTION_TAPE_BARRIER = register("caution_tape_barrier", Properties.of().mapColor(MapColor.COLOR_YELLOW)
			.strength(0.2F).sound(SoundType.WOOL).noOcclusion().pushReaction(PushReaction.DESTROY),
			p -> new Level4FacingBlock(p, Level4FacingBlock.northBox(0, 4, 7, 16, 15, 9)));

	// Batch 2 POI blocks
	public static final Block OFFICE_DARK_MIRROR = register("office_dark_mirror",
			unbreakable(MapColor.COLOR_BLACK, SoundType.GLASS), OfficeDarkMirrorBlock::new);

	// Batch 3 POI blocks
	public static final Block CORRUPTED_FOUNTAIN_POOL = register("corrupted_fountain_pool",
			Properties.of().mapColor(MapColor.COLOR_RED)
					.strength(-1.0F, 3600000.0F).noLootTable().sound(SoundType.HONEY_BLOCK)
					.noOcclusion().lightLevel(s -> 3),
			CorruptedFountainPoolBlock::new);

	// Batch 4 POI blocks
	public static final Block EXECUTIVE_RED_PHONE = register("executive_red_phone",
			Properties.of().mapColor(MapColor.COLOR_RED)
					.strength(2.0F).sound(SoundType.METAL).noOcclusion().lightLevel(s -> 4),
			p -> new Level4FacingBlock(p, Level4FacingBlock.northBox(1, 0, 2, 15, 14, 14)));

	// Route A POI container blocks
	public static final Block MEG_SUPPLY_CRATE = register("office_supply_crate",
			Properties.of().mapColor(MapColor.COLOR_BLUE)
					.strength(2.5F, 6.0F).sound(SoundType.METAL).noOcclusion(),
			p -> new Level4FacingBlock(p, Level4FacingBlock.northBox(1, 0, 2, 15, 11, 14)));
	public static final Block INDUSTRIAL_TOOLBOX = register("industrial_toolbox",
			Properties.of().mapColor(MapColor.COLOR_RED)
					.strength(2.5F, 5.0F).sound(SoundType.METAL).noOcclusion(),
			p -> new Level4FacingBlock(p, Level4FacingBlock.northBox(1, 0, 3, 15, 10, 13)));
	public static final Block OFFICE_STEEL_SAFE = register("office_steel_safe",
			Properties.of().mapColor(MapColor.COLOR_GRAY)
					.strength(4.0F, 8.0F).sound(SoundType.METAL).noOcclusion(),
			p -> new Level4FacingBlock(p, Level4FacingBlock.northBox(1, 0, 1, 15, 15, 15)));

	// Route B landmark monitor wall
	public static final Block EXECUTIVE_BROADCAST_SCREEN = register("executive_broadcast_screen",
			Properties.of().mapColor(MapColor.COLOR_GREEN)
					.strength(2.5F).sound(SoundType.GLASS).lightLevel(s -> 9),
			p -> new Level4FacingBlock(p, Level4FacingBlock.full()));

	private Level4Blocks() {
	}

	static void register() {
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.BUILDING_BLOCKS).register(output -> ALL.forEach(output::accept));
	}

	private static Properties carpet(MapColor color) {
		return Properties.of().mapColor(color).strength(0.8F).sound(SoundType.WOOL);
	}

	private static Properties wall(MapColor color) {
		return Properties.of().mapColor(color).strength(1.6F, 4.0F).sound(SoundType.STONE);
	}

	private static Properties fixture(int light) {
		return Properties.of().mapColor(MapColor.METAL).strength(0.5F).sound(SoundType.GLASS).noOcclusion().lightLevel(s -> light);
	}

	private static Properties unbreakable(MapColor color, SoundType sound) {
		return Properties.of().mapColor(color).strength(-1.0F, 3600000.0F).noLootTable().sound(sound).pushReaction(PushReaction.BLOCK);
	}

	private static Block register(String name, Properties properties) {
		return register(name, properties, Block::new);
	}

	private static <T extends Block> T register(String name, Properties properties, Function<Properties, T> factory) {
		ResourceKey<Block> blockKey = ResourceKey.create(Registries.BLOCK, Level4.id(name));
		T block = Registry.register(BuiltInRegistries.BLOCK, blockKey, factory.apply(properties.setId(blockKey)));
		ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, Level4.id(name));
		Registry.register(BuiltInRegistries.ITEM, itemKey, new BlockItem(block, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix()));
		ALL.add(block);
		return block;
	}

	private static final class AlmondBottleClusterBlock extends Block {
		private static final VoxelShape SHAPE = Block.box(3, 0, 3, 13, 9, 13);

		AlmondBottleClusterBlock(Properties properties) {
			super(properties);
		}

		@Override
		protected VoxelShape getShape(net.minecraft.world.level.block.state.BlockState state, net.minecraft.world.level.BlockGetter level,
				net.minecraft.core.BlockPos pos, net.minecraft.world.phys.shapes.CollisionContext context) {
			return SHAPE;
		}

		@Override
		protected net.minecraft.world.InteractionResult useWithoutItem(net.minecraft.world.level.block.state.BlockState state,
				net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos,
				net.minecraft.world.entity.player.Player player, net.minecraft.world.phys.BlockHitResult hit) {
			return collect(level, pos, player);
		}

		@Override
		protected net.minecraft.world.InteractionResult useItemOn(net.minecraft.world.item.ItemStack stack,
				net.minecraft.world.level.block.state.BlockState state, net.minecraft.world.level.Level level,
				net.minecraft.core.BlockPos pos, net.minecraft.world.entity.player.Player player,
				net.minecraft.world.InteractionHand hand, net.minecraft.world.phys.BlockHitResult hit) {
			return collect(level, pos, player);
		}

		private static net.minecraft.world.InteractionResult collect(net.minecraft.world.level.Level level,
				net.minecraft.core.BlockPos pos, net.minecraft.world.entity.player.Player player) {
			if (!level.isClientSide() && player instanceof net.minecraft.server.level.ServerPlayer sp) {
				var rng = level.getRandom();
				level.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
				var water = new net.minecraft.world.item.ItemStack(dev.noclip.backrooms.level1.Level1Items.ALMOND_WATER, 1 + rng.nextInt(2));
				if (!sp.getInventory().add(water)) sp.drop(water, false);
				if (rng.nextInt(100) < 35) {
					var bottle = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.GLASS_BOTTLE);
					if (!sp.getInventory().add(bottle)) sp.drop(bottle, false);
				}
				level.playSound(null, pos, net.minecraft.sounds.SoundEvents.ITEM_PICKUP,
						net.minecraft.sounds.SoundSource.PLAYERS, 0.85F, 1.05F);
				sp.sendOverlayMessage(net.minecraft.network.chat.Component.translatable("message.noclip_backrooms.level4.almond_pickup"));
			}
			return net.minecraft.world.InteractionResult.SUCCESS;
		}
	}

	static final class MegChalkSignBlock extends Level4FacingBlock {
		static final BooleanProperty SLASHED = BooleanProperty.create("slashed");

		MegChalkSignBlock(Properties properties) {
			super(properties, Level4FacingBlock.northBox(1, 2, 15, 15, 14, 16));
			registerDefaultState(defaultBlockState().setValue(SLASHED, false));
		}

		@Override
		public BlockState getStateForPlacement(net.minecraft.world.item.context.BlockPlaceContext context) {
			return super.getStateForPlacement(context).setValue(SLASHED, false);
		}

		@Override
		protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
			super.createBlockStateDefinition(builder);
			builder.add(SLASHED);
		}
	}

	static final class CorruptedFountainPoolBlock extends Block {
		static final BooleanProperty SAMPLED = BooleanProperty.create("sampled");
		private static final VoxelShape POOL_FULL = Block.box(0, 0, 0, 16, 12, 16);
		private static final VoxelShape POOL_SAMPLED = Block.box(0, 0, 0, 16, 7, 16);

		CorruptedFountainPoolBlock(Properties properties) {
			super(properties);
			registerDefaultState(defaultBlockState().setValue(SAMPLED, false));
		}

		@Override
		protected VoxelShape getShape(BlockState state, net.minecraft.world.level.BlockGetter level,
				net.minecraft.core.BlockPos pos, net.minecraft.world.phys.shapes.CollisionContext context) {
			return state.getValue(SAMPLED) ? POOL_SAMPLED : POOL_FULL;
		}

		@Override
		protected net.minecraft.world.InteractionResult useItemOn(net.minecraft.world.item.ItemStack stack,
				BlockState state, net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos,
				net.minecraft.world.entity.player.Player player, net.minecraft.world.InteractionHand hand,
				net.minecraft.world.phys.BlockHitResult hit) {
			if (hand != net.minecraft.world.InteractionHand.MAIN_HAND) {
				return net.minecraft.world.InteractionResult.PASS;
			}
			return Level4PoiEvents.interactCorruptedPool(level, pos, state, player, stack);
		}

		@Override
		protected net.minecraft.world.InteractionResult useWithoutItem(BlockState state,
				net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos,
				net.minecraft.world.entity.player.Player player, net.minecraft.world.phys.BlockHitResult hit) {
			return Level4PoiEvents.interactCorruptedPool(level, pos, state, player, net.minecraft.world.item.ItemStack.EMPTY);
		}

		@Override
		protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
			builder.add(SAMPLED);
		}
	}

	static final class OfficeDarkMirrorBlock extends Block {
		static final IntegerProperty SILHOUETTE = IntegerProperty.create("silhouette", 0, 2);

		OfficeDarkMirrorBlock(Properties properties) {
			super(properties);
			registerDefaultState(defaultBlockState().setValue(SILHOUETTE, 0));
		}

		@Override
		protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
			builder.add(SILHOUETTE);
		}
	}
}
