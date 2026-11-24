package dev.noclip.backrooms.level2;

import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A working light. Level 2's lights are wired in series: break one and every light on its circuit goes out
 * with it (each turns into its broken counterpart). Broken lights are plain Level2AxisBlocks.
 */
final class Level2LightBlock extends Level2AxisBlock {
	private final Supplier<Block> broken;

	Level2LightBlock(Properties properties, VoxelShape[] shape, Supplier<Block> broken) {
		super(properties, shape[0], shape[1]);
		this.broken = broken;
	}

	Block broken() {
		return broken.get();
	}

	@Override
	protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
		super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
		// Only when the light is gone, not when the circuit swaps it for its broken self.
		if (level.dimension() == Level2.LEVEL_2 && level.getBlockState(pos).isAir()) cut(level, pos);
	}

	/** Takes down every light on the circuit around pos. Returns how many went out. */
	static int cut(ServerLevel level, BlockPos pos) {
		int[] box = Level2Layout.circuit(pos.getX(), pos.getZ());
		int out = 0;
		BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
		for (int x = box[0]; x <= box[2]; x++) {
			for (int z = box[1]; z <= box[3]; z++) {
				BlockState light = level.getBlockState(at.set(x, pos.getY(), z));
				if (!(light.getBlock() instanceof Level2LightBlock lamp)) continue;
				level.setBlock(at, lamp.broken().defaultBlockState().setValue(AXIS, light.getValue(AXIS)), Block.UPDATE_ALL);
				out++;
			}
		}
		if (out > 0) {
			level.playSound(null, pos, Level2Sounds.CIRCUIT_DOWN, SoundSource.BLOCKS, 1.0F, 0.8F);
		}
		return out;
	}
}
