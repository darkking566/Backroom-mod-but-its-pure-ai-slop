package dev.noclip.backrooms.horror;

import dev.noclip.backrooms.core.LevelExits;
import dev.noclip.backrooms.core.LevelGates;
import dev.noclip.backrooms.core.economy.Wallet;
import dev.noclip.backrooms.core.floors.FloorDifficulty;
import dev.noclip.backrooms.core.floors.Floors;
import dev.noclip.backrooms.core.floors.FloorsClient;
import dev.noclip.backrooms.core.guild.Contracts;
import dev.noclip.backrooms.core.locker.Lockers;
import dev.noclip.backrooms.core.locker.RelayService;
import dev.noclip.backrooms.core.locker.Safebox;
import dev.noclip.backrooms.core.quest.Survivors;
import dev.noclip.backrooms.core.rpg.Badges;
import dev.noclip.backrooms.core.rpg.InventoryStatsPanel;
import dev.noclip.backrooms.core.rpg.PlayerRpg;
import dev.noclip.backrooms.core.rpg.PlayerStats;
import dev.noclip.backrooms.core.rpg.SurvivalXp;
import dev.noclip.backrooms.core.trade.Appraisal;
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
import dev.noclip.backrooms.items.BackroomsItems;
import dev.noclip.backrooms.props.BackroomsProps;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import dev.noclip.backrooms.level0.Level0;
import dev.noclip.backrooms.level1.Level1;
import dev.noclip.backrooms.level1.Level1Items;
import dev.noclip.backrooms.level2.Level2;
import dev.noclip.backrooms.monsters.Monsters;
import dev.noclip.backrooms.monsters.hound.Hound;
import dev.noclip.backrooms.weapons.BackroomsMeleeItem;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelData;
import org.lwjgl.glfw.GLFW;

