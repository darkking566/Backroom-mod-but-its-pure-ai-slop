package dev.noclip.backrooms.core.field;

/**
 * The picture on a photo: a small fixed-size RGB565 bitmap (so its size on the wire and on disk is known), with the
 * look of a cheap old instant camera baked in: soft low resolution, faded warm colours, grain, dark corners.
 */
public final class PhotoImage {
	public static final int WIDTH = 144, HEIGHT = 108;
	/** Two bytes a pixel; under the 32767 bytes a client may send in one packet. */
	public static final int BYTES = WIDTH * HEIGHT * 2;

	public static final float MAX_ZOOM = 4, MAX_EXPOSURE = 2;

	private PhotoImage() {
	}

	/** A viewfinder zoom a client claims, kept to what the camera can do. */
	public static float clampZoom(float zoom) {
		return zoom >= 1 ? Math.min(zoom, MAX_ZOOM) : 1; // the comparison also turns NaN away
	}

	/** The cosine a thing's direction must beat to be in a frame {@code degrees} wide each side of the middle at ×1. */
	public static double minDot(double degrees, float zoom) {
		return Math.cos(Math.atan(Math.tan(Math.toRadians(degrees)) / zoom));
	}

	/** Reads a pixel of the screen as 0xRRGGBB (alpha ignored). */
	public interface Source {
		int rgb(int x, int y);
	}

	/** The middle 4:3 of a {@code width}×{@code height} screen, shrunk and aged, {@code exposure} stops brighter or darker. */
	public static byte[] develop(Source screen, int width, int height, long seed, float exposure) {
		double gain = Math.pow(2, exposure);
		int cropW = Math.min(width, height * 4 / 3), cropH = Math.min(height, width * 3 / 4);
		int left = (width - cropW) / 2, top = (height - cropH) / 2;
		java.util.Random grain = new java.util.Random(seed);
		byte[] out = new byte[BYTES];
		for (int y = 0; y < HEIGHT; y++) {
			for (int x = 0; x < WIDTH; x++) {
				// The average of a few points under this pixel: soft rather than speckled.
				int r = 0, g = 0, b = 0;
				for (int sy = 0; sy < 3; sy++) {
					for (int sx = 0; sx < 3; sx++) {
						int rgb = screen.rgb(left + Math.min(cropW - 1, (x * 3 + sx) * cropW / (WIDTH * 3)),
								top + Math.min(cropH - 1, (y * 3 + sy) * cropH / (HEIGHT * 3)));
						r += rgb >> 16 & 255;
						g += rgb >> 8 & 255;
						b += rgb & 255;
					}
				}
				double dx = (x + 0.5) / WIDTH - 0.5, dy = (y + 0.5) / HEIGHT - 0.5;
				double middle = 1 - 2 * (dx * dx + dy * dy); // 1 in the middle, 0 in the corners
				double flash = 1 + 0.45 * middle * middle; // the flash lights what is in front
				double dark = 0.55 + 0.45 * Math.sqrt(Math.max(0, middle));
				double noise = (grain.nextDouble() - 0.5) * 22;
				// Lifted blacks, warm cast, short on blue.
				int rr = aged(r / 9.0 * gain, flash * dark, 1.05, 22, noise);
				int gg = aged(g / 9.0 * gain, flash * dark, 0.98, 18, noise);
				int bb = aged(b / 9.0 * gain, flash * dark, 0.78, 20, noise);
				int packed = (rr >> 3) << 11 | (gg >> 2) << 5 | bb >> 3;
				int i = (y * WIDTH + x) * 2;
				out[i] = (byte) (packed >> 8);
				out[i + 1] = (byte) packed;
			}
		}
		return out;
	}

	private static int aged(double value, double light, double cast, double lift, double noise) {
		double v = lift + Math.min(255, value * light) * cast * (255 - lift) / 255 + noise;
		return (int) Math.max(0, Math.min(255, v));
	}

	/** Pixel {@code x, y} of a developed picture, as 0xAARRGGBB. */
	public static int argb(byte[] image, int x, int y) {
		int i = (y * WIDTH + x) * 2;
		int packed = (image[i] & 255) << 8 | image[i + 1] & 255;
		int r = packed >> 11, g = packed >> 5 & 63, b = packed & 31;
		return 0xFF000000 | (r << 3 | r >> 2) << 16 | (g << 2 | g >> 4) << 8 | (b << 3 | b >> 2);
	}
}
