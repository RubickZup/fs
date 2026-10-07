package net.mossystonegolem.client.model;

import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.mossystonegolem.MossyStoneGolemMod;
import net.mossystonegolem.entity.MossyStoneGolemEntity;
import net.mossystonegolem.entity.VerletChain;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;

public class MossyStoneGolemModel extends GeoModel<MossyStoneGolemEntity> {
    private static final Identifier MODEL = Identifier.of(MossyStoneGolemMod.MOD_ID, "geo/mossy_stone_golem.geo.json");
    private static final Identifier TEXTURE = Identifier.of(MossyStoneGolemMod.MOD_ID, "textures/entity/mossy_stone_golem.png");
    private static final Identifier ANIMATION = Identifier.of(MossyStoneGolemMod.MOD_ID, "animations/mossy_stone_golem.animation.json");

    @Override
    public Identifier getModelResource(MossyStoneGolemEntity animatable) {
        return MODEL;
    }

    @Override
    public Identifier getTextureResource(MossyStoneGolemEntity animatable) {
        return TEXTURE;
    }

    @Override
    public Identifier getAnimationResource(MossyStoneGolemEntity animatable) {
        return ANIMATION;
    }

    @Override
    public void setCustomAnimations(MossyStoneGolemEntity entity, long instanceId, AnimationState<MossyStoneGolemEntity> animationState) {
        super.setCustomAnimations(entity, instanceId, animationState);

        VerletChain chain = entity.chain;
        float bodyYawRad = (float) Math.toRadians(entity.bodyYaw);
        double cosYaw = Math.cos(-bodyYawRad);
        double sinYaw = Math.sin(-bodyYawRad);

        // Position and orient chain links 1..7 and bell_bone dynamically based on Verlet physics
        for (int i = 1; i < VerletChain.NODE_COUNT; i++) {
            String boneName = (i == 8) ? "bell_bone" : ("chain_" + i);
            GeoBone bone = this.getAnimationProcessor().getBone(boneName);
            if (bone == null) continue;

            Vec3d curr = chain.getNodePos(i);
            Vec3d prevNode = chain.getNodePos(i - 1);

            // World displacement from entity center
            double worldRelX = curr.x - entity.getX();
            double worldRelY = curr.y - entity.getY();
            double worldRelZ = curr.z - entity.getZ();

            // Transform into entity-local space rotated by -bodyYaw
            double localX = worldRelX * cosYaw - worldRelZ * sinYaw;
            double localZ = worldRelX * sinYaw + worldRelZ * cosYaw;

            // Target in Blockbench coordinates (16 units/block, X axis inverted)
            float targetX = (float) (-localX * 16.0);
            float targetY = (float) (worldRelY * 16.0);
            float targetZ = (float) (localZ * 16.0);

            bone.setPosX(targetX - bone.getPivotX());
            bone.setPosY(targetY - bone.getPivotY());
            bone.setPosZ(targetZ - bone.getPivotZ());

            // Orient the bone to follow the link direction
            double dirX = curr.x - prevNode.x;
            double dirY = curr.y - prevNode.y;
            double dirZ = curr.z - prevNode.z;
            double hDist = Math.sqrt(dirX * dirX + dirZ * dirZ);

            if (hDist > 1.0E-4 || Math.abs(dirY) > 1.0E-4) {
                float pitch = (float) Math.atan2(dirY, hDist);
                double localDirX = dirX * cosYaw - dirZ * sinYaw;
                double localDirZ = dirX * sinYaw + dirZ * cosYaw;
                float yaw = (float) Math.atan2(localDirX, localDirZ);

                bone.setRotX(pitch);
                bone.setRotY(yaw);
            }
        }
    }
}
