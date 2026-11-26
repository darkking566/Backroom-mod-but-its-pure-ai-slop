package dev.noclip.backrooms.core.combat;

import dev.noclip.backrooms.core.trip.Extraction;
import dev.noclip.backrooms.weapons.BackroomsFirearmItem;
import dev.noclip.backrooms.weapons.BackroomsMagicItem;
import dev.noclip.backrooms.weapons.BackroomsMeleeItem;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * The horror balance of the weapons (docs/project/1-gameplay/README.md, "裝備與武器在恐怖玩法下的定位原則"), in one
 * place over Gemini's numbers: weapons are not for mowing down the ordinary creatures. Against them, the better the
 * weapon the less of its damage counts (tiers 0 and 1 keep 90%, tier 4 only 55%); the big guns and the end-game gear are for
 * bosses (tier 3 does 130%, tier 4 160% against them) and for holding out while a pickup comes (half again on top).
 * Creatures that are not the mod's own, and fists and vanilla weapons, are left as they are.
 */
public final class WeaponBalance {
	/** Damage kept against ordinary creatures, against bosses, by tier 1..4. */
	static final float[] ORDINARY = {0.9F, 0.8F, 0.65F, 0.55F}, BOSS = {0.8F, 1.0F, 1.3F, 1.6F};
	/** On top, while the attacker is waiting for a pickup: the heavy gear's moment. */
	static final float[] HOLDING_OUT = {1.1F, 1.15F, 1.5F, 1.5F};
	static final Set<String> BOSSES = Set.of("beast_level5");

	private WeaponBalance() {
	}

	/** The damage that counts, for a hit from {@code source} on {@code target}. */
	public static float adjust(LivingEntity target, DamageSource source, float amount) {
		if (!(source.getEntity() instanceof Player player) || target instanceof Player) return amount;
		var key = BuiltInRegistries.ENTITY_TYPE.getKey(target.getType());
		if (!key.getNamespace().equals("noclip_backrooms")) return amount;
		int tier = tier(player.getMainHandItem());
		if (tier < 0) return amount;
		int i = Math.clamp(tier, 1, 4) - 1;
		float factor = BOSSES.contains(key.getPath()) ? BOSS[i] : ORDINARY[i];
		if (player instanceof ServerPlayer serverPlayer
				&& (Extraction.running(serverPlayer) || dev.noclip.backrooms.core.ending.Endings.overriding(serverPlayer))) {
			factor *= HOLDING_OUT[i];
		}
		return amount * factor;
	}

	/** A Backrooms weapon's tier (0-4; tier 0 counts as tier 1 here), or -1 for anything else. */
	public static int tier(ItemStack stack) {
		Item item = stack.getItem();
		if (item instanceof BackroomsMeleeItem melee) return melee.tier();
		if (item instanceof BackroomsFirearmItem gun) return gun.tier();
		if (item instanceof BackroomsMagicItem magic) return magic.tier();
		return -1;
	}
}
