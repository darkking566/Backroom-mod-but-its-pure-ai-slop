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

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext game = context.worldBuilder().create()) {
			var server = game.getServer();
			game.getConnection().waitForChunksRender();
			server.runCommand("gamerule send_command_feedback false");
			server.runCommand("gamerule noclip_backrooms:edge_shift false");
			server.runCommand("gamemode survival @a");

			enterWithBeacon(context, game);
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
			rooms(context, game, arrival);
			stairs(context, game);
			atrium(context, game);
			middle(context, game);
			robots(context, game, arrival);
			doors(context, game, arrival);
			ways(context, game, arrival);
			hatch(context, game);
			blocks(game);
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

	/** Doors open on their own when you come near and shut when you leave. */
	private void doors(ClientGameTestContext context, TestSingleplayerContext game, BlockPos from) {
		var server = game.getServer();
		int[] room = findRoom(from, Room.BEDROOM);
		if (room == null) room = findRoom(from, Room.STORAGE);
		int doorsMask = ZenithLayout.doors(seed, room[0], room[1], room[2]);
		int side = Integer.numberOfTrailingZeros(doorsMask);
		// West wall door at u=4, v=9/10; north wall at v=4, u=9/10; east u=15; south v=15.
		int x = room[0] * 16 + (side == 0 ? 4 : side == 2 ? 15 : 9), z = room[1] * 16 + (side == 1 ? 4 : side == 3 ? 15 : 9);
		int y = room[2] * 8 + 1;
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
