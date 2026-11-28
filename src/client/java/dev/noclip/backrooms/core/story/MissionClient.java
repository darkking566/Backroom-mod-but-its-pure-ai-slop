package dev.noclip.backrooms.core.story;

import dev.noclip.backrooms.BackroomsMod;
import dev.noclip.backrooms.core.Home;
import dev.noclip.backrooms.core.floors.FloorsClient;
import dev.noclip.backrooms.core.story.MissionSyncPayload.ObjLine;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * Client-side mission cache, Steam-style top-left checklist HUD, and Mission Board opener.
 */
public final class MissionClient {
	private static MissionSyncPayload state = MissionSyncPayload.EMPTY;

	private MissionClient() {
	}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(MissionSyncPayload.TYPE, (payload, ctx) -> state = payload);
		ClientPlayNetworking.registerGlobalReceiver(MissionBoardPayload.TYPE, (payload, ctx) -> {
			state = payload.activeState();
			ctx.client().execute(() -> ctx.client().gui.setScreen(new MissionBoardScreen(payload)));
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> state = MissionSyncPayload.EMPTY);
		HudElementRegistry.addLast(BackroomsMod.id("missions"), MissionClient::hud);
	}

	public static MissionSyncPayload state() {
		return state;
	}

	private static void hud(GuiGraphicsExtractor g, DeltaTracker delta) {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.level == null || client.gui.hud.isHidden() || client.gui.screen() != null) return;

		boolean atHome = Home.isHome(client.level.dimension());
		int startY = atHome
				? (dev.noclip.backrooms.core.quota.QuotaClient.status().active() ? 52 : 10)
				: Math.max(46, FloorsClient.hudBottomY() + 6);
		Font font = client.font;

		if (atHome && !state.briefed()) {
			String prompt = Component.translatable("hud.noclip_backrooms.mission.goto_commander").getString();
			String compass = formatCompassLabel(client);
			String full = compass.isEmpty() ? prompt : prompt + "  [" + compass + "]";
			int w = font.width(full) + 12;
			g.fill(8, startY - 2, 8 + w, startY + 12, 0x9912161C);
			g.fill(8, startY - 2, 10, startY + 12, 0xFFE6C84F);
			g.text(font, full, 13, startY + 1, 0xFFFFEB99, true);
			return;
		}

		if (!state.hasExploration() && !state.hasBounty()) return;

		List<String> lines = new ArrayList<>();
		List<Integer> colors = new ArrayList<>();

		if (state.hasExploration()) {
			String header = Component.translatable(state.expTitleKey(), state.expLevel()).getString();
			lines.add(header);
			colors.add(state.expReady() ? 0xFF7BE88C : 0xFF6CD6FF);

			for (ObjLine o : state.expObjectives()) {
				String box = o.done() ? "☑ " : "☐ ";
				String text = box + Component.translatable(o.labelKey()).getString() + " (" + o.progress() + "/" + o.goal() + ")";
				lines.add(text);
				colors.add(o.done() ? 0xFF9DE8A8 : 0xFFEAEAEA);
			}
			if (state.expReady()) {
				String turnIn = Component.translatable("hud.noclip_backrooms.mission.ready_turnin").getString();
				lines.add("► " + turnIn);
				colors.add(0xFFFFD866);
			}
		}

		if (state.hasBounty()) {
			String bPrefix = Component.translatable("hud.noclip_backrooms.mission.bounty_prefix").getString();
			String bName = Component.translatable(state.bountyTitleKey()).getString();
			String box = state.bountyReady() ? "☑ " : "☐ ";
			lines.add(box + bPrefix + bName + " (" + state.bountyProgress() + "/" + state.bountyGoal() + ")");
			colors.add(state.bountyReady() ? 0xFFFFD866 : 0xFFF2AE66);
		}

		String compassLine = formatCompassLabel(client);
		if (!compassLine.isEmpty()) {
			String targetDesc = state.hintLabelKey().isEmpty()
					? ""
					: Component.translatable(state.hintLabelKey()).getString();
			lines.add("◎ " + (targetDesc.isEmpty() ? compassLine : targetDesc + " [" + compassLine + "]"));
			colors.add(0xFF9EDBFF);
		}

		int maxW = 90;
		for (String l : lines) {
			maxW = Math.max(maxW, Math.round(font.width(l) * 0.88F));
		}
		int panelW = maxW + 14;
		int rowH = 11;
		int panelH = lines.size() * rowH + 6;
		int x = 10;

		g.fill(x - 2, startY - 2, x + panelW, startY + panelH, 0x8A10141A);
		g.fill(x - 2, startY - 2, x, startY + panelH, state.expReady() || state.bountyReady() ? 0xFF66E688 : 0xFF52B8E6);

		float scale = 0.88F;
		for (int i = 0; i < lines.size(); i++) {
			int ry = startY + 2 + i * rowH;
			g.pose().pushMatrix();
			g.pose().scale(scale, scale);
			g.text(font, lines.get(i), Math.round((x + 4) / scale), Math.round(ry / scale), colors.get(i), true);
			g.pose().popMatrix();
		}
	}

	private static String formatCompassLabel(Minecraft client) {
		if (!state.hasHint() || client.player == null) return "";
		String raw = Missions.formatCompass(client.player.getX(), client.player.getZ(), client.player.getYRot(),
				state.hintX(), state.hintZ());
		if ("NEAR".equals(raw)) {
			return Component.translatable("hud.noclip_backrooms.mission.near").getString();
		}
		return raw;
	}
}
