package dev.noclip.backrooms.level1;

import dev.noclip.backrooms.levels.LevelExits;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A door frame standing on its own. Walking through it does nothing, unless you keep going back and forth
 * through the same frame: the fourth pass in a short while takes you somewhere else.
 */
final class DoorFrameBlock extends ShapedBlock {
	static final int PASSES = 4;
	private static final int GAP = 4, FORGET = 30 * 20;

	private record Passes(BlockPos frame, int count, int lastTick) {
	}

	private static final Map<UUID, Passes> PASSING = new HashMap<>();
	private final String level;

	DoorFrameBlock(Properties properties, VoxelShape shape, String level) {
		super(properties, shape);
		this.level = level;
	}

	@Override
	protected void entityInside(BlockState state, Level world, BlockPos pos, Entity entity, InsideBlockEffectApplier effects, boolean intersects) {
		if (!(entity instanceof ServerPlayer player)) return;
		int now = player.level().getServer().getTickCount();
		Passes passes = PASSING.get(player.getUUID());
		if (passes != null && passes.frame().equals(pos) && now - passes.lastTick() <= GAP) {
			// Still in the same pass.
			PASSING.put(player.getUUID(), new Passes(passes.frame(), passes.count(), now));
			return;
		}
		boolean counting = passes != null && passes.frame().equals(pos) && now - passes.lastTick() <= FORGET;
		int count = counting ? passes.count() + 1 : 1;
		if (count >= PASSES) {
			PASSING.remove(player.getUUID());
			LevelExits.use(player, level);
			return;
		}
		PASSING.put(player.getUUID(), new Passes(pos.immutable(), count, now));
	}
}
