package dev.noclip.backrooms.core.story;

import dev.noclip.backrooms.BackroomsMod;
import java.util.function.Function;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * What the first trip leaves in Level 0 for a newcomer (docs/企劃書/1-遊戲玩法/README.md, "第一趟：跟著纜線走"): the
 * researchers' cable along the floor and the glowing door at its end. Nothing breaks them,
 * like Level 0's exits. Textures and models come from tools/make_story.py.
 */
public final class StoryBlocks {
	public static final Block GUIDE_CABLE = register("guide_cable",
			fixed(MapColor.COLOR_YELLOW).sound(SoundType.WOOL).noCollision().noOcclusion(), Cable::new);
	public static final Block GUIDE_DOOR = register("guide_door",
			fixed(MapColor.SNOW).sound(SoundType.METAL).noOcclusion().lightLevel(s -> 15), Door::new);

	private StoryBlocks() {
	}

	static void register() {
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(output -> {
			output.accept(GUIDE_CABLE);
			output.accept(GUIDE_DOOR);
		});
	}

	private static Properties fixed(MapColor color) {
		return Properties.of().mapColor(color).strength(-1.0F, 3600000.0F).noLootTable().pushReaction(PushReaction.BLOCK);
	}

	private static Block register(String name, Properties properties, Function<Properties, Block> factory) {
		Identifier id = BackroomsMod.id(name);
		Block block = Blocks.register(ResourceKey.create(Registries.BLOCK, id), factory, properties);
		ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, id);
		BlockItem item = new BlockItem(block, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix());
		item.registerBlocks(Item.BY_BLOCK, item);
		Registry.register(BuiltInRegistries.ITEM, itemKey, item);
		return block;
	}

	/** Which two sides of the block a piece of cable joins: straight through, or round a corner. */
	public enum Run implements StringRepresentable {
		NORTH_SOUTH("north_south"), EAST_WEST("east_west"),
		NORTH_EAST("north_east"), EAST_SOUTH("east_south"), SOUTH_WEST("south_west"), WEST_NORTH("west_north");

		private final String name;

		Run(String name) {
			this.name = name;
		}

		@Override
		public String getSerializedName() {
			return name;
		}

		/** The piece joining the sides toward {@code a} and {@code b} (null: an end, joining only one side). */
		public static Run joining(Direction a, Direction b) {
			if (b == null || b == a) b = a.getOpposite();
			boolean n = a == Direction.NORTH || b == Direction.NORTH, s = a == Direction.SOUTH || b == Direction.SOUTH;
			boolean e = a == Direction.EAST || b == Direction.EAST, w = a == Direction.WEST || b == Direction.WEST;
			if (n && s) return NORTH_SOUTH;
			if (e && w) return EAST_WEST;
			if (n && e) return NORTH_EAST;
			if (e && s) return EAST_SOUTH;
			if (s && w) return SOUTH_WEST;
			return WEST_NORTH;
		}
	}

	/** A thick black and yellow cable lying flat on the carpet; you walk over it. */
	public static final class Cable extends Block {
		public static final EnumProperty<Run> RUN = EnumProperty.create("run", Run.class);
		private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 1, 16);

		Cable(Properties properties) {
			super(properties);
			registerDefaultState(stateDefinition.any().setValue(RUN, Run.NORTH_SOUTH));
		}

		@Override
		protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
			builder.add(RUN);
		}

		@Override
		public BlockState getStateForPlacement(BlockPlaceContext context) {
			return defaultBlockState().setValue(RUN, context.getHorizontalDirection().getAxis() == Direction.Axis.X ? Run.EAST_WEST : Run.NORTH_SOUTH);
		}

		@Override
		protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
			return SHAPE;
		}
	}

	/**
	 * The door at the end of the cable: lit from behind, the only door in Level 0 that opens on a newcomer's first
	 * trip, and only for them (FirstTrip). FACING is the way its face looks; its back is against the wall.
	 */
	public static final class Door extends HorizontalDirectionalBlock {
		public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;
		private static final VoxelShape NORTH = Block.box(0, 0, 13, 16, 16, 16), SOUTH = Block.box(0, 0, 0, 16, 16, 3),
				WEST = Block.box(13, 0, 0, 16, 16, 16), EAST = Block.box(0, 0, 0, 3, 16, 16);

		Door(Properties properties) {
			super(properties);
			registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(HALF, DoubleBlockHalf.LOWER));
		}

		@Override
		protected com.mojang.serialization.MapCodec<? extends HorizontalDirectionalBlock> codec() {
			return simpleCodec(Door::new);
		}

		@Override
		protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
			builder.add(FACING, HALF);
		}

		@Override
		public BlockState getStateForPlacement(BlockPlaceContext context) {
			return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
		}

		@Override
		protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
			return switch (state.getValue(FACING)) {
				case SOUTH -> SOUTH;
				case WEST -> WEST;
				case EAST -> EAST;
				default -> NORTH;
			};
		}

		@Override
		protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
			if (player instanceof ServerPlayer serverPlayer) {
				var server = serverPlayer.level().getServer();
				// Not in the middle of the interaction: going through changes the player's dimension.
				// In the Frontrooms it is the door at the end of 398's street; in Level 0, the end of the cable.
				server.schedule(new TickTask(server.getTickCount(), () -> {
					if (serverPlayer.level().dimension() == dev.noclip.backrooms.core.Home.FRONTROOMS) {
						dev.noclip.backrooms.frontrooms.Nights398.throughDoor(serverPlayer);
					} else {
						FirstTrip.throughDoor(serverPlayer);
					}
				}));
			}
			return InteractionResult.SUCCESS;
		}
	}
}
