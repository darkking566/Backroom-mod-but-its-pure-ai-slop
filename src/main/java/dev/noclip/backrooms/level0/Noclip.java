package dev.noclip.backrooms.level0;

import dev.noclip.backrooms.BackroomsMod;
import java.util.Set;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Suffocating in an Overworld block drops the player into Level 0. */
final class Noclip {
	private static final Logger LOGGER = LoggerFactory.getLogger(BackroomsMod.MOD_ID);
	private static final int LANDING_ATTEMPTS = 64;

	private Noclip() {
	}

	static void register() {
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			if (!(entity instanceof ServerPlayer player) || !source.is(DamageTypes.IN_WALL)) return true;
			if (player.level().dimension() != Level.OVERWORLD) return true;

			MinecraftServer server = player.level().getServer();
			ServerLevel level0 = server.getLevel(Level0.LEVEL_0);
			if (level0 == null) return true;

			// schedule, not execute: execute runs immediately here, and changing dimension mid-hurt is unsafe.
			server.schedule(new TickTask(server.getTickCount(), () -> enter(player, level0)));
			return false;
		});
	}

	private static void enter(ServerPlayer player, ServerLevel level0) {
		if (player.isRemoved() || player.hasDisconnected() || !player.isAlive()) return;
		// Several hits can be queued before the first teleport runs.
		if (player.level().dimension() != Level.OVERWORLD) return;

		int cellX = Math.floorDiv(player.getBlockX(), Maze.CELL);
		int cellZ = Math.floorDiv(player.getBlockZ(), Maze.CELL);

		// Prefer cell centres; if a centre is blocked skip to the next cell, and if it is an open pit step onto the walkway.
		for (int i = 0; i < LANDING_ATTEMPTS; i++) {
			int baseX = (cellX + i) * Maze.CELL;
			int baseZ = cellZ * Maze.CELL;
			Vec3 centre = new Vec3(baseX + Maze.CENTER + 0.5, Level0ChunkGenerator.FLOOR_Y + 1, baseZ + Maze.CENTER + 0.5);
			BlockPos centreFeet = BlockPos.containing(centre);
			level0.getChunk(centreFeet);
			boolean centreHasFloor = !level0.getBlockState(centreFeet.below()).getCollisionShape(level0, centreFeet.below()).isEmpty();
			Vec3 landing = centreHasFloor ? centre
					: new Vec3(baseX + 1.5, Level0ChunkGenerator.FLOOR_Y + 1, baseZ + 1.5);
			BlockPos floorPos = BlockPos.containing(landing).below();
			if (Level0Arrival.inRedRoom(level0, floorPos)) continue;
			if (level0.getBlockState(floorPos).getCollisionShape(level0, floorPos).isEmpty()) continue;
			if (!level0.noCollision(player.getDimensions(player.getPose()).makeBoundingBox(landing))) continue;

			player.stopRiding();
			player.teleportTo(level0, landing.x, landing.y, landing.z, Set.of(), player.getYRot(), 0, true);
			player.setDeltaMovement(Vec3.ZERO);
			player.resetFallDistance();
			return;
		}
		LOGGER.warn("No free landing spot in Level 0 near cell {}, {}", cellX, cellZ);
	}
}
