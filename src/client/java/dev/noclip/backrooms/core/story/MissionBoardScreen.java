package dev.noclip.backrooms.core.story;

import dev.noclip.backrooms.core.economy.Wallet;
import dev.noclip.backrooms.core.story.MissionBoardPayload.BoardEntry;
import dev.noclip.backrooms.core.story.MissionBoardPayload.BountyEntry;
import dev.noclip.backrooms.core.story.MissionSyncPayload.ObjLine;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/**
 * M.E.G. Commander Mission Board UI with two tabs:
 * Tab 0: Randomized Exploration Contracts per unlocked floor (Level 0 - Level 4);
 * Tab 1: Player-selectable Combat & Boss Bounties.
 */
public final class MissionBoardScreen extends Screen {
	private static final int W = 326, H = 230;
	private static final int PANEL = 0xFFC6C6C6, DARK = 0xFF373737, LIGHT = 0xFFFFFFFF, SHADE = 0xFF8B8B8B;
	private static final int TEXT = 0xFF282828, MUTED = 0xFF555555;

	private final MissionBoardPayload data;
	private int left, top;
	private int activeTab = 0; // 0 = Exploration, 1 = Bounties
	private String selectedLevel;
	private int bountyScroll = 0;

	public MissionBoardScreen(MissionBoardPayload data) {
		super(Component.translatable("ui.noclip_backrooms.mission.board_title"));
		this.data = data;
		MissionSyncPayload active = data.activeState();
		if (active.hasExploration() && !active.expLevel().isEmpty()) {
			this.selectedLevel = active.expLevel();
		} else if (!data.unlockedLevels().isEmpty()) {
			this.selectedLevel = data.unlockedLevels().getFirst();
		} else {
			this.selectedLevel = "0";
		}
	}

	@Override
	protected void init() {
		left = (width - W) / 2;
		top = (height - H) / 2;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		super.extractRenderState(g, mouseX, mouseY, delta);
		Player player = minecraft == null ? null : minecraft.player;
		long wallet = player == null ? 0 : Wallet.of(player);

		bevel(g, left, top, W, H, PANEL, LIGHT, SHADE);
		g.text(font, title, left + 10, top + 8, TEXT, false);

		String statsTxt = Component.translatable("ui.noclip_backrooms.mission.completed_count", data.completedCount(), wallet).getString();
		g.text(font, statsTxt, left + W - 10 - font.width(statsTxt), top + 8, 0xFF6B4C00, false);

		// Top Tabs: Exploration vs Bounties
		drawTabBtn(g, left + 10, top + 22, 148, 16, activeTab == 0, mouseX, mouseY,
				Component.translatable("ui.noclip_backrooms.mission.tab_exp").getString());
		drawTabBtn(g, left + 168, top + 22, 148, 16, activeTab == 1, mouseX, mouseY,
				Component.translatable("ui.noclip_backrooms.mission.tab_bounty").getString());

		if (activeTab == 0) {
			renderExplorationTab(g, mouseX, mouseY);
		} else {
			renderBountyTab(g, mouseX, mouseY);
		}
	}

