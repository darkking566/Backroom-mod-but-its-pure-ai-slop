package dev.noclip.backrooms.crafting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.noclip.backrooms.weapons.WeaponEvolution;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

final class BackroomsCraftingTest {
	@Test
	void stationRecipesLoadCleanlyAcrossAllSixStationsWithValidShapedDimensions() throws IOException {
		BackroomsRecipeRegistry.reload();
		List<StationRecipe> all = BackroomsRecipeRegistry.all();
		assertTrue(all.size() >= 250, "Expected at least 250 workstation recipes, got " + all.size());

		Set<String> knownModItems = knownModItemIds();
		Set<Identifier> seenIds = new HashSet<>();
		for (StationType station : StationType.values()) {
			List<StationRecipe> perStation = BackroomsRecipeRegistry.byStation(station);
			assertFalse(perStation.isEmpty(), "Station " + station.id() + " must have recipes");
			// Every station + 136px recipe book must fit side-by-side inside 1080p 4x (480x270) AND 854x480 2x (427x240)
			assertTrue(136 + 3 + station.guiWidth() + 6 <= 427,
					"Station " + station.id() + " + recipe book exceeds 427px width");
			assertTrue(station.guiHeight() <= 240,
					"Station " + station.id() + " height exceeds 240px: " + station.guiHeight());
		}
		for (StationRecipe r : all) {
			assertTrue(seenIds.add(r.id()), "Duplicate station recipe ID: " + r.id());
			assertFalse(r.inputs().isEmpty(), "Empty inputs on " + r.id());
			assertFalse(r.outputs().isEmpty(), "Empty outputs on " + r.id());
			assertTrue(r.patternWidth() >= 1 && r.patternWidth() <= r.station().gridWidth(),
					"Pattern width out of bounds on " + r.id());
			assertTrue(r.patternHeight() >= 1 && r.patternHeight() <= r.station().gridHeight(),
					"Pattern height out of bounds on " + r.id());
			// Enforce user rule: 5x5 / 7x7 / 9x9 tables MUST actually exceed 3x3!
			if (r.station() == StationType.WORKSHOP) {
				assertTrue(Math.max(r.patternWidth(), r.patternHeight()) >= 4,
						"5x5 Workshop recipe must exceed 3x3: " + r.id());
			} else if (r.station() == StationType.SPIDERWEB) {
				assertTrue(r.patternWidth() >= 5 && r.patternHeight() >= 5,
						"7x7 Primary Spiderweb recipe must span at least 5x5: " + r.id());
			} else if (r.station() == StationType.ULTIMATE_SPIDERWEB) {
				assertTrue(r.patternWidth() >= 7 && r.patternHeight() >= 7,
						"9x9 Ultimate Spiderweb recipe must span at least 7x7: " + r.id());
			}
			for (StationRecipe.ItemCount in : r.inputs()) {
				assertTrue(in.count() > 0, "Non-positive input count on " + r.id());
				assertItemExists(in.itemId(), knownModItems, "input of " + r.id());
			}
			for (StationRecipe.ItemCount out : r.outputs()) {
				assertTrue(out.count() > 0, "Non-positive output count on " + r.id());
				assertItemExists(out.itemId(), knownModItems, "output of " + r.id());
			}
		}
	}

