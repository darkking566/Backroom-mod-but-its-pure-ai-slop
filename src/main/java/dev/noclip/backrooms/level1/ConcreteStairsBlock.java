package dev.noclip.backrooms.level1;

import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;

/** Plain concrete stairs, for the stairwells out of the parking structure. */
final class ConcreteStairsBlock extends StairBlock {
	ConcreteStairsBlock(BlockState base, Properties properties) {
		super(base, properties);
	}
}
