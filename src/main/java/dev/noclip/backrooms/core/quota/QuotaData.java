package dev.noclip.backrooms.core.quota;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.noclip.backrooms.BackroomsMod;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * The team's M.E.G. cycle, saved with the world (on the Overworld): one team per server, every player on it
 * (docs/project/1-gameplay/README.md). Nothing runs until someone first comes home from the Backrooms.
 */
public final class QuotaData extends SavedData {
	/** Whether the cycle has started: the first time anyone walked out of the Backrooms alive. */
	boolean active;
	/** This cycle's number (1-based), and how many cycles the team has paid in full. */
	int cycle = 1, paid;
	/** Home time spent in this cycle (ticks of the Overworld clock while someone was there). */
	long homeTicks;
	/** Points this cycle asks for, points handed in, and debt carried from cycles not paid. */
	long required, delivered, debt;
	/** The most players seen on this cycle (the quota grows with it), and who they were. */
	int team;
	final Set<String> seen = new HashSet<>();
	/** Items this cycle requires (item id -> how many), and how many have been handed in. */
	final Map<String, Integer> items = new LinkedHashMap<>(), handed = new LinkedHashMap<>();
	/** Overworld clock time at which M.E.G. takes the players at home back into the Backrooms; -1 when not due. */
	long redeployAt = -1;
	/** The last day announced on screen, so each day's title shows once. */
	int lastDay;
	/** Levels the team has been to: M.E.G. can send them straight there, for a price. */
	final Set<String> visited = new HashSet<>();
	/** Levels the team has mapped with a beacon (core.field.Evidence): M.E.G. sends people there for nothing. */
	final Set<String> mapped = new HashSet<>();

	private record Saved(boolean active, int cycle, int paid, long homeTicks, long required, long delivered, long debt, int team,
			List<String> seen, Map<String, Integer> items, Map<String, Integer> handed, long redeployAt, int lastDay,
			List<String> visited, List<String> mapped) {
	}

	private static final Codec<Saved> SAVED = RecordCodecBuilder.create(i -> i.group(
			Codec.BOOL.optionalFieldOf("active", false).forGetter(Saved::active),
			Codec.INT.optionalFieldOf("cycle", 1).forGetter(Saved::cycle),
			Codec.INT.optionalFieldOf("paid", 0).forGetter(Saved::paid),
			Codec.LONG.optionalFieldOf("home_ticks", 0L).forGetter(Saved::homeTicks),
			Codec.LONG.optionalFieldOf("required", 0L).forGetter(Saved::required),
			Codec.LONG.optionalFieldOf("delivered", 0L).forGetter(Saved::delivered),
			Codec.LONG.optionalFieldOf("debt", 0L).forGetter(Saved::debt),
			Codec.INT.optionalFieldOf("team", 1).forGetter(Saved::team),
			Codec.STRING.listOf().optionalFieldOf("seen", List.of()).forGetter(Saved::seen),
			Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("items", Map.of()).forGetter(Saved::items),
			Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("handed", Map.of()).forGetter(Saved::handed),
			Codec.LONG.optionalFieldOf("redeploy_at", -1L).forGetter(Saved::redeployAt),
			Codec.INT.optionalFieldOf("last_day", 0).forGetter(Saved::lastDay),
			Codec.STRING.listOf().optionalFieldOf("visited", List.of()).forGetter(Saved::visited),
			Codec.STRING.listOf().optionalFieldOf("mapped", List.of()).forGetter(Saved::mapped)).apply(i, Saved::new));

	private static final Codec<QuotaData> CODEC = SAVED.xmap(QuotaData::fromSaved, QuotaData::toSaved);

	public static final SavedDataType<QuotaData> TYPE = new SavedDataType<>(BackroomsMod.id("meg_quota"), QuotaData::new, CODEC,
			DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

	public QuotaData() {
	}

	public static QuotaData get(ServerLevel overworld) {
		return overworld.getDataStorage().computeIfAbsent(TYPE);
	}

	private static QuotaData fromSaved(Saved s) {
		QuotaData d = new QuotaData();
		d.active = s.active;
		d.cycle = s.cycle;
		d.paid = s.paid;
		d.homeTicks = s.homeTicks;
		d.required = s.required;
		d.delivered = s.delivered;
		d.debt = s.debt;
		d.team = s.team;
		d.seen.addAll(s.seen);
		d.items.putAll(s.items);
		d.handed.putAll(s.handed);
		d.redeployAt = s.redeployAt;
		d.lastDay = s.lastDay;
		d.visited.addAll(s.visited);
		d.mapped.addAll(s.mapped);
		return d;
	}

	private Saved toSaved() {
		return new Saved(active, cycle, paid, homeTicks, required, delivered, debt, team, new ArrayList<>(seen), new LinkedHashMap<>(items),
				new LinkedHashMap<>(handed), redeployAt, lastDay, new ArrayList<>(visited), new ArrayList<>(mapped));
	}

	public boolean mapped(String level) {
		return mapped.contains(level);
	}

	/** A level mapped with a beacon: from now on M.E.G. sends people there for nothing. */
	public void map(String level) {
		if (mapped.add(level)) setDirty();
	}

	void changed() {
		setDirty();
	}

	/** Whether everything this cycle asks for is in: the debt, the points and every required item. */
	boolean met() {
		if (debt > 0 || delivered < required) return false;
		for (var e : items.entrySet()) {
			if (handed.getOrDefault(e.getKey(), 0) < e.getValue()) return false;
		}
		return true;
	}

	/** Required items not yet handed in, as points of debt. */
	long missingItemPoints() {
		long missing = 0;
		for (var e : items.entrySet()) missing += Math.max(0, e.getValue() - handed.getOrDefault(e.getKey(), 0));
		return missing * QuotaMath.MISSING_ITEM_POINTS;
	}

	/** How many quotas the team has met: the institute's clearance. */
	public int paid() {
		return paid;
	}

	public boolean active() {
		return active;
	}

	public long debt() {
		return debt;
	}

	public long homeTicks() {
		return homeTicks;
	}

	public int cycle() {
		return cycle;
	}

	public long required() {
		return required;
	}

	public long delivered() {
		return delivered;
	}

	public long redeployAt() {
		return redeployAt;
	}

	public Map<String, Integer> items() {
		return items;
	}

	public Map<String, Integer> handed() {
		return handed;
	}
}
