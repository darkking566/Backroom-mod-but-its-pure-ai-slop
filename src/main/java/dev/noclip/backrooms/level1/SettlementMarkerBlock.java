package dev.noclip.backrooms.level1;

import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
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

/**
 * A settlement team's marker: a strip of bright cloth against a wall with supplies and a note tied on.
 * Right-click takes the supplies (the loot table) once and reads the note, which points toward the nearest
 * settlement; the bare cloth stays behind. FACING points away from the block it hangs on.
 */
final class SettlementMarkerBlock extends Block {
	static final EnumProperty<Direction> FACING = BlockStateProperties.FACING;
	static final BooleanProperty TAKEN = BooleanProperty.create("taken");
	private static final int SEARCH = 4;
	private static final Map<Direction, VoxelShape> SHAPES = new EnumMap<>(Map.of(
			Direction.UP, Block.box(0, 0, 0, 16, 0.5, 16),
			Direction.DOWN, Block.box(0, 15.5, 0, 16, 16, 16),
			Direction.NORTH, Block.box(0, 0, 15.5, 16, 16, 16),
			Direction.SOUTH, Block.box(0, 0, 0, 16, 16, 0.5),
			Direction.EAST, Block.box(0, 0, 0, 0.5, 16, 16),
			Direction.WEST, Block.box(15.5, 0, 0, 16, 16, 16)));

	SettlementMarkerBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(TAKEN, false));
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer serverPlayer)) return InteractionResult.SUCCESS;
		if (!state.getValue(TAKEN)) {
			server.playSound(null, pos, SoundEvents.WOOL_BREAK, SoundSource.BLOCKS, 0.8F, 1.1F);
			Block.dropResources(state, server, pos, null, player, ItemStack.EMPTY);
			server.setBlock(pos, state.setValue(TAKEN, true), Block.UPDATE_ALL);
		}
		serverPlayer.sendSystemMessage(note(server, pos));
		return InteractionResult.SUCCESS;
	}

	/** "The nearest settlement lies to the north-east, about 90 blocks": a team's note always points somewhere real. */
	private static Component note(ServerLevel level, BlockPos pos) {
		long seed = Level1ChunkGenerator.planSeed(level.getChunkSource().randomState());
		int dx0 = Math.floorDiv(pos.getX(), Level1Layout.DISTRICT), dz0 = Math.floorDiv(pos.getZ(), Level1Layout.DISTRICT);
		double best = Double.MAX_VALUE;
		int bestX = 0, bestZ = 0;
		Level1Layout.Base bestBase = Level1Layout.Base.NONE;
		for (int dx = dx0 - SEARCH; dx <= dx0 + SEARCH; dx++) {
			for (int dz = dz0 - SEARCH; dz <= dz0 + SEARCH; dz++) {
				Level1Layout.Base base = Level1Layout.base(seed, dx, dz);
				if (base == Level1Layout.Base.NONE) continue;
				int cx = dx * Level1Layout.DISTRICT + Level1Layout.DISTRICT / 2, cz = dz * Level1Layout.DISTRICT + Level1Layout.DISTRICT / 2;
				double distance = Math.hypot(cx - pos.getX(), cz - pos.getZ());
				if (distance < best) {
					best = distance;
					bestX = cx;
					bestZ = cz;
					bestBase = base;
				}
			}
		}
		if (bestBase == Level1Layout.Base.NONE) return Component.translatable("message.noclip_backrooms.marker_note_none");
		// Compass sector: 0 east, 1 south-east, 2 south, ... (z grows to the south).
		int sector = Math.floorMod((int) Math.round(Math.atan2(bestZ - pos.getZ(), bestX - pos.getX()) / (Math.PI / 4)), 8);
		return Component.translatable("message.noclip_backrooms.marker_note",
				Component.translatable("base.noclip_backrooms." + bestBase.name().toLowerCase()),
				Component.translatable("direction.noclip_backrooms." + sector),
				(int) (Math.round(best / 10) * 10));
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPES.get(state.getValue(FACING));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, TAKEN);
	}
}
