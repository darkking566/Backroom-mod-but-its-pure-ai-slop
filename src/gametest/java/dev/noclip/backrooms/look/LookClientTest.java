package dev.noclip.backrooms.look;

import dev.noclip.backrooms.core.look.Look;
import dev.noclip.backrooms.level0.Level0;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.state.LightmapRenderState;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;

/**
 * The found-footage look (core.look.Look) in Level 0: the same views with the drawn textures, with the realistic
 * resource pack, and with the pack and the camcorder filter, for comparing side by side. Run alone with
 * gradlew runClientGameTest -Plevels=look
 */
public final class LookClientTest implements FabricClientGameTest {
	/** Level 0's floor is at y=32 (Level0ChunkGenerator.FLOOR_Y, not visible from here). */
	private static final int STAND_Y = 33;
	/** Where to stand and where to look: x, z, yaw, pitch. */
	private static final float[][] VIEWS = {{3.5F, 3.5F, 0, 5}, {3.5F, 3.5F, 90, 5}, {3.5F, 3.5F, 180, 5}, {3.5F, 3.5F, 270, 5},
			{3.5F, 3.5F, 90, -50}, {3.5F, 3.5F, 0, 45}};

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext game = context.worldBuilder().create()) {
			var server = game.getServer();
			server.runCommand("gamerule send_command_feedback false");
			server.runCommand("gamerule noclip_backrooms:edge_shift false");
			server.runCommand("gamemode spectator @a");
			int standY = STAND_Y;
			server.runCommand("execute as @a in noclip_backrooms:level_0 run tp @s 3.5 " + standY + " 3.5");
			server.waitFor(s -> player(s).level().dimension() == Level0.LEVEL_0, 200);
			game.getConnection().waitForChunksRender();

			check(context.computeOnClient(client -> client.getResourcePackRepository().getSelectedIds().contains(packId(client))),
					"the realistic pack is not on by default");
			context.waitTicks(5);
			check(context.computeOnClient(client -> Look.CAMCORDER.equals(client.gameRenderer.currentPostEffect())), "the camcorder filter is not on in Level 0");

			shoot(context, game, false, false, "drawn");
			shoot(context, game, true, false, "realistic");
			shoot(context, game, true, true, "camcorder");
			checkNightVision(context, game);

			// Out of the Backrooms the filter goes off.
			server.runCommand("execute as @a in minecraft:overworld run tp @s 0 120 0");
			server.waitFor(s -> player(s).level().dimension() != Level0.LEVEL_0, 200);
			context.waitTicks(5);
			check(context.computeOnClient(client -> client.gameRenderer.currentPostEffect() == null), "the camcorder filter stayed on outside the Backrooms");
		}
	}

	private static void shoot(ClientGameTestContext context, TestSingleplayerContext game, boolean realistic, boolean camcorder, String label) {
		setPack(context, realistic);
		context.runOnClient(client -> Look.setEnabled(camcorder));
		game.getConnection().waitForChunksRender();
		context.waitTicks(10);
		for (int i = 0; i < VIEWS.length; i++) {
			float[] v = VIEWS[i];
			game.getServer().runCommand(String.format(Locale.ROOT, "execute as @a in noclip_backrooms:level_0 run tp @s %.1f %d %.1f %.0f %.0f",
					v[0], STAND_Y, v[1], v[2], v[3]));
			context.waitTicks(12);
			context.takeScreenshot("look_level0_" + i + "_" + label);
		}
	}

	private static void checkNightVision(ClientGameTestContext context, TestSingleplayerContext game) {
		var server = game.getServer();
		float[] v = VIEWS[0];
		server.runCommand(String.format(Locale.ROOT, "execute as @a in noclip_backrooms:level_0 run tp @s %.1f %d %.1f %.0f %.0f",
				v[0], STAND_Y, v[1], v[2], v[3]));
		context.waitTicks(10);

		check(context.computeOnClient(client -> {
			LightmapRenderState light = new LightmapRenderState();
			light.brightness = 0.5F;
			light.nightVisionEffectIntensity = 0.0F;
			Look.falloff(light);

			FogData fog = new FogData();
			fog.environmentalStart = 6.0F;
			fog.environmentalEnd = 32.0F;
			fog.renderDistanceEnd = 128.0F;
			Look.darken(client.gameRenderer.mainCamera(), fog, 1.0F);
			return light.brightness < 0.0F && fog.environmentalEnd <= 30.0F;
		}), "normal Backrooms darkness/fog should stay active without Night Vision");

		server.runCommand("effect give @a minecraft:night_vision 30 0 true");
		context.waitFor(c -> c.player != null && c.player.hasEffect(MobEffects.NIGHT_VISION), 100);
		context.waitTicks(12);

		check(context.computeOnClient(client -> {
			LightmapRenderState light = new LightmapRenderState();
			light.brightness = 0.5F;
			light.nightVisionEffectIntensity = 1.0F;
			Look.falloff(light);

			FogData fog = new FogData();
			fog.environmentalStart = 6.0F;
			fog.environmentalEnd = 32.0F;
			fog.renderDistanceEnd = 128.0F;
			Look.darken(client.gameRenderer.mainCamera(), fog, 1.0F);
			return light.brightness >= 0.89F && fog.environmentalStart >= 48.0F && fog.environmentalEnd >= 120.0F;
		}), "Night Vision should lift lightmap brightness and push Backrooms dark fog outward");

		context.takeScreenshot("look_level0_0_night_vision");
		context.runOnClient(client -> Look.setEnabled(false));
		context.waitTicks(8);
		context.takeScreenshot("look_level0_0_night_vision_clean");
		context.runOnClient(client -> Look.setEnabled(true));

		server.runCommand("effect clear @a");
		context.waitFor(c -> c.player != null && !c.player.hasEffect(MobEffects.NIGHT_VISION), 100);
		context.waitTicks(5);

		check(context.computeOnClient(client -> {
			LightmapRenderState light = new LightmapRenderState();
			light.brightness = 0.5F;
			light.nightVisionEffectIntensity = 0.0F;
			Look.falloff(light);

			FogData fog = new FogData();
			fog.environmentalStart = 6.0F;
			fog.environmentalEnd = 32.0F;
			fog.renderDistanceEnd = 128.0F;
			Look.darken(client.gameRenderer.mainCamera(), fog, 1.0F);
			return light.brightness < 0.0F && fog.environmentalEnd <= 30.0F;
		}), "clearing Night Vision should restore Backrooms darkness and close fog");
	}

	private static void setPack(ClientGameTestContext context, boolean on) {
		CompletableFuture<Void> reload = context.computeOnClient(client -> {
			var packs = client.getResourcePackRepository();
			String id = packId(client);
			List<String> selected = new ArrayList<>(packs.getSelectedIds());
			if (selected.contains(id) == on) return null;
			if (on) selected.add(id);
			else selected.remove(id);
			packs.setSelected(selected);
			return client.reloadResourcePacks();
		});
		if (reload != null) context.waitFor(client -> reload.isDone() && client.gui.overlay() == null, 2400);
	}

	private static String packId(net.minecraft.client.Minecraft client) {
		return client.getResourcePackRepository().getAvailableIds().stream().filter(id -> id.contains(Look.PACK.getPath()))
				.findFirst().orElseThrow(() -> new AssertionError("no realistic pack among " + client.getResourcePackRepository().getAvailableIds()));
	}

	private static ServerPlayer player(MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}

	private static void check(boolean ok, String message) {
		if (!ok) throw new AssertionError(message);
	}
}
