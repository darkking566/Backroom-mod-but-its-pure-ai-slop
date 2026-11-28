package dev.noclip.backrooms.core.field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PhotoImageTest {
	@Test
	void fitsInAClientPacketAndKeepsLeftAndRightApart() {
		// A wide screen, red on the left, blue on the right.
		byte[] image = PhotoImage.develop((x, y) -> x < 960 ? 0xC02020 : 0x2020C0, 1920, 1080, 7L);
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
		}, 60, 90, 1L).length);
	}
}
