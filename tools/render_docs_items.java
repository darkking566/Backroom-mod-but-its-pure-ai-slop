import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;

/**
 * Standalone JDK 25 studio 3D preview renderer that outputs an individual crisp anti-aliased PNG
 * into docs/project/3-crafting/items/ for EVERY Backrooms item (369), 3D block/furniture/workstation (58),
 * Blender true-curved weapon mesh (22 + 3 Cycles showcases), and worn 3D armor set (6).
 *
 * Run with:
 *   "C:\Program Files\Microsoft\jdk-25.0.5.102-hotspot\bin\java.exe" tools/render_docs_items.java
 */
public final class render_docs_items {
	private static final Path ASSETS = Path.of("src/main/resources/assets/noclip_backrooms");
	private static final Path DOCS_OUT = Path.of("docs/project/3-crafting/items");

	public static void main(String[] args) throws Exception {
		Path outItems = DOCS_OUT.resolve("items");
		Path outBlocks = DOCS_OUT.resolve("blocks");
		Path outBlender = DOCS_OUT.resolve("weapons_blender");
		Path outArmor = DOCS_OUT.resolve("armor");
		Files.createDirectories(outItems);
		Files.createDirectories(outBlocks);
		Files.createDirectories(outBlender);
		Files.createDirectories(outArmor);

		// 1. Copy Cycles Blender showcase renders into docs/project/3-crafting/items/weapons_blender/
		Path artWeapons = Path.of("art/weapons");
		for (String preview : List.of("preview_hero_railgun.png", "preview_void_staff_evolution.png", "preview_void_staff_tier3_closeup.png")) {
			Path src = artWeapons.resolve(preview);
			if (Files.exists(src)) {
				Files.copy(src, outBlender.resolve(preview), StandardCopyOption.REPLACE_EXISTING);
			}
		}

		// 2. Render all 22 Blender true-curved weapon .obj meshes at 512x360 into docs/project/3-crafting/items/weapons_blender/
		Path blenderDir = ASSETS.resolve("models/blender");
		Map<String, ObjMesh> objCache = new HashMap<>();
		List<Path> objFiles;
		try (var stream = Files.list(blenderDir)) {
			objFiles = stream.filter(p -> p.toString().endsWith(".obj")).sorted().toList();
		}
		for (Path objPath : objFiles) {
			String name = stripExt(objPath.getFileName().toString());
			ObjMesh mesh = ObjMesh.load(objPath);
			objCache.put(name, mesh);
			int[] palette = defaultPaletteForMesh(name);
			float yaw = yawForMesh(name);
			float pitch = (name.startsWith("gun_") || name.equals("office_crossbow")) ? 12.0F : 15.0F;
			BufferedImage img = renderObj(mesh, palette[0], palette[1], 512, 360, yaw, pitch);
			ImageIO.write(img, "PNG", outBlender.resolve(name + ".png").toFile());
		}
		System.out.println("Rendered " + objFiles.size() + " Blender weapon showcase PNGs -> " + outBlender);

		// 3. Render ALL 369 individual items into docs/project/3-crafting/items/items/<id>.png (256x256)
		//    - The 80 Backrooms weapons render their actual Blender curved mesh + signature elemental glow!
		//    - The other 289 3D items render their exact 3D cuboid JSON model + 128x128 texture + light_emission bloom!
		Path itemModelsDir = ASSETS.resolve("models/item/crafting");
		Path itemTexDir = ASSETS.resolve("textures/item/equipment3d");
		List<Path> itemModels;
		try (var stream = Files.list(itemModelsDir)) {
			itemModels = stream.filter(p -> p.toString().endsWith(".json")).sorted().toList();
		}
		int blenderWeaponItems = 0;
		int cuboidItems = 0;
		for (Path modelPath : itemModels) {
			String id = stripExt(modelPath.getFileName().toString());
			WeaponSpec ws = weaponSpecForItem(id);
			BufferedImage img;
			if (ws != null && objCache.containsKey(ws.meshName)) {
				float yaw = yawForMesh(ws.meshName);
				float pitch = ws.isFirearm ? 12.0F : 15.0F;
				img = renderObj(objCache.get(ws.meshName), ws.primaryRgb, ws.secondaryRgb, 256, 256, yaw, pitch);
				blenderWeaponItems++;
			} else {
				Path texPath = itemTexDir.resolve(id + ".png");
				BufferedImage tex = Files.exists(texPath) ? ImageIO.read(texPath.toFile()) : null;
				img = renderCuboidJson(modelPath, tex, 256, 256, 36.0F, 24.0F);
				cuboidItems++;
			}
			ImageIO.write(img, "PNG", outItems.resolve(id + ".png").toFile());
		}
		System.out.println("Rendered " + itemModels.size() + " item PNGs (" + blenderWeaponItems + " Blender weapons + " + cuboidItems + " 3D items) -> " + outItems);

		// 4. Render ALL 59 individual 3D blocks/workstations/furniture/chests into docs/project/3-crafting/items/blocks/<id>.png (256x256)
		//    - Uses the TRUE multi-block & half-sliced Blender .obj mesh whenever available in models/blender_props/!
		Path blockModelsDir = ASSETS.resolve("models/block/crafting");
		Path blockTexDir = ASSETS.resolve("textures/block/crafting3d");
		Path blenderPropsDir = ASSETS.resolve("models/blender_props");
		List<Path> blockModels;
		try (var stream = Files.list(blockModelsDir)) {
			blockModels = stream.filter(p -> p.toString().endsWith(".json"))
					.filter(p -> !p.getFileName().toString().equals("prop_part.json"))
					.sorted().toList();
		}
		int blenderPropCount = 0;
		for (Path modelPath : blockModels) {
			String id = stripExt(modelPath.getFileName().toString());
			Path propObj = blenderPropsDir.resolve(id + ".obj");
			BufferedImage img;
			if (Files.exists(propObj)) {
				ObjMesh mesh = ObjMesh.load(propObj);
				int[] pal = propEmissivePalette(id);
				img = renderObj(mesh, pal[0], pal[1], 320, 320, 214.0F, 18.0F);
				blenderPropCount++;
			} else {
				Path texPath = blockTexDir.resolve(id + ".png");
				BufferedImage tex = Files.exists(texPath) ? ImageIO.read(texPath.toFile()) : null;
				img = renderCuboidJson(modelPath, tex, 256, 256, 38.0F, 26.0F);
			}
			ImageIO.write(img, "PNG", outBlocks.resolve(id + ".png").toFile());
		}
		System.out.println("Rendered " + blockModels.size() + " block PNGs (" + blenderPropCount + " Blender multi-block/sliced props) -> " + outBlocks);

		// 5. Render ALL 6 worn 3D armor set full-suit previews into docs/project/3-crafting/items/armor/<set>.png (256x256)
		Map<String, List<String>> armorSetPieces = new LinkedHashMap<>();
		armorSetPieces.put("improvised_office", List.of("magazine_armor_chestplate", "silent_carpet_boots"));
		armorSetPieces.put("almond_gas_mask", List.of("almond_gas_mask"));
		armorSetPieces.put("scrap_tactical", List.of("scrap_tactical_helmet", "scrap_tactical_chestplate", "scrap_tactical_leggings", "scrap_tactical_boots"));
		armorSetPieces.put("skin_stealer", List.of("skin_stealer_helmet", "skin_stealer_chestplate", "skin_stealer_leggings", "skin_stealer_boots"));
		armorSetPieces.put("boiler_thermal", List.of("boiler_thermal_helmet", "boiler_thermal_chestplate", "boiler_thermal_leggings", "boiler_thermal_boots"));
		armorSetPieces.put("abyssal_void", List.of("abyssal_void_helmet", "abyssal_void_chestplate", "abyssal_void_leggings", "abyssal_void_boots"));
		for (var entry : armorSetPieces.entrySet()) {
			BufferedImage img = renderArmorSetComposite(entry.getValue(), itemModelsDir, itemTexDir, 256, 320);
			ImageIO.write(img, "PNG", outArmor.resolve(entry.getKey() + ".png").toFile());
		}
		System.out.println("Rendered " + armorSetPieces.size() + " 3D armor set PNGs -> " + outArmor);
		System.out.println("DONE! Total PNGs written under docs/project/3-crafting/items/: " + (objFiles.size() + 3 + itemModels.size() + blockModels.size() + armorSetPieces.size()));
	}

