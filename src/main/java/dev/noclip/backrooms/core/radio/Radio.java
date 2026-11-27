package dev.noclip.backrooms.core.radio;

import com.mojang.serialization.Codec;
import dev.noclip.backrooms.BackroomsMod;
import dev.noclip.backrooms.core.Home;
import dev.noclip.backrooms.core.economy.Economy;
import dev.noclip.backrooms.core.floors.Floors;
import dev.noclip.backrooms.core.quota.Quota;
import dev.noclip.backrooms.core.quota.QuotaData;
import dev.noclip.backrooms.core.quota.QuotaMath;
import dev.noclip.backrooms.core.rpg.PlayerRpg;
import dev.noclip.backrooms.core.rpg.PlayerStats;
import dev.noclip.backrooms.monsters.Monsters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;

/**
 * The M.E.G. radio (docs/企劃書/1-遊戲玩法/README.md, "M.E.G. 無線電" and "失真無線電"). Carried into the Backrooms,
 * it picks up broken broadcasts every few minutes: what is left of the missing squad's traffic, what this cycle's quota
 * wants, how many days the team has left. Deep down, out of one's mind, or with a skin stealer close by, some of what it
 * says is false: a teammate's voice calling them somewhere that is not there.
 */
public final class Radio {
	/** Whether M.E.G. has issued this player a radio (the first time they got back). */
	public static final AttachmentType<Boolean> ISSUED = AttachmentRegistry.create(BackroomsMod.id("radio_issued"),
			builder -> builder.persistent(Codec.BOOL).copyOnDeath());
	static final int SQUAD_LINES = 12, FAKE_LINES = 6;
	/** Between broadcasts, in ticks: three to six minutes. */
	static final int MIN_GAP = 3600, MAX_GAP = 7200;
	/** How deep, how far gone, or how near a skin stealer the radio starts to lie. */
	static final int LYING_FLOOR = 4;
	static final float LYING_SANITY = 0.3F;
	static final double STEALER_RANGE = 32;
	private static final Map<UUID, Integer> NEXT = new HashMap<>();

	private Radio() {
	}

	public static void register() {
		ServerLifecycleEvents.SERVER_STARTED.register(server -> NEXT.clear());
		ServerTickEvents.END_SERVER_TICK.register(Radio::tick);
		CommandRegistrationCallback.EVENT.register((dispatcher, registry, environment) -> dispatcher.register(
				Commands.literal("backrooms").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
						.then(Commands.literal("radio")
								.executes(ctx -> broadcast(ctx.getSource().getPlayerOrException(), false) ? 1 : 0)
								.then(Commands.literal("fake").executes(ctx -> broadcast(ctx.getSource().getPlayerOrException(), true) ? 1 : 0)))));
	}

	/** M.E.G. issues a radio the first time someone gets back to the institute. */
	public static void issue(ServerPlayer player) {
		if (Boolean.TRUE.equals(player.getAttached(ISSUED))) return;
		player.setAttached(ISSUED, true);
		ItemStack radio = new ItemStack(Economy.MEG_RADIO);
		if (!player.getInventory().add(radio)) player.drop(radio, false);
		player.sendSystemMessage(Component.translatable("message.noclip_backrooms.radio.issued").withStyle(ChatFormatting.DARK_AQUA));
	}

	public static boolean carries(ServerPlayer player) {
		var inventory = player.getInventory();
		for (int i = 0; i < inventory.getContainerSize(); i++) if (inventory.getItem(i).is(Economy.MEG_RADIO)) return true;
		return false;
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % 20 != 0) return;
		int now = server.getTickCount();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (!Home.inBackrooms(player.level().dimension()) || !carries(player)) {
				NEXT.remove(player.getUUID());
				continue;
			}
			Integer next = NEXT.get(player.getUUID());
			if (next == null) {
				NEXT.put(player.getUUID(), now + gap(player.getRandom()));
				continue;
			}
			if (now < next) continue;
			NEXT.put(player.getUUID(), now + gap(player.getRandom()));
			broadcast(player, lying(player) && player.getRandom().nextFloat() < 0.4F);
		}
	}

	private static int gap(RandomSource random) {
		return MIN_GAP + random.nextInt(MAX_GAP - MIN_GAP + 1);
	}

	/** Whether the radio may lie to this player now: deep, out of their mind, or a skin stealer listening in. */
	public static boolean lying(ServerPlayer player) {
		if (Floors.of(player).floor() >= LYING_FLOOR) return true;
		PlayerStats stats = PlayerRpg.get(player);
		if (stats.maxSanity() > 0 && stats.sanity() / stats.maxSanity() < LYING_SANITY) return true;
		return !player.level().getEntities(Monsters.SKIN_STEALER, player.getBoundingBox().inflate(STEALER_RANGE), e -> e.isAlive()).isEmpty();
	}

	/** One broadcast, true or false. Returns whether anything was said. */
	public static boolean broadcast(ServerPlayer player, boolean fake) {
		RandomSource random = player.getRandom();
		MutableComponent line;
		if (fake) {
			List<ServerPlayer> others = new ArrayList<>(player.level().getServer().getPlayerList().getPlayers());
			others.remove(player);
			Component who = others.isEmpty() ? Component.translatable("radio.noclip_backrooms.someone")
					: others.get(random.nextInt(others.size())).getDisplayName();
			line = Component.translatable("radio.noclip_backrooms.fake." + random.nextInt(FAKE_LINES), who);
		} else {
			line = truth(player, random);
		}
		player.sendSystemMessage(Component.translatable("radio.noclip_backrooms.prefix").withStyle(ChatFormatting.DARK_AQUA)
				.append(" ").append(line.withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC)));
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.NOTE_BLOCK_BIT.value(), SoundSource.PLAYERS, 0.5F, 0.5F);
		return true;
	}

	/** What M.E.G. actually says: the squad's last traffic, this cycle's wants, the days left. */
	private static MutableComponent truth(ServerPlayer player, RandomSource random) {
		QuotaData d = Quota.data(player.level().getServer());
		int roll = random.nextInt(10);
		if (d.active() && roll < 3 && !d.items().isEmpty()) {
			List<String> wanted = new ArrayList<>(d.items().keySet());
			String id = wanted.get(random.nextInt(wanted.size()));
			var item = BuiltInRegistries.ITEM.getValue(Identifier.parse(id));
			return Component.translatable("radio.noclip_backrooms.wants", new ItemStack(item).getHoverName(), d.items().get(id));
		}
		if (d.active() && roll < 5) {
			return d.debt() > 0 ? Component.translatable("radio.noclip_backrooms.debt", d.debt())
					: Component.translatable("radio.noclip_backrooms.days", QuotaMath.daysLeft(d.homeTicks()), d.delivered(), d.required());
		}
		return Component.translatable("radio.noclip_backrooms.squad." + random.nextInt(SQUAD_LINES));
	}
}