/**
 * The horror direction (docs/企劃書/1-遊戲玩法/README.md) end to end:
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
			// Not a newcomer: the first trip's cable (core.story.FirstTrip) belongs to the story test.
			server.runOnServer(s -> s.getPlayerList().getPlayers().forEach(p -> p.setAttached(dev.noclip.backrooms.core.story.FirstTrip.DONE, true)));
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

			// 6. The M.E.G. cycle (docs/企劃書/1-遊戲玩法/README.md): day 1 on getting out, the clock runs only at home,
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

			// 10. Priority-1 core exploration loop: dual-axis difficulty & anti-farm, safebox, relay & gear insurance, and expanded contracts.
			server.runCommand("execute as @a in minecraft:overworld run tp @s 0 120 0");
			server.waitFor(s -> player(s).level().dimension() == Level.OVERWORLD, 200);
			context.waitTicks(45);
			context.runOnClient(client -> {
				if (client.gui.screen() != null) client.gui.screen().onClose();
			});
			context.waitTicks(5);

			// 10a. Dual-axis scaling: walking >250 blocks from landing raises Band to 1 and scales hound health.
			server.runCommand("effect give @a resistance infinite 4 true");
			toLevel0(server);
			check(server.computeOnServer(s -> FloorDifficulty.of(player(s)).band()) == 0, "fresh landing was not Band 0");
			server.runCommand("execute as @a in noclip_backrooms:level_0 run tp @s 455.5 " + STAND_Y + " 3.5");
			game.getConnection().waitForChunksRender();
			context.waitFor(client -> FloorsClient.difficulty().active() && FloorsClient.difficulty().band() == 1, 80);
			int scaledHoundId = server.computeOnServer(s -> {
				ServerPlayer p = player(s);
				Hound h = Monsters.HOUND.create(p.level(), EntitySpawnReason.COMMAND);
				h.snapTo(p.getX() + 3, STAND_Y, p.getZ(), 0, 0);
				h.setNoAi(true);
				p.level().addFreshEntity(h);
				return h.getId();
			});
			float scaledHp = server.computeOnServer(s -> hound(s, scaledHoundId).getMaxHealth());
			check(Math.abs(scaledHp - 75.0F * 1.35F) < 0.5F, "Band 1 hound max health expected ~101.25, got " + scaledHp);
			server.runCommand("kill @e[type=noclip_backrooms:hound]");

			// 10b. Anti-farm ping-pong: 0 -> 1 -> 0 reaches floor 3 with 0 penalty; bouncing back to 1 freezes floor & adds penalty.
			server.runOnServer(s -> LevelExits.use(player(s), "1"));
			server.waitFor(s -> player(s).level().dimension() == Level1.LEVEL_1, 400);
			check(server.computeOnServer(s -> Floors.of(player(s)).floor()) == 2 && server.computeOnServer(s -> FloorDifficulty.farmPenalty(player(s))) == 0,
					"0 -> 1 did not advance cleanly to floor 2");
			server.runOnServer(s -> LevelExits.use(player(s), "0"));
			server.waitFor(s -> player(s).level().dimension() == Level0.LEVEL_0, 400);
			check(server.computeOnServer(s -> Floors.of(player(s)).floor()) == 3 && server.computeOnServer(s -> FloorDifficulty.farmPenalty(player(s))) == 0,
					"single backtrack 0 -> 1 -> 0 should still reach floor 3 cleanly");
			server.runOnServer(s -> LevelExits.use(player(s), "1"));
			server.waitFor(s -> player(s).level().dimension() == Level1.LEVEL_1, 400);
			check(server.computeOnServer(s -> Floors.of(player(s)).floor()) == 3 && server.computeOnServer(s -> FloorDifficulty.farmPenalty(player(s))) >= 1,
					"rapid A->B->A->B bounce did not freeze floor or apply anti-farm penalty");

			// 10c. Portable safebox: keeps protected items on death in Backrooms, expands at home for credits.
			game.getConnection().waitForChunksRender();
			context.waitTicks(10);
			check(server.computeOnServer(s -> Safebox.capacity(player(s))) == 2, "initial safebox capacity was not 2");
			server.runOnServer(s -> Safebox.put(player(s), 0, new ItemStack(Items.DIAMOND, 1)));
			server.runCommand("item replace entity @a hotbar.0 with minecraft:dirt 1");
			die(context, server);
			server.waitFor(s -> player(s).isAlive() && player(s).level().dimension() == Level0.LEVEL_0, 200);
			check(server.computeOnServer(s -> player(s).getInventory().countItem(Items.DIRT)) == 0, "unprotected dirt survived Backrooms death");
			check(server.computeOnServer(s -> Safebox.count(player(s), Items.DIAMOND)) == 1, "safebox item was lost on Backrooms death");

			server.runCommand("effect give @a resistance infinite 4 true");
			server.runOnServer(s -> Institute.receive(player(s)));
			server.waitFor(s -> player(s).level().dimension() == Home.FRONTROOMS, 200);
			context.waitFor(client -> client.gui.screen() instanceof MissionReportScreen, 200);
			context.runOnClient(client -> client.gui.screen().onClose());
			context.waitTicks(5);
			server.runOnServer(s -> Wallet.set(player(s), 500));
			check(server.computeOnServer(s -> Safebox.upgrade(player(s), false)), "safebox upgrade failed");
			check(server.computeOnServer(s -> Safebox.capacity(player(s))) == 3 && server.computeOnServer(s -> Wallet.of(player(s))) == 200,
					"safebox capacity/cost mismatch after upgrade");
			server.runOnServer(s -> Safebox.open(player(s)));
			context.waitFor(client -> client.gui.screen() instanceof ContainerScreen, 100);
			context.waitTicks(5);
			context.takeScreenshot("horror_safebox");
			context.runOnClient(client -> client.gui.screen().onClose());
			context.waitTicks(5);

			// 10d. Relay drop-off box + single-trip gear insurance: ship mid-run and recover inventory on death.
			toLevel0(server);
			game.getConnection().waitForChunksRender();
			context.waitTicks(10);
			check(server.computeOnServer(s -> RelayService.shipToLocker(player(s),
					List.of(new ItemStack(Items.APPLE, 3), new ItemStack(Items.COAL, 2)))), "relay drop-off failed");
			check(server.computeOnServer(s -> Wallet.of(player(s))) == 170
					&& server.computeOnServer(s -> Lockers.count(player(s), Items.APPLE)) == 3
					&& server.computeOnServer(s -> Lockers.count(player(s), Items.COAL)) == 2,
					"relayed items or fee did not reach institute locker");
			check(server.computeOnServer(s -> RelayService.buyInsurance(player(s), false)), "failed to buy single-trip gear insurance");
			check(server.computeOnServer(s -> RelayService.isInsured(player(s))) && server.computeOnServer(s -> Wallet.of(player(s))) == 50,
					"insurance state or fee mismatch");
			server.runCommand("item replace entity @a hotbar.0 with minecraft:emerald 1");
			die(context, server);
			server.waitFor(s -> player(s).isAlive() && player(s).level().dimension() == Level0.LEVEL_0, 200);
			check(server.computeOnServer(s -> !RelayService.isInsured(player(s))), "gear insurance remained active after payout");
			check(server.computeOnServer(s -> Lockers.count(player(s), Items.EMERALD)) == 1, "insured inventory was not rescued to locker");
			check(server.computeOnServer(s -> Safebox.count(player(s), Items.DIAMOND)) == 1, "safebox item lost during insured death");

			// 10e. Expanded contracts: hunt hounds, courier 0 -> 1 -> 2, and collect delivery.
			server.runCommand("effect give @a resistance infinite 4 true");
			server.runCommand("clear @a");
			server.runCommand("execute as @a in minecraft:overworld run tp @s 0 120 0");
			server.waitFor(s -> player(s).level().dimension() == Level.OVERWORLD, 200);
			context.waitFor(client -> client.gui.screen() instanceof MissionReportScreen, 200);
			context.runOnClient(client -> client.gui.screen().onClose());
			context.waitTicks(5);

			// Hunt 2 hounds contract
			check(Contracts.available().contains(Contracts.Contract.HUNT_HOUNDS), "HUNT_HOUNDS missing from available contracts");
			long creditsBeforeHunt = server.computeOnServer(s -> Wallet.of(player(s)));
			server.runOnServer(s -> Contracts.start(player(s), Contracts.Contract.HUNT_HOUNDS));
			server.waitFor(s -> player(s).level().dimension() == Level0.LEVEL_0, 200);
			for (int k = 0; k < 2; k++) {
				server.runOnServer(s -> {
					ServerPlayer p = player(s);
					Hound target = Monsters.HOUND.create(p.level(), EntitySpawnReason.COMMAND);
					target.snapTo(p.getX() + 1.5, STAND_Y, p.getZ(), 0, 0);
					target.setNoAi(true);
					p.level().addFreshEntity(target);
					target.setHealth(1.0F);
					target.hurtServer(p.level(), p.level().damageSources().playerAttack(p), 50.0F);
				});
			}
			check(server.computeOnServer(s -> player(s).getAttached(Contracts.ACTIVE) != null && player(s).getAttached(Contracts.ACTIVE).reached()),
					"killing 2 hounds did not complete HUNT_HOUNDS contract");
			server.runCommand("execute as @a in minecraft:overworld run tp @s 0 120 0");
			server.waitFor(s -> player(s).level().dimension() == Level.OVERWORLD, 200);
			context.waitFor(client -> client.gui.screen() instanceof MissionReportScreen, 200);
			context.runOnClient(client -> client.gui.screen().onClose());
			context.waitTicks(5);
			check(server.computeOnServer(s -> Wallet.of(player(s))) > creditsBeforeHunt, "HUNT_HOUNDS contract did not pay out");

			// Multi-level courier 0 -> 1 -> 2 contract
			server.runOnServer(s -> Contracts.start(player(s), Contracts.Contract.COURIER_012));
			server.waitFor(s -> player(s).level().dimension() == Level0.LEVEL_0, 200);
			check(server.computeOnServer(s -> player(s).getAttached(Contracts.ACTIVE).progress()) == 1, "courier leg 1 (Level 0) failed");
			server.runOnServer(s -> LevelExits.use(player(s), "1"));
			server.waitFor(s -> player(s).level().dimension() == Level1.LEVEL_1, 400);
			check(server.computeOnServer(s -> player(s).getAttached(Contracts.ACTIVE).progress()) == 2, "courier leg 2 (Level 1) failed");
			server.runOnServer(s -> LevelExits.use(player(s), "2"));
			server.waitFor(s -> player(s).level().dimension() == Level2.LEVEL_2, 400);
			check(server.computeOnServer(s -> player(s).getAttached(Contracts.ACTIVE).reached()), "courier leg 3 (Level 2) did not complete");
			server.runCommand("execute as @a in minecraft:overworld run tp @s 0 120 0");
			server.waitFor(s -> player(s).level().dimension() == Level.OVERWORLD, 200);
			context.waitFor(client -> client.gui.screen() instanceof MissionReportScreen, 200);
			context.runOnClient(client -> client.gui.screen().onClose());
			context.waitTicks(5);

			// Collect delivery contract (4 almond water -> keeps remaining 1, pays out)
			long creditsBeforeCollect = server.computeOnServer(s -> Wallet.of(player(s)));
			server.runOnServer(s -> Contracts.start(player(s), Contracts.Contract.COLLECT_ALMOND));
			server.waitFor(s -> player(s).level().dimension() == Level0.LEVEL_0, 200);
			server.runOnServer(s -> player(s).getInventory().add(new ItemStack(Level1Items.ALMOND_WATER, 5)));
			server.runOnServer(s -> Institute.receive(player(s)));
			server.waitFor(s -> player(s).level().dimension() == Home.FRONTROOMS, 200);
			context.waitFor(client -> client.gui.screen() instanceof MissionReportScreen, 200);
			context.runOnClient(client -> client.gui.screen().onClose());
			check(server.computeOnServer(s -> player(s).getInventory().countItem(Level1Items.ALMOND_WATER)) == 1,
					"COLLECT_ALMOND did not consume exact goal count (4 of 5)");
			check(server.computeOnServer(s -> Wallet.of(player(s))) > creditsBeforeCollect, "COLLECT_ALMOND contract did not pay out");
			context.waitTicks(5);

			// 11. Priority-2 RPG depth: hidden stats via scrolls (Option 1), high-risk health cost melee,
			// Martial Master badge unlock & safe-zone switch, extra survival XP, and Inventory [Adv.] tab.
			server.runCommand("clear @a");
			server.runCommand("gamemode survival @a");

			// 11a. Hidden stats: first scroll unlocks at 1, repeat scroll grants +1 free point; unlocked stat accepts points.
			server.runOnServer(s -> {
				ServerPlayer p = player(s);
				PlayerRpg.set(p, PlayerRpg.get(p).addStatPoints(1));
				ItemStack luckA = new ItemStack(BackroomsItems.FORBIDDEN_SCROLL_LUCK, 2);
				ItemStack psy = new ItemStack(BackroomsItems.FORBIDDEN_SCROLL_PSY, 1);
				ItemStack tech = new ItemStack(BackroomsItems.FORBIDDEN_SCROLL_TECH, 1);
				PlayerRpg.applyForbiddenScroll(p, PlayerStats.StatKind.LUCK, luckA);
				PlayerRpg.applyForbiddenScroll(p, PlayerStats.StatKind.LUCK, luckA);
				PlayerRpg.applyForbiddenScroll(p, PlayerStats.StatKind.PSI, psy);
				PlayerRpg.applyForbiddenScroll(p, PlayerStats.StatKind.ENGINEERING, tech);
				PlayerRpg.set(p, PlayerRpg.get(p).allocate(PlayerStats.StatKind.ENGINEERING));
			});
			check(server.computeOnServer(s -> {
				PlayerStats st = PlayerRpg.get(player(s));
				return st.luckUnlocked() && st.luck() == 2 && st.psiUnlocked() && st.psi() == 1
						&& st.engUnlocked() && st.eng() == 2;
			}), "forbidden scroll Option 1 unlock/boost or hidden stat allocation failed");

			// 11b. High-risk heavy melee health toll locks when health is too low and drains safely when healthy.
			server.runOnServer(s -> {
				ServerPlayer p = player(s);
				p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(BackroomsItems.WEST_VOID_GREATSWORD));
				p.setHealth(2.0F);
				InteractionResult lowRes = ((BackroomsMeleeItem) p.getMainHandItem().getItem())
						.use(p.level(), p, InteractionHand.MAIN_HAND);
				check(lowRes == InteractionResult.FAIL && Math.abs(p.getHealth() - 2.0F) < 0.05F,
						"high-risk weapon did not lock when player health was critically low");
				p.setHealth(10.0F);
				InteractionResult okRes = ((BackroomsMeleeItem) p.getMainHandItem().getItem())
						.use(p.level(), p, InteractionHand.MAIN_HAND);
				check(okRes != InteractionResult.FAIL && Math.abs(p.getHealth() - 7.5F) < 0.05F,
						"high-risk weapon did not deduct 2.5 HP: " + p.getHealth());
			});

			// 11c. Martial Master badge unlock with Lost Old Coins, safe-zone toggle & Vanguard Breaker 50% toll cut.
			server.runOnServer(s -> {
				ServerPlayer p = player(s);
				Badges.spawnVendor(p.level(), p.getX() + 1.5, p.getY(), p.getZ());
				p.getInventory().add(new ItemStack(BackroomsItems.LOST_OLD_COIN, 12));
				check(Badges.unlock(p, Badges.Badge.VANGUARD_BREAKER, true), "failed to unlock Vanguard Breaker");
				check(Badges.unlock(p, Badges.Badge.ANOMALY_SCHOLAR, true), "failed to unlock Anomaly Scholar");
				check(Badges.countCoins(p.getInventory()) == 0, "coins were not deducted cleanly on badge unlock");
				check(Badges.equip(p, Badges.Badge.VANGUARD_BREAKER, true), "failed to equip badge inside safe zone");
			});
			context.waitTicks(45); // Wait out VOID_GREATSWORD 40-tick cooldown
			server.runOnServer(s -> {
				ServerPlayer p = player(s);
				p.setHealth(10.0F);
				InteractionResult res = ((BackroomsMeleeItem) p.getMainHandItem().getItem())
						.use(p.level(), p, InteractionHand.MAIN_HAND);
				check(res != InteractionResult.FAIL && Math.abs(p.getHealth() - 8.75F) < 0.05F,
						"Vanguard Breaker did not halve high-risk weapon health cost: " + p.getHealth());
			});
			toLevel0(server);
			game.getConnection().waitForChunksRender();
			context.waitTicks(10);
			check(server.computeOnServer(s -> !Badges.inSafeZone(player(s))
					&& !Badges.equip(player(s), Badges.Badge.ANOMALY_SCHOLAR, true)
					&& Badges.active(player(s)) == Badges.Badge.VANGUARD_BREAKER),
					"badge swap should be blocked outside safe zones");

			server.runOnServer(s -> Institute.receive(player(s)));
			server.waitFor(s -> player(s).level().dimension() == Home.FRONTROOMS, 200);
			context.waitFor(client -> client.gui.screen() instanceof MissionReportScreen, 200);
			context.runOnClient(client -> client.gui.screen().onClose());
			context.waitTicks(5);

			// 11d. Survival XP: first-time field note read (+15 XP once) & buyer delivery XP.
			server.runOnServer(s -> {
				ServerPlayer p = player(s);
				long xpBeforeNote = (long) PlayerRpg.get(p).level() * 10000L + PlayerRpg.get(p).xp();
				check(SurvivalXp.recordNoteRead(p, "test:manila_manual"), "first note read did not grant XP");
				check(!SurvivalXp.recordNoteRead(p, "test:manila_manual"), "duplicate note read granted XP twice");
				long afterNote = (long) PlayerRpg.get(p).level() * 10000L + PlayerRpg.get(p).xp();
				check(afterNote != xpBeforeNote, "note read did not change player XP");
				long xpBeforeSale = afterNote;
				SurvivalXp.onBuyerSale(p, 200L);
				long afterSale = (long) PlayerRpg.get(p).level() * 10000L + PlayerRpg.get(p).xp();
				check(afterSale > xpBeforeSale, "buyer sale did not grant survival XP");
			});

			// 11e. Open inventory switched to [Adv.] tab to inspect hidden stats & Specialization Badge UI.
			context.runOnClient(client -> InventoryStatsPanel.selectTab(1));
			context.getInput().pressKey(GLFW.GLFW_KEY_E);
			context.waitTicks(5);
			context.takeScreenshot("horror_rpg_advanced");
			context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
			context.waitTicks(5);
			context.runOnClient(client -> InventoryStatsPanel.selectTab(0));

			// 12a. Conditional level gates framework & contract deadlock integration.
			server.runCommand("gamemode survival @a");
			server.runOnServer(s -> {
				ServerPlayer p = player(s);
				LevelGates.registerGate(LevelGates.Gate.of("1", 2, "", false, "test:power", ""));
				check(!LevelGates.tryPass(p, "1"), "gate should block without T2 hacker and flag");
				check(!Contracts.isCompletableFor(Contracts.Contract.SURVEY, p),
						"contract requiring gated Level 1 should be filtered as unreachable");

				ItemStack t2 = new ItemStack(BackroomsItems.KEYPAD_HACKER_T2);
				p.getInventory().add(t2);
				LevelGates.grantFlag(s, "test:power");
				check(Contracts.isCompletableFor(Contracts.Contract.SURVEY, p),
						"contract should become reachable once gate requirements are met");
				check(LevelGates.tryPass(p, "1"), "gate should allow passage with T2 hacker and flag");
				check(LevelGates.isLevelUnlocked(s, "1"), "gate breach did not persist unlocked state");

				LevelGates.clearGate("1");
				LevelGates.clearAll(s);
				p.getInventory().clearContent();
			});

			// 12b. Tiered chests: ANOMALY_VAULT_CHEST (T2+ hacker) and BOSS_TROPHY_CHEST (guardian kill flag).
			toLevel0(server);
			game.getConnection().waitForChunksRender();
			context.waitTicks(10);
			server.runCommand("kill @e[type=!player]");
			server.runOnServer(s -> {
				ServerPlayer p = player(s);
				ServerLevel lvl = p.level();
				BlockPos vaultPos = new BlockPos(5, STAND_Y, 3);
				BlockPos bossChestPos = new BlockPos(5, STAND_Y, 5);
				lvl.setBlockAndUpdate(vaultPos, BackroomsProps.ANOMALY_VAULT_CHEST.defaultBlockState());
				lvl.setBlockAndUpdate(bossChestPos, BackroomsProps.BOSS_TROPHY_CHEST.defaultBlockState());

				BlockHitResult vaultHit = new BlockHitResult(Vec3.atCenterOf(vaultPos), Direction.UP, vaultPos, false);
				p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
				p.gameMode.useItemOn(p, lvl, ItemStack.EMPTY, InteractionHand.MAIN_HAND, vaultHit);
				check(lvl.getBlockState(vaultPos).is(BackroomsProps.ANOMALY_VAULT_CHEST),
						"anomaly vault chest opened without a tool");

				ItemStack t1 = new ItemStack(BackroomsItems.KEYPAD_HACKER_T1);
				p.setItemInHand(InteractionHand.MAIN_HAND, t1);
				p.gameMode.useItemOn(p, lvl, t1, InteractionHand.MAIN_HAND, vaultHit);
				check(lvl.getBlockState(vaultPos).is(BackroomsProps.ANOMALY_VAULT_CHEST),
						"anomaly vault chest should reject Tier 1 hacker");

				ItemStack t2 = new ItemStack(BackroomsItems.KEYPAD_HACKER_T2);
				p.setItemInHand(InteractionHand.MAIN_HAND, t2);
				p.gameMode.useItemOn(p, lvl, t2, InteractionHand.MAIN_HAND, vaultHit);
				check(!lvl.getBlockState(vaultPos).is(BackroomsProps.ANOMALY_VAULT_CHEST),
						"anomaly vault chest failed to unseal with Tier 2 hacker");

				Hound boss = Monsters.HOUND.create(lvl, EntitySpawnReason.EVENT);
				check(boss != null, "failed to create boss hound");
				boss.snapTo(5.5, STAND_Y, 6.5, 0.0F, 0.0F);
				boss.addTag(LevelGates.BOSS_ENTITY_TAG);
				lvl.addFreshEntity(boss);

				BlockHitResult bossHit = new BlockHitResult(Vec3.atCenterOf(bossChestPos), Direction.UP, bossChestPos, false);
				p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
				p.gameMode.useItemOn(p, lvl, ItemStack.EMPTY, InteractionHand.MAIN_HAND, bossHit);
				check(lvl.getBlockState(bossChestPos).is(BackroomsProps.BOSS_TROPHY_CHEST),
						"boss trophy chest opened while nearby boss guardian was alive");

				boss.hurtServer(lvl, lvl.damageSources().playerAttack(p), 9999.0F);
				check(LevelGates.hasFlag(s, "boss:hound"), "defeating tagged boss did not grant boss flag");
				p.gameMode.useItemOn(p, lvl, ItemStack.EMPTY, InteractionHand.MAIN_HAND, bossHit);
				check(!lvl.getBlockState(bossChestPos).is(BackroomsProps.BOSS_TROPHY_CHEST),
						"boss trophy chest stayed locked after guardian died");
			});

			// 12c. Black market floor scaling, field gear repair, and paid guide shortcut.
			server.runOnServer(s -> {
				check(Shops.offersForFloor(ShopRole.BLACK_MARKET, 1).size()
						< Shops.offersForFloor(ShopRole.BLACK_MARKET, 3).size(),
						"black market should unlock new offers by Floor 3");
				check(Shops.offersForFloor(ShopRole.BLACK_MARKET, 3).size()
						< Shops.offersForFloor(ShopRole.BLACK_MARKET, 5).size(),
						"black market should unlock top offers by Floor 5");
				LevelExits.fallInto(player(s), "1");
			});
			server.waitFor(s -> player(s).level().dimension() == Level1.LEVEL_1, 300);
			game.getConnection().waitForChunksRender();
			context.waitTicks(10);
			server.runOnServer(s -> {
				ServerPlayer p = player(s);
				Shops.spawnBlackMarket(p.level(), p.getX() + 1.5, p.getY(), p.getZ());
				Wallet.set(p, 1000L);
				ItemStack bat = new ItemStack(BackroomsItems.BLUNT_NAILED_BAT);
				bat.setDamageValue(80);
				p.setItemInHand(InteractionHand.MAIN_HAND, bat);
				check(RelayService.repairMainHand(p, true), "field repair failed near black market");
				check(p.getMainHandItem().getDamageValue() == 0 && Wallet.of(p) < 1000L,
						"field repair did not reset durability and charge credits");
				check(RelayService.shortcutTargets(p).contains("0"), "visited Level 0 missing from shortcut targets");
				check(RelayService.shortcut(p, "0", true), "paid guide shortcut from Level 1 to Level 0 failed");
			});
			server.waitFor(s -> player(s).level().dimension() == Level0.LEVEL_0, 300);
			game.getConnection().waitForChunksRender();
			context.waitTicks(10);

			// 12d. Paid geode appraisal at Black Market / Buyer.
			server.runCommand("kill @e[type=!player]");
			server.runOnServer(s -> {
				ServerPlayer p = player(s);
				Shops.spawnBlackMarket(p.level(), p.getX() + 1.5, p.getY(), p.getZ());
				Wallet.set(p, 500L);
				p.getInventory().add(new ItemStack(BackroomsItems.RUSTED_OFFICE_SAFE, 1));
				p.getInventory().add(new ItemStack(BackroomsItems.ENCRYPTED_HARD_DRIVE, 1));
				check(Appraisal.hasAppraisable(p), "player should carry appraisable geodes");
				check(Appraisal.appraise(p, "safe", true), "safe appraisal failed");
				check(Appraisal.appraise(p, "drive", true), "encrypted drive appraisal failed");
				check(!Appraisal.hasAppraisable(p), "geodes were not consumed by appraisal");
				long expectedCredits = 500L - Appraisal.SAFE_FEE - Appraisal.DRIVE_FEE;
				check(Wallet.of(p) == expectedCredits, "expected wallet " + expectedCredits + " after appraisal, got " + Wallet.of(p));
			});

			// 12e. Trapped survivor side-quest (feed supply -> return keepsake -> escort to relay -> rewards & quest flag).
			server.runCommand("kill @e[type=!player]");
			UUID girlId = server.computeOnServer(s -> {
				ServerPlayer p = player(s);
				Person girl = Survivors.spawn(p.level(), p.getX() + 1.5, p.getY(), p.getZ(), Survivors.Script.LITTLE_GIRL);
				check(girl != null && Survivors.stageOf(p.level(), girl.getUUID()) == Survivors.Stage.STRANDED,
						"survivor did not initialize in STRANDED stage");
				p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Level1Items.ALMOND_WATER));
				UseEntityCallback.EVENT.invoker().interact(p, p.level(), InteractionHand.MAIN_HAND, girl, null);
				check(Survivors.stageOf(p.level(), girl.getUUID()) == Survivors.Stage.SEEKING_ITEM,
						"feeding almond water did not advance survivor to SEEKING_ITEM");
				check(Survivors.keepsakePosOf(p.level(), girl.getUUID()) != null, "keepsake target spot missing");
				return girl.getUUID();
			});
			context.waitTicks(6);
			server.runOnServer(s -> {
				ServerPlayer p = player(s);
				Person girl = (Person) p.level().getEntity(girlId);
				check(girl != null, "survivor missing before keepsake hand-in");
				ItemStack keepsake = Survivors.createKeepsake(girlId, Survivors.Script.LITTLE_GIRL);
				p.setItemInHand(InteractionHand.MAIN_HAND, keepsake);
				UseEntityCallback.EVENT.invoker().interact(p, p.level(), InteractionHand.MAIN_HAND, girl, null);
				check(Survivors.stageOf(p.level(), girlId) == Survivors.Stage.ESCORTING,
						"returning keepsake did not advance survivor to ESCORTING");
				// Spawn a relay box beside the player to mark a valid safe point and trigger escort completion on tick.
				p.level().setBlockAndUpdate(p.blockPosition().north(), BackroomsProps.RELAY_DROPOFF_BOX.defaultBlockState());
			});
			server.waitFor(s -> Survivors.stageOf(player(s).level(), girlId) == Survivors.Stage.RESCUED, 80);
			check(server.computeOnServer(s -> LevelGates.hasFlag(s, "quest:little_girl")),
					"completing little_girl rescue did not grant quest progression flag");
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
		server.waitFor(s -> !player(s).isAlive(), 100);
		context.waitTicks(10);
		context.runOnClient(client -> client.player.respawn());
		server.waitFor(s -> player(s).isAlive(), 100);
		context.waitTicks(10);
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
