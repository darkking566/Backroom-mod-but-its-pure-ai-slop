package dev.noclip.backrooms.level3;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A gate in a set of bars, two blocks high. It is locked; the lock can be picked: keep at it (right-click) and
 * after a few good turns it gives and the gate swings open, for good. The bars around it cannot be broken.
 */
final class Level3GateBlock extends Block {
	static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;
	static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;
	static final BooleanProperty OPEN = BlockStateProperties.OPEN;
	/** Good turns of the pick it takes. */
	static final int PICKS = 6;
	private static final int FORGET_AFTER = 100;
	private static final VoxelShape[] CLOSED = Level3Shapes.axis(0, 0, 7, 16, 16, 9);
	private static final Map<BlockPos, int[]> PROGRESS = new HashMap<>();

	Level3GateBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(AXIS, Direction.Axis.X).setValue(HALF, DoubleBlockHalf.LOWER).setValue(OPEN, false));
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return state.getValue(OPEN) ? Shapes.empty() : CLOSED[state.getValue(AXIS) == Direction.Axis.X ? 0 : 1];
	}

	@Override
	protected VoxelShape getVisualShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return CLOSED[state.getValue(AXIS) == Direction.Axis.X ? 0 : 1];
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (state.getValue(OPEN)) return InteractionResult.PASS;
		if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer serverPlayer)) return InteractionResult.SUCCESS;
		BlockPos bottom = state.getValue(HALF) == DoubleBlockHalf.LOWER ? pos : pos.below();
		int now = server.getServer().getTickCount();
		int[] progress = PROGRESS.computeIfAbsent(bottom, k -> new int[] {0, now});
		if (now - progress[1] > FORGET_AFTER || now < progress[1]) progress[0] = 0;
		progress[1] = now;
		// The pick slips now and then.
		if (server.getRandom().nextInt(100) < 70) progress[0]++;
		server.playSound(null, pos, Level3Sounds.LOCKPICK, SoundSource.BLOCKS, 0.7F, 1.4F + server.getRandom().nextFloat() * 0.4F);
		if (progress[0] < PICKS) {
			serverPlayer.sendOverlayMessage(Component.translatable("message.noclip_backrooms.level3.picking", progress[0], PICKS));
			return InteractionResult.SUCCESS;
		}
		PROGRESS.remove(bottom);
		open(server, bottom);
		serverPlayer.sendOverlayMessage(Component.translatable("message.noclip_backrooms.level3.picked"));
		return InteractionResult.SUCCESS;
	}

	static void open(ServerLevel level, BlockPos bottom) {
		BlockState lower = level.getBlockState(bottom), upper = level.getBlockState(bottom.above());
		if (lower.getBlock() instanceof Level3GateBlock) level.setBlock(bottom, lower.setValue(OPEN, true), Block.UPDATE_ALL);
		if (upper.getBlock() instanceof Level3GateBlock) level.setBlock(bottom.above(), upper.setValue(OPEN, true), Block.UPDATE_ALL);
		level.playSound(null, bottom, Level3Sounds.GATE_OPEN, SoundSource.BLOCKS, 1.0F, 0.8F);
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(AXIS, HALF, OPEN);
	}
}