	private void renderExplorationTab(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		MissionSyncPayload sync = data.activeState();
		// Top banner: current active exploration status
		int bx = left + 10, by = top + 43, bw = W - 20, bh = 36;
		g.fill(bx, by, bx + bw, by + bh, 0xFF2B2F36);
		g.fill(bx + 1, by + 1, bx + bw - 1, by + bh - 1, sync.hasExploration() ? 0xFF333B45 : 0xFF282B30);

		if (sync.hasExploration()) {
			String ttl = Component.translatable(sync.expTitleKey(), sync.expLevel()).getString();
			g.text(font, ttl, bx + 7, by + 5, sync.expReady() ? 0xFF7BE88C : 0xFF6CD6FF, true);
			String rew = Component.translatable("ui.noclip_backrooms.mission.reward", sync.expReward()).getString();
			g.text(font, rew, bx + 7, by + 19, 0xFFFFDC73, true);

			if (sync.expReady()) {
				drawActionBtn(g, bx + bw - 96, by + 8, 88, 20, 0xFF388E3C, 0xFF4CAF50, mouseX, mouseY,
						Component.translatable("ui.noclip_backrooms.mission.turnin_btn").getString());
			} else {
				drawActionBtn(g, bx + bw - 196, by + 8, 62, 20, 0xFF2E6A96, 0xFF3B85BC, mouseX, mouseY,
						Component.translatable("ui.noclip_backrooms.mission.deploy_btn").getString());
				drawActionBtn(g, bx + bw - 130, by + 8, 76, 20, 0xFF5D5280, 0xFF7567A3, mouseX, mouseY,
						Component.translatable("ui.noclip_backrooms.mission.camera_btn").getString());
				drawActionBtn(g, bx + bw - 50, by + 8, 42, 20, 0xFF8C3B3B, 0xFFB04C4C, mouseX, mouseY,
						Component.translatable("ui.noclip_backrooms.mission.abandon_btn").getString());
			}
		} else {
			g.text(font, Component.translatable("ui.noclip_backrooms.mission.banner_none"), bx + 8, by + 13, 0xFFB8C0CC, true);
		}

		// Left column: Level selector list (0..4)
		int lx = left + 10, ly = top + 85, lw = 84;
		List<BoardEntry> offers = data.explorationOffers();
		BoardEntry selectedOffer = offers.isEmpty() ? null : offers.getFirst();
		for (int i = 0; i < offers.size(); i++) {
			BoardEntry e = offers.get(i);
			boolean unlocked = data.unlockedLevels().contains(e.level());
			boolean sel = e.level().equals(selectedLevel);
			if (sel) selectedOffer = e;
			int ry = ly + i * 26;
			boolean hov = inside(mouseX, mouseY, lx, ry, lw, 22);
			int fill = !unlocked ? 0xFF9A9A9A : (sel ? 0xFFE6D48A : (hov ? 0xFFDADADA : 0xFFB8B8B8));
			bevel(g, lx, ry, lw, 22, fill, unlocked ? LIGHT : 0xFFB0B0B0, SHADE);
			String label = unlocked ? "Level " + e.level() : "Level " + e.level() + " 🔒";
			g.text(font, label, lx + 8, ry + 7, unlocked ? (sel ? 0xFF3A2C00 : TEXT) : 0xFF606060, false);
		}

		// Right detail box for selected floor's randomized contract
		int dx = left + 100, dy = top + 85, dw = W - 110, dh = 134;
		g.fill(dx, dy, dx + dw, dy + dh, SHADE);
		g.fill(dx + 1, dy + 1, dx + dw - 1, dy + dh - 1, 0xFFB6B6B6);

		if (selectedOffer != null) {
			boolean unlocked = data.unlockedLevels().contains(selectedOffer.level());
			g.text(font, Component.translatable(sync.expTitleKey().isEmpty() ? "mission.noclip_backrooms.exp_title" : sync.expTitleKey(),
					selectedOffer.level()), dx + 8, dy + 7, 0xFF143C5E, false);
			String rewTxt = Component.translatable("ui.noclip_backrooms.mission.reward", selectedOffer.reward()).getString();
			g.text(font, rewTxt, dx + dw - 8 - font.width(rewTxt), dy + 7, 0xFF705000, false);

			List<ObjLine> objs = (sync.hasExploration() && selectedOffer.level().equals(sync.expLevel()))
					? sync.expObjectives()
					: selectedOffer.objectives();

			for (int i = 0; i < objs.size(); i++) {
				ObjLine o = objs.get(i);
				int oy = dy + 25 + i * 20;
				g.fill(dx + 6, oy, dx + dw - 6, oy + 17, 0xFFC8C8C8);
				String box = o.done() ? "☑ " : "☐ ";
				String text = box + Component.translatable(o.labelKey()).getString();
				g.text(font, text, dx + 10, oy + 5, o.done() ? 0xFF1D6E25 : TEXT, false);
				String prog = o.done() ? "OK" : ("0/" + o.goal());
				if (sync.hasExploration() && selectedOffer.level().equals(sync.expLevel())) {
					prog = o.progress() + "/" + o.goal();
				}
				g.text(font, prog, dx + dw - 12 - font.width(prog), oy + 5, o.done() ? 0xFF1D6E25 : MUTED, false);
			}

			int btnY = dy + dh - 28;
			if (!unlocked) {
				String lockMsg = Component.translatable("ui.noclip_backrooms.mission.level_locked", selectedOffer.level()).getString();
				g.centeredText(font, lockMsg, dx + dw / 2, btnY + 6, 0xFF8C2020);
			} else {
				drawActionBtn(g, dx + 8, btnY, 102, 20, 0xFF2F7A4D, 0xFF3E9C64, mouseX, mouseY,
						Component.translatable("ui.noclip_backrooms.mission.accept_btn").getString());
				drawActionBtn(g, dx + 116, btnY, 92, 20, 0xFF4F5D73, 0xFF667894, mouseX, mouseY,
						Component.translatable("ui.noclip_backrooms.mission.reroll_btn").getString());
			}
		}
	}