	private static String stripExt(String fn) {
		int dot = fn.lastIndexOf('.');
		return dot > 0 ? fn.substring(0, dot) : fn;
	}

	// =========================================================================
	// 1. BLENDER WEAPON MAPPING (mirrors BlenderWeaponRenderer.java 1:1)
	// =========================================================================
	private record WeaponSpec(String meshName, int primaryRgb, int secondaryRgb, boolean isFirearm) {}

	private static int[] propEmissivePalette(String id) {
		if (id.contains("red_anomaly")) return new int[]{0xFF223C, 0xCC1828};
		if (id.contains("crystal_vein")) return new int[]{0x3CE4FF, 0xA848FF};
		if (id.contains("neon_sign") || id.contains("arcade")) return new int[]{0xFF38B8, 0x2CE8FF};
		if (id.contains("boiler") || id.contains("chandelier") || id.contains("floor_lamp")) return new int[]{0xFFB844, 0xFF6622};
		if (id.contains("vending") || id.contains("water_cooler")) return new int[]{0x56E4FF, 0xFF4448};
		if (id.contains("computer") || id.contains("crt_tv") || id.contains("phone")) return new int[]{0x48E898, 0x44A8FF};
		if (id.contains("noclipped")) return new int[]{0x8C58FF, 0x28D8FF};
		return new int[]{0x66D8FF, 0xFF5252};
	}

	private static float yawForMesh(String name) {
		if (name.startsWith("gun_") || name.equals("office_crossbow")) return 68.0F;
		if (name.startsWith("melee_") || name.equals("magic_crimson_scythe")) return 66.0F;
		if (name.equals("magic_grimoire") || name.equals("magic_watch")) return 22.0F;
		return 30.0F;
	}

	private static int[] defaultPaletteForMesh(String name) {
		return switch (name) {
			case "gun_railgun" -> new int[]{0x1EE5FF, 0xFF2838};
			case "gun_sniper" -> new int[]{0x38D4FF, 0x32FF66};
			case "gun_assault_rifle" -> new int[]{0x38CFFF, 0xFF2838};
			case "gun_shotgun" -> new int[]{0x3BFF68, 0xFF342C};
			case "gun_pistol" -> new int[]{0x38FF72, 0xFF2834};
			case "gun_revolver" -> new int[]{0xFFAA28, 0xFF3828};
			case "gun_flamethrower" -> new int[]{0xFF6B18, 0xFFDC3A};
			case "gun_sonic_pulse" -> new int[]{0xA855FF, 0x2CE8FF};
			case "office_crossbow" -> new int[]{0x4CE4FF, 0xFF4242};
			case "magic_staff_t1", "magic_staff_t2", "magic_staff_t3" -> new int[]{0xA636FF, 0x1EE5FF};
			case "magic_crimson_scythe" -> new int[]{0xFF2038, 0xFF7048};
			case "magic_grimoire" -> new int[]{0x42D8FF, 0xFFE666};
			case "magic_bell" -> new int[]{0xFFD85C, 0xA866FF};
			case "magic_watch" -> new int[]{0x44E6FF, 0xFFD248};
			case "melee_greatsword" -> new int[]{0xA836FF, 0x20E8FF};
			case "melee_curved_dao" -> new int[]{0xFF243C, 0xFF9838};
			case "melee_warhammer" -> new int[]{0x32C8FF, 0xA8F6FF};
			case "melee_polearm" -> new int[]{0x48E8FF, 0xFFD452};
			case "melee_dagger" -> new int[]{0xFF8824, 0xFFE066};
			default -> new int[]{0xA636FF, 0x1EE5FF};
		};
	}

	private static final Set<String> EXACT_FIREARMS = Set.of(
			"gun_pipe_pistol", "gun_pipe_shotgun", "gun_nailgun", "gun_taser", "gun_flamethrower",
			"gun_revolver", "gun_pistol_9mm", "gun_heavy_handgun",
			"gun_double_barrel", "gun_pump_shotgun", "gun_auto_shotgun",
			"gun_scrap_smg", "gun_tactical_smg", "gun_assault_rifle",
			"gun_bolt_sniper", "gun_antimateriel_rifle", "gun_railgun", "gun_sonic_pulse",
			"office_crossbow"
	);

