package dev.noclip.backrooms.level4;

import dev.noclip.backrooms.TestFilter;
import dev.noclip.backrooms.core.LevelExits;
import dev.noclip.backrooms.core.economy.Economy;
import dev.noclip.backrooms.core.people.Person;
import dev.noclip.backrooms.level1.Level1Items;
import dev.noclip.backrooms.level4.Level4Layout.Kind;
import dev.noclip.backrooms.level4.Level4Layout.Tile;
import dev.noclip.backrooms.level4.Level4Plan.PlacedExit;
import dev.noclip.backrooms.level4.Level4Plan.ZoneKind;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.locale.Language;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Plays Level 4 in a real client:
 * - Enter from Level 2 ("4")
 * - Views of open office, rainy double-height atrium, M.E.G. Base Omega, Amor Incrementum, T.B.D., dark wing
 * - Water cooler & fountain capped at 2 Almond Water bottles
 * - Three faction interactions (Omega medic/cot, Amor hostility -> pacified by Brass Crucifix, T.B.D. curio trade)
 * - Entity 2 trap window damage + painting it black
 * - Wild vanishing exit disappearing after losing sight
 * - Dark wing breaker restoring broken lights
 */
public final class Level4ClientTest implements FabricClientGameTest {
	private static final int STAND = Level4ChunkGenerator.FLOOR_Y + 1;

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext game = context.worldBuilder().create()) {
			var server = game.getServer();
			game.getConnection().waitForChunksRender();
			server.runCommand("gamerule send_command_feedback false");
			server.runCommand("gamerule noclip_backrooms:edge_shift false");
			server.runCommand("gamemode survival @a");
			server.runOnServer(s -> {
				Level4Monsters.holdOff(s, 20 * 60 * 20);
				Level4Wanderers.holdOff(s, 20 * 60 * 20);
				Level4VanishingExits.holdOff(s, 20 * 60 * 20);
			});

			enterFromLevel2(context, game);
			long seed = server.computeOnServer(s -> Level4ChunkGenerator.planSeed(level4(s).getChunkSource().randomState()));
			BlockPos arrival = server.computeOnServer(s -> player(s).blockPosition());
			server.runOnServer(s -> {
				Tile tile = Level4Layout.tile(seed, arrival.getX(), arrival.getZ());
				check(tile.kind() == Kind.AIR, "arrived inside non-air at " + arrival);
				check(!player(s).isInWall(), "arrived inside a wall");
			});

			server.runCommand("gamemode spectator @a");
			if (TestFilter.section("views")) views(context, game, seed, arrival);
			if (TestFilter.section("water")) waterSources(context, game, seed, arrival);
			if (TestFilter.section("factions")) factions(context, game, seed, arrival);
			if (TestFilter.section("windows")) trapWindows(context, game, seed, arrival);
			if (TestFilter.section("vanish")) vanishingExits(context, game, seed, arrival);
			if (TestFilter.section("wanderers")) wanderers(context, game, seed, arrival);
			if (TestFilter.section("extras")) extras(context, game, seed, arrival);
			if (TestFilter.section("blocks")) checkBlocks(game);
		}
	}

	private static void enterFromLevel2(ClientGameTestContext context, TestSingleplayerContext game) {
		var server = game.getServer();
		server.runCommand("execute in noclip_backrooms:level_2 run tp @a 2.5 33 2.5");
		game.getConnection().waitForChunksDownload();
		context.waitTicks(20);
		server.runOnServer(s -> check(LevelExits.isOpen("4"), "Level 4 did not open its way in"));
		server.runOnServer(s -> LevelExits.use(player(s), "4"));
		server.waitFor(s -> player(s).level().dimension() == Level4.LEVEL_4, 100);
		game.getConnection().waitForChunksDownload();
		context.waitTicks(30);
		game.getConnection().waitForChunksRender();
	}

	private static void views(ClientGameTestContext context, TestSingleplayerContext game, long seed, BlockPos from) {
		shoot(context, game, from.getX() + 0.5, STAND, from.getZ() + 0.5, 25, 4, false, "level4_open_office");

		int[] atrium = findZone(seed, from, ZoneKind.ATRIUM);
		if (atrium != null) shoot(context, game, atrium[0] + 4.5, STAND, atrium[1] + 4.5, 135, 3, false, "level4_atrium");
		int[] omega = findZone(seed, from, ZoneKind.OMEGA_BASE);
		if (omega != null) shoot(context, game, omega[0] + 0.5, STAND, omega[1] + 2.2, 165, 6, true, "level4_omega_base");
		int[] amor = findZone(seed, from, ZoneKind.AMOR_CAMP);
		if (amor != null) shoot(context, game, amor[0] + 0.8, STAND, amor[1] + 1.8, 135, 10, true, "level4_amor_camp");
		int[] tbd = findZone(seed, from, ZoneKind.TBD_ROOM);
		if (tbd != null) shoot(context, game, tbd[0] + 0.5, STAND, tbd[1] + 2.2, 180, 6, true, "level4_tbd_room");
		int[] dark = findZone(seed, from, ZoneKind.DARK_WING);
		if (dark != null) shoot(context, game, dark[0] + 0.5, STAND, dark[1] + 0.5, -35, 4, false, "level4_dark_wing");

		int[] elev = findExitView(seed, from, Level4Exit.ELEVATOR_3);
		if (elev != null) shoot(context, game, elev[2] + 0.5, STAND, elev[3] + 0.5, elev[4], 5, false, "level4_elevator");
		int[] stairs5 = findExitView(seed, from, Level4Exit.OMEGA_STAIRS_5);
		if (stairs5 != null) shoot(context, game, stairs5[2] + 0.5, STAND, stairs5[3] + 0.5, stairs5[4], -8, false, "level4_stairwell");
		int[] base71 = findExitView(seed, from, Level4Exit.BASEMENT_71);
		if (base71 != null) shoot(context, game, base71[2] + 0.5, STAND, base71[3] + 0.5, base71[4], 18, false, "level4_basement");

		int[] rainView = findRainWindowView(seed, from);
		if (rainView != null) shoot(context, game, rainView[0] + 0.5, STAND, rainView[1] + 0.5, rainView[2], 2, false, "level4_rain_window_view");
	}

	private static void waterSources(ClientGameTestContext context, TestSingleplayerContext game, long seed, BlockPos from) {
		var server = game.getServer();
		int[] cooler = findKind(seed, from, Kind.WATER_COOLER);
		check(cooler != null, "no water cooler found near arrival");
		server.runCommand("gamemode survival @a");
		server.runOnServer(s -> {
			ServerPlayer p = player(s);
			p.teleportTo(cooler[0] + 0.5, STAND, cooler[1] + 1.5);
			p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.GLASS_BOTTLE, 4));
		});
		game.getConnection().waitForChunksDownload();
		context.waitTicks(10);
		server.runOnServer(s -> {
			ServerPlayer p = player(s);
			BlockPos pos = new BlockPos(cooler[0], STAND, cooler[1]);
			BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.SOUTH, pos, false);
			p.gameMode.useItemOn(p, level4(s), p.getMainHandItem(), InteractionHand.MAIN_HAND, hit);
			p.gameMode.useItemOn(p, level4(s), p.getMainHandItem(), InteractionHand.MAIN_HAND, hit);
			p.gameMode.useItemOn(p, level4(s), p.getMainHandItem(), InteractionHand.MAIN_HAND, hit);
			check(level4(s).getBlockState(pos).getValue(Level4WaterSourceBlock.BOTTLES) == 0, "cooler should empty after 2 bottles");
			int almondCount = 0;
			for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
				ItemStack st = p.getInventory().getItem(i);
				if (st.is(Level1Items.ALMOND_WATER)) almondCount += st.getCount();
			}
			check(almondCount == 2, "expected exactly 2 Almond Water bottles, got " + almondCount);
		});
		server.runCommand("gamemode spectator @a");
	}

	private static void factions(ClientGameTestContext context, TestSingleplayerContext game, long seed, BlockPos from) {
		var server = game.getServer();
		// 1. M.E.G. Base Omega: 10 personnel, >= 4 active guards that fight monsters
		int[] omega = findZone(seed, from, ZoneKind.OMEGA_BASE);
		check(omega != null, "no M.E.G. Base Omega found");
		server.runCommand("gamemode survival @a");
		server.runOnServer(s -> player(s).teleportTo(omega[0] + 0.5, STAND, omega[1] + 0.5));
		game.getConnection().waitForChunksDownload();
		context.waitTicks(20);
		server.runOnServer(s -> {
			ServerPlayer p = player(s);
			Level4Bases.populateNear(level4(s), seed, p);
			var staff = level4(s).getEntitiesOfClass(Person.class, new AABB(p.blockPosition()).inflate(28),
					e -> e.entityTags().contains(Level4Bases.STAFF_TAG));
			check(staff.size() == 10, "expected 10 Omega personnel, got " + staff.size());
			var guards = level4(s).getEntitiesOfClass(Person.class, new AABB(p.blockPosition()).inflate(28),
					e -> e.entityTags().contains(Level4Bases.OMEGA_GUARD_TAG));
			check(guards.size() >= 4, "expected >= 4 Omega guards, got " + guards.size());
			Person guard = guards.getFirst();
			var hound = dev.noclip.backrooms.monsters.Monsters.HOUND.create(level4(s), net.minecraft.world.entity.EntitySpawnReason.NATURAL);
			check(hound != null, "failed to create test hound");
			hound.snapTo(guard.getX() + 3.0, STAND, guard.getZ(), 0, 0);
			hound.addTag("noclip_backrooms_level4_guard_test_hound");
			level4(s).addFreshEntity(hound);
		});
		context.waitTicks(60);
		server.runOnServer(s -> {
			var hounds = level4(s).getEntities(dev.noclip.backrooms.monsters.Monsters.HOUND,
					new AABB(player(s).blockPosition()).inflate(32),
					e -> e.entityTags().contains("noclip_backrooms_level4_guard_test_hound"));
			boolean damagedOrDead = hounds.isEmpty() || hounds.getFirst().getHealth() < hounds.getFirst().getMaxHealth();
			check(damagedOrDead, "Omega guard did not engage nearby monster");
			hounds.forEach(net.minecraft.world.entity.Entity::discard);
		});

		// 2. Amor Incrementum: 10 fightable members, hostile without relic, trade + direct relic offering
		int[] amor = findZone(seed, from, ZoneKind.AMOR_CAMP);
		check(amor != null, "no Amor Incrementum camp found");
		server.runOnServer(s -> {
			ServerPlayer p = player(s);
			p.setHealth(p.getMaxHealth());
			p.teleportTo(amor[0] + 0.5, STAND, amor[1] + 0.5);
		});
		game.getConnection().waitForChunksDownload();
		context.waitTicks(20);
		server.runOnServer(s -> {
			ServerPlayer p = player(s);
			Level4Bases.populateNear(level4(s), seed, p);
			var cultists = level4(s).getEntitiesOfClass(Person.class, new AABB(p.blockPosition()).inflate(18),
					e -> e.entityTags().contains(Level4Bases.AMOR_TAG));
			check(cultists.size() == 10, "expected 10 Amor members, got " + cultists.size());
			check(!cultists.getFirst().isInvulnerable(), "Amor members should be fightable");
		});
		float beforeHp = server.computeOnServer(s -> player(s).getHealth());
		context.waitTicks(65);
		float afterHp = server.computeOnServer(s -> player(s).getHealth());
		check(afterHp < beforeHp, "Amor Incrementum did not attack outsider without religious relic");

		server.runOnServer(s -> {
			ServerPlayer p = player(s);
			p.getInventory().clearContent();
			p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Level4Items.BRASS_CRUCIFIX));
			p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Economy.LOST_COIN, 2));
			var cultists = level4(s).getEntitiesOfClass(Person.class, new AABB(p.blockPosition()).inflate(18),
					e -> e.entityTags().contains(Level4Bases.AMOR_TAG));
			check(!cultists.isEmpty(), "expected Amor cultist nearby");
			var res = net.fabricmc.fabric.api.event.player.UseEntityCallback.EVENT.invoker()
					.interact(p, level4(s), InteractionHand.MAIN_HAND, cultists.getFirst(), null);
			check(res == net.minecraft.world.InteractionResult.SUCCESS, "expected Amor interact to succeed");
		});
		context.waitTicks(10);
		server.runOnServer(s -> {
			ServerPlayer p = player(s);
			check(p.getOffhandItem().getCount() == 1, "expected Amor trade to consume 1 lost_coin");
			p.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
			p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Level4Items.FADED_ICON_FRAME));
			var cultists = level4(s).getEntitiesOfClass(Person.class, new AABB(p.blockPosition()).inflate(18),
					e -> e.entityTags().contains(Level4Bases.AMOR_TAG));
			var offerRes = net.fabricmc.fabric.api.event.player.UseEntityCallback.EVENT.invoker()
					.interact(p, level4(s), InteractionHand.MAIN_HAND, cultists.getFirst(), null);
			check(offerRes == net.minecraft.world.InteractionResult.SUCCESS, "expected direct relic offer to succeed");
			check(!p.getMainHandItem().is(Level4Items.FADED_ICON_FRAME), "expected direct relic offer to consume faded_icon_frame");
		});

		// 3. T.B.D.: 6 members, refuse hoard crate, only sell useless curios
		int[] tbd = findZone(seed, from, ZoneKind.TBD_ROOM);
		check(tbd != null, "no T.B.D. room found");
		server.runOnServer(s -> player(s).teleportTo(tbd[0] + 0.5, STAND, tbd[1] + 0.5));
		game.getConnection().waitForChunksDownload();
		context.waitTicks(20);
		server.runOnServer(s -> {
			ServerPlayer p = player(s);
			Level4Bases.populateNear(level4(s), seed, p);
			var members = level4(s).getEntitiesOfClass(Person.class, new AABB(p.blockPosition()).inflate(18),
					e -> e.entityTags().contains(Level4Bases.TBD_TAG));
			check(members.size() == 6, "expected 6 T.B.D. members, got " + members.size());
			int[] crate = findKind(seed, p.blockPosition(), Kind.TBD_HOARD);
			check(crate != null, "expected T.B.D. hoard crate");
			BlockPos cratePos = new BlockPos(crate[0], STAND, crate[1]);
			BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(cratePos), Direction.SOUTH, cratePos, false);
			var crateRes = p.gameMode.useItemOn(p, level4(s), ItemStack.EMPTY, InteractionHand.MAIN_HAND, hit);
			check(crateRes == net.minecraft.world.InteractionResult.SUCCESS, "expected hoard crate interact");

			p.getInventory().clearContent();
			p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Economy.LOST_COIN, 2));
			var buyRes = net.fabricmc.fabric.api.event.player.UseEntityCallback.EVENT.invoker()
					.interact(p, level4(s), InteractionHand.MAIN_HAND, members.getFirst(), null);
			check(buyRes == net.minecraft.world.InteractionResult.SUCCESS, "expected T.B.D. trade to succeed");
			check(p.getMainHandItem().getCount() == 1, "expected T.B.D. trade to consume 1 lost_coin");
			boolean gotCurio = false;
			for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
				ItemStack st = p.getInventory().getItem(i);
				if (!st.isEmpty() && Level4Items.CURIOS.contains(st.getItem())) gotCurio = true;
			}
			check(gotCurio, "expected T.B.D. to sell only useless curios");
		});
		server.runCommand("gamemode spectator @a");
	}

	private static void trapWindows(ClientGameTestContext context, TestSingleplayerContext game, long seed, BlockPos from) {
		var server = game.getServer();
		server.runOnServer(s -> {
			check(Level4Windows.isTrapWindow(Level4Blocks.OFFICE_TRAP_WINDOW.defaultBlockState()), "trap window should be trap");
			check(!Level4Windows.isTrapWindow(Level4Blocks.OFFICE_RAIN_WINDOW.defaultBlockState()), "rain window should be safe");
			check(!Level4Windows.isTrapWindow(Level4Blocks.OFFICE_BLACKED_WINDOW.defaultBlockState()), "blacked window should be safe");
		});

		int[] win = findKind(seed, from, Kind.WINDOW_TRAP);
		check(win != null, "no OFFICE_TRAP_WINDOW found");
		int[] trapStand = indoorStandNextTo(seed, win[0], win[1]);
		double standX = trapStand != null ? trapStand[0] + 0.5 : win[0] + 0.5;
		double standZ = trapStand != null ? trapStand[1] + 0.5 : win[1] + 1.8;
		server.runCommand("gamemode survival @a");
		server.runOnServer(s -> {
			ServerPlayer p = player(s);
			p.setHealth(p.getMaxHealth());
			p.teleportTo(standX, STAND, standZ);
			p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
			double dx = win[0] + 0.5 - p.getX(), dz = win[1] + 0.5 - p.getZ();
			float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
			p.setYRot(yaw);
			p.setYHeadRot(yaw);
		});
		game.getConnection().waitForChunksDownload();
		context.waitTicks(12);
		game.getConnection().waitForChunksRender();
		context.takeScreenshot("level4_trap_window");

		server.runOnServer(s -> {
			ServerPlayer p = player(s);
			BlockPos pos = new BlockPos(win[0], STAND + 1, win[1]);
			check(level4(s).getBlockState(pos).is(Level4Blocks.OFFICE_TRAP_WINDOW), "expected trap window at " + pos);
			float hpBefore = p.getHealth();
			BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.SOUTH, pos, false);
			p.gameMode.useItemOn(p, level4(s), ItemStack.EMPTY, InteractionHand.MAIN_HAND, hit);
			check(p.getHealth() < hpBefore, "expected empty-hand touch on trap window to hurt player");

			p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.INK_SAC));
			p.gameMode.useItemOn(p, level4(s), p.getMainHandItem(), InteractionHand.MAIN_HAND, hit);
			check(level4(s).getBlockState(pos).is(Level4Blocks.OFFICE_BLACKED_WINDOW), "trap window was not painted black");
		});

		// Verify open rain window is safe to view empty-handed, yet still paintable with Charcoal
		int[] rainWin = findKind(seed, from, Kind.WINDOW_RAIN);
		check(rainWin != null, "no OFFICE_RAIN_WINDOW found");
		int[] rainStand = indoorStandNextTo(seed, rainWin[0], rainWin[1]);
		double rStandX = rainStand != null ? rainStand[0] + 0.5 : rainWin[0] + 0.5;
		double rStandZ = rainStand != null ? rainStand[1] + 0.5 : rainWin[1] + 1.8;
		server.runOnServer(s -> {
			ServerPlayer p = player(s);
			p.setHealth(p.getMaxHealth());
			p.teleportTo(rStandX, STAND, rStandZ);
			p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		});
		game.getConnection().waitForChunksDownload();
		context.waitTicks(12);
		server.runOnServer(s -> {
			ServerPlayer p = player(s);
			BlockPos pos = new BlockPos(rainWin[0], STAND + 1, rainWin[1]);
			if (level4(s).getBlockState(pos).is(Level4Blocks.OFFICE_RAIN_WINDOW)) {
				float hpBefore = p.getHealth();
				BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.SOUTH, pos, false);
				p.gameMode.useItemOn(p, level4(s), ItemStack.EMPTY, InteractionHand.MAIN_HAND, hit);
				check(p.getHealth() == hpBefore, "expected empty-hand touch on rain window to be safe");

				p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.CHARCOAL));
				p.gameMode.useItemOn(p, level4(s), p.getMainHandItem(), InteractionHand.MAIN_HAND, hit);
				check(level4(s).getBlockState(pos).is(Level4Blocks.OFFICE_BLACKED_WINDOW), "rain window was not painted black");
			}
		});
		server.runCommand("gamemode spectator @a");
	}

	private static void vanishingExits(ClientGameTestContext context, TestSingleplayerContext game, long seed, BlockPos from) {
		var server = game.getServer();
		int[] found = findVanishingExit(seed, from);
		check(found != null, "no vanishing exit found near arrival");
		server.runOnServer(s -> {
			Level4VanishingExits.setChanceForTest(100);
			Level4VanishingExits.holdOff(s, 0);
		});
		server.runCommand("gamemode survival @a");
		BlockPos exitPos = new BlockPos(found[0], found[1], found[2]);
		server.runOnServer(s -> player(s).teleportTo(found[3] + 0.5, STAND, found[4] + 0.5));
		game.getConnection().waitForChunksDownload();
		// Look at exit
		server.runCommand("rotate @p " + found[5] + " 0");
		context.waitTicks(20);
		// Turn away for > 4 seconds
		server.runCommand("rotate @p " + ((found[5] + 180) % 360) + " 0");
		context.waitTicks(110);
		server.runOnServer(s -> {
			check(!(level4(s).getBlockState(exitPos).getBlock() instanceof Level4ExitBlock),
					"wild vanishing exit did not vanish after looking away at " + exitPos);
			Level4VanishingExits.setChanceForTest(55);
		});
		server.runCommand("gamemode spectator @a");
	}

	private static void wanderers(ClientGameTestContext context, TestSingleplayerContext game, long seed, BlockPos from) {
		var server = game.getServer();
		server.runCommand("gamemode survival @a");
		server.runOnServer(s -> {
			ServerPlayer p = player(s);
			p.teleportTo(from.getX() + 0.5, STAND, from.getZ() + 0.5);
		});
		game.getConnection().waitForChunksDownload();
		context.waitTicks(15);

		java.util.UUID[] firstId = new java.util.UUID[1];
		Vec3[] startPos = new Vec3[1];
		server.runOnServer(s -> {
			ServerPlayer p = player(s);
			int count = Level4Wanderers.spawnNear(level4(s), seed, p, 3);
			check(count >= 1, "expected at least 1 roaming wanderer to spawn near arrival");
			var list = level4(s).getEntitiesOfClass(Person.class, new AABB(p.blockPosition()).inflate(48),
					e -> e.entityTags().contains(Level4Wanderers.TAG));
			check(!list.isEmpty(), "expected spawned wanderer with tag");
			Person w = list.getFirst();
			firstId[0] = w.getUUID();
			startPos[0] = w.position();
			Tile tile = Level4Layout.tile(seed, w.getBlockX(), w.getBlockZ());
			check(Level4Wanderers.isWanderZone(tile.zone()), "wanderer spawned outside wander zone: " + tile.zone());
			p.teleportTo(w.getX() + 3.0, STAND, w.getZ() + 3.0);
		});
		game.getConnection().waitForChunksDownload();
		server.runOnServer(s -> {
			Person w = (Person) level4(s).getEntity(firstId[0]);
			ServerPlayer p = player(s);
			double dx = w.getX() - p.getX(), dz = w.getZ() - p.getZ();
			float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
			p.setYRot(yaw);
			p.setYHeadRot(yaw);
		});
		context.waitTicks(12);
		game.getConnection().waitForChunksRender();
		context.takeScreenshot("level4_wanderers");

		// Move player away (>4.5 blocks) so wanderer is not paused facing player, then wait for it to stroll
		server.runOnServer(s -> player(s).teleportTo(from.getX() + 0.5, STAND, from.getZ() + 0.5));
		context.waitTicks(140);
		server.runOnServer(s -> {
			Person w = (Person) level4(s).getEntity(firstId[0]);
			check(w != null && w.isAlive(), "wanderer disappeared prematurely");
			double moved = w.position().subtract(startPos[0]).horizontalDistance();
			check(moved > 0.3, "expected wanderer to stroll at least 0.3 blocks, moved=" + moved);

			// Test chat & barter interaction
			ServerPlayer p = player(s);
			p.teleportTo(w.getX() + 1.2, STAND, w.getZ() + 1.2);
			p.getInventory().clearContent();
			p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
			var chatRes = net.fabricmc.fabric.api.event.player.UseEntityCallback.EVENT.invoker()
					.interact(p, level4(s), InteractionHand.MAIN_HAND, w, null);
			check(chatRes == net.minecraft.world.InteractionResult.SUCCESS, "expected wanderer chat to succeed");

			p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Economy.LOST_COIN, 2));
			var tradeRes = net.fabricmc.fabric.api.event.player.UseEntityCallback.EVENT.invoker()
					.interact(p, level4(s), InteractionHand.MAIN_HAND, w, null);
			check(tradeRes == net.minecraft.world.InteractionResult.SUCCESS, "expected wanderer barter to succeed");
			check(p.getMainHandItem().getCount() == 1, "expected wanderer barter to consume 1 lost_coin");
			int almond = 0, bottles = 0;
			for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
				ItemStack st = p.getInventory().getItem(i);
				if (st.is(Level1Items.ALMOND_WATER)) almond += st.getCount();
				if (st.is(Items.GLASS_BOTTLE)) bottles += st.getCount();
			}
			check(almond >= 1 && bottles >= 1, "expected Almond Water and Glass Bottle from wanderer barter");
		});
		server.runCommand("gamemode spectator @a");
	}

	private static void extras(ClientGameTestContext context, TestSingleplayerContext game, long seed, BlockPos from) {
		var server = game.getServer();
		server.runCommand("gamemode survival @a");

		// 1. Scattered Almond Water bottle cluster pickup
		server.runOnServer(s -> {
			ServerPlayer p = player(s);
			p.getInventory().clearContent();
			p.teleportTo(from.getX() + 0.5, STAND, from.getZ() + 0.5);
			BlockPos clusterPos = new BlockPos(from.getX(), STAND, from.getZ() + 1);
			level4(s).setBlock(clusterPos, Level4Blocks.ALMOND_BOTTLE_CLUSTER.defaultBlockState(), 3);
			BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(clusterPos), Direction.NORTH, clusterPos, false);
			p.gameMode.useItemOn(p, level4(s), ItemStack.EMPTY, InteractionHand.MAIN_HAND, hit);
			check(level4(s).getBlockState(clusterPos).isAir(), "almond_bottle_cluster should vanish after pickup");
			int water = 0;
			for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
				ItemStack st = p.getInventory().getItem(i);
				if (st.is(Level1Items.ALMOND_WATER)) water += st.getCount();
			}
			check(water >= 1, "expected at least 1 Almond Water from cluster, got " + water);
		});

		// 2. Client-only Smiler rumor: active on client, zero server Smiler entities
		context.runOnClient(client -> check(Level4Client.triggerRumorForTest(client), "expected client-only rumor Smiler to spawn"));
		context.runOnClient(client -> check(Level4Client.hasActiveRumor(), "expected active rumor Smiler on client"));
		server.runOnServer(s -> {
			var smilers = level4(s).getEntities(dev.noclip.backrooms.monsters.Monsters.SMILER,
					new AABB(player(s).blockPosition()).inflate(32), e -> true);
			check(smilers.isEmpty(), "Smiler rumor must remain 100% client-only with zero server entities");
		});
		context.runOnClient(Level4Client::clearForTest);

		// 3. Settled at M.E.G. Base Omega -> returning from Level 2 brings player back to settled Omega Base
		int[] omega = findZone(seed, from, ZoneKind.OMEGA_BASE);
		check(omega != null, "no OMEGA_BASE found near arrival");
		int[] cot = null;
		for (int dx = -20; dx <= 20 && cot == null; dx++) {
			for (int dz = -20; dz <= 20; dz++) {
				if (Level4Layout.tile(seed, omega[0] + dx, omega[1] + dz).kind() == Kind.OMEGA_COT) {
					cot = new int[] {omega[0] + dx, omega[1] + dz};
					break;
				}
			}
		}
		check(cot != null, "no OMEGA_COT found inside Omega Base");
		int cotX = cot[0], cotZ = cot[1];
		BlockPos[] settledPos = new BlockPos[1];
		server.runOnServer(s -> {
			ServerPlayer p = player(s);
			p.teleportTo(cotX + 1.5, STAND, cotZ + 0.5);
		});
		game.getConnection().waitForChunksDownload();
		context.waitTicks(10);
		server.runOnServer(s -> {
			ServerPlayer p = player(s);
			BlockPos cotPos = new BlockPos(cotX, STAND, cotZ);
			BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(cotPos), Direction.EAST, cotPos, false);
			p.gameMode.useItemOn(p, level4(s), ItemStack.EMPTY, InteractionHand.MAIN_HAND, hit);
			settledPos[0] = p.blockPosition();
		});

		server.runCommand("execute in noclip_backrooms:level_2 run tp @a 2.5 33 2.5");
		game.getConnection().waitForChunksDownload();
		context.waitTicks(15);
		server.runOnServer(s -> LevelExits.use(player(s), "4"));
		server.waitFor(s -> player(s).level().dimension() == Level4.LEVEL_4, 100);
		game.getConnection().waitForChunksDownload();
		context.waitTicks(15);
		server.runOnServer(s -> {
			BlockPos returned = player(s).blockPosition();
			check(returned.closerThan(settledPos[0], 8.0),
					"expected return to settled Omega Base near " + settledPos[0] + ", got " + returned);
		});
		server.runCommand("gamemode spectator @a");
	}

	private static void checkBlocks(TestSingleplayerContext game) {
		game.getServer().runOnServer(s -> {
			Language lang = Language.getInstance();
			for (Block block : Level4Blocks.ALL) {
				String key = block.getDescriptionId();
				check(lang.has(key), "missing translation: " + key);
			}
			check(lang.has("level.noclip_backrooms.4"), "missing level 4 name");
			check(lang.has(Level4Items.BRASS_CRUCIFIX.getDescriptionId()), "missing crucifix name");
			check(lang.has(Level4Items.RAIN_GLASS_ORB.getDescriptionId()), "missing rain orb name");
			for (String role : new String[] {"drifter", "barterer", "scout", "cartographer", "restless"}) {
				check(lang.has("entity.noclip_backrooms.level4_wanderer." + role), "missing wanderer role: " + role);
			}
			for (String msg : new String[] {"wanderer_tip_omega", "wanderer_tip_atrium", "wanderer_tip_windows",
					"wanderer_tip_exits", "wanderer_tip_amor", "wanderer_tip_tbd", "wanderer_tip_barter",
					"wanderer_barter_coin", "wanderer_barter_alloy", "wanderer_barter_paper", "wanderer_barter_done",
					"amor_offered", "tbd_hoard_refuse",
					"window_lure", "window_rain_lure", "window_grab", "window_touch_grab",
					"almond_pickup", "returned_omega"}) {
				check(lang.has("message.noclip_backrooms.level4." + msg), "missing wanderer message: " + msg);
			}
		});
	}

	private static int[] findZone(long seed, BlockPos from, ZoneKind target) {
		int rx0 = Math.floorDiv(from.getX(), Level4Plan.SIZE), rz0 = Math.floorDiv(from.getZ(), Level4Plan.SIZE);
		for (int r = 0; r <= 3; r++) {
			for (int dx = -r; dx <= r; dx++) {
				for (int dz = -r; dz <= r; dz++) {
					Level4Plan plan = Level4Plan.of(seed, rx0 + dx, rz0 + dz);
					for (Level4Plan.RoomBox box : plan.rooms) {
						if (box.kind() != target) continue;
						int x = (rx0 + dx) * Level4Plan.SIZE + box.cx() * Level4Plan.CELL + 3;
						int z = (rz0 + dz) * Level4Plan.SIZE + box.cz() * Level4Plan.CELL + 3;
						if (Level4Layout.tile(seed, x, z).kind() == Kind.AIR) return new int[] {x, z};
						return new int[] {x - 2, z - 2};
					}
				}
			}
		}
		return null;
	}

	private static int[] findKind(long seed, BlockPos from, Kind target) {
		for (int r = 0; r < 180; r += 1) {
			for (int dx = -r; dx <= r; dx++) {
				for (int dz = -r; dz <= r; dz++) {
					if (Math.abs(dx) != r && Math.abs(dz) != r) continue;
					int x = from.getX() + dx, z = from.getZ() + dz;
					if (Level4Layout.tile(seed, x, z).kind() == target) return new int[] {x, z};
				}
			}
		}
		return null;
	}

	private static int[] indoorStandNextTo(long seed, int wx, int wz) {
		int[][] dirs = {{0, 1}, {0, -1}, {1, 0}, {-1, 0}};
		for (int[] d : dirs) {
			int sx = wx + d[0], sz = wz + d[1];
			if (Level4Layout.tile(seed, sx, sz).kind() == Kind.AIR) return new int[] {sx, sz};
		}
		return null;
	}

	private static int[] findRainWindowView(long seed, BlockPos from) {
		int[] win = findKind(seed, from, Kind.WINDOW_RAIN);
		if (win == null) return null;
		int[][] dirs = {{0, 2, 180}, {0, -2, 0}, {2, 0, 90}, {-2, 0, 270}};
		for (int[] d : dirs) {
			int sx = win[0] + d[0], sz = win[1] + d[1];
			int midX = win[0] + d[0] / 2, midZ = win[1] + d[1] / 2;
			if (Level4Layout.tile(seed, sx, sz).kind() == Kind.AIR
					&& Level4Layout.tile(seed, midX, midZ).kind() == Kind.AIR) {
				return new int[] {sx, sz, d[2]};
			}
		}
		return null;
	}

	private static int[] findExitView(long seed, BlockPos from, Level4Exit target) {
		int rx0 = Math.floorDiv(from.getX(), Level4Plan.SIZE), rz0 = Math.floorDiv(from.getZ(), Level4Plan.SIZE);
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				Level4Plan plan = Level4Plan.of(seed, rx0 + dx, rz0 + dz);
				for (PlacedExit pe : plan.exits) {
					if (pe.exit() != target) continue;
					int[] view = exitViewFromPlaced(seed, rx0 + dx, rz0 + dz, pe);
					if (view != null) return view;
				}
			}
		}
		return null;
	}

	private static int[] findVanishingExit(long seed, BlockPos from) {
		int rx0 = Math.floorDiv(from.getX(), Level4Plan.SIZE), rz0 = Math.floorDiv(from.getZ(), Level4Plan.SIZE);
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				Level4Plan plan = Level4Plan.of(seed, rx0 + dx, rz0 + dz);
				for (PlacedExit pe : plan.exits) {
					if (!pe.exit().vanishing || pe.exit() == Level4Exit.HATCH_2) continue;
					int[] view = exitViewFromPlaced(seed, rx0 + dx, rz0 + dz, pe);
					if (view != null) return view;
				}
			}
		}
		return null;
	}

	/** Returns {ex, ey, ez, standX, standZ, yaw} for an exit, accounting for 3D stairwell/basement offsets. */
	private static int[] exitViewFromPlaced(long seed, int rx, int rz, PlacedExit pe) {
		int cx = pe.cell() % Level4Plan.N, cz = pe.cell() / Level4Plan.N;
		int baseX = rx * Level4Plan.SIZE + cx * Level4Plan.CELL;
		int baseZ = rz * Level4Plan.SIZE + cz * Level4Plan.CELL;
		int doorDepth = (pe.exit() == Level4Exit.ELEVATOR_3 || pe.exit() == Level4Exit.BASEMENT_71 || !pe.exit().hasStructure()) ? 0 : 1;
		int ex = switch (pe.dir()) {
			case Level4Plan.WEST -> baseX + doorDepth;
			case Level4Plan.EAST -> baseX + Level4Plan.CELL - 1 - doorDepth;
			default -> baseX + 3;
		};
		int ez = switch (pe.dir()) {
			case Level4Plan.NORTH -> baseZ + doorDepth;
			case Level4Plan.SOUTH -> baseZ + Level4Plan.CELL - 1 - doorDepth;
			default -> baseZ + 3;
		};
		int ey = switch (pe.exit()) {
			case STAIRS_5, OMEGA_STAIRS_5, STAIRS_6, OMEGA_STAIRS_6 -> Level4ChunkGenerator.FLOOR_Y + 3;
			case BASEMENT_71 -> Level4ChunkGenerator.FLOOR_Y - 1;
			default -> STAND;
		};
		int standDepth = pe.exit().hasStructure() ? 4 : 3;
		int sx = switch (pe.dir()) {
			case Level4Plan.WEST -> baseX + standDepth;
			case Level4Plan.EAST -> baseX + Level4Plan.CELL - 1 - standDepth;
			default -> baseX + 3;
		};
		int sz = switch (pe.dir()) {
			case Level4Plan.NORTH -> baseZ + standDepth;
			case Level4Plan.SOUTH -> baseZ + Level4Plan.CELL - 1 - standDepth;
			default -> baseZ + 3;
		};
		if (Level4Layout.tile(seed, ex, ez).kind() != Kind.EXIT_DOOR) return null;
		if (Level4Layout.tile(seed, sx, sz).kind() != Kind.AIR) return null;
		int yaw = switch (pe.dir()) {
			case Level4Plan.NORTH -> 180;
			case Level4Plan.SOUTH -> 0;
			case Level4Plan.EAST -> 270;
			default -> 90;
		};
		return new int[] {ex, ey, ez, sx, sz, yaw};
	}

	private static void shoot(ClientGameTestContext context, TestSingleplayerContext game, double x, double y, double z, float yaw, float pitch, boolean populate, String name) {
		var server = game.getServer();
		server.runOnServer(s -> player(s).teleportTo(x, y, z));
		server.runCommand("rotate @p " + yaw + " " + pitch);
		game.getConnection().waitForChunksDownload();
		if (populate) {
			long seed = server.computeOnServer(s -> Level4ChunkGenerator.planSeed(level4(s).getChunkSource().randomState()));
			server.runOnServer(s -> Level4Bases.populateNear(level4(s), seed, player(s)));
		}
		context.waitTicks(12);
		game.getConnection().waitForChunksRender();
		context.takeScreenshot(name);
	}

	private static ServerLevel level4(MinecraftServer server) {
		return server.getLevel(Level4.LEVEL_4);
	}

	private static ServerPlayer player(MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}

	private static void check(boolean ok, String message) {
		if (!ok) throw new AssertionError(message);
	}
}
