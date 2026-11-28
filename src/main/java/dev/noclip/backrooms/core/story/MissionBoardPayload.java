package dev.noclip.backrooms.core.story;

import dev.noclip.backrooms.BackroomsMod;
import dev.noclip.backrooms.core.story.MissionSyncPayload.ObjLine;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server to client: opens or refreshes the Commander's Exploration & Bounty Mission Board screen. */
public record MissionBoardPayload(
		boolean firstBriefing,
		int completedCount,
		List<String> unlockedLevels,
		MissionSyncPayload activeState,
		List<BoardEntry> explorationOffers,
		List<BountyEntry> bountyOffers) implements CustomPacketPayload {

	public static final Type<MissionBoardPayload> TYPE = new Type<>(BackroomsMod.id("mission_board"));

	public record BoardEntry(String level, long reward, List<ObjLine> objectives) {
		static void write(FriendlyByteBuf buf, BoardEntry e) {
			buf.writeUtf(e.level);
			buf.writeVarLong(e.reward);
			buf.writeVarInt(e.objectives.size());
			for (ObjLine o : e.objectives) ObjLine.write(buf, o);
		}

		static BoardEntry read(FriendlyByteBuf buf) {
			String lvl = buf.readUtf();
			long rew = buf.readVarLong();
			int n = Math.clamp(buf.readVarInt(), 0, 12);
			List<ObjLine> list = new ArrayList<>(n);
			for (int i = 0; i < n; i++) list.add(ObjLine.read(buf));
			return new BoardEntry(lvl, rew, list);
		}
	}

	public record BountyEntry(
			String id,
			String level,
			String titleKey,
			String descKey,
			long reward,
			int goal,
			boolean unlocked,
			boolean boss,
			int reqCompleted) {
		static void write(FriendlyByteBuf buf, BountyEntry b) {
			buf.writeUtf(b.id);
			buf.writeUtf(b.level);
			buf.writeUtf(b.titleKey);
			buf.writeUtf(b.descKey);
			buf.writeVarLong(b.reward);
			buf.writeVarInt(b.goal);
			buf.writeBoolean(b.unlocked);
			buf.writeBoolean(b.boss);
			buf.writeVarInt(b.reqCompleted);
		}

		static BountyEntry read(FriendlyByteBuf buf) {
			return new BountyEntry(buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readUtf(),
					buf.readVarLong(), buf.readVarInt(), buf.readBoolean(), buf.readBoolean(), buf.readVarInt());
		}
	}

	public static final StreamCodec<FriendlyByteBuf, MissionBoardPayload> STREAM_CODEC = StreamCodec.of(
			(buf, p) -> {
				buf.writeBoolean(p.firstBriefing);
				buf.writeVarInt(p.completedCount);
				buf.writeVarInt(p.unlockedLevels.size());
				for (String lvl : p.unlockedLevels) buf.writeUtf(lvl);
				MissionSyncPayload.write(buf, p.activeState);
				buf.writeVarInt(p.explorationOffers.size());
				for (BoardEntry e : p.explorationOffers) BoardEntry.write(buf, e);
				buf.writeVarInt(p.bountyOffers.size());
				for (BountyEntry b : p.bountyOffers) BountyEntry.write(buf, b);
			},
			buf -> {
				boolean first = buf.readBoolean();
				int completed = buf.readVarInt();
				int nLvl = Math.clamp(buf.readVarInt(), 0, 16);
				List<String> levels = new ArrayList<>(nLvl);
				for (int i = 0; i < nLvl; i++) levels.add(buf.readUtf());
				MissionSyncPayload sync = MissionSyncPayload.read(buf);
				int nExp = Math.clamp(buf.readVarInt(), 0, 16);
				List<BoardEntry> exps = new ArrayList<>(nExp);
				for (int i = 0; i < nExp; i++) exps.add(BoardEntry.read(buf));
				int nBnt = Math.clamp(buf.readVarInt(), 0, 32);
				List<BountyEntry> bnts = new ArrayList<>(nBnt);
				for (int i = 0; i < nBnt; i++) bnts.add(BountyEntry.read(buf));
				return new MissionBoardPayload(first, completed, levels, sync, exps, bnts);
			});

	@Override
	public Type<MissionBoardPayload> type() {
		return TYPE;
	}
}
