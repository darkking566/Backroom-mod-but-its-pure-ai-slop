package dev.noclip.backrooms.level0;

import dev.noclip.backrooms.core.LevelExits;
import dev.noclip.backrooms.level1.Level1Blocks;
import dev.noclip.backrooms.level1.Level1Items;
import dev.noclip.backrooms.props.BackroomsProps;
import java.util.ArrayDeque;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Set;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.locale.Language;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.state.BlockState;

/** Plays the first version in a real client: fall in, look around, die out, reload. */
public final class Level0ClientTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		if (!dev.noclip.backrooms.TestFilter.level("level0")) return;
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
			checkManilaRoom(context, game, standY);
			checkStaircaseComplexes(context, game, standY);
			checkZonePois(context, game, standY);
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
			// Dying in the Backrooms without having settled at a base wakes the player lost in Level 0 again, not at home
			// (docs/HORROR_ESCAPE_PLAN.md, 2026-11-25); suffocate() below takes them back to the Overworld.
			server.waitFor(s -> player(s).isAlive() && player(s).level().dimension() == Level0.LEVEL_0, 400);
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
			check(Language.getInstance().has(Level1Items.ALMOND_WATER.getDescriptionId()), "no name for almond water");
		});
	}

	private static void checkManilaRoom(ClientGameTestContext context, TestSingleplayerContext game, int standY) {
		var server = game.getServer();
		ManilaRoomPlan.Room room = server.computeOnServer(s -> {
			ServerLevel level = s.getLevel(Level0.LEVEL_0);
			long seed = Level0ChunkGenerator.mazeSeed(level.getChunkSource().randomState());
			for (int radius = 0; radius <= 12; radius++) {
				for (int rx = -radius; rx <= radius; rx++) {
					for (int rz = -radius; rz <= radius; rz++) {
						if (Math.max(Math.abs(rx), Math.abs(rz)) != radius) continue;
						ManilaRoomPlan.Room found = ManilaRoomPlan.roomInRegion(seed, rx, rz);
						if (found != null) return found;
					}
				}
			}
			throw new AssertionError("no Manila Room found within radius 12 regions");
		});

		int bx = room.baseX(), bz = room.baseZ();
		server.runCommand("gamemode survival @a");
		server.runCommand("execute as @a in noclip_backrooms:level_0 run tp @s " + (bx + 4.5) + " " + standY + " " + (bz + 4.5) + " -45 5");
		game.getConnection().waitForChunksDownload();
		context.waitTicks(10);
		game.getConnection().waitForChunksRender();
		context.takeScreenshot("level0_manila_room");

		server.runOnServer(s -> {
			ServerLevel level = player(s).level();
			check(level.getBlockState(new BlockPos(bx + 4, Level0ChunkGenerator.FLOOR_Y, bz + 4)).is(ModBlocks.MANILA_WOOD_FLOOR),
					"Manila Room floor missing");
			check(level.getBlockState(new BlockPos(bx + 6, standY, bz + 6)).is(ModBlocks.MANILA_TABLE),
					"Manila Room table missing");
			check(level.getBlockState(new BlockPos(bx + 7, standY, bz + 7)).is(ModBlocks.MANILA_CABINET),
					"Manila Room cabinet missing");
			check(level.getBlockState(new BlockPos(bx + 6, standY + 1, bz + 6)).is(ModBlocks.MANILA_NOTE),
					"Manila Room guide folder missing");
			check(level.getBlockState(new BlockPos(bx + 5, standY, bz + 6)).is(ModBlocks.MANILA_CHAIR),
					"Manila Room chair missing");
			check(level.getBlockState(new BlockPos(bx + 1, standY, bz + 6)).is(ModBlocks.MANILA_DOOR),
					"Manila Room door missing");
			check(level.getBlockState(new BlockPos(bx + 1, standY, bz + 3)).is(ModBlocks.VOID_STONE),
					"Manila Room hidden Void Stone missing");
			check(level.getBlockState(new BlockPos(bx + 6, Level0ChunkGenerator.CEILING_Y, bz + 6)).is(ModBlocks.MANILA_LIGHT),
					"Manila Room warm light missing");
		});

		// Standing inside the Manila Room must trigger a nearby Level 1 flickering wall quickly.
		server.waitFor(s -> {
			ServerLevel level = player(s).level();
			BlockPos center = player(s).blockPosition();
			for (int dx = -42; dx <= 42; dx++) {
				for (int dz = -42; dz <= 42; dz++) {
					if (level.getBlockState(center.offset(dx, 0, dz)).is(dev.noclip.backrooms.level1.Level1Blocks.FLICKERING_WALLPAPER)) {
						return true;
					}
				}
			}
			return false;
		}, 120);

		// Return to origin sector before remaining checks.
		server.runCommand("execute as @a in noclip_backrooms:level_0 run tp @s 3.5 " + standY + " 3.5");
		game.getConnection().waitForChunksDownload();
		context.waitTicks(5);
	}

	private static void checkStaircaseComplexes(ClientGameTestContext context, TestSingleplayerContext game, int standY) {
		var server = game.getServer();
		EnumMap<StaircasePlan.Type, StaircasePlan.Complex> found = server.computeOnServer(s -> {
			ServerLevel level = s.getLevel(Level0.LEVEL_0);
			long seed = Level0ChunkGenerator.mazeSeed(level.getChunkSource().randomState());
			EnumMap<StaircasePlan.Type, StaircasePlan.Complex> map = new EnumMap<>(StaircasePlan.Type.class);
			for (int radius = 1; radius <= 24 && map.size() < StaircasePlan.Type.values().length; radius++) {
				for (int sx = -radius; sx <= radius; sx++) {
					for (int sz = -radius; sz <= radius; sz++) {
						if (Math.max(Math.abs(sx), Math.abs(sz)) != radius) continue;
						StaircasePlan.Complex c = StaircasePlan.complexInSector(seed, sx, sz);
						if (c != null) {
							map.putIfAbsent(c.type(), c);
						}
					}
				}
			}
			for (StaircasePlan.Type t : StaircasePlan.Type.values()) {
				check(map.containsKey(t), "missing staircase type " + t);
			}
			for (StaircasePlan.Complex c : map.values()) {
				verify3DReachabilityAndNoExposedBedrock(seed, c);
			}
			return map;
		});

		// Live test in a Utility Loft: verify furniture, notes, breaker lever, and light restoration.
		StaircasePlan.Complex loft = found.get(StaircasePlan.Type.UTILITY_LOFT);
		int standX = loft.worldX(14, 10);
		int standZ = loft.worldZ(14, 10);
		int leverX = loft.worldX(15, 10);
		int leverZ = loft.worldZ(15, 10);
		Direction toWall = loft.worldDir(Direction.EAST);
		float yaw = switch (toWall) {
			case SOUTH -> 0F;
			case WEST -> 90F;
			case NORTH -> 180F;
			default -> -90F;
		};

		server.runCommand("gamemode survival @a");
		server.runCommand("item replace entity @a hotbar.0 with minecraft:air");
		server.runCommand("execute as @a in noclip_backrooms:level_0 run tp @s " + (standX + 0.5) + " 38 " + (standZ + 0.5) + " " + yaw + " 12");
		game.getConnection().waitForChunksDownload();
		context.waitTicks(10);
		game.getConnection().waitForChunksRender();
		context.takeScreenshot("level0_staircase_loft");

		server.runOnServer(s -> {
			ServerLevel level = player(s).level();
			check(level.getBlockState(new BlockPos(loft.worldX(11, 6), 39, loft.worldZ(11, 6))).is(Level1Blocks.OLD_COMPUTER),
					"Utility Loft computer missing");
			check(level.getBlockState(new BlockPos(loft.worldX(12, 6), 39, loft.worldZ(12, 6))).is(ModBlocks.FIELD_NOTE),
					"Utility Loft field note missing");
			check(level.getBlockState(new BlockPos(loft.worldX(11, 14), 38, loft.worldZ(11, 14))).is(ModBlocks.MANILA_CABINET),
					"Utility Loft cabinet missing");
			check(level.getBlockState(new BlockPos(loft.worldX(12, 14), 38, loft.worldZ(12, 14))).is(Level1Blocks.SUPPLY_CRATE),
					"Utility Loft supply crate missing");
			BlockState lever = level.getBlockState(new BlockPos(leverX, 39, leverZ));
			check(lever.is(Blocks.LEVER) && !lever.getValue(LeverBlock.POWERED), "Utility Loft lever missing or already on");
			check(level.getBlockState(new BlockPos(loft.worldX(14, 7), 42, loft.worldZ(14, 7))).is(ModBlocks.BROKEN_FLUORESCENT_LIGHT),
					"expected broken fluorescent light in Utility Loft before breaker throw");
		});

		// Aim directly at wall-mounted lever hitbox and flip it.
		double aimX = leverX + 0.5 + toWall.getStepX() * 0.35;
		double aimZ = leverZ + 0.5 + toWall.getStepZ() * 0.35;
		double ddx = aimX - (standX + 0.5);
		double ddz = aimZ - (standZ + 0.5);
		double ddy = 39.5 - 39.62;
		float exactYaw = (float) Math.toDegrees(Math.atan2(-ddx, ddz));
		float exactPitch = (float) Math.toDegrees(-Math.atan2(ddy, Math.hypot(ddx, ddz)));
		context.getInput().lookAt(exactYaw, exactPitch);
		context.waitTicks(3);
		context.getInput().pressMouse(1);
		server.waitFor(s -> {
			ServerLevel level = player(s).level();
			BlockState lever = level.getBlockState(new BlockPos(leverX, 39, leverZ));
			return lever.is(Blocks.LEVER) && lever.getValue(LeverBlock.POWERED)
					&& level.getBlockState(new BlockPos(loft.worldX(14, 7), 42, loft.worldZ(14, 7))).is(ModBlocks.FLUORESCENT_LIGHT);
		}, 60);

		// Live test in a Flooded Basement: verify stairs, water sump, pipe, and field note.
		StaircasePlan.Complex basement = found.get(StaircasePlan.Type.FLOODED_BASEMENT);
		server.runCommand("execute as @a in noclip_backrooms:level_0 run tp @s "
				+ (basement.worldX(11, 10) + 0.5) + " 25 " + (basement.worldZ(11, 10) + 0.5) + " " + yaw + " 10");
		game.getConnection().waitForChunksDownload();
		context.waitTicks(10);
		game.getConnection().waitForChunksRender();
		context.takeScreenshot("level0_flooded_basement");

		server.runOnServer(s -> {
			ServerLevel level = player(s).level();
			check(level.getBlockState(new BlockPos(basement.worldX(13, 10), 24, basement.worldZ(13, 10))).is(Blocks.WATER),
					"Flooded Basement sump water missing");
			check(level.getBlockState(new BlockPos(basement.worldX(13, 10), 28, basement.worldZ(13, 10))).is(Level1Blocks.LEAKING_CEILING_PIPE),
					"Flooded Basement leaking pipe missing");
			check(level.getBlockState(new BlockPos(basement.worldX(17, 10), 26, basement.worldZ(17, 10))).is(ModBlocks.FIELD_NOTE),
					"Flooded Basement field note missing");
			check(level.getBlockState(new BlockPos(basement.worldX(8, 10), 25, basement.worldZ(8, 10))).is(Level1Blocks.CONCRETE_STAIRS),
					"Flooded Basement concrete stairs missing");
		});

		server.runCommand("execute as @a in noclip_backrooms:level_0 run tp @s 3.5 " + standY + " 3.5");
		game.getConnection().waitForChunksDownload();
		context.waitTicks(5);
	}

	private static void checkZonePois(ClientGameTestContext context, TestSingleplayerContext game, int standY) {
		var server = game.getServer();
		long seed = server.computeOnServer(s -> Level0ChunkGenerator.mazeSeed(
				s.getLevel(Level0.LEVEL_0).getChunkSource().randomState()));

		ZonePoiPlan.ChasmOutpost chasm = findChasmOutpost(seed);
		ZonePoiPlan.MegahallLounge megahall = findMegahallLounge(seed);
		int[] redCell = findLargeZonePoiCell(seed, Maze.Zone.RED_ROOM);
		int[] darkenedCell = findLargeZonePoiCell(seed, Maze.Zone.DARKENED);

		// 1. CHASM outpost
		int cx = chasm.cx() * Maze.CELL, cz = chasm.cz() * Maze.CELL;
		int cFloor = chasm.floorY();
		server.runCommand("gamemode spectator @a");
		server.runCommand("execute as @a in noclip_backrooms:level_0 run tp @s "
				+ (cx + 5.5) + " " + (cFloor + 1) + " " + (cz + 1.5) + " 45 10");
		game.getConnection().waitForChunksDownload();
		context.waitTicks(15);
		game.getConnection().waitForChunksRender();
		context.takeScreenshot("level0_chasm_outpost");
		server.runOnServer(s -> {
			ServerLevel level = player(s).level();
			check(level.getBlockState(new BlockPos(cx + 3, cFloor, cz + 3)).is(Level1Blocks.GARAGE_FLOOR),
					"Chasm outpost floor missing");
			Block expectedLoot = chasm.tier() >= 4 ? BackroomsProps.ANOMALY_VAULT_CHEST : Level1Blocks.SUPPLY_CRATE;
			check(level.getBlockState(new BlockPos(cx + 3, cFloor + 1, cz + 3)).is(expectedLoot),
					"Chasm outpost chest/crate missing");
			check(level.getBlockState(new BlockPos(cx + 4, cFloor + 1, cz + 3)).is(BackroomsProps.NOCLIPPED_FOLDING_TABLE),
					"Chasm outpost folding table missing");
			check(level.getBlockState(new BlockPos(cx + 4, cFloor + 2, cz + 3)).is(Level1Blocks.OLD_COMPUTER),
					"Chasm outpost computer missing");
			check(level.getBlockState(new BlockPos(cx + 3, cFloor + 1, cz + 4)).is(BackroomsProps.WANDERER_REMAINS),
					"Chasm outpost wanderer remains missing");
			check(level.getBlockState(new BlockPos(cx + 2, cFloor + 1, cz + 4)).is(BackroomsProps.INDUSTRIAL_FUSE_BOX),
					"Chasm outpost fuse box missing");
			int lightY = chasm.ceilY() > cFloor + 8 ? cFloor + 4 : chasm.ceilY();
			check(level.getBlockState(new BlockPos(cx + 3, lightY, cz + 3)).is(ModBlocks.FLUORESCENT_LIGHT),
					"Chasm outpost fluorescent light missing");
		});

		// 2. MEGATHALL skylight lounge
		int mx = megahall.cx() * Maze.CELL, mz = megahall.cz() * Maze.CELL;
		int mFloor = megahall.floorY();
		server.runCommand("execute as @a in noclip_backrooms:level_0 run tp @s "
				+ (mx + 5.5) + " " + (mFloor + 1) + " " + (mz + 1.5) + " 45 6");
		game.getConnection().waitForChunksDownload();
		context.waitTicks(15);
		game.getConnection().waitForChunksRender();
		context.takeScreenshot("level0_megahall_lounge");
		server.runOnServer(s -> {
			ServerLevel level = player(s).level();
			check(level.getBlockState(new BlockPos(mx + 3, mFloor, mz + 3)).is(ModBlocks.MANILA_WOOD_FLOOR),
					"Megahall lounge wood inlay missing");
			check(level.getBlockState(new BlockPos(mx + 3, mFloor + 1, mz + 3)).is(BackroomsProps.NOCLIPPED_FOLDING_TABLE),
					"Megahall lounge table missing");
			check(level.getBlockState(new BlockPos(mx + 4, mFloor + 1, mz + 3)).is(BackroomsProps.NOCLIPPED_CRT_TV),
					"Megahall lounge CRT TV missing");
			check(level.getBlockState(new BlockPos(mx + 3, mFloor + 1, mz + 4)).is(BackroomsProps.NOCLIPPED_OFFICE_CHAIR),
					"Megahall lounge office chair missing");
			check(level.getBlockState(new BlockPos(mx + 2, mFloor + 1, mz + 4)).is(BackroomsProps.NOCLIPPED_WATER_COOLER),
					"Megahall lounge water cooler missing");
			check(level.getBlockState(new BlockPos(mx + 3, mFloor + 5, mz + 3)).is(ModBlocks.FLUORESCENT_LIGHT),
					"Megahall lounge lightwell lamp missing");
		});

		// 3. RED_ROOM anomaly shrine
		int rx = redCell[0] * Maze.CELL, rz = redCell[1] * Maze.CELL;
		server.runCommand("execute as @a in noclip_backrooms:level_0 run tp @s "
				+ (rx + 1.5) + " " + standY + " " + (rz + 1.5) + " -45 6");
		game.getConnection().waitForChunksDownload();
		context.waitTicks(15);
		game.getConnection().waitForChunksRender();
		context.takeScreenshot("level0_red_shrine");
		server.runOnServer(s -> {
			ServerLevel level = player(s).level();
			check(level.getBlockState(new BlockPos(rx + 3, Level0ChunkGenerator.FLOOR_Y, rz + 3)).is(ModBlocks.VOID_STONE),
					"Red Room shrine pedestal missing");
			check(level.getBlockState(new BlockPos(rx + 3, standY, rz + 3)).is(BackroomsProps.RED_ANOMALY_CLUSTER),
					"Red Room anomaly cluster missing");
			check(level.getBlockState(new BlockPos(rx + 4, standY, rz + 3)).is(BackroomsProps.ANOMALY_VAULT_CHEST),
					"Red Room anomaly vault chest missing");
			check(level.getBlockState(new BlockPos(rx + 2, standY, rz + 4)).is(BackroomsProps.WANDERER_REMAINS),
					"Red Room wanderer remains missing");
			check(level.getBlockState(new BlockPos(rx + 3, Level0ChunkGenerator.CEILING_Y, rz + 3)).is(ModBlocks.RED_FLUORESCENT_LIGHT),
					"Red Room shrine light missing");
		});

		// 4. DARKENED static signal
		int dx = darkenedCell[0] * Maze.CELL, dz = darkenedCell[1] * Maze.CELL;
		server.runCommand("execute as @a in noclip_backrooms:level_0 run tp @s "
				+ (dx + 3.5) + " " + standY + " " + (dz + 5.5) + " 180 12");
		game.getConnection().waitForChunksDownload();
		context.waitTicks(15);
		game.getConnection().waitForChunksRender();
		context.takeScreenshot("level0_darkened_signal");
		server.runOnServer(s -> {
			ServerLevel level = player(s).level();
			check(level.getBlockState(new BlockPos(dx + 3, standY, dz + 3)).is(BackroomsProps.NOCLIPPED_CRT_TV),
					"Darkened signal CRT TV missing");
			check(level.getBlockState(new BlockPos(dx + 4, standY, dz + 3)).is(BackroomsProps.WANDERER_REMAINS),
					"Darkened signal wanderer remains missing");
			check(level.getBlockState(new BlockPos(dx + 2, standY, dz + 4)).is(Level1Blocks.SUPPLY_CRATE),
					"Darkened signal supply crate missing");
		});

		// 5. Sector micro-vignettes (GLASS_OFFICE, WANDERER_SITE, NOCLIP_ALCOVE)
		EnumMap<ZonePoiPlan.VignetteType, ZonePoiPlan.Vignette> vignettes = findAllVignettes(seed);
		ZonePoiPlan.Vignette glassOffice = vignettes.get(ZonePoiPlan.VignetteType.GLASS_OFFICE);
		int gx = glassOffice.cx() * Maze.CELL, gz = glassOffice.cz() * Maze.CELL;
		server.runCommand("execute as @a in noclip_backrooms:level_0 run tp @s "
				+ (gx + 5.5) + " " + standY + " " + (gz + 5.5) + " 135 8");
		game.getConnection().waitForChunksDownload();
		context.waitTicks(15);
		game.getConnection().waitForChunksRender();
		context.takeScreenshot("level0_glass_office");
		server.runOnServer(s -> {
			ServerLevel level = player(s).level();
			check(level.getBlockState(new BlockPos(gx + 2, standY, gz + 2)).is(Blocks.STAINED_GLASS.gray()),
					"Glass office partition missing");
			check(level.getBlockState(new BlockPos(gx + 3, standY, gz + 3)).is(Level1Blocks.OFFICE_DESK),
					"Glass office desk missing");
			check(level.getBlockState(new BlockPos(gx + 3, standY + 1, gz + 3)).is(Level1Blocks.OLD_COMPUTER),
					"Glass office computer missing");
			check(level.getBlockState(new BlockPos(gx + 3, standY, gz + 4)).is(BackroomsProps.NOCLIPPED_OFFICE_CHAIR),
					"Glass office chair missing");
			check(level.getBlockState(new BlockPos(gx + 4, standY, gz + 4)).is(Level1Blocks.WASTEBASKET),
					"Glass office wastebasket missing");
			check(level.getBlockState(new BlockPos(gx + 4, Level0ChunkGenerator.CEILING_Y, gz + 3)).is(ModBlocks.FLUORESCENT_LIGHT),
					"Glass office lit lamp missing");
		});

		ZonePoiPlan.Vignette wandererSite = vignettes.get(ZonePoiPlan.VignetteType.WANDERER_SITE);
		int wx = wandererSite.cx() * Maze.CELL, wz = wandererSite.cz() * Maze.CELL;
		server.runCommand("execute as @a in noclip_backrooms:level_0 run tp @s "
				+ (wx + 5.5) + " " + standY + " " + (wz + 5.5) + " 135 12");
		game.getConnection().waitForChunksDownload();
		context.waitTicks(15);
		game.getConnection().waitForChunksRender();
		context.takeScreenshot("level0_wanderer_site");
		server.runOnServer(s -> {
			ServerLevel level = player(s).level();
			check(level.getBlockState(new BlockPos(wx + 3, standY, wz + 3)).is(BackroomsProps.WANDERER_REMAINS),
					"Wanderer site remains missing");
			check(level.getBlockState(new BlockPos(wx + 4, standY, wz + 3)).is(BackroomsProps.NOCLIPPED_WOOD_CHAIR),
					"Wanderer site wood chair missing");
			check(level.getBlockState(new BlockPos(wx + 2, standY, wz + 3)).is(Level1Blocks.SUPPLY_CRATE),
					"Wanderer site supply crate missing");
			check(level.getBlockState(new BlockPos(wx + 4, standY, wz + 4)).is(ModBlocks.FIELD_NOTE),
					"Wanderer site field note missing");
			check(level.getBlockState(new BlockPos(wx + 3, Level0ChunkGenerator.CEILING_Y, wz + 3)).is(ModBlocks.BROKEN_FLUORESCENT_LIGHT),
					"Wanderer site broken lamp missing");
		});

		ZonePoiPlan.Vignette noclipAlcove = vignettes.get(ZonePoiPlan.VignetteType.NOCLIP_ALCOVE);
		int nx = noclipAlcove.cx() * Maze.CELL, nz = noclipAlcove.cz() * Maze.CELL;
		server.runCommand("execute as @a in noclip_backrooms:level_0 run tp @s "
				+ (nx + 3.5) + " " + standY + " " + (nz + 5.5) + " 180 8");
		game.getConnection().waitForChunksDownload();
		context.waitTicks(15);
		game.getConnection().waitForChunksRender();
		context.takeScreenshot("level0_noclip_alcove");
		server.runOnServer(s -> {
			ServerLevel level = player(s).level();
			check(level.getBlockState(new BlockPos(nx + 3, standY, nz + 3)).is(BackroomsProps.NOCLIPPED_VENDING_MACHINE),
					"Noclip alcove vending machine missing");
			check(level.getBlockState(new BlockPos(nx + 4, standY, nz + 3)).is(BackroomsProps.NOCLIPPED_WATER_COOLER),
					"Noclip alcove water cooler missing");
			check(level.getBlockState(new BlockPos(nx + 2, standY, nz + 3)).is(BackroomsProps.NOCLIPPED_FILE_CABINET),
					"Noclip alcove file cabinet missing");
			check(level.getBlockState(new BlockPos(nx + 4, standY, nz + 4)).is(BackroomsProps.NOCLIPPED_OFFICE_CHAIR),
					"Noclip alcove office chair missing");
		});

		ZonePoiPlan.Vignette breakerNiche = vignettes.get(ZonePoiPlan.VignetteType.BREAKER_NICHE);
		int bx = breakerNiche.cx() * Maze.CELL, bz = breakerNiche.cz() * Maze.CELL;
		server.runCommand("gamemode survival @a");
		server.runCommand("item replace entity @a hotbar.0 with minecraft:air");
		server.runCommand("execute as @a in noclip_backrooms:level_0 run tp @s "
				+ (bx + 3.5) + " " + standY + " " + (bz + 5.5) + " 180 12");
		game.getConnection().waitForChunksDownload();
		context.waitTicks(15);
		game.getConnection().waitForChunksRender();
		context.takeScreenshot("level0_breaker_niche");
		server.runOnServer(s -> {
			ServerLevel level = player(s).level();
			check(level.getBlockState(new BlockPos(bx + 3, standY, bz + 3)).is(BackroomsProps.INDUSTRIAL_FUSE_BOX),
					"Breaker niche fuse box missing");
			check(level.getBlockState(new BlockPos(bx + 4, standY, bz + 3)).is(Level1Blocks.SUPPLY_CRATE),
					"Breaker niche supply crate missing");
			check(level.getBlockState(new BlockPos(bx + 2, standY + 2, bz + 3)).is(Level1Blocks.CEILING_PIPE),
					"Breaker niche ceiling pipe missing");
			check(level.getBlockState(new BlockPos(bx + 3, Level0ChunkGenerator.CEILING_Y, bz + 3)).is(ModBlocks.BROKEN_FLUORESCENT_LIGHT),
					"Breaker niche overhead lamp should start broken");
			check(FuseBoxEvents.trigger(level, player(s), new BlockPos(bx + 3, standY, bz + 3), true),
					"Breaker niche fuse box failed to restore");
			check(level.getBlockState(new BlockPos(bx + 3, Level0ChunkGenerator.CEILING_Y, bz + 3)).is(ModBlocks.FLUORESCENT_LIGHT),
					"Breaker niche overhead lamp did not turn back on");
			check(FuseBoxEvents.isOnCooldown(level, new BlockPos(bx + 3, standY, bz + 3)),
					"Breaker niche fuse box did not enter cooldown");
		});

		ZonePoiPlan.Vignette peekChamber = vignettes.get(ZonePoiPlan.VignetteType.PEEK_CHAMBER);
		int px = peekChamber.cx() * Maze.CELL, pz = peekChamber.cz() * Maze.CELL;
		server.runCommand("gamemode spectator @a");
		server.runCommand("execute as @a in noclip_backrooms:level_0 run tp @s "
				+ (px + 3.5) + " " + standY + " " + (pz + 5.5) + " 180 8");
		game.getConnection().waitForChunksDownload();
		context.waitTicks(15);
		game.getConnection().waitForChunksRender();
		context.takeScreenshot("level0_peek_chamber");
		server.runOnServer(s -> {
			ServerLevel level = player(s).level();
			check(level.getBlockState(new BlockPos(px + 3, standY, pz + 4)).is(Blocks.GLASS),
					"Peek chamber viewing window missing");
			check(level.getBlockState(new BlockPos(px + 3, standY, pz + 3)).is(BackroomsProps.NOCLIPPED_WOOD_CHAIR),
					"Peek chamber chair missing");
			check(level.getBlockState(new BlockPos(px + 3, standY, pz + 2)).isAir(),
					"Peek chamber north back entrance must stay open");
			check(level.getBlockState(new BlockPos(px + 3, Level0ChunkGenerator.CEILING_Y, pz + 3)).is(ModBlocks.FLUORESCENT_LIGHT),
					"Peek chamber light missing");
		});

		// 6. Mystery foreshadowing exits (HUB_DOOR -> "hub", FUN_DOOR -> "fun")
		ZonePoiPlan.MysteryExit hubExit = findMysteryExit(seed, ZonePoiPlan.MysteryKind.HUB);
		int hx = hubExit.cx() * Maze.CELL, hz = hubExit.cz() * Maze.CELL;
		server.runCommand("gamemode survival @a");
		server.runCommand("item replace entity @a hotbar.0 with minecraft:air");
		server.runCommand("execute as @a in noclip_backrooms:level_0 run tp @s "
				+ (hx + 3.5) + " " + standY + " " + (hz + 4.8) + " 180 6");
		game.getConnection().waitForChunksDownload();
		context.waitTicks(15);
		game.getConnection().waitForChunksRender();
		context.takeScreenshot("level0_hub_door");
		int refusalsBefore = LevelExits.refusals();
		server.runOnServer(s -> {
			ServerLevel level = player(s).level();
			BlockState door = level.getBlockState(new BlockPos(hx + 3, standY, hz + 3));
			check(door.is(ModBlocks.HUB_DOOR) && door.getValue(MysteryExitBlock.AXIS) == Direction.Axis.Z,
					"Hub door missing or wrong axis");
			check(level.getBlockState(new BlockPos(hx + 3, standY + 1, hz + 3)).is(ModBlocks.HUB_DOOR_TOP),
					"Hub door top missing");
		});
		context.getInput().lookAt(180, 5);
		context.waitTicks(3);
		context.getInput().pressMouse(1);
		server.waitFor(s -> LevelExits.refusals() > refusalsBefore, 40);

		ZonePoiPlan.MysteryExit funExit = findMysteryExit(seed, ZonePoiPlan.MysteryKind.FUN);
		int fx = funExit.cx() * Maze.CELL, fz = funExit.cz() * Maze.CELL;
		server.runCommand("gamemode spectator @a");
		server.runCommand("execute as @a in noclip_backrooms:level_0 run tp @s "
				+ (fx + 3.5) + " " + standY + " " + (fz + 4.8) + " 180 6");
		game.getConnection().waitForChunksDownload();
		context.waitTicks(15);
		game.getConnection().waitForChunksRender();
		context.takeScreenshot("level0_fun_door");
		server.runOnServer(s -> {
			ServerLevel level = player(s).level();
			check(level.getBlockState(new BlockPos(fx + 3, Level0ChunkGenerator.FLOOR_Y, fz + 4)).is(ModBlocks.DRY_CARPET),
					"Fun door clean carpet missing");
			check(level.getBlockState(new BlockPos(fx + 3, standY, fz + 3)).is(ModBlocks.FUN_DOOR),
					"Fun door missing");
			check(level.getBlockState(new BlockPos(fx + 3, standY + 1, fz + 3)).is(ModBlocks.FUN_DOOR_TOP),
					"Fun door top missing");
			check(level.getBlockState(new BlockPos(fx + 3, Level0ChunkGenerator.CEILING_Y, fz + 4)).is(ModBlocks.MANILA_LIGHT),
					"Fun door warm ceiling light missing");
		});

		server.runCommand("gamemode survival @a");
		server.runCommand("execute as @a in noclip_backrooms:level_0 run tp @s 3.5 " + standY + " 3.5");
		game.getConnection().waitForChunksDownload();
		context.waitTicks(5);
	}

	private static EnumMap<ZonePoiPlan.VignetteType, ZonePoiPlan.Vignette> findAllVignettes(long seed) {
		EnumMap<ZonePoiPlan.VignetteType, ZonePoiPlan.Vignette> map = new EnumMap<>(ZonePoiPlan.VignetteType.class);
		for (int radius = 1; radius <= 24 && map.size() < ZonePoiPlan.VignetteType.values().length; radius++) {
			for (int sx = -radius; sx <= radius; sx++) {
				for (int sz = -radius; sz <= radius; sz++) {
					if (Math.max(Math.abs(sx), Math.abs(sz)) != radius) continue;
					ZonePoiPlan.Vignette v = ZonePoiPlan.vignetteInSector(seed, sx, sz);
					if (v != null) map.putIfAbsent(v.type(), v);
				}
			}
		}
		for (ZonePoiPlan.VignetteType t : ZonePoiPlan.VignetteType.values()) {
			check(map.containsKey(t), "missing vignette type " + t);
		}
		return map;
	}

	private static ZonePoiPlan.MysteryExit findMysteryExit(long seed, ZonePoiPlan.MysteryKind kind) {
		for (int radius = 0; radius <= 12; radius++) {
			for (int rx = -radius; rx <= radius; rx++) {
				for (int rz = -radius; rz <= radius; rz++) {
					if (Math.max(Math.abs(rx), Math.abs(rz)) != radius) continue;
					ZonePoiPlan.MysteryExit exit = ZonePoiPlan.mysteryExitInRegion(seed, rx, rz);
					if (exit != null && exit.kind() == kind) return exit;
				}
			}
		}
		throw new AssertionError("no mystery exit " + kind + " found within radius 12 regions");
	}

	private static ZonePoiPlan.ChasmOutpost findChasmOutpost(long seed) {
		ZonePoiPlan.ChasmOutpost fallback = null;
		for (int radius = 0; radius <= 24; radius++) {
			for (int mx = -radius; mx <= radius; mx++) {
				for (int mz = -radius; mz <= radius; mz++) {
					if (Math.max(Math.abs(mx), Math.abs(mz)) != radius) continue;
					int baseCx = mx * ChasmPlan.MACRO_CELLS;
					int baseCz = mz * ChasmPlan.MACRO_CELLS;
					if (Maze.zoneAt(seed, baseCx * Maze.CELL + Maze.CENTER, baseCz * Maze.CELL + Maze.CENTER) != Maze.Zone.CHASM) {
						continue;
					}
					ZonePoiPlan.ChasmOutpost found = ZonePoiPlan.chasmOutpostInMacro(seed, baseCx, baseCz);
					if (found == null) continue;
					if (found.tier() >= 4) return found;
					if (fallback == null) fallback = found;
				}
			}
		}
		if (fallback != null) return fallback;
		throw new AssertionError("no CHASM outpost found within macro radius 24");
	}

	private static ZonePoiPlan.MegahallLounge findMegahallLounge(long seed) {
		for (int radius = 0; radius <= 24; radius++) {
			for (int mx = -radius; mx <= radius; mx++) {
				for (int mz = -radius; mz <= radius; mz++) {
					if (Math.max(Math.abs(mx), Math.abs(mz)) != radius) continue;
					int baseCx = mx * MegahallPlan.MACRO_CELLS;
					int baseCz = mz * MegahallPlan.MACRO_CELLS;
					if (Maze.zoneAt(seed, baseCx * Maze.CELL + Maze.CENTER, baseCz * Maze.CELL + Maze.CENTER) != Maze.Zone.MEGATHALL) {
						continue;
					}
					ZonePoiPlan.MegahallLounge found = ZonePoiPlan.megahallLoungeInMacro(seed, baseCx, baseCz);
					if (found != null) return found;
				}
			}
		}
		throw new AssertionError("no MEGATHALL lounge found within macro radius 24");
	}

	private static int[] findLargeZonePoiCell(long seed, Maze.Zone target) {
		for (int radius = 1; radius <= 40; radius++) {
			for (int sx = -radius; sx <= radius; sx++) {
				for (int sz = -radius; sz <= radius; sz++) {
					if (Math.max(Math.abs(sx), Math.abs(sz)) != radius) continue;
					int osx = Maze.largeZoneOriginSector(sx);
					int osz = Maze.largeZoneOriginSector(sz);
					if (osx == 0 && osz == 0) continue;
					if (Maze.baseZoneForSector(seed, osx, osz) == target) {
						return new int[] {osx * Maze.SECTOR + 12, osz * Maze.SECTOR + 12};
					}
				}
			}
		}
		throw new AssertionError("no " + target + " macro-zone found within sector radius 40");
	}

	private static void verify3DReachabilityAndNoExposedBedrock(long seed, StaircasePlan.Complex c) {
		int bx = c.baseX(), bz = c.baseZ();
		int minY = 23, maxY = 43;

		// Verify no exposed bedrock touches any open or transparent block inside the 21x21 structure.
		for (int dx = 0; dx < StaircasePlan.SPAN_BLOCKS; dx++) {
			for (int dz = 0; dz < StaircasePlan.SPAN_BLOCKS; dz++) {
				int x = bx + dx, z = bz + dz;
				for (int y = minY; y <= maxY; y++) {
					BlockState state = Level0ChunkGenerator.blockAt(seed, x, y, z);
					if (state.isAir() || state.is(Blocks.WATER) || state.is(Blocks.STAINED_GLASS.gray()) || state.is(Blocks.LEVER)) {
						for (Direction dir : Direction.values()) {
							BlockState neighbor = Level0ChunkGenerator.blockAt(seed, x + dir.getStepX(), y + dir.getStepY(), z + dir.getStepZ());
							check(!neighbor.is(Blocks.BEDROCK), "exposed bedrock near (" + x + "," + y + "," + z + ") in " + c.type());
						}
					}
				}
			}
		}

		// Run 3D voxel BFS starting from entry ground block (lu=1, lv=10, floor=32).
		Set<Long> visited = new HashSet<>();
		ArrayDeque<BlockPos> queue = new ArrayDeque<>();
		BlockPos start = new BlockPos(c.worldX(1, 10), Level0ChunkGenerator.FLOOR_Y, c.worldZ(1, 10));
		check(isWalkableVoxel(seed, start.getX(), start.getY(), start.getZ()), "stair entry not walkable for " + c.type());
		visited.add(start.asLong());
		queue.add(start);

		while (!queue.isEmpty()) {
			BlockPos cur = queue.poll();
			for (Direction dir : Direction.Plane.HORIZONTAL) {
				int nx = cur.getX() + dir.getStepX(), nz = cur.getZ() + dir.getStepZ();
				if (nx < bx || nx >= bx + StaircasePlan.SPAN_BLOCKS || nz < bz || nz >= bz + StaircasePlan.SPAN_BLOCKS) {
					continue;
				}
				for (int dy = -1; dy <= 1; dy++) {
					int ny = cur.getY() + dy;
					if (ny < minY || ny > maxY) continue;
					BlockPos next = new BlockPos(nx, ny, nz);
					if (!visited.contains(next.asLong()) && isWalkableVoxel(seed, nx, ny, nz)) {
						visited.add(next.asLong());
						queue.add(next);
					}
				}
			}
		}

		switch (c.type()) {
			case ATTIC_SHORTCUT -> {
				check(visited.contains(new BlockPos(c.worldX(10, 10), StaircasePlan.UPPER_FLOOR_Y, c.worldZ(10, 10)).asLong()),
						"ATTIC_SHORTCUT center unreachable");
				check(visited.contains(new BlockPos(c.worldX(20, 10), Level0ChunkGenerator.FLOOR_Y, c.worldZ(20, 10)).asLong()),
						"ATTIC_SHORTCUT exit ground unreachable");
			}
			case PARADOX_LOOP -> {
				check(visited.contains(new BlockPos(c.worldX(10, 8), StaircasePlan.UPPER_FLOOR_Y, c.worldZ(10, 8)).asLong()),
						"PARADOX_LOOP sealed-door alcove unreachable");
				check(visited.contains(new BlockPos(c.worldX(20, 10), Level0ChunkGenerator.FLOOR_Y, c.worldZ(20, 10)).asLong()),
						"PARADOX_LOOP exit ground unreachable");
			}
			case UTILITY_LOFT -> check(visited.contains(new BlockPos(c.worldX(14, 10), StaircasePlan.UPPER_FLOOR_Y, c.worldZ(14, 10)).asLong()),
					"UTILITY_LOFT breaker area unreachable");
			case OBSERVATION_OVERLOOK -> check(visited.contains(new BlockPos(c.worldX(12, 10), StaircasePlan.UPPER_FLOOR_Y, c.worldZ(12, 10)).asLong()),
					"OBSERVATION_OVERLOOK glass deck unreachable");
			case FLOODED_BASEMENT -> {
				check(visited.contains(new BlockPos(c.worldX(15, 10), StaircasePlan.BASEMENT_FLOOR_Y, c.worldZ(15, 10)).asLong()),
						"FLOODED_BASEMENT dry platform unreachable");
				check(visited.contains(new BlockPos(c.worldX(13, 10), StaircasePlan.BASEMENT_FLOOR_Y - 1, c.worldZ(13, 10)).asLong()),
						"FLOODED_BASEMENT water sump unreachable");
			}
		}
	}

	private static boolean isWalkableVoxel(long seed, int x, int y, int z) {
		BlockState floor = Level0ChunkGenerator.blockAt(seed, x, y, z);
		if (floor.isAir() || floor.is(Blocks.WATER) || floor.is(ModBlocks.FIELD_NOTE) || floor.is(Blocks.LEVER)) {
			return false;
		}
		return isPassableHeadroom(Level0ChunkGenerator.blockAt(seed, x, y + 1, z))
				&& isPassableHeadroom(Level0ChunkGenerator.blockAt(seed, x, y + 2, z));
	}

	private static boolean isPassableHeadroom(BlockState state) {
		return state.isAir() || state.is(Blocks.WATER) || state.is(Blocks.LEVER) || state.is(ModBlocks.FIELD_NOTE);
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
