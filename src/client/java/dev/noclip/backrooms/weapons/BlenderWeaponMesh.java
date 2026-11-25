package dev.noclip.backrooms.weapons;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lightweight Wavefront OBJ + MTL mesh loader & cache for Blender-exported true-curved weapon models.
 * Splits triangulated geometry into shaded solid faces and 3 tintable emissive buckets (EMIT_MAIN,
 * EMIT_HOT, EMIT_ALT) stored in compact flat float arrays for zero-allocation per-frame rendering.
 */
public final class BlenderWeaponMesh {
	private static final String BASE_PATH = "/assets/noclip_backrooms/models/blender/";
	private static final Map<String, BlenderWeaponMesh> CACHE = new ConcurrentHashMap<>();
	private static final BlenderWeaponMesh EMPTY = new BlenderWeaponMesh(new float[0], new float[0], new float[0], new float[0]);

	/** Shaded triangles: 9 floats/vertex (x, y, z, nx, ny, nz, r, g, b), 27 floats/triangle. */
	public final float[] shaded;
	/** Primary emissive triangles: 6 floats/vertex (x, y, z, nx, ny, nz), 18 floats/triangle. */
	public final float[] emitMain;
	/** White-hot inner emissive core triangles: 6 floats/vertex. */
	public final float[] emitHot;
	/** Secondary accent emissive triangles: 6 floats/vertex. */
	public final float[] emitAlt;

	private BlenderWeaponMesh(float[] shaded, float[] emitMain, float[] emitHot, float[] emitAlt) {
		this.shaded = shaded;
		this.emitMain = emitMain;
		this.emitHot = emitHot;
		this.emitAlt = emitAlt;
	}

	public boolean isEmpty() {
		return shaded.length == 0 && emitMain.length == 0 && emitHot.length == 0 && emitAlt.length == 0;
	}

	public static BlenderWeaponMesh get(String meshName) {
		return getFromResource(BASE_PATH + meshName);
	}

	/** Loads and caches a Blender OBJ+MTL pair from any classpath resource prefix (without .obj/.mtl extension). */
	public static BlenderWeaponMesh getFromResource(String resourcePrefix) {
		return CACHE.computeIfAbsent(resourcePrefix, BlenderWeaponMesh::load);
	}

	private static BlenderWeaponMesh load(String resourcePrefix) {
		Map<String, float[]> mtlColors = loadMtl(resourcePrefix);
		try (InputStream in = BlenderWeaponMesh.class.getResourceAsStream(resourcePrefix + ".obj")) {
			if (in == null) return EMPTY;
			List<float[]> verts = new ArrayList<>(1024);
			List<float[]> norms = new ArrayList<>(1024);
			FloatList shadedBuf = new FloatList(4096);
			FloatList mainBuf = new FloatList(1024);
			FloatList hotBuf = new FloatList(512);
			FloatList altBuf = new FloatList(512);

			String curMat = "MAT_GUNMETAL";
			try (BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
				String line;
				while ((line = br.readLine()) != null) {
					if (line.isEmpty() || line.charAt(0) == '#') continue;
					if (line.startsWith("v ")) {
						String[] parts = line.trim().split("\\s+");
						if (parts.length >= 4) {
							verts.add(new float[]{
									Float.parseFloat(parts[1]),
									Float.parseFloat(parts[2]),
									Float.parseFloat(parts[3])
							});
						}
					} else if (line.startsWith("vn ")) {
						String[] parts = line.trim().split("\\s+");
						if (parts.length >= 4) {
							norms.add(new float[]{
									Float.parseFloat(parts[1]),
									Float.parseFloat(parts[2]),
									Float.parseFloat(parts[3])
							});
						}
					} else if (line.startsWith("usemtl ")) {
						curMat = line.substring(7).trim();
					} else if (line.startsWith("f ")) {
						String[] parts = line.trim().split("\\s+");
						int count = parts.length - 1;
						if (count < 3) continue;
						int[] vIdx = new int[count];
						int[] nIdx = new int[count];
						for (int i = 0; i < count; i++) {
							String tok = parts[i + 1];
							int slash1 = tok.indexOf('/');
							if (slash1 < 0) {
								vIdx[i] = resolveIndex(Integer.parseInt(tok), verts.size());
								nIdx[i] = -1;
							} else {
								vIdx[i] = resolveIndex(Integer.parseInt(tok.substring(0, slash1)), verts.size());
								int slash2 = tok.indexOf('/', slash1 + 1);
								if (slash2 >= 0 && slash2 + 1 < tok.length()) {
									nIdx[i] = resolveIndex(Integer.parseInt(tok.substring(slash2 + 1)), norms.size());
								} else {
									nIdx[i] = -1;
								}
							}
						}
						// Fan triangulation (OBJ is already triangulated by Blender, fan handles quads safely)
						for (int t = 1; t < count - 1; t++) {
							appendTri(verts, norms, vIdx[0], vIdx[t], vIdx[t + 1],
									nIdx[0], nIdx[t], nIdx[t + 1], curMat, mtlColors,
									shadedBuf, mainBuf, hotBuf, altBuf);
						}
					}
				}
			}
			return new BlenderWeaponMesh(shadedBuf.toArray(), mainBuf.toArray(), hotBuf.toArray(), altBuf.toArray());
		} catch (IOException | NumberFormatException e) {
			return EMPTY;
		}
	}

