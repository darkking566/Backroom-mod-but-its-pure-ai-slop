package dev.noclip.backrooms.level0;

import dev.noclip.backrooms.BackroomsMod;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleBuilder;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRuleCategory;
import net.minecraft.world.phys.Vec3;

/**
 * Plan C non-Euclidean Stage Loop for Level 0:
 * Every 300x300-block Stage (5x5 sectors) forms a dense self-contained labyrinth containing all 10 zones,
 * landmarks, micro-events, and branching exits. When a survival/adventure player walks past the outer 4-block
 * rim of their active Stage, they seamlessly wrap around to the opposite side of the same Stage with a subtle
 * fluorescent flicker—making the finite high-density map feel endless with zero invisible walls.
 *
 * Creative/spectator players and explicit admin teleports (> 24 blocks jump) smoothly switch to the destination
 * Stage instead of being pulled back.
 */
final class StageLoop {
	private static GameRule<Boolean> stageLoopRule;
	private static final int CHECK_INTERVAL_TICKS = 4;
	private static final int WRAP_MARGIN = 4;
	private static final int WRAP_COOLDOWN_TICKS = 30;
	private static final double TELEPORT_JUMP_THRESHOLD = 24.0;

	private static final Map<UUID, StageTracker> TRACKERS = new HashMap<>();

	private StageLoop() {
	}

