package dev.noclip.backrooms.level1;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
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
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Full-scale multi-block abandoned Level 1 vehicle master block:
 * <ul>
 *   <li>{@link Kind#SEDAN}: 2 wide x 2 tall x 4 long</li>
 *   <li>{@link Kind#VAN}: 2 wide x 2 tall x 5 long</li>
 * </ul>
 * World rendering is handled by {@code Level1VehicleRenderer}; non-master occupied cells hold
 * {@link Level1VehiclePartBlock} collision proxies pointing back to this block.
 */
public final class Level1VehicleBlock extends HorizontalDirectionalBlock implements EntityBlock {
	public enum Kind {
		SEDAN("sedan", -1, 2),
		VAN("van", -2, 2);

		public final String id;
		public final int minZ;
		public final int maxZ;

		Kind(String id, int minZ, int maxZ) {
			this.id = id;
			this.minZ = minZ;
			this.maxZ = maxZ;
		}
	}

	public static final MapCodec<Level1VehicleBlock> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
			Codec.STRING.fieldOf("kind").forGetter(b -> b.kind.id),
			propertiesCodec()
	).apply(i, (k, p) -> new Level1VehicleBlock(p, "van".equals(k) ? Kind.VAN : Kind.SEDAN)));

	private final Kind kind;

	public Level1VehicleBlock(Properties properties, Kind kind) {
		super(properties);
		this.kind = kind;
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
	}

	public Kind kind() {
		return kind;
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
		return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
	}

	@Override
	protected RenderShape getRenderShape(BlockState state) {
		return RenderShape.INVISIBLE;
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return Shapes.block();
	}

	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return Shapes.block();
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new Level1VehicleBlockEntity(pos, state);
	}

	/** Transforms NORTH-local offset (localX in 0..1, localZ in minZ..maxZ) to world XZ delta. */
	public static int[] rotatedOffset(Direction facing, int localX, int localZ) {
		return switch (facing) {
			case WEST -> new int[]{localZ, -localX};
			case SOUTH -> new int[]{-localX, -localZ};
			case EAST -> new int[]{-localZ, localX};
			default -> new int[]{localX, localZ};
		};
	}

	/** Places missing {@link Level1VehiclePartBlock} proxies in empty cells of this vehicle's footprint. */
	public void ensureParts(Level level, BlockPos masterPos, BlockState state) {
		Direction facing = state.getValue(FACING);
		boolean isVan = kind == Kind.VAN;
		for (int lx = 0; lx <= 1; lx++) {
			for (int lz = kind.minZ; lz <= kind.maxZ; lz++) {
				int[] delta = rotatedOffset(facing, lx, lz);
				int dx = delta[0], dz = delta[1];
				for (int dy = 0; dy <= 1; dy++) {
					if (dx == 0 && dy == 0 && dz == 0) continue;
					BlockPos cell = masterPos.offset(dx, dy, dz);
					BlockState existing = level.getBlockState(cell);
					if (!existing.isAir() && !existing.is(Level1Blocks.VEHICLE_PART)) continue;
					BlockState part = Level1Blocks.VEHICLE_PART.defaultBlockState()
							.setValue(Level1VehiclePartBlock.DX, dx + 2)
							.setValue(Level1VehiclePartBlock.DY, dy)
							.setValue(Level1VehiclePartBlock.DZ, dz + 2)
							.setValue(Level1VehiclePartBlock.VAN, isVan);
					if (!existing.equals(part)) {
						level.setBlock(cell, part, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
					}
				}
			}
		}
	}

	@Override
	public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
		super.setPlacedBy(level, pos, state, placer, stack);
		if (!level.isClientSide()) {
			ensureParts(level, pos, state);
		}
	}

	@Override
	public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
		if (!level.isClientSide()) {
			clearParts(level, pos, state, pos);
		}
		return super.playerWillDestroy(level, pos, state, player);
	}

	public void breakWholeStructure(Level level, BlockPos masterPos, BlockState masterState, Player player, BlockPos skipPos) {
		clearParts(level, masterPos, masterState, skipPos);
		if (!masterPos.equals(skipPos)) {
			boolean dropLoot = player == null || !player.isCreative();
			level.destroyBlock(masterPos, dropLoot, player);
		}
	}

	private void clearParts(Level level, BlockPos masterPos, BlockState state, BlockPos skipPos) {
		Direction facing = state.getValue(FACING);
		for (int lx = 0; lx <= 1; lx++) {
			for (int lz = kind.minZ; lz <= kind.maxZ; lz++) {
				int[] delta = rotatedOffset(facing, lx, lz);
				for (int dy = 0; dy <= 1; dy++) {
					if (delta[0] == 0 && dy == 0 && delta[1] == 0) continue;
					BlockPos cell = masterPos.offset(delta[0], dy, delta[1]);
					if (cell.equals(skipPos)) continue;
					if (level.getBlockState(cell).is(Level1Blocks.VEHICLE_PART)) {
						level.setBlock(cell, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
					}
				}
			}
		}
	}

	public static void populateChunkParts(ServerLevel level, Iterable<BlockPos> candidates) {
		for (BlockPos pos : candidates) {
			if (!level.isLoaded(pos)) continue;
			BlockState state = level.getBlockState(pos);
			if (state.getBlock() instanceof Level1VehicleBlock vb) {
				vb.ensureParts(level, pos, state);
			}
		}
	}
}
