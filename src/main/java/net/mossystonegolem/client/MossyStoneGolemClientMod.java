package net.mossystonegolem.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.mossystonegolem.MossyStoneGolemMod;
import net.mossystonegolem.client.renderer.MossyStoneGolemRenderer;

public class MossyStoneGolemClientMod implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        EntityRendererRegistry.register(MossyStoneGolemMod.MOSSY_STONE_GOLEM, MossyStoneGolemRenderer::new);
    }
}
