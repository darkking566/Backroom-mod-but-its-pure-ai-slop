package dev.noclip.backrooms.props;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Material-matched break debris particles and accent sounds for Backrooms 3D props.
 * Multi-block and Blender-rendered props use RenderShape.INVISIBLE on their block states,
 * so explicit debris bursts across every occupied cell make salvaging feel tactile and clear.
 */
final class PropBreakEffects {
	private PropBreakEffects() {
	}

	static void emitCell(ServerLevel level, BlockPos pos, BlockState propState) {
		emitBurst(level, pos, propState.getSoundType(), true);
	}

	static void emitMulti(ServerLevel level, BlockPos masterPos, MultiPartPropBlock multi, BlockState masterState) {
		SoundType sound = masterState.getSoundType();
		boolean first = true;
		for (BlockPos cell : multi.occupiedPositions(masterPos, masterState.getValue(MultiPartPropBlock.FACING))) {
			emitBurst(level, cell, sound, first);
			first = false;
		}
	}

	private static void emitBurst(ServerLevel level, BlockPos pos, SoundType sound, boolean playAccentSound) {
		double cx = pos.getX() + 0.5;
		double cy = pos.getY() + 0.5;
		double cz = pos.getZ() + 0.5;

		BlockState debris;
		SoundEvent accent;
		float pitch;
		if (sound == SoundType.WOOD) {
			debris = Blocks.OAK_PLANKS.defaultBlockState();
			accent = SoundEvents.WOOD_BREAK;
			pitch = 0.85F;
			level.sendParticles(ParticleTypes.POOF, cx, cy, cz, 4, 0.28, 0.28, 0.28, 0.02);
		} else if (sound == SoundType.GLASS) {
			debris = Blocks.GLASS.defaultBlockState();
			accent = SoundEvents.GLASS_BREAK;
			pitch = 1.05F;
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, cx, cy, cz, 6, 0.3, 0.3, 0.3, 0.08);
		} else if (sound == SoundType.METAL || sound == SoundType.ANVIL || sound == SoundType.CHAIN) {
			debris = Blocks.IRON_BLOCK.defaultBlockState();
			accent = SoundEvents.IRON_TRAPDOOR_CLOSE;
			pitch = 0.72F;
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, cx, cy, cz, 7, 0.32, 0.32, 0.32, 0.09);
			level.sendParticles(ParticleTypes.SMOKE, cx, cy, cz, 4, 0.25, 0.25, 0.25, 0.02);
		} else if (sound == SoundType.WOOL) {
			debris = Blocks.WOOL.white().defaultBlockState();
			accent = SoundEvents.WOOL_BREAK;
			pitch = 0.88F;
			level.sendParticles(ParticleTypes.POOF, cx, cy, cz, 6, 0.3, 0.25, 0.3, 0.03);
		} else if (sound == SoundType.AMETHYST) {
			debris = Blocks.AMETHYST_BLOCK.defaultBlockState();
			accent = SoundEvents.AMETHYST_CLUSTER_BREAK;
			pitch = 0.92F;
			level.sendParticles(ParticleTypes.CRIMSON_SPORE, cx, cy, cz, 12, 0.35, 0.35, 0.35, 0.04);
			level.sendParticles(ParticleTypes.PORTAL, cx, cy, cz, 8, 0.3, 0.3, 0.3, 0.06);
		} else if (sound == SoundType.BONE_BLOCK) {
			debris = Blocks.BONE_BLOCK.defaultBlockState();
			accent = SoundEvents.BONE_BLOCK_BREAK;
			pitch = 0.82F;
			level.sendParticles(ParticleTypes.ASH, cx, cy, cz, 8, 0.3, 0.25, 0.3, 0.02);
		} else if (sound == SoundType.GRASS) {
			debris = Blocks.OAK_LEAVES.defaultBlockState();
			accent = SoundEvents.GRASS_BREAK;
			pitch = 1.0F;
		} else {
			debris = Blocks.STONE.defaultBlockState();
			accent = SoundEvents.STONE_BREAK;
			pitch = 0.88F;
			level.sendParticles(ParticleTypes.POOF, cx, cy, cz, 4, 0.28, 0.28, 0.28, 0.02);
		}

		level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, debris),
				cx, cy, cz, 16, 0.32, 0.32, 0.32, 0.14);
		if (playAccentSound) {
			level.playSound(null, pos, accent, SoundSource.BLOCKS, 0.75F, pitch);
		}
	}
}
