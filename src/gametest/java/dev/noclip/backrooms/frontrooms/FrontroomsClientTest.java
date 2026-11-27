package dev.noclip.backrooms.frontrooms;

import dev.noclip.backrooms.core.Home;
import dev.noclip.backrooms.core.economy.Wallet;
import dev.noclip.backrooms.core.locker.Lockers;
import dev.noclip.backrooms.core.people.Person;
import dev.noclip.backrooms.core.quota.Quota;
import dev.noclip.backrooms.core.trade.ShopRole;
import dev.noclip.backrooms.core.trade.Shops;
import dev.noclip.backrooms.core.trade.Treatment;
import dev.noclip.backrooms.core.trip.Extraction;
import dev.noclip.backrooms.core.trip.MissionReportScreen;
import dev.noclip.backrooms.core.trip.Trips;
import dev.noclip.backrooms.items.BackroomsItems;
import dev.noclip.backrooms.level0.Level0;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The Frontrooms and M.E.G.'s institute (docs/企劃書/1-遊戲玩法/README.md, "前室"), in a real game: getting out of
 * Level 0 lands in the lobby with a report; its people are at their posts; the building cannot be broken but its work
 * area can be built in; the quarantine wall keeps everyone in; the locker; the medic and the blast doors open as quotas
 * are met (1: medical wing, 3: armoury, 5: B1 and its transport pads, which send people in); a tier 3 keypad hacker opens
 * B2. Screenshots of the lobby, the building from outside, the grounds and the wall, the wings, B1 and B2.
 * Run alone with gradlew runClientGameTest -Plevels=frontrooms
 */
