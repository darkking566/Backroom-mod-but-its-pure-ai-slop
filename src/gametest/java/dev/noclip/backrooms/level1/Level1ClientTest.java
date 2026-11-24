package dev.noclip.backrooms.level1;

import dev.noclip.backrooms.core.LevelExits;
import dev.noclip.backrooms.level0.Level0;
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
	/** Set NOCLIP_ONLY=people,secrets to run just those sections (the full run takes a quarter of an hour); unset runs everything. */
	private static final String ONLY = System.getenv("NOCLIP_ONLY");

	private static boolean only(String section) {
		return ONLY == null || ONLY.isBlank() || java.util.Arrays.asList(ONLY.split(",")).contains(section);
	}

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext game = context.worldBuilder().create()) {
			var server = game.getServer();
			game.getConnection().waitForChunksRender();
			server.runCommand("gamerule send_command_feedback false");
			// Level 0 reshaping itself is tested on its own; here it would only move the flickering wall away.
			server.runCommand("gamerule noclip_backrooms:edge_shift false");
			server.runCommand("gamemode survival @a");
			server.runCommand("clear @a");

			enterThroughFlickeringWall(context, game);

			long seed = server.computeOnServer(s -> Level1ChunkGenerator.planSeed(level1(s).getChunkSource().randomState()));
			server.runOnServer(s -> {
				Blackouts.holdOff(20 * 60 * 10);
				// Marauders are tested on their own below; until then none turn up by chance.
				Marauders.holdOff(s, 20 * 60 * 10);
				// Same for hounds, which come out in blackouts.
				Hounds.holdOff(s, 20 * 60 * 10);
				// And nobody moves into the settlements, or is carried off along the halls, until their own tests.
				Residents.holdOff(s, 20 * 60 * 10);
				Warps.holdOff(s, 20 * 60 * 10);
				Patrols.holdOff(s, 20 * 60 * 10);
				Workers.holdOff(s, 20 * 60 * 10);
			});
			server.runCommand("gamemode spectator @a");
			BlockPos arrival = server.computeOnServer(s -> player(s).blockPosition());
			for (int yaw = 0; yaw < 360; yaw += 90) {
				shoot(context, game, arrival.getX() + 0.5, STAND, arrival.getZ() + 0.5, yaw, 5, "level1_arrival_yaw_" + yaw);
			}
			shoot(context, game, arrival.getX() + 0.5, STAND, arrival.getZ() + 0.5, 90, -50, "level1_ceiling");

			if (only("blackout")) blackout(context, game);
			if (only("crate")) crate(context, game);
			if (only("marker")) marker(context, game);
			if (only("views")) views(context, game, seed);
			if (only("sectors")) sectors(context, game, seed);
			if (only("exits")) exits(context, game, seed);
			if (only("events")) events(context, game, seed);
			if (only("settlements")) settlements(context, game, seed);
			if (only("wikiSettlements")) wikiSettlements(context, game, seed);
			if (only("secrets")) secrets(context, game, seed);
			if (only("items")) items(context, game, seed);
			if (only("monsters")) monsters(context, game, seed);
			if (only("people")) people(context, game, seed);
			if (only("rooms")) rooms(context, game, seed);
			if (only("hounds")) hounds(context, game, seed);
			if (only("shepherdSnake")) shepherdSnake(context, game, seed);
			if (only("checkBlocks")) checkBlocks(context, game);
			if (only("leaveThroughDoor")) leaveThroughDoor(context, game, seed);
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
				boolean clear = true;
				for (int step = 1; step <= 2; step++) {
					BlockPos at = wall.relative(side, step);
					clear &= level.getBlockState(at).isAir() && level.getBlockState(at.above()).isAir()
							&& !level.getBlockState(at.below()).getCollisionShape(level, at.below()).isEmpty();
				}
				if (clear) return side;
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
		check(server.computeOnServer(s -> player(s).level().dimension() == Level0.LEVEL_0), "left Level 0 too early");

		context.getInput().holdKeyFor(options -> options.keyUp, 40);
		for (int waited = 0; waited < 200 && !server.computeOnServer(s -> player(s).level().dimension() == Level1.LEVEL_1); waited += 10) {
			context.waitTicks(10);
		}
		server.runOnServer(s -> check(player(s).level().dimension() == Level1.LEVEL_1, "did not fall through the flickering wall at "
				+ wall + " (" + player(s).level().getBlockState(wall) + "), player at " + player(s).position() + " in " + player(s).level().dimension()));
		context.waitTicks(40);
		game.getConnection().waitForChunksRender();
		server.runOnServer(s -> {
			ServerPlayer player = player(s);
			check(Math.abs(player.getY() - STAND) < 0.01, "landed at y=" + player.getY());
			check(!player.isInWall(), "landed inside a wall");
			check(player.getHealth() == player.getMaxHealth(), "hurt on the way in");
			long seed = Level1ChunkGenerator.planSeed(player.level().getChunkSource().randomState());
			check(Level1Layout.kindAt(seed, player.getBlockX(), player.getBlockZ()) == Kind.PARKING, "did not land in a parking hall");
			BlockState left = s.getLevel(Level0.LEVEL_0).getBlockState(wall);
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

	/** The Trader's Keep sells for alloy, Hippocrates-1 mends for free; a picture of each other settlement too. */
	private static void wikiSettlements(ClientGameTestContext context, TestSingleplayerContext game, long seed) {
		var server = game.getServer();
		server.runCommand("gamemode spectator @a");
		for (Level1Layout.Base base : new Level1Layout.Base[] {Level1Layout.Base.TOMS, Level1Layout.Base.SIGNUP, Level1Layout.Base.CORNUCOPIA}) {
			int[] at = nearestBase(seed, base);
			shoot(context, game, at[0] * 64 + 31.5, STAND, at[1] * 64 + 11.5, 0, 0, "level1_" + base.name().toLowerCase());
		}
		int[] keep = nearestBase(seed, Level1Layout.Base.TRADERS_KEEP);
		shoot(context, game, keep[0] * 64 + 31.5, STAND, keep[1] * 64 + 11.5, 0, 0, "level1_traders_keep");
		server.runOnServer(s -> player(s).getInventory().clearContent());
		server.runCommand("give @a noclip_backrooms:alloy 1");
		standAndUse(context, game, keep[0] * 64 + 24.5, keep[1] * 64 + 26.5, 180, 35);
		server.runOnServer(s -> {
			check(player(s).getInventory().countItem(Level1Items.ALLOY) == 0, "the counter took no alloy");
			boolean got = !level1(s).getEntitiesOfClass(ItemEntity.class, new AABB(player(s).blockPosition()).inflate(5)).isEmpty()
					|| !player(s).getInventory().isEmpty();
			check(got, "the counter gave nothing");
		});
		int[] clinic = nearestBase(seed, Level1Layout.Base.HIPPOCRATES);
		shoot(context, game, clinic[0] * 64 + 31.5, STAND, clinic[1] * 64 + 11.5, 0, 0, "level1_hippocrates");
		server.runOnServer(s -> player(s).setHealth(4.0F));
		standAndUse(context, game, clinic[0] * 64 + 24.5, clinic[1] * 64 + 25.5, 180, 22);
		server.runOnServer(s -> check(player(s).getHealth() >= player(s).getMaxHealth() - 0.1F, "the clinic bed did not mend the player"));

		// The people move in, once.
		server.runCommand("gamemode spectator @a");
		int came = server.computeOnServer(s -> Residents.populate(level1(s), clinic[0], clinic[1]));
		check(came == 2, "the medics did not move into Hippocrates-1: " + came);
		check(server.computeOnServer(s -> Residents.populate(level1(s), clinic[0], clinic[1])) == 0, "the medics moved in twice");
		shoot(context, game, clinic[0] * 64 + 31.5, STAND, clinic[1] * 64 + 11.5, 0, 0, "level1_hippocrates_people");
		int[] fort = nearestBase(seed, Level1Layout.Base.CORNUCOPIA);
		// The district has to be loaded for anyone to move in.
		shoot(context, game, fort[0] * 64 + 31.5, STAND, fort[1] * 64 + 11.5, 0, 0, "level1_cornucopia");
		check(server.computeOnServer(s -> Residents.populate(level1(s), fort[0], fort[1])) > 0, "no one is at Cornucopia");
		shoot(context, game, fort[0] * 64 + 31.5, STAND, fort[1] * 64 + 11.5, 0, 0, "level1_cornucopia_people");

		// Where the copper pipes crowd in, the way to Level 2 (shut until that level exists).
		BlockPos pipes = null;
		for (int rx = -6; rx <= 6 && pipes == null; rx++) {
			for (int rz = -6; rz <= 6 && pipes == null; rz++) {
				int cx = rx * 256 + 128, cz = rz * 256 + 128;
				for (int dx = -8; dx <= 8 && pipes == null; dx++) {
					for (int dz = -8; dz <= 8 && pipes == null; dz++) {
						double zone = Level1Layout.pipeZone(seed, cx + dx, cz + dz);
						if (zone >= 0 && zone <= 0.06 && Level1Layout.tile(seed, cx + dx, cz + dz) == Tile.FLOOR
								&& Level1Layout.kindAt(seed, cx + dx, cz + dz) != Kind.CORRIDORS) pipes = new BlockPos(cx + dx, STAND, cz + dz);
					}
				}
			}
		}
		check(pipes != null, "no pipe zone near the origin");
		int refusedBefore = LevelExits.refusals();
		shoot(context, game, pipes.getX() + 0.5, STAND, pipes.getZ() + 0.5, 90, 5, "level1_pipe_zone");
		server.runCommand("gamemode survival @a");
		context.waitTicks(30);
		// Level 2 is open now, so the threshold carries you down; before it opened, it refused. Either way it does something.
		boolean carried = server.computeOnServer(s -> s.getPlayerList().getPlayers().getFirst().level().dimension() != Level1.LEVEL_1);
		check(LevelExits.refusals() > refusedBefore || carried, "the threshold to Level 2 did nothing");
		if (carried) {
			server.runCommand(String.format(Locale.ROOT, "execute in noclip_backrooms:level_1 run tp @a %.1f %d %.1f", pipes.getX() + 0.5, STAND, pipes.getZ() + 0.5));
			server.waitFor(s -> s.getPlayerList().getPlayers().getFirst().level().dimension() == Level1.LEVEL_1, 100);
			game.getConnection().waitForChunksDownload();
			context.waitTicks(20);
		}
		// Gauges on the pillars and thicker pipes overhead, toward the middle of the pipe zone.
		long pipeSeed = seed;
		BlockPos gauge = null;
		for (int dx = -30; dx <= 30 && gauge == null; dx++) {
			for (int dz = -30; dz <= 30 && gauge == null; dz++) {
				if (Level1ChunkGenerator.blockAt(pipeSeed, pipes.getX() + dx, Level1ChunkGenerator.FLOOR_Y + 2, pipes.getZ() + dz).is(Level1Blocks.PIPE_GAUGE)) {
					gauge = new BlockPos(pipes.getX() + dx, Level1ChunkGenerator.FLOOR_Y + 2, pipes.getZ() + dz);
				}
			}
		}
		check(gauge != null, "no pipe gauge in the pipe zone");
		Direction away = Level1ChunkGenerator.blockAt(pipeSeed, gauge.getX(), gauge.getY(), gauge.getZ()).getValue(PhosphorMarkBlock.FACING);
		server.runCommand("gamemode spectator @a");
		shoot(context, game, gauge.getX() + 0.5 + away.getStepX() * 3, STAND, gauge.getZ() + 0.5 + away.getStepZ() * 3, away.getOpposite().toYRot(), 0, "level1_pipe_gauge");
		server.runCommand("gamemode spectator @a");

		// The zone is not Euclidean: carried a bay or two along the hall, heading kept.
		int[] spot = Level1Layout.landing(seed, 0, 0);
		server.runCommand(String.format(Locale.ROOT, "execute as @a in noclip_backrooms:level_1 run tp @s %.1f %d %.1f 0 0", spot[0] + 0.5, STAND, spot[1] + 0.5));
		server.runCommand("gamemode survival @a");
		context.waitTicks(20);
		boolean warped = server.computeOnServer(s -> {
			for (int jumps = 1; jumps <= 2; jumps++) {
				for (int sign = 1; sign >= -1; sign -= 2) {
					if (Warps.warp(level1(s), player(s), new Vec3(1, 0, 0), sign, jumps)) return true;
				}
			}
			return false;
		});
		check(warped, "the hall carried no one anywhere");
		server.runCommand("gamemode spectator @a");
		server.runCommand("gamemode spectator @a");
	}

	/** The patrol, the workers, the lock, the smoke, the secret route to the Hub and the glitch trail. */
	private static void secrets(ClientGameTestContext context, TestSingleplayerContext game, long seed) {
		var server = game.getServer();
		server.runCommand("gamemode spectator @a");

		// Every settlement with its people, seen from inside.
		for (Level1Layout.Base base : Level1Layout.Base.values()) {
			if (base == Level1Layout.Base.NONE) continue;
			int[] at = nearestBase(seed, base);
			shoot(context, game, at[0] * 64 + 31.5, STAND, at[1] * 64 + 11.5, 0, 0, "level1_tour_" + base.name().toLowerCase() + "_outside");
			server.runOnServer(s -> Residents.populate(level1(s), at[0], at[1]));
			shoot(context, game, at[0] * 64 + 31.5, STAND, at[1] * 64 + 40.5, 180, 10, "level1_tour_" + base.name().toLowerCase());
		}

		// Newcomers are found and led toward the outpost.
		int[] spot = Level1Layout.landing(seed, 0, 0);
		shoot(context, game, spot[0] + 0.5, STAND, spot[1] + 0.5, 0, 0, "level1_patrol_start");
		server.runCommand("gamemode survival @a");
		check(server.computeOnServer(s -> Patrols.dispatch(level1(s), player(s))), "no patrol set out");
		for (int i = 0; i < 80 && Patrols.leading() == 0; i++) context.waitTicks(10);
		check(Patrols.leading() > 0, "the patrol never reached the player");
		double start = server.computeOnServer(s -> Patrols.memberPosition().distanceTo(Patrols.destination()));
		for (int i = 0; i < 60; i++) {
			context.waitTicks(10);
			Vec3 to = server.computeOnServer(s -> {
				Vec3 member = Patrols.memberPosition(), base = Patrols.destination();
				if (member == null || base == null) return null;
				return member.add(base.subtract(member).multiply(1, 0, 1).normalize().scale(6));
			});
			if (to != null) server.runCommand(String.format(Locale.ROOT, "execute as @a in noclip_backrooms:level_1 run tp @s %.2f %d %.2f", to.x, STAND, to.z));
		}
		double end = server.computeOnServer(s -> Patrols.memberPosition() == null ? 0 : Patrols.memberPosition().distanceTo(Patrols.destination()));
		check(end < start - 15, "the patrol did not lead anywhere: " + start + " -> " + end);
		server.runCommand("gamemode spectator @a");

		// A natural lock in a doorway shuts the whole room for good.
		int[] corridor = null;
		for (int ring = 0; ring < 10 && corridor == null; ring++) {
			for (int dx = -ring; dx <= ring && corridor == null; dx++) {
				for (int dz = -ring; dz <= ring && corridor == null; dz++) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) == ring && !Level1Layout.doorways(seed, dx, dz).isEmpty()) corridor = new int[] {dx, dz};
				}
			}
		}
		check(corridor != null, "no room with a doorway near the origin");
		int[] room = corridor;
		CorridorPlan.Doorway doorway = Level1Layout.doorways(seed, room[0], room[1]).getFirst();
		BlockPos lock = new BlockPos(room[0] * 64 + doorway.lockX(), Level1ChunkGenerator.LOW_CEILING_Y - 1, room[1] * 64 + doorway.lockZ());
		shoot(context, game, lock.getX() + 0.5, STAND, lock.getZ() + 0.5, 0, 0, "level1_lock_place");
		server.runOnServer(s -> level1(s).setBlock(lock, Level1Blocks.NATURAL_LOCK.defaultBlockState(), 3));
		server.runCommand("gamemode survival @a");
		context.waitTicks(10);
		shoot(context, game, lock.getX() + 0.5, STAND, lock.getZ() + 0.5, 0, 0, "level1_lock");
		server.runOnServer(s -> {
			ServerLevel level = level1(s);
			level.getBlockState(lock).useWithoutItem(level, player(s), new net.minecraft.world.phys.BlockHitResult(Vec3.atCenterOf(lock), Direction.NORTH, lock, false));
			for (int[] tile : doorway.tiles()) {
				BlockPos at = new BlockPos(room[0] * 64 + tile[0], STAND, room[1] * 64 + tile[1]);
				check(level.getBlockState(at).is(Level1Blocks.SEALED_DOOR), "a doorway of the room was not sealed at " + at);
			}
		});
		context.waitTicks(10);
		server.runCommand("gamemode spectator @a");
		// The sealed doorway, seen from either side along the passage.
		boolean throughX = doorway.tiles()[0][0] == doorway.tiles()[1][0];
		for (int side = -3; side <= 3; side += 6) {
			int cx = lock.getX() + (throughX ? side : 0), cz = lock.getZ() + (throughX ? 0 : side);
			if (!Level1Layout.tile(seed, cx, cz).walkable()) continue;
			float yaw = throughX ? (side < 0 ? -90 : 90) : (side < 0 ? 0 : 180);
			shoot(context, game, cx + 0.5, STAND, cz + 0.5, yaw, 0, "level1_sealed_door");
			break;
		}

		// Smoke chokes a camp in the corridors.
		BlockPos camp = find(seed, room[0] * 64 + 22, room[1] * 64 + 22, Tile.CORRIDOR);
		shoot(context, game, camp.getX() + 0.5, STAND, camp.getZ() + 0.5, 0, 0, "level1_smoke_place");
		server.runOnServer(s -> {
			level1(s).setBlock(camp.east(), net.minecraft.world.level.block.Blocks.CAMPFIRE.defaultBlockState(), 3);
			player(s).setHealth(player(s).getMaxHealth());
		});
		server.runCommand("gamemode survival @a");
		context.waitTicks(70);
		server.runOnServer(s -> check(player(s).getHealth() < player(s).getMaxHealth(), "the campfire smoke did not choke the player"));
		server.runCommand("gamemode spectator @a");

		// Shadows at work in the Tailchase Sector.
		BlockPos tail = null;
		for (int rx = -6; rx <= 6 && tail == null; rx++) {
			for (int rz = -6; rz <= 6 && tail == null; rz++) {
				int x = rx * 256 + 128, z = rz * 256 + 128;
				if (Level1Layout.sector(seed, x, z) != Level1Layout.Sector.TAILCHASE) continue;
				for (int dx = 0; dx < 128 && tail == null; dx += 9) {
					if (Level1Layout.tile(seed, x + dx, z) == Tile.FLOOR && Level1Layout.kindAt(seed, x + dx, z) != Kind.CORRIDORS) tail = new BlockPos(x + dx, STAND, z);
				}
			}
		}
		check(tail != null, "no Tailchase sector near the origin");
		shoot(context, game, tail.getX() + 0.5, STAND, tail.getZ() + 0.5, 0, 5, "level1_tailchase_place");
		check(server.computeOnServer(s -> Workers.spawnNear(level1(s), player(s), seed, level1(s).getRandom())), "no worker could be placed");
		Vec3 worker = server.computeOnServer(s -> level1(s).getEntitiesOfClass(net.minecraft.world.entity.Mob.class, new AABB(player(s).blockPosition()).inflate(40),
				m -> m.entityTags().contains(Workers.TAG)).getFirst().position());
		// From five blocks off along a line of clear floor, so no pillar is in the way.
		for (Direction side : Direction.Plane.HORIZONTAL) {
			boolean clear = true;
			for (int i = 1; i <= 5; i++) {
				Tile along = Level1Layout.tile(seed, (int) Math.floor(worker.x) + side.getStepX() * i, (int) Math.floor(worker.z) + side.getStepZ() * i);
				clear &= along == Tile.FLOOR || along == Tile.LINE || along == Tile.DAMP;
			}
			if (clear) {
				shoot(context, game, worker.x + side.getStepX() * 5, STAND, worker.z + side.getStepZ() * 5, side.getOpposite().toYRot(), 8, "level1_worker");
				break;
			}
		}

		// The secret order of doors leads to the Hub.
		server.runCommand("gamemode survival @a");
		server.runOnServer(s -> {
			LevelExits.use(player(s), "22");
			LevelExits.use(player(s), "1.2");
			LevelExits.use(player(s), "19");
		});
		check(HubRoute.completions() == 1, "the route to the Hub did not complete");
		server.runCommand("gamemode spectator @a");

		// The glitch trail grows more broken toward Level 38.
		BlockPos trail = null;
		for (int ring = 0; ring < 14 && trail == null; ring++) {
			for (int dx = -ring; dx <= ring && trail == null; dx++) {
				for (int dz = -ring; dz <= ring && trail == null; dz++) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) continue;
					for (int ox = -3; ox <= 3 && trail == null; ox++) {
						for (int oz = -3; oz <= 3 && trail == null; oz++) {
							int x = dx * 64 + 32 + ox, z = dz * 64 + 32 + oz;
							double zone = Level1Layout.glitchZone(seed, x, z);
							if (zone >= 0 && zone <= 0.1 && Level1Layout.tile(seed, x, z) == Tile.CORRIDOR) trail = new BlockPos(x, STAND, z);
						}
					}
				}
			}
		}
		check(trail != null, "no glitch trail near the origin");
		context.waitTicks(60);
		int refused = LevelExits.refusals();
		shoot(context, game, trail.getX() + 0.5, STAND, trail.getZ() + 0.5, 0, 0, "level1_glitch_trail");
		server.runCommand("gamemode survival @a");
		context.waitTicks(30);
		check(LevelExits.refusals() > refused, "the glitch trail led nowhere");
		server.runCommand("gamemode spectator @a");
	}

	/** One picture of every sector, found by scanning the regions around the origin for a hall floor in each. */
	private static void sectors(ClientGameTestContext context, TestSingleplayerContext game, long seed) {
		for (Level1Layout.Sector sector : Level1Layout.Sector.values()) {
			BlockPos spot = null;
			for (int rx = -4; rx <= 4 && spot == null; rx++) {
				for (int rz = -4; rz <= 4 && spot == null; rz++) {
					int x = rx * 256 + 128, z = rz * 256 + 128;
					if (Level1Layout.sector(seed, x, z) != sector) continue;
					for (int dx = 0; dx < 128 && spot == null; dx += 9) {
						Tile tile = Level1Layout.tile(seed, x + dx, z);
						if (tile == Tile.FLOOR && Level1Layout.kindAt(seed, x + dx, z) != Kind.CORRIDORS) spot = new BlockPos(x + dx, STAND, z);
					}
				}
			}
			check(spot != null, "no " + sector + " sector near the origin");
			shoot(context, game, spot.getX() + 0.5, STAND, spot.getZ() + 0.5, 90, 5, "level1_sector_" + sector.name().toLowerCase());
		}
	}

	/** A settlement marker is hung on a pillar nearby; right-clicking takes its supplies once and leaves the bare cloth. */
	private static void marker(ClientGameTestContext context, TestSingleplayerContext game) {
		var server = game.getServer();
		BlockPos marker = server.computeOnServer(s -> Markers.hangOnPillar(level1(s), player(s)));
		check(marker != null, "no marker was hung");
		Direction out = server.computeOnServer(s -> level1(s).getBlockState(marker).getValue(SettlementMarkerBlock.FACING));
		BlockPos stand = marker.relative(out, 2);
		server.runOnServer(s -> check(level1(s).getBlockState(new BlockPos(stand.getX(), STAND, stand.getZ())).isAir(), "nowhere to stand by the marker"));
		server.runOnServer(s -> player(s).getInventory().clearContent());
		standAndUse(context, game, stand.getX() + 0.5, stand.getZ() + 0.5, out.getOpposite().toYRot(), 0);
		context.takeScreenshot("level1_marker");
		server.runOnServer(s -> {
			check(level1(s).getBlockState(marker).getValue(SettlementMarkerBlock.TAKEN), "the marker's supplies were not taken");
			boolean spilled = !level1(s).getEntitiesOfClass(ItemEntity.class, new AABB(marker).inflate(4)).isEmpty();
			check(spilled || !player(s).getInventory().isEmpty(), "the marker gave nothing");
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
		BlockPos bed = findNear(seed, Tile.BED);
		shoot(context, game, bed.getX() + 0.5, STAND, bed.getZ() + 2.5, 180, 25, "level1_infirmary");
		BlockPos gallery = findNear(seed, Tile.GALLERY);
		for (Direction side : Direction.Plane.HORIZONTAL) {
			if (Level1Layout.tile(seed, gallery.getX() + side.getStepX(), gallery.getZ() + side.getStepZ()).walkable()) {
				lookBack(context, game, seed, gallery, side, "level1_gallery", 5);
				break;
			}
		}
		BlockPos brick = findNear(seed, Tile.BRICK);
		for (Direction side : Direction.Plane.HORIZONTAL) {
			if (Level1Layout.tile(seed, brick.getX() + side.getStepX(), brick.getZ() + side.getStepZ()).walkable()) {
				lookBack(context, game, seed, brick, side, "level1_brick", 5);
				break;
			}
		}
	}

	/**
	 * Every kind of exit to another level, as generated: a picture of each, a shut one refusing,
	 * the lone door frame passed back and forth, and the stairs up into Level 0.
	 */
	private static void exits(ClientGameTestContext context, TestSingleplayerContext game, long seed) {
		var server = game.getServer();
		server.runCommand("gamemode spectator @a");
		for (Level1Exit exit : Level1Exit.values()) {
			HallPlan.Placed placed = nearestExit(seed, exit);
			int[] spot = placed.spot();
			double x = spot[0] + 0.5, z = spot[1] + 0.5;
			String name = "level1_exit_" + exit.level.replace('.', '_');
			switch (exit.shape) {
				case CORE_DOOR, CORE_PAINTING, CORE_OUTLETS -> {
					int[] out = HallPlan.FACES[placed.face()];
					float yaw = facing(out);
					shoot(context, game, x + out[0] * 3, STAND, z + out[1] * 3, yaw, exit.shape == Level1Exit.Shape.CORE_OUTLETS ? 25 : 5, name);
				}
				case PILLAR -> shoot(context, game, x - 3, STAND, z, -90, 5, name);
				case HATCH -> shoot(context, game, x, STAND, z + 2.5, 180, 50, name);
				case FRAME -> shoot(context, game, x, STAND, z + 4, 180, 5, name);
				case STAIRS_DOWN -> shoot(context, game, x + 0.5, STAND, z - 2.5, 0, 40, name);
				case STAIRS_UP -> shoot(context, game, x + 0.5, STAND, z - 3, 0, -25, name);
			}
		}

		// A door to a level that does not exist yet stays shut and says so.
		HallPlan.Placed shop = nearestExit(seed, Level1Exit.AUTOMATIC_DOOR);
		int[] door = shop.spot();
		int[] out = HallPlan.FACES[shop.face()];
		float yaw = facing(out);
		int refused = dev.noclip.backrooms.core.LevelExits.refusals();
		server.runCommand(String.format(Locale.ROOT, "execute as @a in noclip_backrooms:level_1 run tp @s %.1f %d %.1f %.1f 20",
				door[0] + 0.5 + out[0] * 1.5, STAND, door[1] + 0.5 + out[1] * 1.5, yaw));
		server.runCommand("gamemode survival @a");
		context.waitTicks(10);
		context.getInput().lookAt(yaw, 20);
		context.waitTicks(3);
		context.getInput().pressMouse(1);
		context.waitTicks(10);
		context.takeScreenshot("level1_exit_closed");
		server.runOnServer(s -> {
			check(dev.noclip.backrooms.core.LevelExits.refusals() > refused, "the door to Level 197 did not refuse");
			check(player(s).level().dimension() == Level1.LEVEL_1, "a closed door let the player through");
		});

		// Back and forth through the lone frame: the fourth pass tries to go to 1.1, which is not open either.
		HallPlan.Placed frame = nearestExit(seed, Level1Exit.DOOR_FRAME);
		int[] at = frame.spot();
		server.runCommand(String.format(Locale.ROOT, "execute as @a in noclip_backrooms:level_1 run tp @s %.1f %d %.1f 180 0",
				at[0] + 0.5, STAND, at[1] + 3.0));
		context.waitTicks(20);
		int before = dev.noclip.backrooms.core.LevelExits.refusals();
		for (int pass = 0; pass < DoorFrameBlock.PASSES; pass++) {
			boolean forward = pass % 2 == 0;
			context.getInput().holdKeyFor(options -> forward ? options.keyUp : options.keyDown, 22);
			context.waitTicks(6);
		}
		context.waitTicks(10);
		server.runOnServer(s -> check(dev.noclip.backrooms.core.LevelExits.refusals() > before, "going back and forth through the frame did nothing"));

		// The stairs up, rarest of all, climb back into Level 0.
		HallPlan.Placed stairs = nearestExit(seed, Level1Exit.STAIRS_UP);
		int[] bottom = stairs.spot();
		server.runCommand(String.format(Locale.ROOT, "execute as @a in noclip_backrooms:level_1 run tp @s %.1f %d %.1f 0 0",
				bottom[0] + 1.0, STAND, bottom[1] - 0.5));
		game.getConnection().waitForChunksDownload();
		context.waitTicks(20);
		context.getInput().holdKeyFor(options -> options.keyUp, 80);
		server.waitFor(s -> player(s).level().dimension() == Level0.LEVEL_0, 200);
		context.waitTicks(30);
		game.getConnection().waitForChunksRender();
		context.takeScreenshot("level1_stairs_into_level0");
		server.runOnServer(s -> {
			ServerPlayer player = player(s);
			check(!player.isInWall(), "came up inside a Level 0 wall");
			check(player.getHealth() == player.getMaxHealth(), "hurt on the stairs");
		});
		// Back to Level 1 for the rest of the test.
		server.runCommand("gamemode spectator @a");
		server.runCommand(String.format(Locale.ROOT, "execute as @a in noclip_backrooms:level_1 run tp @s %.1f %d %.1f",
				bottom[0] + 1.0, STAND, bottom[1] - 1.5));
		context.waitTicks(20);
	}

	/** The yaw that looks back at an exit from the side it faces. */
	private static float facing(int[] out) {
		return (float) Math.toDegrees(Math.atan2(out[0], -out[1]));
	}

	/** The toll booth's drawer, the generator in a blackout, the crate with eyes, and a stray by a crate. */
	private static void events(ClientGameTestContext context, TestSingleplayerContext game, long seed) {
		var server = game.getServer();
		server.runCommand("gamemode spectator @a");
		server.runCommand("clear @a");

		HallPlan.PlacedProp booth = nearestProp(seed, HallPlan.Prop.TOLL_BOOTH);
		shoot(context, game, booth.x0() + 3.5, STAND, booth.z0() + 12.5, 180, 0, "level1_toll_booth");
		standAndUse(context, game, booth.x0() + 3.5, booth.z0() + 8.5, 180, 45);
		server.runOnServer(s -> {
			BlockPos drawer = new BlockPos(booth.x0() + 3, STAND, booth.z0() + 7);
			check(level1(s).getBlockState(drawer).getValue(BoothDrawerBlock.EMPTY), "drawer not emptied");
			boolean flashlight = false;
			for (int i = 0; i < 36; i++) {
				flashlight |= BuiltInRegistries.ITEM.getKey(player(s).getInventory().getItem(i).getItem()).getPath().equals("flashlight");
			}
			check(flashlight, "no flashlight from the drawer");
		});
		context.takeScreenshot("level1_booth_drawer");

		HallPlan.PlacedProp room = nearestProp(seed, HallPlan.Prop.GENERATOR_ROOM);
		int[] door = room.door();
		int[] out = HallPlan.FACES[room.face()];
		float inward = facing(out);
		server.runCommand("gamemode spectator @a");
		shoot(context, game, door[0] + 0.5 + out[0] * 4, STAND, door[1] + 0.5 + out[1] * 4, inward, 0, "level1_generator_room");
		server.runOnServer(s -> Blackouts.startNow());
		server.waitFor(s -> Blackouts.phase() == Blackouts.Phase.DARK, 200);
		standAndUse(context, game, door[0] + 0.5 - out[0], door[1] + 0.5 - out[1], inward, 40);
		context.takeScreenshot("level1_generator_in_blackout");
		server.waitFor(s -> Blackouts.phase() == Blackouts.Phase.NORMAL, 60);
		server.runOnServer(s -> Blackouts.holdOff(20 * 60 * 10));

		HallPlan.PlacedProp crate = nearestProp(seed, HallPlan.Prop.EYED_CRATE);
		int[] at = crate.spot();
		server.runCommand("gamemode spectator @a");
		shoot(context, game, at[0] - 1.5, STAND, at[1] + 0.5, -90, 25, "level1_eyed_crate");
		server.runCommand("clear @a");
		standAndUse(context, game, at[0] - 1.5, at[1] + 0.5, -90, 30);
		server.runOnServer(s -> {
			check(level1(s).getBlockState(new BlockPos(at[0], STAND, at[1])).is(Level1Blocks.SHUT_EYED_CRATE), "the eyes did not close");
			boolean spilled = !level1(s).getEntitiesOfClass(ItemEntity.class, new AABB(new BlockPos(at[0], STAND, at[1])).inflate(3)).isEmpty();
			check(spilled || !player(s).getInventory().isEmpty(), "the crate with eyes was empty");
		});

		// A stray turns up by a crate, and bolts once someone comes close.
		server.runCommand("gamemode spectator @a");
		server.runCommand(String.format(Locale.ROOT, "execute as @a in noclip_backrooms:level_1 run tp @s %.1f %d %.1f",
				at[0] + 0.5, STAND, at[1] + 14.5));
		context.waitTicks(5);
		BlockPos crateAt = new BlockPos(at[0], STAND, at[1]);
		check(server.computeOnServer(s -> Strays.beside(level1(s), crateAt) != null), "no room for a stray by the crate");
		server.runOnServer(s -> check(!level1(s).getEntitiesOfClass(net.minecraft.world.entity.animal.feline.Cat.class,
				new AABB(crateAt).inflate(2)).isEmpty(), "no stray appeared"));
		context.takeScreenshot("level1_stray");
		standAndUse(context, game, at[0] - 1.5, at[1] + 0.5, -90, 0);
		context.waitTicks(20);
		server.runOnServer(s -> check(level1(s).getEntitiesOfClass(net.minecraft.world.entity.animal.feline.Cat.class,
				new AABB(crateAt).inflate(6)).isEmpty(), "the stray did not run off"));
	}

	/** Both settlements: a look in through the way in, settling on a cot, and a share from a locker. */
	private static void settlements(ClientGameTestContext context, TestSingleplayerContext game, long seed) {
		var server = game.getServer();
		int[] outpost = nearestBase(seed, Level1Layout.Base.FIRST_LIGHT);
		int x0 = outpost[0] * 64, z0 = outpost[1] * 64;
		server.runCommand("gamemode spectator @a");
		shoot(context, game, x0 + 31.5, STAND, z0 + 11.5, 0, 0, "level1_first_light");
		shoot(context, game, x0 + 22.5, STAND, z0 + 30.5, 180, 10, "level1_first_light_cots");

		server.runCommand("clear @a");
		standAndUse(context, game, x0 + 18.5, z0 + 21.5, 180, 50);
		server.runOnServer(s -> {
			var respawn = player(s).getRespawnConfig();
			check(respawn != null && respawn.respawnData().dimension() == Level1.LEVEL_1, "the cot did not set the respawn point");
		});
		standAndUse(context, game, x0 + 34.5, z0 + 42.5, 0, 30);
		server.runOnServer(s -> {
			boolean spilled = !level1(s).getEntitiesOfClass(ItemEntity.class, new AABB(new BlockPos(x0 + 34, STAND, z0 + 44)).inflate(3)).isEmpty();
			check(spilled || !player(s).getInventory().isEmpty(), "the locker gave nothing");
		});
		// Empty now; a piece of alloy buys another share.
		server.runCommand("clear @a");
		server.runCommand("item replace entity @a hotbar.0 with noclip_backrooms:alloy 2");
		standAndUse(context, game, x0 + 34.5, z0 + 42.5, 0, 30);
		server.runOnServer(s -> check(player(s).getInventory().countItem(Level1Items.ALLOY) == 1, "the locker did not take alloy"));
		// Settling is undone by sleeping elsewhere; for the rest of the test, home is the Overworld again.
		server.runCommand("execute in minecraft:overworld run spawnpoint @a 0 -60 0");

		int[] camp = nearestBase(seed, Level1Layout.Base.BLINK);
		int cx = camp[0] * 64, cz = camp[1] * 64;
		server.runCommand("gamemode spectator @a");
		shoot(context, game, cx + 31.5, STAND, cz + 11.5, 0, 0, "level1_blink");
		shoot(context, game, cx + 24.5, STAND, cz + 31.5, 180, 10, "level1_blink_fire");
	}

	/** Almond water clears a Red Room's effects and leaves the bottle; phosphor powder leaves a mark that glows in a blackout. */
	private static void items(ClientGameTestContext context, TestSingleplayerContext game, long seed) {
		var server = game.getServer();
		int[] spot = Level1Layout.landing(seed, 0, 0);
		server.runCommand(String.format(Locale.ROOT, "execute as @a in noclip_backrooms:level_1 run tp @s %.1f %d %.1f 0 0",
				spot[0] + 0.5, STAND, spot[1] + 0.5));
		server.runCommand("gamemode survival @a");
		server.runCommand("clear @a");
		server.runCommand("item replace entity @a hotbar.0 with noclip_backrooms:almond_water");
		server.runCommand("effect give @a minecraft:nausea 30");
		server.runCommand("effect give @a minecraft:darkness 30");
		context.waitTicks(10);
		context.getInput().holdMouse(1);
		context.waitTicks(45);
		context.getInput().releaseMouse(1);
		context.waitTicks(5);
		server.runOnServer(s -> {
			ServerPlayer player = player(s);
			check(!player.hasEffect(net.minecraft.world.effect.MobEffects.NAUSEA), "almond water left the nausea");
			check(!player.hasEffect(net.minecraft.world.effect.MobEffects.DARKNESS), "almond water left the darkness");
			check(player.getInventory().getItem(0).is(net.minecraft.world.item.Items.GLASS_BOTTLE), "no empty bottle after drinking");
		});

		server.runCommand("item replace entity @a hotbar.0 with noclip_backrooms:phosphor_powder 4");
		context.waitTicks(5);
		context.getInput().lookAt(0, 60);
		context.waitTicks(3);
		context.getInput().pressMouse(1);
		context.waitTicks(5);
		server.runOnServer(s -> {
			ServerPlayer player = player(s);
			int marks = 0;
			for (BlockPos pos : BlockPos.betweenClosed(player.blockPosition().offset(-3, -1, -3), player.blockPosition().offset(3, 1, 3))) {
				if (player.level().getBlockState(pos).is(Level1Blocks.PHOSPHOR_MARK)) marks++;
			}
			check(marks == 1, "phosphor left " + marks + " marks");
			check(player.getInventory().getItem(0).getCount() == 3, "phosphor powder not used up");
		});
		server.runOnServer(s -> Blackouts.startNow());
		server.waitFor(s -> Blackouts.phase() == Blackouts.Phase.DARK, 200);
		context.waitTicks(10);
		context.takeScreenshot("level1_phosphor_in_blackout");
		server.runOnServer(s -> Blackouts.endNow());
		server.waitFor(s -> Blackouts.phase() == Blackouts.Phase.NORMAL, 60);
		server.runOnServer(s -> Blackouts.holdOff(20 * 60 * 10));
	}

	/** A band of marauders behind the player, who drop alloy; and in a blackout, a smiler drawn in by a torch. */
	private static void monsters(ClientGameTestContext context, TestSingleplayerContext game, long seed) {
		var server = game.getServer();
		int[] spot = Level1Layout.landing(seed, 0, 0);
		server.runCommand(String.format(Locale.ROOT, "execute as @a in noclip_backrooms:level_1 run tp @s %.1f %d %.1f 180 0",
				spot[0] + 0.5, STAND, spot[1] + 0.5));
		server.runCommand("gamemode survival @a");
		server.runCommand("clear @a");
		game.getConnection().waitForChunksDownload();
		context.waitTicks(20);

		int came = server.computeOnServer(s -> Marauders.spawnBand(player(s)));
		check(came >= 2, "only " + came + " marauders came");
		Vec3 band = server.computeOnServer(s -> {
			var marauders = level1(s).getEntitiesOfClass(net.minecraft.world.entity.Mob.class, new AABB(player(s).blockPosition()).inflate(64),
					mob -> mob.entityTags().contains(Marauders.TAG));
			check(marauders.size() == came, "marauders missing");
			for (var mob : marauders) {
				check(mob instanceof net.minecraft.world.entity.monster.illager.AbstractIllager, "a marauder is not one of the raiders: " + mob);
				check(mob.getCustomName() != null, "a marauder has no name");
			}
			return marauders.getFirst().position();
		});
		server.runCommand("gamemode spectator @a");
		Vec3 toBand = band.subtract(spot[0] + 0.5, STAND, spot[1] + 0.5).normalize();
		shoot(context, game, band.x - toBand.x * 6, STAND, band.z - toBand.z * 6, (float) Math.toDegrees(Math.atan2(-toBand.x, toBand.z)), 5, "level1_marauders");
		server.runCommand("kill @e[tag=" + Marauders.TAG + "]");
		context.waitTicks(10);
		server.runOnServer(s -> {
			var alloy = level1(s).getEntitiesOfClass(ItemEntity.class, new AABB(BlockPos.containing(band)).inflate(6),
					item -> item.getItem().is(Level1Items.ALLOY));
			check(!alloy.isEmpty(), "marauders dropped no alloy");
		});

		// Smilers: in the dark they wait; a torch in hand brings them on.
		server.runCommand(String.format(Locale.ROOT, "execute as @a in noclip_backrooms:level_1 run tp @s %.1f %d %.1f 180 0",
				spot[0] + 0.5, STAND, spot[1] + 0.5));
		server.runCommand("gamemode survival @a");
		context.waitTicks(10);
		server.runOnServer(s -> Blackouts.startNow());
		server.waitFor(s -> Blackouts.phase() == Blackouts.Phase.DARK, 200);
		context.waitTicks(10);
		check(server.computeOnServer(s -> Smilers.count()) >= 1, "no smiler in the dark");
		double before = server.computeOnServer(Level1ClientTest::nearestSmiler);
		context.waitTicks(30);
		double still = server.computeOnServer(Level1ClientTest::nearestSmiler);
		check(before - still < 1.5, "a smiler rushed a player standing still in the dark");
		server.runCommand("item replace entity @a hotbar.0 with minecraft:torch");
		context.waitTicks(30);
		double drawn = server.computeOnServer(Level1ClientTest::nearestSmiler);
		check(still - drawn > 2.5, "a torch did not draw the smiler (" + still + " -> " + drawn + ")");
		float[] view = server.computeOnServer(s -> {
			var face = level1(s).getEntitiesOfClass(net.minecraft.world.entity.Display.ItemDisplay.class,
					new AABB(player(s).blockPosition()).inflate(32), d -> d.entityTags().contains(Smilers.TAG)).getFirst();
			Vec3 look = face.position().subtract(player(s).getEyePosition());
			return new float[] {(float) Math.toDegrees(Math.atan2(-look.x, look.z)),
					(float) -Math.toDegrees(Math.atan2(look.y, look.horizontalDistance()))};
		});
		context.getInput().lookAt(view[0], view[1]);
		context.waitTicks(3);
		context.takeScreenshot("level1_smiler");
		// Face to face, for a good look: drop the torch so it holds still, then bring it right in front.
		server.runCommand("clear @a");
		context.getInput().lookAt(180, 0);
		context.waitTicks(3);
		server.runOnServer(s -> {
			var face = level1(s).getEntitiesOfClass(net.minecraft.world.entity.Display.ItemDisplay.class,
					new AABB(player(s).blockPosition()).inflate(40), d -> d.entityTags().contains(Smilers.TAG)).getFirst();
			face.setPos(player(s).getX(), player(s).getEyeY(), player(s).getZ() - 3);
		});
		context.waitTicks(5);
		context.takeScreenshot("level1_smiler_close");
		server.runCommand("clear @a");
		server.runOnServer(s -> Blackouts.endNow());
		server.waitFor(s -> Blackouts.phase() == Blackouts.Phase.NORMAL, 60);
		context.waitTicks(5);
		check(server.computeOnServer(s -> Smilers.count()) == 0, "smilers stayed after the lights came back");
		server.runOnServer(s -> Blackouts.holdOff(20 * 60 * 10));
	}

	/** The Hound: how it looks standing and mid-lunge, its eyes in a blackout, a real bite, and leaving when the lights come back. */
	/** Every look of the people, standing in a row in a hall, from afar and close up. */
	private static void people(ClientGameTestContext context, TestSingleplayerContext game, long seed) {
		var server = game.getServer();
		int[] spot = Level1Layout.landing(seed, 0, 0);
		server.runCommand("gamemode spectator @a");
		var looks = dev.noclip.backrooms.core.people.People.LOOKS;
		server.runOnServer(s -> {
			for (int i = 0; i < looks.size(); i++) {
				var type = dev.noclip.backrooms.core.people.People.type(looks.get(i));
				var mob = type.create(level1(s), net.minecraft.world.entity.EntitySpawnReason.EVENT);
				mob.snapTo(spot[0] + 0.5 + (i - 3.5) * 1.1, STAND, spot[1] + 4.5, 180, 0);
				mob.setYBodyRot(180);
				mob.setYHeadRot(180);
				mob.setNoAi(true);
				mob.setInvulnerable(true);
				mob.addTag("test_person");
				level1(s).addFreshEntity(mob);
			}
		});
		shoot(context, game, spot[0] + 0.5, STAND + 0.6, spot[1] - 1.0, 0, 5, "level1_people_row");
		shoot(context, game, spot[0] + 0.5 - 1.65, STAND + 0.9, spot[1] + 2.4, 0, 5, "level1_people_close_left");
		shoot(context, game, spot[0] + 0.5 + 1.65, STAND + 0.9, spot[1] + 2.4, 0, 5, "level1_people_close_right");
		shoot(context, game, spot[0] + 0.5, STAND + 0.9, spot[1] + 8.5, 180, 5, "level1_people_backs");
		server.runCommand("kill @e[tag=test_person]");
		// Residents stroll about rather than stand still.
		int[] home = nearestBase(seed, Level1Layout.Base.TOMS);
		shoot(context, game, home[0] * 64 + 31.5, STAND, home[1] * 64 + 11.5, 0, 0, "level1_resident_walk_place");
		server.runOnServer(s -> Residents.populate(level1(s), home[0], home[1]));
		BlockPos hub = new BlockPos(home[0] * 64 + 32, STAND, home[1] * 64 + 30);
		Vec3 first = server.computeOnServer(s -> Residents.residentNear(level1(s), hub));
		check(first != null, "no resident moved in");
		double farthest = 0;
		for (int i = 0; i < 30; i++) {
			context.waitTicks(20);
			Vec3 now = server.computeOnServer(s -> Residents.residentNear(level1(s), hub));
			if (now != null) farthest = Math.max(farthest, now.distanceTo(first));
		}
		check(farthest > 1.5, "the residents stood still (moved " + farthest + ")");
	}

	/** One picture of each kind of room in the corridors, from a corner of its free ring. */
	private static void rooms(ClientGameTestContext context, TestSingleplayerContext game, long seed) {
		game.getServer().runCommand("gamemode spectator @a");
		for (CorridorPlan.Room type : CorridorPlan.Room.values()) {
			int[] at = null;
			for (int ring = 0; ring < 20 && at == null; ring++) {
				for (int dx = -ring; dx <= ring && at == null; dx++) {
					for (int dz = -ring; dz <= ring && at == null; dz++) {
						if (Math.max(Math.abs(dx), Math.abs(dz)) != ring || Level1Layout.kindAt(seed, dx * 64 + 32, dz * 64 + 32) != Kind.CORRIDORS) continue;
						CorridorPlan plan = CorridorPlan.of(seed, dx, dz);
						for (int ci = 1; ci < Level1Layout.CELLS && at == null; ci++) {
							for (int cj = 1; cj < Level1Layout.CELLS && at == null; cj++) {
								int lx = ci * Level1Layout.CELL, lz = cj * Level1Layout.CELL;
								// The top-left cell of a room, so its free ring starts one block in from the corner.
								if (plan.roomAt(lx, lz) == type && plan.roomAt(lx - 1, lz) != type && plan.roomAt(lx, lz - 1) != type) at = new int[] {dx * 64 + lx + 1, dz * 64 + lz + 1};
							}
						}
					}
				}
			}
			if (at == null) {
				System.out.println("no " + type + " room near the origin");
				continue;
			}
			shoot(context, game, at[0] + 0.5, STAND, at[1] + 0.5, -45, 12, "level1_room_" + type.name().toLowerCase());
		}
		BlockPos bin = findNear(seed, Tile.BIN);
		shoot(context, game, bin.getX() + 0.5, STAND - 0.3, bin.getZ() + 2.0, 180, 40, "level1_wastebasket");
	}

	private static void hounds(ClientGameTestContext context, TestSingleplayerContext game, long seed) {
		var server = game.getServer();
		int[] spot = Level1Layout.landing(seed, 0, 0);
		// A straight run of open floor from the landing spot to put it on.
		int[] way = server.computeOnServer(s -> {
			ServerLevel level = level1(s);
			for (Direction direction : Direction.Plane.HORIZONTAL) {
				boolean open = true;
				for (int k = 1; k <= 6 && open; k++) {
					BlockPos at = new BlockPos(spot[0] + direction.getStepX() * k, STAND, spot[1] + direction.getStepZ() * k);
					open = level.getBlockState(at).isAir() && level.getBlockState(at.above()).isAir()
							&& !level.getBlockState(at.below()).getCollisionShape(level, at.below()).isEmpty();
				}
				if (open) return new int[] {direction.getStepX(), direction.getStepZ()};
			}
			return null;
		});
		check(way != null, "no open floor next to the landing spot");
		float yaw = (float) Math.toDegrees(Math.atan2(-way[0], way[1]));
		double hx = spot[0] + 0.5 + way[0] * 4, hz = spot[1] + 0.5 + way[1] * 4;
		server.runCommand("gamemode spectator @a");
		server.runCommand(String.format(Locale.ROOT,
				"execute in noclip_backrooms:level_1 run summon noclip_backrooms:hound %.1f %d %.1f {NoAI:1b,Tags:[\"test_hound\"]}", hx, STAND, hz));
		server.runOnServer(s -> {
			var hound = testHound(s);
			float facing = yaw + 180 + 35; // turned a little, to see its side
			hound.setYRot(facing);
			hound.setYBodyRot(facing);
			hound.setYHeadRot(facing);
		});
		shoot(context, game, spot[0] + 0.5, STAND, spot[1] + 0.5, yaw, 15, "level1_hound");
		shoot(context, game, spot[0] + 0.5 + way[0] * 1.8, STAND - 0.6, spot[1] + 0.5 + way[1] * 1.8, yaw, 15, "level1_hound_close");
		server.runOnServer(s -> testHound(s).lunge(player(s)));
		context.waitTicks(17);
		context.takeScreenshot("level1_hound_lunge");
		context.waitTicks(40);
		server.runOnServer(s -> Blackouts.startNow());
		server.waitFor(s -> Blackouts.phase() == Blackouts.Phase.DARK, 200);
		context.waitTicks(10);
		context.takeScreenshot("level1_hound_in_the_dark");
		server.runOnServer(s -> Blackouts.endNow());
		server.waitFor(s -> Blackouts.phase() == Blackouts.Phase.NORMAL, 60);
		server.runOnServer(s -> Blackouts.holdOff(20 * 60 * 10));
		server.runCommand("kill @e[type=noclip_backrooms:hound]");
		context.waitTicks(25); // the body takes a second to go

		// A real one: it comes for a player in survival and its lunge lands.
		server.runCommand("gamemode survival @a");
		server.runCommand("clear @a");
		server.runCommand(String.format(Locale.ROOT, "execute as @a in noclip_backrooms:level_1 run tp @s %.1f %d %.1f %.1f 0",
				spot[0] + 0.5, STAND, spot[1] + 0.5, yaw));
		server.runOnServer(s -> player(s).setHealth(player(s).getMaxHealth()));
		server.runCommand(String.format(Locale.ROOT,
				"execute in noclip_backrooms:level_1 run summon noclip_backrooms:hound %.1f %d %.1f {Tags:[\"test_hound\"]}", hx, STAND, hz));
		context.waitTicks(8);
		context.takeScreenshot("level1_hound_coming");
		server.waitFor(s -> player(s).getHealth() < player(s).getMaxHealth(), 200);
		server.runCommand("kill @e[type=noclip_backrooms:hound]");
		server.runOnServer(s -> player(s).setHealth(player(s).getMaxHealth()));
		context.waitTicks(25);

		// Out in the halls, and gone again once the lights are back with nobody near it.
		Vec3 out = server.computeOnServer(s -> {
			var hound = Hounds.spawnNear(player(s));
			return hound == null ? null : hound.position();
		});
		check(out != null, "no hound came out of the halls");
		check(out.distanceTo(new Vec3(spot[0] + 0.5, STAND, spot[1] + 0.5)) > 16, "a hound came out right next to the player");
		int gone = server.computeOnServer(s -> Hounds.slinkOff(level1(s)));
		check(gone == 1, "a far-off hound did not leave when the lights came back (" + gone + ")");
		server.runCommand("gamemode spectator @a");
	}

	/** Verifies the Shepherd Snake stoops cleanly in 3-block Level 0 without suffocating, and stands tall in 5-block Level 1 halls. */
	private static void shepherdSnake(ClientGameTestContext context, TestSingleplayerContext game, long seed) {
		var server = game.getServer();
		server.runCommand("gamemode spectator @a");
		server.runCommand("execute as @a in noclip_backrooms:level_0 run tp @s 3.5 34.0 1.2 0 10");
		game.getConnection().waitForChunksDownload();
		context.waitTicks(20);
		game.getConnection().waitForChunksRender();

		server.runOnServer(s -> {
			ServerLevel level0 = s.getLevel(dev.noclip.backrooms.level0.Level0.LEVEL_0);
			var snake = dev.noclip.backrooms.monsters.Monsters.SHEPHERD_SNAKE.create(level0, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
			check(snake != null, "could not create shepherd snake");
			snake.setPos(3.5, 33.0, 4.5);
			snake.setYRot(180.0F);
			snake.yBodyRot = 180.0F;
			snake.yHeadRot = 180.0F;
			snake.addTag("test_snake");
			level0.addFreshEntity(snake);
		});
		for (int step : new int[] {1, 4, 40}) {
			context.waitTicks(step);
			server.runOnServer(s -> {
				ServerLevel level0 = s.getLevel(dev.noclip.backrooms.level0.Level0.LEVEL_0);
				var snakes = level0.getEntitiesOfClass(dev.noclip.backrooms.monsters.shepherdsnake.ShepherdSnake.class,
						new AABB(3, 32, 4, 4, 36, 5).inflate(16), e -> e.entityTags().contains("test_snake"));
				check(!snakes.isEmpty(), "test shepherd snake missing in level 0");
				var snake = snakes.getFirst();
				var src = snake.getLastDamageSource();
				check(snake.getHealth() == snake.getMaxHealth(),
						"shepherd snake took damage in level 0 at tick " + snake.tickCount + ": "
								+ snake.getHealth() + "/" + snake.getMaxHealth()
								+ " pose=" + snake.getPose() + " stoop=" + snake.stoopAmount()
								+ " pos=" + snake.position() + " src=" + (src == null ? "none" : src.getMsgId())
								+ " inWall=" + snake.isInWall() + " eyeY=" + snake.getEyeY());
			});
		}
		context.takeScreenshot("level0_shepherd_snake_stoop");
		server.runOnServer(s -> {
			ServerLevel level0 = s.getLevel(dev.noclip.backrooms.level0.Level0.LEVEL_0);
			var snakes = level0.getEntitiesOfClass(dev.noclip.backrooms.monsters.shepherdsnake.ShepherdSnake.class,
					new AABB(3, 32, 4, 4, 36, 5).inflate(16), e -> e.entityTags().contains("test_snake"));
			check(!snakes.isEmpty(), "test shepherd snake missing in level 0");
			var snake = snakes.getFirst();
			check(snake.getPose() == net.minecraft.world.entity.Pose.CROUCHING,
					"shepherd snake did not crouch in level 0: " + snake.getPose());
			check(snake.stoopAmount() >= 0.99F,
					"shepherd snake stoop too low in level 0: " + snake.stoopAmount());
			check(!snake.isInWall(), "shepherd snake stuck in wall in level 0");
		});
		server.runCommand("kill @e[tag=test_snake]");

		// Also verify it stands tall in a 5-block Level 1 hall.
		int[] spot = Level1Layout.landing(seed, 0, 0);
		server.runCommand(String.format(Locale.ROOT, "execute as @a in noclip_backrooms:level_1 run tp @s %.1f %d %.1f 180 0",
				spot[0] + 0.5, STAND + 1, spot[1] + 0.5));
		game.getConnection().waitForChunksDownload();
		context.waitTicks(15);
		server.runOnServer(s -> {
			var snake = dev.noclip.backrooms.monsters.Monsters.SHEPHERD_SNAKE.create(level1(s), net.minecraft.world.entity.EntitySpawnReason.COMMAND);
			check(snake != null, "could not create shepherd snake in level 1");
			snake.setPos(spot[0] + 0.5, STAND, spot[1] + 0.5);
			snake.addTag("test_snake");
			level1(s).addFreshEntity(snake);
		});
		context.waitTicks(20);
		server.runOnServer(s -> {
			var snakes = level1(s).getEntitiesOfClass(dev.noclip.backrooms.monsters.shepherdsnake.ShepherdSnake.class,
					new AABB(spot[0], STAND, spot[1], spot[0] + 1, STAND + 4, spot[1] + 1).inflate(16),
					e -> e.entityTags().contains("test_snake"));
			check(!snakes.isEmpty(), "test shepherd snake missing in level 1");
			var snake = snakes.getFirst();
			check(snake.getPose() == net.minecraft.world.entity.Pose.STANDING,
					"shepherd snake crouched in tall level 1 hall: " + snake.getPose());
			check(snake.stoopAmount() < 0.05F,
					"shepherd snake stooped in tall level 1 hall: " + snake.stoopAmount());
		});
		server.runCommand("kill @e[tag=test_snake]");
	}

	private static dev.noclip.backrooms.monsters.hound.Hound testHound(MinecraftServer server) {
		return level1(server).getEntitiesOfClass(dev.noclip.backrooms.monsters.hound.Hound.class,
				new AABB(player(server).blockPosition()).inflate(16), hound -> hound.entityTags().contains("test_hound")).getFirst();
	}

	private static double nearestSmiler(MinecraftServer server) {
		ServerPlayer player = player(server);
		double best = Double.MAX_VALUE;
		for (var face : level1(server).getEntitiesOfClass(net.minecraft.world.entity.Display.ItemDisplay.class,
				new AABB(player.blockPosition()).inflate(40), d -> d.entityTags().contains(Smilers.TAG))) {
			best = Math.min(best, face.position().distanceTo(player.getEyePosition()));
		}
		return best;
	}

	private static int[] nearestBase(long seed, Level1Layout.Base base) {
		for (int ring = 0; ring < 16; ring++) {
			for (int dx = -ring; dx <= ring; dx++) {
				for (int dz = -ring; dz <= ring; dz++) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) == ring && Level1Layout.base(seed, dx, dz) == base) return new int[] {dx, dz};
				}
			}
		}
		throw new AssertionError("no " + base + " near the origin");
	}

	/** Teleports there in survival, looks that way and right-clicks. */
	private static void standAndUse(ClientGameTestContext context, TestSingleplayerContext game, double x, double z, float yaw, float pitch) {
		var server = game.getServer();
		server.runCommand(String.format(Locale.ROOT, "execute as @a in noclip_backrooms:level_1 run tp @s %.2f %d %.2f %.1f %.1f",
				x, STAND, z, yaw, pitch));
		server.runCommand("gamemode survival @a");
		game.getConnection().waitForChunksDownload();
		context.waitTicks(15);
		context.getInput().lookAt(yaw, pitch);
		context.waitTicks(3);
		context.getInput().pressMouse(1);
		context.waitTicks(10);
	}

	private static HallPlan.PlacedProp nearestProp(long seed, HallPlan.Prop prop) {
		for (int ring = 0; ring < 12; ring++) {
			for (int dx = -ring; dx <= ring; dx++) {
				for (int dz = -ring; dz <= ring; dz++) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) continue;
					for (HallPlan.PlacedProp placed : Level1Layout.props(seed, dx, dz)) {
						if (placed.prop() == prop) return placed;
					}
				}
			}
		}
		throw new AssertionError("no " + prop + " near the origin");
	}

	private static HallPlan.Placed nearestExit(long seed, Level1Exit exit) {
		for (int ring = 0; ring < 12; ring++) {
			for (int dx = -ring; dx <= ring; dx++) {
				for (int dz = -ring; dz <= ring; dz++) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) continue;
					for (HallPlan.Placed placed : Level1Layout.exits(seed, dx, dz)) {
						if (placed.exit() == exit) return placed;
					}
				}
			}
		}
		throw new AssertionError("no " + exit + " near the origin");
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
				if (block.getLootTable().isEmpty() || block == Level1Blocks.SUPPLY_CRATE || block == Level1Blocks.DEBRIS_PILE || block == Level1Blocks.EYED_CRATE
						|| block == Level1Blocks.SUPPLY_LOCKER || block == Level1Blocks.TRADE_COUNTER || block == Level1Blocks.TOM_COUNTER) continue; // these hand out their contents instead
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
