package dev.noclip.backrooms.level0p1;

import dev.noclip.backrooms.core.LevelExits;
import dev.noclip.backrooms.level0.Level0;
import dev.noclip.backrooms.level0p1.ZenithLayout.Kind;
import dev.noclip.backrooms.level0p1.ZenithLayout.Room;
import java.util.Locale;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.locale.Language;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Plays Level 0.1 in a real client: in through the beacon, a look round the airlock, a room of every kind, the
 * stairs, an atrium, the promenade, the middle with its singularity; doors that open by themselves; the way back to
 * Level 0, a random cut-out, and the hatch in Level 0.
 */
public final class Level0p1ClientTest implements FabricClientGameTest {
	private long seed;

	private static boolean only(String section) {
		return dev.noclip.backrooms.TestFilter.section(section);
	}

	@Override
	public void runTest(ClientGameTestContext context) {
		if (!dev.noclip.backrooms.TestFilter.level("level0p1")) return;
		try (TestSingleplayerContext game = context.worldBuilder().create()) {
			var server = game.getServer();
			game.getConnection().waitForChunksRender();
			server.runCommand("gamerule send_command_feedback false");
			server.runCommand("gamerule noclip_backrooms:edge_shift false");
			server.runCommand("gamemode survival @a");

			enterWithBeacon(context, game);
			ZenithBlackouts.holdOff(20 * 60 * 10);
			seed = server.computeOnServer(s -> ZenithChunkGenerator.planSeed(station(s).getChunkSource().randomState()));
			BlockPos arrival = server.computeOnServer(s -> player(s).blockPosition());
			server.runOnServer(s -> {
				check(ZenithLayout.room(seed, Math.floorDiv(arrival.getX(), 16), Math.floorDiv(arrival.getZ(), 16), Math.floorDiv(arrival.getY(), 8)) == Room.AIRLOCK,
						"arrived outside an airlock at " + arrival);
				check(!player(s).isInWall(), "arrived inside a wall");
				checkGenerated(station(s), arrival);
			});

			server.runCommand("gamemode spectator @a");
			int mx = Math.floorDiv(arrival.getX(), 16) * 16, mz = Math.floorDiv(arrival.getZ(), 16) * 16;
			shoot(context, game, mx + 7.5, arrival.getY(), mz + 7.5, -135, 5, "level0p1_airlock");
			shoot(context, game, mx + 10.5, arrival.getY(), mz + 6.5, 0, 5, "level0p1_airlock_doorway");
			if (only("rooms")) rooms(context, game, arrival);
			if (only("variants")) variants(context, game, arrival);
			if (only("zones")) zones(context, game, arrival);
			if (only("details")) details(context, game, arrival);
			if (only("stairs")) stairs(context, game);
			if (only("atrium")) atrium(context, game);
			if (only("middle")) middle(context, game);
			if (only("robots")) robots(context, game, arrival);
			if (only("blackout")) blackout(context, game, arrival);
			if (only("doors")) doors(context, game, arrival);
			if (only("ways")) ways(context, game, arrival);
			if (only("hatch")) hatch(context, game);
			if (only("blocks")) blocks(game);
			if (only("props")) props(context, game);
		}
	}

	/** The B.N.T.G.'s way in: a beacon used in Level 0 takes you through. */
	private void enterWithBeacon(ClientGameTestContext context, TestSingleplayerContext game) {
		var server = game.getServer();
		server.runCommand("execute in noclip_backrooms:level_0 run tp @a 32.5 33 41.5");
		game.getConnection().waitForChunksDownload();
		context.waitTicks(20);
		server.runOnServer(s -> check(LevelExits.isOpen("0.1"), "Level 0.1 did not open its way in"));
		server.runCommand("give @a noclip_backrooms:bntg_beacon");
		context.waitTicks(5);
		context.getInput().pressMouse(1);
		server.waitFor(s -> player(s).level().dimension() == Level0p1.LEVEL_0_1, 200);
		game.getConnection().waitForChunksDownload();
		context.waitTicks(40);
		game.getConnection().waitForChunksRender();
		server.runOnServer(s -> check(!player(s).getInventory().contains(st -> st.is(ZenithItems.BNTG_BEACON)), "the beacon was not used up"));
	}

	/** A room of each kind, from just inside its cross, looking at a corner. */
	private void rooms(ClientGameTestContext context, TestSingleplayerContext game, BlockPos from) {
		for (Room kind : Room.values()) {
			if (kind == Room.AIRLOCK) continue;
			int[] at = findRoom(from, kind);
			if (at == null) continue;
			shoot(context, game, at[0] * 16 + 10.5, at[2] * 8 + 1, at[1] * 16 + 10.5, 135, 18, "level0p1_room_" + kind.name().toLowerCase(Locale.ROOT));
		}
		int[] corridor = findRoom(from, Room.BEDROOM);
		if (corridor != null) shoot(context, game, corridor[0] * 16 + 2.5, corridor[2] * 8 + 1, corridor[1] * 16 + 20.5, 270, 0, "level0p1_corridor");
	}

