package dev.noclip.backrooms.props;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Invisible collision filler block for multi-block Backrooms furniture (2x3 fridge, 2x2 vending
 * machine, 2x1 sofa, 1x2 water cooler, 2x2x2 boiler, etc.). Breaking ANY part destroys the entire
 * multi-block structure and drops the master block's base salvage parts ONCE.
 */
public final class PropPartBlock extends Block implements EntityBlock {
	public static final MapCodec<PropPartBlock> CODEC = simpleCodec(PropPartBlock::new);

	public PropPartBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected MapCodec<? extends Block> codec() {
		return CODEC;
	}

	@Override
	protected RenderShape getRenderShape(BlockState state) {
		return RenderShape.INVISIBLE;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new PropBlockEntity(pos, state);
	}

	@Override
	protected ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData) {
		if (level.getBlockEntity(pos) instanceof PropBlockEntity be) {
			BlockPos master = be.masterPos();
			if (!master.equals(pos)) {
				BlockState masterState = level.getBlockState(master);
				if (masterState.getBlock() instanceof MultiPartPropBlock) {
					return new ItemStack(masterState.getBlock());
				}
			}
		}
		return ItemStack.EMPTY;
	}

	@Override
	protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
			Direction dir, BlockPos neighborPos, BlockState neighborState, RandomSource random) {
		if (level.getBlockEntity(pos) instanceof PropBlockEntity be) {
			BlockPos master = be.masterPos();
			if (!master.equals(pos) && !(level.getBlockState(master).getBlock() instanceof MultiPartPropBlock)) {
				return Blocks.AIR.defaultBlockState();
			}
		}
		return super.updateShape(state, level, ticks, pos, dir, neighborPos, neighborState, random);
	}

	@Override
	public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
		if (!level.isClientSide() && level.getBlockEntity(pos) instanceof PropBlockEntity be) {
			BlockPos masterPos = be.masterPos();
			if (!masterPos.equals(pos)) {
				BlockState masterState = level.getBlockState(masterPos);
				if (masterState.getBlock() instanceof MultiPartPropBlock multi) {
					multi.breakWholeStructure(level, masterPos, masterState, player, pos);
				}
			}
		}
		return super.playerWillDestroy(level, pos, state, player);
	}
}