public final class FrontroomsClientTest implements FabricClientGameTest {
	private static final int STAND_Y = 33;

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext game = context.worldBuilder().create()) {
			var server = game.getServer();
			server.runCommand("gamerule send_command_feedback false");
			server.runCommand("gamerule noclip_backrooms:edge_shift false");
			server.runCommand("gamemode survival @a");
			server.runCommand("effect give @a resistance infinite 4 true");
			server.runCommand("effect give @a night_vision infinite 0 true");
			server.runCommand("time set 6000");

			// Into Level 0, settle, call a pickup, and be brought to the institute.
			server.runCommand("execute as @a in noclip_backrooms:level_0 run tp @s 3.5 " + STAND_Y + " 3.5");
			server.waitFor(s -> player(s).level().dimension() == Level0.LEVEL_0, 200);
			server.runOnServer(s -> Trips.settle(player(s), LevelData.RespawnData.of(Level0.LEVEL_0, player(s).blockPosition(), 0, 0)));
			check(server.computeOnServer(s -> Trips.extract(player(s))), "no pickup");
			server.runCommand("execute as @a run backrooms extract now");
			server.waitFor(s -> player(s).level().dimension() == Home.FRONTROOMS, 200);
			context.waitFor(client -> client.gui.screen() instanceof MissionReportScreen, 200);
			context.runOnClient(client -> client.gui.screen().onClose());
			game.getConnection().waitForChunksRender();
			context.waitTicks(40);
			context.takeScreenshot("frontrooms_lobby");

			// The objects stand where the plans put them, and a multi-block one's other blocks know the one that draws it.
			InstituteFurnishing.Place table = InstituteFurnishing.places().stream()
					.filter(p -> p.type().id().equals("inst_interview_table")).findFirst().orElseThrow();
			server.runOnServer(s -> {
				BlockPos master = new BlockPos(table.x(), InstituteLayout.FLOOR + 1, table.z());
				check(level(s).getBlockState(master).is(InstituteProps.block("inst_interview_table")), "no interview table in the receiving room");
				int[] part = InstituteFurnishing.cells(table).get(1);
				BlockPos partPos = new BlockPos(part[0], InstituteLayout.FLOOR + 1 + part[1], part[2]);
				check(level(s).getBlockEntity(partPos) instanceof dev.noclip.backrooms.props.PropBlockEntity be && be.masterPos().equals(master),
						"the table's other half does not know its master");
			});

			// Its people, each at their post.
			server.waitFor(s -> people(s) >= 5, 400);
			for (ShopRole role : new ShopRole[] {ShopRole.MASTER, ShopRole.BUYER, ShopRole.SUPPLIER, ShopRole.MEDIC, ShopRole.QUARTERMASTER}) {
				check(server.computeOnServer(s -> count(s, role)) == 1, "not exactly one " + role.id() + " at the institute");
			}

			// Closed wings at first: doors shut and red, the medic will not serve.
			InstituteLayout.Door medical = door(1), armoury = door(3), deep = door(5);
			check(!open(server, medical) && !open(server, armoury) && !open(server, deep), "a wing is open before any quota");
			InstituteLayout.Spot medic = InstituteLayout.spot('n');
			server.runOnServer(s -> Wallet.set(player(s), 1000));
			stand(server, medic.x(), medic.z() - 2);
			check(server.computeOnServer(s -> Shops.trade(player(s), ShopRole.MEDIC, 0, 1)) == Shops.Outcome.WING_LOCKED, "the medic serves before any quota");

			// The building cannot be broken; its work area can be built in.
			InstituteLayout.Spot a = InstituteLayout.spot('a');
			BlockPos lobbyFloor = new BlockPos(a.x(), InstituteLayout.FLOOR, a.z());
			stand(server, a.x(), a.z());
			check(!server.computeOnServer(s -> player(s).gameMode.destroyBlock(lobbyFloor.east())), "the lobby floor broke");
			check(server.computeOnServer(s -> level(s).getBlockState(lobbyFloor.east()).is(FrontroomsBlocks.INSTITUTE_FLOOR)), "the lobby floor is gone");
			BlockPos work = new BlockPos(-36, InstituteLayout.FLOOR + 1, -25);
			check(InstituteLayout.workArea(work.getX(), work.getY(), work.getZ()), "the test's work spot is not in a work area");
			server.runOnServer(s -> level(s).setBlockAndUpdate(work, Blocks.CHEST.defaultBlockState()));
			stand(server, work.getX(), work.getZ() + 2);
			check(server.computeOnServer(s -> player(s).gameMode.destroyBlock(work)), "a chest in the work area could not be broken");

			// The locker keeps things for its owner.
			stand(server, a.x(), a.z());
			server.runOnServer(s -> Lockers.open(player(s)));
			context.waitFor(client -> client.gui.screen() instanceof ContainerScreen, 100);
			context.takeScreenshot("frontrooms_locker");
			context.runOnClient(client -> client.gui.screen().onClose());

			// 1F lobby Level 0 return pad works even at 0 quotas: right-clicking twice sends the player straight back to Level 0.
			InstituteLayout.Spot lobbyPad = InstituteLayout.lobbyPads().getFirst();
			BlockPos lobbyPadPos = new BlockPos(lobbyPad.x(), InstituteLayout.FLOOR, lobbyPad.z());
			check(server.computeOnServer(s -> level(s).getBlockState(lobbyPadPos).is(FrontroomsBlocks.TRANSPORT_PAD)), "no 1F lobby return pad block");
			stand(server, lobbyPad.x(), lobbyPad.z() + 2);
			server.runOnServer(s -> {
				ServerPlayer p = player(s);
				BlockState st = level(s).getBlockState(lobbyPadPos);
				BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(lobbyPadPos), Direction.UP, lobbyPadPos, false);
				st.useWithoutItem(level(s), p, hit); // 1st click: confirmation prompt
				check(p.level().dimension() == Home.FRONTROOMS, "1F lobby pad teleported on first accidental click");
				st.useWithoutItem(level(s), p, hit); // 2nd click: confirmed deploy to Level 0
			});
			server.waitFor(s -> player(s).level().dimension() == Level0.LEVEL_0, 200);
			// Return to institute lobby for the remaining Frontrooms checks
			server.runOnServer(s -> Institute.receive(player(s)));
			server.waitFor(s -> player(s).level().dimension() == Home.FRONTROOMS, 200);
			context.waitFor(client -> client.gui.screen() instanceof MissionReportScreen, 200);
			context.runOnClient(client -> client.gui.screen().onClose());

			// The quarantine wall: whoever gets past it is walked back in.
			server.runCommand("gamemode spectator @a");
			server.runCommand("execute as @a in noclip_backrooms:frontrooms run tp @s 0.5 80 160.5 0 -5");
			game.getConnection().waitForChunksRender();
			context.waitTicks(60);
			context.takeScreenshot("frontrooms_wall");
			server.runCommand("gamemode survival @a");
			server.runCommand("execute as @a in noclip_backrooms:frontrooms run tp @s 0.5 70 230.5");
			server.waitFor(s -> player(s).position().distanceTo(Institute.arrival()) < 3, 100);

			// The building from its forecourt, and the grounds.
			server.runCommand("gamemode spectator @a");
			shoot(context, server, game, "frontrooms_institute_outside", 0.5, 67, 70.5, 180, -12);
			shoot(context, server, game, "frontrooms_grounds", 75.5, 125, 125.5, 145, 28);
			shoot(context, server, game, "frontrooms_towers", -40.5, 90, -20.5, 120, 10);
			// Inside: the atrium with the specimen chamber, the balconies, the receiving room, the containment cells.
			shoot(context, server, game, "frontrooms_atrium", 0.5, InstituteLayout.FLOOR + 1, 21.5, 180, -28);
			shoot(context, server, game, "frontrooms_balcony", -19.5, InstituteLayout.F2_FLOOR + 1, 19.5, 225, 8);
			shoot(context, server, game, "frontrooms_atrium_top", 9.5, InstituteLayout.F3_FLOOR + 1, 9.5, 135, 35);
			shoot(context, server, game, "frontrooms_receiving", -28.5, InstituteLayout.FLOOR + 1, 31.5, 135, 5);
			shoot(context, server, game, "frontrooms_cells", 0.5, InstituteLayout.B1_FLOOR + 1, 2.5, 0, 5);
			shoot(context, server, game, "frontrooms_gate", 1.5, InstituteLayout.B1_FLOOR + 2, -10.5, 180, 5);
			shoot(context, server, game, "frontrooms_observation", -46.5, InstituteLayout.FLOOR + 1, 23.5, -100, 5);
			shoot(context, server, game, "frontrooms_lockers", -44.5, InstituteLayout.FLOOR + 1, 6.5, -75, 5);
			shoot(context, server, game, "frontrooms_operations", 8.5, InstituteLayout.FLOOR + 1, -30.5, 140, 5);
			shoot(context, server, game, "frontrooms_labs", -42.5, InstituteLayout.F2_FLOOR + 1, -8.5, 180, 0);
			shoot(context, server, game, "frontrooms_tanks", -36.5, InstituteLayout.F3_FLOOR + 1, 4.5, 20, 0);
			// The staff town: the street, a house inside (the film's furniture on Level 0's carpet), the park, the store.
			shoot(context, server, game, "frontrooms_town_street", -40.5, 70, 80.5, 90, 8);
			shoot(context, server, game, "frontrooms_town_house", -71.5, FrontroomsTown.G + 2, 67.5, 160, 10);
			shoot(context, server, game, "frontrooms_town_park", -33.5, 71, 111.5, 180, 18);
			shoot(context, server, game, "frontrooms_town_store", -37.5, FrontroomsTown.G + 1, 68.5, 180, 5);
			server.runCommand("gamemode survival @a");
			server.runCommand("execute as @a in noclip_backrooms:frontrooms run tp @s -40.5 %d 90.5".formatted(FrontroomsTown.G + 1));
			game.getConnection().waitForChunksRender();
			// Its doors are doors; its people come to the picnic and to the store.
			server.runOnServer(s -> check(level(s).getBlockState(new BlockPos(FrontroomsTown.LOTS[0] + 4, FrontroomsTown.G + 1, 69))
					.is(net.minecraft.world.level.block.Blocks.BIRCH_DOOR), "a house has no front door"));
			server.waitFor(s -> level(s).getEntitiesOfClass(Person.class, new net.minecraft.world.phys.AABB(-60, 60, 50, -10, 80, 110),
					p -> p.entityTags().contains(TownLife.TAG)).size() >= 4, 400);
			// The mailbox: a letter from home, once a day.
			server.runOnServer(s -> {
				ServerPlayer p = player(s);
				TownLife.mailbox(p);
				check(p.getInventory().countItem(TownLife.HOME_LETTER) == 1, "the mailbox gave no letter");
				TownLife.mailbox(p);
				check(p.getInventory().countItem(TownLife.HOME_LETTER) == 1, "the mailbox gave two letters in a day");
			});
			// The picnic: sitting down with them fills you up, once a day.
			server.runOnServer(s -> {
				ServerPlayer p = player(s);
				p.getFoodData().setFoodLevel(4);
				TownLife.picnic(p, level(s), new BlockPos(-42, FrontroomsTown.G + 1, 88));
				check(p.getFoodData().getFoodLevel() > 4, "the picnic did not feed the player");
				TownLife.clock(p);
			});

			server.runCommand("gamemode spectator @a");
			server.runCommand("gamemode survival @a");

			// Quotas open the wings: one the medical wing, three the armoury, five B1.
			meet(server);
			check(open(server, medical) && !open(server, armoury), "one quota did not open just the medical wing");
			stand(server, medic.x(), medic.z() - 2);
			check(server.computeOnServer(s -> Shops.trade(player(s), ShopRole.MEDIC, 0, 1)) == Shops.Outcome.DONE, "the medic does not serve after a quota");
			server.runOnServer(s -> player(s).setHealth(4.0F));
			check(server.computeOnServer(s -> Treatment.treat(player(s))), "no treatment");
			check(server.computeOnServer(s -> player(s).getHealth() == player(s).getMaxHealth()), "the treatment did not heal");
			look(server, medic.x() + 0.5, medic.z() - 4.5, 0);
			context.waitTicks(20);
			context.takeScreenshot("frontrooms_medical_wing");
			meet(server);
			meet(server);
			check(open(server, armoury) && !open(server, deep), "three quotas did not open just the armoury");
			InstituteLayout.Spot quartermaster = InstituteLayout.spot('q');
			look(server, quartermaster.x() + 0.5, quartermaster.z() + 8.5, 180);
			context.waitTicks(20);
			context.takeScreenshot("frontrooms_armoury");
			meet(server);
			meet(server);
			check(open(server, deep), "five quotas did not open B1");

			// B1: the pads send people in.
			InstituteLayout.Spot pad = InstituteLayout.pads().getFirst();
			server.runCommand("execute as @a in noclip_backrooms:frontrooms run tp @s 1.5 %d -11.5 0 12".formatted(InstituteLayout.B1_FLOOR + 1));
			game.getConnection().waitForChunksRender();
			context.waitTicks(20);
			context.takeScreenshot("frontrooms_b1");

			// B2: only a high-grade keypad hacker opens it.
			InstituteLayout.Spot hatch = InstituteLayout.hatch();
			BlockPos hatchPos = new BlockPos(hatch.x(), InstituteLayout.B1_FLOOR, hatch.z());
			stand(server, hatch.x() - 1, hatch.z(), InstituteLayout.B1_FLOOR + 1);
			check(!server.computeOnServer(s -> level(s).getBlockState(hatchPos).getValue(InstituteBlocks.Hatch.OPEN)), "B2 is open from the start");
			server.runOnServer(s -> {
				ServerPlayer p = player(s);
				p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(BackroomsItems.KEYPAD_HACKER_T3));
				BlockState state = level(s).getBlockState(hatchPos);
				state.useItemOn(p.getMainHandItem(), level(s), p, InteractionHand.MAIN_HAND,
						new BlockHitResult(Vec3.atCenterOf(hatchPos).add(0, 0.5, 0), Direction.UP, hatchPos, false));
			});
			check(server.computeOnServer(s -> level(s).getBlockState(hatchPos).getValue(InstituteBlocks.Hatch.OPEN)), "the hacker did not open B2");
			InstituteLayout.Spot file = InstituteLayout.files().getFirst();
			server.runCommand("execute as @a in noclip_backrooms:frontrooms run tp @s %.1f %d %.1f 180 30".formatted(file.x() + 0.5, InstituteLayout.B2_FLOOR + 1, file.z() + 2.5));
			game.getConnection().waitForChunksRender();
			context.waitTicks(20);
			context.takeScreenshot("frontrooms_b2_archive");

			// From a pad, M.E.G. sends people in without the commander.
			server.runCommand("execute as @a in noclip_backrooms:frontrooms run tp @s %.1f %d %.1f".formatted(pad.x() + 0.5, InstituteLayout.B1_FLOOR + 1, pad.z() + 0.5));
			context.waitTicks(5);
			check(server.computeOnServer(s -> Quota.deploy(player(s), "0", true)), "the pad would not send the player in");
			server.waitFor(s -> player(s).level().dimension() == Level0.LEVEL_0, 200);
			check(!server.computeOnServer(s -> Extraction.running(player(s))), "a pickup is still running");

			// Level 398: once someone knows the truth of the B2 files, the town's nights are 398's.
			// (Last: it sends the player to Level 0 and back, so it must not come between the quotas above.)
			server.runOnServer(s -> Institute.receive(player(s)));
			server.waitFor(s -> player(s).level().dimension() == Home.FRONTROOMS, 200);
			context.waitFor(client -> client.gui.screen() instanceof MissionReportScreen, 200);
			context.runOnClient(client -> client.gui.screen().onClose());
			server.runCommand("gamemode creative @a");
			server.runCommand("execute as @a in noclip_backrooms:frontrooms run tp @s -70.5 %d 80.5 90 0".formatted(FrontroomsTown.G + 1));
			game.getConnection().waitForChunksRender();
			server.runOnServer(s -> {
				for (int i = 0; i < dev.noclip.backrooms.core.ending.Endings.TOTAL_ARCHIVE_FILES; i++) dev.noclip.backrooms.core.ending.Endings.recordFile(player(s), i);
			});
			server.runCommand("time set midnight");
			server.waitFor(s -> Nights398.active(level(s)), 200);
			server.waitFor(s -> nightThings(s, dev.noclip.backrooms.monsters.faceling.Faceling.class) >= 3
					&& nightThings(s, dev.noclip.backrooms.monsters.smiler.Smiler.class) >= 1, 400);
			check(server.computeOnServer(s -> level(s).getEntitiesOfClass(Person.class, new net.minecraft.world.phys.AABB(-120, 60, 50, -10, 80, 115),
					p -> p.entityTags().contains(TownLife.TAG)).isEmpty()), "the town's day people are still out on a night of 398");
			shoot(context, server, game, "frontrooms_398", -76.5, FrontroomsTown.G + 2, 80.5, -90, 0);
			// Waiting under a streetlight for the morning: you never left, and wake in Level 0.
			server.runOnServer(s -> Nights398.lampTicks = 60);
			server.runCommand("gamemode creative @a");
			server.runCommand("execute as @a in noclip_backrooms:frontrooms run tp @s -71.5 %d 77.5".formatted(FrontroomsTown.G + 1));
			server.waitFor(s -> player(s).level().dimension() == Level0.LEVEL_0, 400);
			server.runOnServer(s -> Nights398.lampTicks = 60 * 20);
			// Back to the institute; morning takes the night away.
			server.runOnServer(s -> Institute.receive(player(s)));
			server.waitFor(s -> player(s).level().dimension() == Home.FRONTROOMS, 200);
			context.waitFor(client -> client.gui.screen() instanceof MissionReportScreen, 200);
			context.runOnClient(client -> client.gui.screen().onClose());
			server.runCommand("time set day");
			server.runCommand("execute as @a in noclip_backrooms:frontrooms run tp @s -70.5 %d 80.5".formatted(FrontroomsTown.G + 1));
			server.waitFor(s -> !Nights398.active(level(s)) && nightThings(s, net.minecraft.world.entity.Entity.class) == 0, 200);
		}
	}

	/** How many things of a 398 night (Nights398) of a kind are about. */
	private static int nightThings(MinecraftServer s, Class<? extends net.minecraft.world.entity.Entity> kind) {
		int count = 0;
		for (var entity : level(s).getAllEntities()) {
			if (kind.isInstance(entity) && entity.entityTags().contains(Nights398.TAG)) count++;
		}
		return count;
	}

	/** A spectator's picture from a place, looking a way. */
	private static void shoot(ClientGameTestContext context, TestServerContext server, TestSingleplayerContext game, String name,
			double x, double y, double z, float yaw, float pitch) {
		server.runCommand("execute as @a in noclip_backrooms:frontrooms run tp @s %.1f %.1f %.1f %.1f %.1f".formatted(x, y, z, yaw, pitch));
		game.getConnection().waitForChunksRender();
		context.runOnClient(client -> { if (!client.gui.hud.isHidden()) client.gui.hud.toggle(); });
		context.waitTicks(30);
		context.takeScreenshot(name);
		context.runOnClient(client -> { if (client.gui.hud.isHidden()) client.gui.hud.toggle(); });
	}

	/** Hands in what this cycle wants, as if sold, and waits for the doors to follow. */
	private static void meet(TestServerContext server) {
		int before = server.computeOnServer(s -> Quota.clearance(s));
		server.runOnServer(s -> {
			var d = Quota.data(s);
			for (var e : d.items().entrySet()) {
				var item = net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.parse(e.getKey()));
				Quota.onSold(player(s), item, e.getValue(), 0);
			}
			Quota.addPoints(player(s), d.required() + d.debt());
		});
		server.waitFor(s -> Quota.clearance(s) == before + 1, 40);
	}

	private static boolean open(TestServerContext server, InstituteLayout.Door door) {
		return server.computeOnServer(s -> {
			BlockState state = level(s).getBlockState(new BlockPos(door.x(), door.y(), door.z()));
			BlockState lamp = level(s).getBlockState(new BlockPos(door.x(), door.y() + 2, door.z()));
			return state.getValue(BlastDoorBlock.OPEN) && lamp.getValue(DoorLampBlock.UNLOCKED);
		});
	}

	private static InstituteLayout.Door door(int requires) {
		return InstituteLayout.doors().stream().filter(d -> d.requires() == requires).findFirst().orElseThrow();
	}

	private static void stand(TestServerContext server, int x, int z) {
		stand(server, x, z, InstituteLayout.FLOOR + 1);
	}

	private static void stand(TestServerContext server, int x, int z, int y) {
		server.runCommand("execute as @a in noclip_backrooms:frontrooms run tp @s %.1f %d %.1f".formatted(x + 0.5, y, z + 0.5));
		try {
			server.waitFor(s -> player(s).blockPosition().getX() == x && player(s).blockPosition().getZ() == z, 40);
		} catch (AssertionError e) {
			throw new AssertionError("could not stand at " + x + ", " + y + ", " + z + ": the player is at "
					+ server.computeOnServer(s -> player(s).blockPosition() + " in " + player(s).level().dimension().identifier()));
		}
	}

	private static void look(TestServerContext server, double x, double z, float yaw) {
		server.runCommand("execute as @a in noclip_backrooms:frontrooms run tp @s %.1f %d %.1f %.1f 10".formatted(x, InstituteLayout.FLOOR + 1, z, yaw));
	}

	private static int people(MinecraftServer server) {
		ServerLevel level = level(server);
		return level.getEntitiesOfClass(Person.class, new net.minecraft.world.phys.AABB(-40, 40, -30, 40, 80, 30),
				p -> p.entityTags().contains(Institute.TAG)).size();
	}

	private static int count(MinecraftServer server, ShopRole role) {
		return level(server).getEntitiesOfClass(Person.class, new net.minecraft.world.phys.AABB(-40, 40, -30, 40, 80, 30),
				p -> p.entityTags().contains(role.tag())).size();
	}

	private static ServerLevel level(MinecraftServer server) {
		return server.getLevel(Home.FRONTROOMS);
	}

	private static ServerPlayer player(MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}

	private static void check(boolean ok, String message) {
		if (!ok) throw new AssertionError(message);
	}
}