	/** Merged halls, two-story rooms, sunken pits, raised platforms, cubicles, and narrow halls. */
	private void variants(ClientGameTestContext context, TestSingleplayerContext game, BlockPos from) {
		int[] merged = findVariant(from, null, true);
		if (merged != null) {
			int[] b = ZenithLayout.roomBounds(seed, merged[0], merged[1], merged[2]);
			shoot(context, game, b[0] * 16 + 9.5, merged[2] * 8 + 1, b[1] * 16 + 9.5, -45, 5, "level0p1_variant_merged");
		}
		int[] twoStory = findVariant(from, ZenithLayout.Shape.DOUBLE_LOWER, false);
		if (twoStory != null) {
			shoot(context, game, twoStory[0] * 16 + 10.5, twoStory[2] * 8 + 1.5, twoStory[1] * 16 + 10.5, 135, -20, "level0p1_variant_double_lower");
			shoot(context, game, twoStory[0] * 16 + 10.5, (twoStory[2] + 1) * 8 + 2.5, twoStory[1] * 16 + 10.5, 135, 25, "level0p1_variant_double_upper");
		}
		for (ZenithLayout.Shape s : new ZenithLayout.Shape[] {
				ZenithLayout.Shape.SUNKEN_PIT, ZenithLayout.Shape.RAISED_DAIS, ZenithLayout.Shape.CUBICLES, ZenithLayout.Shape.NARROW_HALL}) {
			int[] at = findVariant(from, s, false);
			if (at == null) continue;
			shoot(context, game, at[0] * 16 + 10.5, at[2] * 8 + 1.5, at[1] * 16 + 10.5, 135, 14,
					"level0p1_variant_" + s.name().toLowerCase(Locale.ROOT));
		}
	}

	private int[] findVariant(BlockPos from, ZenithLayout.Shape shape, boolean requireMerged) {
		int mx0 = Math.floorDiv(from.getX(), 16), mz0 = Math.floorDiv(from.getZ(), 16);
		int deck = Math.floorDiv(from.getY(), 8);
		for (int ring = 0; ring < 20; ring++) {
			for (int dk : new int[] {0, -1, 1, -2, 2}) {
				int k = deck + dk;
				for (int i = -ring; i <= ring; i++) {
					for (int j = -ring; j <= ring; j++) {
						if (Math.max(Math.abs(i), Math.abs(j)) != ring) continue;
						int mx = mx0 + i, mz = mz0 + j;
						double cx = mx * 16 + 10, cy = k * 8 + 4, cz = mz * 16 + 10;
						double distance = Math.sqrt(cx * cx + cy * cy + cz * cz);
						if (distance < ZenithLayout.RI + 40 || distance > ZenithLayout.RO - 36) continue;
						if (ZenithLayout.atrium(seed, Math.floorDiv(mx, 3), Math.floorDiv(mz, 3), Math.floorDiv(k, 6))) continue;
						if (ZenithLayout.room(seed, mx, mz, k) == Room.AIRLOCK) continue;
						if (requireMerged && !ZenithLayout.isMerged(seed, mx, mz, k)) continue;
						if (shape != null && ZenithLayout.shape(seed, mx, mz, k) != shape) continue;
						return new int[] {mx, mz, k};
					}
				}
			}
		}
		return null;
	}

	/** The corridors of a cold, a hot and a dark stretch: damaged zones 1, 2 and 3. */
	private void zones(ClientGameTestContext context, TestSingleplayerContext game, BlockPos from) {
		String[] names = {"", "cold", "hot", "dark"};
		for (int zone = 1; zone <= 3; zone++) {
			int[] at = findZone(from, zone);
			if (at == null) continue;
			shoot(context, game, at[0] * 16 + 2.5, at[2] * 8 + 1, at[1] * 16 + 20.5, 270, 0, "level0p1_zone_" + names[zone] + "_corridor");
			shoot(context, game, at[0] * 16 + 10.5, at[2] * 8 + 1, at[1] * 16 + 10.5, 135, 18, "level0p1_zone_" + names[zone] + "_room");
			shoot(context, game, at[0] * 16 + 10.5, at[2] * 8 + 1, at[1] * 16 + 10.5, 135, -50, "level0p1_zone_" + names[zone] + "_ceiling");
		}
	}

