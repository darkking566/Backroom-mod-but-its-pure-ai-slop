package dev.noclip.backrooms.core;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * A block that is a way to another level: right-click and you go through. What happens is decided by a {@link Use}
 * (a fixed level key, "back to the parent", or a random cut-out), so sublevels need no block class of their own.
 */
public class LevelExitBlock extends Block {
	public interface Use {
		void go(ServerPlayer player);
	}

	private final Use use;

	public LevelExitBlock(Properties properties, Use use) {
		super(properties);
		this.use = use;
	}

	public static Use to(String level) {
		return player -> LevelExits.use(player, level);
	}

	public static Use leave(String sublevel) {
		return player -> Sublevels.leave(player, sublevel);
	}

	public static Use cutOut(String sublevel) {
		return player -> Sublevels.cutOut(player, sublevel);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (player instanceof ServerPlayer serverPlayer) use.go(serverPlayer);
		return InteractionResult.SUCCESS;
	}
}
