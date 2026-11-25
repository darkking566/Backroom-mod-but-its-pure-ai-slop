package dev.noclip.backrooms.core.look;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;

/**
 * A textured triangle mesh from a Wavefront OBJ (made in Blender by tools/item_look/blender_mesh.py): positions in
 * blocks, laid out like a block model (0..1 is the model's own block), texture coordinates, normals.
 */
public final class MeshModel {
	public static final MeshModel EMPTY = new MeshModel(new float[0], new float[] {0, 0, 0}, new float[] {1, 1, 1});
	/** Per vertex, three to a triangle: x, y, z, u, v, nx, ny, nz. */
	private final float[] data;
	public final float[] min, max;

	private MeshModel(float[] data, float[] min, float[] max) {
		this.data = data;
		this.min = min;
		this.max = max;
	}

	public boolean isEmpty() {
		return data.length == 0;
	}

	/** Loads a mesh from the resource packs as they are now; EMPTY if it is not there or cannot be read. */
	public static MeshModel load(Identifier id) {
		return load(Minecraft.getInstance().getResourceManager(), id);
	}

	public static MeshModel load(ResourceManager manager, Identifier id) {
		var resource = manager.getResource(id);
		if (resource.isEmpty()) return EMPTY;
		try (BufferedReader in = resource.get().openAsReader()) {
			return parse(in);
		} catch (IOException | RuntimeException e) {
			return EMPTY;
		}
	}

	static MeshModel parse(BufferedReader in) throws IOException {
		List<float[]> v = new ArrayList<>(), vt = new ArrayList<>(), vn = new ArrayList<>();
		List<Float> out = new ArrayList<>();
		float[] min = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE}, max = {-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
		String line;
		while ((line = in.readLine()) != null) {
			String[] p = line.trim().split("\\s+");
			switch (p[0]) {
				case "v" -> v.add(new float[] {Float.parseFloat(p[1]), Float.parseFloat(p[2]), Float.parseFloat(p[3])});
				case "vt" -> vt.add(new float[] {Float.parseFloat(p[1]), Float.parseFloat(p[2])});
				case "vn" -> vn.add(new float[] {Float.parseFloat(p[1]), Float.parseFloat(p[2]), Float.parseFloat(p[3])});
				case "f" -> {
					// Fan-triangulate, in case a face has more than three corners.
					for (int k = 2; k + 1 <= p.length - 1; k++) {
						for (String corner : new String[] {p[1], p[k], p[k + 1]}) {
							String[] idx = corner.split("/");
							float[] pos = v.get(Integer.parseInt(idx[0]) - 1);
							float[] uv = idx.length > 1 && !idx[1].isEmpty() ? vt.get(Integer.parseInt(idx[1]) - 1) : new float[] {0, 0};
							float[] n = idx.length > 2 ? vn.get(Integer.parseInt(idx[2]) - 1) : new float[] {0, 1, 0};
							out.add(pos[0]);
							out.add(pos[1]);
							out.add(pos[2]);
							out.add(uv[0]);
							out.add(1.0F - uv[1]);
							out.add(n[0]);
							out.add(n[1]);
							out.add(n[2]);
							for (int a = 0; a < 3; a++) {
								min[a] = Math.min(min[a], pos[a]);
								max[a] = Math.max(max[a], pos[a]);
							}
						}
					}
				}
				default -> {
				}
			}
		}
		float[] data = new float[out.size()];
		for (int i = 0; i < data.length; i++) data[i] = out.get(i);
		return data.length == 0 ? EMPTY : new MeshModel(data, min, max);
	}

	/**
	 * Sends the triangles to a consumer of an entity-style render type. Those draw quads (four corners at a time), so
	 * each triangle goes as a quad with its last corner twice.
	 */
	public void emit(PoseStack.Pose pose, VertexConsumer vc, int light, int overlay) {
		for (int i = 0; i < data.length; i += 24) {
			corner(pose, vc, i, light, overlay);
			corner(pose, vc, i + 8, light, overlay);
			corner(pose, vc, i + 16, light, overlay);
			corner(pose, vc, i + 16, light, overlay);
		}
	}

	private void corner(PoseStack.Pose pose, VertexConsumer vc, int i, int light, int overlay) {
		vc.addVertex(pose, data[i], data[i + 1], data[i + 2])
				.setColor(255, 255, 255, 255)
				.setUv(data[i + 3], data[i + 4])
				.setOverlay(overlay)
				.setLight(light)
				.setNormal(pose, data[i + 5], data[i + 6], data[i + 7]);
	}
}