	/** Close-ups of the speakers and the cold and hot stretches' props, each from a few blocks away. */
	private void details(ClientGameTestContext context, TestSingleplayerContext game, BlockPos from) {
		int[] room = findRoom(from, Room.BEDROOM), cold = findZone(from, 1), hot = findZone(from, 2);
		if (room != null) closeUp(context, game, room, ZenithLayout.Kind.SPEAKER, "speaker");
		java.util.List<double[]> done = new java.util.ArrayList<>();
		for (int n = 0; n < 3; n++) outside(context, game, from, done, n);
		int[] lab = findRoom(from, Room.LAB), airlock = findRoom(from, Room.AIRLOCK);
		if (lab != null) {
			closeUp(context, game, lab, ZenithLayout.Kind.COIL, "coil");
			closeUp(context, game, lab, ZenithLayout.Kind.SCREEN, "countdown");
		}
		if (airlock != null) {
			closeUp(context, game, airlock, ZenithLayout.Kind.NOTES, "notes");
			closeUp(context, game, airlock, ZenithLayout.Kind.COAT, "coat");
		}
		if (cold != null) {
			closeUp(context, game, cold, ZenithLayout.Kind.ICICLES, "icicles");
			closeUp(context, game, cold, ZenithLayout.Kind.DRIFT, "ice_drift");
		}
		if (hot != null) {
			closeUp(context, game, hot, ZenithLayout.Kind.SLAG, "slag");
			closeUp(context, game, hot, ZenithLayout.Kind.VENT, "vent");
		}
	}

	private static double pick0(int cell, double direction, double distance) {
		return cell + 0.5 + direction * distance;
	}

	/** Looking out through an outer window at the sea of rooms: the nearest window with floor behind it. */
	private void outside(ClientGameTestContext context, TestSingleplayerContext game, BlockPos from, java.util.List<double[]> done, int n) {
		double best = Double.MAX_VALUE;
		double[] pick = null;
		for (int i = 0; i < 60000; i++) {
			double yaw = i * 2.399963, pitch = Math.asin(((i * 0.618034) % 1.0) * 2 - 1);
			double dx = Math.cos(pitch) * Math.cos(yaw), dy = Math.sin(pitch), dz = Math.cos(pitch) * Math.sin(yaw);
			int x = (int) (478.5 * dx), y = (int) (478.5 * dy), z = (int) (478.5 * dz);
			double distance = Math.hypot(Math.hypot(x - from.getX(), y - from.getY()), z - from.getZ());
			final double px = x, py = y, pz = z;
			if (distance >= best || done.stream().anyMatch(d -> Math.hypot(Math.hypot(d[0] - px, d[1] - py), d[2] - pz) < 40) || ZenithLayout.kind(ZenithLayout.cell(seed, x, y, z)) != ZenithLayout.Kind.WINDOW) continue;
			int cx = (int) Math.round(473.5 * dx), cz = (int) Math.round(473.5 * dz), cy = (int) Math.round(473.5 * dy);
			int fy = cy;
			while (fy > cy - 6 && ZenithLayout.passable(ZenithLayout.cell(seed, cx, fy - 1, cz))) fy--;
			if (ZenithLayout.passable(ZenithLayout.cell(seed, cx, fy - 1, cz)) || !ZenithLayout.passable(ZenithLayout.cell(seed, cx, fy, cz))
					|| !ZenithLayout.passable(ZenithLayout.cell(seed, cx, fy + 1, cz))) continue;
			// Looking straight out from here, the first thing in the way (at eye height) has to be the window.
			double hx = dx / Math.hypot(dx, dz), hz = dz / Math.hypot(dx, dz);
			boolean clear = false;
			for (double r = 0; r < 14; r += 0.25) {
				int rx = (int) Math.floor(pick0(cx, hx, r)), rz = (int) Math.floor(pick0(cz, hz, r));
				ZenithLayout.Kind kind = ZenithLayout.kind(ZenithLayout.cell(seed, rx, fy + 1, rz));
				if (ZenithLayout.passable(ZenithLayout.cell(seed, rx, fy + 1, rz)) && ZenithLayout.passable(ZenithLayout.cell(seed, rx, fy, rz))) continue;
				clear = kind == ZenithLayout.Kind.WINDOW && r > 2;
				break;
			}
			if (!clear) continue;
			best = distance;
			pick = new double[] {cx + 0.5, fy, cz + 0.5, hx, hz, dy, x, y, z};
		}
		if (pick == null) {
			System.out.println("details: no outer window found");
			return;
		}
		done.add(new double[] {pick[6], pick[7], pick[8]});
		float yaw = (float) Math.toDegrees(Math.atan2(-pick[3], pick[4]));
		shoot(context, game, pick[0], pick[1], pick[2], yaw, 0, "level0p1_detail_outside" + n);
	}

