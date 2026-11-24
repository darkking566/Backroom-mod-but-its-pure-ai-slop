package dev.noclip.backrooms.core.people;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;

/**
 * One of the people of the Backrooms (a settlement's resident, a patrol, a shadow at work). They carry no AI of their
 * own: the level that places them walks them about (see level1.Walkers). Each look is its own entity type, so the
 * client knows which clothes to draw (client side: PersonRenderer).
 */
public class Person extends PathfinderMob {
	public Person(EntityType<? extends Person> type, Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 20).add(Attributes.MOVEMENT_SPEED, 0.25);
	}
}
