package dev.noclip.backrooms.props;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Multi-block Backrooms furniture / industrial prop (e.g., 2x3 fridge, 2x2 vending machine,
 * 1x2 water cooler, 2x1 sofa, 1x2 standing mirror, 2x2x2 boiler).
 * Places linked solid PropPartBlock cells across its entire (width x height x depth) footprint,
 * renders seamlessly as a full-scale Blender 3D mesh on the master block, and breaking ANY cell
 * destroys the entire structure at once while dropping the master's base salvage parts ONCE.
 */
public final class MultiPartPropBlock extends HorizontalDirectionalBlock implements EntityBlock {
	public static final MapCodec<MultiPartPropBlock> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			Codec.INT.fieldOf("width").forGetter(b -> b.width),
			Codec.INT.fieldOf("height").forGetter(b -> b.height),
			Codec.INT.fieldOf("depth").forGetter(b -> b.depth),
			propertiesCodec()
	).apply(instance, MultiPartPropBlock::new));

	private final int width;
	private final int height;
	private final int depth;

	public MultiPartPropBlock(int width, int height, int depth, Properties properties) {
		super(properties);
		this.width = Math.max(1, width);
		this.height = Math.max(1, height);
		this.depth = Math.max(1, depth);
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
	}

	public int width() {
		return width;
	}

	public int height() {
		return height;
	}

	public int depth() {
		return depth;
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
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return PropShapes.outline(this, state.getValue(FACING));
	}

	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return PropShapes.collisionAt(this, state.getValue(FACING), 0, 0, 0);
	}

	@Override
	protected RenderShape getRenderShape(BlockState state) {
		return RenderShape.INVISIBLE;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new PropBlockEntity(pos, state);
	}

	/** Computes all world BlockPos occupied by this multi-block prop anchored at masterPos. */
	public List<BlockPos> occupiedPositions(BlockPos masterPos, Direction facing) {
		Direction right = facing.getClockWise();
		Direction back = facing.getOpposite();
		List<BlockPos> list = new ArrayList<>(width * height * depth);
		for (int dy = 0; dy < height; dy++) {
			for (int dx = 0; dx < width; dx++) {
				for (int dz = 0; dz < depth; dz++) {
					list.add(masterPos.relative(right, dx).relative(back, dz).above(dy));
				}
			}
		}
		return list;
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		Direction facing = context.getHorizontalDirection().getOpposite();
		BlockPos origin = context.getClickedPos();
		Level level = context.getLevel();
		for (BlockPos cell : occupiedPositions(origin, facing)) {
			if (!level.isInWorldBounds(cell)) return null;
			if (!level.getBlockState(cell).canBeReplaced(context)) return null;
		}
		return defaultBlockState().setValue(FACING, facing);
	}

	@Override
	public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
		super.setPlacedBy(level, pos, state, placer, stack);
		if (level.isClientSide()) return;
		Direction facing = state.getValue(FACING);
		BlockState partState = BackroomsProps.PROP_PART.defaultBlockState();
		for (BlockPos cell : occupiedPositions(pos, facing)) {
			if (cell.equals(pos)) continue;
			level.setBlock(cell, partState, Block.UPDATE_ALL);
			if (level.getBlockEntity(cell) instanceof PropBlockEntity partBe) {
				partBe.setMasterPos(pos);
			}
		}
	}

	@Override
	public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
		if (level instanceof ServerLevel sl) {
			PropBreakEffects.emitMulti(sl, pos, this, state);
			clearLinkedParts(level, pos, state, pos);
		}
		return super.playerWillDestroy(level, pos, state, player);
	}

	/** Called when a player breaks ANY linked PropPartBlock cell: destroys master (dropping salvage loot ONCE) + all parts! */
	public void breakWholeStructure(Level level, BlockPos masterPos, BlockState masterState, Player player, BlockPos alreadyBreakingPos) {
		if (level instanceof ServerLevel sl) {
			PropBreakEffects.emitMulti(sl, masterPos, this, masterState);
		}
		clearLinkedParts(level, masterPos, masterState, alreadyBreakingPos);
		if (!masterPos.equals(alreadyBreakingPos)) {
			boolean dropLoot = player == null || !player.isCreative();
			level.destroyBlock(masterPos, dropLoot, player);
		}
	}

	private void clearLinkedParts(Level level, BlockPos masterPos, BlockState masterState, BlockPos skipPos) {
		Direction facing = masterState.getValue(FACING);
		for (BlockPos cell : occupiedPositions(masterPos, facing)) {
			if (cell.equals(masterPos) || cell.equals(skipPos)) continue;
			if (level.getBlockState(cell).getBlock() instanceof PropPartBlock) {
				level.setBlock(cell, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
			}
		}
	}
}
