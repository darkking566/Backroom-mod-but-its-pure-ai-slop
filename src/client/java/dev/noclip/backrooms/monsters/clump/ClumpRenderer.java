package dev.noclip.backrooms.monsters.clump;

import dev.noclip.backrooms.BackroomsMod;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.AnimationState;

/** Renders the 3D 8-armed Clump. */
public final class ClumpRenderer extends MobRenderer<Clump, ClumpRenderer.State, ClumpModel> {
	private static final Identifier TEXTURE = BackroomsMod.id("textures/entity/clump.png");

	public ClumpRenderer(EntityRendererProvider.Context context) {
		super(context, new ClumpModel(context.bakeLayer(ClumpModel.LAYER)), 0.65F);
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(Clump mob, State state, float partialTick) {
		super.extractRenderState(mob, state, partialTick);
		state.charging = mob.isCharging();
		state.grab.copyFrom(mob.grabAnimation);
	}

	@Override
	public Identifier getTextureLocation(State state) {
		return TEXTURE;
	}

	public static final class State extends LivingEntityRenderState {
		boolean charging;
		final AnimationState grab = new AnimationState();
	}
}
