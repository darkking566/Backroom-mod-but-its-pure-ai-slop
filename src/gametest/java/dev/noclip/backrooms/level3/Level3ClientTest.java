package dev.noclip.backrooms.level3;

import dev.noclip.backrooms.TestFilter;
import dev.noclip.backrooms.core.LevelExits;
import dev.noclip.backrooms.core.people.Person;
import dev.noclip.backrooms.core.trade.ShopRole;
import dev.noclip.backrooms.level3.Level3Layout.Kind;
import dev.noclip.backrooms.level3.Level3Layout.Profile;
import dev.noclip.backrooms.level3.Level3Layout.Tile;
import dev.noclip.backrooms.level3.Level3Plan.HallKind;
import dev.noclip.backrooms.monsters.Monsters;
import dev.noclip.backrooms.monsters.faceling.Faceling;
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
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.monster.illager.Pillager;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Plays Level 3 in a real client: down from Level 2 through a door marked "3", a look at passages of every size and
 * at every kind of hall, a lock picked, the ways out, the hazards, the paranoia, and an angel scaring a hound off.
 */
public final class Level3ClientTest implements FabricClientGameTest {
	private static final int STAND = Level3ChunkGenerator.FLOOR_Y + 1;

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext game = context.worldBuilder().create()) {
			var server = game.getServer();
			game.getConnection().waitForChunksRender();
			server.runCommand("gamerule send_command_feedback false");
			server.runCommand("gamerule noclip_backrooms:edge_shift false");
			server.runCommand("gamemode survival @a");
			server.runOnServer(s -> {
				Level3Events.holdOffHounds(s, 20 * 60 * 20);
				Level3Monsters.holdOff(s, 20 * 60 * 20);
			});

			enterFromLevel2(context, game);
			long seed = server.computeOnServer(s -> Level3ChunkGenerator.planSeed(level3(s).getChunkSource().randomState()));
			BlockPos arrival = server.computeOnServer(s -> player(s).blockPosition());
			server.runOnServer(s -> {
				Tile tile = Level3Layout.tile(seed, arrival.getX(), arrival.getZ());
				check(tile.kind() == Kind.FLOOR && tile.clearance() >= 3, "arrived somewhere cramped at " + arrival);
				check(!player(s).isInWall(), "arrived inside a wall");
				checkGenerated(level3(s), seed, arrival);
			});
			server.runCommand("gamemode spectator @a");
			for (int yaw = 0; yaw < 360; yaw += 90) {
				shoot(context, game, arrival.getX() + 0.5, STAND, arrival.getZ() + 0.5, yaw, 5, "level3_arrival_yaw_" + yaw);
			}
			if (TestFilter.section("passages")) passages(context, game, seed, arrival);
			if (TestFilter.section("halls")) halls(context, game, seed, arrival);
			if (TestFilter.section("gate")) gate(context, game, seed, arrival);
			if (TestFilter.section("exits")) exits(context, game, seed, arrival);
			if (TestFilter.section("hazards")) hazards(context, game, seed, arrival);
			if (TestFilter.section("paranoia")) paranoia(context, game, seed, arrival);
			if (TestFilter.section("angel")) angel(context, game, seed, arrival);
			if (TestFilter.section("hounds")) hounds(context, game, seed, arrival);
			if (TestFilter.section("monsters")) monsters(context, game, seed, arrival);
			if (TestFilter.section("bases")) bases(context, game, seed, arrival);
			if (TestFilter.section("gamma")) gamma(context, game, seed, arrival);
			if (TestFilter.section("blocks")) checkBlocks(game);
		}
	}

	/** Level 2's doors marked "3" lead here now: Level 3 has opened "3". */
	private static void enterFromLevel2(ClientGameTestContext context, TestSingleplayerContext game) {
		var server = game.getServer();
		server.runCommand("execute in noclip_backrooms:level_2 run tp @a 2.5 33 2.5");
		game.getConnection().waitForChunksDownload();
		context.waitTicks(20);
		server.runOnServer(s -> check(LevelExits.isOpen("3"), "Level 3 did not open its way in"));
		server.runOnServer(s -> LevelExits.use(player(s), "3"));
		server.waitFor(s -> player(s).level().dimension() == Level3.LEVEL_3, 100);
		game.getConnection().waitForChunksDownload();
		context.waitTicks(30);
		game.getConnection().waitForChunksRender();
	}

	/** One of each kind of passage, looking along it, and a set of bars. */
	private static void passages(ClientGameTestContext context, TestSingleplayerContext game, long seed, BlockPos from) {
		for (Profile profile : Profile.values()) {
			int[] at = nearest(seed, from, t -> t.kind() == Kind.FLOOR, (x, z) -> {
				Level3Plan plan = Level3Layout.plan(seed, x, z);
				int lx = Math.floorMod(x, Level3Plan.SIZE), lz = Math.floorMod(z, Level3Plan.SIZE);
				int cell = Level3Plan.cell(lx / Level3Plan.CELL, lz / Level3Plan.CELL);
				return lx % Level3Plan.CELL == 2 && lz % Level3Plan.CELL == 2 && plan.profile(lx / Level3Plan.CELL, lz / Level3Plan.CELL) == profile
						&& plan.straightAxis(cell) >= 0;
			});
			Level3Plan plan = Level3Layout.plan(seed, at[0], at[1]);
			int cell = Level3Plan.cell(Math.floorMod(at[0], Level3Plan.SIZE) / Level3Plan.CELL, Math.floorMod(at[1], Level3Plan.SIZE) / Level3Plan.CELL);
			float yaw = plan.straightAxis(cell) == 0 ? -90 : 0;
			double eye = profile == Profile.LOW ? STAND - 0.4 : STAND;
			shoot(context, game, at[0] + 0.5, eye, at[1] + 0.5, yaw, 5, "level3_passage_" + profile.name().toLowerCase(Locale.ROOT));
		}
		for (boolean warning : new boolean[] {false, true}) {
			int[] signed = nearest(seed, from, t -> t.kind() == Kind.FLOOR, (x, z) -> {
				Level3Layout.Sign sign = Level3Layout.sign(seed, x, z);
				return sign != null && sign.warning() == warning;
			});
			int facing = Level3Layout.sign(seed, signed[0], signed[1]).facing();
			shoot(context, game, signed[0] + 0.5 + Level3Plan.DX[facing] * 1.0, STAND, signed[1] + 0.5 + Level3Plan.DZ[facing] * 1.0,
					yaw(Level3Plan.opposite(facing)), 10, warning ? "level3_hotspot_warning" : "level3_base_gamma_sign");
		}
		int[] bars = nearest(seed, from, t -> t.kind() == Kind.BARS && t.clearance() >= 3, (x, z) -> true);
		Tile tile = Level3Layout.tile(seed, bars[0], bars[1]);
		int back = Level3Plan.opposite(tile.dir());
		shoot(context, game, bars[0] + 0.5 + Level3Plan.DX[back] * 2.5, STAND, bars[1] + 0.5 + Level3Plan.DZ[back] * 2.5, yaw(tile.dir()), 5, "level3_bars");
	}

	/** Every kind of hall, from a corner. */
	private static void halls(ClientGameTestContext context, TestSingleplayerContext game, long seed, BlockPos from) {
		for (HallKind kind : HallKind.values()) {
			// Gamma is far bigger than a hall; it has its own section.
			if (kind == HallKind.GAMMA) continue;
			int[] at = nearest(seed, from, t -> t.kind() == Kind.HALL && t.hall().kind() == kind && t.hx() == 1 && t.hz() == 1, (x, z) -> true);
			Tile tile = Level3Layout.tile(seed, at[0], at[1]);
			int w = Level3Halls.width(tile.hall()), d = Level3Halls.depth(tile.hall());
			double cx = at[0] - 1 + w / 2.0, cz = at[1] - 1 + d / 2.0;
			float yaw = (float) Math.toDegrees(Math.atan2(-(cx - at[0]), cz - at[1]));
			shoot(context, game, at[0] + 0.5, STAND + 1.5, at[1] + 0.5, yaw, 15, "level3_hall_" + kind.name().toLowerCase(Locale.ROOT));
		}
	}

	/** A gate in the bars: keep picking at the lock and it gives. */
	private static void gate(ClientGameTestContext context, TestSingleplayerContext game, long seed, BlockPos from) {
		var server = game.getServer();
		int[] gate = nearest(seed, from, t -> t.kind() == Kind.GATE && t.clearance() >= 2, (x, z) -> true);
		int side = Level3Layout.tile(seed, gate[0], gate[1]).dir();
		// Stand inside the cell the gate's side belongs to, facing out through it.
		int back = Level3Plan.opposite(side);
		float yaw = yaw(side);
		server.runCommand(String.format(Locale.ROOT, "execute as @a in noclip_backrooms:level_3 run tp @s %.2f %d %.2f %.1f 25",
				gate[0] + 0.5 + Level3Plan.DX[back] * 1.4, STAND, gate[1] + 0.5 + Level3Plan.DZ[back] * 1.4, yaw));
		server.runCommand("gamemode survival @a");
		game.getConnection().waitForChunksDownload();
		context.waitTicks(15);
		context.takeScreenshot("level3_gate_locked");
		BlockPos bottom = new BlockPos(gate[0], STAND, gate[1]);
		for (int tries = 0; tries < 40; tries++) {
			context.getInput().lookAt(yaw, 25);
			context.getInput().pressMouse(1);
			context.waitTicks(4);
			if (server.computeOnServer(s -> level3(s).getBlockState(bottom).getValue(Level3GateBlock.OPEN))) break;
		}
		server.runOnServer(s -> check(level3(s).getBlockState(bottom).getValue(Level3GateBlock.OPEN), "the gate never opened"));
		context.waitTicks(10);
		context.takeScreenshot("level3_gate_open");
		server.runCommand("gamemode spectator @a");
	}

	/** The ways out: an elevator, Base Gamma's door, a car. None of their levels exists yet, so each refuses. */
	private static void exits(ClientGameTestContext context, TestSingleplayerContext game, long seed, BlockPos from) {
		var server = game.getServer();
		int[] elevator = nearest(seed, from, t -> t.kind() == Kind.EXIT && t.exit() == Level3Exit.ELEVATOR, (x, z) -> true);
		int[] door = nearest(seed, from, t -> t.kind() == Kind.EXIT && t.exit() == Level3Exit.BASE_GAMMA_DOOR, (x, z) -> true);
		for (int[] at : new int[][] {elevator, door}) {
			int out = Level3Layout.tile(seed, at[0], at[1]).dir();
			String name = at == elevator ? "level3_exit_elevator" : "level3_exit_base_gamma";
			shoot(context, game, at[0] + 0.5 + Level3Plan.DX[out] * 2.2, STAND, at[1] + 0.5 + Level3Plan.DZ[out] * 2.2, yaw(Level3Plan.opposite(out)), 10, name);
			context.waitTicks(45); // exits only say "not open yet" every two seconds
			int refused = LevelExits.refusals();
			useBlockFrom(context, game, at, out, 1.5);
			server.runOnServer(s -> {
				check(LevelExits.refusals() > refused, name + " did not refuse");
				check(player(s).level().dimension() == Level3.LEVEL_3, name + " let the player through");
			});
			server.runCommand("gamemode spectator @a");
		}
		int[] car = nearest(seed, from, t -> t.kind() == Kind.HALL && t.hall().kind() == HallKind.CAR, (x, z) -> true);
		BlockPos carBlock = server.computeOnServer(s -> {
			ServerLevel level = level3(s);
			for (int dx = -6; dx <= 6; dx++) {
				for (int dz = -6; dz <= 6; dz++) {
					BlockPos pos = new BlockPos(car[0] + dx, STAND, car[1] + dz);
					if (level.getBlockState(pos).is(Level3Blocks.CAR)) return pos;
				}
			}
			return null;
		});
		check(carBlock != null, "no car in the car room");
		shoot(context, game, carBlock.getX() + 2.5, STAND + 1, carBlock.getZ() + 2.5, 135, 20, "level3_exit_car");
		context.waitTicks(45);
		int refused = LevelExits.refusals();
		server.runOnServer(s -> level3(s).getBlockState(carBlock).useWithoutItem(level3(s), player(s),
				new net.minecraft.world.phys.BlockHitResult(Vec3.atCenterOf(carBlock), net.minecraft.core.Direction.UP, carBlock, false)));
		context.waitTicks(5);
		server.runOnServer(s -> check(LevelExits.refusals() > refused, "the car did not refuse"));
	}

	/** Live wires shock, sludge burns and sickens, flickering lights shatter, generators overload and blow. */
	private static void hazards(ClientGameTestContext context, TestSingleplayerContext game, long seed, BlockPos from) {
		var server = game.getServer();
		int[] wire = nearest(seed, from, t -> t.kind() == Kind.FLOOR, (x, z) -> Level3Layout.hazard(seed, x, z, Level3Layout.tile(seed, x, z)) == Level3Layout.Hazard.WIRE);
		shoot(context, game, wire[0] + 0.5, STAND + 1, wire[1] + 2.5, 180, 45, "level3_live_wire");
		standOn(context, game, wire);
		server.waitFor(s -> player(s).getHealth() < player(s).getMaxHealth(), 60);
		heal(game);
		int[] sludge = nearest(seed, from, t -> t.kind() == Kind.FLOOR, (x, z) -> Level3Layout.hazard(seed, x, z, Level3Layout.tile(seed, x, z)) == Level3Layout.Hazard.SLUDGE);
		standOn(context, game, sludge);
		server.waitFor(s -> player(s).getHealth() < player(s).getMaxHealth() && player(s).hasEffect(MobEffects.NAUSEA), 60);
		heal(game);
		// A flickering light right over a player shatters.
		boolean shattered = server.computeOnServer(s -> {
			ServerPlayer player = player(s);
			BlockPos over = player.blockPosition().above(2);
			level3(s).setBlock(over, Level3Blocks.FLICKERING_STATION_LIGHT.defaultBlockState(), 3);
			// Another flickering light close by may go first.
			for (int i = 0; i < 4 && !level3(s).getBlockState(over).is(Level3Blocks.BROKEN_STATION_LIGHT); i++) Level3Events.shatterOver(level3(s), player);
			return level3(s).getBlockState(over).is(Level3Blocks.BROKEN_STATION_LIGHT);
		});
		check(shattered, "a flickering light over the player did not shatter");
		heal(game);
		// A generator near a player overloads, then blows.
		int[] generator = nearest(seed, from, t -> t.kind() == Kind.HALL && t.hall().kind() == HallKind.GENERATOR && t.hx() == 1 && t.hz() == 1, (x, z) -> true);
		standOn(context, game, generator);
		boolean overloading = server.computeOnServer(s -> Level3Events.overloadNear(level3(s), seed, player(s), s.getTickCount()));
		check(overloading, "the generator did not overload");
		server.waitFor(s -> !Level3Events.overloading(), 120);
		context.takeScreenshot("level3_after_overload");
		heal(game);
		server.runCommand("gamemode spectator @a");
	}

	/** Stand in a dark narrow stretch and you start to feel watched. */
	private static void paranoia(ClientGameTestContext context, TestSingleplayerContext game, long seed, BlockPos from) {
		var server = game.getServer();
		int[] spot = nearest(seed, from, t -> t.kind() == Kind.FLOOR && t.clearance() == 2, (x, z) -> Level3Layout.hotspot(seed, x, z));
		standOn(context, game, spot);
		server.waitFor(s -> Level3Events.watched(player(s)), 60);
		context.takeScreenshot("level3_hotspot");
		server.runCommand("gamemode spectator @a");
	}

	/** A hound in sight of a sanctum's angel is afraid, and leaves. */
	private static void angel(ClientGameTestContext context, TestSingleplayerContext game, long seed, BlockPos from) {
		var server = game.getServer();
		int[] corner = nearest(seed, from, t -> t.kind() == Kind.HALL && t.hall().kind() == HallKind.SANCTUM && t.hx() == 1 && t.hz() == 1, (x, z) -> true);
		Vec3 angel = server.computeOnServer(s -> {
			for (Level3Events.Placed placed : Level3Events.hallsNear(seed, corner[0], corner[1], 2)) {
				if (placed.hall().kind() == HallKind.SANCTUM) return placed.angel();
			}
			return null;
		});
		check(angel != null, "no angel in the sanctum");
		server.runCommand(String.format(Locale.ROOT, "execute as @a in noclip_backrooms:level_3 run tp @s %.1f %d %.1f", angel.x + 3, STAND, angel.z + 3));
		game.getConnection().waitForChunksDownload();
		context.waitTicks(10);
		shoot(context, game, angel.x + 3.5, STAND + 1, angel.z + 3.5, 135, 5, "level3_angel");
		server.runOnServer(s -> {
			var hound = Monsters.HOUND.create(level3(s), EntitySpawnReason.COMMAND);
			hound.snapTo(angel.x + 4, STAND, angel.z + 1, 0, 0);
			level3(s).addFreshEntity(hound);
			var moth = Monsters.DEATHMOTH.create(level3(s), EntitySpawnReason.COMMAND);
			moth.snapTo(angel.x - 4, STAND, angel.z + 1, 0, 0);
			level3(s).addFreshEntity(moth);
			var faceling = Monsters.FACELING.create(level3(s), EntitySpawnReason.COMMAND);
			faceling.snapTo(angel.x + 1, STAND, angel.z - 4, 0, 0);
			faceling.setHostile(true);
			level3(s).addFreshEntity(faceling);
		});
		server.waitFor(s -> level3(s).getEntities(Monsters.HOUND, h -> h.position().distanceTo(angel) < 20).isEmpty()
				&& level3(s).getEntities(Monsters.DEATHMOTH, m -> m.position().distanceTo(angel) < 20).isEmpty()
				&& level3(s).getEntities(Monsters.FACELING, f -> f.position().distanceTo(angel) < 20).isEmpty(), 80);
	}

	private static void hounds(ClientGameTestContext context, TestSingleplayerContext game, long seed, BlockPos from) {
		var server = game.getServer();
		int[] spot = nearest(seed, from, t -> t.kind() == Kind.FLOOR && t.clearance() >= 3,
				(x, z) -> Level3Events.hallsNear(seed, x, z, 52).stream()
						.noneMatch(p -> p.hall().kind() == HallKind.SANCTUM || p.hall().kind() == HallKind.BASE));
		server.runCommand(String.format(Locale.ROOT, "execute as @a in noclip_backrooms:level_3 run tp @s %.1f %d %.1f", spot[0] + 0.5, STAND, spot[1] + 0.5));
		server.runCommand("gamemode survival @a");
		game.getConnection().waitForChunksDownload();
		context.waitTicks(20);
		int came = server.computeOnServer(s -> Level3Events.spawnPack(player(s)));
		check(came >= 2, "a pack of " + came + " hounds");
		server.runCommand("kill @e[tag=" + Level3Events.HOUND_TAG + "]");
		server.runCommand("gamemode spectator @a");
	}

	private static void monsters(ClientGameTestContext context, TestSingleplayerContext game, long seed, BlockPos from) {
		var server = game.getServer();
		int[] spot = nearest(seed, from, t -> t.kind() == Kind.FLOOR && t.clearance() >= 3,
				(x, z) -> Level3Events.hallsNear(seed, x, z, 52).stream()
						.noneMatch(p -> p.hall().kind() == HallKind.SANCTUM || p.hall().kind() == HallKind.BASE));
		server.runCommand(String.format(Locale.ROOT, "execute as @a in noclip_backrooms:level_3 run tp @s %.1f %d %.1f", spot[0] + 0.5, STAND, spot[1] + 0.5));
		server.runCommand("gamemode survival @a");
		game.getConnection().waitForChunksDownload();
		context.waitTicks(20);
		server.runOnServer(s -> {
			for (Level3Monsters.Entry entry : Level3Monsters.POOL) {
				var mob = Level3Monsters.spawnOne(player(s), entry.type());
				check(mob != null, "failed to spawn " + entry.type().getDescriptionId());
				if (mob instanceof Faceling faceling) {
					check(faceling.isHostile(), "Level 3 faceling is not hostile");
					check(!faceling.getMainHandItem().isEmpty(), "Level 3 faceling is unarmed");
				}
			}
		});
		server.runCommand("kill @e[tag=" + Level3Monsters.TAG + "]");
		server.runCommand("gamemode spectator @a");
	}

	private static void bases(ClientGameTestContext context, TestSingleplayerContext game, long seed, BlockPos from) {
		var server = game.getServer();
		for (HallKind kind : new HallKind[] {HallKind.BASE, HallKind.BNTG_STORAGE, HallKind.SCYLLA_COLONY}) {
			int[] at = nearest(seed, from, t -> t.kind() == Kind.HALL && t.hall().kind() == kind && t.hx() == 2 && t.hz() == 2, (x, z) -> true);
			server.runCommand(String.format(Locale.ROOT, "execute as @a in noclip_backrooms:level_3 run tp @s %.1f %d %.1f", at[0] + 0.5, STAND, at[1] + 0.5));
			server.runCommand("gamemode survival @a");
			game.getConnection().waitForChunksDownload();
			context.waitTicks(15);
			server.runOnServer(s -> {
				ServerLevel level = level3(s);
				Level3Bases.populateNear(level, seed, player(s));
				AABB box = new AABB(player(s).blockPosition()).inflate(24);
				if (kind == HallKind.BASE) {
					var staff = level.getEntitiesOfClass(Person.class, box, p -> p.entityTags().contains(Level3Bases.STAFF_TAG));
					check(staff.size() >= 2, "Base Gamma missing staff");
					check(staff.stream().anyMatch(p -> ShopRole.of(p) == ShopRole.QUARTERMASTER), "Base Gamma missing Quartermaster");
					BlockPos cot = null;
					for (int dx = -16; dx <= 16 && cot == null; dx++) {
						for (int dz = -16; dz <= 16 && cot == null; dz++) {
							BlockPos p = player(s).blockPosition().offset(dx, 0, dz);
							if (level.getBlockState(p).is(Level3Blocks.COT)) cot = p;
						}
					}
					check(cot != null, "Base Gamma missing cot");
					level.getBlockState(cot).useWithoutItem(level, player(s),
							new net.minecraft.world.phys.BlockHitResult(Vec3.atCenterOf(cot), net.minecraft.core.Direction.UP, cot, false));
					check(player(s).getRespawnConfig() != null && player(s).getRespawnConfig().respawnData().dimension() == Level3.LEVEL_3,
							"cot did not set Level 3 respawn");
				} else if (kind == HallKind.BNTG_STORAGE) {
					var staff = level.getEntitiesOfClass(Person.class, box, p -> p.entityTags().contains(Level3Bases.STAFF_TAG));
					check(staff.stream().anyMatch(p -> ShopRole.of(p) == ShopRole.BUYER), "B.N.T.G. Storage missing Buyer");
				} else {
					var ops = level.getEntitiesOfClass(Pillager.class, box, p -> p.entityTags().contains(Level3Bases.UEC_TAG));
					check(ops.size() >= 2, "Scylla Colony missing U.E.C. operatives");
				}
			});
			server.runCommand("gamemode spectator @a");
		}
		server.runCommand("kill @e[tag=" + Level3Bases.UEC_TAG + "]");
	}

	/**
	 * Base Gamma: up to the gate from the maze, turned back at the checkpoint while armed and let through without
	 * weapons, settling in a room, everyone at their place, the hot spring, and a look round every part.
	 */
	private static void gamma(ClientGameTestContext context, TestSingleplayerContext game, long seed, BlockPos from) {
		var server = game.getServer();
		int[] gate = GammaPlan.nearestGate(seed, from.getX(), from.getZ());
		check(gate != null, "no Base Gamma within reach of " + from);
		int x0 = gate[0] - GammaLayout.GATE_U, z0 = gate[1];
		int gx = gate[0];
		shoot(context, game, gx + 1.0, STAND, z0 - 4.5, 0, 5, "level3_gamma_gate");
		server.runOnServer(s -> {
			ServerLevel level = level3(s);
			for (int x = x0 - 2; x < x0 + GammaLayout.W + 2; x += 3) {
				for (int z = z0 - 2; z < z0 + 14; z++) {
					for (int y = 1; y < Level3ChunkGenerator.HEIGHT - 1; y += 2) {
						BlockPos pos = new BlockPos(x, y, z);
						if (!level.isLoaded(pos)) continue;
						check(level.getBlockState(pos).is(Level3ChunkGenerator.blockAt(seed, x, y, z).getBlock()), "Gamma differs from its template at " + pos);
					}
				}
			}
		});

		// The checkpoint: an iron sword sets the detector off; without it, the guards let you in.
		BlockPos detector = new BlockPos(gx, GammaLayout.RESIDENTIAL + 1, z0 + 9);
		server.runCommand(String.format(Locale.ROOT, "execute as @a in noclip_backrooms:level_3 run tp @s %.1f %d %.1f 0 0", gx + 0.5, GammaLayout.RESIDENTIAL + 1, z0 + 7.5));
		server.runCommand("gamemode survival @a");
		server.runCommand("clear @a");
		server.runCommand("give @a minecraft:iron_sword");
		game.getConnection().waitForChunksDownload();
		context.waitTicks(10);
		server.runOnServer(s -> {
			ServerLevel level = level3(s);
			check(level.getBlockState(detector).is(Level3Blocks.CHECKPOINT_DOOR), "no metal detector at " + detector);
			use(level, player(s), detector);
			check(player(s).getZ() < z0 + 9, "let through with a sword");
		});
		server.runCommand("clear @a");
		server.runOnServer(s -> {
			use(level3(s), player(s), detector);
			check(player(s).getZ() > z0 + 10, "not let through without weapons: " + player(s).position());
			use(level3(s), player(s), detector.above());
			check(player(s).getZ() < z0 + 9, "not let back out");
			use(level3(s), player(s), detector);
			// No digging round the detector, nor through the outer wall.
			for (BlockPos wall : new BlockPos[] {detector.west(), new BlockPos(x0, GammaLayout.RESIDENTIAL + 1, z0 + 5)}) {
				var before = level3(s).getBlockState(wall);
				check(!before.isAir(), "no wall at " + wall);
				player(s).gameMode.destroyBlock(wall);
				check(level3(s).getBlockState(wall) == before, "dug through Gamma at " + wall);
			}
		});
		server.runCommand("gamemode spectator @a");
		shoot(context, game, gx + 1.0, GammaLayout.RESIDENTIAL + 1.6, z0 + 3.5, 0, 10, "level3_gamma_checkpoint");

		// Everyone at their place, once only however often it is asked.
		shoot(context, game, x0 + 27.5, GammaLayout.RESIDENTIAL + 1, z0 + 12.5, 0, 5, "level3_gamma_street");
		server.runOnServer(s -> {
			ServerLevel level = level3(s);
			Level3Bases.populateNear(level, seed, player(s));
			// Someone pushed away from their place is still the one there: no second one comes.
			AABB all = new AABB(x0, 0, z0, x0 + GammaLayout.W, Level3ChunkGenerator.HEIGHT, z0 + GammaLayout.D);
			Person moved = level.getEntitiesOfClass(Person.class, all, p -> p.entityTags().contains("noclip_backrooms_gamma_resident_north")).getFirst();
			moved.teleportTo(moved.getX() + 5, moved.getY(), moved.getZ());
			Level3Bases.populateNear(level, seed, player(s));
			AABB box = new AABB(x0, 0, z0, x0 + GammaLayout.W, Level3ChunkGenerator.HEIGHT, z0 + GammaLayout.D);
			for (GammaLayout.Spot spot : GammaLayout.PEOPLE) {
				String tag = "noclip_backrooms_gamma_" + spot.id();
				int count = level.getEntitiesOfClass(Person.class, box, p -> p.entityTags().contains(tag)).size();
				check(count == 1, spot.id() + ": " + count + " people");
			}
			check(level.getEntitiesOfClass(Person.class, box, p -> ShopRole.of(p) == ShopRole.MASON).size() == 1, "no Mason");
		});

		// Settling in a room: a cot sets the respawn and offers the way home.
		int[] cot = find(GammaLayout.Thing.COT);
		server.runCommand(String.format(Locale.ROOT, "execute as @a in noclip_backrooms:level_3 run tp @s %.1f %d %.1f", x0 + cot[0] + 1.5, cot[1], z0 + cot[2] + 0.5));
		server.runCommand("gamemode survival @a");
		game.getConnection().waitForChunksDownload();
		context.waitTicks(5);
		server.runOnServer(s -> {
			BlockPos at = new BlockPos(x0 + cot[0], cot[1], z0 + cot[2]);
			check(level3(s).getBlockState(at).is(Level3Blocks.COT), "no cot at " + at);
			use(level3(s), player(s), at);
			check(dev.noclip.backrooms.core.trip.Trips.settled(player(s)), "a Gamma cot did not settle the player");
		});

		// The hot spring settles the mind.
		server.runCommand(String.format(Locale.ROOT, "execute as @a in noclip_backrooms:level_3 run tp @s %.1f %d %.1f",
				x0 + GammaLayout.SPRING_U0 + 2.5, GammaLayout.MALL_1, z0 + GammaLayout.SPRING_V0 + 2.5));
		game.getConnection().waitForChunksDownload();
		server.runOnServer(s -> dev.noclip.backrooms.core.rpg.PlayerRpg.drainSanity(player(s), 1000));
		float low = server.computeOnServer(s -> dev.noclip.backrooms.core.rpg.PlayerRpg.get(player(s)).sanity());
		context.waitTicks(80);
		server.runOnServer(s -> check(player(s).isInWater(), "not in the hot spring"));
		float after = server.computeOnServer(s -> dev.noclip.backrooms.core.rpg.PlayerRpg.get(player(s)).sanity());
		check(after > low + 2, "the hot spring did nothing: " + low + " -> " + after);

		// The way out to Level 11 and the elevator are there.
		int[] exit = find(GammaLayout.Thing.EXIT_DOOR), lift = find(GammaLayout.Thing.ELEVATOR);
		server.runOnServer(s -> {
			check(level3(s).getBlockState(new BlockPos(x0 + exit[0], exit[1], z0 + exit[2])).is(Level3Blocks.BASE_GAMMA_DOOR), "no door to Level 11");
			check(level3(s).getBlockState(new BlockPos(x0 + lift[0], lift[1], z0 + lift[2])).is(Level3Blocks.ELEVATOR), "no elevator");
		});

		server.runCommand("gamemode spectator @a");
		shoot(context, game, x0 + 12.5, GammaLayout.MALL_3 + 2.6, z0 + 24.5, -90, 30, "level3_gamma_atrium");
		shoot(context, game, x0 + 8.5, GammaLayout.MALL_1 + 2.6, z0 + 10.5, -60, 10, "level3_gamma_mall");
		shoot(context, game, x0 + 23.5, GammaLayout.MALL_1 + 1.6, z0 + 16.5, 0, -70, "level3_gamma_daylight");
		shoot(context, game, x0 + 24.5, GammaLayout.MALL_1 + 3.6, z0 + 28.5, 180, 25, "level3_gamma_spring");
		shoot(context, game, x0 + 9.5, GammaLayout.MALL_2 + 1.6, z0 + 15.5, 180, 5, "level3_gamma_old_quarter");
		shoot(context, game, x0 + 53.5, GammaLayout.MALL_1 + 1.6, z0 + 14.5, 180, 5, "level3_gamma_storage");
		shoot(context, game, x0 + 17.5, GammaLayout.TUNNEL + 1.6, z0 + 40.5, 90, 5, "level3_gamma_tunnel");
		shoot(context, game, x0 + 40.5, GammaLayout.ADMIN + 1.6, z0 + 43.5, 90, 5, "level3_gamma_admin_door");
		shoot(context, game, x0 + 18.5, GammaLayout.RESIDENTIAL + 1.6, z0 + 16.5, 90, 5, "level3_gamma_room");
	}

	/** The first block of something in Gamma's template, as {u, y, v}. */
	private static int[] find(GammaLayout.Thing thing) {
		for (int y = 0; y < GammaLayout.Y; y++) {
			for (int v = 0; v < GammaLayout.D; v++) {
				for (int u = 0; u < GammaLayout.W; u++) {
					GammaLayout.Bit bit = GammaLayout.at(u, y, v);
					if (bit.thing() == thing && bit.part() == 0) return new int[] {u, y, v};
				}
			}
		}
		throw new AssertionError("no " + thing + " in Gamma");
	}

	private static void use(ServerLevel level, ServerPlayer player, BlockPos pos) {
		level.getBlockState(pos).useWithoutItem(level, player, new net.minecraft.world.phys.BlockHitResult(Vec3.atCenterOf(pos), net.minecraft.core.Direction.UP, pos, false));
	}

	private static void checkBlocks(TestSingleplayerContext game) {
		game.getServer().runOnServer(s -> {
			ServerLevel level = level3(s);
			for (Block block : Level3Blocks.ALL) {
				check(Language.getInstance().has(block.getDescriptionId()), "no name for " + block);
				if (block.getLootTable().isEmpty()) continue;
				var drops = Block.getDrops(block.defaultBlockState(), level, new BlockPos(3, 3, 3), null);
				check(drops.size() == 1 && drops.getFirst().is(block.asItem()), block + " does not drop itself");
			}
		});
	}

	private static void checkGenerated(ServerLevel level, long seed, BlockPos around) {
		for (int x = around.getX() - 24; x < around.getX() + 24; x++) {
			for (int z = around.getZ() - 24; z < around.getZ() + 24; z++) {
				for (int y : new int[] {0, Level3ChunkGenerator.FLOOR_Y, STAND, STAND + 1, STAND + 2, STAND + 4, Level3ChunkGenerator.ROOF_Y}) {
					BlockState expected = Level3ChunkGenerator.blockAt(seed, x, y, z);
					check(level.getBlockState(new BlockPos(x, y, z)).is(expected.getBlock()), "world differs from generator at " + x + ", " + y + ", " + z);
				}
			}
		}
	}

	private static void standOn(ClientGameTestContext context, TestSingleplayerContext game, int[] at) {
		var server = game.getServer();
		server.runCommand(String.format(Locale.ROOT, "execute as @a in noclip_backrooms:level_3 run tp @s %.1f %d %.1f", at[0] + 0.5, STAND, at[1] + 0.5));
		server.runCommand("gamemode survival @a");
		heal(game);
		game.getConnection().waitForChunksDownload();
		context.waitTicks(5);
	}

	private static void heal(TestSingleplayerContext game) {
		game.getServer().runOnServer(s -> {
			player(s).setHealth(player(s).getMaxHealth());
			player(s).removeAllEffects();
		});
	}

	private static void useBlockFrom(ClientGameTestContext context, TestSingleplayerContext game, int[] at, int out, double distance) {
		var server = game.getServer();
		float yaw = yaw(Level3Plan.opposite(out));
		server.runCommand(String.format(Locale.ROOT, "execute as @a in noclip_backrooms:level_3 run tp @s %.2f %d %.2f %.1f 20",
				at[0] + 0.5 + Level3Plan.DX[out] * distance, STAND, at[1] + 0.5 + Level3Plan.DZ[out] * distance, yaw));
		server.runCommand("gamemode survival @a");
		game.getConnection().waitForChunksDownload();
		context.waitTicks(10);
		context.getInput().lookAt(yaw, 20);
		context.waitTicks(3);
		context.getInput().pressMouse(1);
		context.waitTicks(10);
	}

	private interface Where {
		boolean test(int x, int z);
	}

	private static int[] nearest(long seed, BlockPos from, Predicate<Tile> wanted, Where where) {
		int x0 = from.getX(), z0 = from.getZ();
		for (int ring = 0; ring < 900; ring++) {
			for (int dx = -ring; dx <= ring; dx++) {
				for (int dz = -ring; dz <= ring; dz++) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) continue;
					int x = x0 + dx, z = z0 + dz;
					if (wanted.test(Level3Layout.tile(seed, x, z)) && where.test(x, z)) return new int[] {x, z};
				}
			}
		}
		throw new AssertionError("nothing like that within 900 blocks");
	}

	private static float yaw(int dir) {
		return (float) Math.toDegrees(Math.atan2(-Level3Plan.DX[dir], Level3Plan.DZ[dir]));
	}

	private static void shoot(ClientGameTestContext context, TestSingleplayerContext game, double x, double y, double z, float yaw, float pitch, String name) {
		game.getServer().runCommand(String.format(Locale.ROOT, "execute as @a in noclip_backrooms:level_3 run tp @s %.2f %.2f %.2f %.1f %.1f", x, y, z, yaw, pitch));
		game.getConnection().waitForChunksDownload();
		context.waitTicks(25);
		game.getConnection().waitForChunksRender();
		context.runOnClient(client -> client.gui.toastManager().clear());
		context.waitTick();
		context.takeScreenshot(name);
	}

	private static ServerLevel level3(MinecraftServer server) {
		return server.getLevel(Level3.LEVEL_3);
	}

	private static ServerPlayer player(MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}

	private static void check(boolean condition, String message) {
		if (!condition) throw new AssertionError(message);
	}
}
