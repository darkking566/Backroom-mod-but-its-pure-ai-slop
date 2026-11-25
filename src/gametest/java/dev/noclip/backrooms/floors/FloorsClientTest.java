package dev.noclip.backrooms.floors;

import dev.noclip.backrooms.core.LevelExits;
import dev.noclip.backrooms.core.floors.FloorRun;
import dev.noclip.backrooms.core.floors.Floors;
import dev.noclip.backrooms.core.floors.FloorsClient;
import dev.noclip.backrooms.level0.Level0;
import dev.noclip.backrooms.level1.Level1;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

/**
 * The floor count of docs/FLOORS.md on a short trip: into Level 0 (floor 1), through an exit to Level 1 (floor 2, far
 * from the exit), back to Level 0 (floor 3, a fresh stretch far from where the trip began), then home (the trip ends).
 * Screenshots of the title, the floor at the top left and the level above the inventory. Run alone with
 * gradlew runClientGameTest -Plevels=floors
 */
public final class FloorsClientTest implements FabricClientGameTest {
	private static final int STAND_Y = 33;
	private static final double FAR = 30_000;

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext game = context.worldBuilder().create()) {
			var server = game.getServer();
			server.runCommand("gamerule send_command_feedback false");
			server.runCommand("gamerule noclip_backrooms:edge_shift false");
			server.runCommand("gamemode creative @a");
			check(server.computeOnServer(s -> Floors.of(player(s)).floor()) == 0, "a trip had begun in the Overworld");

			server.runCommand("execute as @a in noclip_backrooms:level_0 run tp @s 3.5 " + STAND_Y + " 3.5");
			server.waitFor(s -> player(s).level().dimension() == Level0.LEVEL_0, 200);
			check(server.computeOnServer(s -> Floors.of(player(s)).floor()) == 1, "falling into Level 0 is not floor 1");
			context.waitFor(client -> FloorsClient.floor() == 1, 100);
			game.getConnection().waitForChunksRender();
			context.waitTicks(5);
			context.takeScreenshot("floors_level0_title");
			Vec3 began = server.computeOnServer(s -> player(s).position());

			go(context, game, "1", Level1.LEVEL_1, began, 2);
			context.waitTicks(5);
			context.takeScreenshot("floors_level1_title");

			Vec3 exit = server.computeOnServer(s -> player(s).position());
			go(context, game, "0", Level0.LEVEL_0, exit, 3);
			check(server.computeOnServer(s -> player(s).position().subtract(began).horizontalDistance()) > FAR,
					"back in Level 0 the player landed where the trip began, not in a fresh stretch");
			check(server.computeOnServer(s -> Floors.of(player(s)).distinct()) == 2, "two different levels not counted as two");
			context.waitTicks(80);
			context.takeScreenshot("floors_level0_again");

			server.runCommand("gamemode survival @a");
			context.waitTicks(5);
			context.getInput().pressKey(GLFW.GLFW_KEY_E);
			context.waitTicks(5);
			context.takeScreenshot("floors_inventory");
			context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
			context.waitTicks(5);

			server.runCommand("gamemode creative @a");
			server.runCommand("execute as @a in minecraft:overworld run tp @s 0 120 0");
			server.waitFor(s -> player(s).level().dimension() == Level.OVERWORLD, 200);
			FloorRun home = server.computeOnServer(s -> Floors.of(player(s)));
			check(home.floor() == 0 && home.best() == 3, "the trip did not end at home: " + home);
		}
	}

	/** Through an exit to a level; checks the floor, and that the player landed far from {@code from}. */
	private static void go(ClientGameTestContext context, TestSingleplayerContext game, String level,
			net.minecraft.resources.ResourceKey<Level> dimension, Vec3 from, int floor) {
		var server = game.getServer();
		server.runOnServer(s -> LevelExits.use(player(s), level));
		server.waitFor(s -> player(s).level().dimension() == dimension, 400);
		check(server.computeOnServer(s -> Floors.of(player(s)).floor()) == floor, "not floor " + floor + " in Level " + level);
		check(server.computeOnServer(s -> player(s).position().subtract(from).horizontalDistance()) > FAR,
				"Level " + level + " was entered right by the exit, not in a fresh stretch");
		context.waitFor(client -> FloorsClient.floor() == floor, 100);
		game.getConnection().waitForChunksRender();
	}

	private static ServerPlayer player(MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}

	private static void check(boolean ok, String message) {
		if (!ok) throw new AssertionError(message);
	}
}