	private void closeUp(ClientGameTestContext context, TestSingleplayerContext game, int[] module, ZenithLayout.Kind kind, String name) {
		int y0 = module[2] * 8;
		for (int dx = -48; dx < 64; dx++) {
			for (int dz = -48; dz < 64; dz++) {
				int x = module[0] * 16 + dx, z = module[1] * 16 + dz;
				for (int t = 1; t < 8; t++) {
					if (ZenithLayout.kind(ZenithLayout.cell(seed, x, y0 + t, z)) != kind) continue;
					for (int[] step : new int[][] {{-3, 0}, {3, 0}, {0, -3}, {0, 3}, {-2, 0}, {2, 0}, {0, -2}, {0, 2}}) {
						int cx = x + step[0], cz = z + step[1];
						boolean clear = true;
						for (int i = 0; i < 3 && clear; i++) {
							int mx = cx + Integer.signum(x - cx) * i, mz = cz + Integer.signum(z - cz) * i;
							clear = ZenithLayout.passable(ZenithLayout.cell(seed, mx, y0 + 1, mz)) && ZenithLayout.passable(ZenithLayout.cell(seed, mx, y0 + 2, mz));
						}
						if (!clear) continue;
						double ox = x + 0.5 - (cx + 0.5), oy = y0 + t + 0.5 - (y0 + 2.6), oz = z + 0.5 - (cz + 0.5);
						float yaw = (float) Math.toDegrees(Math.atan2(-ox, oz)), pitch = (float) -Math.toDegrees(Math.atan2(oy, Math.hypot(ox, oz)));
						shoot(context, game, cx + 0.5, y0 + 1, cz + 0.5, yaw, pitch, "level0p1_detail_" + name);
						return;
					}
				}
			}
		}
		System.out.println("details: no " + kind + " found near " + module[0] + "," + module[1]);
	}

	/** A module (mx, mz, deck) near here in a damaged stretch of this kind, not part of an atrium. */
	private int[] findZone(BlockPos from, int zone) {
		int mx0 = Math.floorDiv(from.getX(), 16), mz0 = Math.floorDiv(from.getZ(), 16);
		int deck = Math.floorDiv(from.getY(), 8);
		for (int ring = 0; ring < 40; ring++) {
			for (int i = -ring; i <= ring; i++) {
				for (int j = -ring; j <= ring; j++) {
					if (Math.max(Math.abs(i), Math.abs(j)) != ring) continue;
					int mx = mx0 + i, mz = mz0 + j;
					if (ZenithLayout.zone(seed, mx, mz, deck) != zone) continue;
					double cx = mx * 16 + 10, cy = deck * 8 + 4, cz = mz * 16 + 10;
					double distance = Math.sqrt(cx * cx + cy * cy + cz * cz);
					if (distance < ZenithLayout.RI + 40 || distance > ZenithLayout.RO - 26) continue;
					if (ZenithLayout.atrium(seed, Math.floorDiv(mx, 3), Math.floorDiv(mz, 3), Math.floorDiv(deck, 6))) continue;
					return new int[] {mx, mz, deck};
				}
			}
		}
		return null;
	}

	/** A module (mx, mz, deck) near here whose room is of this kind and not part of an atrium. */
	private int[] findRoom(BlockPos from, Room kind) {
		int mx0 = Math.floorDiv(from.getX(), 16), mz0 = Math.floorDiv(from.getZ(), 16);
		int deck = Math.floorDiv(from.getY(), 8);
		for (int ring = 0; ring < 12; ring++) {
			for (int i = -ring; i <= ring; i++) {
				for (int j = -ring; j <= ring; j++) {
					if (Math.max(Math.abs(i), Math.abs(j)) != ring) continue;
					int mx = mx0 + i, mz = mz0 + j;
					if (ZenithLayout.room(seed, mx, mz, deck) != kind) continue;
					double cx = mx * 16 + 10, cy = deck * 8 + 4, cz = mz * 16 + 10;
					double distance = Math.sqrt(cx * cx + cy * cy + cz * cz);
					if (distance < ZenithLayout.RI + 40 || distance > ZenithLayout.RO - 26) continue;
					if (ZenithLayout.atrium(seed, Math.floorDiv(mx, 3), Math.floorDiv(mz, 3), Math.floorDiv(deck, 6))) continue;
					return new int[] {mx, mz, deck};
				}
			}
		}
		return null;
	}

	/** One full turn of a spiral stair, from below and from the top. */
	private void stairs(ClientGameTestContext context, TestSingleplayerContext game) {
		shoot(context, game, 1.5, 361, 10.5, 180, 8, "level0p1_stairwell_below");
		shoot(context, game, -4.5, 361, 1.5, 270, -25, "level0p1_stairwell_up");
	}

