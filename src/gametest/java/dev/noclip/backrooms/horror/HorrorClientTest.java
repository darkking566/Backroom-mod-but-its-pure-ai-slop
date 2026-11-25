package dev.noclip.backrooms.horror;

import dev.noclip.backrooms.core.rpg.PlayerRpg;
import dev.noclip.backrooms.level0.Level0;
import dev.noclip.backrooms.monsters.Monsters;
import dev.noclip.backrooms.monsters.hound.Hound;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.Level;

/**
 * The horror direction's numbers (docs/HORROR_ESCAPE_PLAN.md): the player has half the health in the Backrooms and
 * gains a heart and 10 sanity every five levels; a hound has two and a half times its old health and, badly hurt by the
 * player, stops hunting and backs away. Run alone with gradlew runClientGameTest -Plevels=horror
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

			check(maxHealth(server) == 20.0F, "Overworld max health is not 20: " + maxHealth(server));
			server.runCommand("execute as @a in noclip_backrooms:level_0 run tp @s 3.5 " + STAND_Y + " 3.5");
			server.waitFor(s -> player(s).level().dimension() == Level0.LEVEL_0, 200);
			server.waitFor(s -> player(s).getMaxHealth() == 10.0F, 40);
			check(server.computeOnServer(s -> player(s).getHealth()) <= 10.0F, "health not cut down to the new maximum");

			server.runOnServer(s -> PlayerRpg.grantXp(player(s), 510));
			check(server.computeOnServer(s -> PlayerRpg.get(player(s)).level()) == 5, "510 XP is not level 5");
			check(maxHealth(server) == 12.0F, "level 5 in the Backrooms is not 12 max health: " + maxHealth(server));
			check(server.computeOnServer(s -> PlayerRpg.get(player(s)).maxSanity()) == 60.0F, "level 5 is not 60 max sanity");

			server.runCommand("execute as @a in minecraft:overworld run tp @s 0 120 0");
			server.waitFor(s -> player(s).level().dimension() == Level.OVERWORLD, 200);
			server.waitFor(s -> player(s).getMaxHealth() == 22.0F, 40);
			// Health keeps its share across the change (10 of 12 in the Backrooms is 10/12 of 22 at home).
			check(Math.abs(server.computeOnServer(s -> player(s).getHealth()) - 22.0F * 10.0F / 12.0F) < 0.01F,
					"health did not keep its share when leaving: " + server.computeOnServer(s -> player(s).getHealth()));

			// A hound, hurt to a quarter of its health by the player, stops hunting and backs away.
			server.runCommand("execute as @a in noclip_backrooms:level_0 run tp @s 3.5 " + STAND_Y + " 3.5");
			server.waitFor(s -> player(s).level().dimension() == Level0.LEVEL_0, 200);
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
				Hound hound = hound(s, id);
				hound.hurtServer(p.level(), p.damageSources().playerAttack(p), 57.0F);
			});
			check(server.computeOnServer(s -> hound(s, id).getHealth()) <= 75.0F * 0.3F, "the hound was not hurt enough");
			context.waitTicks(120);
			check(server.computeOnServer(s -> hound(s, id).getTarget()) == null, "a badly hurt hound still hunts the player");
			double after = server.computeOnServer(s -> hound(s, id).distanceTo(player(s)));
			check(after > before + 4, "a badly hurt hound did not back away: " + before + " -> " + after);
			context.takeScreenshot("horror_hound_backs_away");
		}
	}

	private static float maxHealth(net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext server) {
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