	private static WeaponSpec weaponSpecForItem(String id) {
		// Exact 19 Firearms (excluding firearm crafting parts like gun_barrel_*, gun_receiver_*, gun_mag_*, etc.)
		if (EXACT_FIREARMS.contains(id)) {
			return switch (id) {
				case "gun_railgun" -> new WeaponSpec("gun_railgun", 0x1EE5FF, 0xFF2838, true);
				case "gun_bolt_sniper" -> new WeaponSpec("gun_sniper", 0x38D4FF, 0x32FF66, true);
				case "gun_antimateriel_rifle" -> new WeaponSpec("gun_sniper", 0xFFB424, 0xFF3424, true);
				case "gun_flamethrower" -> new WeaponSpec("gun_flamethrower", 0xFF6B18, 0xFFDC3A, true);
				case "gun_sonic_pulse" -> new WeaponSpec("gun_sonic_pulse", 0xA855FF, 0x2CE8FF, true);
				case "office_crossbow" -> new WeaponSpec("office_crossbow", 0x4CE4FF, 0xFF4242, true);
				case "gun_revolver" -> new WeaponSpec("gun_revolver", 0xFFAA28, 0xFF3828, true);
				case "gun_pipe_shotgun", "gun_double_barrel", "gun_pump_shotgun" -> new WeaponSpec("gun_shotgun", 0x3BFF68, 0xFF342C, true);
				case "gun_auto_shotgun" -> new WeaponSpec("gun_shotgun", 0x44FF38, 0xA0FF28, true);
				case "gun_pipe_pistol", "gun_nailgun", "gun_pistol_9mm", "gun_heavy_handgun" -> new WeaponSpec("gun_pistol", 0x38FF72, 0xFF2834, true);
				case "gun_taser" -> new WeaponSpec("gun_pistol", 0x3CE0FF, 0xFFE63C, true);
				default -> new WeaponSpec("gun_assault_rifle", id.contains("tactical") ? 0x38FF88 : 0x38CFFF, 0xFF2838, true);
			};
		}
		// Magic weapons (20) - render signature Tier 3 / Tier 2 / Tier 1 staff visuals so high-tier staffs shine!
		if (id.startsWith("magic_") || id.equals("static_pulse_staff")) {
			return switch (id) {
				case "magic_void_rift_staff" -> new WeaponSpec("magic_staff_t3", 0xA636FF, 0x1EE5FF, false);
				case "magic_fate_dice_staff" -> new WeaponSpec("magic_staff_t3", 0xFFCC28, 0xD848FF, false);
				case "magic_noclip_wand" -> new WeaponSpec("magic_staff_t2", 0xD850FF, 0xFFE250, false);
				case "magic_prism_light_staff" -> new WeaponSpec("magic_staff_t2", 0xFFE466, 0xFFF8D0, false);
				case "magic_steam_flame_staff" -> new WeaponSpec("magic_staff_t2", 0xFF6618, 0xFFD438, false);
				case "magic_arc_lightning_staff" -> new WeaponSpec("magic_staff_t2", 0x30C8FF, 0xA8F4FF, false);
				case "magic_abyssal_frost_scepter" -> new WeaponSpec("magic_staff_t2", 0x48E0FF, 0xC8F8FF, false);
				case "magic_plague_censer_wand" -> new WeaponSpec("magic_staff_t2", 0x48FF58, 0xC4FF38, false);
				case "static_pulse_staff" -> new WeaponSpec("magic_staff_t1", 0x5EE8FF, 0xFFE866, false);
				case "magic_clump_grapple" -> new WeaponSpec("magic_staff_t1", 0xE64455, 0xFFAA44, false);
				case "magic_crimson_scythe" -> new WeaponSpec("magic_crimson_scythe", 0xFF2038, 0xFF7048, false);
				case "magic_acid_grimoire" -> new WeaponSpec("magic_grimoire", 0x44FF38, 0xC0FF40, false);
				case "magic_party_grimoire" -> new WeaponSpec("magic_grimoire", 0xFF42B8, 0xFFE038, false);
				case "magic_astral_codex" -> new WeaponSpec("magic_grimoire", 0x42D8FF, 0xFFE666, false);
				case "magic_screech_bell" -> new WeaponSpec("magic_bell", 0x42D8FF, 0xA866FF, false);
				case "magic_beast_summon_bell" -> new WeaponSpec("magic_bell", 0xB838FF, 0xFF3850, false);
				case "magic_almond_censer" -> new WeaponSpec("magic_bell", 0xFFD85C, 0x70FFAA, false);
				case "magic_stopmotion_watch" -> new WeaponSpec("magic_watch", 0x44E6FF, 0xFFD248, false);
				case "magic_decoy_mirror" -> new WeaponSpec("magic_watch", 0x9CEEFF, 0xD8A8FF, false);
				case "magic_phosphor_talisman" -> new WeaponSpec("magic_watch", 0xFF9828, 0xFFF066, false);
				default -> null;
			};
		}
		// Melee weapons (41)
		if (id.startsWith("blunt_") || id.startsWith("west_") || id.startsWith("cn_")) {
			String mesh;
			if (id.contains("guandao") || id.contains("fangtian") || id.contains("spear") || id.contains("halberd") || id.contains("lucerne") || id.contains("staff")) {
				mesh = "melee_polearm";
			} else if (id.contains("dao") || id.contains("scimitar")) {
				mesh = "melee_curved_dao";
			} else if (id.contains("knife") || id.contains("dagger") || id.contains("rapier") || id.contains("darts")) {
				mesh = "melee_dagger";
			} else if (id.startsWith("blunt_") || id.contains("axe") || id.contains("hammer") || id.contains("nunchaku")) {
				mesh = "melee_warhammer";
			} else {
				mesh = "melee_greatsword";
			}
			int p = 0x66D8FF, s = 0xC8F0FF;
			if (id.contains("void")) { p = 0xA836FF; s = 0x20E8FF; }
			else if (id.contains("thunder")) { p = 0x32C8FF; s = 0xA8F6FF; }
			else if (id.contains("crimson") || id.contains("alpha") || id.contains("hotel") || id.contains("nailed") || id.contains("barbed")) { p = 0xFF243C; s = 0xFF9838; }
			else if (id.contains("acid")) { p = 0x48FF38; s = 0xB8FF44; }
			else if (id.contains("hallowed") || id.contains("coin") || id.contains("peachwood")) { p = 0xFFE052; s = 0xFFF8C0; }
			else if (id.contains("fangtian")) { p = 0x48E8FF; s = 0xFFD452; }
			else if (id.contains("lucerne") || id.contains("rapier") || id.contains("flanged") || id.contains("clump")) { p = 0xFF8824; s = 0xFFE066; }
			return new WeaponSpec(mesh, p, s, false);
		}
		return null;
	}

	// =========================================================================
	// 2. FAST 2x2 SUPERSAMPLED Z-BUFFERED OBJ RENDERER + EMISSIVE BLOOM
	// =========================================================================
	private static final class ObjMesh {
		final List<float[]> tris = new ArrayList<>(); // 22 floats per tri: 3*(x,y,z,nx,ny,nz) + r,g,b,emitKind(0=shaded,1=main,2=hot,3=alt)

		static ObjMesh load(Path objPath) throws IOException {
			String name = stripExt(objPath.getFileName().toString());
			Map<String, float[]> mtl = loadMtl(objPath.resolveSibling(name + ".mtl"));
			ObjMesh m = new ObjMesh();
			List<float[]> verts = new ArrayList<>();
			List<float[]> norms = new ArrayList<>();
			String curMat = "MAT_GUNMETAL";
			try (BufferedReader br = Files.newBufferedReader(objPath, StandardCharsets.UTF_8)) {
				String line;
				while ((line = br.readLine()) != null) {
					if (line.isEmpty() || line.charAt(0) == '#') continue;
					if (line.startsWith("v ")) {
						String[] a = line.trim().split("\\s+");
						verts.add(new float[]{Float.parseFloat(a[1]), Float.parseFloat(a[2]), Float.parseFloat(a[3])});
					} else if (line.startsWith("vn ")) {
						String[] a = line.trim().split("\\s+");
						norms.add(new float[]{Float.parseFloat(a[1]), Float.parseFloat(a[2]), Float.parseFloat(a[3])});
					} else if (line.startsWith("usemtl ")) {
						curMat = line.substring(7).trim();
					} else if (line.startsWith("f ")) {
						String[] a = line.trim().split("\\s+");
						int n = a.length - 1;
						int[] vi = new int[n], ni = new int[n];
						for (int i = 0; i < n; i++) {
							String tok = a[i + 1];
							int s1 = tok.indexOf('/');
							vi[i] = Integer.parseInt(s1 < 0 ? tok : tok.substring(0, s1)) - 1;
							int s2 = s1 >= 0 ? tok.indexOf('/', s1 + 1) : -1;
							ni[i] = (s2 >= 0 && s2 + 1 < tok.length()) ? Integer.parseInt(tok.substring(s2 + 1)) - 1 : -1;
						}
						int ek = curMat.startsWith("EMIT_HOT") ? 2 : (curMat.startsWith("EMIT_ALT") ? 3 : (curMat.startsWith("EMIT_") ? 1 : 0));
						float[] col = mtl.getOrDefault(curMat, new float[]{0.55F, 0.58F, 0.64F});
						for (int t = 1; t < n - 1; t++) {
							float[] p0 = verts.get(vi[0]), p1 = verts.get(vi[t]), p2 = verts.get(vi[t + 1]);
							float[] n0 = ni[0] >= 0 ? norms.get(ni[0]) : faceNorm(p0, p1, p2);
							float[] n1 = ni[t] >= 0 ? norms.get(ni[t]) : n0;
							float[] n2 = ni[t + 1] >= 0 ? norms.get(ni[t + 1]) : n0;
							m.tris.add(new float[]{
									p0[0], p0[1], p0[2], n0[0], n0[1], n0[2],
									p1[0], p1[1], p1[2], n1[0], n1[1], n1[2],
									p2[0], p2[1], p2[2], n2[0], n2[1], n2[2],
									col[0], col[1], col[2], ek
							});
						}
					}
				}
			}
			return m;
		}

