package dev.noclip.backrooms.core.guild;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.noclip.backrooms.BackroomsMod;
import dev.noclip.backrooms.core.LevelExits;
import dev.noclip.backrooms.core.economy.Wallet;
import dev.noclip.backrooms.core.floors.FloorRun;
import dev.noclip.backrooms.core.floors.Floors;
import dev.noclip.backrooms.core.people.Person;
import dev.noclip.backrooms.core.rpg.PlayerRpg;
import dev.noclip.backrooms.core.trade.ShopRole;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/**
 * M.E.G. contracts, handed out by the commander at the surface outpost (the guild): go back into the Backrooms of your
 * own accord, reach a level, and come back alive to be paid (docs/project/1-gameplay/README.md). Accepting one sends the player
 * straight into Level 0; reaching the level marks it done; walking out alive pays into the account; coming home without
 * it is a failed contract. One at a time; kept through death.
 */
public final class Contracts {
	public enum Contract {
		SURVEY("survey_level1", "1", 150),
		RECOVER("recover_level2", "2", 400),
		READINGS("readings_level3", "3", 800);

		public final String id, level;
		public final long reward;

		Contract(String id, String level, long reward) {
			this.id = id;
			this.level = level;
			this.reward = reward;
		}

		public static Contract byId(String id) {
			for (Contract c : values()) if (c.id.equals(id)) return c;
			return null;
		}

		Component title() {
			return Component.translatable("contract.noclip_backrooms." + id);
		}
	}

	public record Active(String id, boolean reached) {
		static final Codec<Active> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.STRING.fieldOf("id").forGetter(Active::id),
				Codec.BOOL.optionalFieldOf("reached", false).forGetter(Active::reached)).apply(i, Active::new));
	}

	public static final AttachmentType<Active> ACTIVE = AttachmentRegistry.create(BackroomsMod.id("contract"),
			builder -> builder.persistent(Active.CODEC).copyOnDeath());
	/** How near the commander the player must be to take a contract. */
	static final double NEAR_COMMANDER = 8.0;

	private Contracts() {
	}

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registry, environment) -> dispatcher.register(
				Commands.literal("meg").then(Commands.literal("contract")
						.then(Commands.literal("accept").then(Commands.argument("id", StringArgumentType.word())
								.executes(ctx -> accept(ctx.getSource().getPlayerOrException(), StringArgumentType.getString(ctx, "id")) ? 1 : 0)))
						.then(Commands.literal("abandon").executes(ctx -> {
							ServerPlayer player = ctx.getSource().getPlayerOrException();
							if (!player.hasAttached(ACTIVE)) return 0;
							player.removeAttached(ACTIVE);
							player.sendSystemMessage(Component.translatable("message.noclip_backrooms.contract.abandoned"));
							return 1;
						})))));
		Floors.onArrive((player, level, run, newLevel) -> {
			Active active = player.getAttached(ACTIVE);
			Contract contract = active == null ? null : Contract.byId(active.id());
			if (contract == null || active.reached() || !contract.level.equals(level)) return;
			player.setAttached(ACTIVE, new Active(active.id(), true));
			player.sendSystemMessage(Component.translatable("message.noclip_backrooms.contract.reached", contract.reward)
					.withStyle(ChatFormatting.GREEN));
		});
		Floors.onHome(Contracts::cameHome);
	}

	/** What the commander says when spoken to: who they are, and the contracts on offer (or the one in hand). */
	public static void talk(ServerPlayer player) {
		for (int i = 1; i <= 3; i++) player.sendSystemMessage(Component.translatable("guild.noclip_backrooms.master." + i));
		Active active = player.getAttached(ACTIVE);
		Contract current = active == null ? null : Contract.byId(active.id());
		if (current != null) {
			player.sendSystemMessage(Component.translatable("contract.noclip_backrooms.active", current.title(),
					Component.translatable(active.reached() ? "contract.noclip_backrooms.status.reached" : "contract.noclip_backrooms.status.going"))
					.append(" ").append(button("contract.noclip_backrooms.abandon", "/meg contract abandon", ChatFormatting.RED)));
			return;
		}
		player.sendSystemMessage(Component.translatable("contract.noclip_backrooms.header").withStyle(ChatFormatting.GOLD));
		for (Contract contract : Contract.values()) {
			player.sendSystemMessage(Component.translatable("contract.noclip_backrooms.line", contract.title(), contract.reward)
					.append(" ").append(button("contract.noclip_backrooms.accept", "/meg contract accept " + contract.id, ChatFormatting.GREEN)));
		}
	}

	private static MutableComponent button(String key, String command, ChatFormatting colour) {
		return Component.translatable(key).withStyle(style -> style.withColor(colour)
				.withClickEvent(new ClickEvent.RunCommand(command))
				.withHoverEvent(new HoverEvent.ShowText(Component.literal(command))));
	}

	static boolean accept(ServerPlayer player, String id) {
		Contract contract = Contract.byId(id);
		String refusal = contract == null ? "message.noclip_backrooms.contract.unknown"
				: player.hasAttached(ACTIVE) ? "message.noclip_backrooms.contract.already"
				: player.level().dimension() != Level.OVERWORLD ? "message.noclip_backrooms.contract.not_home"
				: !nearCommander(player) ? "message.noclip_backrooms.contract.no_commander" : null;
		if (refusal != null) {
			player.sendSystemMessage(Component.translatable(refusal));
			return false;
		}
		start(player, contract);
		return true;
	}

	/** Takes the contract and sends the player in (accept() checks first; tests call this directly). */
	public static void start(ServerPlayer player, Contract contract) {
		player.setAttached(ACTIVE, new Active(contract.id, false));
		player.sendSystemMessage(Component.translatable("message.noclip_backrooms.contract.accepted", contract.title())
				.withStyle(ChatFormatting.GRAY));
		LevelExits.fallInto(player, "0");
	}

	private static boolean nearCommander(ServerPlayer player) {
		return !player.level().getEntitiesOfClass(Person.class, player.getBoundingBox().inflate(NEAR_COMMANDER),
				person -> ShopRole.of(person) == ShopRole.MASTER).isEmpty();
	}

	private static void cameHome(ServerPlayer player, FloorRun run) {
		Active active = player.getAttached(ACTIVE);
		if (active == null) return;
		Contract contract = Contract.byId(active.id());
		player.removeAttached(ACTIVE);
		if (contract == null) return;
		if (active.reached()) {
			Wallet.add(player, contract.reward);
			PlayerRpg.grantXp(player, (int) (contract.reward / 5));
			player.sendSystemMessage(Component.translatable("message.noclip_backrooms.contract.paid", contract.title(), contract.reward)
					.withStyle(ChatFormatting.GREEN));
		} else {
			player.sendSystemMessage(Component.translatable("message.noclip_backrooms.contract.failed", contract.title())
					.withStyle(ChatFormatting.RED));
		}
	}
}
