package dev.noclip.backrooms.core.trade;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;

/**
 * Who stands behind a counter: each role is a person of one look (core.people) with a tag, and its goods come from
 * {@code data/noclip_backrooms/shops/<id>.json}. The master has no goods; he explains the guild.
 */
public enum ShopRole {
	BUYER("buyer", "trader"),
	SUPPLIER("supplier", "medic"),
	QUARTERMASTER("quartermaster", "meg_patrol"),
	MASTER("master", "colonist");

	private final String id, look;

	ShopRole(String id, String look) {
		this.id = id;
		this.look = look;
	}

	public String id() {
		return id;
	}

	/** The look of core.people this role wears. */
	public String look() {
		return look;
	}

	/** The entity tag marking someone as this role. */
	public String tag() {
		return "noclip_backrooms_guild_" + id;
	}

	public Component title() {
		return Component.translatable("guild.noclip_backrooms.role." + id);
	}

	public static ShopRole byId(String id) {
		for (ShopRole role : values()) if (role.id.equals(id)) return role;
		return null;
	}

	/** The role an entity has, or null. */
	public static ShopRole of(Entity entity) {
		for (ShopRole role : values()) if (entity.entityTags().contains(role.tag())) return role;
		return null;
	}
}
