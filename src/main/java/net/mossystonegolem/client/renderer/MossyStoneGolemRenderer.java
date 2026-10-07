package net.mossystonegolem.client.renderer;

import net.minecraft.client.render.entity.EntityRendererFactory;
import net.mossystonegolem.client.model.MossyStoneGolemModel;
import net.mossystonegolem.entity.MossyStoneGolemEntity;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class MossyStoneGolemRenderer extends GeoEntityRenderer<MossyStoneGolemEntity> {
    public MossyStoneGolemRenderer(EntityRendererFactory.Context context) {
        super(context, new MossyStoneGolemModel());
        this.shadowRadius = 1.6f;
    }
}
