package dev.noclip.backrooms.core.people;

import dev.noclip.backrooms.BackroomsMod;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;

/** Draws a person in the clothes of its look. */
public final class PersonRenderer extends MobRenderer<Person, PersonRenderer.State, PersonModel> {
	private final Identifier texture;

	public PersonRenderer(EntityRendererProvider.Context context, String look) {
		super(context, new PersonModel(context.bakeLayer(PersonModel.LAYER), look), 0.45F);
		texture = BackroomsMod.id("textures/entity/people/" + look + ".png");
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(Person person, State state, float partialTick) {
		super.extractRenderState(person, state, partialTick);
		state.swing = person.getAttackAnim(partialTick);
	}

	@Override
	public Identifier getTextureLocation(State state) {
		return texture;
	}

	public static final class State extends LivingEntityRenderState {
		/** How far through an arm swing, 0 when not swinging. */
		float swing;
	}
}
