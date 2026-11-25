package dev.noclip.backrooms.trial;

import dev.noclip.backrooms.BackroomsMod;
import dev.noclip.backrooms.core.look.Look;
import dev.noclip.backrooms.core.look.TrialPack;
import dev.noclip.backrooms.level0.Level0;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.lwjgl.glfw.GLFW;

/**
 * Claude's trial models (core.look.TrialPack) beside the current ones: a small room of Level 0 with the two props in
 * it, then each of the six items in hand, and the inventory; once with the trial pack off, once on. Run alone with
 * gradlew runClientGameTest -Plevels=trial
 */
public final class TrialClientTest implements FabricClientGameTest {
	private static final int FLOOR = 32;
	private static final String[] ITEMS = {"blunt_rusty_pipe", "improvised_crowbar", "magic_noclip_wand", "gun_pipe_pistol", "copper_wire", "almond_elixir"};

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext game = context.worldBuilder().create()) {
			var server = game.getServer();
			server.runCommand("gamerule send_command_feedback false");
			server.runCommand("gamerule noclip_backrooms:edge_shift false");
			server.runCommand("gamemode survival @a");
			server.runCommand("execute as @a in noclip_backrooms:level_0 run tp @s 3.5 " + (FLOOR + 1) + " 6.5 180 18");
			server.waitFor(s -> player(s).level().dimension() == Level0.LEVEL_0, 200);
			server.runOnServer(TrialClientTest::room);
			server.runOnServer(s -> {
				ServerPlayer player = player(s);
				player.getInventory().clearContent();
				for (int i = 0; i < ITEMS.length; i++) player.getInventory().setItem(i, new ItemStack(BuiltInRegistries.ITEM.getValue(BackroomsMod.id(ITEMS[i]))));
				player.getAbilities().invulnerable = true;
			});
			context.runOnClient(client -> Look.setEnabled(false));
			game.getConnection().waitForChunksRender();

			for (boolean on : new boolean[] {false, true}) {
				setPack(context, on);
				check(context.computeOnClient(client -> TrialPack.on()) == on, "the trial pack did not switch " + (on ? "on" : "off"));
				String label = on ? "claude" : "gemini";
				server.runCommand("execute as @a in noclip_backrooms:level_0 run tp @s 3.5 " + (FLOOR + 1) + " 6.5 180 22");
				context.getInput().pressKey(GLFW.GLFW_KEY_9);
				game.getConnection().waitForChunksRender();
				context.waitTicks(20);
				context.takeScreenshot("trial_props_" + label);
				server.runCommand("execute as @a in noclip_backrooms:level_0 run tp @s 3.5 " + (FLOOR + 1) + " 3.2 180 30");
				context.waitTicks(10);
				context.takeScreenshot("trial_close_" + label);
				server.runCommand("execute as @a in noclip_backrooms:level_0 run tp @s 3.5 " + (FLOOR + 1) + " 4.5 180 8");
				context.waitTicks(10);
				for (int i = 0; i < ITEMS.length; i++) {
					context.getInput().pressKey(GLFW.GLFW_KEY_1 + i);
					context.waitTicks(25);
					context.takeScreenshot("trial_hand_" + ITEMS[i] + "_" + label);
				}
				context.getInput().pressKey(GLFW.GLFW_KEY_E);
				context.waitTicks(8);
				context.takeScreenshot("trial_inventory_" + label);
				context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
				context.waitTicks(5);
			}
		}
	}

	/** A small lit room of Level 0 around the player, the two props against its north wall. */
	private static void room(MinecraftServer server) {
		ServerLevel level = server.getLevel(Level0.LEVEL_0);
		Block carpet = block("carpet"), wall = block("wallpaper"), base = block("wallpaper_baseboard"), ceiling = block("ceiling_tile"),
				light = block("fluorescent_light");
		for (int x = -4; x <= 10; x++) {
			for (int z = -4; z <= 10; z++) {
				boolean edge = x == -4 || x == 10 || z == -4 || z == 10;
				level.setBlockAndUpdate(new BlockPos(x, FLOOR, z), carpet.defaultBlockState());
				boolean lamp = (x == 3 || x == 0 || x == 6) && (z == 1 || z == 6);
				level.setBlockAndUpdate(new BlockPos(x, FLOOR + 4, z), (lamp ? light : ceiling).defaultBlockState());
				for (int y = FLOOR + 1; y <= FLOOR + 3; y++) {
					BlockState state = !edge ? net.minecraft.world.level.block.Blocks.AIR.defaultBlockState()
							: (y == FLOOR + 1 ? base : wall).defaultBlockState();
					level.setBlockAndUpdate(new BlockPos(x, y, z), state);
				}
			}
		}
		place(level, "prop_office_chair", new BlockPos(5, FLOOR + 1, 0));
		place(level, "prop_desktop_computer", new BlockPos(1, FLOOR + 1, 0));
	}

	private static void place(ServerLevel level, String id, BlockPos at) {
		BlockState state = block(id).defaultBlockState();
		if (state.hasProperty(HorizontalDirectionalBlock.FACING)) state = state.setValue(HorizontalDirectionalBlock.FACING, Direction.SOUTH);
		level.setBlockAndUpdate(at, state);
	}

	private static Block block(String id) {
		return BuiltInRegistries.BLOCK.getValue(BackroomsMod.id(id));
	}

	private static void setPack(ClientGameTestContext context, boolean on) {
		CompletableFuture<Void> reload = context.computeOnClient(client -> {
			var packs = client.getResourcePackRepository();
			String id = packs.getAvailableIds().stream().filter(p -> p.endsWith(TrialPack.PACK.getPath())).findFirst()
					.orElseThrow(() -> new AssertionError("no trial pack among " + packs.getAvailableIds()));
			List<String> selected = new ArrayList<>(packs.getSelectedIds());
			if (selected.contains(id) == on) return null;
			if (on) selected.add(id);
			else selected.remove(id);
			packs.setSelected(selected);
			return client.reloadResourcePacks();
		});
		if (reload != null) context.waitFor(client -> reload.isDone() && client.gui.overlay() == null, 2400);
	}

	private static ServerPlayer player(MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}

	private static void check(boolean ok, String message) {
		if (!ok) throw new AssertionError(message);
	}
}