	private void renderBountyTab(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		MissionSyncPayload sync = data.activeState();
		int bx = left + 10, by = top + 43, bw = W - 20, bh = 32;
		g.fill(bx, by, bx + bw, by + bh, 0xFF2B2F36);
		g.fill(bx + 1, by + 1, bx + bw - 1, by + bh - 1, 0xFF332D28);

		if (sync.hasBounty()) {
			String txt = Component.translatable(sync.bountyTitleKey()).getString()
					+ " (" + sync.bountyProgress() + "/" + sync.bountyGoal() + ") · "
					+ Component.translatable("ui.noclip_backrooms.mission.reward", sync.bountyReward()).getString();
			g.text(font, txt, bx + 8, by + 12, sync.bountyReady() ? 0xFF7BE88C : 0xFFFFD866, true);
			if (sync.bountyReady()) {
				drawActionBtn(g, bx + bw - 92, by + 6, 84, 20, 0xFF388E3C, 0xFF4CAF50, mouseX, mouseY,
						Component.translatable("ui.noclip_backrooms.mission.turnin_btn").getString());
			} else {
				drawActionBtn(g, bx + bw - 54, by + 6, 46, 20, 0xFF8C3B3B, 0xFFB04C4C, mouseX, mouseY,
						Component.translatable("ui.noclip_backrooms.mission.abandon_btn").getString());
			}
		} else {
			g.text(font, Component.translatable("ui.noclip_backrooms.mission.bounty_none"), bx + 8, by + 12, 0xFFD0C4A8, true);
		}

		// List up to 4 visible rows at a time
		int lx = left + 10, ly = top + 82, lw = W - 20, rowH = 33, visible = 4;
		List<BountyEntry> list = data.bountyOffers();
		for (int i = 0; i < visible && bountyScroll + i < list.size(); i++) {
			BountyEntry b = list.get(bountyScroll + i);
			int ry = ly + i * (rowH + 3);
			boolean isCurrent = sync.hasBounty() && sync.bountyId().equals(b.id());
			g.fill(lx, ry, lx + lw, ry + rowH, b.boss() ? 0xFF6B5210 : DARK);
			g.fill(lx + 1, ry + 1, lx + lw - 1, ry + rowH - 1, isCurrent ? 0xFFD8D0A8 : 0xFFB6B6B6);

			String header = Component.translatable(b.titleKey()).getString() + " [Lv." + b.level() + "]";
			if (b.boss()) header = "★ " + header;
			g.text(font, header, lx + 7, ry + 5, b.unlocked() ? (b.boss() ? 0xFF7A4E00 : TEXT) : 0xFF606060, false);
			g.text(font, Component.translatable(b.descKey()), lx + 7, ry + 18, MUTED, false);

			String rew = "+" + b.reward();
			g.text(font, rew, lx + lw - 86 - font.width(rew), ry + 12, 0xFF1E6B1E, false);

			if (!b.unlocked()) {
				String lock = Component.translatable("ui.noclip_backrooms.mission.locked_req", b.reqCompleted()).getString();
				g.fill(lx + lw - 80, ry + 6, lx + lw - 6, ry + 26, 0xFF888888);
				g.centeredText(font, lock, lx + lw - 43, ry + 12, 0xFFDDDDDD);
			} else if (isCurrent && sync.bountyReady()) {
				drawActionBtn(g, lx + lw - 78, ry + 6, 72, 20, 0xFF388E3C, 0xFF4CAF50, mouseX, mouseY,
						Component.translatable("ui.noclip_backrooms.mission.turnin_btn").getString());
			} else {
				drawActionBtn(g, lx + lw - 78, ry + 6, 72, 20, 0xFF8C4A1F, 0xFFB56029, mouseX, mouseY,
						Component.translatable("ui.noclip_backrooms.mission.accept_bounty").getString());
			}
		}
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (activeTab == 1) {
			int max = Math.max(0, data.bountyOffers().size() - 4);
			bountyScroll = Mth.clamp(bountyScroll - (int) Math.signum(scrollY), 0, max);
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		double mx = event.x(), my = event.y();

		if (inside(mx, my, left + 10, top + 22, 148, 16)) {
			activeTab = 0;
			clickSound();
			return true;
		}
		if (inside(mx, my, left + 168, top + 22, 148, 16)) {
			activeTab = 1;
			clickSound();
			return true;
		}

		MissionSyncPayload sync = data.activeState();
		if (activeTab == 0) {
			int bx = left + 10, by = top + 43, bw = W - 20;
			if (sync.hasExploration()) {
				if (sync.expReady() && inside(mx, my, bx + bw - 96, by + 8, 88, 20)) {
					send("turnin_exp", "");
					return true;
				}
				if (!sync.expReady()) {
					if (inside(mx, my, bx + bw - 196, by + 8, 62, 20)) {
						send("deploy", sync.expLevel());
						return true;
					}
					if (inside(mx, my, bx + bw - 130, by + 8, 76, 20)) {
						send("claim_camera", "");
						return true;
					}
					if (inside(mx, my, bx + bw - 50, by + 8, 42, 20)) {
						send("abandon_exp", "");
						return true;
					}
				}
			}

			int lx = left + 10, ly = top + 85, lw = 84;
			List<BoardEntry> offers = data.explorationOffers();
			for (int i = 0; i < offers.size(); i++) {
				int ry = ly + i * 26;
				if (inside(mx, my, lx, ry, lw, 22)) {
					selectedLevel = offers.get(i).level();
					clickSound();
					return true;
				}
			}

			int dx = left + 100, dy = top + 85, dh = 134;
			int btnY = dy + dh - 28;
			boolean unlocked = data.unlockedLevels().contains(selectedLevel);
			if (unlocked) {
				if (inside(mx, my, dx + 8, btnY, 102, 20)) {
					send("accept_exp", selectedLevel);
					return true;
				}
				if (inside(mx, my, dx + 116, btnY, 92, 20)) {
					send("reroll_exp", selectedLevel);
					return true;
				}
			}
		} else {
			int bx = left + 10, by = top + 43, bw = W - 20;
			if (sync.hasBounty()) {
				if (sync.bountyReady() && inside(mx, my, bx + bw - 92, by + 6, 84, 20)) {
					send("turnin_bounty", "");
					return true;
				}
				if (!sync.bountyReady() && inside(mx, my, bx + bw - 54, by + 6, 46, 20)) {
					send("abandon_bounty", "");
					return true;
				}
			}
			int lx = left + 10, ly = top + 82, lw = W - 20, rowH = 33, visible = 4;
			List<BountyEntry> list = data.bountyOffers();
			for (int i = 0; i < visible && bountyScroll + i < list.size(); i++) {
				BountyEntry b = list.get(bountyScroll + i);
				int ry = ly + i * (rowH + 3);
				if (!b.unlocked()) continue;
				if (inside(mx, my, lx + lw - 78, ry + 6, 72, 20)) {
					if (sync.hasBounty() && sync.bountyId().equals(b.id()) && sync.bountyReady()) {
						send("turnin_bounty", "");
					} else {
						send("accept_bounty", b.id());
					}
					return true;
				}
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	private void send(String action, String arg) {
		clickSound();
		ClientPlayNetworking.send(new MissionActionPayload(action, arg));
	}

	private void clickSound() {
		if (minecraft != null) {
			minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
		}
	}

	private void drawTabBtn(GuiGraphicsExtractor g, int x, int y, int w, int h, boolean selected, int mx, int my, String label) {
		boolean hov = inside(mx, my, x, y, w, h);
		int fill = selected ? 0xFFE6D48A : (hov ? 0xFFDADADA : 0xFFB0B0B0);
		bevel(g, x, y, w, h, fill, LIGHT, SHADE);
		float scale = Math.min(1.0F, (w - 8.0F) / Math.max(1, font.width(label)));
		int tw = Math.round(font.width(label) * scale);
		int tx = x + (w - tw) / 2;
		int ty = y + (h - Math.round(7 * scale)) / 2;
		g.pose().pushMatrix();
		g.pose().scale(scale, scale);
		g.text(font, label, Math.round(tx / scale), Math.round(ty / scale), selected ? 0xFF3A2C00 : TEXT, false);
		g.pose().popMatrix();
	}

	private void drawActionBtn(GuiGraphicsExtractor g, int x, int y, int w, int h, int baseColor, int hoverColor,
			int mx, int my, String label) {
		boolean hov = inside(mx, my, x, y, w, h);
		g.fill(x, y, x + w, y + h, 0xFF000000);
		g.fill(x + 1, y + 1, x + w - 1, y + h - 1, hov ? hoverColor : baseColor);
		g.fill(x + 1, y + 1, x + w - 1, y + 2, 0x44FFFFFF);
		g.centeredText(font, label, x + w / 2, y + (h - 7) / 2, 0xFFFFFFFF);
	}

	private static boolean inside(double mx, double my, int x, int y, int w, int h) {
		return mx >= x && mx < x + w && my >= y && my < y + h;
	}

	private static void bevel(GuiGraphicsExtractor g, int x, int y, int w, int h, int fill, int hi, int lo) {
		g.fill(x, y, x + w, y + h, DARK);
		g.fill(x + 1, y + 1, x + w - 1, y + h - 1, fill);
		g.fill(x + 1, y + 1, x + w - 1, y + 2, hi);
		g.fill(x + 1, y + 1, x + 2, y + h - 1, hi);
		g.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, lo);
		g.fill(x + w - 2, y + 1, x + w - 1, y + h - 1, lo);
	}
}
