package dev.noclip.backrooms.level1;

import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

/**
 * Not a check: draws a 320x320-block map of Level 1 around the origin, for looking at the floor plan.
 * Runs only when LEVEL1_MAP names the PNG to write, for example
 * {@code LEVEL1_MAP=map.png ./gradlew test --tests dev.noclip.backrooms.level1.MapDump}.
 */
class MapDump {
	@Test
	void dump() throws Exception {
		String out = System.getenv("LEVEL1_MAP");
		if (out == null) return;
		int size = 64 * 5, origin = -64 * 2, scale = 3;
		BufferedImage image = new BufferedImage(size * scale, size * scale, BufferedImage.TYPE_INT_RGB);
		for (int i = 0; i < size; i++) {
			for (int j = 0; j < size; j++) {
				int x = origin + i, z = origin + j;
				int rgb = switch (Level1Layout.tile(12345, x, z)) {
					case FLOOR -> 0x8a8a86;
					case LINE -> 0xd8c040;
					case DAMP -> 0x6a7078;
					case PUDDLE -> 0x3a4a60;
					case PILLAR -> 0x303030;
					case SHELF -> 0x5a4030;
					case HALL_WALL -> 0x101010;
					case CORRIDOR -> 0xd8d8d0;
					case DESK -> 0x8040c0;
					case ROOM_SHELF -> 0xa06030;
					case BED -> 0xe0e0ff;
					case TABLE -> 0x8060a0;
					case GALLERY -> 0xe0c060;
					case WALL -> 0x404858;
					case BRICK -> 0xa03020;
					case PADDED -> 0xf0e0f0;
					case DOOR -> 0x00ff00;
					case PERIMETER -> 0x202838;
					case OPENING -> 0xb0e0b0;
					case OPENING_SIGN -> 0x00c060;
					case EXIT_CORE -> 0xff40ff;
					case HATCH -> 0xff8000;
					case FRAME -> 0x00ffff;
					case STAIRWELL -> 0xff0000;
					case BOOTH -> 0xf0f0f0;
					case ROOM -> 0xc0a0ff;
					case MACHINE -> 0x40a040;
					case CRATE -> 0xffd000;
					case BARRIER -> 0xff2020;
					case FURNITURE -> 0x2040ff;
				};
				var ceiling = Level1Layout.ceiling(12345, x, z);
				if (ceiling == Level1Layout.Ceiling.LAMP || ceiling == Level1Layout.Ceiling.BULB) rgb = 0xffffa0;
				if (ceiling == Level1Layout.Ceiling.EXIT_BULB) rgb = 0x60ff60;
				for (int a = 0; a < scale; a++) {
					for (int b = 0; b < scale; b++) image.setRGB(i * scale + a, j * scale + b, rgb);
				}
			}
		}
		ImageIO.write(image, "png", new File(out));
	}
}
