package dev.noclip.backrooms.level0;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave;
import net.minecraft.core.BlockPos;
import net.minecraft.locale.Language;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/** Plays the first version in a real client: fall in, look around, die out, reload. */
public final class Level0ClientTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		TestWorldSave save;
		try (TestSingleplayerContext game = context.worldBuilder().create()) {
			save = game.getWorldSave();
			var server = game.getServer();
			game.getConnection().waitForChunksRender();
			// Keeps chat out of the screenshots, and holds Edge Shift until baseline block checks finish.
			server.runCommand("gamerule send_command_feedback false");
			server.runCommand("gamerule noclip_backrooms:edge_shift false");
			server.runCommand("gamemode survival @a");
			server.runCommand("give @a minecraft:apple 3");

			// Creative players do not suffocate, so they must stay in the Overworld.
			server.runCommand("gamemode creative @a");
			server.runCommand("execute as @a at @s run fill ~-1 ~ ~-1 ~1 ~2 ~1 minecraft:stone");
			context.waitTicks(40);
			server.runOnServer(s -> check(player(s).level().dimension() == Level.OVERWORLD, "creative player fell in"));
			server.runCommand("execute as @a at @s run fill ~-1 ~ ~-1 ~1 ~2 ~1 minecraft:air");

			int standY = Level0ChunkGenerator.FLOOR_Y + 1;

			// The shortcut command given in NEXT.md
			server.runCommand("execute as @a in noclip_backrooms:level_0 run tp @s 3.5 " + standY + " 3.5");
			context.waitTicks(20);
			server.runOnServer(s -> check(player(s).level().dimension() == Level0.LEVEL_0 && !player(s).isInWall(), "shortcut command failed"));
			// Spectators hover, so no fall damage is carried into the checks below.
			server.runCommand("gamemode spectator @a");
			server.runCommand("execute as @a in minecraft:overworld run tp @s 0 200 0");
			context.waitTicks(20);

			suffocate(context, game);
			server.waitFor(s -> player(s).level().dimension() == Level0.LEVEL_0, 400);
			context.waitTicks(40);
			game.getConnection().waitForChunksRender();
			server.runOnServer(s -> {
				ServerPlayer player = player(s);
				check(Math.abs(player.getY() - standY) < 0.01, "landed at y=" + player.getY());
				check(!player.isInWall(), "landed inside a wall");
				check(player.getHealth() == player.getMaxHealth(), "hurt on the way in");
				check(!player.getInventory().isEmpty(), "inventory lost on the way in");
				checkGeneratedBlocks(player.level());
				for (var entity : player.level().getAllEntities()) check(!(entity instanceof Mob), "a mob spawned");
			});

			checkBlocks(context, game);
			ExitClientChecks.run(context, game, 3, 3);
			checkEdgeShift(context, game, standY);
			checkRedRoomLoop(context, game, standY);
			checkShadowHallucination(context, game, standY);
			checkFlashlight(context, game, standY);
			server.runOnServer(s -> {
				for (int i = 0; i < ModBlocks.ALL.size(); i++) {
					player(s).getInventory().setItem(i, new ItemStack(ModBlocks.ALL.get(i)));
				}
			});

			for (int yaw = 0; yaw < 360; yaw += 90) {
				server.runCommand("execute as @a at @s run tp @s ~ ~ ~ " + yaw + " 5");
				context.waitTicks(15);
				context.runOnClient(client -> client.gui.toastManager().clear());
				context.waitTick();
				context.takeScreenshot("level0_yaw_" + yaw);
			}

			server.runCommand("execute as @a at @s run tp @s ~ ~ ~ 90 -55");
			context.waitTicks(15);
			context.takeScreenshot("level0_ceiling");

			server.runCommand("execute as @a at @s run tp @s ~ ~ ~ 0 5");
			context.waitTicks(10);
			context.getInput().holdKeyFor(options -> options.keyUp, 20);
			server.waitFor(s -> player(s).getZ() > 4, 100);

			server.runCommand("execute as @a at @s run tp @s 701.5 " + standY + " -699.5 45 5");
			game.getConnection().waitForChunksDownload();
			context.waitTicks(20);
			game.getConnection().waitForChunksRender();
			context.takeScreenshot("level0_far_away");
			server.runOnServer(s -> check(!player(s).isInWall(), "distant cell centre is blocked"));

			server.runCommand("kill @a");
			context.waitFor(client -> client.player != null && client.player.isDeadOrDying());
			context.runOnClient(client -> client.player.respawn());
			server.waitFor(s -> player(s).isAlive() && player(s).level().dimension() == Level.OVERWORLD, 400);
			game.getConnection().waitForChunksRender();

			// Second entry, onto a landing spot someone has built on.
			server.runOnServer(s -> s.getLevel(Level0.LEVEL_0)
					.setBlockAndUpdate(new BlockPos(3, standY, 3), Blocks.STONE.defaultBlockState()));
			suffocate(context, game);
			server.waitFor(s -> player(s).level().dimension() == Level0.LEVEL_0, 400);
			context.waitTicks(20);
			server.runOnServer(s -> check(player(s).getBlockX() == 10 && !player(s).isInWall(), "blocked landing not skipped: " + player(s).position()));

			// Inside Level 0 suffocation must hurt as normal.
			context.waitTicks(80);
			server.runCommand("execute as @a at @s run fill ~ ~ ~ ~ ~1 ~ minecraft:stone");
			server.runCommand("execute as @a at @s run fill ~-1 ~ ~-1 ~1 ~1 ~1 minecraft:stone");
			server.waitFor(s -> player(s).getHealth() < player(s).getMaxHealth(), 200);
			server.runCommand("execute as @a at @s run fill ~-1 ~ ~-1 ~1 ~1 ~1 minecraft:air");
			context.waitTicks(20);
		}

		try (TestSingleplayerContext game = save.open()) {
			game.getConnection().waitForChunksRender();
			game.getServer().runOnServer(s -> check(player(s).level().dimension() == Level0.LEVEL_0, "reload left Level 0"));
			context.takeScreenshot("level0_after_reload");
		}
	}

	/** The route given to players: a one-block pit, three deep, with two sand blocks dropped on your head. */
	private static void suffocate(ClientGameTestContext context, TestSingleplayerContext game) {
		var server = game.getServer();
		server.runCommand("gamemode survival @a");
		server.runCommand("fill 2 199 2 4 202 4 minecraft:stone");
		server.runCommand("fill 3 200 3 3 202 3 minecraft:air");
		server.runCommand("execute in minecraft:overworld run tp @a 3.5 200 3.5");
		game.getConnection().waitForChunksDownload();
		context.waitTicks(10);
		server.runCommand("item replace entity @a hotbar.0 with minecraft:sand 8");
		// Look up at the top block of the pit's west wall and place, as a player would.
		context.getInput().lookAt(90, -60);
		context.waitTicks(5);
		context.getInput().pressMouse(1);
		context.waitTicks(15);
		context.getInput().lookAt(90, -60);
		context.getInput().pressMouse(1);
	}

	private static void checkEdgeShift(ClientGameTestContext context, TestSingleplayerContext game, int standY) {
		var server = game.getServer();
		server.runCommand("execute as @a in noclip_backrooms:level_0 run tp @s 3.5 " + standY + " 3.5 0 5");
		game.getConnection().waitForChunksDownload();
		context.waitTicks(10);

		int frontZ = 3 * Maze.CELL + Maze.CENTER;
		server.runOnServer(s -> {
			ServerLevel level = player(s).level();
			for (int cz = -5; cz <= -3; cz++) {
				for (int cx = -3; cx <= 3; cx++) {
					level.setBlockAndUpdate(new BlockPos(cx * Maze.CELL + Maze.CENTER, standY, cz * Maze.CELL + Maze.CENTER),
							Blocks.COBBLESTONE.defaultBlockState());
				}
			}
			level.setBlockAndUpdate(new BlockPos(Maze.CENTER, standY, frontZ), Blocks.COBBLESTONE.defaultBlockState());
		});

		// With the gamerule off, nothing may shift.
		context.waitTicks(45);
		server.runOnServer(s -> {
			ServerLevel level = player(s).level();
			EdgeShift.ShiftState state = level.getDataStorage().computeIfAbsent(EdgeShift.ShiftState.TYPE);
			check(state.epochAt(0, -3) == 0
					&& level.getBlockState(new BlockPos(Maze.CENTER, standY, -3 * Maze.CELL + Maze.CENTER)).is(Blocks.COBBLESTONE),
					"edge shift ran while gamerule was disabled");
		});

		// Enable Edge Shift and wait for at least one behind-cell to shift while facing +Z.
		server.runCommand("gamerule noclip_backrooms:edge_shift true");
		server.waitFor(s -> {
			ServerLevel level = player(s).level();
			EdgeShift.ShiftState state = level.getDataStorage().computeIfAbsent(EdgeShift.ShiftState.TYPE);
			for (int cz = -5; cz <= -3; cz++) {
				for (int cx = -3; cx <= 3; cx++) {
					if (state.epochAt(cx, cz) > 0) return true;
				}
			}
			return false;
		}, 400);

		server.runOnServer(s -> {
			ServerLevel level = player(s).level();
			EdgeShift.ShiftState state = level.getDataStorage().computeIfAbsent(EdgeShift.ShiftState.TYPE);
			boolean verifiedShift = false;
			for (int cz = -5; cz <= -3; cz++) {
				for (int cx = -3; cx <= 3; cx++) {
					BlockPos markerPos = new BlockPos(cx * Maze.CELL + Maze.CENTER, standY, cz * Maze.CELL + Maze.CENTER);
					if (state.epochAt(cx, cz) > 0) {
						check(!level.getBlockState(markerPos).is(Blocks.COBBLESTONE),
								"shifted cell did not erase placed marker at " + markerPos);
						verifiedShift = true;
					} else if (level.getBlockState(markerPos).is(Blocks.COBBLESTONE)) {
						level.setBlockAndUpdate(markerPos, Blocks.AIR.defaultBlockState());
					}
				}
			}
			check(verifiedShift, "no behind-cell shifted");
			BlockPos frontPos = new BlockPos(Maze.CENTER, standY, frontZ);
			check(state.epochAt(0, 3) == 0 && level.getBlockState(frontPos).is(Blocks.COBBLESTONE),
					"observed cell in front of the player shifted");
			level.setBlockAndUpdate(frontPos, Blocks.AIR.defaultBlockState());
		});
		server.runCommand("gamerule noclip_backrooms:edge_shift false");

		// Watch mode should continuously shift cells right in front of the player (including (0, 3)), then turn off cleanly.
		server.runCommand("execute as @a run backrooms edgeshift watch on");
		server.runOnServer(s -> check(EdgeShift.isWatching(player(s)), "watch mode failed to enable"));
		server.waitFor(s -> player(s).level().getDataStorage().computeIfAbsent(EdgeShift.ShiftState.TYPE).epochAt(0, 3) > 0, 400);
		server.runCommand("execute as @a run backrooms edgeshift watch off");
		server.runOnServer(s -> check(!EdgeShift.isWatching(player(s)), "watch mode failed to disable"));
		server.runCommand("execute as @a run backrooms edgeshift status");
		server.runCommand("execute as @a run backrooms edgeshift pulse");
	}

	private static void checkRedRoomLoop(ClientGameTestContext context, TestSingleplayerContext game, int standY) {
		var server = game.getServer();
		int[] redSector = server.computeOnServer(s -> {
			ServerLevel level = s.getLevel(Level0.LEVEL_0);
			long seed = Level0ChunkGenerator.mazeSeed(level.getChunkSource().randomState());
			int sectorBlocks = Maze.CELL * Maze.SECTOR;
			for (int radius = 1; radius <= 40; radius++) {
				for (int sx = -radius; sx <= radius; sx++) {
					for (int sz = -radius; sz <= radius; sz++) {
						int osx = Maze.largeZoneOriginSector(sx);
						int osz = Maze.largeZoneOriginSector(sz);
						if (Maze.zoneAt(seed, osx * sectorBlocks + Maze.CENTER, osz * sectorBlocks + Maze.CENTER) == Maze.Zone.RED_ROOM
								&& Maze.zoneAt(seed, (osx - 1) * sectorBlocks + Maze.CENTER, osz * sectorBlocks + Maze.CENTER) != Maze.Zone.RED_ROOM) {
							return new int[] {osx, osz};
						}
					}
				}
			}
			throw new AssertionError("no RED_ROOM macro-zone found within radius 40");
		});

		int rsx = redSector[0], rsz = redSector[1];
		int innerX = (rsx * Maze.SECTOR + 3) * Maze.CELL + Maze.CENTER;
		int innerZ = (rsz * Maze.SECTOR + 3) * Maze.CELL + Maze.CENTER;
		server.runCommand("gamemode survival @a");
		server.runCommand("execute as @a in noclip_backrooms:level_0 run tp @s " + (innerX + 0.5) + " " + standY + " " + (innerZ + 0.5));
		game.getConnection().waitForChunksDownload();
		context.waitTicks(5);

		server.runOnServer(s -> {
			ServerPlayer player = player(s);
			check(RedRoomLoop.isTrapped(player), "player entering Red Room interior was not trapped");
			check(!RedRoomLoop.canBreakBlock(player.level(), player, new BlockPos(innerX, standY, innerZ),
					ModBlocks.RED_WALLPAPER.defaultBlockState()), "survival player could break Red Room wall");
		});

		// Stepping onto the outer perimeter cell of the Red Room macro-zone must loop the player back into rlx,rlz in 2..redCells-3.
		int perimeterX = (rsx * Maze.SECTOR) * Maze.CELL + Maze.CENTER;
		int redCells = Maze.LARGE_ZONE_SPAN * Maze.SECTOR;
		server.runCommand("execute as @a in noclip_backrooms:level_0 run tp @s " + (perimeterX + 0.5) + " " + standY + " " + (innerZ + 0.5));
		server.waitFor(s -> {
			ServerPlayer player = player(s);
			int rlx = Math.floorDiv(player.getBlockX(), Maze.CELL) - rsx * Maze.SECTOR;
			int rlz = Math.floorDiv(player.getBlockZ(), Maze.CELL) - rsz * Maze.SECTOR;
			return rlx >= 2 && rlx <= redCells - 3 && rlz >= 2 && rlz <= redCells - 3;
		}, 40);

		server.runOnServer(s -> check(RedRoomLoop.isTrapped(player(s)), "looped player lost trap state"));

		// Returning to the starting sector clears the trap and effects for subsequent screenshot checks.
		server.runCommand("execute as @a in noclip_backrooms:level_0 run tp @s 3.5 " + standY + " 3.5");
		server.runCommand("effect clear @a");
		game.getConnection().waitForChunksDownload();
		context.waitTicks(5);
		server.runOnServer(s -> {
			ServerPlayer player = player(s);
			check(!RedRoomLoop.isTrapped(player), "distant teleport did not release Red Room trap");
			check(!player.isInWall(), "stuck in wall after returning from Red Room");
		});
	}

	private static void checkShadowHallucination(ClientGameTestContext context, TestSingleplayerContext game, int standY) {
		var server = game.getServer();
		server.runCommand("execute as @a in noclip_backrooms:level_0 run tp @s 3.5 " + standY + " 3.5 0 0");
		game.getConnection().waitForChunksDownload();
		context.waitTicks(5);

		server.runCommand("execute as @a run backrooms shadow");
		server.runOnServer(s -> {
			ServerPlayer player = player(s);
			check(ShadowHallucination.hasActiveShadow(player), "shadow hallucination failed to spawn");
			for (var entity : player.level().getAllEntities()) check(!(entity instanceof Mob), "shadow spawned a Mob");
		});

		server.runCommand("execute as @a run backrooms shadow clear");
		server.runOnServer(s -> check(!ShadowHallucination.hasActiveShadow(player(s)), "shadow clear command failed"));
		server.runCommand("effect clear @a");
	}

	private static void checkFlashlight(ClientGameTestContext context, TestSingleplayerContext game, int standY) {
		var server = game.getServer();
		server.runCommand("execute as @a in noclip_backrooms:level_0 run tp @s 3.5 " + standY + " 3.5 0 0");
		server.runCommand("item replace entity @a weapon.mainhand with noclip_backrooms:flashlight");
		context.waitTicks(3);

		context.getInput().pressMouse(1);
		server.waitFor(s -> {
			ServerPlayer p = player(s);
			return FlashlightItem.isLit(p.getMainHandItem()) && FlashlightLight.hasActiveBeam(p);
		}, 40);

		context.getInput().pressMouse(1);
		server.waitFor(s -> {
			ServerPlayer p = player(s);
			return !FlashlightItem.isLit(p.getMainHandItem()) && !FlashlightLight.hasActiveBeam(p);
		}, 40);

		server.runCommand("item replace entity @a weapon.mainhand with minecraft:air");
	}

	private static void checkBlocks(ClientGameTestContext context, TestSingleplayerContext game) {
		game.getServer().runOnServer(s -> {
			ServerLevel level = player(s).level();
			for (Block block : ModBlocks.ALL) {
				if (block.getLootTable().isEmpty()) continue; // the unbreakable exit
				var drops = Block.getDrops(block.defaultBlockState(), level, new BlockPos(3, 3, 3), null);
				check(drops.size() == 1 && drops.getFirst().is(block.asItem()), "wrong drop for " + block);
			}
		});
		context.runOnClient(client -> {
			for (Block block : ModBlocks.ALL) {
				check(Language.getInstance().has(block.getDescriptionId()), "no name for " + block);
				check(block.asItem().getDescriptionId().equals(block.getDescriptionId()), "wrong item name key for " + block);
			}
			check(Language.getInstance().has(ModItems.FLASHLIGHT.getDescriptionId()), "no name for flashlight");
		});
	}

	private static void checkGeneratedBlocks(ServerLevel level) {
		long seed = Level0ChunkGenerator.mazeSeed(level.getChunkSource().randomState());
		int[] ys = {Level0ChunkGenerator.MIN_Y, 0, Level0ChunkGenerator.FLOOR_Y, Level0ChunkGenerator.FLOOR_Y + 1,
				Level0ChunkGenerator.FLOOR_Y + 2, Level0ChunkGenerator.CEILING_Y - 1,
				Level0ChunkGenerator.CEILING_Y, Level0ChunkGenerator.MAX_Y};
		for (int x = -32; x < 32; x++) {
			for (int z = -32; z < 32; z++) {
				for (int y : ys) {
					var expected = Level0ChunkGenerator.blockAt(seed, x, y, z);
					check(level.getBlockState(new BlockPos(x, y, z)).is(expected.getBlock()),
							"world differs from generator at " + x + ", " + y + ", " + z);
				}
			}
		}
	}

	private static ServerPlayer player(MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}

	private static void check(boolean condition, String message) {
		if (!condition) throw new AssertionError(message);
	}
}
