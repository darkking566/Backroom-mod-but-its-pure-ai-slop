package dev.noclip.backrooms.level2;

import dev.noclip.backrooms.core.LevelExits;
import dev.noclip.backrooms.level1.Level1;
import dev.noclip.backrooms.level2.Level2Layout.Kind;
import dev.noclip.backrooms.level2.Level2Layout.Tile;
import java.util.Locale;
import java.util.function.Predicate;
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
 * Plays Level 2 in a real client: down Level 1's stairs into the tunnels, a look around, a room door and a locked
 * one, every kind of exit (one refusing, one back up to Level 1), a light circuit going out, the heat, a window.
 */
public final class Level2ClientTest implements FabricClientGameTest {
	private static final int STAND = Level2ChunkGenerator.FLOOR_Y + 1;

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext game = context.worldBuilder().create()) {
			var server = game.getServer();
			game.getConnection().waitForChunksRender();
			server.runCommand("gamerule send_command_feedback false");
			server.runCommand("gamerule noclip_backrooms:edge_shift false");
			server.runCommand("gamemode survival @a");
			// Packs are tested on their own below; until then none turn up by chance.
			server.runOnServer(s -> Level2Hounds.holdOff(s, 20 * 60 * 20));

			enterFromLevel1(context, game);
			long seed = server.computeOnServer(s -> Level2ChunkGenerator.planSeed(level2(s).getChunkSource().randomState()));
			BlockPos arrival = server.computeOnServer(s -> player(s).blockPosition());
			server.runOnServer(s -> {
				check(Level2Layout.tile(seed, arrival.getX(), arrival.getZ()).kind() == Kind.FLOOR, "arrived off the tunnel floor at " + arrival);
				check(!player(s).isInWall(), "arrived inside a wall");
				checkGenerated(level2(s), seed, arrival);
			});

			server.runCommand("gamemode spectator @a");
			for (int yaw = 0; yaw < 360; yaw += 90) {
				shoot(context, game, arrival.getX() + 0.5, STAND, arrival.getZ() + 0.5, yaw, 5, "level2_arrival_yaw_" + yaw);
			}
			views(context, game, seed, arrival);
			doors(context, game, seed, arrival);
			exits(context, game, seed, arrival);
			circuit(context, game, seed, arrival);
			heat(context, game, seed, arrival);
			window(context, game, seed, arrival);
			hounds(context, game, seed, arrival);
			checkBlocks(game);
		}
	}

	/** Level 1's stairs down lead here now: Level 2 has opened "2". */
	private static void enterFromLevel1(ClientGameTestContext context, TestSingleplayerContext game) {
		var server = game.getServer();
		server.runCommand("execute in noclip_backrooms:level_1 run tp @a 32.5 33 41.5");
		game.getConnection().waitForChunksDownload();
		context.waitTicks(20);
		server.runOnServer(s -> check(LevelExits.isOpen("2"), "Level 2 did not open its way in"));
		server.runOnServer(s -> LevelExits.use(player(s), "2"));
		server.waitFor(s -> player(s).level().dimension() == Level2.LEVEL_2, 100);
		game.getConnection().waitForChunksDownload();
		context.waitTicks(30);
		game.getConnection().waitForChunksRender();
	}

	/** Down a long run of tunnel with its pipe bank, from inside the bank's walkway. */
	private static void views(ClientGameTestContext context, TestSingleplayerContext game, long seed, BlockPos from) {
		int[] bank = nearest(seed, from, tile -> tile.kind() == Kind.BANK, 0);
		Tile tile = Level2Layout.tile(seed, bank[0], bank[1]);
		int out = tile.dir();
		int along = (out + 1) & 3;
		double x = bank[0] + 0.5 + Level2Plan.DX[out] * 1.0 - Level2Plan.DX[along] * 2, z = bank[1] + 0.5 + Level2Plan.DZ[out] * 1.0 - Level2Plan.DZ[along] * 2;
		shoot(context, game, x, STAND, z, yaw(along), 8, "level2_tunnel");
		shoot(context, game, x, STAND, z, yaw(Level2Plan.opposite(out)), 10, "level2_pipe_bank");
		int[] machine = nearest(seed, from, t -> t.kind() == Kind.MACHINE, 0);
		Tile m = Level2Layout.tile(seed, machine[0], machine[1]);
		shoot(context, game, machine[0] + 0.5 + Level2Plan.DX[m.dir()] * 2.2, STAND, machine[1] + 0.5 + Level2Plan.DZ[m.dir()] * 2.2,
				yaw(Level2Plan.opposite(m.dir())), 15, "level2_machine");
		int[] marked = nearest(seed, from, t -> t.kind() == Kind.FLOOR, 0, (x2, z2) -> Level2Layout.mark(seed, x2, z2) != null);
		int markFacing = Level2Layout.mark(seed, marked[0], marked[1]).facing();
		shoot(context, game, marked[0] + 0.5 + Level2Plan.DX[markFacing] * 1.2, STAND, marked[1] + 0.5 + Level2Plan.DZ[markFacing] * 1.2,
				yaw(Level2Plan.opposite(markFacing)), 10, "level2_wanderer_mark");
		int[] dark = nearest(seed, from, t -> t.kind() == Kind.FLOOR, 0, (x2, z2) -> Level2Layout.light(seed, x2, z2) == Level2Layout.Light.VIOLET);
		shoot(context, game, dark[0] + 2.5, STAND, dark[1] + 0.5, 90, -10, "level2_emergency_light");
	}

	/** A room door opens with a right-click; a locked one rattles and stays shut. */
	private static void doors(ClientGameTestContext context, TestSingleplayerContext game, long seed, BlockPos from) {
		var server = game.getServer();
		int[] door = nearest(seed, from, tile -> tile.kind() == Kind.DOOR, 0);
		int out = Level2Layout.tile(seed, door[0], door[1]).dir();
		shoot(context, game, door[0] + 0.5 + Level2Plan.DX[out] * 2.5, STAND, door[1] + 0.5 + Level2Plan.DZ[out] * 2.5,
				yaw(Level2Plan.opposite(out)), 10, "level2_room_door");
		useBlockFrom(context, game, door, out, 1.6);
		server.runOnServer(s -> check(level2(s).getBlockState(new BlockPos(door[0], STAND, door[1])).getValue(DoorBlock.OPEN), "the room door did not open"));
		server.runCommand("gamemode spectator @a");
		shoot(context, game, door[0] + 0.5 - Level2Plan.DX[out] * 1.5, STAND, door[1] + 0.5 - Level2Plan.DZ[out] * 1.5,
				yaw(Level2Plan.opposite(out)), 15, "level2_room");

		int[] locked = nearest(seed, from, tile -> tile.kind() == Kind.LOCKED_DOOR, 0);
		int lockedOut = Level2Layout.tile(seed, locked[0], locked[1]).dir();
		useBlockFrom(context, game, locked, lockedOut, 1.6);
		context.takeScreenshot("level2_locked_door");
		server.runOnServer(s -> check(!level2(s).getBlockState(new BlockPos(locked[0], STAND, locked[1])).getValue(DoorBlock.OPEN), "a locked door opened"));
		server.runCommand("gamemode spectator @a");
	}

	/** Every kind of door out, as generated; one to a level that is not there yet refuses; "1" goes back up. */
	private static void exits(ClientGameTestContext context, TestSingleplayerContext game, long seed, BlockPos from) {
		var server = game.getServer();
		for (Level2Exit exit : Level2Exit.values()) {
			int[] at = nearest(seed, from, tile -> tile.kind() == Kind.EXIT && tile.exit() == exit, 0);
			int out = Level2Layout.tile(seed, at[0], at[1]).dir();
			float pitch = exit.height == 4 ? -15 : 10;
			shoot(context, game, at[0] + 0.5 + Level2Plan.DX[out] * 2.6, STAND, at[1] + 0.5 + Level2Plan.DZ[out] * 2.6,
					yaw(Level2Plan.opposite(out)), pitch, "level2_exit_" + exit.level);
		}
		// The door marked "4" leads to a level that is not there yet: it refuses.
		int[] four = nearest(seed, from, tile -> tile.kind() == Kind.EXIT && tile.exit() == Level2Exit.MARKED_4, 0);
		int refused = LevelExits.refusals();
		useBlockFrom(context, game, four, Level2Layout.tile(seed, four[0], four[1]).dir(), 1.6);
		server.runOnServer(s -> {
			check(LevelExits.refusals() > refused, "the door to Level 4 did not refuse");
			check(player(s).level().dimension() == Level2.LEVEL_2, "a closed door let the player through");
		});
		// The door marked "3" goes on down to Level 3, and Level 3's way back is "2".
		int[] three = nearest(seed, from, tile -> tile.kind() == Kind.EXIT && tile.exit() == Level2Exit.MARKED_3, 0);
		useBlockFrom(context, game, three, Level2Layout.tile(seed, three[0], three[1]).dir(), 1.6);
		server.waitFor(s -> player(s).level().dimension() == dev.noclip.backrooms.level3.Level3.LEVEL_3, 100);
		server.runOnServer(s -> LevelExits.use(player(s), "2"));
		server.waitFor(s -> player(s).level().dimension() == Level2.LEVEL_2, 100);
		int[] one = nearest(seed, from, tile -> tile.kind() == Kind.EXIT && tile.exit() == Level2Exit.MARKED_1, 0);
		useBlockFrom(context, game, one, Level2Layout.tile(seed, one[0], one[1]).dir(), 1.6);
		server.waitFor(s -> player(s).level().dimension() == Level1.LEVEL_1, 100);
		game.getConnection().waitForChunksDownload();
		context.waitTicks(20);
		context.takeScreenshot("level2_back_in_level1");
		// And down again.
		server.runOnServer(s -> LevelExits.use(player(s), "2"));
		server.waitFor(s -> player(s).level().dimension() == Level2.LEVEL_2, 100);
		server.runCommand("gamemode spectator @a");
	}

	/** Lights are in series: break one and the whole circuit goes dark. */
	private static void circuit(ClientGameTestContext context, TestSingleplayerContext game, long seed, BlockPos from) {
		var server = game.getServer();
		int[] lamp = nearest(seed, from, tile -> tile.kind() == Kind.FLOOR, 0,
				(x, z) -> Level2Layout.light(seed, x, z) == Level2Layout.Light.WHITE && litInCircuit(seed, x, z) >= 3);
		shoot(context, game, lamp[0] + 0.5, STAND, lamp[1] + 3.5, 180, -8, "level2_circuit_on");
		int[] counts = server.computeOnServer(s -> {
			ServerLevel level = level2(s);
			BlockPos pos = new BlockPos(lamp[0], Level2ChunkGenerator.TOP, lamp[1]);
			int before = countLit(level, pos);
			level.destroyBlock(pos, false);
			return new int[] {before, countLit(level, pos)};
		});
		check(counts[0] >= 3, "fewer lights on the circuit than planned: " + counts[0]);
		check(counts[1] == 0, "lights stayed on after one on their circuit broke: " + counts[1]);
		context.waitTicks(20);
		context.takeScreenshot("level2_circuit_off");
	}

	/** The hot stretches wear you down. */
	private static void heat(ClientGameTestContext context, TestSingleplayerContext game, long seed, BlockPos from) {
		var server = game.getServer();
		int[] hot = nearest(seed, from, tile -> tile.kind() == Kind.FLOOR, 0, (x, z) -> Level2Layout.hot(seed, x, z) && Level2Layout.hot(seed, x + 3, z));
		server.runCommand(String.format(Locale.ROOT, "execute as @a in noclip_backrooms:level_2 run tp @s %.1f %d %.1f 90 5", hot[0] + 0.5, STAND, hot[1] + 0.5));
		server.runCommand("gamemode survival @a");
		game.getConnection().waitForChunksDownload();
		context.waitTicks(70);
		game.getConnection().waitForChunksRender();
		context.takeScreenshot("level2_hot");
		server.runOnServer(s -> check(Level2Heat.feelingIt(player(s)), "standing in a hot stretch did nothing"));
		server.runCommand("gamemode spectator @a");
	}

	/** Do not go near the windows. */
	private static void window(ClientGameTestContext context, TestSingleplayerContext game, long seed, BlockPos from) {
		var server = game.getServer();
		int[] window = nearest(seed, from, tile -> tile.kind() == Kind.WINDOW, 0);
		int out = Level2Layout.tile(seed, window[0], window[1]).dir();
		shoot(context, game, window[0] + 0.5 + Level2Plan.DX[out] * 3.5, STAND, window[1] + 0.5 + Level2Plan.DZ[out] * 3.5,
				yaw(Level2Plan.opposite(out)), 0, "level2_window");
		server.runCommand(String.format(Locale.ROOT, "execute as @a in noclip_backrooms:level_2 run tp @s %.2f %d %.2f %.1f 0",
				window[0] + 0.5 + Level2Plan.DX[out] * 1.5, STAND, window[1] + 0.5 + Level2Plan.DZ[out] * 1.5, yaw(Level2Plan.opposite(out))));
		server.runCommand("gamemode survival @a");
		server.runOnServer(s -> player(s).setHealth(player(s).getMaxHealth()));
		server.waitFor(s -> player(s).getHealth() < player(s).getMaxHealth(), 100);
		server.runOnServer(s -> player(s).setHealth(player(s).getMaxHealth()));
		server.runCommand("gamemode spectator @a");
	}

	private static void hounds(ClientGameTestContext context, TestSingleplayerContext game, long seed, BlockPos from) {
		var server = game.getServer();
		server.runCommand(String.format(Locale.ROOT, "execute as @a in noclip_backrooms:level_2 run tp @s %.1f %d %.1f", from.getX() + 0.5, STAND, from.getZ() + 0.5));
		server.runCommand("gamemode survival @a");
		game.getConnection().waitForChunksDownload();
		context.waitTicks(20);
		int came = server.computeOnServer(s -> Level2Hounds.spawnPack(player(s)));
		check(came == 2, "a pack of " + came + " hounds");
		server.runCommand("kill @e[tag=" + Level2Hounds.TAG + "]");
		server.runCommand("gamemode spectator @a");
	}

	private static void checkBlocks(TestSingleplayerContext game) {
		game.getServer().runOnServer(s -> {
			ServerLevel level = level2(s);
			for (Block block : Level2Blocks.ALL) {
				check(Language.getInstance().has(block.getDescriptionId()), "no name for " + block);
				if (block.getLootTable().isEmpty() || block instanceof DoorBlock) continue;
				var drops = Block.getDrops(block.defaultBlockState(), level, new BlockPos(3, 3, 3), null);
				check(drops.size() == 1 && drops.getFirst().is(block.asItem()), block + " does not drop itself");
			}
		});
	}

	/** Every column within 24 blocks of here matches the generator. */
	private static void checkGenerated(ServerLevel level, long seed, BlockPos around) {
		for (int x = around.getX() - 24; x < around.getX() + 24; x++) {
			for (int z = around.getZ() - 24; z < around.getZ() + 24; z++) {
				for (int y : new int[] {0, Level2ChunkGenerator.FLOOR_Y, STAND, STAND + 1, STAND + 2, Level2ChunkGenerator.TOP, Level2ChunkGenerator.CEILING_Y}) {
					BlockState expected = Level2ChunkGenerator.blockAt(seed, x, y, z);
					check(level.getBlockState(new BlockPos(x, y, z)).is(expected.getBlock()), "world differs from generator at " + x + ", " + y + ", " + z);
				}
			}
		}
	}

	/** Stands in front of a block in a wall, facing it, and right-clicks it at chest height. */
	private static void useBlockFrom(ClientGameTestContext context, TestSingleplayerContext game, int[] at, int out, double distance) {
		var server = game.getServer();
		float yaw = yaw(Level2Plan.opposite(out));
		server.runCommand(String.format(Locale.ROOT, "execute as @a in noclip_backrooms:level_2 run tp @s %.2f %d %.2f %.1f 20",
				at[0] + 0.5 + Level2Plan.DX[out] * distance, STAND, at[1] + 0.5 + Level2Plan.DZ[out] * distance, yaw));
		server.runCommand("gamemode survival @a");
		game.getConnection().waitForChunksDownload();
		context.waitTicks(10);
		context.getInput().lookAt(yaw, 20);
		context.waitTicks(3);
		context.getInput().pressMouse(1);
		context.waitTicks(10);
	}

	private static int litInCircuit(long seed, int x, int z) {
		int[] box = Level2Layout.circuit(x, z);
		int lit = 0;
		for (int a = box[0]; a <= box[2]; a++) {
			for (int b = box[1]; b <= box[3]; b++) {
				Level2Layout.Light light = Level2Layout.light(seed, a, b);
				if (light == Level2Layout.Light.WHITE || light == Level2Layout.Light.WARM || light == Level2Layout.Light.BULB) lit++;
			}
		}
		return lit;
	}

	private static int countLit(ServerLevel level, BlockPos around) {
		int[] box = Level2Layout.circuit(around.getX(), around.getZ());
		int lit = 0;
		for (int x = box[0]; x <= box[2]; x++) {
			for (int z = box[1]; z <= box[3]; z++) {
				if (level.getBlockState(new BlockPos(x, around.getY(), z)).getBlock() instanceof Level2LightBlock) lit++;
			}
		}
		return lit;
	}

	private static int[] nearest(long seed, BlockPos from, Predicate<Tile> wanted, int unused) {
		return nearest(seed, from, wanted, unused, (x, z) -> true);
	}

	private interface Where {
		boolean test(int x, int z);
	}

	/** The nearest column (by rings around from) whose tile is wanted. */
	private static int[] nearest(long seed, BlockPos from, Predicate<Tile> wanted, int unused, Where where) {
		int x0 = from.getX(), z0 = from.getZ();
		for (int ring = 0; ring < 900; ring++) {
			for (int dx = -ring; dx <= ring; dx++) {
				for (int dz = -ring; dz <= ring; dz++) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) continue;
					int x = x0 + dx, z = z0 + dz;
					if (wanted.test(Level2Layout.tile(seed, x, z)) && where.test(x, z)) return new int[] {x, z};
				}
			}
		}
		throw new AssertionError("nothing like that within 900 blocks");
	}

	/** The yaw that looks along a plan direction. */
	private static float yaw(int dir) {
		return (float) Math.toDegrees(Math.atan2(-Level2Plan.DX[dir], Level2Plan.DZ[dir]));
	}

	private static void shoot(ClientGameTestContext context, TestSingleplayerContext game, double x, double y, double z, float yaw, float pitch, String name) {
		game.getServer().runCommand(String.format(Locale.ROOT, "execute as @a in noclip_backrooms:level_2 run tp @s %.2f %.2f %.2f %.1f %.1f", x, y, z, yaw, pitch));
		game.getConnection().waitForChunksDownload();
		context.waitTicks(25);
		game.getConnection().waitForChunksRender();
		context.runOnClient(client -> client.gui.toastManager().clear());
		context.waitTick();
		context.takeScreenshot(name);
	}

	private static ServerLevel level2(MinecraftServer server) {
		return server.getLevel(Level2.LEVEL_2);
	}

	private static ServerPlayer player(MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}

	private static void check(boolean condition, String message) {
		if (!condition) throw new AssertionError(message);
	}
}