		private static Map<String, float[]> loadMtl(Path mtlPath) throws IOException {
			Map<String, float[]> map = new HashMap<>();
			if (!Files.exists(mtlPath)) return map;
			String cur = null;
			for (String line : Files.readAllLines(mtlPath, StandardCharsets.UTF_8)) {
				String t = line.trim();
				if (t.startsWith("newmtl ")) cur = t.substring(7).trim();
				else if (cur != null && t.startsWith("Kd ")) {
					String[] a = t.split("\\s+");
					map.put(cur, new float[]{
							(float) Math.pow(Float.parseFloat(a[1]), 0.68),
							(float) Math.pow(Float.parseFloat(a[2]), 0.68),
							(float) Math.pow(Float.parseFloat(a[3]), 0.68)
					});
				}
			}
			return map;
		}
	}

	private static BufferedImage renderObj(ObjMesh mesh, int primaryRgb, int secondaryRgb, int outW, int outH, float yawDeg, float pitchDeg) {
		int W = outW * 2, H = outH * 2;
		float[] rBuf = new float[W * H], gBuf = new float[W * H], bBuf = new float[W * H];
		float[] emR = new float[W * H], emG = new float[W * H], emB = new float[W * H];
		float[] zBuf = new float[W * H];
		Arrays.fill(zBuf, -1e9F);
		fillStudioBackdrop(W, H, rBuf, gBuf, bBuf, primaryRgb);

		double cy = Math.cos(Math.toRadians(yawDeg)), sy = Math.sin(Math.toRadians(yawDeg));
		double cp = Math.cos(Math.toRadians(pitchDeg)), sp = Math.sin(Math.toRadians(pitchDeg));

		// Rotate all triangles and compute tight projected bounds
		float minX = 1e9F, maxX = -1e9F, minY = 1e9F, maxY = -1e9F;
		float[][] rot = new float[mesh.tris.size()][22];
		for (int i = 0; i < mesh.tris.size(); i++) {
			float[] t = mesh.tris.get(i);
			float[] o = rot[i];
			for (int v = 0; v < 3; v++) {
				int b = v * 6;
				// Note: rotate firearm -Z forward toward +X right on screen nicely by adding 90 deg yaw
				float x = t[b], y = t[b + 1], z = t[b + 2];
				float rx = (float) (x * cy - z * sy);
				float rz = (float) (x * sy + z * cy);
				float ry = (float) (y * cp - rz * sp);
				float rzz = (float) (y * sp + rz * cp);
				o[b] = rx; o[b + 1] = ry; o[b + 2] = rzz;
				if (rx < minX) minX = rx; if (rx > maxX) maxX = rx;
				if (ry < minY) minY = ry; if (ry > maxY) maxY = ry;

				float nx = t[b + 3], ny = t[b + 4], nz = t[b + 5];
				float rnx = (float) (nx * cy - nz * sy);
				float rnz = (float) (nx * sy + nz * cy);
				float rny = (float) (ny * cp - rnz * sp);
				float rnzz = (float) (ny * sp + rnz * cp);
				o[b + 3] = rnx; o[b + 4] = rny; o[b + 5] = rnzz;
			}
			o[18] = t[18]; o[19] = t[19]; o[20] = t[20]; o[21] = t[21];
		}

		float spanX = Math.max(0.01F, maxX - minX);
		float spanY = Math.max(0.01F, maxY - minY);
		float scale = Math.min((W * 0.84F) / spanX, (H * 0.84F) / spanY);
		float cx = (minX + maxX) * 0.5F, cyMid = (minY + maxY) * 0.5F;

		float pr = ((primaryRgb >> 16) & 0xFF) / 255.0F;
		float pg = ((primaryRgb >> 8) & 0xFF) / 255.0F;
		float pb = (primaryRgb & 0xFF) / 255.0F;
		float sr = ((secondaryRgb >> 16) & 0xFF) / 255.0F;
		float sg = ((secondaryRgb >> 8) & 0xFF) / 255.0F;
		float sb = (secondaryRgb & 0xFF) / 255.0F;
		float hr = pr + (1.0F - pr) * 0.62F;
		float hg = pg + (1.0F - pg) * 0.62F;
		float hb = pb + (1.0F - pb) * 0.62F;

		// Studio 3-point directional lights (normalized)
		float[] L1 = norm3(0.55F, 0.72F, 0.42F);  // Warm key
		float[] L2 = norm3(-0.70F, 0.35F, -0.62F); // Cool rim
		float[] L3 = norm3(-0.35F, -0.55F, 0.75F); // Soft fill

		for (float[] t : rot) {
			float sx0 = W * 0.5F + (t[0] - cx) * scale, sy0 = H * 0.5F - (t[1] - cyMid) * scale, sz0 = t[2];
			float sx1 = W * 0.5F + (t[6] - cx) * scale, sy1 = H * 0.5F - (t[7] - cyMid) * scale, sz1 = t[8];
			float sx2 = W * 0.5F + (t[12] - cx) * scale, sy2 = H * 0.5F - (t[13] - cyMid) * scale, sz2 = t[14];
			int ek = (int) t[21];

			float cr, cg, cb;
			if (ek == 1) { cr = pr; cg = pg; cb = pb; }
			else if (ek == 2) { cr = hr; cg = hg; cb = hb; }
			else if (ek == 3) { cr = sr; cg = sg; cb = sb; }
			else { cr = t[18]; cg = t[19]; cb = t[20]; }

			int xMin = Math.max(0, (int) Math.floor(Math.min(sx0, Math.min(sx1, sx2))));
			int xMax = Math.min(W - 1, (int) Math.ceil(Math.max(sx0, Math.max(sx1, sx2))));
			int yMin = Math.max(0, (int) Math.floor(Math.min(sy0, Math.min(sy1, sy2))));
			int yMax = Math.min(H - 1, (int) Math.ceil(Math.max(sy0, Math.max(sy1, sy2))));

			float area = edge(sx0, sy0, sx1, sy1, sx2, sy2);
			if (Math.abs(area) < 1e-4F) continue;
			float invArea = 1.0F / area;

			for (int py = yMin; py <= yMax; py++) {
				for (int px = xMin; px <= xMax; px++) {
					float w0 = edge(sx1, sy1, sx2, sy2, px + 0.5F, py + 0.5F) * invArea;
					float w1 = edge(sx2, sy2, sx0, sy0, px + 0.5F, py + 0.5F) * invArea;
					float w2 = 1.0F - w0 - w1;
					if (w0 < -1e-4F || w1 < -1e-4F || w2 < -1e-4F) continue;
					float z = w0 * sz0 + w1 * sz1 + w2 * sz2;
					int idx = py * W + px;
					if (z <= zBuf[idx]) continue;
					zBuf[idx] = z;

					if (ek > 0) {
						rBuf[idx] = Math.min(1.0F, cr * 1.18F);
						gBuf[idx] = Math.min(1.0F, cg * 1.18F);
						bBuf[idx] = Math.min(1.0F, cb * 1.18F);
						emR[idx] = cr; emG[idx] = cg; emB[idx] = cb;
					} else {
						float nx = w0 * t[3] + w1 * t[9] + w2 * t[15];
						float ny = w0 * t[4] + w1 * t[10] + w2 * t[16];
						float nz = w0 * t[5] + w1 * t[11] + w2 * t[17];
						float nl = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
						if (nl > 1e-5F) { nx /= nl; ny /= nl; nz /= nl; }
						float d1 = Math.max(0.0F, nx * L1[0] + ny * L1[1] + nz * L1[2]);
						float d2 = Math.max(0.0F, nx * L2[0] + ny * L2[1] + nz * L2[2]);
						float d3 = Math.max(0.0F, nx * L3[0] + ny * L3[1] + nz * L3[2]);
						// Specular highlight toward camera (0, 0, +1)
						float hx = L1[0], hy = L1[1], hz = L1[2] + 1.0F;
						float hl = (float) Math.sqrt(hx * hx + hy * hy + hz * hz);
						float spec = (float) Math.pow(Math.max(0.0F, (nx * hx + ny * hy + nz * hz) / hl), 22.0) * 0.42F;

						float lr = 0.24F + d1 * 0.88F + d2 * (0.28F + pr * 0.22F) + d3 * 0.26F;
						float lg = 0.25F + d1 * 0.85F + d2 * (0.28F + pg * 0.22F) + d3 * 0.28F;
						float lb = 0.28F + d1 * 0.80F + d2 * (0.32F + pb * 0.24F) + d3 * 0.34F;
						rBuf[idx] = Math.min(1.0F, cr * lr + spec);
						gBuf[idx] = Math.min(1.0F, cg * lg + spec);
						bBuf[idx] = Math.min(1.0F, cb * lb + spec);
						emR[idx] = 0; emG[idx] = 0; emB[idx] = 0;
					}
				}
			}
		}

		addBloomAndDownsample(W, H, outW, outH, rBuf, gBuf, bBuf, emR, emG, emB);
		return downsample(W, H, outW, outH, rBuf, gBuf, bBuf);
	}