	static void register() {
		if (stageLoopRule == null) {
			stageLoopRule = GameRuleBuilder.forBoolean(true)
					.category(GameRuleCategory.MISC)
					.buildAndRegister(BackroomsMod.id("stage_loop"));
		}
		ServerTickEvents.END_SERVER_TICK.register(StageLoop::tick);
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
				Commands.literal("backrooms")
						.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
						.then(Commands.literal("stage")
								.then(Commands.literal("info").executes(ctx -> {
									ServerPlayer player = ctx.getSource().getPlayer();
									if (player == null) return 0;
									int stx = stageXOfBlock(player.getBlockX());
									int stz = stageZOfBlock(player.getBlockZ());
									int minX = stx * Maze.STAGE_BLOCKS;
									int minZ = stz * Maze.STAGE_BLOCKS;
									int lx = player.getBlockX() - minX;
									int lz = player.getBlockZ() - minZ;
									long seed = Level0ChunkGenerator.mazeSeed(player.level().getChunkSource().randomState());
									Maze.Zone zone = Maze.zoneAt(seed, player.getBlockX(), player.getBlockZ());
									ctx.getSource().sendSuccess(() -> Component.literal(
											"Level 0 Stage (" + stx + ", " + stz + ") origin=[" + minX + "," + minZ
													+ "] local=(" + lx + "," + lz + ")/" + Maze.STAGE_BLOCKS
													+ " zone=" + zone), false);
									return 1;
								})))));
	}

	static int stageXOfBlock(int x) {
		return Math.floorDiv(x, Maze.STAGE_BLOCKS);
	}

	static int stageZOfBlock(int z) {
		return Math.floorDiv(z, Maze.STAGE_BLOCKS);
	}

	private static void tick(MinecraftServer server) {
		int now = server.getTickCount();
		if (now % CHECK_INTERVAL_TICKS != 0) return;
		ServerLevel level = server.getLevel(Level0.LEVEL_0);
		if (level == null || stageLoopRule == null || !level.getGameRules().get(stageLoopRule)) return;

		long seed = Level0ChunkGenerator.mazeSeed(level.getChunkSource().randomState());
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			UUID id = player.getUUID();
			if (player.level().dimension() != Level0.LEVEL_0 || !player.isAlive()) {
				TRACKERS.remove(id);
				continue;
			}
			int curStx = stageXOfBlock(player.getBlockX());
			int curStz = stageZOfBlock(player.getBlockZ());
			StageTracker prev = TRACKERS.get(id);

			if (player.isCreative() || player.isSpectator() || RedRoomLoop.isTrapped(player)) {
				TRACKERS.put(id, new StageTracker(curStx, curStz, player.getX(), player.getZ(),
						prev != null ? prev.lastWrapTick() : -WRAP_COOLDOWN_TICKS));
				continue;
			}

			if (prev == null) {
				TRACKERS.put(id, new StageTracker(curStx, curStz, player.getX(), player.getZ(), -WRAP_COOLDOWN_TICKS));
				continue;
			}

			double moved = Math.hypot(player.getX() - prev.lastX(), player.getZ() - prev.lastZ());
			if (moved > TELEPORT_JUMP_THRESHOLD) {
				// External teleport (e.g. respawn, command, arrival): adopt the new Stage cleanly.
				TRACKERS.put(id, new StageTracker(curStx, curStz, player.getX(), player.getZ(), prev.lastWrapTick()));
				continue;
			}

			if (now - prev.lastWrapTick() < WRAP_COOLDOWN_TICKS) {
				TRACKERS.put(id, new StageTracker(prev.stx(), prev.stz(), player.getX(), player.getZ(), prev.lastWrapTick()));
				continue;
			}

			int minX = prev.stx() * Maze.STAGE_BLOCKS;
			int maxX = minX + Maze.STAGE_BLOCKS;
			int minZ = prev.stz() * Maze.STAGE_BLOCKS;
			int maxZ = minZ + Maze.STAGE_BLOCKS;

			double targetX = player.getX();
			double targetZ = player.getZ();
			boolean wrap = false;

			if (targetX < minX + WRAP_MARGIN) {
				targetX = maxX - WRAP_MARGIN - 2.5;
				wrap = true;
			} else if (targetX > maxX - WRAP_MARGIN) {
				targetX = minX + WRAP_MARGIN + 2.5;
				wrap = true;
			}
			if (targetZ < minZ + WRAP_MARGIN) {
				targetZ = maxZ - WRAP_MARGIN - 2.5;
				wrap = true;
			} else if (targetZ > maxZ - WRAP_MARGIN) {
				targetZ = minZ + WRAP_MARGIN + 2.5;
				wrap = true;
			}

			if (!wrap) {
				TRACKERS.put(id, new StageTracker(prev.stx(), prev.stz(), player.getX(), player.getZ(), prev.lastWrapTick()));
				continue;
			}

			Vec3 safeDest = findSafeWrapDest(level, seed, player, targetX, targetZ, minX, maxX, minZ, maxZ);
			if (safeDest != null) {
				player.teleportTo(level, safeDest.x, safeDest.y, safeDest.z,
						Relative.union(Relative.DELTA, Relative.ROTATION), 0.0F, 0.0F, false);
				player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 14, 0, true, false, false));
				level.playSound(null, safeDest.x, safeDest.y + 2.0, safeDest.z,
						ModSounds.FLUORESCENT_HUM, SoundSource.AMBIENT, 0.35F, 1.35F);
				TRACKERS.put(id, new StageTracker(prev.stx(), prev.stz(), safeDest.x, safeDest.z, now));
			} else {
				TRACKERS.put(id, new StageTracker(curStx, curStz, player.getX(), player.getZ(), now));
			}
		}
	}

	private static Vec3 findSafeWrapDest(ServerLevel level, long seed, ServerPlayer player,
			double rawX, double rawZ, int minX, int maxX, int minZ, int maxZ) {
		int baseCx = Math.floorDiv((int) Math.floor(rawX), Maze.CELL);
		int baseCz = Math.floorDiv((int) Math.floor(rawZ), Maze.CELL);
		int minCx = minX / Maze.CELL + 1;
		int maxCx = maxX / Maze.CELL - 2;
		int minCz = minZ / Maze.CELL + 1;
		int maxCz = maxZ / Maze.CELL - 2;

		for (int ring = 0; ring <= 4; ring++) {
			for (int dcx = -ring; dcx <= ring; dcx++) {
				for (int dcz = -ring; dcz <= ring; dcz++) {
					if (Math.max(Math.abs(dcx), Math.abs(dcz)) != ring) continue;
					int cx = Math.clamp(baseCx + dcx, minCx, maxCx);
					int cz = Math.clamp(baseCz + dcz, minCz, maxCz);
					int tx = cx * Maze.CELL + Maze.CENTER;
					int tz = cz * Maze.CELL + Maze.CENTER;
					Maze.Zone z = Maze.zoneAt(seed, tx, tz);
					if (z == Maze.Zone.RED_ROOM || z == Maze.Zone.CHASM || z == Maze.Zone.PITS) continue;
					int floorY = Maze.walkableFloorY(seed, tx, tz, Maze.EpochSource.ZERO);
					if (floorY == Integer.MIN_VALUE) continue;
					BlockPos feet = new BlockPos(tx, floorY + 1, tz);
					level.getChunk(feet);
					if (level.getBlockState(feet.below()).getCollisionShape(level, feet.below()).isEmpty()) continue;
					Vec3 cand = new Vec3(tx + 0.5, floorY + 1, tz + 0.5);
					if (level.noCollision(player.getDimensions(player.getPose()).makeBoundingBox(cand))) {
						return cand;
					}
				}
			}
		}
		return null;
	}

	private record StageTracker(int stx, int stz, double lastX, double lastZ, int lastWrapTick) {
	}
}
