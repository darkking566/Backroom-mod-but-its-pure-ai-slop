package dev.noclip.backrooms.core.floors;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * One trip through the Backrooms, counted the way 狐霖's Backrooms RPG counts it (see docs/FLOORS.md): {@code floor} is
 * how many levels the player has come into on this trip, every exit one more and going back counting too; {@code visited}
 * are the different levels among them; {@code best} is the deepest floor the player has ever reached. A floor of 0 means
 * the player is not on a trip (in the Overworld).
 */
public record FloorRun(int floor, int best, List<String> visited) {
	public static final FloorRun NONE = new FloorRun(0, 0, List.of());

	public static final Codec<FloorRun> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.INT.optionalFieldOf("floor", 0).forGetter(FloorRun::floor),
			Codec.INT.optionalFieldOf("best", 0).forGetter(FloorRun::best),
			Codec.STRING.listOf().optionalFieldOf("visited", List.of()).forGetter(FloorRun::visited)
	).apply(instance, FloorRun::new));

	public static final StreamCodec<ByteBuf, FloorRun> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, FloorRun::floor,
			ByteBufCodecs.VAR_INT, FloorRun::best,
			ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), FloorRun::visited,
			FloorRun::new);

	public FloorRun {
		visited = List.copyOf(visited);
	}

	/** How many different levels this trip has been through. */
	public int distinct() {
		return visited.size();
	}

	/** Falling into the Backrooms from the Overworld: the first floor of a new trip. */
	public FloorRun start(String level) {
		return new FloorRun(1, Math.max(best, 1), List.of(level));
	}

	/** Through an exit into a level, new or not: one floor further. */
	public FloorRun enter(String level) {
		List<String> seen = visited;
		if (!seen.contains(level)) {
			seen = new ArrayList<>(seen);
			seen.add(level);
		}
		return new FloorRun(floor + 1, Math.max(best, floor + 1), seen);
	}

	/** Back in the Overworld: the trip is over; the record stays. */
	public FloorRun end() {
		return new FloorRun(0, best, List.of());
	}
}
