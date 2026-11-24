package dev.noclip.backrooms.core.people;

import dev.noclip.backrooms.BackroomsMod;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

/**
 * The people's looks, one entity type each. The figure itself is a box rig modelled in Blender
 * (art/people, tools/make_people.py, assets/noclip_backrooms/monsters/people.json); a look only changes the clothes.
 */
public final class People {
	public static final List<String> LOOKS = List.of("meg_patrol", "colonist", "trader", "medic", "tom", "aiko", "speednoclipper", "shadow_worker");
	private static final Map<String, EntityType<Person>> TYPES = new LinkedHashMap<>();

	static {
		for (String look : LOOKS) {
			ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, BackroomsMod.id("person_" + look));
			TYPES.put(look, Registry.register(BuiltInRegistries.ENTITY_TYPE, key,
					EntityType.Builder.of(Person::new, MobCategory.MISC).sized(0.6F, 2.0F).eyeHeight(1.75F).clientTrackingRange(10).build(key)));
		}
	}

	private People() {
	}

	public static void register() {
		for (EntityType<Person> type : TYPES.values()) FabricDefaultAttributeRegistry.register(type, Person.createAttributes());
	}

	public static EntityType<Person> type(String look) {
		EntityType<Person> type = TYPES.get(look);
		if (type == null) throw new IllegalArgumentException("no such look " + look);
		return type;
	}
}
