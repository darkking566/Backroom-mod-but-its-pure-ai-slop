package dev.noclip.backrooms.crafting;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.noclip.backrooms.BackroomsMod;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.Identifier;
import net.minecraft.world.Container;

/**
 * Loads all Backrooms workstation recipes from the bundled shaped JSON catalog
 * ({@code data/noclip_backrooms/backrooms_recipes/station_recipes.json}).
 * Synchronized across server menus, client recipe books, and JEI categories.
 */
public final class BackroomsRecipeRegistry {
	public static final String RESOURCE_PATH = "data/" + BackroomsMod.MOD_ID + "/backrooms_recipes/station_recipes.json";

	private static List<StationRecipe> allRecipes = List.of();
	private static Map<Identifier, StationRecipe> byId = Map.of();
	private static Map<StationType, List<StationRecipe>> byStation = Map.of();
	private static boolean loaded = false;

	private BackroomsRecipeRegistry() {
	}

	public static synchronized void ensureLoaded() {
		if (loaded) return;
		reload();
	}

	public static synchronized void reload() {
		List<StationRecipe> list = new ArrayList<>();
		Map<Identifier, StationRecipe> idMap = new LinkedHashMap<>();
		Map<StationType, List<StationRecipe>> stationMap = new EnumMap<>(StationType.class);
		for (StationType type : StationType.values()) {
			stationMap.put(type, new ArrayList<>());
		}

		try (InputStream stream = BackroomsRecipeRegistry.class.getClassLoader().getResourceAsStream(RESOURCE_PATH)) {
			if (stream == null) {
				throw new IllegalStateException("Missing station recipes resource: " + RESOURCE_PATH);
			}
			JsonObject root = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
			JsonArray recipes = root.getAsJsonArray("recipes");
			for (JsonElement elem : recipes) {
				JsonObject obj = elem.getAsJsonObject();
				Identifier id = Identifier.parse(obj.get("id").getAsString());
				StationType station = StationType.byId(obj.get("station").getAsString());
				String category = obj.has("category") ? obj.get("category").getAsString() : "parts";
				int xp = obj.has("xp") ? obj.get("xp").getAsInt() : 6;
				List<StationRecipe.ItemCount> outputs = parseItemList(obj.getAsJsonArray("outputs"));

				int width;
				int height;
				List<StationRecipe.ItemCount> cells;
				if (obj.has("pattern") && obj.has("key")) {
					JsonArray patArr = obj.getAsJsonArray("pattern");
					JsonObject keyObj = obj.getAsJsonObject("key");
					Map<Character, StationRecipe.ItemCount> keyMap = new HashMap<>();
					for (Map.Entry<String, JsonElement> e : keyObj.entrySet()) {
						if (e.getKey().isEmpty()) continue;
						char ch = e.getKey().charAt(0);
						if (e.getValue().isJsonPrimitive()) {
							keyMap.put(ch, new StationRecipe.ItemCount(Identifier.parse(e.getValue().getAsString()), 1));
						} else {
							JsonObject ko = e.getValue().getAsJsonObject();
							Identifier itemId = Identifier.parse(ko.get("item").getAsString());
							int count = ko.has("count") ? ko.get("count").getAsInt() : 1;
							keyMap.put(ch, new StationRecipe.ItemCount(itemId, count));
						}
					}
					height = patArr.size();
					width = 0;
					for (JsonElement rowEl : patArr) {
						width = Math.max(width, rowEl.getAsString().length());
					}
					cells = new ArrayList<>(width * height);
					for (int y = 0; y < height; y++) {
						String row = patArr.get(y).getAsString();
						for (int x = 0; x < width; x++) {
							char ch = x < row.length() ? row.charAt(x) : ' ';
							cells.add(ch == ' ' ? null : keyMap.get(ch));
						}
					}
				} else {
					// Salvage or 1-slot direct input fallback
					List<StationRecipe.ItemCount> rawInputs = parseItemList(obj.getAsJsonArray("inputs"));
					width = Math.min(station.gridWidth(), Math.max(1, rawInputs.size()));
					height = Math.max(1, (rawInputs.size() + width - 1) / width);
					cells = new ArrayList<>(width * height);
					for (int i = 0; i < width * height; i++) {
						cells.add(i < rawInputs.size() ? rawInputs.get(i) : null);
					}
				}

				List<StationRecipe.ItemCount> aggregated = StationRecipe.aggregateInputs(cells);
				StationRecipe recipe = new StationRecipe(
						id, station, category, width, height,
						Collections.unmodifiableList(cells),
						aggregated,
						List.copyOf(outputs),
						xp
				);
				list.add(recipe);
				idMap.put(id, recipe);
				stationMap.get(station).add(recipe);
			}
		} catch (Exception e) {
			throw new RuntimeException("Failed to load Backrooms workstation recipes from " + RESOURCE_PATH, e);
		}

		allRecipes = Collections.unmodifiableList(list);
		byId = Collections.unmodifiableMap(idMap);
		Map<StationType, List<StationRecipe>> frozen = new EnumMap<>(StationType.class);
		for (Map.Entry<StationType, List<StationRecipe>> entry : stationMap.entrySet()) {
			frozen.put(entry.getKey(), Collections.unmodifiableList(entry.getValue()));
		}
		byStation = Collections.unmodifiableMap(frozen);
		loaded = true;
	}

	private static List<StationRecipe.ItemCount> parseItemList(JsonArray arr) {
		List<StationRecipe.ItemCount> out = new ArrayList<>(arr.size());
		for (JsonElement elem : arr) {
			JsonObject o = elem.getAsJsonObject();
			Identifier itemId = Identifier.parse(o.get("item").getAsString());
			int count = o.has("count") ? o.get("count").getAsInt() : 1;
			out.add(new StationRecipe.ItemCount(itemId, count));
		}
		return out;
	}

	public static StationRecipe.Match findMatch(StationType station, Container container) {
		ensureLoaded();
		int gw = station.gridWidth();
		int gh = station.gridHeight();
		for (StationRecipe recipe : byStation(station)) {
			StationRecipe.Match match = recipe.matchInGrid(container, gw, gh);
			if (match != null) return match;
		}
		return null;
	}

	public static List<StationRecipe> all() {
		ensureLoaded();
		return allRecipes;
	}

	public static List<StationRecipe> byStation(StationType station) {
		ensureLoaded();
		return byStation.getOrDefault(station, List.of());
	}

	public static StationRecipe byId(Identifier id) {
		ensureLoaded();
		return byId.get(id);
	}
}
