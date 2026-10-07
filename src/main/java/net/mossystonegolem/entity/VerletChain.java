package net.mossystonegolem.entity;

import net.minecraft.block.BlockState;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.World;

public class VerletChain {
    public static final int NODE_COUNT = 9; // Node 0 = mount, Nodes 1..7 = chain links, Node 8 = bell

    public final double[] currentX = new double[NODE_COUNT];
    public final double[] currentY = new double[NODE_COUNT];
    public final double[] currentZ = new double[NODE_COUNT];

    public final double[] prevX = new double[NODE_COUNT];
    public final double[] prevY = new double[NODE_COUNT];
    public final double[] prevZ = new double[NODE_COUNT];

    private boolean initialized = false;
    private int soundCooldown = 0;
    private int scrapeCooldown = 0;

    public void tick(World world, Vec3d anchorPos, float bodyYaw) {
        if (!initialized) {
            double yawRad = Math.toRadians(bodyYaw);
            double backDx = Math.sin(yawRad);
            double backDz = -Math.cos(yawRad);
            for (int i = 0; i < NODE_COUNT; i++) {
                double dist = i * 0.28;
                double drop = i * 0.20;
                currentX[i] = prevX[i] = anchorPos.x + backDx * dist;
                currentY[i] = prevY[i] = anchorPos.y - drop;
                currentZ[i] = prevZ[i] = anchorPos.z + backDz * dist;
            }
            initialized = true;
        }

        // Anchor is pinned to the back mount point
        currentX[0] = anchorPos.x;
        currentY[0] = anchorPos.y;
        currentZ[0] = anchorPos.z;

        // 1. Verlet step
        for (int i = 1; i < NODE_COUNT; i++) {
            double damping = (i == 8) ? 0.82 : 0.88; // Bell has more inertia
            double vx = (currentX[i] - prevX[i]) * damping;
            double vy = (currentY[i] - prevY[i]) * damping - 0.045; // Gravity
            double vz = (currentZ[i] - prevZ[i]) * damping;

            prevX[i] = currentX[i];
            prevY[i] = currentY[i];
            prevZ[i] = currentZ[i];

            currentX[i] += vx;
            currentY[i] += vy;
            currentZ[i] += vz;
        }

        // 2. Relaxation iterations for distance constraints
        for (int iter = 0; iter < 8; iter++) {
            currentX[0] = anchorPos.x;
            currentY[0] = anchorPos.y;
            currentZ[0] = anchorPos.z;

            for (int i = 0; i < NODE_COUNT - 1; i++) {
                double dx = currentX[i + 1] - currentX[i];
                double dy = currentY[i + 1] - currentY[i];
                double dz = currentZ[i + 1] - currentZ[i];
                double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
                if (dist > 1.0E-4) {
                    double targetL = (i == 7) ? 0.32 : 0.26;
                    double factor = (dist - targetL) / dist;
                    if (i == 0) {
                        currentX[1] -= dx * factor;
                        currentY[1] -= dy * factor;
                        currentZ[1] -= dz * factor;
                    } else {
                        currentX[i] += dx * factor * 0.5;
                        currentY[i] += dy * factor * 0.5;
                        currentZ[i] += dz * factor * 0.5;
                        currentX[i + 1] -= dx * factor * 0.5;
                        currentY[i + 1] -= dy * factor * 0.5;
                        currentZ[i + 1] -= dz * factor * 0.5;
                    }
                }
            }

            // Block & ground collision
            for (int i = 1; i < NODE_COUNT; i++) {
                double groundH = getGroundHeight(world, currentX[i], currentY[i], currentZ[i]);
                double radius = (i == 8) ? 0.35 : 0.08;
                if (currentY[i] < groundH + radius) {
                    currentY[i] = groundH + radius;
                    // Ground friction
                    currentX[i] = prevX[i] + (currentX[i] - prevX[i]) * 0.40;
                    currentZ[i] = prevZ[i] + (currentZ[i] - prevZ[i]) * 0.40;
                }
            }
        }

        // 3. Audio effects for bell on solid surfaces
        if (!world.isClient()) {
            double bellGround = getGroundHeight(world, currentX[8], currentY[8], currentZ[8]);
            boolean onGround = currentY[8] <= bellGround + 0.40;
            double hSpeed = Math.sqrt((currentX[8] - prevX[8]) * (currentX[8] - prevX[8]) + (currentZ[8] - prevZ[8]) * (currentZ[8] - prevZ[8]));
            double vSpeed = currentY[8] - prevY[8];

            if (onGround) {
                if (vSpeed < -0.10 && soundCooldown <= 0) {
                    // Heavy ground landing / bouncing sound
                    world.playSound(null, currentX[8], currentY[8], currentZ[8], SoundEvents.BLOCK_BELL_USE, SoundCategory.NEUTRAL, 1.4f, 0.65f);
                    world.playSound(null, currentX[8], currentY[8], currentZ[8], SoundEvents.BLOCK_ANVIL_LAND, SoundCategory.NEUTRAL, 0.7f, 0.5f);
                    soundCooldown = 14;
                } else if (hSpeed > 0.04 && scrapeCooldown <= 0) {
                    // Scraping along stone/ground
                    world.playSound(null, currentX[8], currentY[8], currentZ[8], SoundEvents.BLOCK_CHAIN_STEP, SoundCategory.NEUTRAL, 0.85f, 0.7f);
                    if (world.random.nextFloat() < 0.28f && soundCooldown <= 0) {
                        world.playSound(null, currentX[8], currentY[8], currentZ[8], SoundEvents.BLOCK_BELL_USE, SoundCategory.NEUTRAL, 0.55f, 0.68f);
                        soundCooldown = 22;
                    }
                    scrapeCooldown = 7;
                }
            }
        }

        if (soundCooldown > 0) soundCooldown--;
        if (scrapeCooldown > 0) scrapeCooldown--;
    }

    private double getGroundHeight(World world, double x, double y, double z) {
        BlockPos pos = BlockPos.ofFloored(x, y, z);
        BlockState state = world.getBlockState(pos);
        if (!state.getCollisionShape(world, pos).isEmpty()) {
            VoxelShape shape = state.getCollisionShape(world, pos);
            return pos.getY() + shape.getMax(Direction.Axis.Y);
        }

        BlockPos below = pos.down();
        BlockState belowState = world.getBlockState(below);
        if (!belowState.getCollisionShape(world, below).isEmpty()) {
            VoxelShape shape = belowState.getCollisionShape(world, below);
            return below.getY() + shape.getMax(Direction.Axis.Y);
        }

        return y - 10.0;
    }

    public Vec3d getNodePos(int index) {
        if (index < 0 || index >= NODE_COUNT) return Vec3d.ZERO;
        return new Vec3d(currentX[index], currentY[index], currentZ[index]);
    }
}
