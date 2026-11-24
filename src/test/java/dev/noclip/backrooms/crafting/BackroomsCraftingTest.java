package dev.noclip.backrooms.crafting;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
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
	void stationRecipesLoadCleanlyAcrossAllFourStations() throws IOException {
		BackroomsRecipeRegistry.reload();
		List<StationRecipe> all = BackroomsRecipeRegistry.all();
		assertTrue(all.size() >= 170, "Expected at least 170 workstation recipes, got " + all.size());

		Set<String> knownModItems = knownModItemIds();
		Set<Identifier> seenIds = new HashSet<>();
		for (StationType station : StationType.values()) {
			List<StationRecipe> perStation = BackroomsRecipeRegistry.byStation(station);
			assertFalse(perStation.isEmpty(), "Station " + station.id() + " must have recipes");
		}
		for (StationRecipe r : all) {
			assertTrue(seenIds.add(r.id()), "Duplicate station recipe ID: " + r.id());
			assertFalse(r.inputs().isEmpty(), "Empty inputs on " + r.id());
			assertFalse(r.outputs().isEmpty(), "Empty outputs on " + r.id());
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
		assertTrue(files.size() >= 20, "Expected at least 20 vanilla 3x3 recipes, got " + files.size());
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
}
