package dev.noclip.backrooms.core;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/**
 * Sublevels ("0.1", "1.1", "4.3", ...): places that belong to a parent level but have a dimension of their own.
 *
 * A sublevel is an ordinary level, registered through {@link LevelExits} under its own key, plus two facts the rest
 * of the mod can ask about: which level is its parent (where "leave" goes) and where a random cut-out lands you
 * (the Wiki's "noclipping out of a level by accident", with weights). Entrances are the parent level's business:
 * the parent places a door that calls {@code LevelExits.use(player, "0.1")}, nothing here knows about it.
 *
 * To add a sublevel: put it in its own package like any level, and call {@link #register} from its register().
 * See README.md, "Sublevels".
 */
public final class Sublevels {
	/** What other code may ask about a sublevel. cutOuts maps a level key to a weight. */
	public record Sublevel(String key, String parent, ResourceKey<Level> dimension, Map<String, Integer> cutOuts) {
	}

	private static final Map<String, Sublevel> ALL = new ConcurrentHashMap<>();

	private Sublevels() {
	}

	public static void register(String key, String parent, ResourceKey<Level> dimension, Map<String, Integer> cutOuts,
			LevelExits.Arrival arrival) {
		ALL.put(key, new Sublevel(key, parent, dimension, Map.copyOf(cutOuts)));
		LevelExits.open(key, arrival);
	}

	public static Sublevel get(String key) {
		return ALL.get(key);
	}

	/** The sublevel whose dimension this is, or null (a main level). */
	public static Sublevel of(ResourceKey<Level> dimension) {
		for (Sublevel sublevel : ALL.values()) {
			if (sublevel.dimension().equals(dimension)) return sublevel;
		}
		return null;
	}

	public static String parentOf(String key) {
		Sublevel sublevel = ALL.get(key);
		return sublevel == null ? null : sublevel.parent();
	}

	/** Picks one key by weight; roll is any non-negative number. */
	public static String pick(Map<String, Integer> weights, int roll) {
		int total = 0;
		for (int weight : weights.values()) total += weight;
		if (total <= 0) return null;
		int at = roll % total;
		// A fixed order, so the same roll always gives the same answer.
		for (String key : weights.keySet().stream().sorted().toList()) {
			at -= weights.get(key);
			if (at < 0) return key;
		}
		return null;
	}

	/** Takes the player back to the parent level. */
	public static void leave(ServerPlayer player, String key) {
		String parent = parentOf(key);
		if (parent != null) LevelExits.use(player, parent);
	}

	/** A random cut-out: the player slips out of the sublevel to somewhere its weights name. */
	public static void cutOut(ServerPlayer player, String key) {
		Sublevel sublevel = ALL.get(key);
		if (sublevel == null) return;
		String target = pick(sublevel.cutOuts(), player.getRandom().nextInt(1 << 20));
		LevelExits.use(player, target == null ? sublevel.parent() : target);
	}
}
