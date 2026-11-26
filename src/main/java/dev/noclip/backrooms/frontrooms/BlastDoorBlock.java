package dev.noclip.backrooms.frontrooms;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A blast door between the institute's wings. It stays shut until the team has met {@code requires} quotas; then
 * {@link Institute#syncDoors} slides it open for good (open, nothing is left in the doorway but the frame).
 */
public final class BlastDoorBlock extends Block {
	public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;
	public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;
	public static final BooleanProperty OPEN = BlockStateProperties.OPEN;
	public static final IntegerProperty REQUIRES = IntegerProperty.create("requires", 0, 5);
	private static final VoxelShape ALONG_X = Block.box(0, 0, 5, 16, 16, 11), ALONG_Z = Block.box(5, 0, 0, 11, 16, 16);

	BlastDoorBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(HALF, DoubleBlockHalf.LOWER).setValue(AXIS, Direction.Axis.X)
				.setValue(OPEN, false).setValue(REQUIRES, 1));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(HALF, AXIS, OPEN, REQUIRES);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		if (state.getValue(OPEN)) return Shapes.empty();
		return state.getValue(AXIS) == Direction.Axis.X ? ALONG_X : ALONG_Z;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (state.getValue(OPEN)) return InteractionResult.PASS;
		if (player instanceof ServerPlayer serverPlayer) {
			int requires = state.getValue(REQUIRES);
			serverPlayer.sendOverlayMessage(Component.translatable("message.noclip_backrooms.institute.door_locked", requires,
					Component.translatable("institute.noclip_backrooms.wing." + requires)).withStyle(ChatFormatting.RED));
			level.playSound(null, pos, SoundEvents.IRON_DOOR_CLOSE, SoundSource.BLOCKS, 0.6F, 0.6F);
		}
		return InteractionResult.SUCCESS;
	}
}
