package dev.noclip.backrooms.level3;

import dev.noclip.backrooms.core.LevelExits;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * One block of something bigger: PART says which (a generator's top or bottom, one of the eight blocks of a car).
 * FACING turns the whole thing. If it is a way out, right-clicking any part goes through.
 */
class Level3PartBlock extends Block {
	static final IntegerProperty PART = IntegerProperty.create("part", 0, 7);
	static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
	private final Level3Exit exit;

	Level3PartBlock(Properties properties, Level3Exit exit) {
		super(properties);
		this.exit = exit;
		registerDefaultState(stateDefinition.any().setValue(PART, 0).setValue(FACING, Direction.SOUTH));
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
