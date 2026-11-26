package dev.noclip.backrooms.core.trip;

import dev.noclip.backrooms.BackroomsMod;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;

/**
 * The mission report shown on getting back to the institute: how far the trip went, what came back and what M.E.G.
 * would pay for it, where the quota stands, and anything else M.E.G. has to say (work done while the player was away).
 */
public record MissionReportPayload(int floor, List<String> levels, List<ItemStack> haul, List<Long> values, long total,
		boolean active, int day, int daysLeft, long delivered, long required, long debt, int clearance, List<Component> notes)
		implements CustomPacketPayload {
	public static final Type<MissionReportPayload> TYPE = new Type<>(BackroomsMod.id("mission_report"));
	public static final StreamCodec<RegistryFriendlyByteBuf, MissionReportPayload> STREAM_CODEC =
			StreamCodec.ofMember(MissionReportPayload::write, MissionReportPayload::read);

	private void write(RegistryFriendlyByteBuf buf) {
		buf.writeVarInt(floor);
		buf.writeVarInt(levels.size());
		for (String level : levels) buf.writeUtf(level);
		buf.writeVarInt(haul.size());
		for (int i = 0; i < haul.size(); i++) {
			ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, haul.get(i));
			buf.writeVarLong(values.get(i));
		}
		buf.writeVarLong(total);
		buf.writeBoolean(active);
		buf.writeVarInt(day);
		buf.writeVarInt(daysLeft);
		buf.writeVarLong(delivered);
		buf.writeVarLong(required);
		buf.writeVarLong(debt);
		buf.writeVarInt(clearance);
		buf.writeVarInt(notes.size());
		for (Component note : notes) ComponentSerialization.STREAM_CODEC.encode(buf, note);
	}

	private static MissionReportPayload read(RegistryFriendlyByteBuf buf) {
		int floor = buf.readVarInt();
		List<String> levels = new ArrayList<>();
		for (int i = buf.readVarInt(); i > 0; i--) levels.add(buf.readUtf());
		List<ItemStack> haul = new ArrayList<>();
		List<Long> values = new ArrayList<>();
		for (int i = buf.readVarInt(); i > 0; i--) {
			haul.add(ItemStack.OPTIONAL_STREAM_CODEC.decode(buf));
			values.add(buf.readVarLong());
		}
		long total = buf.readVarLong();
		boolean active = buf.readBoolean();
		int day = buf.readVarInt(), daysLeft = buf.readVarInt();
		long delivered = buf.readVarLong(), required = buf.readVarLong(), debt = buf.readVarLong();
		int clearance = buf.readVarInt();
		List<Component> notes = new ArrayList<>();
		for (int i = buf.readVarInt(); i > 0; i--) notes.add(ComponentSerialization.STREAM_CODEC.decode(buf));
		return new MissionReportPayload(floor, levels, haul, values, total, active, day, daysLeft, delivered, required, debt, clearance, notes);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
