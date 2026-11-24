package dev.noclip.backrooms.core;

import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/** Putting a player down in another level: the one place that checks the spot is free and the floor is solid. */
public final class Landing {
	private Landing() {
	}

	/** Whether a player could stand with their feet at this spot: solid floor below, nothing in the way. */
	public static boolean free(ServerLevel level, ServerPlayer player, Vec3 feet) {
		BlockPos pos = BlockPos.containing(feet);
		level.getChunk(pos);
		BlockPos below = pos.below();
		if (level.getBlockState(below).getCollisionShape(level, below).isEmpty()) return false;
		return level.noCollision(player.getDimensions(player.getPose()).makeBoundingBox(feet));
	}

	/** Moves the player to the spot if it is free. */
	public static boolean tryLand(ServerPlayer player, ServerLevel level, Vec3 feet) {
		if (player.isRemoved() || player.hasDisconnected() || !player.isAlive()) return false;
		if (!free(level, player, feet)) return false;
		player.stopRiding();
		player.teleportTo(level, feet.x, feet.y, feet.z, Set.of(), player.getYRot(), 0, true);
		player.setDeltaMovement(Vec3.ZERO);
		player.resetFallDistance();
		return true;
	}
}
