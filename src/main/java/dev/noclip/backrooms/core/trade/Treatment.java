package dev.noclip.backrooms.core.trade;

import dev.noclip.backrooms.core.Home;
import dev.noclip.backrooms.core.economy.Wallet;
import dev.noclip.backrooms.core.people.Person;
import dev.noclip.backrooms.core.quota.Quota;
import dev.noclip.backrooms.core.rpg.PlayerRpg;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;

/**
 * The institute's medic treats whoever pays (docs/project/1-gameplay/README.md, "醫療隔離翼"): full health, a clear head
 * and none of the Backrooms' afflictions. It does nothing for the Wretch rejection's clock; only the quota does.
 */
public final class Treatment {
	public static final long PRICE = 40;
	static final double REACH = 8.0;

	private Treatment() {
	}

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registry, environment) -> dispatcher.register(
				Commands.literal("meg").then(Commands.literal("treat").executes(ctx -> treat(ctx.getSource().getPlayerOrException()) ? 1 : 0))));
	}

	/** What the medic says when spoken to, with the treatment as a button. */
	static void offer(ServerPlayer player) {
		player.sendSystemMessage(Component.translatable("message.noclip_backrooms.treat.offer").withStyle(ChatFormatting.GRAY).append(" ")
				.append(Component.translatable("message.noclip_backrooms.treat.button", PRICE).withStyle(style -> style.withColor(ChatFormatting.GREEN)
						.withClickEvent(new ClickEvent.RunCommand("/meg treat"))
						.withHoverEvent(new HoverEvent.ShowText(Component.literal("/meg treat"))))));
	}

	public static boolean treat(ServerPlayer player) {
		boolean near = !player.level().getEntitiesOfClass(Person.class, player.getBoundingBox().inflate(REACH),
				p -> p.entityTags().contains(ShopRole.MEDIC.tag())).isEmpty();
		String refusal = !near ? "message.noclip_backrooms.treat.no_medic"
				: player.level().dimension() == Home.FRONTROOMS && Quota.clearance(player.level().getServer()) < ShopRole.MEDIC.clearance()
						? "shop.noclip_backrooms.wing_locked"
						: Quota.locked(player.level().getServer()) ? "shop.noclip_backrooms.locked"
						: Wallet.of(player) < PRICE ? "shop.noclip_backrooms.no_credits" : null;
		if (refusal != null) {
			player.sendSystemMessage(Component.translatable(refusal).withStyle(ChatFormatting.RED));
			return false;
		}
		Wallet.take(player, PRICE);
		player.setHealth(player.getMaxHealth());
		PlayerRpg.restoreSanity(player, 10_000.0F);
		for (MobEffectInstance effect : java.util.List.copyOf(player.getActiveEffects())) {
			if (effect.getEffect().value().getCategory() == MobEffectCategory.HARMFUL) player.removeEffect(effect.getEffect());
		}
		player.getFoodData().eat(20, 1.0F);
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BREWING_STAND_BREW, SoundSource.PLAYERS, 0.7F, 1.2F);
		player.sendSystemMessage(Component.translatable("message.noclip_backrooms.treat.done", PRICE).withStyle(ChatFormatting.GREEN));
		return true;
	}
}
