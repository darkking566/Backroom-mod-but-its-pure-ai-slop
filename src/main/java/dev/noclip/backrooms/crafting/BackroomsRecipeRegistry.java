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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.Identifier;

/**
 * Loads all Backrooms workstation recipes from the bundled JSON catalog
 * ({@code data/noclip_backrooms/backrooms_recipes/station_recipes.json}).
 * Deterministic on both server and client so JEI and workstation GUIs always stay 100% synchronized.
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
				List<StationRecipe.ItemCount> inputs = parseItemList(obj.getAsJsonArray("inputs"));
				List<StationRecipe.ItemCount> outputs = parseItemList(obj.getAsJsonArray("outputs"));
				int xp = obj.has("xp") ? obj.get("xp").getAsInt() : 6;
				StationRecipe recipe = new StationRecipe(id, station, List.copyOf(inputs), List.copyOf(outputs), xp);
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