	@Test
	void allVanillaRecipesAreValidJson() throws IOException {
		Path recipeDir = Path.of("src/main/resources/data/noclip_backrooms/recipe");
		assertTrue(Files.isDirectory(recipeDir), "Recipe directory missing");
		Set<String> knownModItems = knownModItemIds();
		List<Path> files;
		try (var stream = Files.list(recipeDir)) {
			files = stream.filter(p -> p.toString().endsWith(".json")).toList();
		}
		assertTrue(files.size() >= 38, "Expected at least 38 vanilla 3x3 recipes, got " + files.size());
		for (Path f : files) {
			JsonObject obj = JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8)).getAsJsonObject();
			assertTrue(obj.has("type"), "Missing type in " + f.getFileName());
			assertTrue(obj.has("result"), "Missing result in " + f.getFileName());
			JsonElement resId = obj.getAsJsonObject("result").get("id");
			assertTrue(resId != null && !resId.getAsString().isBlank(), "Missing result.id in " + f.getFileName());
			assertItemExists(Identifier.parse(resId.getAsString()), knownModItems, "result of " + f.getFileName());
			if (obj.has("ingredients")) {
				for (JsonElement ing : obj.getAsJsonArray("ingredients")) {
					if (ing.isJsonPrimitive()) {
						assertItemExists(Identifier.parse(ing.getAsString()), knownModItems, "ingredient of " + f.getFileName());
					}
				}
			}
			if (obj.has("key")) {
				for (var entry : obj.getAsJsonObject("key").entrySet()) {
					if (entry.getValue().isJsonPrimitive()) {
						assertItemExists(Identifier.parse(entry.getValue().getAsString()), knownModItems, "key of " + f.getFileName());
					}
				}
			}
		}
	}

	private static Set<String> knownModItemIds() throws IOException {
		Path itemsDir = Path.of("src/main/resources/assets/noclip_backrooms/items");
		Set<String> ids = new HashSet<>();
		try (var stream = Files.list(itemsDir)) {
			stream.filter(p -> p.toString().endsWith(".json")).forEach(p -> {
				String fn = p.getFileName().toString();
				ids.add(fn.substring(0, fn.length() - 5));
			});
		}
		return ids;
	}

	private static void assertItemExists(Identifier id, Set<String> knownModItems, String context) {
		if ("noclip_backrooms".equals(id.getNamespace())) {
			assertTrue(knownModItems.contains(id.getPath()),
					"Unknown noclip_backrooms item '" + id + "' referenced in " + context);
		}
	}

	@Test
	void all3dModelsAndArmorTexturesExistAndAreValid() throws IOException {
		Path equipTexDir = Path.of("src/main/resources/assets/noclip_backrooms/textures/item/equipment3d");
		Path modelDir = Path.of("src/main/resources/assets/noclip_backrooms/models/item/crafting");
		assertTrue(Files.isDirectory(equipTexDir), "3D item texture dir missing");
		List<Path> textures;
		try (var stream = Files.list(equipTexDir)) {
			textures = stream.filter(p -> p.toString().endsWith(".png")).toList();
		}
		assertTrue(textures.size() >= 369, "Expected all 369 3D item textures, got " + textures.size());
		for (Path tex : textures) {
			String fn = tex.getFileName().toString();
			String id = fn.substring(0, fn.length() - 4);
			Path modelFile = modelDir.resolve(id + ".json");
			assertTrue(Files.exists(modelFile), "Missing 3D item model for " + id);
			JsonObject obj = JsonParser.parseString(Files.readString(modelFile, StandardCharsets.UTF_8)).getAsJsonObject();
			assertTrue(obj.has("elements") && !obj.getAsJsonArray("elements").isEmpty(), "Empty 3D elements in item " + id);
			assertTrue(obj.has("display"), "Missing 3D display transforms in item " + id);
		}

		Path blockTexDir = Path.of("src/main/resources/assets/noclip_backrooms/textures/block/crafting3d");
		Path blockModelDir = Path.of("src/main/resources/assets/noclip_backrooms/models/block/crafting");
		assertTrue(Files.isDirectory(blockTexDir), "3D block texture dir missing");
		List<Path> blockTextures;
		try (var stream = Files.list(blockTexDir)) {
			blockTextures = stream.filter(p -> p.toString().endsWith(".png")).toList();
		}
		assertTrue(blockTextures.size() >= 59, "Expected all 59 3D block textures, got " + blockTextures.size());
		for (Path tex : blockTextures) {
			String fn = tex.getFileName().toString();
			String id = fn.substring(0, fn.length() - 4);
			Path modelFile = blockModelDir.resolve(id + ".json");
			assertTrue(Files.exists(modelFile), "Missing 3D block model for " + id);
			JsonObject obj = JsonParser.parseString(Files.readString(modelFile, StandardCharsets.UTF_8)).getAsJsonObject();
			assertTrue(obj.has("elements") && !obj.getAsJsonArray("elements").isEmpty(), "Empty 3D elements in block " + id);
		}

		// Verify all 39 Blender multi-block & half-sliced noclipped prop OBJs exist and are non-empty
		Path propObjDir = Path.of("src/main/resources/assets/noclip_backrooms/models/blender_props");
		assertTrue(Files.isDirectory(propObjDir), "Blender props dir missing");
		List<Path> propObjs;
		try (var stream = Files.list(propObjDir)) {
			propObjs = stream.filter(p -> p.toString().endsWith(".obj")).toList();
		}
		assertTrue(propObjs.size() >= 39, "Expected at least 39 Blender prop OBJs, got " + propObjs.size());
		for (Path obj : propObjs) {
			assertTrue(Files.size(obj) > 400, "Empty Blender prop OBJ: " + obj.getFileName());
		}

		Path armorDir = Path.of("src/main/resources/assets/noclip_backrooms/textures/entity/armor3d");
		for (String set : List.of("improvised_office", "almond_gas_mask", "scrap_tactical", "skin_stealer", "boiler_thermal", "abyssal_void")) {
			assertTrue(Files.exists(armorDir.resolve(set + ".png")), "Missing worn 3D armor texture for " + set);
		}

		// Verify all 9 unified GDD item tags exist and reference valid items
		Path tagDir = Path.of("src/main/resources/data/noclip_backrooms/tags/item");
		Set<String> knownModItems = knownModItemIds();
		for (String tag : List.of("electronics", "scrap_metal", "office_junk", "cloth", "wood_scrap", "glass_tier", "circuits", "science_packs", "geodes")) {
			Path f = tagDir.resolve(tag + ".json");
			assertTrue(Files.exists(f), "Missing item tag " + tag);
			JsonObject obj = JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8)).getAsJsonObject();
			for (JsonElement v : obj.getAsJsonArray("values")) {
				assertItemExists(Identifier.parse(v.getAsString()), knownModItems, "tag " + tag);
			}
		}
	}

	@Test
	void allBlenderWeaponMeshesAndEvolutionMathAreValid() throws IOException {
		Path blenderDir = Path.of("src/main/resources/assets/noclip_backrooms/models/blender");
		assertTrue(Files.isDirectory(blenderDir), "Blender weapon mesh directory missing");
		List<String> expectedMeshes = List.of(
				"gun_railgun", "gun_sniper", "gun_assault_rifle", "gun_shotgun",
				"gun_pistol", "gun_revolver", "gun_flamethrower", "gun_sonic_pulse", "office_crossbow",
				"magic_staff_t1", "magic_staff_t2", "magic_staff_t3",
				"magic_crimson_scythe", "magic_grimoire", "magic_bell", "magic_watch",
				"melee_greatsword", "melee_curved_dao", "melee_warhammer", "melee_polearm", "melee_dagger",
				"evo_halo_ring"
		);
		for (String name : expectedMeshes) {
			Path obj = blenderDir.resolve(name + ".obj");
			Path mtl = blenderDir.resolve(name + ".mtl");
			assertTrue(Files.exists(obj) && Files.size(obj) > 500, "Missing or empty Blender OBJ: " + name);
			assertTrue(Files.exists(mtl) && Files.size(mtl) > 50, "Missing or empty Blender MTL: " + name);
		}
		Path whiteTex = Path.of("src/main/resources/assets/noclip_backrooms/textures/item/blender_white.png");
		assertTrue(Files.exists(whiteTex) && Files.size(whiteTex) > 40, "Missing blender_white.png");

		// Verify signature weapon evolution thresholds & scaling math
		assertEquals(1, WeaponEvolution.tierForKills(0));
		assertEquals(1, WeaponEvolution.tierForKills(9));
		assertEquals(2, WeaponEvolution.tierForKills(10));
		assertEquals(2, WeaponEvolution.tierForKills(29));
		assertEquals(3, WeaponEvolution.tierForKills(30));
		assertTrue(WeaponEvolution.damageMultiplierForTier(3) > WeaponEvolution.damageMultiplierForTier(2));
		assertTrue(WeaponEvolution.damageMultiplierForTier(2) > WeaponEvolution.damageMultiplierForTier(1));
		assertTrue(WeaponEvolution.sanityMultiplierForTier(3) < WeaponEvolution.sanityMultiplierForTier(2));
	}
}
