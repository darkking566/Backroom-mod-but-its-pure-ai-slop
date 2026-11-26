package dev.noclip.backrooms.core.trip;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * The mission report on getting back to the institute (docs/project/1-gameplay/README.md, "任務報告畫面"): the trip,
 * the haul and what M.E.G. would pay for it, the quota, the institute's clearance, and M.E.G.'s notes. Closing it shows
 * "Day N of the mission" in yellow.
 */
public final class MissionReportScreen extends Screen {
	private static final int W = 300, PAD = 10, LINE = 11;
	private static final int BACK = 0xEE0E1215, EDGE = 0xFF3FA7A7, TEXT = 0xFFD8E0E0, DIM = 0xFF8A9696, GOLD = 0xFFFFD25A, RED = 0xFFFF6A5A,
			GREEN = 0xFF7CE08A;
	private final MissionReportPayload report;
	private int left, top, height_;
	private int buttonX, buttonY;
	private static final int BUTTON_W = 70, BUTTON_H = 16;

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(MissionReportPayload.TYPE, (payload, context) ->
				context.client().gui.setScreen(new MissionReportScreen(payload)));
	}

	MissionReportScreen(MissionReportPayload report) {
		super(Component.translatable("report.noclip_backrooms.title"));
		this.report = report;
	}

	public MissionReportPayload report() {
		return report;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private List<Component> lines() {
		List<Component> lines = new ArrayList<>();
		lines.add(Component.translatable("report.noclip_backrooms.floors", report.floor(), String.join(" → ", report.levels().stream()
				.map(l -> "Level " + l).toList())));
		return lines;
	}

	/** The notes, wrapped to the panel's width. */
	private List<net.minecraft.util.FormattedCharSequence> wrapped = List.of();

	@Override
	protected void init() {
		wrapped = new ArrayList<>();
		for (Component note : report.notes()) wrapped.addAll(font.split(note, W - PAD * 2));
		int rows = 1 + 1 + Math.max(1, (report.haul().size() + 1) / 2) * 2 + 1 + 3 + wrapped.size() + 2;
		height_ = PAD * 2 + rows * LINE + BUTTON_H + 6;
		left = (width - W) / 2;
		top = Math.max(4, (height - height_) / 2);
		buttonX = left + (W - BUTTON_W) / 2;
		buttonY = top + height_ - PAD - BUTTON_H;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		super.extractRenderState(g, mouseX, mouseY, delta);
		g.fill(left - 1, top - 1, left + W + 1, top + height_ + 1, EDGE);
		g.fill(left, top, left + W, top + height_, BACK);
		int x = left + PAD, y = top + PAD;
		g.text(font, title.copy().withStyle(ChatFormatting.BOLD), x, y, EDGE, false);
		y += LINE + 4;
		for (Component line : lines()) {
			g.text(font, line, x, y, TEXT, false);
			y += LINE;
		}
		y += 2;
		g.text(font, Component.translatable("report.noclip_backrooms.haul"), x, y, DIM, false);
		y += LINE;
		if (report.haul().isEmpty()) {
			g.text(font, Component.translatable("report.noclip_backrooms.haul_none"), x + 4, y + 4, DIM, false);
			y += LINE * 2;
		} else {
			for (int i = 0; i < report.haul().size(); i++) {
				int col = i % 2, row = i / 2;
				int ix = x + col * (W / 2 - PAD), iy = y + row * (LINE * 2);
				ItemStack stack = report.haul().get(i);
				g.item(stack, ix, iy);
				g.itemDecorations(font, stack, ix, iy);
				g.text(font, Component.translatable("report.noclip_backrooms.value", report.values().get(i)), ix + 20, iy + 4, GOLD, false);
			}
			y += ((report.haul().size() + 1) / 2) * LINE * 2;
		}
		g.text(font, Component.translatable("report.noclip_backrooms.total", report.total()), x, y, GOLD, false);
		y += LINE + 4;
		if (report.active()) {
			g.text(font, Component.translatable("report.noclip_backrooms.quota", report.delivered(), report.required(), report.daysLeft()), x, y, TEXT, false);
			y += LINE;
			if (report.debt() > 0) {
				g.text(font, Component.translatable("report.noclip_backrooms.debt", report.debt()), x, y, RED, false);
				y += LINE;
			}
		}
		g.text(font, Component.translatable("report.noclip_backrooms.clearance", report.clearance()), x, y, GREEN, false);
		y += LINE;
		for (var line : wrapped) {
			g.text(font, line, x, y, TEXT, false);
			y += LINE;
		}
		boolean over = mouseX >= buttonX && mouseX < buttonX + BUTTON_W && mouseY >= buttonY && mouseY < buttonY + BUTTON_H;
		g.fill(buttonX, buttonY, buttonX + BUTTON_W, buttonY + BUTTON_H, over ? 0xFF2E7C7C : 0xFF1E4F4F);
		Component label = Component.translatable("report.noclip_backrooms.continue");
		g.text(font, label, buttonX + (BUTTON_W - font.width(label)) / 2, buttonY + 4, 0xFFFFFFFF, false);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.x() >= buttonX && event.x() < buttonX + BUTTON_W && event.y() >= buttonY && event.y() < buttonY + BUTTON_H) {
			onClose();
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public void onClose() {
		super.onClose();
		// Now the day, big and yellow, as at the start of every day.
		Minecraft client = Minecraft.getInstance();
		if (report.active()) {
			client.gui.hud.setTimes(10, 70, 30);
			client.gui.hud.setSubtitle(report.debt() > 0
					? Component.translatable("quota.noclip_backrooms.day_sub_debt", report.debt()).withStyle(ChatFormatting.RED)
					: Component.translatable("quota.noclip_backrooms.day_sub", report.delivered(), report.required(), report.daysLeft()));
			client.gui.hud.setTitle(Component.translatable("quota.noclip_backrooms.day_title", report.day()).withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD));
		}
	}
}
