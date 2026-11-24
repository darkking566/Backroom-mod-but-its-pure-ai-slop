package dev.noclip.backrooms.level2;

import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

/**
 * Not a check: draws a 240x240-block map of Level 2 around the origin, for looking at the floor plan.
 * Runs only when LEVEL2_MAP names the PNG to write, for example
 * {@code LEVEL2_MAP=map.png ./gradlew test --tests dev.noclip.backrooms.level2.MapDump}.
 */
class MapDump {
	@Test
	void dump() throws Exception {
		String out = System.getenv("LEVEL2_MAP");
		if (out == null) return;
		int size = Level2Plan.SIZE * 3, origin = -Level2Plan.SIZE, scale = 4;
		BufferedImage image = new BufferedImage(size * scale, size * scale, BufferedImage.TYPE_INT_RGB);
		for (int i = 0; i < size; i++) {
			for (int j = 0; j < size; j++) {
				int x = origin + i, z = origin + j;
				Level2Layout.Tile tile = Level2Layout.tile(12345, x, z);
				int rgb = switch (tile.kind()) {
					case WALL -> 0x2a2724;
					case FLOOR -> 0x8c8a84;
					case BANK -> 0xd8cfa8;
					case MACHINE -> 0x4f7f9f;
					case DOOR -> 0x00ff00;
					case LOCKED_DOOR -> 0xff7f00;
					case EXIT -> 0xff00ff;
					case WINDOW -> 0x00ffff;
					case ROOM -> 0xb0a080;
					case VOID -> 0x000000;
					case VOID_WALL -> 0x101030;
					case CRATE -> 0x9a6a30;
					case DRUM -> 0x5a6a5a;
					case SCRAP -> 0x707070;
					case CABINET -> 0x3060a0;
					case TOOLBOX -> 0xb03028;
					case CABLE_REEL, LUMBER_STACK -> 0x8a643c;
				};
				if (tile.kind() == Level2Layout.Kind.FLOOR && Level2Layout.hot(12345, x, z)) rgb = 0xc07050;
				switch (Level2Layout.light(12345, x, z)) {
					case WHITE -> rgb = 0xffffff;
					case WARM, BULB -> rgb = 0xffc060;
					case VIOLET -> rgb = 0xa040ff;
					default -> {
					}
				}
				for (int a = 0; a < scale; a++) {
					for (int b = 0; b < scale; b++) image.setRGB(i * scale + a, j * scale + b, rgb);
				}
			}
		}
		ImageIO.write(image, "png", new File(out));
	}
}
