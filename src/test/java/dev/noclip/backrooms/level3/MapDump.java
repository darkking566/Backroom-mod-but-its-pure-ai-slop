package dev.noclip.backrooms.level3;

import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

/**
 * Not a check: draws a 288x288-block map of Level 3 around the origin, for looking at the floor plan.
 * Runs only when LEVEL3_MAP names the PNG to write, for example
 * {@code LEVEL3_MAP=map.png ./gradlew test --tests dev.noclip.backrooms.level3.MapDump}.
 */
class MapDump {
	@Test
	void dump() throws Exception {
		String out = System.getenv("LEVEL3_MAP");
		if (out == null) return;
		int size = Level3Plan.SIZE * 3, origin = -Level3Plan.SIZE, scale = 3;
		BufferedImage image = new BufferedImage(size * scale, size * scale, BufferedImage.TYPE_INT_RGB);
		for (int i = 0; i < size; i++) {
			for (int j = 0; j < size; j++) {
				int x = origin + i, z = origin + j;
				Level3Layout.Tile tile = Level3Layout.tile(12345, x, z);
				int rgb = switch (tile.kind()) {
					case WALL -> 0x2a1c16;
					case FLOOR -> switch (tile.clearance()) {
						case 4 -> 0xb0a090;
						case 3 -> 0x908070;
						case 2 -> 0x6a5a4e;
						default -> 0x4a3c34;
					};
					case BARS -> 0xe0e0e0;
					case GATE -> 0x40c0ff;
					case EXIT -> 0xff00ff;
					case HALL -> switch (tile.hall().kind()) {
						case GENERATOR -> 0x4a8a5a;
						case BOILER -> 0xb08a40;
						case ASSEMBLY -> 0x7a7a8a;
						case SANCTUM -> 0xd0b070;
						case CAR -> 0xc04040;
						case BASE -> 0x4060c0;
						case BNTG_STORAGE -> 0x40b0b0;
						case BLUE_SALVATION -> 0x60a0ff;
						case SCYLLA_COLONY -> 0x802050;
					};
					case HALL_WALL -> 0x101010;
					case HALL_GAP -> 0xffffff;
				};
				if (tile.kind() == Level3Layout.Kind.FLOOR && Level3Layout.hotspot(12345, x, z)) rgb = 0x601010;
				switch (Level3Layout.light(12345, x, z)) {
					case FLUORESCENT, BULB -> rgb = 0xffffa0;
					case FLICKERING -> rgb = 0xffa040;
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
