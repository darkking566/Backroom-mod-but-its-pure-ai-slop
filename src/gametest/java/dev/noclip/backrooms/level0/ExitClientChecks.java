package dev.noclip.backrooms.level0;

import java.util.EnumMap;
import java.util.Map;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

/** Walks out through real, naturally generated exits with real mouse clicks, in every zone. */
final class ExitClientChecks {
	private static final int STAND_Y = Level0ChunkGenerator.FLOOR_Y + 1;

	private ExitClientChecks() {
	}

	static void run(ClientGameTestContext context, TestSingleplayerContext game, int returnX, int returnZ) {
		var server = game.getServer();
		long seed = server.computeOnServer(s -> Level0ChunkGenerator.mazeSeed(
				s.getLevel(Level0.LEVEL_0).getChunkSource().randomState()));

		int expectedZones = Maze.Zone.values().length - 1; // Exits never spawn inside RED_ROOM.
		Map<Maze.Zone, Exits.Door> doors = new EnumMap<>(Maze.Zone.class);
		for (int radius = 0; radius < 30 && doors.size() < expectedZones; radius++) {
			for (int rx = -radius; rx <= radius; rx++) {
				for (int rz = -radius; rz <= radius; rz++) {
					Exits.Door door = Exits.doorIn(seed, rx, rz);
					if (door != null) {
						Maze.Zone zone = Maze.zoneAt(seed, door.x(), door.z());
						check(zone != Maze.Zone.RED_ROOM, "exit generated inside RED_ROOM at " + door);
						doors.putIfAbsent(zone, door);
					}
				}
			}
		}
		check(doors.size() == expectedZones, "not every non-red zone has an exit: " + doors.keySet());

		int i = 0;
		for (var entry : doors.entrySet()) {
			Exits.Door door = entry.getValue();
			// Alternate sides and halves; the last one tests the spawn-in-Level-0 guard.
			boolean east = i % 2 == 0, lowerHalf = i % 3 == 0, spawnInside = i == doors.size() - 1;
			i++;
			int side = east ? 1 : -1;
			float yaw = east ? 90 : -90;

			// A hovering camera far enough back for the whole doorway, whatever the floor there is.
			server.runCommand("gamemode spectator @a");
			server.runCommand("execute as @a in noclip_backrooms:level_0 run tp @s %s %d %s %s 0"
					.formatted(door.x() + 0.5 + side * 4, STAND_Y, door.z() + 0.5, yaw));
			game.getConnection().waitForChunksDownload();
			context.waitTicks(25);
			game.getConnection().waitForChunksRender();
			server.runOnServer(s -> {
				var level = player(s).level();
				BlockPos bottom = new BlockPos(door.x(), STAND_Y, door.z());
				check(level.getBlockState(bottom).is(ModBlocks.EXIT_DOOR), "no door at " + door);
				check(level.getBlockState(bottom.above()).is(ModBlocks.EXIT_DOOR_TOP), "no door top at " + door);
				check(level.getBlockState(bottom.above(2)).is(ModBlocks.EXIT_SIGN), "no sign at " + door);
				check(level.getBlockState(bottom).getDestroySpeed(level, bottom) < 0, "exit can be broken");
			});
			context.runOnClient(client -> client.gui.toastManager().clear());
			context.waitTick();
			context.takeScreenshot("exit_" + entry.getKey().name().toLowerCase(java.util.Locale.ROOT));

			// Right at the door: that block is never a pit or a puddle.
			server.runCommand("execute as @a in noclip_backrooms:level_0 run tp @s %s %d %s %s 0"
					.formatted(door.x() + 0.5 + side, STAND_Y, door.z() + 0.5, yaw));
			server.runCommand("gamemode survival @a");
			context.waitTicks(10);
			server.runOnServer(s -> check(!player(s).isInWall(), "no room in front of " + door));

			boolean ownSpawn = i == 2;
			if (ownSpawn) server.runCommand("execute in minecraft:overworld run spawnpoint @a 40 -60 40");
			if (spawnInside) server.runCommand("execute in noclip_backrooms:level_0 run spawnpoint @a %d %d %d".formatted(door.x() + side, STAND_Y, door.z()));
			// A held block must not be placed instead of opening the door.
			server.runCommand("item replace entity @a hotbar.0 with minecraft:cobblestone 5");
			context.waitTicks(3);
			context.getInput().lookAt(yaw, lowerHalf ? 62 : 5);
			context.waitTicks(3);
			context.getInput().pressMouse(1);
			server.waitFor(s -> player(s).level().dimension() == Level.OVERWORLD, 200);

			// Long enough to notice being dropped straight back in by Noclip.
			context.waitTicks(60);
			server.runOnServer(s -> {
				ServerPlayer player = player(s);
				check(player.level().dimension() == Level.OVERWORLD, "bounced back into Level 0");
				check(!player.isInWall(), "arrived inside a block");
				if (ownSpawn) check(player.blockPosition().closerThan(new BlockPos(40, -60, 40), 4), "ignored the player's spawn point: " + player.position());
				check(player.getHealth() == player.getMaxHealth(), "hurt on the way out");
				check(player.getInventory().getItem(0).is(Items.COBBLESTONE) && player.getInventory().getItem(0).getCount() == 5,
						"held item used or lost");
			});
		}

		// Put the player back where the rest of the test expects them.
		server.runCommand("execute in minecraft:overworld run spawnpoint @a 0 -60 0");
		server.runCommand("execute as @a in noclip_backrooms:level_0 run tp @s %s %d %s"
				.formatted(returnX + 0.5, STAND_Y, returnZ + 0.5));
		game.getConnection().waitForChunksDownload();
		context.waitTicks(20);
		game.getConnection().waitForChunksRender();
	}

	private static ServerPlayer player(MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}

	private static void check(boolean condition, String message) {
		if (!condition) throw new AssertionError(message);
	}
}
