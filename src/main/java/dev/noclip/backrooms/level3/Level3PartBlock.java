package dev.noclip.backrooms.level3;

import dev.noclip.backrooms.core.LevelExits;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * One block of something bigger: PART says which (a generator's top or bottom, one of the eight blocks of a car).
 * FACING turns the whole thing. If it is a way out, right-clicking any part goes through.
 */
class Level3PartBlock extends Block {
	static final IntegerProperty PART = IntegerProperty.create("part", 0, 7);
	static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
	private final Level3Exit exit;
	private final Map<Direction, VoxelShape>[] partShapes;
	private final Map<Direction, VoxelShape>[] partCollisions;

	Level3PartBlock(Properties properties, Level3Exit exit) {
		this(properties, exit, null, null);
	}

	Level3PartBlock(Properties properties, Level3Exit exit, Map<Direction, VoxelShape>[] partShapes) {
		this(properties, exit, partShapes, clipParts(partShapes));
	}

	Level3PartBlock(Properties properties, Level3Exit exit, Map<Direction, VoxelShape>[] partShapes,
			Map<Direction, VoxelShape>[] partCollisions) {
		super(properties);
		this.exit = exit;
		this.partShapes = partShapes;
		this.partCollisions = partCollisions;
		registerDefaultState(stateDefinition.any().setValue(PART, 0).setValue(FACING, Direction.SOUTH));
	}

	@SuppressWarnings("unchecked")
	private static Map<Direction, VoxelShape>[] clipParts(Map<Direction, VoxelShape>[] shapes) {
		if (shapes == null) return null;
		Map<Direction, VoxelShape>[] out = new Map[shapes.length];
		for (int i = 0; i < shapes.length; i++) {
			out[i] = shapes[i] == null ? null : Level3Shapes.clipToUnit(shapes[i]);
		}
		return out;
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		if (partShapes != null) {
			int part = state.getValue(PART);
			if (part >= 0 && part < partShapes.length && partShapes[part] != null) {
				return partShapes[part].get(state.getValue(FACING));
			}
		}
		return Shapes.block();
	}

	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		if (partCollisions != null) {
			int part = state.getValue(PART);
			if (part >= 0 && part < partCollisions.length && partCollisions[part] != null) {
				return partCollisions[part].get(state.getValue(FACING));
			}
		}
		return Shapes.block();
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (exit == null) return InteractionResult.PASS;
		if (player instanceof ServerPlayer serverPlayer) {
			String target = exit.levels[0];
			if (exit.levels.length > 1) {
				// An elevator goes to one of its levels at random; if only one of them is there yet, to that one.
				String other = exit.levels[1];
				boolean pick = level.getRandom().nextBoolean();
				target = pick ? other : target;
				if (!LevelExits.isOpen(target)) target = target.equals(other) ? exit.levels[0] : other;
			}
			if (exit == Level3Exit.ELEVATOR) level.playSound(null, pos, Level3Sounds.ELEVATOR, SoundSource.BLOCKS, 1.0F, 1.0F);
			LevelExits.use(serverPlayer, target);
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(PART, FACING);
	}
}
