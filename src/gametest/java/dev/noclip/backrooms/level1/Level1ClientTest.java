package dev.noclip.backrooms.level1;

import dev.noclip.backrooms.level1.Level1Layout.Kind;
import dev.noclip.backrooms.level1.Level1Layout.Tile;
import java.util.Locale;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.locale.Language;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Plays Level 1 in a real client: through a flickering wall, around the halls and corridors, a blackout, a crate, and out. */
public final class Level1ClientTest implements FabricClientGameTest {
	private static final int STAND = Level1ChunkGenerator.FLOOR_Y + 1;

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext game = context.worldBuilder().create()) {
			var server = game.getServer();
			game.getConnection().waitForChunksRender();
			server.runCommand("gamerule send_command_feedback false");
			server.runCommand("gamemode survival @a");
			server.runCommand("clear @a");

			enterThroughFlickeringWall(context, game);

			long seed = server.computeOnServer(s -> Level1ChunkGenerator.planSeed(level1(s).getChunkSource().randomState()));
			server.runOnServer(s -> Blackouts.holdOff(20 * 60 * 10));
			server.runCommand("gamemode spectator @a");
			BlockPos arrival = server.computeOnServer(s -> player(s).blockPosition());
			for (int yaw = 0; yaw < 360; yaw += 90) {
				shoot(context, game, arrival.getX() + 0.5, STAND, arrival.getZ() + 0.5, yaw, 5, "level1_arrival_yaw_" + yaw);
			}
			shoot(context, game, arrival.getX() + 0.5, STAND, arrival.getZ() + 0.5, 90, -50, "level1_ceiling");

			blackout(context, game);
			crate(context, game);
			views(context, game, seed);
			checkBlocks(context, game);
			leaveThroughDoor(context, game, seed);
		}
	}

	/** Spends a moment in Level 0, makes a wall flicker, looks at it, and walks into it. */
	private static void enterThroughFlickeringWall(ClientGameTestContext context, TestSingleplayerContext game) {
		var server = game.getServer();
		server.runCommand("execute as @a in noclip_backrooms:level_0 run tp @s 3.5 33 3.5 0 0");
		game.getConnection().waitForChunksDownload();
		context.waitTicks(40);
		game.getConnection().waitForChunksRender();

		BlockPos wall = server.computeOnServer(s -> FlickeringWalls.open(player(s)));
		check(wall != null, "no flickering wall opened");
		Direction open = server.computeOnServer(s -> {
			ServerLevel level = player(s).level();
			check(level.getBlockState(wall).is(Level1Blocks.FLICKERING_WALLPAPER), "wall did not change");
			check(level.getBlockState(wall.above()).is(Level1Blocks.FLICKERING_WALLPAPER), "upper wall did not change");
			for (Direction side : Direction.Plane.HORIZONTAL) {
				if (level.getBlockState(wall.relative(side)).isAir() && level.getBlockState(wall.relative(side).above()).isAir()) return side;
			}
			return null;
		});
		check(open != null, "flickering wall faces no open floor");

		// Stand two blocks out, facing it.
		BlockPos front = wall.relative(open, 2);
		float yaw = open.getOpposite().toYRot();
		server.runCommand(String.format(Locale.ROOT, "execute as @a in noclip_backrooms:level_0 run tp @s %.1f %d %.1f %.1f 0",
				front.getX() + 0.5, front.getY(), front.getZ() + 0.5, yaw));
		context.waitTicks(30);
		game.getConnection().waitForChunksRender();
		context.runOnClient(client -> client.gui.toastManager().clear());
		context.takeScreenshot("level1_flickering_wall");
		check(server.computeOnServer(s -> player(s).level().dimension() == Level1.LEVEL_0), "left Level 0 too early");

		context.getInput().holdKeyFor(options -> options.keyUp, 40);
		server.waitFor(s -> player(s).level().dimension() == Level1.LEVEL_1, 200);
		context.waitTicks(40);
		game.getConnection().waitForChunksRender();
		server.runOnServer(s -> {
			ServerPlayer player = player(s);
			check(Math.abs(player.getY() - STAND) < 0.01, "landed at y=" + player.getY());
			check(!player.isInWall(), "landed inside a wall");
			check(player.getHealth() == player.getMaxHealth(), "hurt on the way in");
			long seed = Level1ChunkGenerator.planSeed(player.level().getChunkSource().randomState());
			check(Level1Layout.kindAt(seed, player.getBlockX(), player.getBlockZ()) == Kind.PARKING, "did not land in a parking hall");
			BlockState left = s.getLevel(Level1.LEVEL_0).getBlockState(wall);
			check(!left.is(Level1Blocks.FLICKERING_WALLPAPER), "flickering wall stayed open after use");
			checkGenerated(player.level(), seed, player.blockPosition());
			for (var entity : player.level().getAllEntities()) check(!(entity instanceof Mob), "a mob spawned");
		});
	}

	private static void blackout(ClientGameTestContext context, TestSingleplayerContext game) {
		var server = game.getServer();
		server.runOnServer(s -> check(litNear(s) > 0, "no working lights near the player"));
		server.runOnServer(s -> Blackouts.startNow());
		server.waitFor(s -> Blackouts.phase() == Blackouts.Phase.DARK, 200);
		context.waitTicks(15);
		game.getConnection().waitForChunksRender();
		server.runOnServer(s -> check(litNear(s) == 0, "lights still on in a blackout"));
		context.takeScreenshot("level1_blackout");
		server.runOnServer(s -> Blackouts.endNow());
		server.waitFor(s -> Blackouts.phase() == Blackouts.Phase.NORMAL, 100);
		context.waitTicks(15);
		server.runOnServer(s -> {
			check(litNear(s) > 0, "lights did not come back");
			Blackouts.holdOff(20 * 60 * 10);
		});
	}

	/** A crate turns up behind the player; looking at it and right-clicking spills its contents. */
	private static void crate(ClientGameTestContext context, TestSingleplayerContext game) {
		var server = game.getServer();
		server.runCommand("gamemode survival @a");
		context.waitTicks(10);
		BlockPos crate = server.computeOnServer(s -> Crates.spawnBehind(player(s)));
		check(crate != null, "no crate appeared");
		server.runOnServer(s -> {
			ServerPlayer player = player(s);
			Vec3 toCrate = Vec3.atCenterOf(crate).subtract(player.position());
			check(toCrate.horizontalDistance() >= 4, "crate appeared too close");
			check(player.getLookAngle().dot(toCrate.normalize()) < 0, "crate appeared in front of the player");
		});

		// Step up beside it and look down at it.
		Direction side = server.computeOnServer(s -> {
			ServerLevel level = level1(s);
			for (Direction direction : Direction.Plane.HORIZONTAL) {
				BlockPos next = crate.relative(direction);
				if (level.getBlockState(next).isAir() && level.getBlockState(next.above()).isAir()
						&& !level.getBlockState(next.below()).getCollisionShape(level, next.below()).isEmpty()) return direction;
			}
			return null;
		});
		check(side != null, "nowhere to stand by the crate");
		BlockPos stand = crate.relative(side);
		float yaw = side.getOpposite().toYRot();
		server.runCommand(String.format(Locale.ROOT, "execute as @a in noclip_backrooms:level_1 run tp @s %.1f %d %.1f %.1f 48",
				stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5, yaw));
		context.waitTicks(10);
		context.getInput().lookAt(yaw, 48);
		context.waitTicks(5);
		context.takeScreenshot("level1_crate");
		context.getInput().pressMouse(1);
		context.waitTicks(10);
		server.runOnServer(s -> {
			ServerLevel level = level1(s);
			check(!level.getBlockState(crate).is(Level1Blocks.SUPPLY_CRATE), "crate did not open");
			// Whatever spilled lands at the player's feet and may already be picked up.
			boolean spilled = !level.getEntitiesOfClass(ItemEntity.class, new AABB(crate).inflate(3)).isEmpty();
			check(spilled || !player(s).getInventory().isEmpty(), "crate was empty");
		});
		server.runCommand("gamemode spectator @a");
	}

	/** Pictures of each part of the level, found from the floor plan. */
	private static void views(ClientGameTestContext context, TestSingleplayerContext game, long seed) {
		int[] warehouse = nearestDistrict(seed, Kind.WAREHOUSE);
		int[] aisle = Level1Layout.aisleSpot(warehouse[0], warehouse[1], warehouse[0] * 64 + 32, warehouse[1] * 64 + 32);
		shoot(context, game, aisle[0] + 0.5, STAND, aisle[1] + 0.5, 90, 5, "level1_warehouse");

		// A green sign over a doorway, seen from the hall.
		BlockPos sign = findNear(seed, Tile.OPENING_SIGN);
		Direction away = Math.floorMod(sign.getX(), 64) == 0 ? Direction.WEST : Direction.NORTH;
		if (Level1Layout.kindAt(seed, sign.getX() + away.getStepX(), sign.getZ() + away.getStepZ()) == Kind.CORRIDORS) away = away.getOpposite();
		lookBack(context, game, seed, sign, away, "level1_corridor_entrance", -8);

		// Inside the corridors, looking down a passage.
		int[] corridors = nearestDistrict(seed, Kind.CORRIDORS);
		BlockPos inside = find(seed, corridors[0] * 64 + 22, corridors[1] * 64 + 22, Tile.CORRIDOR);
		Direction along = longestView(seed, inside);
		shoot(context, game, inside.getX() + 0.5, STAND, inside.getZ() + 0.5, along.toYRot(), 5, "level1_corridor");

		BlockPos desk = findNear(seed, Tile.DESK);
		shoot(context, game, desk.getX() + 0.5, STAND, desk.getZ() + 2.5, 180, 25, "level1_office");
		BlockPos brick = findNear(seed, Tile.BRICK);
		for (Direction side : Direction.Plane.HORIZONTAL) {
			if (Level1Layout.tile(seed, brick.getX() + side.getStepX(), brick.getZ() + side.getStepZ()).walkable()) {
				lookBack(context, game, seed, brick, side, "level1_brick", 5);
				break;
			}
		}
	}

	/** A picture of pos, backing away from it in a straight line through open floor, up to seven blocks. */
	private static void lookBack(ClientGameTestContext context, TestSingleplayerContext game, long seed, BlockPos pos, Direction away, String name, float pitch) {
		int distance = 0;
		while (distance < 7 && Level1Layout.tile(seed, pos.getX() + away.getStepX() * (distance + 1), pos.getZ() + away.getStepZ() * (distance + 1)).walkable()) {
			distance++;
		}
		check(distance > 0, "no place to look at " + pos + " from");
		shoot(context, game, pos.getX() + 0.5 + away.getStepX() * distance, STAND, pos.getZ() + 0.5 + away.getStepZ() * distance,
				away.getOpposite().toYRot(), pitch, name);
	}

	/** The direction with the longest straight run of open floor from pos. */
	private static Direction longestView(long seed, BlockPos pos) {
		Direction best = Direction.NORTH;
		int bestRun = -1;
		for (Direction side : Direction.Plane.HORIZONTAL) {
			int run = 0;
			while (run < 16 && Level1Layout.tile(seed, pos.getX() + side.getStepX() * (run + 1), pos.getZ() + side.getStepZ() * (run + 1)).walkable()) {
				run++;
			}
			if (run > bestRun) {
				bestRun = run;
				best = side;
			}
		}
		return best;
	}

	/** The nearest block of a tile type, searching district by district out from the origin. */
	private static BlockPos findNear(long seed, Tile tile) {
		for (int ring = 0; ring < 8; ring++) {
			for (int dx = -ring; dx <= ring; dx++) {
				for (int dz = -ring; dz <= ring; dz++) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) continue;
					BlockPos found = find(seed, dx * 64, dz * 64, tile);
					if (found != null) return found;
				}
			}
		}
		throw new AssertionError("no " + tile + " near the origin");
	}

	private static void checkBlocks(ClientGameTestContext context, TestSingleplayerContext game) {
		game.getServer().runOnServer(s -> {
			ServerLevel level = level1(s);
			for (Block block : Level1Blocks.ALL) {
				if (block.getLootTable().isEmpty() || block == Level1Blocks.SUPPLY_CRATE) continue;
				var drops = Block.getDrops(block.defaultBlockState(), level, new BlockPos(3, 3, 3), null);
				check(drops.size() == 1 && drops.getFirst().is(block.asItem()), "wrong drop for " + block);
			}
			check(Level1Blocks.METAL_EXIT_DOOR.defaultBlockState().getDestroySpeed(level, BlockPos.ZERO) < 0, "exit can be broken");
		});
		context.runOnClient(client -> {
			for (Block block : Level1Blocks.ALL) {
				check(Language.getInstance().has(block.getDescriptionId()), "no name for " + block);
			}
		});
	}

	/** The nearest exit door, from both of its sides, then out with a right-click. */
	private static void leaveThroughDoor(ClientGameTestContext context, TestSingleplayerContext game, long seed) {
		var server = game.getServer();
		int[] district = nearestDistrict(seed, Kind.CORRIDORS);
		Level1Layout.Door door = Level1Layout.door(seed, district[0], district[1]);
		shoot(context, game, door.x() + 3.5, STAND, door.z() + 0.5, 90, 0, "level1_exit_door");
		server.runOnServer(s -> {
			ServerLevel level = level1(s);
			BlockPos bottom = new BlockPos(door.x(), STAND, door.z());
			check(level.getBlockState(bottom).is(Level1Blocks.METAL_EXIT_DOOR), "no door at " + door);
			check(level.getBlockState(bottom.above()).is(Level1Blocks.METAL_EXIT_DOOR_TOP), "no door top at " + door);
			check(BuiltInRegistries.BLOCK.getKey(level.getBlockState(bottom.above(2)).getBlock()).getPath().equals("exit_sign"), "no sign over " + door);
		});

		server.runCommand(String.format(Locale.ROOT, "execute as @a in noclip_backrooms:level_1 run tp @s %.1f %d %.1f 90 0",
				door.x() + 1.5, STAND, door.z() + 0.5));
		server.runCommand("gamemode survival @a");
		server.runCommand("item replace entity @a hotbar.0 with minecraft:cobblestone 5");
		context.waitTicks(10);
		context.getInput().lookAt(90, 20);
		context.waitTicks(3);
		context.getInput().pressMouse(1);
		server.waitFor(s -> player(s).level().dimension() == Level.OVERWORLD, 200);
		context.waitTicks(40);
		server.runOnServer(s -> {
			ServerPlayer player = player(s);
			check(player.level().dimension() == Level.OVERWORLD, "bounced back");
			check(!player.isInWall(), "arrived inside a block");
			check(player.getInventory().getItem(0).getCount() == 5, "held item used or lost");
		});
	}

	private static int litNear(MinecraftServer server) {
		ServerPlayer player = player(server);
		ServerLevel level = player.level();
		int lit = 0;
		for (BlockPos pos : BlockPos.betweenClosed(player.blockPosition().offset(-24, 0, -24), player.blockPosition().offset(24, 5, 24))) {
			BlockState state = level.getBlockState(pos);
			if (state.getBlock() instanceof Level1LampBlock && state.getValue(Level1LampBlock.LIT)) lit++;
		}
		return lit;
	}

	private static int[] nearestDistrict(long seed, Kind kind) {
		for (int ring = 0; ring < 10; ring++) {
			for (int dx = -ring; dx <= ring; dx++) {
				for (int dz = -ring; dz <= ring; dz++) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) == ring && Level1Layout.kind(seed, dx, dz) == kind) return new int[] {dx, dz};
				}
			}
		}
		throw new AssertionError("no " + kind + " district near the origin");
	}

	/** The first block of a tile type in a district, scanning from (x, z). */
	private static BlockPos find(long seed, int x, int z, Tile tile) {
		int x0 = Math.floorDiv(x, 64) * 64, z0 = Math.floorDiv(z, 64) * 64;
		for (int i = 0; i < 64 * 64; i++) {
			int bx = x0 + Math.floorMod(x - x0 + i % 64, 64), bz = z0 + Math.floorMod(z - z0 + i / 64, 64);
			if (Level1Layout.tile(seed, bx, bz) == tile) return new BlockPos(bx, STAND, bz);
		}
		return null;
	}

	private static void checkGenerated(ServerLevel level, long seed, BlockPos around) {
		int[] ys = {0, 31, 32, 33, 35, 36, 37, 38, 39};
		for (int x = around.getX() - 24; x < around.getX() + 24; x++) {
			for (int z = around.getZ() - 24; z < around.getZ() + 24; z++) {
				for (int y : ys) {
					BlockState expected = Level1ChunkGenerator.blockAt(seed, x, y, z);
					check(level.getBlockState(new BlockPos(x, y, z)).is(expected.getBlock()), "world differs from generator at " + x + ", " + y + ", " + z);
				}
			}
		}
	}

	private static void shoot(ClientGameTestContext context, TestSingleplayerContext game, double x, double y, double z, float yaw, float pitch, String name) {
		game.getServer().runCommand(String.format(Locale.ROOT, "execute as @a in noclip_backrooms:level_1 run tp @s %.2f %.2f %.2f %.1f %.1f", x, y, z, yaw, pitch));
		game.getConnection().waitForChunksDownload();
		context.waitTicks(25);
		game.getConnection().waitForChunksRender();
		context.runOnClient(client -> client.gui.toastManager().clear());
		context.waitTick();
		context.takeScreenshot(name);
	}

	private static ServerLevel level1(MinecraftServer server) {
		return server.getLevel(Level1.LEVEL_1);
	}

	private static ServerPlayer player(MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}

	private static void check(boolean condition, String message) {
		if (!condition) throw new AssertionError(message);
	}
}
