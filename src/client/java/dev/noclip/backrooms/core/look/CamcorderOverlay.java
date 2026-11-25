package dev.noclip.backrooms.core.look;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * A camcorder's viewfinder writing: a blinking REC beside the battery (the top left is the floor, see core.floors),
 * the tape counter and the date and time.
 */
final class CamcorderOverlay {
	private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("MMM. d yyyy", Locale.ENGLISH);
	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("h:mm:ss a", Locale.ENGLISH);
	private static final float SCALE = 1.5F;
	private static final int TEXT = 0xE6F2F0E6, RED = 0xFFE8302A;

	private CamcorderOverlay() {
	}

	static void extract(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft client = Minecraft.getInstance();
		if (!Look.active(client) || client.gui.hud.isHidden()) return;
		Font font = client.font;
		int width = (int) (graphics.guiWidth() / SCALE), height = (int) (graphics.guiHeight() / SCALE);
		graphics.pose().pushMatrix();
		graphics.pose().scale(SCALE, SCALE);

		int rec = width - 40 - font.width("REC");
		if (System.currentTimeMillis() / 700 % 2 == 0) graphics.text(font, "●", rec - 10, 8, RED, true);
		graphics.text(font, "REC", rec, 8, TEXT, true);

		// The battery, two bars left.
		int bx = width - 30, by = 8;
		graphics.fill(bx, by, bx + 18, by + 1, TEXT);
		graphics.fill(bx, by + 8, bx + 18, by + 9, TEXT);
		graphics.fill(bx, by, bx + 1, by + 9, TEXT);
		graphics.fill(bx + 17, by, bx + 18, by + 9, TEXT);
		graphics.fill(bx + 18, by + 3, bx + 20, by + 6, TEXT);
		for (int i = 0; i < 2; i++) graphics.fill(bx + 2 + i * 5, by + 2, bx + 6 + i * 5, by + 7, TEXT);

		int seconds = Look.recordingTicks() / 20;
		graphics.text(font, String.format(Locale.ROOT, "SP  %d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60), 10, height - 16, TEXT, true);
		LocalDateTime now = LocalDateTime.now();
		String time = now.format(TIME), date = now.format(DATE).toUpperCase(Locale.ROOT);
		graphics.text(font, time, width - 10 - font.width(time), height - 26, TEXT, true);
		graphics.text(font, date, width - 10 - font.width(date), height - 16, TEXT, true);
		graphics.pose().popMatrix();
	}
}
