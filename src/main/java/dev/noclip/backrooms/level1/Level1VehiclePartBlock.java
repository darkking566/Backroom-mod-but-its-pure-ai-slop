package dev.noclip.backrooms.level1;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Invisible solid collision & interaction cell belonging to a {@link Level1VehicleBlock}.
 * Encodes relative offset to its master block directly in blockstate properties so zero BlockEntity is needed.
 */
public final class Level1VehiclePartBlock extends Block {
	public static final IntegerProperty DX = IntegerProperty.create("dx", 0, 4);
	public static final IntegerProperty DY = IntegerProperty.create("dy", 0, 1);
	public static final IntegerProperty DZ = IntegerProperty.create("dz", 0, 4);
	public static final BooleanProperty VAN = BooleanProperty.create("van");

	public static final MapCodec<Level1VehiclePartBlock> CODEC = simpleCodec(Level1VehiclePartBlock::new);

	private static final VoxelShape SEDAN_TOP = Block.box(0, 0, 0, 16, 8, 16);
	private static final VoxelShape VAN_TOP = Block.box(0, 0, 0, 16, 16, 16);

	public Level1VehiclePartBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any()
				.setValue(DX, 2)
				.setValue(DY, 0)
				.setValue(DZ, 2)
				.setValue(VAN, false));
	}

	@Override
	protected MapCodec<? extends Block> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(DX, DY, DZ, VAN);
	}

	public static BlockPos masterPos(BlockPos partPos, BlockState state) {
		int dx = state.getValue(DX) - 2;
		int dy = state.getValue(DY);
		int dz = state.getValue(DZ) - 2;
		return partPos.offset(-dx, -dy, -dz);
	}

	@Override
	protected RenderShape getRenderShape(BlockState state) {
		return RenderShape.INVISIBLE;
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		if (state.getValue(DY) == 0) return Shapes.block();
		return state.getValue(VAN) ? VAN_TOP : SEDAN_TOP;
	}

	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return getShape(state, level, pos, context);
	}

	@Override
	protected ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData) {
		BlockState master = level.getBlockState(masterPos(pos, state));
		return master.getBlock() instanceof Level1VehicleBlock
				? new ItemStack(master.getBlock())
				: ItemStack.EMPTY;
	}

	@Override
	public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
		if (!level.isClientSide()) {
			BlockPos master = masterPos(pos, state);
			BlockState mState = level.getBlockState(master);
			if (mState.getBlock() instanceof Level1VehicleBlock vb) {
				vb.breakWholeStructure(level, master, mState, player, pos);
			}
		}
		return super.playerWillDestroy(level, pos, state, player);
	}
}
