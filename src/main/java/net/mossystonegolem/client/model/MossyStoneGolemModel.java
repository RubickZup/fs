package net.mossystonegolem.client.model;

import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.mossystonegolem.MossyStoneGolemMod;
import net.mossystonegolem.entity.MossyStoneGolemEntity;
import net.mossystonegolem.entity.VerletChain;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;

/**
 * GeckoLib model of the mossy stone golem.
 *
 * <p>Besides driving the chain bones from the Verlet simulation this class converts the simulated
 * chain from world space into the model space GeckoLib renders in (see
 * {@link VerletChain#worldToModelOffset}), interpolates it with the same partial tick and body yaw
 * the renderer itself uses, and orients every link along its segment.</p>
 *
 * <p>Bone transforms in GeckoLib are {@code translate(pos + pivot) * rotate * translate(-pivot)}, so
 * a bone is placed by giving it the offset from its rest pivot to the target point, and a bone's
 * local -Y axis (the long axis of a chain link) is aligned with the chain by rotating around X and
 * then around Y - the order GeckoLib applies them in.</p>
 */
public class MossyStoneGolemModel extends GeoModel<MossyStoneGolemEntity> {
    private static final Identifier MODEL = Identifier.of(MossyStoneGolemMod.MOD_ID, "geo/mossy_stone_golem.geo.json");
    private static final Identifier TEXTURE = Identifier.of(MossyStoneGolemMod.MOD_ID, "textures/entity/mossy_stone_golem.png");
    private static final Identifier ANIMATION = Identifier.of(MossyStoneGolemMod.MOD_ID, "animations/mossy_stone_golem.animation.json");

    /** Model units per block. */
    private static final double UNITS = 16.0;
    /** Distance between the bell bone's pivot and the ring the chain is tied to, in model units. */
    private static final double RING_OFFSET = 7.5;

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
        float tickDelta = animationState.getPartialTick();

        // The entity is drawn at its interpolated position and rotated by its interpolated body yaw,
        // exactly like GeoEntityRenderer does it, so use the same values here.
        Vec3d origin = entity.getLerpedPos(tickDelta);
        float bodyYaw = entity.prevBodyYaw + tickDelta * MathHelper.wrapDegrees(entity.bodyYaw - entity.prevBodyYaw);

        double[] nodeX = new double[VerletChain.NODE_COUNT];
        double[] nodeY = new double[VerletChain.NODE_COUNT];
        double[] nodeZ = new double[VerletChain.NODE_COUNT];
        for (int i = 0; i < VerletChain.NODE_COUNT; i++) {
            Vec3d world = chain.getLerpedNodePos(i, tickDelta).subtract(origin);
            Vec3d model = VerletChain.worldToModelOffset(world, bodyYaw);
            nodeX[i] = model.x * UNITS;
            nodeY[i] = model.y * UNITS;
            nodeZ[i] = model.z * UNITS;
        }

        // Chain links: keep the pivot on the simulated node and point the link down the chain, at the
        // node below it. Every link mesh hangs below its own pivot in the geometry, so this makes it
        // cover the whole segment and interlock with the link below it, exactly like the rest pose.
        for (int i = 1; i < VerletChain.NODE_COUNT - 1; i++) {
            GeoBone bone = this.getAnimationProcessor().getBone("chain_" + i);
            if (bone == null) {
                continue;
            }

            double dx = nodeX[i + 1] - nodeX[i];
            double dy = nodeY[i + 1] - nodeY[i];
            double dz = nodeZ[i + 1] - nodeZ[i];
            double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (length > 1.0E-4) {
                dx /= length;
                dy /= length;
                dz /= length;

                // Align the bone's local -Y axis (the long axis of a link) with the chain direction.
                double horizontal = Math.sqrt(dx * dx + dz * dz);
                double pitch = Math.atan2(horizontal, -dy);
                double yawRotation = (horizontal > 1.0E-4) ? Math.atan2(-dx, -dz) : 0.0;
                bone.setRotX((float) pitch);
                bone.setRotY((float) yawRotation);
                bone.setRotZ(0.0f);
            }

            placeBone(bone, nodeX[i], nodeY[i], nodeZ[i]);
        }

        // Bell: hinged on the last node, so the ring stays attached to the chain while the bell swings.
        GeoBone bell = this.getAnimationProcessor().getBone("bell_bone");
        if (bell != null) {
            Vec3d up = VerletChain.worldToModelOffset(chain.getBellUp(tickDelta), bodyYaw);
            double upX = up.x;
            double upY = up.y;
            double upZ = up.z;

            double horizontal = Math.sqrt(upX * upX + upZ * upZ);
            double pitch = Math.atan2(horizontal, upY);
            double yawRotation = (horizontal > 1.0E-4) ? Math.atan2(upX, upZ) : 0.0;
            bell.setRotX((float) pitch);
            bell.setRotY((float) yawRotation);
            bell.setRotZ(0.0f);

            // The bone rotates around its pivot, so move the pivot so that the ring lands on the node.
            double ringX = RING_OFFSET * Math.sin(pitch) * Math.sin(yawRotation);
            double ringY = RING_OFFSET * Math.cos(pitch);
            double ringZ = RING_OFFSET * Math.sin(pitch) * Math.cos(yawRotation);
            placeBone(bell, nodeX[VerletChain.NODE_COUNT - 1] - ringX, nodeY[VerletChain.NODE_COUNT - 1] - ringY, nodeZ[VerletChain.NODE_COUNT - 1] - ringZ);
        }
    }

    /** Moves a bone so that its pivot sits at the given point of the model space. */
    private static void placeBone(GeoBone bone, double x, double y, double z) {
        bone.setPosX((float) (x - bone.getPivotX()));
        bone.setPosY((float) (y - bone.getPivotY()));
        bone.setPosZ((float) (z - bone.getPivotZ()));
    }
}
