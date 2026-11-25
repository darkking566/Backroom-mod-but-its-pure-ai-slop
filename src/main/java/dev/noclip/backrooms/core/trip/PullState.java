package dev.noclip.backrooms.core.trip;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * How hard the Backrooms are pulling at a player in the Overworld (0 to Pull.FALL, where they fall in), and the game
 * time until which they are left alone after getting out.
 */
public record PullState(float pull, long graceUntil) {
	public static final PullState NONE = new PullState(0.0F, 0L);
	public static final Codec<PullState> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.FLOAT.optionalFieldOf("pull", 0.0F).forGetter(PullState::pull),
			Codec.LONG.optionalFieldOf("grace_until", 0L).forGetter(PullState::graceUntil)).apply(i, PullState::new));
	public static final StreamCodec<ByteBuf, PullState> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.FLOAT, PullState::pull,
			ByteBufCodecs.VAR_LONG, PullState::graceUntil,
			PullState::new);

	public PullState withPull(float value) {
		return new PullState(value, graceUntil);
	}
}
