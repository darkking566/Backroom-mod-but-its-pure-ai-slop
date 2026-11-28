package dev.noclip.backrooms.core.story;

import dev.noclip.backrooms.BackroomsMod;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server to client: synchronized active exploration mission, active bounty, and compass hint. */
public record MissionSyncPayload(
		boolean briefed,
		int completedCount,
		boolean hasExploration,
		String expLevel,
		String expTitleKey,
		boolean expReady,
		long expReward,
		List<ObjLine> expObjectives,
		boolean hasBounty,
		String bountyId,
		String bountyLevel,
		String bountyTitleKey,
		boolean bountyReady,
		long bountyReward,
		int bountyProgress,
		int bountyGoal,
		boolean hasHint,
		int hintX,
		int hintZ,
		String hintLevel,
		String hintLabelKey) implements CustomPacketPayload {

	public static final Type<MissionSyncPayload> TYPE = new Type<>(BackroomsMod.id("mission_sync"));

	public static final MissionSyncPayload EMPTY = new MissionSyncPayload(
			false, 0,
			false, "", "", false, 0L, List.of(),
			false, "", "", "", false, 0L, 0, 0,
			false, 0, 0, "", "");

	public record ObjLine(String kind, String labelKey, int progress, int goal, boolean done) {
		public static void write(FriendlyByteBuf buf, ObjLine o) {
			buf.writeUtf(o.kind);
			buf.writeUtf(o.labelKey);
			buf.writeVarInt(o.progress);
			buf.writeVarInt(o.goal);
			buf.writeBoolean(o.done);
		}

		public static ObjLine read(FriendlyByteBuf buf) {
			return new ObjLine(buf.readUtf(), buf.readUtf(), buf.readVarInt(), buf.readVarInt(), buf.readBoolean());
		}
	}

	public static final StreamCodec<FriendlyByteBuf, MissionSyncPayload> STREAM_CODEC = StreamCodec.of(
			MissionSyncPayload::write, MissionSyncPayload::read);

	public static void write(FriendlyByteBuf buf, MissionSyncPayload p) {
		buf.writeBoolean(p.briefed);
		buf.writeVarInt(p.completedCount);
		buf.writeBoolean(p.hasExploration);
		buf.writeUtf(p.expLevel);
		buf.writeUtf(p.expTitleKey);
		buf.writeBoolean(p.expReady);
		buf.writeVarLong(p.expReward);
		buf.writeVarInt(p.expObjectives.size());
		for (ObjLine o : p.expObjectives) ObjLine.write(buf, o);
		buf.writeBoolean(p.hasBounty);
		buf.writeUtf(p.bountyId);
		buf.writeUtf(p.bountyLevel);
		buf.writeUtf(p.bountyTitleKey);
		buf.writeBoolean(p.bountyReady);
		buf.writeVarLong(p.bountyReward);
		buf.writeVarInt(p.bountyProgress);
		buf.writeVarInt(p.bountyGoal);
		buf.writeBoolean(p.hasHint);
		buf.writeVarInt(p.hintX);
		buf.writeVarInt(p.hintZ);
		buf.writeUtf(p.hintLevel);
		buf.writeUtf(p.hintLabelKey);
	}

	public static MissionSyncPayload read(FriendlyByteBuf buf) {
		boolean briefed = buf.readBoolean();
		int completedCount = buf.readVarInt();
		boolean hasExp = buf.readBoolean();
		String expLevel = buf.readUtf();
		String expTitleKey = buf.readUtf();
		boolean expReady = buf.readBoolean();
		long expReward = buf.readVarLong();
		int n = Math.clamp(buf.readVarInt(), 0, 16);
		List<ObjLine> objs = new ArrayList<>(n);
		for (int i = 0; i < n; i++) objs.add(ObjLine.read(buf));
		boolean hasBounty = buf.readBoolean();
		String bountyId = buf.readUtf();
		String bountyLevel = buf.readUtf();
		String bountyTitleKey = buf.readUtf();
		boolean bountyReady = buf.readBoolean();
		long bountyReward = buf.readVarLong();
		int bountyProg = buf.readVarInt();
		int bountyGoal = buf.readVarInt();
		boolean hasHint = buf.readBoolean();
		int hintX = buf.readVarInt();
		int hintZ = buf.readVarInt();
		String hintLevel = buf.readUtf();
		String hintLabelKey = buf.readUtf();
		return new MissionSyncPayload(briefed, completedCount,
				hasExp, expLevel, expTitleKey, expReady, expReward, objs,
				hasBounty, bountyId, bountyLevel, bountyTitleKey, bountyReady, bountyReward, bountyProg, bountyGoal,
				hasHint, hintX, hintZ, hintLevel, hintLabelKey);
	}

	@Override
	public Type<MissionSyncPayload> type() {
		return TYPE;
	}
}
