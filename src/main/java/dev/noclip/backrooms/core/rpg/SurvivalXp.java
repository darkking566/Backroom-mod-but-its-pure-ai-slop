package dev.noclip.backrooms.core.rpg;

import com.mojang.serialization.Codec;
import dev.noclip.backrooms.BackroomsMod;
import dev.noclip.backrooms.core.economy.Economy;
import dev.noclip.backrooms.core.floors.Floors;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Experience for surviving rather than killing (docs/企劃書/1-遊戲玩法/README.md & 2-玩家能力值/README.md):
 * new floor discovery, shaking off hunters, reading new field notes/manuals, and buyer trade deliveries.
 */
public final class SurvivalXp {
	static final int NEW_LEVEL = 40;
	static final int SHOOK_OFF = 20;
	static final int NOTE_READ = 15;
	/** Hunted for at least this many seconds, then left alone for this many, counts as getting away. */
	static final int HUNTED_FOR = 5, CALM_FOR = 10;
	static final double HUNTERS_WITHIN = 32.0;

	private static final class Holders {
		static final AttachmentType<List<String>> READ_NOTES = AttachmentRegistry.create(
				BackroomsMod.id("read_notes"),
				builder -> builder.initializer(List::of).persistent(Codec.STRING.listOf()).copyOnDeath());
	}

	/** Per player: seconds hunted in the current chase, seconds since anything was hunting them. */
	private static final Map<UUID, int[]> CHASES = new HashMap<>();

	private SurvivalXp() {
	}

	public static void register() {
		Floors.onArrive((player, level, run, newLevel) -> {
			if (newLevel) PlayerRpg.grantXp(player, NEW_LEVEL);
		});
		UseItemCallback.EVENT.register((player, level, hand) -> {
			if (!level.isClientSide() && player instanceof ServerPlayer sp) {
				ItemStack stack = player.getItemInHand(hand);
				if (stack.is(Items.WRITTEN_BOOK)) {
					WrittenBookContent content = stack.get(DataComponents.WRITTEN_BOOK_CONTENT);
					if (content != null) {
						String noteId = "Manila Mary Foundation".equals(content.author())
								? "note:manila_guide"
								: "book:" + content.author() + ":" + content.title().raw();
						recordNoteRead(sp, noteId);
					}
				} else if (stack.is(Economy.MEG_SQUAD_REPORT)) {
					recordNoteRead(sp, "item:meg_squad_report");
				}
			}
			return InteractionResult.PASS;
		});
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (!level.isClientSide() && player instanceof ServerPlayer sp) {
				BlockState state = level.getBlockState(hit.getBlockPos());
				String path = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
				if ("manila_note".equals(path)) {
					recordNoteRead(sp, "note:manila_guide");
				} else if ("field_note".equals(path)) {
					recordNoteRead(sp, "note:field_note:" + state.getValues());
				}
			}
			return InteractionResult.PASS;
		});
		ServerTickEvents.END_SERVER_TICK.register(SurvivalXp::tick);
	}

	/** Grants one-time XP the first time {@code player} reads a unique note/guide/dossier. */
	public static boolean recordNoteRead(ServerPlayer player, String noteId) {
		if (noteId == null || noteId.isEmpty()) return false;
		List<String> seen = player.getAttachedOrElse(Holders.READ_NOTES, List.of());
		if (seen.contains(noteId)) return false;
		List<String> next = new ArrayList<>(seen);
		next.add(noteId);
		player.setAttached(Holders.READ_NOTES, List.copyOf(next));
		PlayerRpg.grantXp(player, NOTE_READ);
		player.sendOverlayMessage(Component.translatable("message.noclip_backrooms.rpg.note_xp", NOTE_READ)
				.withStyle(ChatFormatting.AQUA));
		return true;
	}

	/** Grants modest XP when delivering Backrooms goods/evidence to the institute buyer. */
	public static void onBuyerSale(ServerPlayer player, long grossValue) {
		if (grossValue < 20L) return;
		int xp = Math.clamp((int) Math.max(1L, grossValue / 40L), 1, 25);
		PlayerRpg.grantXp(player, xp);
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % 20 != 0) return;
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (!player.isAlive() || player.isCreative() || player.isSpectator() || !PlayerRpg.inBackrooms(player)) {
				CHASES.remove(player.getUUID());
				continue;
			}
			boolean hunted = !player.level().getEntitiesOfClass(Monster.class, player.getBoundingBox().inflate(HUNTERS_WITHIN),
					monster -> monster.getTarget() == player).isEmpty();
			int[] chase = CHASES.computeIfAbsent(player.getUUID(), id -> new int[2]);
			if (hunted) {
				chase[0]++;
				chase[1] = 0;
			} else if (chase[0] >= HUNTED_FOR && ++chase[1] >= CALM_FOR) {
				chase[0] = 0;
				chase[1] = 0;
				PlayerRpg.grantXp(player, SHOOK_OFF);
				player.sendOverlayMessage(Component.translatable("message.noclip_backrooms.rpg.shook_off").withStyle(ChatFormatting.GRAY));
			} else if (chase[0] < HUNTED_FOR) {
				chase[0] = 0;
			}
		}
	}
}