	// =========================================================================
	// 3. FAST 2x2 SUPERSAMPLED TEXTURED 3D CUBOID JSON RENDERER (ITEMS & BLOCKS)
	// =========================================================================
	private record Quad3D(float[][] pts, float[][] uvs, float[] norm, boolean emissive) {}

	@SuppressWarnings("unchecked")
	private static BufferedImage renderCuboidJson(Path jsonPath, BufferedImage tex, int outW, int outH, float yawDeg, float pitchDeg) throws IOException {
		Map<String, Object> root = (Map<String, Object>) JsonMini.parse(Files.readString(jsonPath, StandardCharsets.UTF_8));
		List<Object> elements = (List<Object>) root.getOrDefault("elements", List.of());
		List<Quad3D> quads = new ArrayList<>();

		for (Object elObj : elements) {
			Map<String, Object> el = (Map<String, Object>) elObj;
			float[] from = vec3(el.get("from"));
			float[] to = vec3(el.get("to"));
			Map<String, Object> rotMap = (Map<String, Object>) el.get("rotation");
			Map<String, Object> faces = (Map<String, Object>) el.get("faces");
			if (faces == null) continue;

			float x0 = from[0], y0 = from[1], z0 = from[2];
			float x1 = to[0], y1 = to[1], z1 = to[2];

			for (var entry : faces.entrySet()) {
				String dir = entry.getKey();
				Map<String, Object> f = (Map<String, Object>) entry.getValue();
				float[][] corners = faceCorners(dir, x0, y0, z0, x1, y1, z1);
				if (corners == null) continue;
				if (rotMap != null) {
					float[] origin = vec3(rotMap.get("origin"));
					String axis = String.valueOf(rotMap.get("axis"));
					float angle = ((Number) rotMap.get("angle")).floatValue();
					for (float[] pt : corners) rotatePoint(pt, origin, axis, angle);
				}
				float[] uv = f.containsKey("uv") ? vec4(f.get("uv")) : new float[]{0, 0, 16, 16};
				int uvRot = f.containsKey("rotation") ? ((Number) f.get("rotation")).intValue() : 0;
				float[][] uvs = faceUvs(uv, uvRot);
				boolean emissive = f.containsKey("light_emission") && ((Number) f.get("light_emission")).intValue() >= 8;
				float[] n = faceNorm(corners[0], corners[1], corners[2]);
				quads.add(new Quad3D(corners, uvs, n, emissive));
			}
		}

		int W = outW * 2, H = outH * 2;
		float[] rBuf = new float[W * H], gBuf = new float[W * H], bBuf = new float[W * H];
		float[] emR = new float[W * H], emG = new float[W * H], emB = new float[W * H];
		float[] zBuf = new float[W * H];
		Arrays.fill(zBuf, -1e9F);
		fillStudioBackdrop(W, H, rBuf, gBuf, bBuf, 0x3888CC);

		if (quads.isEmpty()) return downsample(W, H, outW, outH, rBuf, gBuf, bBuf);

		double cy = Math.cos(Math.toRadians(yawDeg)), sy = Math.sin(Math.toRadians(yawDeg));
		double cp = Math.cos(Math.toRadians(pitchDeg)), sp = Math.sin(Math.toRadians(pitchDeg));

		// Rotate all quad corners and compute bounding box
		float minX = 1e9F, maxX = -1e9F, minY = 1e9F, maxY = -1e9F;
		List<float[][]> rotPts = new ArrayList<>(quads.size());
		List<float[]> rotNorms = new ArrayList<>(quads.size());
		for (Quad3D q : quads) {
			float[][] rp = new float[4][3];
			for (int i = 0; i < 4; i++) {
				float x = q.pts[i][0] - 8.0F, y = q.pts[i][1] - 8.0F, z = q.pts[i][2] - 8.0F;
				float rx = (float) (x * cy - z * sy);
				float rz = (float) (x * sy + z * cy);
				float ry = (float) (y * cp - rz * sp);
				float rzz = (float) (y * sp + rz * cp);
				rp[i][0] = rx; rp[i][1] = ry; rp[i][2] = rzz;
				if (rx < minX) minX = rx; if (rx > maxX) maxX = rx;
				if (ry < minY) minY = ry; if (ry > maxY) maxY = ry;
			}
			float nx = q.norm[0], ny = q.norm[1], nz = q.norm[2];
			float rnx = (float) (nx * cy - nz * sy);
			float rnz = (float) (nx * sy + nz * cy);
			float rny = (float) (ny * cp - rnz * sp);
			float rnzz = (float) (ny * sp + rnz * cp);
			rotPts.add(rp);
			rotNorms.add(new float[]{rnx, rny, rnzz});
		}

		float spanX = Math.max(0.5F, maxX - minX);
		float spanY = Math.max(0.5F, maxY - minY);
		float scale = Math.min((W * 0.80F) / spanX, (H * 0.80F) / spanY);
		float cx = (minX + maxX) * 0.5F, cyMid = (minY + maxY) * 0.5F;
		float[] L1 = norm3(0.45F, 0.78F, 0.44F);
		int tw = tex != null ? tex.getWidth() : 16;
		int th = tex != null ? tex.getHeight() : 16;

		for (int qi = 0; qi < quads.size(); qi++) {
			Quad3D q = quads.get(qi);
			float[][] rp = rotPts.get(qi);
			float[] rn = rotNorms.get(qi);
			float shade = q.emissive ? 1.15F : (0.44F + 0.56F * Math.max(0.0F, Math.abs(rn[0] * L1[0] + rn[1] * L1[1] + rn[2] * L1[2])));
			if (!q.emissive && rn[1] > 0.5F) shade = Math.min(1.08F, shade + 0.10F);

			int[][] triIdx = {{0, 1, 2}, {0, 2, 3}};
			for (int[] ti : triIdx) {
				float[] p0 = rp[ti[0]], p1 = rp[ti[1]], p2 = rp[ti[2]];
				float[] u0 = q.uvs[ti[0]], u1 = q.uvs[ti[1]], u2 = q.uvs[ti[2]];
				float sx0 = W * 0.5F + (p0[0] - cx) * scale, sy0 = H * 0.5F - (p0[1] - cyMid) * scale;
				float sx1 = W * 0.5F + (p1[0] - cx) * scale, sy1 = H * 0.5F - (p1[1] - cyMid) * scale;
				float sx2 = W * 0.5F + (p2[0] - cx) * scale, sy2 = H * 0.5F - (p2[1] - cyMid) * scale;

				int xMin = Math.max(0, (int) Math.floor(Math.min(sx0, Math.min(sx1, sx2))));
				int xMax = Math.min(W - 1, (int) Math.ceil(Math.max(sx0, Math.max(sx1, sx2))));
				int yMin = Math.max(0, (int) Math.floor(Math.min(sy0, Math.min(sy1, sy2))));
				int yMax = Math.min(H - 1, (int) Math.ceil(Math.max(sy0, Math.max(sy1, sy2))));
				float area = edge(sx0, sy0, sx1, sy1, sx2, sy2);
				if (Math.abs(area) < 1e-4F) continue;
				float invArea = 1.0F / area;

				for (int py = yMin; py <= yMax; py++) {
					for (int px = xMin; px <= xMax; px++) {
						float w0 = edge(sx1, sy1, sx2, sy2, px + 0.5F, py + 0.5F) * invArea;
						float w1 = edge(sx2, sy2, sx0, sy0, px + 0.5F, py + 0.5F) * invArea;
						float w2 = 1.0F - w0 - w1;
						if (w0 < -1e-4F || w1 < -1e-4F || w2 < -1e-4F) continue;
						float z = w0 * p0[2] + w1 * p1[2] + w2 * p2[2];
						int idx = py * W + px;
						if (z <= zBuf[idx]) continue;

						float u = (w0 * u0[0] + w1 * u1[0] + w2 * u2[0]) / 16.0F;
						float v = (w0 * u0[1] + w1 * u1[1] + w2 * u2[1]) / 16.0F;
						int argb = sampleTex(tex, tw, th, u, v);
						int alpha = (argb >>> 24) & 0xFF;
						if (alpha < 24) continue;

						zBuf[idx] = z;
						float tr = ((argb >> 16) & 0xFF) / 255.0F;
						float tg = ((argb >> 8) & 0xFF) / 255.0F;
						float tb = (argb & 0xFF) / 255.0F;
						rBuf[idx] = Math.min(1.0F, tr * shade);
						gBuf[idx] = Math.min(1.0F, tg * shade);
						bBuf[idx] = Math.min(1.0F, tb * shade);
						if (q.emissive) {
							emR[idx] = tr; emG[idx] = tg; emB[idx] = tb;
						} else {
							emR[idx] = 0; emG[idx] = 0; emB[idx] = 0;
						}
					}
				}
			}
		}

		addBloomAndDownsample(W, H, outW, outH, rBuf, gBuf, bBuf, emR, emG, emB);
		return downsample(W, H, outW, outH, rBuf, gBuf, bBuf);
	}