	/** A hydroponic hall: the first atrium group near the middle of the shell. */
	private void atrium(ClientGameTestContext context, TestSingleplayerContext game) {
		for (int ax = -4; ax <= 4; ax++) {
			for (int az = -4; az <= 4; az++) {
				for (int kg = 3; kg < 9; kg++) {
					if (!ZenithLayout.atrium(seed, ax, az, kg)) continue;
					int x0 = ax * 48, z0 = az * 48, y0 = kg * 6 * 8;
					if (Math.sqrt((double) x0 * x0 + (double) z0 * z0 + (double) y0 * y0) > 330) continue;
					shoot(context, game, x0 + 2.5, y0 + 17, z0 + 24.5, 270, 0, "level0p1_atrium_from_ring");
					shoot(context, game, x0 + 24.5, y0 + 1, z0 + 3.5, 0, -10, "level0p1_atrium_floor");
					shoot(context, game, x0 + 24.5, y0 + 25, z0 + 24.5, 45, 20, "level0p1_atrium_bridge");
					return;
				}
			}
		}
	}

	/** Promenade windows, the doorway to the middle, the catwalk, the singularity, and the hull. */
	private void middle(ClientGameTestContext context, TestSingleplayerContext game) {
		shoot(context, game, 40.5, 1, 181.5, 180, 0, "level0p1_promenade_windows");
		shoot(context, game, 1.5, 1, 182.5, 180, 0, "level0p1_spoke_doorway");
		shoot(context, game, 1.5, 3, 120.5, 180, 4, "level0p1_spoke");
		shoot(context, game, 1.5, 3, 64.5, 180, 6, "level0p1_singularity");
		shoot(context, game, 1.5, 4, 42.5, 180, 12, "level0p1_ring_walk");
		shoot(context, game, 1.5, 14, 100.5, 0, -35, "level0p1_cavity_up");
		shoot(context, game, 200, 20, -2, 90, 0, "level0p1_equator_corridor");
		shoot(context, game, 1.5, 2, 466.5, 180, 0, "level0p1_hull_corridor");
	}


	/** One robot of each kind in a corridor: they stand, wander without attacking, and are drawn. */
	private void robots(ClientGameTestContext context, TestSingleplayerContext game, BlockPos arrival) {
		var server = game.getServer();
		int mx = Math.floorDiv(arrival.getX(), 16) * 16, mz = Math.floorDiv(arrival.getZ(), 16) * 16, y = arrival.getY();
		double[][] spots = {{mx + 7, mz + 0.9}, {mx + 9.5, mz + 3.1}, {mx + 12, mz + 0.9}};
		var kinds = dev.noclip.backrooms.level0p1.robots.Robot.Kind.values();
		java.util.List<java.util.UUID> ids = new java.util.ArrayList<>();
		server.runOnServer(s -> {
			for (int i = 0; i < kinds.length; i++) {
				var robot = dev.noclip.backrooms.level0p1.robots.Robots.type(kinds[i]).create(station(s), net.minecraft.world.entity.EntitySpawnReason.COMMAND);
				robot.snapTo(spots[i][0], y, spots[i][1], 90, 0);
				station(s).addFreshEntity(robot);
				ids.add(robot.getUUID());
			}
		});
		shoot(context, game, mx + 15.5, y + 0.4, mz + 2.0, 90, 8, "level0p1_robots");
		context.waitTicks(100);
		shoot(context, game, mx + 15.5, y + 0.4, mz + 2.0, 90, 8, "level0p1_robots_later");
		server.runOnServer(s -> {
			int alive = 0;
			for (java.util.UUID id : ids) {
				var entity = station(s).getEntity(id);
				if (entity instanceof dev.noclip.backrooms.level0p1.robots.Robot robot) {
					alive++;
					check(robot.getTarget() == null, "a robot picked a target");
				}
			}
			check(alive == kinds.length, "robots went missing: " + alive);
			// The spawner puts the right kind where it belongs.
			int[] kitchen = findRoom(arrival, Room.KITCHEN);
			if (kitchen != null) {
				check(ZenithRobots.kindAt(seed, kitchen[0] * 16 + 10, kitchen[2] * 8 + 1, kitchen[1] * 16 + 10) == dev.noclip.backrooms.level0p1.robots.Robot.Kind.CHEF, "no chef in the galley");
			}
		});
	}

