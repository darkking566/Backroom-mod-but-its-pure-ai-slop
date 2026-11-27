package dev.noclip.backrooms.horror;

import dev.noclip.backrooms.core.LevelExits;
import dev.noclip.backrooms.core.economy.Wallet;
import dev.noclip.backrooms.core.floors.Floors;
import dev.noclip.backrooms.core.guild.Contracts;
import dev.noclip.backrooms.core.rpg.PlayerRpg;
import dev.noclip.backrooms.core.trip.Pull;
import dev.noclip.backrooms.core.trip.PullState;
import dev.noclip.backrooms.core.trip.Trips;
import dev.noclip.backrooms.core.Home;
import dev.noclip.backrooms.core.trip.Extraction;
import dev.noclip.backrooms.core.trip.MissionReportScreen;
import dev.noclip.backrooms.frontrooms.Institute;
import dev.noclip.backrooms.core.people.People;
import dev.noclip.backrooms.core.people.Person;
import dev.noclip.backrooms.core.quota.Quota;
import dev.noclip.backrooms.core.quota.QuotaClient;
import dev.noclip.backrooms.core.quota.QuotaMath;
import dev.noclip.backrooms.core.trade.ShopRole;
import dev.noclip.backrooms.core.trade.Shops;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import dev.noclip.backrooms.level0.Level0;
import dev.noclip.backrooms.level1.Level1;
import dev.noclip.backrooms.monsters.Monsters;
import dev.noclip.backrooms.monsters.hound.Hound;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelData;

/**
 * The horror direction (docs/project/1-gameplay/README.md) end to end:
 * <ol>
 * <li>half health everywhere, and a heart and 10 sanity every five levels;</li>
 * <li>eating well heals a quarter as fast in the Backrooms;</li>
 * <li>a badly hurt hound stops hunting and backs away;</li>
 * <li>dying in the Backrooms without a base wakes the player lost in Level 0, on a new trip;</li>
 * <li>having settled, they wake at the base, and M.E.G. takes them home from there, giving back their home spawn and a
 * day of peace;</li>
 * <li>the pull: warning signs, held off during that day, then falling into Level 0;</li>
 * <li>a contract: sent in, reach Level 1, walk out alive, paid.</li>
 * </ol>
 * Run alone with gradlew runClientGameTest -Plevels=horror
 */
