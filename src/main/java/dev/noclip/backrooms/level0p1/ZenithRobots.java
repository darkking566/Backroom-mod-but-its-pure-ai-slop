package dev.noclip.backrooms.level0p1;

import dev.noclip.backrooms.level0p1.ZenithLayout.Room;
import dev.noclip.backrooms.level0p1.robots.Robot;
import dev.noclip.backrooms.level0p1.robots.Robots;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.phys.AABB;

/**
 * The robots going about the station: now and then one appears out of sight near a player, a cleaner in the
 * corridors, a chef in the galleys, and a broken servant where the lights are dead. At most a handful at once;
 * they leave again when nobody is near (removeWhenFarAway).
 */
public final class ZenithRobots {
	private static final int INTERVAL = 80;
	private static final int CAP = 5;
	private static final int NEAR = 40;

	private ZenithRobots() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(ZenithRobots::tick);
	}

	/** Which robot belongs at this block: chefs in galleys, servants in wrecks and dead stretches, cleaners elsewhere. */
	public static Robot.Kind kindAt(long seed, int x, int y, int z) {
		int mx = Math.floorDiv(x, ZenithLayout.MODULE), mz = Math.floorDiv(z, ZenithLayout.MODULE), k = Math.floorDiv(y, ZenithLayout.PITCH);
		Room room = ZenithLayout.room(seed, mx, mz, k);
		if (room == Room.KITCHEN) return Robot.Kind.CHEF;
		if (room == Room.WRECK || ZenithLayout.zone(seed, mx, mz, k) != 0) return Robot.Kind.SERVANT;
		return Robot.Kind.CLEANER;
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % INTERVAL != 0) return;
		ServerLevel level = server.getLevel(Level0p1.LEVEL_0_1);
		if (level == null) return;
		long seed = ZenithChunkGenerator.planSeed(level.getChunkSource().randomState());
		RandomSource random = level.getRandom();
		for (ServerPlayer player : level.players()) {
			if (player.isSpectator()) continue;
			AABB around = player.getBoundingBox().inflate(NEAR);
			if (level.getEntitiesOfClass(Robot.class, around).size() >= CAP) continue;
			double angle = random.nextDouble() * Math.PI * 2, distance = 9 + random.nextDouble() * 20;
			int x = (int) Math.floor(player.getX() + Math.cos(angle) * distance), z = (int) Math.floor(player.getZ() + Math.sin(angle) * distance);
			int y = standingY(seed, x, player.getBlockY(), z);
			if (y == Integer.MIN_VALUE) continue;
			BlockPos at = new BlockPos(x, y, z);
			if (!level.hasChunkAt(at)) continue;
			spawn(level, seed, at, random);
		}
	}

	/** A floor to stand on in this column within a few blocks of the player's height, or MIN_VALUE. */
	static int standingY(long seed, int x, int near, int z) {
		for (int dy = 0; dy <= 6; dy++) {
			for (int sign : new int[] {1, -1}) {
				int y = near + dy * sign;
				if (ZenithLayout.passable(ZenithLayout.cell(seed, x, y, z)) && ZenithLayout.passable(ZenithLayout.cell(seed, x, y + 1, z))
						&& ZenithLayout.passable(ZenithLayout.cell(seed, x, y + 2, z)) && !ZenithLayout.passable(ZenithLayout.cell(seed, x, y - 1, z))) {
					return y;
				}
			}
		}
		return Integer.MIN_VALUE;
	}

	/** Places a robot of the kind that belongs here; returns whether there was room. For tests too. */
	public static Robot spawn(ServerLevel level, long seed, BlockPos at, RandomSource random) {
		// In a blackout the broken servants wander out of the dark, wherever you are.
		Robot.Kind kind = ZenithBlackouts.phase() == ZenithBlackouts.Phase.DARK ? Robot.Kind.SERVANT : kindAt(seed, at.getX(), at.getY(), at.getZ());
		Robot robot = Robots.type(kind).create(level, EntitySpawnReason.EVENT);
		if (robot == null) return null;
		robot.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, random.nextFloat() * 360, 0);
		if (!level.noCollision(robot)) return null;
		level.addFreshEntity(robot);
		return robot;
	}
}