	/** A power failure: the ceiling lights go out where you stand and come back, and none that were dead come to life. */
	private void blackout(ClientGameTestContext context, TestSingleplayerContext game, BlockPos arrival) {
		var server = game.getServer();
		int mx = Math.floorDiv(arrival.getX(), 16) * 16, mz = Math.floorDiv(arrival.getZ(), 16) * 16;
		server.runCommand(String.format(Locale.ROOT, "execute as @a in noclip_backrooms:level_0.1 run tp @s %.2f %d %.2f 0 10", mx + 8.5, arrival.getY(), mz + 8.5));
		game.getConnection().waitForChunksDownload();
		context.waitTicks(10);
		int lit = server.computeOnServer(s -> countLights(s, ZenithBlocks.CEILING_LIGHT));
		int dead = server.computeOnServer(s -> countLights(s, ZenithBlocks.DEAD_CEILING_LIGHT));
		check(lit > 0, "no ceiling lights near the airlock to black out");
		server.runOnServer(s -> ZenithBlackouts.startNow());
		server.waitFor(s -> ZenithBlackouts.phase() == ZenithBlackouts.Phase.DARK, 400);
		context.waitTicks(30);
		server.runOnServer(s -> {
			check(countLights(s, ZenithBlocks.CEILING_LIGHT) == 0, "lights stayed on in the blackout");
			check(countLights(s, ZenithBlocks.DEAD_CEILING_LIGHT) == lit + dead, "lights went missing in the blackout");
		});
		context.takeScreenshot("level0p1_blackout");
		server.runOnServer(s -> ZenithBlackouts.endNow());
		server.waitFor(s -> ZenithBlackouts.phase() == ZenithBlackouts.Phase.NORMAL, 200);
		context.waitTicks(10);
		server.runOnServer(s -> {
			check(countLights(s, ZenithBlocks.CEILING_LIGHT) == lit, "lights did not come back");
			check(countLights(s, ZenithBlocks.DEAD_CEILING_LIGHT) == dead, "dead lights came back to life");
			ZenithBlackouts.holdOff(20 * 60 * 10);
		});
	}

	private static int countLights(MinecraftServer server, Block kind) {
		ServerLevel level = station(server);
		BlockPos centre = player(server).blockPosition();
		int count = 0;
		for (BlockPos pos : BlockPos.betweenClosed(centre.offset(-16, -4, -16), centre.offset(16, 8, 16))) {
			if (level.getBlockState(pos).is(kind)) count++;
		}
		return count;
	}

	/** Doors open on their own when you come near and shut when you leave. */
	private void doors(ClientGameTestContext context, TestSingleplayerContext game, BlockPos from) {
		var server = game.getServer();
		int mx0 = Math.floorDiv(from.getX(), 16), mz0 = Math.floorDiv(from.getZ(), 16);
		int deck = Math.floorDiv(from.getY(), 8);
		int[] room = null;
		int side = 0, x = 0, y = 0, z = 0;
		search:
		for (int ring = 0; ring < 16; ring++) {
			for (int i = -ring; i <= ring; i++) {
				for (int j = -ring; j <= ring; j++) {
					if (Math.max(Math.abs(i), Math.abs(j)) != ring) continue;
					int mx = mx0 + i, mz = mz0 + j;
					if (ZenithLayout.isMerged(seed, mx, mz, deck)) continue;
					if (ZenithLayout.atrium(seed, Math.floorDiv(mx, 3), Math.floorDiv(mz, 3), Math.floorDiv(deck, 6))) continue;
					int doorsMask = ZenithLayout.doors(seed, mx, mz, deck);
					int s = Integer.numberOfTrailingZeros(doorsMask);
					int dx = mx * 16 + (s == 0 ? 4 : s == 2 ? 15 : 9), dz = mz * 16 + (s == 1 ? 4 : s == 3 ? 15 : 9);
					int dy = deck * 8 + 1;
					if (ZenithLayout.kind(ZenithLayout.cell(seed, dx, dy, dz)) == Kind.DOOR) {
						room = new int[] {mx, mz, deck};
						side = s; x = dx; y = dy; z = dz;
						break search;
					}
				}
			}
		}
		check(room != null, "no flush door found near arrival");
		BlockPos door = new BlockPos(x, y, z);
		server.runCommand(String.format(Locale.ROOT, "execute as @a in noclip_backrooms:level_0.1 run tp @s %d %d %d", x + (side == 0 ? -4 : side == 2 ? 4 : 0), y, z + (side == 1 ? -4 : side == 3 ? 4 : 0)));
		server.runCommand("gamemode survival @a");
		game.getConnection().waitForChunksDownload();
		context.waitTicks(10);
		server.runOnServer(s -> check(!station(s).getBlockState(door).getValue(DoorBlock.OPEN), "the door was open before anyone came"));
		server.runCommand(String.format(Locale.ROOT, "execute as @a in noclip_backrooms:level_0.1 run tp @s %d %d %d", x + (side == 0 ? -1 : side == 2 ? 1 : 0), y, z + (side == 1 ? -1 : side == 3 ? 1 : 0)));
		context.waitTicks(20);
		server.runOnServer(s -> check(station(s).getBlockState(door).getValue(DoorBlock.OPEN), "the door did not open by itself"));
		context.takeScreenshot("level0p1_door_open");
		server.runCommand(String.format(Locale.ROOT, "execute as @a in noclip_backrooms:level_0.1 run tp @s %d %d %d", x, y + 40, z + 30));
		context.waitTicks(100);
		server.runOnServer(s -> check(!station(s).getBlockState(door).getValue(DoorBlock.OPEN), "the door did not close behind"));
		server.runCommand("gamemode spectator @a");
	}

