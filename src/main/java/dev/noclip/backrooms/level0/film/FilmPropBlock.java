package dev.noclip.backrooms.level0.film;

import com.mojang.serialization.MapCodec;
import dev.noclip.backrooms.props.PropBlockEntity;
import dev.noclip.backrooms.props.PropShapes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A one-block film prop. The block is invisible; its Blender mesh is drawn through the props' block entity
 * (props.PropBlockEntity, client props.BlenderPropRenderer). How it sits decides its box: on the floor, flat on the
 * floor (walked over), hung on a wall (its back against the wall it was put on), or hung from the ceiling.
 */
public final class FilmPropBlock extends HorizontalDirectionalBlock implements EntityBlock {
	public enum Mount { FLOOR, SMALL, LOW, WALL, CEILING }

	public static final MapCodec<FilmPropBlock> CODEC = simpleCodec(p -> new FilmPropBlock(Mount.FLOOR, p));
	private static final VoxelShape FLOOR = Block.box(1, 0, 1, 15, 16, 15), SMALL = Block.box(2, 0, 2, 14, 8, 14),
			LOW = Block.box(0, 0, 0, 16, 2, 16), CEILING = Block.box(2, 4, 2, 14, 16, 14);
	// Hung on a wall: a thin slab against the wall behind the prop's front (front = FACING).
	private static final VoxelShape WALL_N = Block.box(0, 1, 14, 16, 15, 16), WALL_S = Block.box(0, 1, 0, 16, 15, 2),
			WALL_E = Block.box(0, 1, 0, 2, 15, 16), WALL_W = Block.box(14, 1, 0, 16, 15, 16);

	private final Mount mount;

	public FilmPropBlock(Mount mount, Properties properties) {
		super(properties);
		this.mount = mount;
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
	}

	public Mount mount() {
		return mount;
	}

	@Override
	protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		Direction clicked = context.getClickedFace();
		if (mount == Mount.WALL && clicked.getAxis().isHorizontal()) return defaultBlockState().setValue(FACING, clicked);
		return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		if (PropShapes.hasShape(this)) {
			return PropShapes.outline(this, state.getValue(FACING));
		}
		return switch (mount) {
			case FLOOR -> FLOOR;
			case SMALL -> SMALL;
			case LOW -> LOW;
			case CEILING -> CEILING;
			case WALL -> switch (state.getValue(FACING)) {
				case SOUTH -> WALL_S;
				case EAST -> WALL_E;
				case WEST -> WALL_W;
				default -> WALL_N;
			};
		};
	}

	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		if (mount != Mount.FLOOR && mount != Mount.SMALL) {
			return Shapes.empty();
		}
		return PropShapes.hasShape(this)
				? PropShapes.collisionAt(this, state.getValue(FACING), 0, 0, 0)
				: getShape(state, level, pos, context);
	}

	@Override
	protected RenderShape getRenderShape(BlockState state) {
		return RenderShape.INVISIBLE;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new PropBlockEntity(pos, state);
	}
}
