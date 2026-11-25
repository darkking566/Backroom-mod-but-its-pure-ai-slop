package dev.noclip.backrooms.mixin.client;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Exposes container panel bounds so the RPG stats side panel anchors cleanly beside the inventory. */
@Mixin(AbstractContainerScreen.class)
public interface AbstractContainerScreenAccess {
	@Accessor("leftPos")
	int noclip_backrooms$leftPos();

	@Accessor("topPos")
	int noclip_backrooms$topPos();

	@Accessor("imageWidth")
	int noclip_backrooms$imageWidth();

	@Accessor("imageHeight")
	int noclip_backrooms$imageHeight();
}