	/** Out through an airlock's doorway to Level 0, in again, and a static tear's random cut-out. */
	private void ways(ClientGameTestContext context, TestSingleplayerContext game, BlockPos arrival) {
		var server = game.getServer();
		int mx = Math.floorDiv(arrival.getX(), 16) * 16, mz = Math.floorDiv(arrival.getZ(), 16) * 16;
		server.runCommand(String.format(Locale.ROOT, "execute as @a in noclip_backrooms:level_0.1 run tp @s %.2f %d %.2f 0 10", mx + 9.5, arrival.getY(), mz + 7.4));
		server.runCommand("gamemode survival @a");
		game.getConnection().waitForChunksDownload();
		context.waitTicks(15);
		context.getInput().lookAt(0, 12);
		context.waitTicks(3);
		context.getInput().pressMouse(1);
		server.waitFor(s -> player(s).level().dimension() == Level0.LEVEL_0, 200);
		game.getConnection().waitForChunksDownload();
		context.waitTicks(20);
		context.takeScreenshot("level0p1_back_in_level0");
		server.runOnServer(s -> LevelExits.use(player(s), "0.1"));
		server.waitFor(s -> player(s).level().dimension() == Level0p1.LEVEL_0_1, 200);
		game.getConnection().waitForChunksDownload();
		context.waitTicks(20);
		// A cut-out takes you out somewhere else.
		server.runOnServer(s -> dev.noclip.backrooms.core.Sublevels.cutOut(player(s), "0.1"));
		server.waitFor(s -> player(s).level().dimension() != Level0p1.LEVEL_0_1, 200);
		game.getConnection().waitForChunksDownload();
		context.waitTicks(20);
		context.takeScreenshot("level0p1_cut_out");
		server.runOnServer(s -> check(player(s).level().dimension() == Level0.LEVEL_0 || player(s).level().dimension() == dev.noclip.backrooms.level1.Level1.LEVEL_1,
				"a cut-out went somewhere unexpected"));
		server.runCommand("gamemode spectator @a");
	}

	/** In Level 0, the hatch is put in its wall when its chunk loads, and it takes you in. */
	private void hatch(ClientGameTestContext context, TestSingleplayerContext game) {
		var server = game.getServer();
		server.runCommand("execute in noclip_backrooms:level_0 run tp @a 32.5 33 41.5");
		game.getConnection().waitForChunksDownload();
		context.waitTicks(10);
		// The first region with a hatch: load only its chunk (a Level 0 chunk is expensive) and give it a moment.
		int[] where = server.computeOnServer(s -> {
			ServerLevel level0 = s.getLevel(Level0.LEVEL_0);
			for (int rx = 0; rx >= -3; rx--) {
				for (int rz = 0; rz >= -3; rz--) {
					int[] target = ZenithEntrance.targetChunk(level0.getSeed(), rx, rz);
					if (target != null) return target;
				}
			}
			return null;
		});
		check(where != null, "no region near the origin has a hatch");
		server.runCommand(String.format(Locale.ROOT, "execute in noclip_backrooms:level_0 run forceload add %d %d", where[0] * 16, where[1] * 16));
		context.waitTicks(60);
		BlockPos found = server.computeOnServer(s -> {
			ServerLevel level0 = s.getLevel(Level0.LEVEL_0);
			var chunk = level0.getChunk(where[0], where[1]);
			for (int x = 0; x < 16; x++) {
				for (int z = 0; z < 16; z++) {
					BlockPos pos = new BlockPos(chunk.getPos().getMinBlockX() + x, 33, chunk.getPos().getMinBlockZ() + z);
					if (level0.getBlockState(pos).is(ZenithBlocks.STATION_HATCH)) return pos;
				}
			}
			return null;
		});
		check(found != null, "the hatch was not put in chunk " + where[0] + ", " + where[1]);
	}

