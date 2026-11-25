package dev.noclip.backrooms.itemlook;

import dev.noclip.backrooms.BackroomsMod;
import dev.noclip.backrooms.core.look.ItemLook;
import dev.noclip.backrooms.core.look.Look;
import dev.noclip.backrooms.level0.Level0;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.lwjgl.glfw.GLFW;

/**
 * The realistic items pack (core.look.ItemLook) against the earlier models: every item it redraws laid out in chests,
 * a few weapons in hand and seen from outside, the props in a room of Level 0; once with the pack off, once on. Run
 * alone with gradlew runClientGameTest -Plevels=itemlook
 */
public final class ItemLookClientTest implements FabricClientGameTest {
	private static final int FLOOR = 32;
	/** A spread of shapes to hold (the batch being worked on). */
	private static final String[] HELD = {"west_arming_sword", "gun_pistol_9mm", "west_claymore", "gun_revolver", "blunt_twohand_maul",
			"gun_assault_rifle", "cn_guandao", "magic_stopmotion_watch"};
	/** Which of HELD to see from outside. */
	private static final int[] OUTSIDE = {0, 1};

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext game = context.worldBuilder().create()) {
			var server = game.getServer();
			server.runCommand("gamerule send_command_feedback false");
			server.runCommand("gamerule noclip_backrooms:edge_shift false");
			server.runCommand("gamemode survival @a");
			server.runCommand("execute as @a in noclip_backrooms:level_0 run tp @s 3.5 " + (FLOOR + 1) + " 6.5 180 18");
			server.waitFor(s -> player(s).level().dimension() == Level0.LEVEL_0, 200);
			server.runOnServer(ItemLookClientTest::room);
			server.runOnServer(s -> player(s).getAbilities().invulnerable = true);
			context.runOnClient(client -> Look.setEnabled(false));
			setPack(context, true);
			List<String> covered = context.computeOnClient(client -> ItemLook.covered().stream().sorted().toList());
			check(covered.size() > 40, "the pack lists only " + covered.size() + " things");
			game.getConnection().waitForChunksRender();

			for (boolean on : new boolean[] {false, true}) {
				setPack(context, on);
				check(context.computeOnClient(client -> ItemLook.on()) == on, "the pack did not switch " + (on ? "on" : "off"));
				String label = on ? "claude" : "gemini";
				// Every item, in chests of 54.
				for (int page = 0; page * 54 < covered.size(); page++) {
					List<String> slice = covered.subList(page * 54, Math.min(covered.size(), page * 54 + 54));
					server.runOnServer(s -> openChest(player(s), slice));
					context.waitTicks(10);
					context.takeScreenshot("itemlook_chest_" + page + "_" + label);
					context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
					context.waitTicks(5);
				}
				// In hand.
				server.runOnServer(s -> {
					ServerPlayer player = player(s);
					player.getInventory().clearContent();
					for (int i = 0; i < HELD.length; i++) player.getInventory().setItem(i, new ItemStack(item(HELD[i])));
				});
				server.runCommand("execute as @a in noclip_backrooms:level_0 run tp @s 3.5 " + (FLOOR + 1) + " 4.5 180 8");
				game.getConnection().waitForChunksRender();
				for (int i = 0; i < HELD.length; i++) {
					context.getInput().pressKey(GLFW.GLFW_KEY_1 + i);
					context.waitTicks(25);
					context.takeScreenshot("itemlook_hand_" + HELD[i] + "_" + label);
					if (HELD[i].startsWith("gun_") || HELD[i].equals("office_crossbow")) {
						// Aimed down the sights: the sights should line up on the middle, the gun below them.
						context.getInput().holdMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
						context.waitTicks(25);
						context.takeScreenshot("itemlook_ads_" + HELD[i] + "_" + label);
						context.getInput().releaseMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
						context.waitTicks(15);
					}
				}
				// From outside, holding a great sword and a polearm.
				context.runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
				for (int i : OUTSIDE) {
					context.getInput().pressKey(GLFW.GLFW_KEY_1 + i);
					context.waitTicks(20);
					context.takeScreenshot("itemlook_third_" + HELD[i] + "_" + label);
				}
				context.runOnClient(client -> client.options.setCameraType(CameraType.FIRST_PERSON));
				// The props.
				context.getInput().pressKey(GLFW.GLFW_KEY_9);
				server.runCommand("execute as @a in noclip_backrooms:level_0 run tp @s 3.5 " + (FLOOR + 1) + " 3.2 180 30");
				context.waitTicks(15);
				context.takeScreenshot("itemlook_props_" + label);
			}
		}
	}

	private static void openChest(ServerPlayer player, List<String> ids) {
		SimpleContainer chest = new SimpleContainer(54);
		for (int i = 0; i < ids.size(); i++) chest.setItem(i, new ItemStack(item(ids.get(i))));
		player.openMenu(new SimpleMenuProvider((id, inventory, p) -> ChestMenu.sixRows(id, inventory, chest), Component.literal("Realistic items")));
	}

	/** A small lit room of Level 0 around the player, the props against its north wall. */
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
					BlockState state = !edge ? Blocks.AIR.defaultBlockState() : (y == FLOOR + 1 ? base : wall).defaultBlockState();
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

	private static Item item(String id) {
		return BuiltInRegistries.ITEM.getValue(BackroomsMod.id(id));
	}

	private static void setPack(ClientGameTestContext context, boolean on) {
		CompletableFuture<Void> reload = context.computeOnClient(client -> {
			var packs = client.getResourcePackRepository();
			String id = packs.getAvailableIds().stream().filter(p -> p.endsWith(ItemLook.PACK.getPath())).findFirst()
					.orElseThrow(() -> new AssertionError("no realistic items pack among " + packs.getAvailableIds()));
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
