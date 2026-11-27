package dev.noclip.backrooms.level0;

import dev.noclip.backrooms.BackroomsMod;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleBuilder;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRuleCategory;

/**
 * Level 0 is finite and you cannot see where it ends: a 300 x 300 Stage that repeats in every direction (Level0Space).
 * Every player, in any game mode, is kept in the centre Stage of their region: on crossing one of its edges they are
 * moved exactly one Stage back. The blocks around them are the same before and after (the copy they walked into is
 * the Stage itself), their facing and speed are kept, so the move cannot be seen; walking on, you come round to where
 * you started. Near an edge the far side is loaded ahead, so the move does not wait on chunks.
 *
 * The game rule noclip_backrooms:stage_loop (on by default) turns the loop off; the world still repeats.
 */
final class StageLoop {
	private static GameRule<Boolean> stageLoopRule;
	/** How close to an edge (blocks) the far side starts loading. */
	private static final int PRELOAD_DISTANCE = 48;
	private static final int PRELOAD_RADIUS = 3;
	private static final int PRELOAD_EVERY = 40;
	private static final Map<UUID, Integer> LAST_PRELOAD = new HashMap<>();

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
									int lx = Level0Space.template(player.getBlockX()) - minX;
									int lz = Level0Space.template(player.getBlockZ()) - minZ;
									long seed = Level0ChunkGenerator.mazeSeed(player.level().getChunkSource().randomState());
									Maze.Zone zone = Maze.zoneAt(seed, Level0Space.template(player.getBlockX()), Level0Space.template(player.getBlockZ()));
									boolean original = Level0Space.original(player.getBlockX()) && Level0Space.original(player.getBlockZ());
									ctx.getSource().sendSuccess(() -> Component.literal(
											"Level 0 Stage (" + stx + ", " + stz + ") origin=[" + minX + "," + minZ
													+ "] local=(" + lx + "," + lz + ")/" + Maze.STAGE_BLOCKS
													+ " zone=" + zone + (original ? "" : " (in a copy)")), false);
									return 1;
								})))));
	}

	/** The Stage a block belongs to: the centre Stage it is a copy of. */
	static int stageXOfBlock(int x) {
		return Math.floorDiv(Level0Space.template(x), Maze.STAGE_BLOCKS);
	}

	static int stageZOfBlock(int z) {
		return Math.floorDiv(Level0Space.template(z), Maze.STAGE_BLOCKS);
	}

	private static void tick(MinecraftServer server) {
		ServerLevel level = server.getLevel(Level0.LEVEL_0);
		if (level == null || stageLoopRule == null || !level.getGameRules().get(stageLoopRule)) {
			LAST_PRELOAD.clear();
			return;
		}
		int now = server.getTickCount();
		java.util.Set<UUID> here = new java.util.HashSet<>();
		for (ServerPlayer player : level.players()) here.add(player.getUUID());
		Level0Space.keepOnly(here);
		for (ServerPlayer player : level.players()) {
			if (!player.isAlive() || RedRoomLoop.isTrapped(player)) continue;
			int bx = Mth.floor(player.getX()), bz = Mth.floor(player.getZ());
			int dx = Level0Space.template(bx) - bx, dz = Level0Space.template(bz) - bz;
			if (dx != 0 || dz != 0) {
				// Out of the centre Stage (over an edge, or set down in a copy): back by whole Stages. Same blocks around.
				player.teleportTo(level, player.getX() + dx, player.getY(), player.getZ() + dz,
						Relative.union(Relative.DELTA, Relative.ROTATION), 0.0F, 0.0F, false);
				Level0Space.moved(player.getUUID(), dx, dz);
				continue;
			}
			preload(level, player, bx, bz, now);
		}
	}

	/** Near an edge, load the far side (where the player will be moved to) a little ahead. */
	private static void preload(ServerLevel level, ServerPlayer player, int bx, int bz, int now) {
		int lx = Math.floorMod(bx, Level0Space.PERIOD), lz = Math.floorMod(bz, Level0Space.PERIOD);
		int shiftX = lx < PRELOAD_DISTANCE ? Level0Space.PERIOD : lx >= Level0Space.PERIOD - PRELOAD_DISTANCE ? -Level0Space.PERIOD : 0;
		int shiftZ = lz < PRELOAD_DISTANCE ? Level0Space.PERIOD : lz >= Level0Space.PERIOD - PRELOAD_DISTANCE ? -Level0Space.PERIOD : 0;
		if (shiftX == 0 && shiftZ == 0) return;
		Integer last = LAST_PRELOAD.get(player.getUUID());
		if (last != null && now - last < PRELOAD_EVERY && now >= last) return;
		LAST_PRELOAD.put(player.getUUID(), now);
		var chunks = level.getChunkSource();
		if (shiftX != 0) chunks.addTicketWithRadius(TicketType.PORTAL, chunk(bx + shiftX, bz), PRELOAD_RADIUS);
		if (shiftZ != 0) chunks.addTicketWithRadius(TicketType.PORTAL, chunk(bx, bz + shiftZ), PRELOAD_RADIUS);
		if (shiftX != 0 && shiftZ != 0) {
			chunks.addTicketWithRadius(TicketType.PORTAL, chunk(bx + shiftX, bz + shiftZ), PRELOAD_RADIUS);
		}
	}

	private static ChunkPos chunk(int x, int z) {
		return new ChunkPos(Math.floorDiv(x, 16), Math.floorDiv(z, 16));
	}
}
