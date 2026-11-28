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
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.InteractionHand;
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

/** Plays Level 0 in a real client: fall in, verify cinematic 5-high scale, Plan C stage loop, pitfalls undercroft, micro-events, multi-form exits, and reload. */
public final class Level0ClientTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		if (!dev.noclip.backrooms.TestFilter.level("level0")) return;
		TestWorldSave save;
		try (TestSingleplayerContext game = context.worldBuilder().create()) {
			save = game.getWorldSave();
			var server = game.getServer();
			// Not a newcomer: the first trip's cable (core.story.FirstTrip) belongs to the story test.
			server.runOnServer(s -> s.getPlayerList().getPlayers().forEach(p -> p.setAttached(dev.noclip.backrooms.core.story.FirstTrip.DONE, true)));
			game.getConnection().waitForChunksRender();
			server.runCommand("gamerule send_command_feedback false");
			server.runCommand("gamerule noclip_backrooms:edge_shift false");
			server.runCommand("gamerule noclip_backrooms:stage_loop false");
			server.runCommand("gamemode survival @a");
			server.runCommand("give @a minecraft:apple 3");

			// Creative players do not suffocate, so they must stay in the Overworld.
			server.runCommand("gamemode creative @a");
			server.runCommand("execute as @a at @s run fill ~-1 ~ ~-1 ~1 ~2 ~1 minecraft:stone");
			context.waitTicks(40);
			server.runOnServer(s -> check(player(s).level().dimension() == Level.OVERWORLD, "creative player fell in"));
			server.runCommand("execute as @a at @s run fill ~-1 ~ ~-1 ~1 ~2 ~1 minecraft:air");

			int standY = Level0ChunkGenerator.FLOOR_Y + 1;
			double originC = Maze.CENTER + 0.5;

			server.runCommand("execute as @a in noclip_backrooms:level_0 run tp @s " + originC + " " + standY + " " + originC);
			context.waitTicks(20);
			server.runOnServer(s -> check(player(s).level().dimension() == Level0.LEVEL_0 && !player(s).isInWall(), "shortcut command failed"));
			server.runCommand("gamemode spectator @a");
			server.runCommand("execute as @a in minecraft:overworld run tp @s 0 200 0");
			context.waitTicks(20);
			context.waitFor(client -> client.gui.screen() instanceof dev.noclip.backrooms.core.trip.MissionReportScreen, 200);
			context.runOnClient(client -> client.gui.screen().onClose());

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
			ExitClientChecks.run(context, game, Maze.CENTER, Maze.CENTER);
			checkEdgeShift(context, game, standY);
			checkRedRoomLoop(context, game, standY);
			checkShadowHallucination(context, game, standY);
			checkFlashlight(context, game, standY);
			checkManilaRoom(context, game, standY);
			checkStaircaseComplexes(context, game, standY);
			checkZonePois(context, game, standY);
			checkOverhaulFeatures(context, game, standY);

			server.runOnServer(s -> {
				int limit = Math.min(ModBlocks.ALL.size(), 36);
				for (int i = 0; i < limit; i++) {
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
			server.waitFor(s -> player(s).getZ() > Maze.CENTER + 1, 100);

			server.runCommand("execute as @a at @s run tp @s 705.5 " + standY + " -694.5 45 5");
			game.getConnection().waitForChunksDownload();
			context.waitTicks(20);
			game.getConnection().waitForChunksRender();
			context.takeScreenshot("level0_far_away");
			server.runOnServer(s -> check(!player(s).isInWall(), "distant cell centre is blocked"));

			// Death leaves everything where it fell but the mapping beacon, and tells the way back.
			server.runCommand("clear @a");
			server.runCommand("give @a noclip_backrooms:mapping_beacon");
			server.runCommand("give @a minecraft:bread");
			context.waitTicks(5);
			// A second beacon, mid-scan when its owner dies (the entry is moved away for the moment: far enough to set it down).
			BlockPos scanning = server.computeOnServer(s -> {
				ServerPlayer player = player(s);
				ServerLevel level = player.level();
				BlockPos at = player.blockPosition().north();
				level.setBlockAndUpdate(at, Blocks.AIR.defaultBlockState());
				level.setBlockAndUpdate(at.below(), Blocks.STONE.defaultBlockState());
				var entry = player.getAttached(dev.noclip.backrooms.core.trip.Trips.ENTRY);
				player.setAttached(dev.noclip.backrooms.core.trip.Trips.ENTRY, new dev.noclip.backrooms.core.trip.Trips.Entry(
						level.dimension().identifier().toString(), at.east(1000), 0));
				ItemStack held = player.getMainHandItem().copy();
				player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(dev.noclip.backrooms.core.field.FieldKit.BEACON));
				player.gameMode.useItemOn(player, level, player.getMainHandItem(), InteractionHand.MAIN_HAND,
						new BlockHitResult(Vec3.atCenterOf(at.below()).add(0, 0.5, 0), Direction.UP, at.below(), false));
				player.setItemInHand(InteractionHand.MAIN_HAND, held);
				if (entry == null) player.removeAttached(dev.noclip.backrooms.core.trip.Trips.ENTRY);
				else player.setAttached(dev.noclip.backrooms.core.trip.Trips.ENTRY, entry);
				check(dev.noclip.backrooms.core.field.Evidence.scanning(level, at), "the test beacon did not start scanning");
				return at;
			});
			server.runCommand("kill @a");
			context.waitFor(client -> client.player != null && client.player.isDeadOrDying());
			context.runOnClient(client -> client.player.respawn());
			server.waitFor(s -> player(s).isAlive() && player(s).level().dimension() == Level0.LEVEL_0, 400);
			game.getConnection().waitForChunksRender();
			server.runOnServer(s -> {
				var inventory = player(s).getInventory();
				check(inventory.countItem(dev.noclip.backrooms.core.field.FieldKit.BEACON) == 2, "death took a mapping beacon, or doubled one");
				ServerLevel level0 = s.getLevel(Level0.LEVEL_0);
				level0.getChunk(scanning);
				check(!level0.getBlockState(scanning).is(dev.noclip.backrooms.core.field.FieldKit.BEACON_BLOCK), "the scanning beacon was left standing");
				check(inventory.countItem(net.minecraft.world.item.Items.BREAD) == 0, "the rest was not left behind");
				check(player(s).hasAttached(dev.noclip.backrooms.core.trip.Relics.AT), "where the things fell was not noted");
				check(dev.noclip.backrooms.core.trip.Relics.tell(player(s)), "no way back to the things");
				check(dev.noclip.backrooms.core.trip.Relics.octant(0, 5, 0) == 0 && dev.noclip.backrooms.core.trip.Relics.octant(-5, 0, 0) == 2
						&& dev.noclip.backrooms.core.trip.Relics.octant(0, -5, 0) == 4 && dev.noclip.backrooms.core.trip.Relics.octant(5, 0, 0) == 6, "directions are off");
			});

			int[] zone = server.computeOnServer(s -> dev.noclip.backrooms.core.trip.Expedition.zone(s, "0"));
			int landX = Math.floorDiv(zone[0], Maze.CELL) * Maze.CELL + Maze.CENTER;
			int landZ = Math.floorDiv(zone[1], Maze.CELL) * Maze.CELL + Maze.CENTER;
			server.runOnServer(s -> {
				s.getLevel(Level0.LEVEL_0).getChunk(new BlockPos(landX, standY, landZ));
				s.getLevel(Level0.LEVEL_0).setBlockAndUpdate(new BlockPos(landX, standY, landZ), Blocks.STONE.defaultBlockState());
			});
			// A pit hole under the first cell's centre (a PITS zone): Noclip lands on that cell's walkway, the stone not in the way.
			boolean pitCentre = server.computeOnServer(s -> s.getLevel(Level0.LEVEL_0).getBlockState(new BlockPos(landX, standY - 1, landZ)).isAir());
			suffocate(context, game);
			server.waitFor(s -> player(s).level().dimension() == Level0.LEVEL_0, 400);
			context.waitTicks(20);
			if (pitCentre) server.runOnServer(s -> check(Math.floorDiv(player(s).getBlockX(), Maze.CELL) == Math.floorDiv(landX, Maze.CELL) && !player(s).isInWall(),
					"pit landing not on the first cell's walkway: " + player(s).position()));
			else server.runOnServer(s -> check(Math.floorDiv(player(s).getBlockX(), Maze.CELL) >= Math.floorDiv(landX, Maze.CELL) + 1
					&& Math.floorDiv(player(s).getBlockX(), Maze.CELL) < Math.floorDiv(landX, Maze.CELL) + 64
					&& Math.floorDiv(player(s).getBlockZ(), Maze.CELL) == Math.floorDiv(landZ, Maze.CELL) && !player(s).isInWall(),
					"blocked landing not skipped: " + player(s).position() + ", first cell " + landX + ", " + landZ));

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

	private static void suffocate(ClientGameTestContext context, TestSingleplayerContext game) {
		var server = game.getServer();
		server.runCommand("gamemode survival @a");
		server.runCommand("fill 2 199 2 4 202 4 minecraft:stone");
		server.runCommand("fill 3 200 3 3 202 3 minecraft:air");
		server.runCommand("execute in minecraft:overworld run tp @a 3.5 200 3.5");
		game.getConnection().waitForChunksDownload();
		context.waitTicks(10);
		context.waitTicks(30);
		context.runOnClient(client -> {
			if (client.gui.screen() instanceof dev.noclip.backrooms.core.trip.MissionReportScreen report) report.onClose();
		});
		server.runCommand("item replace entity @a hotbar.0 with minecraft:sand 8");
		context.getInput().lookAt(90, -60);
		context.waitTicks(5);
		context.getInput().pressMouse(1);
		context.waitTicks(15);
		context.getInput().lookAt(90, -60);
		context.getInput().pressMouse(1);
	}

	private static void checkEdgeShift(ClientGameTestContext context, TestSingleplayerContext game, int standY) {
		var server = game.getServer();
		double originC = Maze.CENTER + 0.5;
		server.runCommand("execute as @a in noclip_backrooms:level_0 run tp @s " + originC + " " + standY + " " + originC + " 0 5");
		game.getConnection().waitForChunksDownload();
		context.waitTicks(10);

		int frontZ = 3 * Maze.CELL + Maze.CENTER;
		server.runOnServer(s -> {
			ServerLevel level = player(s).level();
			for (int cz = -4; cz <= -2; cz++) {
				for (int cx = -2; cx <= 2; cx++) {
					level.setBlockAndUpdate(new BlockPos(cx * Maze.CELL + Maze.CENTER, standY, cz * Maze.CELL + Maze.CENTER),
							Blocks.COBBLESTONE.defaultBlockState());
				}
			}
			level.setBlockAndUpdate(new BlockPos(Maze.CENTER, standY, frontZ), Blocks.COBBLESTONE.defaultBlockState());
		});

		context.waitTicks(45);
		server.runOnServer(s -> {
			ServerLevel level = player(s).level();
			EdgeShift.ShiftState state = level.getDataStorage().computeIfAbsent(EdgeShift.ShiftState.TYPE);
			check(state.epochAt(0, -2) == 0
					&& level.getBlockState(new BlockPos(Maze.CENTER, standY, -2 * Maze.CELL + Maze.CENTER)).is(Blocks.COBBLESTONE),
					"edge shift ran while gamerule was disabled");
		});

		server.runCommand("gamerule noclip_backrooms:edge_shift true");
		server.waitFor(s -> {
			ServerLevel level = player(s).level();
			EdgeShift.ShiftState state = level.getDataStorage().computeIfAbsent(EdgeShift.ShiftState.TYPE);
			for (int cz = -4; cz <= -2; cz++) {
				for (int cx = -2; cx <= 2; cx++) {
					if (state.epochAt(cx, cz) > 0) return true;
				}
			}
			return false;
		}, 400);

		server.runOnServer(s -> {
			ServerLevel level = player(s).level();
			EdgeShift.ShiftState state = level.getDataStorage().computeIfAbsent(EdgeShift.ShiftState.TYPE);
			boolean verifiedShift = false;
			for (int cz = -4; cz <= -2; cz++) {
				for (int cx = -2; cx <= 2; cx++) {
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

		server.runCommand("execute as @a run backrooms edgeshift watch on");
		server.runOnServer(s -> check(EdgeShift.isWatching(player(s)), "watch mode failed to enable"));
		// Watch mode reshapes cells in plain view too (any of those in front; Level 0 being a torus, a cell's
		// neighbours across a seam can rule a particular one out).
		server.waitFor(s -> {
			EdgeShift.ShiftState watched = player(s).level().getDataStorage().computeIfAbsent(EdgeShift.ShiftState.TYPE);
			for (int cz = 1; cz <= 3; cz++) {
				for (int cx = -2; cx <= 2; cx++) {
					if (watched.epochAt(cx, cz) > 0) return true;
				}
			}
			return false;
		}, 400);
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
						if (sectorInWorld(sx, sz) && sectorInWorld(sx - 1, sz)
								&& Maze.zoneAt(seed, sx * sectorBlocks + Maze.CENTER, sz * sectorBlocks + Maze.CENTER) == Maze.Zone.RED_ROOM
								&& Maze.zoneAt(seed, (sx - 1) * sectorBlocks + Maze.CENTER, sz * sectorBlocks + Maze.CENTER) != Maze.Zone.RED_ROOM) {
							return new int[] {sx, sz};
						}
					}
				}
			}
			throw new AssertionError("no RED_ROOM sector in a centre Stage within radius 40");
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

		double originC = Maze.CENTER + 0.5;
		server.runCommand("execute as @a in noclip_backrooms:level_0 run tp @s " + originC + " " + standY + " " + originC);
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
		double originC = Maze.CENTER + 0.5;
		server.runCommand("execute as @a in noclip_backrooms:level_0 run tp @s " + originC + " " + standY + " " + originC + " 0 0");
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
		double originC = Maze.CENTER + 0.5;
		server.runCommand("execute as @a in noclip_backrooms:level_0 run tp @s " + originC + " " + standY + " " + originC + " 0 0");
		// Every player starts with a limited-battery flashlight on join (custom 5-bar battery, no vanilla durability).
		check(server.computeOnServer(s -> player(s).entityTags().contains(FlashlightItem.STARTER_TAG)),
				"starter flashlight tag not granted on join");

		server.runCommand("item replace entity @a weapon.mainhand with noclip_backrooms:flashlight");
		context.waitTicks(3);
		check(server.computeOnServer(s -> {
			ItemStack fl = player(s).getMainHandItem();
			return FlashlightItem.isFlashlight(fl) && !FlashlightItem.isInfinite(fl)
					&& FlashlightItem.bars(fl) == FlashlightItem.BARS && !fl.isDamaged();
		}), "fresh standard flashlight should have 5 full bars and no vanilla damage");

		context.getInput().pressMouse(1);
		server.waitFor(s -> {
			ServerPlayer p = player(s);
			return FlashlightItem.isLit(p.getMainHandItem()) && FlashlightLight.hasActiveBeam(p);
		}, 40);
		context.waitTicks(6);
		context.takeScreenshot("level0_flashlight_3d_on");

		// Test custom battery drain + Shift+Right-click battery_cell reload (+2 bars) + battery pack reload (full)
		server.runOnServer(s -> {
			ServerPlayer p = player(s);
			ItemStack fl = p.getMainHandItem();
			FlashlightItem.setBattery(fl, FlashlightItem.SECONDS_PER_BAR); // 1 bar (20%)
			check(FlashlightItem.bars(fl) == 1, "expected 1 bar after draining to 240s");
			p.getInventory().add(new ItemStack(dev.noclip.backrooms.items.BackroomsItems.BATTERY_CELL));
			p.setShiftKeyDown(true);
			fl.use(p.level(), p, net.minecraft.world.InteractionHand.MAIN_HAND);
			p.setShiftKeyDown(false);
			check(FlashlightItem.bars(fl) == 3, "battery_cell Shift+Right-click should add +2 bars (1 -> 3), got " + FlashlightItem.bars(fl));
		});

		context.getInput().pressMouse(1);
		server.waitFor(s -> {
			ServerPlayer p = player(s);
			return !FlashlightItem.isLit(p.getMainHandItem()) && !FlashlightLight.hasActiveBeam(p);
		}, 40);

		// Verify Level 3 infinite ambient-energy flashlight: toggles on/off, never drains, has localization
		server.runCommand("item replace entity @a weapon.mainhand with noclip_backrooms:ambient_flashlight");
		context.waitTicks(3);
		context.getInput().pressMouse(1);
		server.waitFor(s -> {
			ServerPlayer p = player(s);
			ItemStack fl = p.getMainHandItem();
			return FlashlightItem.isInfinite(fl) && FlashlightItem.isLit(fl) && FlashlightLight.hasActiveBeam(p);
		}, 40);
		context.waitTicks(6);
		context.takeScreenshot("level0_ambient_flashlight_3d_on");
		server.runOnServer(s -> {
			ItemStack fl = player(s).getMainHandItem();
			check(!FlashlightItem.drain(fl, 500) && FlashlightItem.battery(fl) == FlashlightItem.MAX_BATTERY,
					"infinite ambient flashlight should never lose battery");
		});
		context.getInput().pressMouse(1);
		server.waitFor(s -> !FlashlightItem.isLit(player(s).getMainHandItem()) && !FlashlightLight.hasActiveBeam(player(s)), 40);

		server.runCommand("item replace entity @a weapon.mainhand with minecraft:air");
	}

	private static void checkBlocks(ClientGameTestContext context, TestSingleplayerContext game) {
		game.getServer().runOnServer(s -> {
			ServerLevel level = player(s).level();
			for (Block block : ModBlocks.ALL) {
				if (block.getLootTable().isEmpty()) continue;
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
			check(Language.getInstance().has(ModItems.AMBIENT_FLASHLIGHT.getDescriptionId()), "no name for ambient_flashlight");
			check(Language.getInstance().has(Level1Items.ALMOND_WATER.getDescriptionId()), "no name for almond water");
		});
	}

	private static void checkManilaRoom(ClientGameTestContext context, TestSingleplayerContext game, int standY) {
		var server = game.getServer();
		ManilaRoomPlan.Room room = server.computeOnServer(s -> {
			ServerLevel level = s.getLevel(Level0.LEVEL_0);
			long seed = Level0ChunkGenerator.mazeSeed(level.getChunkSource().randomState());
			for (int radius = 0; radius <= 30; radius++) {
				for (int rx = -radius; rx <= radius; rx++) {
					for (int rz = -radius; rz <= radius; rz++) {
						if (Math.max(Math.abs(rx), Math.abs(rz)) != radius) continue;
						ManilaRoomPlan.Room found = ManilaRoomPlan.roomInRegion(seed, rx, rz);
						if (found != null && cellInWorld(found.cx0(), found.cz0())) return found;
					}
				}
			}
			throw new AssertionError("no Manila Room in a centre Stage within radius 30");
		});

		int bx = room.baseX(), bz = room.baseZ();
		int da = ManilaRoomPlan.DOOR_A, db = ManilaRoomPlan.DOOR_B;
		server.runCommand("gamemode survival @a");
		server.runCommand("execute as @a in noclip_backrooms:level_0 run tp @s " + (bx + 5.5) + " " + standY + " " + (bz + 5.5) + " -45 5");
		game.getConnection().waitForChunksDownload();
		context.waitTicks(10);
		game.getConnection().waitForChunksRender();
		context.takeScreenshot("level0_manila_room");

		server.runOnServer(s -> {
			ServerLevel level = player(s).level();
			check(level.getBlockState(new BlockPos(bx + 5, Level0ChunkGenerator.FLOOR_Y, bz + 5)).is(ModBlocks.MANILA_WOOD_FLOOR),
					"Manila Room floor missing");
			check(level.getBlockState(new BlockPos(bx + da, standY, bz + da)).is(ModBlocks.MANILA_TABLE),
					"Manila Room table missing");
			check(level.getBlockState(new BlockPos(bx + db, standY, bz + db)).is(ModBlocks.MANILA_CABINET),
					"Manila Room cabinet missing");
			check(level.getBlockState(new BlockPos(bx + da, standY + 1, bz + da)).is(ModBlocks.MANILA_NOTE),
					"Manila Room guide folder missing");
			check(level.getBlockState(new BlockPos(bx + da - 1, standY, bz + da)).is(ModBlocks.MANILA_CHAIR),
					"Manila Room chair missing");
			check(level.getBlockState(new BlockPos(bx + 1, standY, bz + da)).is(ModBlocks.MANILA_DOOR),
					"Manila Room door missing");
			check(level.getBlockState(new BlockPos(bx + 1, standY, bz + 3)).is(ModBlocks.VOID_STONE),
					"Manila Room hidden Void Stone missing");
			check(level.getBlockState(new BlockPos(bx + da, Level0ChunkGenerator.CEILING_Y, bz + da)).is(ModBlocks.MANILA_LIGHT),
					"Manila Room warm light missing");
			check(level.getBlockState(new BlockPos(bx + 15, standY, bz + 4)).getBlock() instanceof net.minecraft.world.level.block.BedBlock,
					"Manila Room bed head missing");
			check(level.getBlockState(new BlockPos(bx + 15, standY, bz + 5)).getBlock() instanceof net.minecraft.world.level.block.BedBlock,
					"Manila Room bed foot missing");
		});

		// Manila Room grants positive Regeneration while inside, and clears it immediately on leaving
		context.waitTicks(20);
		check(server.computeOnServer(s -> Level0MicroEvents.isInsideManilaSanctuary(player(s))
				&& player(s).hasEffect(net.minecraft.world.effect.MobEffects.REGENERATION)),
				"Manila Room did not grant Regeneration inside sanctuary");

		// Manila Room bed: first right-click prompts choice without exploding; second right-click sleeps -> Level 1 or Level 2
		BlockPos bedPos = new BlockPos(bx + 15, standY, bz + 5);
		server.runOnServer(s -> {
			ServerPlayer p = player(s);
			Level0MicroEvents.handleManilaBedInteract(p.level(), p, bedPos);
			check(p.level().dimension() == Level0.LEVEL_0, "Manila bed should prompt before sleeping");
			Level0MicroEvents.handleManilaBedInteract(p.level(), p, bedPos);
		});
		server.waitFor(s -> {
			var dim = player(s).level().dimension();
			return dim == dev.noclip.backrooms.level1.Level1.LEVEL_1 || dim == dev.noclip.backrooms.level2.Level2.LEVEL_2;
		}, 120);
		// Return to Manila interior to verify flickering wallpaper & exit-buff-removal cleanly
		server.runCommand("execute as @a in noclip_backrooms:level_0 run tp @s " + (bx + 5.5) + " " + standY + " " + (bz + 5.5) + " -45 5");
		game.getConnection().waitForChunksDownload();
		context.waitTicks(20);
		check(server.computeOnServer(s -> player(s).hasEffect(net.minecraft.world.effect.MobEffects.REGENERATION)),
				"Manila Room Regeneration did not re-apply upon re-entering");

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

		double originC = Maze.CENTER + 0.5;
		server.runCommand("execute as @a in noclip_backrooms:level_0 run tp @s " + originC + " " + standY + " " + originC);
		game.getConnection().waitForChunksDownload();
		context.waitTicks(20);
		check(server.computeOnServer(s -> !Level0MicroEvents.isInsideManilaSanctuary(player(s))
				&& !player(s).hasEffect(net.minecraft.world.effect.MobEffects.REGENERATION)),
				"Manila Room Regeneration was not removed after leaving");
	}

	private static void checkStaircaseComplexes(ClientGameTestContext context, TestSingleplayerContext game, int standY) {
		var server = game.getServer();
		EnumMap<StaircasePlan.Type, StaircasePlan.Complex> found = server.computeOnServer(s -> {
			ServerLevel level = s.getLevel(Level0.LEVEL_0);
			long seed = Level0ChunkGenerator.mazeSeed(level.getChunkSource().randomState());
			EnumMap<StaircasePlan.Type, StaircasePlan.Complex> map = new EnumMap<>(StaircasePlan.Type.class);
			for (int radius = 1; radius <= 80 && map.size() < StaircasePlan.Type.values().length; radius++) {
				for (int sx = -radius; sx <= radius; sx++) {
					for (int sz = -radius; sz <= radius; sz++) {
						if (Math.max(Math.abs(sx), Math.abs(sz)) != radius) continue;
						if (!sectorInWorld(sx, sz)) continue;
						StaircasePlan.Complex c = StaircasePlan.complexInSector(seed, sx, sz);
						if (c != null && cellInWorld(c.cx0(), c.cz0())) map.putIfAbsent(c.type(), c);
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

		StaircasePlan.Complex loft = found.get(StaircasePlan.Type.UTILITY_LOFT);
		int uf = StaircasePlan.UPPER_FLOOR_Y;
		int uc = StaircasePlan.UPPER_CEIL_Y;
		int mv0 = StaircasePlan.MID_V0;
		int standX = loft.worldX(19, mv0);
		int standZ = loft.worldZ(19, mv0);
		int leverX = loft.worldX(20, mv0);
		int leverZ = loft.worldZ(20, mv0);
		Direction toWall = loft.worldDir(Direction.EAST);
		float yaw = switch (toWall) {
			case SOUTH -> 0F;
			case WEST -> 90F;
			case NORTH -> 180F;
			default -> -90F;
		};

		server.runCommand("gamemode survival @a");
		server.runCommand("item replace entity @a hotbar.0 with minecraft:air");
		server.runCommand("execute as @a in noclip_backrooms:level_0 run tp @s " + (standX + 0.5) + " " + (uf + 1) + " " + (standZ + 0.5) + " " + yaw + " 12");
		game.getConnection().waitForChunksDownload();
		context.waitTicks(10);
		game.getConnection().waitForChunksRender();
		context.takeScreenshot("level0_staircase_loft");

		server.runOnServer(s -> {
			ServerLevel level = player(s).level();
			check(level.getBlockState(new BlockPos(loft.worldX(15, 10), uf + 2, loft.worldZ(15, 10))).is(Level1Blocks.OLD_COMPUTER),
					"Utility Loft computer missing");
			check(level.getBlockState(new BlockPos(loft.worldX(16, 10), uf + 2, loft.worldZ(16, 10))).is(ModBlocks.FIELD_NOTE),
					"Utility Loft field note missing");
			check(level.getBlockState(new BlockPos(loft.worldX(15, 19), uf + 1, loft.worldZ(15, 19))).is(ModBlocks.MANILA_CABINET),
					"Utility Loft cabinet missing");
			check(level.getBlockState(new BlockPos(loft.worldX(16, 19), uf + 1, loft.worldZ(16, 19))).is(Level1Blocks.SUPPLY_CRATE),
					"Utility Loft supply crate missing");
			BlockState lever = level.getBlockState(new BlockPos(leverX, uf + 2, leverZ));
			check(lever.is(Blocks.LEVER) && !lever.getValue(LeverBlock.POWERED), "Utility Loft lever missing or already on");
			check(level.getBlockState(new BlockPos(loft.worldX(18, 11), uc, loft.worldZ(18, 11))).is(ModBlocks.BROKEN_FLUORESCENT_LIGHT),
					"expected broken fluorescent light in Utility Loft before breaker throw");
		});

		double aimX = leverX + 0.5 + toWall.getStepX() * 0.35;
		double aimZ = leverZ + 0.5 + toWall.getStepZ() * 0.35;
		double ddx = aimX - (standX + 0.5);
		double ddz = aimZ - (standZ + 0.5);
		double ddy = (uf + 2.5) - (uf + 2.62);
		float exactYaw = (float) Math.toDegrees(Math.atan2(-ddx, ddz));
		float exactPitch = (float) Math.toDegrees(-Math.atan2(ddy, Math.hypot(ddx, ddz)));
		context.getInput().lookAt(exactYaw, exactPitch);
		context.waitTicks(3);
		context.getInput().pressMouse(1);
		server.waitFor(s -> {
			ServerLevel level = player(s).level();
			BlockState lever = level.getBlockState(new BlockPos(leverX, uf + 2, leverZ));
			return lever.is(Blocks.LEVER) && lever.getValue(LeverBlock.POWERED)
					&& level.getBlockState(new BlockPos(loft.worldX(18, 11), uc, loft.worldZ(18, 11))).is(ModBlocks.FLUORESCENT_LIGHT);
		}, 60);

		StaircasePlan.Complex basement = found.get(StaircasePlan.Type.FLOODED_BASEMENT);
		server.runCommand("execute as @a in noclip_backrooms:level_0 run tp @s "
				+ (basement.worldX(12, mv0) + 0.5) + " 25 " + (basement.worldZ(12, mv0) + 0.5) + " " + yaw + " 10");
		game.getConnection().waitForChunksDownload();
		context.waitTicks(10);
		game.getConnection().waitForChunksRender();
		context.takeScreenshot("level0_flooded_basement");

		server.runOnServer(s -> {
			ServerLevel level = player(s).level();
			check(level.getBlockState(new BlockPos(basement.worldX(15, mv0), 24, basement.worldZ(15, mv0))).is(Blocks.WATER),
					"Flooded Basement sump water missing");
			check(level.getBlockState(new BlockPos(basement.worldX(15, mv0), StaircasePlan.BASEMENT_CEIL_Y - 1, basement.worldZ(15, mv0)))
					.is(Level1Blocks.LEAKING_CEILING_PIPE), "Flooded Basement leaking pipe missing");
			check(level.getBlockState(new BlockPos(basement.worldX(21, mv0), 26, basement.worldZ(21, mv0))).is(ModBlocks.FIELD_NOTE),
					"Flooded Basement field note missing");
			check(level.getBlockState(new BlockPos(basement.worldX(8, mv0), 25, basement.worldZ(8, mv0))).is(Level1Blocks.CONCRETE_STAIRS),
					"Flooded Basement concrete stairs missing");
		});

		double originC = Maze.CENTER + 0.5;
		server.runCommand("execute as @a in noclip_backrooms:level_0 run tp @s " + originC + " " + standY + " " + originC);
		game.getConnection().waitForChunksDownload();
		context.waitTicks(5);
	}

	private static void checkZonePois(ClientGameTestContext context, TestSingleplayerContext game, int standY) {
		var server = game.getServer();
		int c = Maze.CENTER;
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
				+ (cx + c + 2.5) + " " + (cFloor + 1) + " " + (cz + c - 2.5) + " 45 10");
		game.getConnection().waitForChunksDownload();
		context.waitTicks(15);
		game.getConnection().waitForChunksRender();
		context.takeScreenshot("level0_chasm_outpost");
		server.runOnServer(s -> {
			ServerLevel level = player(s).level();
			check(level.getBlockState(new BlockPos(cx + c, cFloor, cz + c)).is(Level1Blocks.GARAGE_FLOOR),
					"Chasm outpost floor missing");
			Block expectedLoot = chasm.tier() >= 4 ? BackroomsProps.ANOMALY_VAULT_CHEST : Level1Blocks.SUPPLY_CRATE;
			check(level.getBlockState(new BlockPos(cx + c, cFloor + 1, cz + c)).is(expectedLoot),
					"Chasm outpost chest/crate missing");
			check(level.getBlockState(new BlockPos(cx + c + 1, cFloor + 1, cz + c)).is(Level1Blocks.OFFICE_DESK),
					"Chasm outpost office desk missing");
			check(level.getBlockState(new BlockPos(cx + c + 1, cFloor + 2, cz + c)).is(Level1Blocks.OLD_COMPUTER),
					"Chasm outpost computer missing");
			check(level.getBlockState(new BlockPos(cx + c, cFloor + 1, cz + c + 1)).is(BackroomsProps.WANDERER_REMAINS),
					"Chasm outpost wanderer remains missing");
			check(level.getBlockState(new BlockPos(cx + c - 1, cFloor + 1, cz + c + 1)).is(BackroomsProps.INDUSTRIAL_FUSE_BOX),
					"Chasm outpost fuse box missing");
			int lightY = chasm.ceilY() > cFloor + 8 ? cFloor + 5 : chasm.ceilY();
			check(level.getBlockState(new BlockPos(cx + c, lightY, cz + c)).is(ModBlocks.FLUORESCENT_LIGHT),
					"Chasm outpost fluorescent light missing");
		});

		// 2. MEGATHALL skylight lounge
		int mx = megahall.cx() * Maze.CELL, mz = megahall.cz() * Maze.CELL;
		int mFloor = megahall.floorY();
		server.runCommand("execute as @a in noclip_backrooms:level_0 run tp @s "
				+ (mx + c + 2.5) + " " + (mFloor + 1) + " " + (mz + c - 2.5) + " 45 6");
		game.getConnection().waitForChunksDownload();
		context.waitTicks(15);
		game.getConnection().waitForChunksRender();
		context.takeScreenshot("level0_megahall_lounge");
		server.runOnServer(s -> {
			ServerLevel level = player(s).level();
			check(level.getBlockState(new BlockPos(mx + c, mFloor, mz + c)).is(ModBlocks.MANILA_WOOD_FLOOR),
					"Megahall lounge wood inlay missing");
			check(level.getBlockState(new BlockPos(mx + c, mFloor + 1, mz + c)).is(BackroomsProps.NOCLIPPED_FOLDING_TABLE),
					"Megahall lounge table missing");
			check(level.getBlockState(new BlockPos(mx + c + 1, mFloor + 1, mz + c)).is(BackroomsProps.NOCLIPPED_CRT_TV),
					"Megahall lounge CRT TV missing");
			check(level.getBlockState(new BlockPos(mx + c, mFloor + 1, mz + c + 1)).is(BackroomsProps.NOCLIPPED_OFFICE_CHAIR),
					"Megahall lounge office chair missing");
			check(level.getBlockState(new BlockPos(mx + c - 1, mFloor + 1, mz + c + 1)).is(BackroomsProps.NOCLIPPED_WATER_COOLER),
					"Megahall lounge water cooler missing");
			check(level.getBlockState(new BlockPos(mx + c, mFloor + 6, mz + c)).is(ModBlocks.FLUORESCENT_LIGHT),
					"Megahall lounge lightwell lamp missing");
		});

		// 3. RED_ROOM anomaly shrine
		int rx = redCell[0] * Maze.CELL, rz = redCell[1] * Maze.CELL;
		server.runCommand("execute as @a in noclip_backrooms:level_0 run tp @s "
				+ (rx + 2.5) + " " + standY + " " + (rz + 2.5) + " -45 6");
		game.getConnection().waitForChunksDownload();
		context.waitTicks(15);
		game.getConnection().waitForChunksRender();
		context.takeScreenshot("level0_red_shrine");
		server.runOnServer(s -> {
			ServerLevel level = player(s).level();
			check(level.getBlockState(new BlockPos(rx + c, Level0ChunkGenerator.FLOOR_Y, rz + c)).is(ModBlocks.VOID_STONE),
					"Red Room shrine pedestal missing");
			check(level.getBlockState(new BlockPos(rx + c, standY, rz + c)).is(BackroomsProps.RED_ANOMALY_CLUSTER),
					"Red Room anomaly cluster missing");
			check(level.getBlockState(new BlockPos(rx + c + 1, standY, rz + c)).is(BackroomsProps.ANOMALY_VAULT_CHEST),
					"Red Room anomaly vault chest missing");
			check(level.getBlockState(new BlockPos(rx + c - 1, standY, rz + c + 1)).is(BackroomsProps.WANDERER_REMAINS),
					"Red Room wanderer remains missing");
			check(level.getBlockState(new BlockPos(rx + c, Level0ChunkGenerator.CEILING_Y, rz + c)).is(ModBlocks.RED_FLUORESCENT_LIGHT),
					"Red Room shrine light missing");
		});

		// 4. DARKENED static signal
		int dx = darkenedCell[0] * Maze.CELL, dz = darkenedCell[1] * Maze.CELL;
		server.runCommand("execute as @a in noclip_backrooms:level_0 run tp @s "
				+ (dx + c + 0.5) + " " + standY + " " + (dz + c + 2.5) + " 180 12");
		game.getConnection().waitForChunksDownload();
		context.waitTicks(15);
		game.getConnection().waitForChunksRender();
		context.takeScreenshot("level0_darkened_signal");
		server.runOnServer(s -> {
			ServerLevel level = player(s).level();
			check(level.getBlockState(new BlockPos(dx + c, standY, dz + c)).is(BackroomsProps.NOCLIPPED_CRT_TV),
					"Darkened signal CRT TV missing");
			check(level.getBlockState(new BlockPos(dx + c + 1, standY, dz + c)).is(BackroomsProps.WANDERER_REMAINS),
					"Darkened signal wanderer remains missing");
			check(level.getBlockState(new BlockPos(dx + c - 1, standY, dz + c + 1)).is(Level1Blocks.SUPPLY_CRATE),
					"Darkened signal supply crate missing");
		});

		// 5. Sector micro-vignettes (all 8 including GLASS_OFFICE, BREAKER_NICHE & LOCKED_UTILITY_ROOM)
		EnumMap<ZonePoiPlan.VignetteType, ZonePoiPlan.Vignette> vignettes = findAllVignettes(seed);
		ZonePoiPlan.Vignette glassOffice = vignettes.get(ZonePoiPlan.VignetteType.GLASS_OFFICE);
		int gx = glassOffice.cx() * Maze.CELL, gz = glassOffice.cz() * Maze.CELL;
		server.runCommand("execute as @a in noclip_backrooms:level_0 run tp @s "
				+ (gx + c + 2.5) + " " + standY + " " + (gz + c + 2.5) + " 135 8");
		game.getConnection().waitForChunksDownload();
		context.waitTicks(15);
		game.getConnection().waitForChunksRender();
		// These two draw only through their block entity, which the client must have been sent.
		context.runOnClient(client -> {
			for (BlockPos pos : new BlockPos[] {new BlockPos(gx + c, standY, gz + c + 1), new BlockPos(gx + c + 1, standY, gz + c)}) {
				check(client.level.getChunkAt(pos).getBlockEntities().containsKey(pos), "Generated prop is invisible at " + pos);
			}
		});

		ZonePoiPlan.Vignette breakerNiche = vignettes.get(ZonePoiPlan.VignetteType.BREAKER_NICHE);
		int bx = breakerNiche.cx() * Maze.CELL, bz = breakerNiche.cz() * Maze.CELL;
		server.runCommand("gamemode survival @a");
		server.runCommand("item replace entity @a hotbar.0 with minecraft:air");
		server.runCommand("execute as @a in noclip_backrooms:level_0 run tp @s "
				+ (bx + c + 0.5) + " " + standY + " " + (bz + c + 2.5) + " 180 12");
		game.getConnection().waitForChunksDownload();
		context.waitTicks(15);
		game.getConnection().waitForChunksRender();
		context.takeScreenshot("level0_breaker_niche");
		server.runOnServer(s -> {
			ServerLevel level = player(s).level();
			check(level.getBlockState(new BlockPos(bx + c, standY, bz + c)).is(BackroomsProps.INDUSTRIAL_FUSE_BOX),
					"Breaker niche fuse box missing");
			check(level.getBlockState(new BlockPos(bx + c, Level0ChunkGenerator.CEILING_Y, bz + c)).is(ModBlocks.BROKEN_FLUORESCENT_LIGHT),
					"Breaker niche overhead lamp should start broken");
			check(FuseBoxEvents.trigger(level, player(s), new BlockPos(bx + c, standY, bz + c), true),
					"Breaker niche fuse box failed to restore");
			check(level.getBlockState(new BlockPos(bx + c, Level0ChunkGenerator.CEILING_Y, bz + c)).is(ModBlocks.FLUORESCENT_LIGHT),
					"Breaker niche overhead lamp did not turn back on");
			check(FuseBoxEvents.isOnCooldown(level, new BlockPos(bx + c, standY, bz + c)),
					"Breaker niche fuse box did not enter cooldown");
		});

		// Locked Utility Room mini-puzzle: fuse box outside unlocks shutter door!
		ZonePoiPlan.Vignette lockedRoom = vignettes.get(ZonePoiPlan.VignetteType.LOCKED_UTILITY_ROOM);
		int lx = lockedRoom.cx() * Maze.CELL, lz = lockedRoom.cz() * Maze.CELL;
		server.runCommand("execute as @a in noclip_backrooms:level_0 run tp @s "
				+ (lx + c + 0.5) + " " + standY + " " + (lz + c + 2.5) + " 180 8");
		game.getConnection().waitForChunksDownload();
		context.waitTicks(15);
		game.getConnection().waitForChunksRender();
		context.takeScreenshot("level0_locked_utility_room");
		server.runOnServer(s -> {
			ServerLevel level = player(s).level();
			BlockPos doorPos = new BlockPos(lx + 5, standY, lz + 5);
			BlockPos fusePos = new BlockPos(lx + 7, standY, lz + 6);
			check(level.getBlockState(doorPos).is(ModBlocks.LOCKED_UTILITY_DOOR)
					&& !level.getBlockState(doorPos).getValue(Level0EventBlocks.LockedUtilityDoorBlock.OPEN),
					"Locked utility door missing or already open");
			check(FuseBoxEvents.trigger(level, player(s), fusePos, true), "Utility room fuse box failed");
			check(level.getBlockState(doorPos).getValue(Level0EventBlocks.LockedUtilityDoorBlock.OPEN),
					"Locked utility door did not open after fuse box restore");
		});

		// 6. Mystery exits (HUB_DOOR, FUN_DOOR)
		ZonePoiPlan.MysteryExit hubExit = findMysteryExit(seed, ZonePoiPlan.MysteryKind.HUB);
		int hx = hubExit.cx() * Maze.CELL, hz = hubExit.cz() * Maze.CELL;
		server.runCommand("gamemode survival @a");
		server.runCommand("item replace entity @a hotbar.0 with minecraft:air");
		server.runCommand("execute as @a in noclip_backrooms:level_0 run tp @s "
				+ (hx + c + 0.5) + " " + standY + " " + (hz + c + 1.8) + " 180 6");
		game.getConnection().waitForChunksDownload();
		context.waitTicks(15);
		game.getConnection().waitForChunksRender();
		context.takeScreenshot("level0_hub_door");
		int refusalsBefore = LevelExits.refusals();
		server.runOnServer(s -> {
			ServerLevel level = player(s).level();
			BlockState door = level.getBlockState(new BlockPos(hx + c, standY, hz + c));
			check(door.is(ModBlocks.HUB_DOOR) && door.getValue(MysteryExitBlock.AXIS) == Direction.Axis.Z,
					"Hub door missing or wrong axis");
		});
		context.getInput().lookAt(180, 5);
		context.waitTicks(3);
		context.getInput().pressMouse(1);
		server.waitFor(s -> LevelExits.refusals() > refusalsBefore, 40);

		double originC = Maze.CENTER + 0.5;
		server.runCommand("execute as @a in noclip_backrooms:level_0 run tp @s " + originC + " " + standY + " " + originC);
		game.getConnection().waitForChunksDownload();
		context.waitTicks(5);
	}

	/** Verifies 2026-11-26 Level 0 Overhaul: 1980 Appalachia poster exit, Pitfalls undercroft + ladder, Domino blackout, and Plan C stage loop. */
	private static void checkOverhaulFeatures(ClientGameTestContext context, TestSingleplayerContext game, int standY) {
		var server = game.getServer();
		long seed = server.computeOnServer(s -> Level0ChunkGenerator.mazeSeed(
				s.getLevel(Level0.LEVEL_0).getChunkSource().randomState()));
		int c = Maze.CENTER;

		// 1. 1980 Appalachia Poster Exit (tear / left-click / double right-click triggers LevelExits -> "161")
		AnomalyExitPlan.Placement poster = AnomalyExitPlan.placementsInStage(seed, 0, 0).stream()
				.filter(p -> p.kind() == AnomalyExitPlan.EntryKind.POSTER_161)
				.findFirst().orElseThrow(() -> new AssertionError("Stage (0,0) missing Level 161 poster"));
		int ax = poster.cx() * Maze.CELL, az = poster.cz() * Maze.CELL;
		server.runCommand("gamemode survival @a");
		server.runCommand("execute as @a in noclip_backrooms:level_0 run tp @s "
				+ (ax + c + 0.5) + " " + standY + " " + (az + c + 2.2) + " 180 0");
		game.getConnection().waitForChunksDownload();
		context.waitTicks(45); // exceed LevelExits.REFUSE_INTERVAL (40 ticks) after HUB_DOOR refusal
		game.getConnection().waitForChunksRender();
		context.takeScreenshot("level0_appalachia_poster");
		int refBeforePoster = LevelExits.refusals();
		server.runOnServer(s -> {
			ServerLevel level = player(s).level();
			BlockPos pos = new BlockPos(ax + c, standY + 1, az + c);
			BlockState st = level.getBlockState(pos);
			check(st.is(ModBlocks.ANOMALY_POSTER), "1980 Appalachia poster block missing at " + pos);
			AnomalyExitBlock block = (AnomalyExitBlock) st.getBlock();
			check(block.triggerTearOrSmash(level, player(s), pos, st), "Tearing 1980 poster did not trigger exit");
		});
		check(LevelExits.refusals() > refBeforePoster, "1980 poster did not route to LevelExits(161)");

		// 2. Steam Grate (Level 2 Pipe Dreams) & Pitfalls lower undercroft screenshot
		int[] pitSector = server.computeOnServer(s -> {
			for (int sx = 0; sx < Maze.STAGE_SECTORS; sx++) {
				for (int sz = 0; sz < Maze.STAGE_SECTORS; sz++) {
					if (Maze.baseZoneForSector(seed, sx, sz) == Maze.Zone.PITS) return new int[] {sx, sz};
				}
			}
			throw new AssertionError("No PITS sector in Stage (0,0)");
		});
		int shaftX = (pitSector[0] * Maze.SECTOR + PitfallsPlan.SHAFT_LX) * Maze.CELL + 5;
		int shaftZ = (pitSector[1] * Maze.SECTOR + PitfallsPlan.SHAFT_LZ) * Maze.CELL + 5;
		server.runCommand("gamemode spectator @a");
		server.runCommand("execute as @a in noclip_backrooms:level_0 run tp @s "
				+ (shaftX + 2.5) + " " + (PitfallsPlan.LOWER_FLOOR_Y + 2) + " " + (shaftZ + 2.5) + " 135 5");
		game.getConnection().waitForChunksDownload();
		context.waitTicks(15);
		game.getConnection().waitForChunksRender();
		context.takeScreenshot("level0_pitfalls_undercroft");
		server.runOnServer(s -> {
			ServerLevel level = player(s).level();
			check(level.getBlockState(new BlockPos(shaftX - 1, PitfallsPlan.LOWER_FLOOR_Y + 1, shaftZ - 1)).is(Blocks.LADDER),
					"Pitfalls undercroft escape ladder missing");
		});

		// 3. Domino blackout command check + Plan C Stage Loop wrap verification
		double originC = Maze.CENTER + 0.5;
		server.runCommand("gamemode survival @a");
		server.runCommand("execute as @a in noclip_backrooms:level_0 run tp @s " + originC + " " + standY + " " + originC + " 0 0");
		game.getConnection().waitForChunksDownload();
		context.waitTicks(10);
		server.runCommand("execute as @a run backrooms l0event domino");
		server.runCommand("execute as @a run backrooms stage info");
		context.waitTicks(10);

		// Level 0 is a torus (Level0Space): past the west edge of Stage (0,0) lies the same Stage again, block for block...
		String seam = server.computeOnServer(s -> {
			ServerLevel level = s.getLevel(Level0.LEVEL_0);
			// (Away from the origin, where the blackout just above turned lamps off on both sides of the edge.)
			for (int z = 60; z < Maze.STAGE_BLOCKS; z += 7) {
				for (int x = -24; x < 0; x += 3) {
					for (int y = standY - 1; y <= standY + 4; y++) {
						BlockState copy = level.getBlockState(new BlockPos(x, y, z));
						BlockState original = level.getBlockState(new BlockPos(x + Level0Space.PERIOD, y, z));
						if (!copy.equals(original)) return "(" + x + "," + y + "," + z + ") " + copy + " vs " + original;
					}
				}
			}
			return null;
		});
		check(seam == null, "the copy past Stage (0,0)'s west edge differs from the Stage at " + seam);
		// ...and crossing it, in every game mode, moves the player exactly one Stage, facing kept.
		server.runCommand("gamerule noclip_backrooms:stage_loop true");
		int freeZ = server.computeOnServer(s -> {
			long mazeSeed = Level0ChunkGenerator.mazeSeed(s.getLevel(Level0.LEVEL_0).getChunkSource().randomState());
			for (int z = 60; z < Maze.STAGE_BLOCKS - 5; z++) {
				boolean open = true;
				for (int x = -3; x <= 3 && open; x++) {
					open = Level0ChunkGenerator.worldBlockAt(mazeSeed, x, standY, z).isAir()
							&& Level0ChunkGenerator.worldBlockAt(mazeSeed, x, standY + 1, z).isAir()
							&& !Level0ChunkGenerator.worldBlockAt(mazeSeed, x, standY - 1, z).isAir();
				}
				if (open) return z;
			}
			throw new AssertionError("no open way across Stage (0,0)'s west edge");
		});
		for (String mode : new String[] {"spectator", "creative", "survival"}) {
			server.runCommand("gamemode " + mode + " @a");
			server.runCommand("execute as @a in noclip_backrooms:level_0 run tp @s 2.5 " + standY + " " + (freeZ + 0.5) + " 90 0");
			context.waitTicks(5);
			server.runCommand("execute as @a in noclip_backrooms:level_0 run tp @s -1.5 " + standY + " " + (freeZ + 0.5) + " 90 0");
			server.waitFor(s -> player(s).getX() > 0, 40);
			double[] after = server.computeOnServer(s -> new double[] {player(s).getX(), player(s).getZ(), player(s).getYRot()});
			check(Math.abs(after[0] - (Level0Space.PERIOD - 1.5)) < 0.01 && Math.abs(after[1] - (freeZ + 0.5)) < 0.01,
					mode + " crossing the west edge landed at " + after[0] + ", " + after[1] + " (want " + (Level0Space.PERIOD - 1.5) + ")");
			check(Math.abs(after[2] - 90) < 0.01, mode + " crossing the edge turned the player to " + after[2]);
		}
		// The copies are kept the same as the Stage (Level0Mirror): a block set in the Stage is in the copy past the
		// edge at once, and in a copy loaded only later...
		server.runCommand("gamemode creative @a");
		server.runCommand("execute as @a in noclip_backrooms:level_0 run tp @s 2.5 " + standY + " " + (freeZ + 0.5) + " 90 10");
		context.waitTicks(5);
		server.runOnServer(s -> s.getLevel(Level0.LEVEL_0).setBlockAndUpdate(new BlockPos(6, standY + 3, freeZ), Blocks.GOLD_BLOCK.defaultBlockState()));
		server.runOnServer(s -> {
			ServerLevel level = s.getLevel(Level0.LEVEL_0);
			check(level.getBlockState(new BlockPos(6 - Level0Space.PERIOD, standY + 3, freeZ)).is(Blocks.GOLD_BLOCK),
					"a block set in the Stage is not in the copy past its west edge");
			check(level.getBlockState(new BlockPos(6 + 2 * Level0Space.PERIOD, standY + 3, freeZ)).is(Blocks.GOLD_BLOCK),
					"a copy loaded later did not get the block set in the Stage");
		});
		// ...a thing over the edge comes back like a player...
		server.runCommand("execute in noclip_backrooms:level_0 run summon minecraft:armor_stand 297.5 " + standY + " " + (freeZ + 0.5) + " {NoGravity:1b}");
		server.runCommand("execute in noclip_backrooms:level_0 run summon minecraft:armor_stand -3.5 " + standY + " " + (freeZ + 0.5) + " {NoGravity:1b,Invisible:1b}");
		server.waitFor(s -> {
			for (var entity : s.getLevel(Level0.LEVEL_0).getAllEntities()) {
				if (entity.getType() == net.minecraft.world.entity.EntityTypes.ARMOR_STAND && entity.getX() < 0) return false;
			}
			return true;
		}, 40);
		// ...and what stands in the Stage is seen again past the edge (a ghost of the stand by the east edge, just west of here).
		context.waitTicks(10);
		game.getConnection().waitForChunksRender();
		check(context.computeOnClient(client -> Level0Ghosts.count()) > 0, "no ghosts past the edge");
		context.takeScreenshot("level0_seam_ghost");
		server.runCommand("kill @e[type=minecraft:armor_stand]");
		server.runCommand("gamemode survival @a");
		server.runCommand("gamerule noclip_backrooms:stage_loop false");

		server.runCommand("execute as @a in noclip_backrooms:level_0 run tp @s " + originC + " " + standY + " " + originC);
		game.getConnection().waitForChunksDownload();
		context.waitTicks(5);
	}

	/** Level 0 repeats the centre Stage of each region (Level0Space): a planned feature is in the world only there. */
	private static boolean sectorInWorld(int sx, int sz) {
		return Level0Space.originalCell(sx * Maze.SECTOR + Maze.SECTOR / 2, Maze.SECTOR / 2)
				&& Level0Space.originalCell(sz * Maze.SECTOR + Maze.SECTOR / 2, Maze.SECTOR / 2);
	}

	/** A feature at a cell, with a few cells around it, lies in a centre Stage. */
	private static boolean cellInWorld(int cx, int cz) {
		return Level0Space.originalCell(cx, 2) && Level0Space.originalCell(cz, 2);
	}

	private static EnumMap<ZonePoiPlan.VignetteType, ZonePoiPlan.Vignette> findAllVignettes(long seed) {
		EnumMap<ZonePoiPlan.VignetteType, ZonePoiPlan.Vignette> map = new EnumMap<>(ZonePoiPlan.VignetteType.class);
		for (int radius = 1; radius <= 80 && map.size() < ZonePoiPlan.VignetteType.values().length; radius++) {
			for (int sx = -radius; sx <= radius; sx++) {
				for (int sz = -radius; sz <= radius; sz++) {
					if (Math.max(Math.abs(sx), Math.abs(sz)) != radius) continue;
					if (!sectorInWorld(sx, sz)) continue;
					ZonePoiPlan.Vignette v = ZonePoiPlan.vignetteInSector(seed, sx, sz);
					if (v != null && cellInWorld(v.cx(), v.cz())) map.putIfAbsent(v.type(), v);
				}
			}
		}
		for (ZonePoiPlan.VignetteType t : ZonePoiPlan.VignetteType.values()) {
			check(map.containsKey(t), "missing vignette type " + t);
		}
		return map;
	}

	private static ZonePoiPlan.MysteryExit findMysteryExit(long seed, ZonePoiPlan.MysteryKind kind) {
		for (int radius = 0; radius <= 40; radius++) {
			for (int rx = -radius; rx <= radius; rx++) {
				for (int rz = -radius; rz <= radius; rz++) {
					if (Math.max(Math.abs(rx), Math.abs(rz)) != radius) continue;
					ZonePoiPlan.MysteryExit exit = ZonePoiPlan.mysteryExitInRegion(seed, rx, rz);
					if (exit != null && exit.kind() == kind && cellInWorld(exit.cx(), exit.cz())) return exit;
				}
			}
		}
		throw new AssertionError("no mystery exit " + kind + " in a centre Stage within radius 40 regions");
	}

	private static ZonePoiPlan.ChasmOutpost findChasmOutpost(long seed) {
		for (int radius = 0; radius <= 60; radius++) {
			for (int mx = -radius; mx <= radius; mx++) {
				for (int mz = -radius; mz <= radius; mz++) {
					if (Math.max(Math.abs(mx), Math.abs(mz)) != radius) continue;
					int baseCx = mx * ChasmPlan.MACRO_CELLS;
					int baseCz = mz * ChasmPlan.MACRO_CELLS;
					if (Maze.zoneAt(seed, baseCx * Maze.CELL + Maze.CENTER, baseCz * Maze.CELL + Maze.CENTER) != Maze.Zone.CHASM) {
						continue;
					}
					ZonePoiPlan.ChasmOutpost found = ZonePoiPlan.chasmOutpostInMacro(seed, baseCx, baseCz);
					if (found != null && cellInWorld(found.cx(), found.cz())) return found;
				}
			}
		}
		throw new AssertionError("no CHASM outpost in a centre Stage within macro radius 60");
	}

	private static ZonePoiPlan.MegahallLounge findMegahallLounge(long seed) {
		for (int radius = 0; radius <= 60; radius++) {
			for (int mx = -radius; mx <= radius; mx++) {
				for (int mz = -radius; mz <= radius; mz++) {
					if (Math.max(Math.abs(mx), Math.abs(mz)) != radius) continue;
					int baseCx = mx * MegahallPlan.MACRO_CELLS;
					int baseCz = mz * MegahallPlan.MACRO_CELLS;
					if (Maze.zoneAt(seed, baseCx * Maze.CELL + Maze.CENTER, baseCz * Maze.CELL + Maze.CENTER) != Maze.Zone.MEGATHALL) {
						continue;
					}
					ZonePoiPlan.MegahallLounge found = ZonePoiPlan.megahallLoungeInMacro(seed, baseCx, baseCz);
					if (found != null && cellInWorld(found.cx(), found.cz())) return found;
				}
			}
		}
		throw new AssertionError("no MEGATHALL lounge in a centre Stage within macro radius 60");
	}

	private static int[] findLargeZonePoiCell(long seed, Maze.Zone target) {
		for (int radius = 1; radius <= 40; radius++) {
			for (int sx = -radius; sx <= radius; sx++) {
				for (int sz = -radius; sz <= radius; sz++) {
					if (Math.max(Math.abs(sx), Math.abs(sz)) != radius) continue;
					if (sx == 0 && sz == 0) continue;
					if (sectorInWorld(sx, sz) && Maze.baseZoneForSector(seed, sx, sz) == target) {
						return new int[] {sx * Maze.SECTOR + 3, sz * Maze.SECTOR + 3};
					}
				}
			}
		}
		throw new AssertionError("no " + target + " sector in a centre Stage within radius 40");
	}

	private static void verify3DReachabilityAndNoExposedBedrock(long seed, StaircasePlan.Complex c) {
		int bx = c.baseX(), bz = c.baseZ();
		int minY = 23, maxY = StaircasePlan.UPPER_CEIL_Y + 1;
		int mv0 = StaircasePlan.MID_V0;

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

		Set<Long> visited = new HashSet<>();
		ArrayDeque<BlockPos> queue = new ArrayDeque<>();
		BlockPos start = new BlockPos(c.worldX(1, mv0), Level0ChunkGenerator.FLOOR_Y, c.worldZ(1, mv0));
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
				check(visited.contains(new BlockPos(c.worldX(15, mv0), StaircasePlan.UPPER_FLOOR_Y, c.worldZ(15, mv0)).asLong()),
						"ATTIC_SHORTCUT center unreachable");
				check(visited.contains(new BlockPos(c.worldX(29, mv0), Level0ChunkGenerator.FLOOR_Y, c.worldZ(29, mv0)).asLong()),
						"ATTIC_SHORTCUT exit ground unreachable");
			}
			case PARADOX_LOOP -> {
				check(visited.contains(new BlockPos(c.worldX(15, 12), StaircasePlan.UPPER_FLOOR_Y, c.worldZ(15, 12)).asLong()),
						"PARADOX_LOOP sealed-door alcove unreachable");
				check(visited.contains(new BlockPos(c.worldX(29, mv0), Level0ChunkGenerator.FLOOR_Y, c.worldZ(29, mv0)).asLong()),
						"PARADOX_LOOP exit ground unreachable");
			}
			case UTILITY_LOFT -> check(visited.contains(new BlockPos(c.worldX(19, mv0), StaircasePlan.UPPER_FLOOR_Y, c.worldZ(19, mv0)).asLong()),
					"UTILITY_LOFT breaker area unreachable");
			case OBSERVATION_OVERLOOK -> check(visited.contains(new BlockPos(c.worldX(15, mv0), StaircasePlan.UPPER_FLOOR_Y, c.worldZ(15, mv0)).asLong()),
					"OBSERVATION_OVERLOOK glass deck unreachable");
			case FLOODED_BASEMENT -> {
				check(visited.contains(new BlockPos(c.worldX(19, mv0), StaircasePlan.BASEMENT_FLOOR_Y, c.worldZ(19, mv0)).asLong()),
						"FLOODED_BASEMENT dry platform unreachable");
				check(visited.contains(new BlockPos(c.worldX(15, mv0), StaircasePlan.BASEMENT_FLOOR_Y - 1, c.worldZ(15, mv0)).asLong()),
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
					var expected = Level0ChunkGenerator.worldBlockAt(seed, x, y, z); // Level 0 repeats its centre Stage (Level0Space)
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
