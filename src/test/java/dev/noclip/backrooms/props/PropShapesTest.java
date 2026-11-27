package dev.noclip.backrooms.props;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.junit.jupiter.api.Test;

final class PropShapesTest {
	@Test
	void all135PropsLoadedWithValidBounds() {
		assertEquals(135, PropShapes.allIds().size(), "Expected all 67 BackroomsProps + 68 FilmProps");

		List<String> representatives = List.of(
				"salvage_bench",
				"locked_armory_crate",
				"relay_dropoff_box",
				"prop_wood_chair",
				"prop_sofa",
				"prop_floor_lamp",
				"film_recliner_leather",
				"film_sofa_tan",
				"film_torchiere",
				"film_mannequin",
				"film_watercolor_print"
		);
		for (String id : representatives) {
			assertTrue(PropShapes.hasShape(id), "Missing representative prop shape: " + id);
		}

		for (String id : PropShapes.allIds()) {
			int count = PropShapes.boxCount(id);
			assertTrue(count >= 1 && count <= 48, id + " has invalid box count: " + count);

			for (Direction dir : Direction.Plane.HORIZONTAL) {
				VoxelShape outline = PropShapes.outline(id, dir);
				assertFalse(outline.isEmpty(), id + " outline empty for " + dir);

				VoxelShape collision = PropShapes.collisionAt(id, dir, 0, 0, 0);
				if (!collision.isEmpty()) {
					AABB cb = collision.bounds();
					assertTrue(cb.minX >= -1e-6 && cb.maxX <= 1.0 + 1e-6, id + " collision X out of [0,1]: " + cb);
					assertTrue(cb.minY >= -1e-6 && cb.maxY <= 1.0 + 1e-6, id + " collision Y out of [0,1]: " + cb);
					assertTrue(cb.minZ >= -1e-6 && cb.maxZ <= 1.0 + 1e-6, id + " collision Z out of [0,1]: " + cb);
				}
			}
		}
	}

	@Test
	void nonCubePropsAreNotFullBlock() {
		List<String> nonCubes = List.of(
				"prop_wood_chair",
				"prop_floor_lamp",
				"prop_desktop_computer",
				"prop_wall_first_aid_kit",
				"salvage_bench",
				"locked_armory_crate",
				"film_recliner_leather",
				"film_torchiere",
				"film_mannequin",
				"film_watercolor_print"
		);
		for (String id : nonCubes) {
			assertNotEquals(Shapes.block(), PropShapes.outline(id, Direction.NORTH), id + " should not be full block");
		}
	}

	@Test
	void multiBlockRotationAndUnifiedAlignment() {
		AABB north = PropShapes.outline("prop_sofa", Direction.NORTH).bounds();
		assertTrue(north.maxX > 1.0 && north.minX >= 0.0 && north.minZ >= 0.0 && north.maxZ <= 1.0,
				"NORTH prop_sofa should extend into +X: " + north);

		AABB east = PropShapes.outline("prop_sofa", Direction.EAST).bounds();
		assertTrue(east.maxZ > 1.0, "EAST prop_sofa should extend into +Z: " + east);

		AABB south = PropShapes.outline("prop_sofa", Direction.SOUTH).bounds();
		assertTrue(south.minX < 0.0, "SOUTH prop_sofa should extend into -X: " + south);

		AABB west = PropShapes.outline("prop_sofa", Direction.WEST).bounds();
		assertTrue(west.minZ < 0.0, "WEST prop_sofa should extend into -Z: " + west);

		AABB partMoved = PropShapes.outlineAt("prop_sofa", Direction.NORTH, -1, 0, 0).move(1, 0, 0).bounds();
		assertEquals(north.minX, partMoved.minX, 1e-6);
		assertEquals(north.minY, partMoved.minY, 1e-6);
		assertEquals(north.minZ, partMoved.minZ, 1e-6);
		assertEquals(north.maxX, partMoved.maxX, 1e-6);
		assertEquals(north.maxY, partMoved.maxY, 1e-6);
		assertEquals(north.maxZ, partMoved.maxZ, 1e-6);
	}
}