	private static BufferedImage renderArmorSetComposite(List<String> pieceIds, Path modelDir, Path texDir, int outW, int outH) throws IOException {
		// Stack each piece's exact 3D cuboid model vertically into a cohesive full-body 3D armor suit!
		List<Quad3D> allQuads = new ArrayList<>();
		List<BufferedImage> quadTex = new ArrayList<>();
		for (String piece : pieceIds) {
			Path mp = modelDir.resolve(piece + ".json");
			Path tp = texDir.resolve(piece + ".png");
			if (!Files.exists(mp)) continue;
			BufferedImage tex = Files.exists(tp) ? ImageIO.read(tp.toFile()) : null;
			float yShift = piece.contains("helmet") || piece.contains("gas_mask") ? 28.0F
					: (piece.contains("chestplate") ? 14.0F
					: (piece.contains("leggings") ? 2.0F : -9.0F));
			List<Quad3D> pq = parseQuadsFromJson(mp, yShift);
			for (Quad3D q : pq) {
				allQuads.add(q);
				quadTex.add(tex);
			}
		}
		return renderQuadsMultiTex(allQuads, quadTex, outW, outH, 32.0F, 14.0F);
	}

	@SuppressWarnings("unchecked")
	private static List<Quad3D> parseQuadsFromJson(Path jsonPath, float yShift) throws IOException {
		Map<String, Object> root = (Map<String, Object>) JsonMini.parse(Files.readString(jsonPath, StandardCharsets.UTF_8));
		List<Object> elements = (List<Object>) root.getOrDefault("elements", List.of());
		List<Quad3D> quads = new ArrayList<>();
		for (Object elObj : elements) {
			Map<String, Object> el = (Map<String, Object>) elObj;
			float[] from = vec3(el.get("from"));
			float[] to = vec3(el.get("to"));
			Map<String, Object> rotMap = (Map<String, Object>) el.get("rotation");
			Map<String, Object> faces = (Map<String, Object>) el.get("faces");
			if (faces == null) continue;
			for (var entry : faces.entrySet()) {
				float[][] corners = faceCorners(entry.getKey(), from[0], from[1], from[2], to[0], to[1], to[2]);
				if (corners == null) continue;
				if (rotMap != null) {
					float[] origin = vec3(rotMap.get("origin"));
					String axis = String.valueOf(rotMap.get("axis"));
					float angle = ((Number) rotMap.get("angle")).floatValue();
					for (float[] pt : corners) rotatePoint(pt, origin, axis, angle);
				}
				for (float[] pt : corners) pt[1] += yShift;
				Map<String, Object> f = (Map<String, Object>) entry.getValue();
				float[] uv = f.containsKey("uv") ? vec4(f.get("uv")) : new float[]{0, 0, 16, 16};
				int uvRot = f.containsKey("rotation") ? ((Number) f.get("rotation")).intValue() : 0;
				boolean emissive = f.containsKey("light_emission") && ((Number) f.get("light_emission")).intValue() >= 8;
				quads.add(new Quad3D(corners, faceUvs(uv, uvRot), faceNorm(corners[0], corners[1], corners[2]), emissive));
			}
		}
		return quads;
	}

