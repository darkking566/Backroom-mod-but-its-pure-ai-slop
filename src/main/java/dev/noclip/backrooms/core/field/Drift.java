package dev.noclip.backrooms.core.field;

import dev.noclip.backrooms.core.Home;
import dev.noclip.backrooms.core.floors.Floors;
import dev.noclip.backrooms.core.rpg.PlayerRpg;
import dev.noclip.backrooms.core.rpg.PlayerStats;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Space drift (docs/企劃書/1-遊戲玩法/README.md, "會背叛玩家的空間標記系統"). Marks are only as reliable as the space
 * they are drawn in: when a player's mind slips (sanity under 40%) or they wander far from every mark they have left, a
 * chalk arrow somewhere behind them quietly turns, or a glow stick goes out. Deep down (from the fourth floor of a trip)
 * the walls also carry old arrows from the missing squad, pointing wherever they pointed then.
 */
public final class Drift {
	/** How often space may shift, in ticks; how far from a mark is "far". */
	static final int EVERY = 600, FAR = 64, REACH = 160;
	static final float SLIPPING = 0.4F;
	private static final Map<ResourceKey<Level>, Set<BlockPos>> MARKS = new HashMap<>();

	private Drift() {
	}

	static void register() {
		ServerLifecycleEvents.SERVER_STARTED.register(server -> MARKS.clear());
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % EVERY == 0) shift(server);
		});
	}

	/** A chalk mark or glow stick put down. */
	static void marked(ServerLevel level, BlockPos pos) {
		MARKS.computeIfAbsent(level.dimension(), k -> new HashSet<>()).add(pos.immutable());
	}

	private static void shift(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (!Home.inBackrooms(player.level().dimension()) || player.isSpectator()) continue;
			ServerLevel level = (ServerLevel) player.level();
			if (drifting(player)) shiftOne(level, player);
			if (Floors.of(player).floor() >= 4 && player.getRandom().nextInt(4) == 0) stale(level, player);
		}
	}

	/** Whether space is slipping around this player now. */
	public static boolean drifting(ServerPlayer player) {
		PlayerStats stats = PlayerRpg.get(player);
		if (stats.maxSanity() > 0 && stats.sanity() / stats.maxSanity() < SLIPPING) return true;
		List<BlockPos> marks = near((ServerLevel) player.level(), player.blockPosition(), REACH);
		if (marks.isEmpty()) return false;
		for (BlockPos mark : marks) if (mark.closerThan(player.blockPosition(), FAR)) return false;
		return true;
	}

	private static List<BlockPos> near(ServerLevel level, BlockPos at, int range) {
		List<BlockPos> list = new ArrayList<>();
		Set<BlockPos> marks = MARKS.get(level.dimension());
		if (marks == null) return list;
		marks.removeIf(pos -> level.isLoaded(pos) && !level.getBlockState(pos).is(FieldKit.CHALK_MARK) && !level.getBlockState(pos).is(FieldKit.GLOW_STICK_BLOCK));
		for (BlockPos pos : marks) if (pos.closerThan(at, range)) list.add(pos);
		return list;
	}

	/** One mark near the player changes: an arrow turns, a glow stick dies. */
	static boolean shiftOne(ServerLevel level, ServerPlayer player) {
		List<BlockPos> marks = near(level, player.blockPosition(), REACH);
		marks.removeIf(pos -> !level.isLoaded(pos));
		if (marks.isEmpty()) return false;
		BlockPos pos = marks.get(player.getRandom().nextInt(marks.size()));
		BlockState state = level.getBlockState(pos);
		if (state.is(FieldKit.GLOW_STICK_BLOCK)) {
			level.removeBlock(pos, false);
			return true;
		}
		if (state.is(FieldKit.CHALK_MARK) && state.getValue(FieldBlocks.ChalkMark.SYMBOL) == FieldBlocks.ChalkMark.Symbol.ARROW
				&& state.getValue(FieldBlocks.ChalkMark.FACE) == FieldBlocks.ChalkMark.Face.FLOOR) {
			Direction facing = state.getValue(FieldBlocks.ChalkMark.FACING);
			Direction turned = player.getRandom().nextBoolean() ? facing.getClockWise() : facing.getCounterClockWise();
			level.setBlockAndUpdate(pos, state.setValue(FieldBlocks.ChalkMark.FACING, turned));
			return true;
		}
		return false;
	}

	/** An old arrow on the floor somewhere near, from someone who came this way before, pointing anywhere. */
	private static void stale(ServerLevel level, ServerPlayer player) {
		RandomSource random = player.getRandom();
		for (int attempt = 0; attempt < 20; attempt++) {
			BlockPos at = player.blockPosition().offset(random.nextInt(31) - 15, random.nextInt(5) - 2, random.nextInt(31) - 15);
			if (at.closerThan(player.blockPosition(), 8)) continue;
			if (!level.getBlockState(at).isAir() || !level.getBlockState(at.below()).isFaceSturdy(level, at.below(), Direction.UP)) continue;
			level.setBlockAndUpdate(at, FieldKit.CHALK_MARK.defaultBlockState().setValue(FieldBlocks.ChalkMark.FACING,
					Direction.Plane.HORIZONTAL.getRandomDirection(random)));
			return;
		}
	}
}
