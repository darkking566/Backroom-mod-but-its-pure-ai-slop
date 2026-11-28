package dev.noclip.backrooms.core.field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PhotoImageTest {
	@Test
	void fitsInAClientPacketAndKeepsLeftAndRightApart() {
		// A wide screen, red on the left, blue on the right.
		byte[] image = PhotoImage.develop((x, y) -> x < 960 ? 0xC02020 : 0x2020C0, 1920, 1080, 7L, 0);
		assertEquals(PhotoImage.BYTES, image.length);
		assertTrue(PhotoImage.BYTES + 64 < 32767);
		int left = PhotoImage.argb(image, 20, PhotoImage.HEIGHT / 2), right = PhotoImage.argb(image, PhotoImage.WIDTH - 20, PhotoImage.HEIGHT / 2);
		assertTrue((left >> 16 & 255) > (left & 255) + 40, "left should be red");
		assertTrue((right & 255) > (right >> 16 & 255) + 20, "right should be blue");
	}

	@Test
	void developsScreensSmallerThanThePhoto() {
		assertEquals(PhotoImage.BYTES, PhotoImage.develop((x, y) -> {
			assertTrue(x >= 0 && x < 60 && y >= 0 && y < 90);
			return 0;
		}, 60, 90, 1L, 0).length);
	}

	@Test
	void exposureBrightensAndDarkens() {
		assertTrue(middle(1) > middle(0) + 30, "a stop up should be brighter");
		assertTrue(middle(0) > middle(-1) + 15, "a stop down should be darker");
	}

	private static int middle(float exposure) {
		byte[] image = PhotoImage.develop((x, y) -> 0x505050, 320, 240, 3L, exposure);
		return PhotoImage.argb(image, PhotoImage.WIDTH / 2, PhotoImage.HEIGHT / 2) >> 8 & 255;
	}

	@Test
	void zoomNarrowsTheFrameAndIsKeptInRange() {
		assertEquals(Math.cos(Math.toRadians(25)), PhotoImage.minDot(25, 1), 1e-9);
		assertTrue(PhotoImage.minDot(25, 4) > PhotoImage.minDot(25, 2));
		assertEquals(1, PhotoImage.clampZoom(Float.NaN));
		assertEquals(1, PhotoImage.clampZoom(-3));
		assertEquals(PhotoImage.MAX_ZOOM, PhotoImage.clampZoom(99));
	}
}