	/** Close-ups of furniture set out in a row in the empty dark outside the station, all facing the camera (north), on lit floor. */
	private void props(ClientGameTestContext context, TestSingleplayerContext game) {
		int x0 = -6, y = 0, z0 = 900;
		game.getServer().runOnServer(s -> {
			ServerLevel level = station(s);
			for (int x = x0 - 1; x <= x0 + 13; x++) {
				for (int z = z0 - 5; z <= z0 + 1; z++) level.setBlock(new BlockPos(x, y - 1, z), ZenithBlocks.CEILING_LIGHT.defaultBlockState(), 2);
				for (int dy = 0; dy < 4; dy++) level.setBlock(new BlockPos(x, y + dy, z0 + 1), ZenithBlocks.WALL_PANEL.defaultBlockState(), 2);
				level.setBlock(new BlockPos(x, y + 3, z0), ZenithBlocks.DECK_CEILING.defaultBlockState(), 2);
			}
			Block[] row = {ZenithBlocks.CREW_DESK, ZenithBlocks.CONSOLE, ZenithBlocks.BROKEN_CONSOLE, ZenithBlocks.LAB_COAT, ZenithBlocks.CREW_BED,
					ZenithBlocks.FRIDGE, ZenithBlocks.CREW_LOCKER};
			for (int i = 0; i < row.length; i++) {
				level.setBlock(new BlockPos(x0 + i, y, z0), row[i].defaultBlockState().setValue(ZenithFacingBlock.FACING, net.minecraft.core.Direction.NORTH), 2);
			}
			level.setBlock(new BlockPos(x0 + 8, y + 2, z0), ZenithBlocks.HANGING_CABLES.defaultBlockState(), 2);
			level.setBlock(new BlockPos(x0 + 8, y + 1, z0), ZenithBlocks.HANGING_CABLES.defaultBlockState(), 2);
			for (int i = 0; i < 2; i++) {
				for (int half = 0; half < 2; half++) {
					level.setBlock(new BlockPos(x0 + 10 + i, y + half, z0), ZenithChunkGenerator.state(ZenithLayout.of(ZenithLayout.Kind.DOOR, 0, (half << 1) | i)), 2);
				}
			}
		});
		shoot(context, game, x0 + 2.5, y + 0.6, z0 - 3.2, 0, 15, "level0p1_props_left");
		shoot(context, game, x0 + 8.5, y + 0.6, z0 - 3.2, 0, 15, "level0p1_props_right");
		shoot(context, game, x0 + 3.5, y + 0.6, z0 - 2.0, 25, 20, "level0p1_props_coat");
	}

	/** Every block has a name and drops itself (the unbreakable ones have no drops). */
	private void blocks(TestSingleplayerContext game) {
		game.getServer().runOnServer(s -> {
			ServerLevel level = station(s);
			for (Block block : ZenithBlocks.ALL) {
				check(Language.getInstance().has(block.getDescriptionId()), "no name for " + block);
				if (block.getLootTable().isEmpty() || block instanceof DoorBlock) continue;
				var drops = Block.getDrops(block.defaultBlockState(), level, new BlockPos(3, 3, 3), null);
				check(drops.size() == 1 && drops.getFirst().is(block.asItem()), block + " does not drop itself");
			}
		});
	}

	/** Columns near here match the generator. */
	private void checkGenerated(ServerLevel level, BlockPos around) {
		for (int x = around.getX() - 20; x < around.getX() + 20; x++) {
			for (int z = around.getZ() - 20; z < around.getZ() + 20; z++) {
				for (int dy = -1; dy <= 8; dy++) {
					int y = around.getY() + dy;
					BlockState expected = ZenithChunkGenerator.blockAt(seed, x, y, z);
					check(level.getBlockState(new BlockPos(x, y, z)).is(expected.getBlock()), "world differs from generator at " + x + ", " + y + ", " + z);
				}
			}
		}
	}

	private static void shoot(ClientGameTestContext context, TestSingleplayerContext game, double x, double y, double z, float yaw, float pitch, String name) {
		game.getServer().runCommand(String.format(Locale.ROOT, "execute as @a in noclip_backrooms:level_0.1 run tp @s %.2f %.2f %.2f %.1f %.1f", x, y, z, yaw, pitch));
		game.getConnection().waitForChunksDownload();
		context.waitTicks(30);
		game.getConnection().waitForChunksRender();
		context.runOnClient(client -> client.gui.toastManager().clear());
		context.waitTick();
		context.takeScreenshot(name);
	}

	private static ServerLevel station(MinecraftServer server) {
		return server.getLevel(Level0p1.LEVEL_0_1);
	}

	private static ServerPlayer player(MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}

	private static void check(boolean condition, String message) {
		if (!condition) throw new AssertionError(message);
	}
}
