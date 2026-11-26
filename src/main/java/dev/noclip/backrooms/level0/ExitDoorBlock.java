package dev.noclip.backrooms.level0;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Using either half of an exit door in Level 0 calls M.E.G. for a pickup: hold out beside it, and they take the player
 * to the institute in the Frontrooms, alive and with their items.
 * The sign above shares the block for its facing only. AXIS is the way the door faces; world generation keeps x.
 */
final class ExitDoorBlock extends Block {
	static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;
	private final boolean door;

	ExitDoorBlock(Properties properties, boolean door) {
		super(properties);
		this.door = door;
		registerDefaultState(stateDefinition.any().setValue(AXIS, Direction.Axis.X));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(AXIS);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return defaultBlockState().setValue(AXIS, context.getHorizontalDirection().getAxis());
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (!door || level.dimension() != Level0.LEVEL_0) return InteractionResult.PASS;

		if (player instanceof ServerPlayer serverPlayer) {
			MinecraftServer server = serverPlayer.level().getServer();
			// Not mid-interaction, for the same reason as Noclip.
			server.schedule(new TickTask(server.getTickCount(), () -> leave(serverPlayer)));
		}
		return InteractionResult.SUCCESS;
	}

	private static void leave(ServerPlayer player) {
		if (player.isRemoved() || player.hasDisconnected() || !player.isAlive()) return;
		// A double click queues this twice.
		if (player.level().dimension() != Level0.LEVEL_0) return;

		// Every known way out is watched by M.E.G. (docs/project/1-gameplay/README.md): the door is a pickup point. Hold out
		// beside it until they come, and they take you to the institute in the Frontrooms (core.trip.Extraction, core.Home).
		dev.noclip.backrooms.core.trip.Extraction.start(player, player.position(), 6.0);
	}
}