	private static BufferedImage renderQuadsMultiTex(List<Quad3D> quads, List<BufferedImage> textures, int outW, int outH, float yawDeg, float pitchDeg) {
		int W = outW * 2, H = outH * 2;
		float[] rBuf = new float[W * H], gBuf = new float[W * H], bBuf = new float[W * H];
		float[] emR = new float[W * H], emG = new float[W * H], emB = new float[W * H];
		float[] zBuf = new float[W * H];
		Arrays.fill(zBuf, -1e9F);
		fillStudioBackdrop(W, H, rBuf, gBuf, bBuf, 0x5888EE);
		if (quads.isEmpty()) return downsample(W, H, outW, outH, rBuf, gBuf, bBuf);

		double cy = Math.cos(Math.toRadians(yawDeg)), sy = Math.sin(Math.toRadians(yawDeg));
		double cp = Math.cos(Math.toRadians(pitchDeg)), sp = Math.sin(Math.toRadians(pitchDeg));
		float minX = 1e9F, maxX = -1e9F, minY = 1e9F, maxY = -1e9F;
		List<float[][]> rotPts = new ArrayList<>(quads.size());
		List<float[]> rotNorms = new ArrayList<>(quads.size());
		for (Quad3D q : quads) {
			float[][] rp = new float[4][3];
			for (int i = 0; i < 4; i++) {
				float x = q.pts[i][0] - 8.0F, y = q.pts[i][1] - 8.0F, z = q.pts[i][2] - 8.0F;
				float rx = (float) (x * cy - z * sy), rz = (float) (x * sy + z * cy);
				float ry = (float) (y * cp - rz * sp), rzz = (float) (y * sp + rz * cp);
				rp[i][0] = rx; rp[i][1] = ry; rp[i][2] = rzz;
				if (rx < minX) minX = rx; if (rx > maxX) maxX = rx;
				if (ry < minY) minY = ry; if (ry > maxY) maxY = ry;
			}
			float nx = q.norm[0], ny = q.norm[1], nz = q.norm[2];
			float rnx = (float) (nx * cy - nz * sy), rnz = (float) (nx * sy + nz * cy);
			float rny = (float) (ny * cp - rnz * sp), rnzz = (float) (ny * sp + rnz * cp);
			rotPts.add(rp); rotNorms.add(new float[]{rnx, rny, rnzz});
		}
		float scale = Math.min((W * 0.80F) / Math.max(0.5F, maxX - minX), (H * 0.84F) / Math.max(0.5F, maxY - minY));
		float cx = (minX + maxX) * 0.5F, cyMid = (minY + maxY) * 0.5F;
		float[] L1 = norm3(0.45F, 0.78F, 0.44F);

		for (int qi = 0; qi < quads.size(); qi++) {
			Quad3D q = quads.get(qi);
			BufferedImage tex = textures.get(qi);
			int tw = tex != null ? tex.getWidth() : 16, th = tex != null ? tex.getHeight() : 16;
			float[][] rp = rotPts.get(qi);
			float[] rn = rotNorms.get(qi);
			float shade = q.emissive ? 1.15F : (0.44F + 0.56F * Math.max(0.0F, Math.abs(rn[0] * L1[0] + rn[1] * L1[1] + rn[2] * L1[2])));
			for (int[] ti : new int[][]{{0, 1, 2}, {0, 2, 3}}) {
				float[] p0 = rp[ti[0]], p1 = rp[ti[1]], p2 = rp[ti[2]];
				float[] u0 = q.uvs[ti[0]], u1 = q.uvs[ti[1]], u2 = q.uvs[ti[2]];
				float sx0 = W * 0.5F + (p0[0] - cx) * scale, sy0 = H * 0.5F - (p0[1] - cyMid) * scale;
				float sx1 = W * 0.5F + (p1[0] - cx) * scale, sy1 = H * 0.5F - (p1[1] - cyMid) * scale;
				float sx2 = W * 0.5F + (p2[0] - cx) * scale, sy2 = H * 0.5F - (p2[1] - cyMid) * scale;
				int xMin = Math.max(0, (int) Math.floor(Math.min(sx0, Math.min(sx1, sx2))));
				int xMax = Math.min(W - 1, (int) Math.ceil(Math.max(sx0, Math.max(sx1, sx2))));
				int yMin = Math.max(0, (int) Math.floor(Math.min(sy0, Math.min(sy1, sy2))));
				int yMax = Math.min(H - 1, (int) Math.ceil(Math.max(sy0, Math.max(sy1, sy2))));
				float area = edge(sx0, sy0, sx1, sy1, sx2, sy2);
				if (Math.abs(area) < 1e-4F) continue;
				float invArea = 1.0F / area;
				for (int py = yMin; py <= yMax; py++) {
					for (int px = xMin; px <= xMax; px++) {
						float w0 = edge(sx1, sy1, sx2, sy2, px + 0.5F, py + 0.5F) * invArea;
						float w1 = edge(sx2, sy2, sx0, sy0, px + 0.5F, py + 0.5F) * invArea;
						float w2 = 1.0F - w0 - w1;
						if (w0 < -1e-4F || w1 < -1e-4F || w2 < -1e-4F) continue;
						float z = w0 * p0[2] + w1 * p1[2] + w2 * p2[2];
						int idx = py * W + px;
						if (z <= zBuf[idx]) continue;
						int argb = sampleTex(tex, tw, th, (w0 * u0[0] + w1 * u1[0] + w2 * u2[0]) / 16.0F, (w0 * u0[1] + w1 * u1[1] + w2 * u2[1]) / 16.0F);
						if (((argb >>> 24) & 0xFF) < 24) continue;
						zBuf[idx] = z;
						float tr = ((argb >> 16) & 0xFF) / 255.0F, tg = ((argb >> 8) & 0xFF) / 255.0F, tb = (argb & 0xFF) / 255.0F;
						rBuf[idx] = Math.min(1.0F, tr * shade); gBuf[idx] = Math.min(1.0F, tg * shade); bBuf[idx] = Math.min(1.0F, tb * shade);
						if (q.emissive) { emR[idx] = tr; emG[idx] = tg; emB[idx] = tb; } else { emR[idx] = 0; emG[idx] = 0; emB[idx] = 0; }
					}
				}
			}
		}
		addBloomAndDownsample(W, H, outW, outH, rBuf, gBuf, bBuf, emR, emG, emB);
		return downsample(W, H, outW, outH, rBuf, gBuf, bBuf);
	}

	// =========================================================================
	// 4. SHARED MATH, BACKDROP, BLOOM & DOWNSAMPLING HELPERS
	// =========================================================================
	private static void fillStudioBackdrop(int W, int H, float[] r, float[] g, float[] b, int accentRgb) {
		float ar = ((accentRgb >> 16) & 0xFF) / 255.0F;
		float ag = ((accentRgb >> 8) & 0xFF) / 255.0F;
		float ab = (accentRgb & 0xFF) / 255.0F;
		for (int y = 0; y < H; y++) {
			float dy = (y / (float) H) - 0.5F;
			for (int x = 0; x < W; x++) {
				float dx = (x / (float) W) - 0.5F;
				float dist = (float) Math.sqrt(dx * dx + dy * dy);
				float vig = Math.max(0.0F, 1.0F - dist * 1.35F);
				int idx = y * W + x;
				r[idx] = 0.055F + vig * (0.045F + ar * 0.035F);
				g[idx] = 0.062F + vig * (0.052F + ag * 0.035F);
				b[idx] = 0.082F + vig * (0.068F + ab * 0.045F);
			}
		}
	}

	private static void addBloomAndDownsample(int W, int H, int outW, int outH,
			float[] r, float[] g, float[] b, float[] emR, float[] emG, float[] emB) {
		// Fast separable 2-pass box glow over emissive pixels so crystals & plasma truly radiate!
		int rad = Math.max(4, W / 64);
		float[] tr = new float[W * H], tg = new float[W * H], tb = new float[W * H];
		boolean any = false;
		for (float v : emR) { if (v > 0.01F) { any = true; break; } }
		if (!any) for (float v : emG) { if (v > 0.01F) { any = true; break; } }
		if (!any) for (float v : emB) { if (v > 0.01F) { any = true; break; } }
		if (!any) return;

		float inv = 1.0F / (rad * 2 + 1);
		// Horizontal pass
		for (int y = 0; y < H; y++) {
			int row = y * W;
			float sr = 0, sg = 0, sb = 0;
			for (int k = -rad; k <= rad; k++) {
				int x = Math.max(0, Math.min(W - 1, k));
				sr += emR[row + x]; sg += emG[row + x]; sb += emB[row + x];
			}
			for (int x = 0; x < W; x++) {
				tr[row + x] = sr * inv; tg[row + x] = sg * inv; tb[row + x] = sb * inv;
				int xOut = Math.max(0, x - rad), xIn = Math.min(W - 1, x + rad + 1);
				sr += emR[row + xIn] - emR[row + xOut];
				sg += emG[row + xIn] - emG[row + xOut];
				sb += emB[row + xIn] - emB[row + xOut];
			}
		}
		// Vertical pass + additive composite onto image
		for (int x = 0; x < W; x++) {
			float sr = 0, sg = 0, sb = 0;
			for (int k = -rad; k <= rad; k++) {
				int y = Math.max(0, Math.min(H - 1, k));
				sr += tr[y * W + x]; sg += tg[y * W + x]; sb += tb[y * W + x];
			}
			for (int y = 0; y < H; y++) {
				int idx = y * W + x;
				r[idx] = Math.min(1.0F, r[idx] + sr * inv * 0.85F);
				g[idx] = Math.min(1.0F, g[idx] + sg * inv * 0.85F);
				b[idx] = Math.min(1.0F, b[idx] + sb * inv * 0.85F);
				int yOut = Math.max(0, y - rad), yIn = Math.min(H - 1, y + rad + 1);
				sr += tr[yIn * W + x] - tr[yOut * W + x];
				sg += tg[yIn * W + x] - tg[yOut * W + x];
				sb += tb[yIn * W + x] - tb[yOut * W + x];
			}
		}
	}

