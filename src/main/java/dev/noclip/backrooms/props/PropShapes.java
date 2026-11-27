package dev.noclip.backrooms.props;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Loads model-derived VoxelShape bounding boxes from {@code data/noclip_backrooms/prop_shapes.json},
 * rotates them to match horizontal block facing, and caches both full outlines and per-cell clipped collisions.
 */
public final class PropShapes {
	private static final String RESOURCE_PATH = "/data/noclip_backrooms/prop_shapes.json";
	private static final Map<String, double[][]> RAW_BOXES = loadShapes();
	private static final Map<Key, VoxelShape> OUTLINE_CACHE = new ConcurrentHashMap<>();
	private static final Map<Key, VoxelShape> COLLISION_CACHE = new ConcurrentHashMap<>();

	private record Key(String id, Direction facing, int dx, int dy, int dz) {
	}

	private PropShapes() {
	}

	private static Map<String, double[][]> loadShapes() {
		try (InputStream stream = PropShapes.class.getResourceAsStream(RESOURCE_PATH)) {
			if (stream == null) {
				throw new IllegalStateException("Missing prop shapes resource: " + RESOURCE_PATH);
			}
			try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
				JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
				Map<String, double[][]> map = new LinkedHashMap<>(root.size());
				for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
					JsonArray arr = entry.getValue().getAsJsonArray();
					double[][] boxes = new double[arr.size()][6];
					for (int i = 0; i < arr.size(); i++) {
						JsonArray b = arr.get(i).getAsJsonArray();
						for (int j = 0; j < 6; j++) {
							boxes[i][j] = b.get(j).getAsDouble();
						}
					}
					map.put(entry.getKey(), boxes);
				}
				return Collections.unmodifiableMap(map);
			}
		} catch (IOException e) {
			throw new IllegalStateException("Failed to load " + RESOURCE_PATH, e);
		}
	}

	public static boolean hasShape(String id) {
		return id != null && RAW_BOXES.containsKey(id);
	}

	public static boolean hasShape(Block block) {
		return block != null && hasShape(blockId(block));
	}

	public static int boxCount(String id) {
		double[][] boxes = RAW_BOXES.get(id);
		return boxes == null ? 0 : boxes.length;
	}

	public static Set<String> allIds() {
		return RAW_BOXES.keySet();
	}

	public static VoxelShape outline(String id, Direction facing) {
		return outlineAt(id, facing, 0, 0, 0);
	}

	public static VoxelShape outline(Block block, Direction facing) {
		return outline(blockId(block), facing);
	}

	public static VoxelShape outlineAt(String id, Direction facing, int dx, int dy, int dz) {
		if (!hasShape(id)) {
			return Shapes.block();
		}
		Direction dir = normalizeFacing(facing);
		return OUTLINE_CACHE.computeIfAbsent(new Key(id, dir, dx, dy, dz), PropShapes::buildOutline);
	}

	public static VoxelShape outlineAt(Block block, Direction facing, int dx, int dy, int dz) {
		return outlineAt(blockId(block), facing, dx, dy, dz);
	}

	public static VoxelShape collisionAt(String id, Direction facing, int dx, int dy, int dz) {
		if (!hasShape(id)) {
			return Shapes.block();
		}
		Direction dir = normalizeFacing(facing);
		return COLLISION_CACHE.computeIfAbsent(new Key(id, dir, dx, dy, dz), PropShapes::buildCollision);
	}

	public static VoxelShape collisionAt(Block block, Direction facing, int dx, int dy, int dz) {
		return collisionAt(blockId(block), facing, dx, dy, dz);
	}

	private static String blockId(Block block) {
		return BuiltInRegistries.BLOCK.getKey(block).getPath();
	}

	private static Direction normalizeFacing(Direction facing) {
		return facing != null && facing.getAxis().isHorizontal() ? facing : Direction.NORTH;
	}

	private static VoxelShape buildOutline(Key key) {
		double[][] boxes = RAW_BOXES.get(key.id());
		if (boxes == null || boxes.length == 0) {
			return Shapes.block();
		}
		VoxelShape combined = Shapes.empty();
		for (double[] b : boxes) {
			VoxelShape rotated = rotateBox(b, key.facing());
			combined = Shapes.or(combined, rotated);
		}
		combined = combined.optimize();
		if (key.dx() != 0 || key.dy() != 0 || key.dz() != 0) {
			combined = combined.move(key.dx(), key.dy(), key.dz());
		}
		return combined.isEmpty() ? Shapes.block() : combined;
	}

	private static VoxelShape buildCollision(Key key) {
		VoxelShape full = outlineAt(key.id(), key.facing(), key.dx(), key.dy(), key.dz());
		return Shapes.joinUnoptimized(full, Shapes.block(), BooleanOp.AND).optimize();
	}

	private static VoxelShape rotateBox(double[] b, Direction facing) {
		double x0 = b[0], y0 = b[1], z0 = b[2], x1 = b[3], y1 = b[4], z1 = b[5];
		return switch (facing) {
			case EAST -> Shapes.box(1.0 - z1, y0, x0, 1.0 - z0, y1, x1);
			case SOUTH -> Shapes.box(1.0 - x1, y0, 1.0 - z1, 1.0 - x0, y1, 1.0 - z0);
			case WEST -> Shapes.box(z0, y0, 1.0 - x1, z1, y1, 1.0 - x0);
			default -> Shapes.box(x0, y0, z0, x1, y1, z1);
		};
	}
}