public final class HorrorClientTest implements FabricClientGameTest {
	private static final int STAND_Y = 33;

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext game = context.worldBuilder().create()) {
			var server = game.getServer();
			server.runCommand("gamerule send_command_feedback false");
			server.runCommand("gamerule noclip_backrooms:edge_shift false");
			server.runCommand("gamemode survival @a");
			server.runCommand("effect give @a resistance infinite 4 true");

			// 1. Someone who has never been in plays the Overworld as it is; from the first fall on, half health
			//    everywhere; levels add to it.
			check(maxHealth(server) == 20.0F, "a newcomer's max health is not 20: " + maxHealth(server));
			check(!QuotaClient.status().active(), "a newcomer sees the M.E.G. cycle");
			float home = regen(context, server);
			check(home >= 1.5F, "eating well healed only " + home + " in 10 seconds at home");
			toLevel0(server);
			check(maxHealth(server) == 10.0F, "Level 0 max health is not 10: " + maxHealth(server));
			// 2. A full stomach heals a quarter as fast in the Backrooms.
			float backrooms = regen(context, server);
			check(backrooms < 1.0F, "eating well healed " + backrooms + " in 10 seconds in the Backrooms");
			server.runOnServer(s -> PlayerRpg.grantXp(player(s), 510));
			check(server.computeOnServer(s -> PlayerRpg.get(player(s)).level()) == 5, "510 XP is not level 5");
			check(maxHealth(server) == 12.0F, "level 5 is not 12 max health: " + maxHealth(server));
			check(server.computeOnServer(s -> PlayerRpg.get(player(s)).maxSanity()) == 60.0F, "level 5 is not 60 max sanity");

			// 3. A hound, hurt to a quarter of its health by the player, stops hunting and backs away.
			server.runCommand("execute as @a in noclip_backrooms:level_0 run tp @s 55.5 " + STAND_Y + " 55.5"); // inside Stage (0,0), away from its seams (Level0Space)
			game.getConnection().waitForChunksRender();
			int id = server.computeOnServer(s -> {
				ServerPlayer p = player(s);
				Hound hound = Monsters.HOUND.create(p.level(), EntitySpawnReason.COMMAND);
				hound.snapTo(p.getX() + 3, STAND_Y, p.getZ(), 0, 0);
				p.level().addFreshEntity(hound);
				return hound.getId();
			});
			check(server.computeOnServer(s -> hound(s, id).getMaxHealth()) == 75.0F, "a hound does not have 75 max health");
			context.waitTicks(40);
			double before = server.computeOnServer(s -> hound(s, id).distanceTo(player(s)));
			server.runOnServer(s -> {
				ServerPlayer p = player(s);
				hound(s, id).hurtServer(p.level(), p.damageSources().playerAttack(p), 57.0F);
			});
			check(server.computeOnServer(s -> hound(s, id).getHealth()) <= 75.0F * 0.3F, "the hound was not hurt enough");
			context.waitTicks(120);
			check(server.computeOnServer(s -> hound(s, id).getTarget()) == null, "a badly hurt hound still hunts the player");
			double after = server.computeOnServer(s -> hound(s, id).distanceTo(player(s)));
			check(after > before + 4, "a badly hurt hound did not back away: " + before + " -> " + after);
			context.takeScreenshot("horror_hound_backs_away");
			server.runCommand("kill @e[type=noclip_backrooms:hound]");

			// 4. Dying in the Backrooms without a base: lost in Level 0 again, on a new trip.
			server.runCommand("item replace entity @a hotbar.0 with minecraft:torch 3");
			die(context, server);
			server.waitFor(s -> player(s).level().dimension() == Level0.LEVEL_0, 200);
			check(server.computeOnServer(s -> Floors.of(player(s)).floor()) == 1, "waking up lost is not a new trip at floor 1");
			check(server.computeOnServer(s -> player(s).blockPosition().closerThan(new BlockPos(3, STAND_Y, 3), 3)),
					"waking up lost is not where this trip came in: " + server.computeOnServer(s -> player(s).blockPosition()));
			check(server.computeOnServer(s -> player(s).getInventory().getItem(0).isEmpty()), "the player kept their things after dying");
			check(server.computeOnServer(s -> !Quota.data(s).active()), "waking up in the Overworld after dying counted as getting out");
			server.runCommand("effect give @a resistance infinite 4 true");

			// 5. Settled: wake at the base; M.E.G. takes them home from there.
			BlockPos base = server.computeOnServer(s -> player(s).blockPosition());
			server.runOnServer(s -> Trips.settle(player(s), LevelData.RespawnData.of(Level0.LEVEL_0, base, 0, 0)));
			check(server.computeOnServer(s -> Trips.settled(player(s))), "settling did not take");
			// Going from cot to cot on the same level earns nothing more.
			long settledXp = server.computeOnServer(s -> (long) PlayerRpg.get(player(s)).level() * 100000 + PlayerRpg.get(player(s)).xp());
			server.runOnServer(s -> Trips.settle(player(s), LevelData.RespawnData.of(Level0.LEVEL_0, base.east(30), 0, 0)));
			server.runOnServer(s -> Trips.settle(player(s), LevelData.RespawnData.of(Level0.LEVEL_0, base, 0, 0)));
			check(server.computeOnServer(s -> (long) PlayerRpg.get(player(s)).level() * 100000 + PlayerRpg.get(player(s)).xp()) == settledXp,
					"moving between cots on one level gave experience again");
			server.runCommand("execute as @a in noclip_backrooms:level_0 run tp @s " + (base.getX() + 40) + " " + STAND_Y + " " + base.getZ());
			die(context, server);
			server.waitFor(s -> player(s).level().dimension() == Level0.LEVEL_0 && player(s).blockPosition().closerThan(base, 3), 200);
			server.runCommand("effect give @a resistance infinite 4 true");
			int levelBefore = server.computeOnServer(s -> PlayerRpg.get(player(s)).level());
			int xpBefore = server.computeOnServer(s -> PlayerRpg.get(player(s)).xp());
			check(server.computeOnServer(s -> Trips.extract(player(s))), "no pickup for a settled player at their base");
			// The pickup takes holding out (core.trip.Extraction); wandering off calls it off.
			check(server.computeOnServer(s -> Extraction.running(player(s))), "calling for a pickup did not start the countdown");
			check(server.computeOnServer(s -> Extraction.seconds(1)) == 30 && server.computeOnServer(s -> Extraction.seconds(9)) == 60,
					"the countdown is not 30 to 60 seconds");
			context.waitTicks(40);
			context.takeScreenshot("horror_extraction_countdown");
			server.runCommand("execute as @a in noclip_backrooms:level_0 run tp @s " + (base.getX() + 20) + " " + STAND_Y + " " + base.getZ());
			server.waitFor(s -> !Extraction.running(player(s)), 40);
			server.runCommand("execute as @a in noclip_backrooms:level_0 run tp @s " + base.getX() + " " + STAND_Y + " " + base.getZ());
			check(server.computeOnServer(s -> Trips.extract(player(s))), "no second pickup call");
			server.runCommand("execute as @a run backrooms extract now");
			server.waitFor(s -> player(s).level().dimension() == Home.FRONTROOMS, 200);
			check(server.computeOnServer(s -> player(s).position().distanceTo(Institute.arrival()) < 3), "not brought to the institute's lobby");
			// The mission report, then the day in yellow.
			context.waitFor(client -> client.gui.screen() instanceof MissionReportScreen, 200);
			context.waitTicks(10);
			context.takeScreenshot("horror_mission_report");
			context.runOnClient(client -> client.gui.screen().onClose());
			context.waitTicks(15);
			context.takeScreenshot("horror_back_at_the_institute");
			check(server.computeOnServer(s -> dev.noclip.backrooms.core.radio.Radio.carries(player(s))), "M.E.G. issued no radio");
			check(server.computeOnServer(s -> !Trips.settled(player(s))), "home, the player still wakes up in the Backrooms");
			check(server.computeOnServer(s -> Floors.of(player(s)).floor()) == 0, "the trip did not end at home");
			check(server.computeOnServer(s -> Quota.data(s).active()), "getting out did not start the M.E.G. cycle");
			check(server.computeOnServer(s -> PlayerRpg.get(player(s)).level()) > levelBefore
					|| server.computeOnServer(s -> PlayerRpg.get(player(s)).xp()) > xpBefore, "no experience for getting out alive");

			// 5b. Dying outside the Backrooms once in: M.E.G. keeps what was carried in the locker; back into Level 0.
			server.runCommand("effect give @a resistance infinite 4 true");
			server.runCommand("item replace entity @a hotbar.1 with minecraft:compass 1");
			die(context, server);
			server.waitFor(s -> player(s).level().dimension() == Level0.LEVEL_0, 200);
			check(server.computeOnServer(s -> dev.noclip.backrooms.core.locker.Lockers.count(player(s), Items.COMPASS)) == 1,
					"what the player carried is not in their locker");
			check(server.computeOnServer(s -> dev.noclip.backrooms.core.trip.Expedition.open(s, "0")), "no team stretch of Level 0 opened");
			server.runCommand("effect give @a resistance infinite 4 true");
			server.runCommand("execute as @a in minecraft:overworld run tp @s 0 120 0");
			server.waitFor(s -> player(s).level().dimension() == Level.OVERWORLD, 200);
			context.waitFor(client -> client.gui.screen() instanceof MissionReportScreen, 200);
			context.runOnClient(client -> client.gui.screen().onClose());

			// 6. The M.E.G. cycle (docs/project/1-gameplay/README.md): day 1 on getting out, the clock runs only at home,
			//    a night slept through counts, the rejection follows the days, a serum only holds it back, selling to the
			//    buyer hands things in, meeting the quota pays out, missing it means debt, a closed outpost and a drop
			//    into the Backrooms at dawn.
			check(server.computeOnServer(s -> QuotaMath.day(Quota.data(s).homeTicks())) == 1, "the cycle did not start on day 1");
			check(server.computeOnServer(s -> Quota.data(s).required()) == QuotaMath.FIRST_QUOTA, "the first quota is not 100");
			check(server.computeOnServer(s -> Quota.data(s).items().size()) == 1, "the first cycle does not ask for one item");
			context.waitFor(client -> QuotaClient.status().active() && QuotaClient.status().day() == 1, 100);
			context.waitTicks(10);
			context.takeScreenshot("horror_quota_day1");
			long homeBefore = server.computeOnServer(s -> Quota.data(s).homeTicks());
			context.waitTicks(40);
			check(server.computeOnServer(s -> Quota.data(s).homeTicks()) >= homeBefore + 30, "time at home did not count");
			toLevel0(server);
			long inside = server.computeOnServer(s -> Quota.data(s).homeTicks());
			context.waitTicks(60);
			check(server.computeOnServer(s -> Quota.data(s).homeTicks()) <= inside + 2, "time in the Backrooms counted");
			server.runCommand("execute as @a in minecraft:overworld run tp @s 0 120 0");
			server.waitFor(s -> player(s).level().dimension() == Level.OVERWORLD, 200);
			context.waitFor(client -> client.gui.screen() instanceof MissionReportScreen, 200);
			context.runOnClient(client -> client.gui.screen().onClose());
			context.waitTicks(5);
			long clockBefore = server.computeOnServer(s -> s.overworld().getOverworldClockTime());
			server.runCommand("time add 24000");
			check(server.computeOnServer(s -> s.overworld().getOverworldClockTime()) >= clockBefore + 24000, "/time add did not move the Overworld clock");
			server.waitFor(s -> QuotaMath.day(Quota.data(s).homeTicks()) == 2, 40);
			context.waitFor(client -> QuotaClient.status().day() == 2, 60);
			context.waitTicks(10);
			context.takeScreenshot("horror_quota_day2");

			// The rejection: none on day 2, some on day 4; a serum holds it back without touching the clock.
			check(server.computeOnServer(s -> Quota.rejection(s)) == 0.0F, "rejection before day 3");
			server.runCommand("backrooms quota day 4");
			server.waitFor(s -> Pull.of(player(s)).stage() >= 1, 60);
			long clock = server.computeOnServer(s -> Quota.data(s).homeTicks());
			server.runOnServer(s -> Pull.useTier2Serum(player(s)));
			server.waitFor(s -> Pull.of(player(s)).pull() == 0.0F, 40);
			check(server.computeOnServer(s -> Quota.data(s).homeTicks()) < clock + 200, "the serum moved the cycle's clock");
			server.runOnServer(s -> Pull.set(player(s), Pull.of(player(s)).withGraceUntil(0L)));

			// Handing in at the buyer: required items and points, the rest paid out; the quota met starts the next cycle.
			server.runOnServer(s -> {
				ServerPlayer p = player(s);
				Person buyer = People.type("trader").create(p.level(), EntitySpawnReason.COMMAND);
				buyer.snapTo(p.getX() + 2, p.getY(), p.getZ(), 0, 0);
				p.level().addFreshEntity(buyer);
				buyer.addTag(ShopRole.BUYER.tag());
			});
			java.util.Map<String, Integer> wanted = server.computeOnServer(s -> Quota.required(s));
			String item = wanted.keySet().iterator().next();
			server.runCommand("clear @a");
			server.runCommand("give @a " + item + " " + wanted.get(item));
			server.runCommand("give @a noclip_backrooms:microchip 6");
			long credits0 = server.computeOnServer(s -> Wallet.of(player(s)));
			check(server.computeOnServer(s -> sell(s, item, wanted.get(item))) == Shops.Outcome.DONE, "could not sell the required item");
			check(server.computeOnServer(s -> Quota.data(s).handed().getOrDefault(item, 0)) == wanted.get(item), "the required item did not count");
			check(server.computeOnServer(s -> sell(s, "noclip_backrooms:microchip", 6)) == Shops.Outcome.DONE, "could not sell microchips");
			check(server.computeOnServer(s -> Quota.data(s).cycle()) == 2, "meeting the quota did not start the next cycle");
			check(server.computeOnServer(s -> QuotaMath.day(Quota.data(s).homeTicks())) == 1, "the next cycle did not start on day 1");
			check(server.computeOnServer(s -> Quota.data(s).required()) == Math.round(QuotaMath.FIRST_QUOTA * QuotaMath.GROWTH),
					"the next quota did not grow 30%");
			check(server.computeOnServer(s -> Wallet.of(player(s))) > credits0, "what was left over after the quota was not paid out");
			check(server.computeOnServer(s -> Shops.count(player(s).getInventory(), new ItemStack(Items.BREAD))) == 4, "no care package");

			// Missing the deadline: debt with 20% on top, the outpost closed, the rejection at its worst, a drop at dawn.
			long owed = server.computeOnServer(s -> Quota.data(s).required() + Quota.data(s).items().size() * QuotaMath.MISSING_ITEM_POINTS
					* (long) QuotaMath.itemCount(1));
			server.runCommand("backrooms quota day 5");
			server.runCommand("time add 24000");
			server.waitFor(s -> Quota.data(s).debt() > 0, 60);
			check(server.computeOnServer(s -> Quota.data(s).debt()) == QuotaMath.debtAfterDeadline(owed), "the debt is not the unpaid quota plus 20%");
			check(server.computeOnServer(s -> Quota.locked(s)), "the outpost is not closed to a team in debt");
			check(server.computeOnServer(s -> Quota.rejection(s)) == 100.0F, "the rejection is not at its worst while in debt");
			long dawn = server.computeOnServer(s -> Quota.data(s).redeployAt() - s.overworld().getOverworldClockTime());
			check(dawn > 0 && dawn <= QuotaMath.DAY, "no redeployment at the next dawn");
			server.runCommand("time add " + dawn);
			server.waitFor(s -> !player(s).level().dimension().identifier().getNamespace().equals("minecraft"), 200);
			context.waitTicks(20);
			context.takeScreenshot("horror_quota_redeployed");

			// Paying the debt from the account, two credits a point, opens the outpost again.
			server.runCommand("execute as @a in minecraft:overworld run tp @s 0 120 0");
			server.waitFor(s -> player(s).level().dimension() == Level.OVERWORLD, 200);
			context.waitFor(client -> client.gui.screen() instanceof MissionReportScreen, 200);
			context.runOnClient(client -> client.gui.screen().onClose());
			long debt = server.computeOnServer(s -> Quota.data(s).debt());
			server.runOnServer(s -> Wallet.set(player(s), debt * QuotaMath.CREDITS_PER_DEBT_POINT));
			check(server.computeOnServer(s -> Quota.payDebt(player(s))), "could not pay the debt from the account");
			check(server.computeOnServer(s -> !Quota.locked(s)), "the outpost stayed closed after the debt was paid");
			check(server.computeOnServer(s -> Wallet.of(player(s))) == 0, "paying the debt did not cost two credits a point");

			// 7. A contract: sent in, reach Level 1, out alive, paid.
			server.runCommand("execute as @a in minecraft:overworld run tp @s 0 120 0");
			server.waitFor(s -> player(s).level().dimension() == Level.OVERWORLD, 200);
			long credits = server.computeOnServer(s -> Wallet.of(player(s)));
			long room = server.computeOnServer(s -> Quota.data(s).required() - Quota.data(s).delivered());
			long deliveredBefore = server.computeOnServer(s -> Quota.data(s).delivered());
			server.runOnServer(s -> Contracts.start(player(s), Contracts.Contract.SURVEY));
			server.waitFor(s -> player(s).level().dimension() == Level0.LEVEL_0, 200);
			server.runOnServer(s -> LevelExits.use(player(s), "1"));
			server.waitFor(s -> player(s).level().dimension() == Level1.LEVEL_1, 400);
			check(server.computeOnServer(s -> player(s).getAttached(Contracts.ACTIVE).reached()), "reaching Level 1 did not count");
			server.runCommand("execute as @a in minecraft:overworld run tp @s 0 120 0");
			server.waitFor(s -> player(s).level().dimension() == Level.OVERWORLD, 200);
			context.waitFor(client -> client.gui.screen() instanceof MissionReportScreen, 200);
			context.runOnClient(client -> client.gui.screen().onClose());
			// An M.E.G. order: its value goes into the quota (what does not fit is paid out), half of it again as a bonus.
			long reward = Contracts.Contract.SURVEY.reward;
			long expected = credits + reward / 2 + Math.max(0, reward - room);
			check(server.computeOnServer(s -> Wallet.of(player(s))) == expected, "the contract was not paid: "
					+ server.computeOnServer(s -> Wallet.of(player(s))) + " instead of " + expected);
			check(server.computeOnServer(s -> Quota.data(s).delivered()) == deliveredBefore + Math.min(reward, room),
					"the contract did not count toward the quota");
			check(server.computeOnServer(s -> !player(s).hasAttached(Contracts.ACTIVE)), "a finished contract is still in hand");

			// 9. A crafting station: shift-clicking the result into a full inventory takes nothing and uses nothing up.
			server.runOnServer(s -> {
				ServerPlayer p = player(s);
				// A recipe that makes several of one thing, and room in the inventory for only one of them.
				var recipe = dev.noclip.backrooms.crafting.BackroomsRecipeRegistry.all().stream()
						.filter(r -> r.allOutputStacks().size() == 1 && r.allOutputStacks().getFirst().getCount() >= 2).findFirst().orElseThrow();
				var type = recipe.station();
				var menu = new dev.noclip.backrooms.crafting.BackroomsStationMenu(dev.noclip.backrooms.crafting.BackroomsCrafting.menuType(type), 0, p.getInventory(), type);
				ItemStack made = recipe.allOutputStacks().getFirst();
				for (int i = 1; i < 36; i++) p.getInventory().setItem(i, new ItemStack(Items.BEDROCK, 64));
				p.getInventory().setItem(0, made.copyWithCount(made.getMaxStackSize() - 1));
				int gw = type.gridWidth();
				for (int y = 0; y < recipe.patternHeight(); y++) {
					for (int x = 0; x < recipe.patternWidth(); x++) {
						var cell = recipe.cellAt(x, y);
						if (cell != null) menu.inputGrid().setItem((recipe.centeredOffsetY() + y) * gw + recipe.centeredOffsetX() + x, new ItemStack(cell.resolveItem(), cell.count()));
					}
				}
				check(!menu.getSlot(menu.outputSlotStart()).getItem().isEmpty(), "the recipe did not show a result");
				int gridBefore = 0;
				for (int i = 0; i < menu.inputSlotCount(); i++) gridBefore += menu.inputGrid().getItem(i).getCount();
				check(menu.quickMoveStack(p, menu.outputSlotStart()).isEmpty(), "a result was moved into a full inventory");
				int gridAfter = 0;
				for (int i = 0; i < menu.inputSlotCount(); i++) gridAfter += menu.inputGrid().getItem(i).getCount();
				check(gridAfter == gridBefore, "the ingredients were used up with nowhere to put the result");
				for (int i = 0; i < menu.inputSlotCount(); i++) menu.inputGrid().setItem(i, ItemStack.EMPTY);
				p.getInventory().clearContent();
			});
		}
	}

	/** How much health a well-fed player regains in ten seconds where they stand. */
	/** Sells to the buyer standing next to the player, through the shop like a click would. */
	private static Shops.Outcome sell(MinecraftServer server, String item, int count) {
		var offers = Shops.offers(ShopRole.BUYER);
		for (int i = 0; i < offers.size(); i++) {
			var offer = offers.get(i);
			if (offer.fromPlayer() && net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(offer.goods().getItem()).toString().equals(item)) {
				return Shops.trade(player(server), ShopRole.BUYER, i, Math.max(1, count / offer.goods().getCount()));
			}
		}
		throw new AssertionError("the buyer does not take " + item);
	}

	private static float regen(ClientGameTestContext context, TestServerContext server) {
		server.runOnServer(s -> {
			ServerPlayer p = player(s);
			p.setHealth(4.0F);
			p.getFoodData().setFoodLevel(20);
			p.getFoodData().setSaturation(0.0F);
		});
		context.waitTicks(200);
		return server.computeOnServer(s -> player(s).getHealth()) - 4.0F;
	}

	private static void toLevel0(TestServerContext server) {
		server.runCommand("execute as @a in noclip_backrooms:level_0 run tp @s 3.5 " + STAND_Y + " 3.5");
		server.waitFor(s -> player(s).level().dimension() == Level0.LEVEL_0, 200);
	}

	private static void die(ClientGameTestContext context, TestServerContext server) {
		server.runCommand("kill @a");
		context.waitTicks(20);
		context.runOnClient(client -> client.player.respawn());
		context.waitTicks(20);
	}

	private static float maxHealth(TestServerContext server) {
		return server.computeOnServer(s -> player(s).getMaxHealth());
	}

	private static Hound hound(MinecraftServer server, int id) {
		return (Hound) player(server).level().getEntity(id);
	}

	private static ServerPlayer player(MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}

	private static void check(boolean ok, String message) {
		if (!ok) throw new AssertionError(message);
	}
}