	private static BufferedImage downsample(int W, int H, int outW, int outH, float[] r, float[] g, float[] b) {
		BufferedImage out = new BufferedImage(outW, outH, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < outH; y++) {
			int y0 = y * 2, y1 = y0 + 1;
			for (int x = 0; x < outW; x++) {
				int x0 = x * 2, x1 = x0 + 1;
				int i00 = y0 * W + x0, i10 = y0 * W + x1, i01 = y1 * W + x0, i11 = y1 * W + x1;
				int cr = clamp255((r[i00] + r[i10] + r[i01] + r[i11]) * 0.25F);
				int cg = clamp255((g[i00] + g[i10] + g[i01] + g[i11]) * 0.25F);
				int cb = clamp255((b[i00] + b[i10] + b[i01] + b[i11]) * 0.25F);
				out.setRGB(x, y, 0xFF000000 | (cr << 16) | (cg << 8) | cb);
			}
		}
		return out;
	}

	private static int clamp255(float v) {
		return Math.max(0, Math.min(255, (int) (v * 255.0F + 0.5F)));
	}

	private static int sampleTex(BufferedImage tex, int tw, int th, float u, float v) {
		if (tex == null) return 0xFF9AA4B0;
		int ix = Math.max(0, Math.min(tw - 1, (int) (u * tw)));
		int iy = Math.max(0, Math.min(th - 1, (int) (v * th)));
		return tex.getRGB(ix, iy);
	}

	private static float edge(float ax, float ay, float bx, float by, float cx, float cy) {
		return (cx - ax) * (by - ay) - (cy - ay) * (bx - ax);
	}

	private static float[] faceNorm(float[] p0, float[] p1, float[] p2) {
		float ux = p1[0] - p0[0], uy = p1[1] - p0[1], uz = p1[2] - p0[2];
		float vx = p2[0] - p0[0], vy = p2[1] - p0[1], vz = p2[2] - p0[2];
		return norm3(uy * vz - uz * vy, uz * vx - ux * vz, ux * vy - uy * vx);
	}

	private static float[] norm3(float x, float y, float z) {
		float l = (float) Math.sqrt(x * x + y * y + z * z);
		return l > 1e-6F ? new float[]{x / l, y / l, z / l} : new float[]{0, 1, 0};
	}

	private static float[][] faceCorners(String dir, float x0, float y0, float z0, float x1, float y1, float z1) {
		return switch (dir) {
			case "north" -> new float[][]{{x1, y1, z0}, {x0, y1, z0}, {x0, y0, z0}, {x1, y0, z0}};
			case "south" -> new float[][]{{x0, y1, z1}, {x1, y1, z1}, {x1, y0, z1}, {x0, y0, z1}};
			case "west"  -> new float[][]{{x0, y1, z0}, {x0, y1, z1}, {x0, y0, z1}, {x0, y0, z0}};
			case "east"  -> new float[][]{{x1, y1, z1}, {x1, y1, z0}, {x1, y0, z0}, {x1, y0, z1}};
			case "up"    -> new float[][]{{x0, y1, z0}, {x1, y1, z0}, {x1, y1, z1}, {x0, y1, z1}};
			case "down"  -> new float[][]{{x0, y0, z1}, {x1, y0, z1}, {x1, y0, z0}, {x0, y0, z0}};
			default -> null;
		};
	}

	private static float[][] faceUvs(float[] uv, int rot) {
		float[][] base = {{uv[0], uv[1]}, {uv[2], uv[1]}, {uv[2], uv[3]}, {uv[0], uv[3]}};
		int shift = ((rot / 90) % 4 + 4) % 4;
		float[][] out = new float[4][2];
		for (int i = 0; i < 4; i++) out[i] = base[(i + shift) % 4];
		return out;
	}

	private static void rotatePoint(float[] p, float[] o, String axis, float angleDeg) {
		double rad = Math.toRadians(angleDeg);
		double c = Math.cos(rad), s = Math.sin(rad);
		float x = p[0] - o[0], y = p[1] - o[1], z = p[2] - o[2];
		switch (axis) {
			case "x" -> { p[1] = (float) (y * c - z * s) + o[1]; p[2] = (float) (y * s + z * c) + o[2]; }
			case "y" -> { p[0] = (float) (x * c + z * s) + o[0]; p[2] = (float) (-x * s + z * c) + o[2]; }
			case "z" -> { p[0] = (float) (x * c - y * s) + o[0]; p[1] = (float) (x * s + y * c) + o[1]; }
		}
	}

	@SuppressWarnings("unchecked")
	private static float[] vec3(Object o) {
		List<Object> l = (List<Object>) o;
		return new float[]{((Number) l.get(0)).floatValue(), ((Number) l.get(1)).floatValue(), ((Number) l.get(2)).floatValue()};
	}

	@SuppressWarnings("unchecked")
	private static float[] vec4(Object o) {
		List<Object> l = (List<Object>) o;
		return new float[]{((Number) l.get(0)).floatValue(), ((Number) l.get(1)).floatValue(), ((Number) l.get(2)).floatValue(), ((Number) l.get(3)).floatValue()};
	}

	// Minimal zero-dependency JSON parser
	private static final class JsonMini {
		private final String s;
		private int p;
		private JsonMini(String s) { this.s = s; }

		static Object parse(String s) { return new JsonMini(s).value(); }

		private void ws() { while (p < s.length() && s.charAt(p) <= ' ') p++; }

		private Object value() {
			ws();
			char c = s.charAt(p);
			if (c == '{') return obj();
			if (c == '[') return arr();
			if (c == '"') return str();
			if (c == 't') { p += 4; return Boolean.TRUE; }
			if (c == 'f') { p += 5; return Boolean.FALSE; }
			if (c == 'n') { p += 4; return null; }
			return num();
		}

		private Map<String, Object> obj() {
			p++;
			Map<String, Object> m = new LinkedHashMap<>();
			ws();
			if (s.charAt(p) == '}') { p++; return m; }
			while (true) {
				ws();
				String k = str();
				ws(); p++; // ':'
				m.put(k, value());
				ws();
				if (s.charAt(p++) == '}') return m;
			}
		}

		private List<Object> arr() {
			p++;
			List<Object> l = new ArrayList<>();
			ws();
			if (s.charAt(p) == ']') { p++; return l; }
			while (true) {
				l.add(value());
				ws();
				if (s.charAt(p++) == ']') return l;
			}
		}

		private String str() {
			p++;
			StringBuilder sb = new StringBuilder();
			while (p < s.length()) {
				char c = s.charAt(p++);
				if (c == '"') return sb.toString();
				if (c == '\\') {
					char e = s.charAt(p++);
					sb.append(switch (e) { case 'n' -> '\n'; case 'r' -> '\r'; case 't' -> '\t'; default -> e; });
				} else sb.append(c);
			}
			return sb.toString();
		}

		private Number num() {
			int start = p;
			while (p < s.length() && "0123456789+-.eE".indexOf(s.charAt(p)) >= 0) p++;
			String tok = s.substring(start, p);
			return tok.contains(".") || tok.contains("e") || tok.contains("E") ? Double.parseDouble(tok) : Long.parseLong(tok);
		}
	}
}
