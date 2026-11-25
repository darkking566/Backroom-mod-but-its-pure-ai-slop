package dev.noclip.backrooms.horror;

import dev.noclip.backrooms.core.LevelExits;
import dev.noclip.backrooms.core.economy.Wallet;
import dev.noclip.backrooms.core.floors.Floors;
import dev.noclip.backrooms.core.guild.Contracts;
import dev.noclip.backrooms.core.rpg.PlayerRpg;
import dev.noclip.backrooms.core.trip.Pull;
import dev.noclip.backrooms.core.trip.PullState;
import dev.noclip.backrooms.core.trip.Trips;
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
 * The horror direction (docs/HORROR_ESCAPE_PLAN.md) end to end:
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

			// 1. Half health everywhere; levels add to it.
			check(maxHealth(server) == 10.0F, "Overworld max health is not 10: " + maxHealth(server));
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
			server.runCommand("execute as @a in noclip_backrooms:level_0 run tp @s 3.5 " + STAND_Y + " 3.5");
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
			check(server.computeOnServer(s -> player(s).getInventory().getItem(0).isEmpty()), "the player kept their things after dying");
			check(server.computeOnServer(s -> !Pull.inGrace(player(s))), "waking up in the Overworld after dying counted as getting out");
			server.runCommand("effect give @a resistance infinite 4 true");

			// 5. Settled: wake at the base; M.E.G. takes them home from there.
			BlockPos base = server.computeOnServer(s -> player(s).blockPosition());
			server.runOnServer(s -> Trips.settle(player(s), LevelData.RespawnData.of(Level0.LEVEL_0, base, 0, 0)));
			check(server.computeOnServer(s -> Trips.settled(player(s))), "settling did not take");
			server.runCommand("execute as @a in noclip_backrooms:level_0 run tp @s " + (base.getX() + 40) + " " + STAND_Y + " " + base.getZ());
			die(context, server);
			server.waitFor(s -> player(s).level().dimension() == Level0.LEVEL_0 && player(s).blockPosition().closerThan(base, 3), 200);
			server.runCommand("effect give @a resistance infinite 4 true");
			int levelBefore = server.computeOnServer(s -> PlayerRpg.get(player(s)).level());
			int xpBefore = server.computeOnServer(s -> PlayerRpg.get(player(s)).xp());
			check(server.computeOnServer(s -> Trips.extract(player(s))), "M.E.G. would not take a settled player home from their base");
			server.waitFor(s -> player(s).level().dimension() == Level.OVERWORLD, 200);
			check(server.computeOnServer(s -> !Trips.settled(player(s))), "home, the player still wakes up in the Backrooms");
			check(server.computeOnServer(s -> Floors.of(player(s)).floor()) == 0, "the trip did not end at home");
			check(server.computeOnServer(s -> Pull.inGrace(player(s))), "no day of peace after getting out");
			check(server.computeOnServer(s -> PlayerRpg.get(player(s)).level()) > levelBefore
					|| server.computeOnServer(s -> PlayerRpg.get(player(s)).xp()) > xpBefore, "no experience for getting out alive");

			// 6. The pull: warning signs, nothing during the day of peace, then down into Level 0.
			long graceUntil = server.computeOnServer(s -> Pull.of(player(s)).graceUntil());
			server.runOnServer(s -> Pull.set(player(s), new PullState(92.0F, graceUntil)));
			server.runCommand("title @a clear");
			context.waitTicks(30);
			context.takeScreenshot("horror_pull_warning");
			server.runCommand("item replace entity @a hotbar.1 with noclip_backrooms:scrap_metal 4");
			check(server.computeOnServer(s -> Pull.relics(player(s))) == 1, "scrap metal from the Backrooms does not count");
			server.runOnServer(s -> Pull.set(player(s), new PullState(99.9F, graceUntil)));
			context.waitTicks(60);
			check(server.computeOnServer(s -> player(s).level().dimension()) == Level.OVERWORLD, "pulled in during the day of peace");
			server.runOnServer(s -> Pull.set(player(s), new PullState(99.9F, 0L)));
			server.waitFor(s -> player(s).level().dimension() == Level0.LEVEL_0, 100);

			// 7. A contract: sent in, reach Level 1, out alive, paid.
			server.runCommand("execute as @a in minecraft:overworld run tp @s 0 120 0");
			server.waitFor(s -> player(s).level().dimension() == Level.OVERWORLD, 200);
			long credits = server.computeOnServer(s -> Wallet.of(player(s)));
			server.runOnServer(s -> Contracts.start(player(s), Contracts.Contract.SURVEY));
			server.waitFor(s -> player(s).level().dimension() == Level0.LEVEL_0, 200);
			server.runOnServer(s -> LevelExits.use(player(s), "1"));
			server.waitFor(s -> player(s).level().dimension() == Level1.LEVEL_1, 400);
			check(server.computeOnServer(s -> player(s).getAttached(Contracts.ACTIVE).reached()), "reaching Level 1 did not count");
			server.runCommand("execute as @a in minecraft:overworld run tp @s 0 120 0");
			server.waitFor(s -> player(s).level().dimension() == Level.OVERWORLD, 200);
			check(server.computeOnServer(s -> Wallet.of(player(s))) == credits + Contracts.Contract.SURVEY.reward, "the contract was not paid");
			check(server.computeOnServer(s -> !player(s).hasAttached(Contracts.ACTIVE)), "a finished contract is still in hand");
		}
	}

	/** How much health a well-fed player regains in ten seconds where they stand. */
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
