package dev.noclip.backrooms.level3;

import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** A sign plate on a wall. FACING points out from the wall; RIGHT, which way an arrow on it points as you face it. */
final class Level3SignBlock extends Block {
	static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
	static final BooleanProperty RIGHT = BooleanProperty.create("right");
	private static final Map<Direction, VoxelShape> SHAPES = Level3Shapes.facing(2, 3, 0, 14, 13, 1);

	Level3SignBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.SOUTH).setValue(RIGHT, true));
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (!state.is(Level3Blocks.MEG_ARROW_SIGN) || !(level instanceof ServerLevel serverLevel)
				|| serverLevel.dimension() != Level3.LEVEL_3 || !(player instanceof ServerPlayer sp)) {
			return InteractionResult.PASS;
		}
		long seed = Level3ChunkGenerator.planSeed(serverLevel.getChunkSource().randomState());
		int[] gate = GammaPlan.nearestGate(seed, pos.getX(), pos.getZ());
		if (gate == null) return InteractionResult.PASS;
		int dx = gate[0] - pos.getX(), dz = gate[1] - pos.getZ();
		int dist = Math.max(1, (int) Math.round(Math.hypot(dx, dz)));
		sp.sendOverlayMessage(Component.translatable("message.noclip_backrooms.level3.gamma_sign_dir", compass(dx, dz), dist)
				.withStyle(ChatFormatting.AQUA));
		return InteractionResult.SUCCESS;
	}

	private static Component compass(int dx, int dz) {
		String key;
		if (Math.abs(dx) > Math.abs(dz) * 2) key = dx > 0 ? "east" : "west";
		else if (Math.abs(dz) > Math.abs(dx) * 2) key = dz > 0 ? "south" : "north";
		else key = (dz < 0 ? "north_" : "south_") + (dx > 0 ? "east" : "west");
		return Component.translatable("direction.noclip_backrooms." + key);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPES.get(state.getValue(FACING));
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		Direction clicked = context.getClickedFace();
		return defaultBlockState().setValue(FACING, clicked.getAxis().isHorizontal() ? clicked : context.getHorizontalDirection().getOpposite());
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, RIGHT);
	}
}
