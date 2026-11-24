package dev.noclip.backrooms.level0p1.robots;

import dev.noclip.backrooms.BackroomsMod;
import java.util.EnumMap;
import java.util.Map;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

/**
 * The robots' entity types, one per kind. The figures are box rigs modelled in Blender (art/robots,
 * tools/make_robots.py, assets/noclip_backrooms/monsters/robots.json); Zenith Station places them (ZenithRobots).
 */
public final class Robots {
	private static final Map<Robot.Kind, EntityType<Robot>> TYPES = new EnumMap<>(Robot.Kind.class);

	static {
		for (Robot.Kind kind : Robot.Kind.values()) {
			ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, BackroomsMod.id("robot_" + kind.id));
			TYPES.put(kind, Registry.register(BuiltInRegistries.ENTITY_TYPE, key,
					EntityType.Builder.of(Robot::new, MobCategory.MISC).sized(kind.width, kind.height).eyeHeight(kind.height * 0.85F)
							.clientTrackingRange(10).build(key)));
		}
	}

	private Robots() {
	}

	public static void register() {
		for (Map.Entry<Robot.Kind, EntityType<Robot>> entry : TYPES.entrySet()) {
			FabricDefaultAttributeRegistry.register(entry.getValue(), Robot.createAttributes(entry.getKey()));
		}
	}

	public static EntityType<Robot> type(Robot.Kind kind) {
		return TYPES.get(kind);
	}
}
