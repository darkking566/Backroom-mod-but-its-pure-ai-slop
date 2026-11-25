package dev.noclip.backrooms.level0p1;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/**
 * The station's doors are automatic: they slide open when anyone comes within three blocks. They shut again on their
 * own (see {@link ZenithDoorBlock}), so the doors that "still work" in the Wiki's description actually do.
 */
final class ZenithDoors {
	private static final int REACH = 4;

	private ZenithDoors() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(ZenithDoors::tick);
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % 6 != 0) return;
		ServerLevel level = server.getLevel(Level0p1.LEVEL_0_1);
		if (level == null) return;
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (ServerPlayer player : level.players()) {
			if (player.isSpectator()) continue;
			BlockPos feet = player.blockPosition();
			for (int dx = -REACH; dx <= REACH; dx++) {
				for (int dz = -REACH; dz <= REACH; dz++) {
					for (int dy = -1; dy <= 2; dy++) {
						pos.set(feet.getX() + dx, feet.getY() + dy, feet.getZ() + dz);
						BlockState state = level.getBlockState(pos);
						if (!(state.getBlock() instanceof ZenithDoorBlock door)) continue;
						if (state.getValue(DoorBlock.HALF) != DoubleBlockHalf.LOWER || state.getValue(DoorBlock.OPEN)) continue;
						if (player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5) > 3.2 * 3.2) continue;
						door.setOpen(player, level, state, pos, true); // opens its partner too, and plays the door's own sound
					}
				}
			}
		}
	}
}