	private static int resolveIndex(int raw, int size) {
		int idx = raw > 0 ? raw - 1 : size + raw;
		return Math.max(0, Math.min(size - 1, idx));
	}

	private static void appendTri(List<float[]> verts, List<float[]> norms,
			int v0i, int v1i, int v2i, int n0i, int n1i, int n2i,
			String mat, Map<String, float[]> mtlColors,
			FloatList shadedBuf, FloatList mainBuf, FloatList hotBuf, FloatList altBuf) {
		float[] p0 = verts.get(v0i);
		float[] p1 = verts.get(v1i);
		float[] p2 = verts.get(v2i);

		float[] fn = null;
		if (n0i < 0 || n1i < 0 || n2i < 0) {
			float ux = p1[0] - p0[0], uy = p1[1] - p0[1], uz = p1[2] - p0[2];
			float vx = p2[0] - p0[0], vy = p2[1] - p0[1], vz = p2[2] - p0[2];
			float nx = uy * vz - uz * vy;
			float ny = uz * vx - ux * vz;
			float nz = ux * vy - uy * vx;
			float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
			if (len > 1.0E-5F) {
				fn = new float[]{nx / len, ny / len, nz / len};
			} else {
				fn = new float[]{0.0F, 1.0F, 0.0F};
			}
		}
		float[] n0 = n0i >= 0 ? norms.get(n0i) : fn;
		float[] n1 = n1i >= 0 ? norms.get(n1i) : fn;
		float[] n2 = n2i >= 0 ? norms.get(n2i) : fn;

		if (mat.startsWith("EMIT_HOT")) {
			putPosNorm(hotBuf, p0, n0);
			putPosNorm(hotBuf, p1, n1);
			putPosNorm(hotBuf, p2, n2);
		} else if (mat.startsWith("EMIT_ALT")) {
			putPosNorm(altBuf, p0, n0);
			putPosNorm(altBuf, p1, n1);
			putPosNorm(altBuf, p2, n2);
		} else if (mat.startsWith("EMIT_")) {
			putPosNorm(mainBuf, p0, n0);
			putPosNorm(mainBuf, p1, n1);
			putPosNorm(mainBuf, p2, n2);
		} else {
			float[] col = mtlColors.getOrDefault(mat, new float[]{0.55F, 0.58F, 0.62F});
			putPosNormCol(shadedBuf, p0, n0, col);
			putPosNormCol(shadedBuf, p1, n1, col);
			putPosNormCol(shadedBuf, p2, n2, col);
		}
	}

	private static void putPosNorm(FloatList buf, float[] p, float[] n) {
		buf.add(p[0], p[1], p[2], n[0], n[1], n[2]);
	}

	private static void putPosNormCol(FloatList buf, float[] p, float[] n, float[] c) {
		buf.add(p[0], p[1], p[2], n[0], n[1], n[2], c[0], c[1], c[2]);
	}

	private static Map<String, float[]> loadMtl(String resourcePrefix) {
		Map<String, float[]> map = new HashMap<>();
		try (InputStream in = BlenderWeaponMesh.class.getResourceAsStream(resourcePrefix + ".mtl")) {
			if (in == null) return map;
			String current = null;
			try (BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
				String line;
				while ((line = br.readLine()) != null) {
					String t = line.trim();
					if (t.startsWith("newmtl ")) {
						current = t.substring(7).trim();
					} else if (current != null && t.startsWith("Kd ")) {
						String[] parts = t.split("\\s+");
						if (parts.length >= 4) {
							// Slight sRGB lift so metallic dark gunmetal reads richly under Minecraft entity lighting
							float r = lift(Float.parseFloat(parts[1]));
							float g = lift(Float.parseFloat(parts[2]));
							float b = lift(Float.parseFloat(parts[3]));
							map.put(current, new float[]{r, g, b});
						}
					}
				}
			}
		} catch (IOException | NumberFormatException ignored) {
		}
		return map;
	}

	private static float lift(float linear) {
		float v = (float) Math.pow(Math.max(0.0F, Math.min(1.0F, linear)), 0.72);
		return Math.max(0.08F, Math.min(1.0F, v));
	}

	private static final class FloatList {
		private float[] data;
		private int size;

		FloatList(int initialCap) {
			this.data = new float[initialCap];
		}

		void add(float a, float b, float c, float d, float e, float f) {
			ensure(6);
			data[size++] = a;
			data[size++] = b;
			data[size++] = c;
			data[size++] = d;
			data[size++] = e;
			data[size++] = f;
		}

		void add(float a, float b, float c, float d, float e, float f, float g, float h, float i) {
			ensure(9);
			data[size++] = a;
			data[size++] = b;
			data[size++] = c;
			data[size++] = d;
			data[size++] = e;
			data[size++] = f;
			data[size++] = g;
			data[size++] = h;
			data[size++] = i;
		}

		private void ensure(int extra) {
			if (size + extra > data.length) {
				float[] grown = new float[Math.max(data.length * 2, size + extra)];
				System.arraycopy(data, 0, grown, 0, size);
				data = grown;
			}
		}

		float[] toArray() {
			float[] out = new float[size];
			System.arraycopy(data, 0, out, 0, size);
			return out;
		}
	}
}
