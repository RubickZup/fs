package net.mossystonegolem.entity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import net.minecraft.entity.Entity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.World;

/**
 * Verlet (position based dynamics) chain that drags the mossy golem's bronze bell behind it.
 *
 * <p>Node 0 is pinned to the mount on the golem's back, nodes 1..7 are the chain links and
 * node 8 is the hinge of the bell (the ring the chain is tied to, <em>not</em> the pivot of the
 * model bones - see {@link #getBellUp(float)} and the renderer).</p>
 *
 * <p>The simulation is fixed time step, sub-stepped and iterated, which keeps it stable no matter
 * how fast the golem moves. Nodes are resolved against real block collision shapes, so the bell
 * rolls and scrapes over the floor, gets caught on ledges and never sinks into the terrain.
 * The chain links are light and the bell is heavy (inverse masses), so the bell drags the chain
 * behind the golem instead of being yanked around by it.</p>
 */
public class VerletChain {
    /** Node 0 is the back mount, 1..7 are the chain links, 8 is the bell hinge. */
    public static final int NODE_COUNT = 9;

    /** Number of physics sub-steps per game tick. */
    private static final int SUBSTEPS = 3;
    /** Number of constraint relaxation iterations per sub-step. */
    private static final int ITERATIONS = 10;

    /** Acceleration in blocks per tick squared. */
    private static final double GRAVITY = 0.055;
    /** Velocity keeper per tick (air drag). */
    private static final double AIR_DRAG = 0.994;
    private static final double BELL_AIR_DRAG = 0.986;
    /** Safety clamp, in blocks per tick. */
    private static final double MAX_VELOCITY = 0.65;

    /** Collision radius of a single chain link. */
    private static final double LINK_RADIUS = 0.075;
    /** Collision half extent of the bell. */
    private static final double BELL_HALF_EXTENT = 0.4;
    /** How far below the hinge the centre of the bell's collision box sits. */
    private static final double BELL_CENTRE_DROP = 0.26;

    /** Velocity kept per sub-step while scraping over the ground. */
    private static final double LINK_FRICTION = 0.95;
    private static final double BELL_FRICTION = 0.9;
    /** Bounce factor for hard landings. */
    private static final double RESTITUTION = 0.16;

    /** 0 = pinned anchor, 1..7 = chain links, 8 = heavy bell. */
    private static final double[] INVERSE_MASS = { 0.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 0.22 };
    /**
     * Rest distance between node i and node i + 1. The chain is only slightly longer than the
     * mount-to-resting-bell distance, so it hangs almost straight and the bell ends up on the floor
     * instead of piling up on a heap of loose links.
     */
    private static final double[] SEGMENT_LENGTH = { 0.14, 0.14, 0.14, 0.14, 0.14, 0.14, 0.14, 0.14 };
    private static final double TOTAL_LENGTH;

    static {
        double total = 0.0;
        for (double length : SEGMENT_LENGTH) {
            total += length;
        }
        TOTAL_LENGTH = total;
    }

    // Current and previous positions (Verlet integration).
    private final double[] currentX = new double[NODE_COUNT];
    private final double[] currentY = new double[NODE_COUNT];
    private final double[] currentZ = new double[NODE_COUNT];
    private final double[] prevX = new double[NODE_COUNT];
    private final double[] prevY = new double[NODE_COUNT];
    private final double[] prevZ = new double[NODE_COUNT];
    // Snapshot taken at the start of every tick, used to interpolate the chain while rendering.
    private final double[] lerpX = new double[NODE_COUNT];
    private final double[] lerpY = new double[NODE_COUNT];
    private final double[] lerpZ = new double[NODE_COUNT];

    private final boolean[] contactUp = new boolean[NODE_COUNT];
    private final boolean[] contactSide = new boolean[NODE_COUNT];
    private final List<Box> colliders = new ArrayList<>();

    private boolean initialized;
    private double tension = 1.0;
    private boolean bellGrounded;
    private Vec3d lastAnchor = Vec3d.ZERO;

    private int ringCooldown;
    private int scrapeCooldown;

    /**
     * Advances the simulation by one tick.
     *
     * @param world  the world the golem lives in (used for block collision lookups and sounds)
     * @param entity the golem itself
     * @param anchor world position of the mount on the golem's back
     */
    public void tick(World world, Entity entity, Vec3d anchor) {
        if (!this.initialized || this.needsReset(anchor)) {
            this.reset(anchor);
        }

        this.captureRenderSnapshot();

        // Velocities before the solver runs, used to scale the impact sounds.
        double incomingX = this.currentX[8] - this.prevX[8];
        double incomingY = this.currentY[8] - this.prevY[8];
        double incomingZ = this.currentZ[8] - this.prevZ[8];
        boolean wasGrounded = this.bellGrounded;

        double step = 1.0 / SUBSTEPS;
        for (int sub = 0; sub < SUBSTEPS; sub++) {
            this.integrate(step);
            this.collectColliders(world, entity);

            for (int iteration = 0; iteration < ITERATIONS; iteration++) {
                // Collisions first, then the chain lengths: that way the links are never left
                // visibly stretched, which is far more noticeable than a millimetre of penetration.
                this.solveCollisions();
                this.solveDistanceConstraints(anchor);
            }

            this.applyContacts();
        }

        // The mount is exactly where the model expects it, always.
        this.currentX[0] = anchor.x;
        this.currentY[0] = anchor.y;
        this.currentZ[0] = anchor.z;

        this.updateBellState(anchor);
        this.playBellSounds(world, incomingX, incomingY, incomingZ, wasGrounded);

        this.lastAnchor = anchor;
    }

    /** Sets the chain hanging straight down from the anchor, e.g. after spawning or teleporting. */
    private void reset(Vec3d anchor) {
        for (int i = 0; i < NODE_COUNT; i++) {
            double drop = 0.0;
            for (int segment = 0; segment < i; segment++) {
                drop += SEGMENT_LENGTH[segment];
            }
            this.currentX[i] = this.prevX[i] = this.lerpX[i] = anchor.x;
            this.currentY[i] = this.prevY[i] = this.lerpY[i] = anchor.y - drop;
            this.currentZ[i] = this.prevZ[i] = this.lerpZ[i] = anchor.z;
        }

        this.initialized = true;
        this.tension = 0.0;
        this.bellGrounded = false;
        this.lastAnchor = anchor;
    }

    private boolean needsReset(Vec3d anchor) {
        Vec3d moved = anchor.subtract(this.lastAnchor);
        if (moved.lengthSquared() > 16.0) {
            return true; // teleported / respawned
        }
        for (int i = 0; i < NODE_COUNT; i++) {
            if (!Double.isFinite(this.currentX[i]) || !Double.isFinite(this.currentY[i]) || !Double.isFinite(this.currentZ[i])) {
                return true;
            }
        }
        return false;
    }

    private void captureRenderSnapshot() {
        System.arraycopy(this.currentX, 0, this.lerpX, 0, NODE_COUNT);
        System.arraycopy(this.currentY, 0, this.lerpY, 0, NODE_COUNT);
        System.arraycopy(this.currentZ, 0, this.lerpZ, 0, NODE_COUNT);
    }

    private void integrate(double step) {
        for (int i = 1; i < NODE_COUNT; i++) {
            double drag = (i == 8) ? BELL_AIR_DRAG : AIR_DRAG;
            double vx = (this.currentX[i] - this.prevX[i]) * drag;
            double vy = (this.currentY[i] - this.prevY[i]) * drag - GRAVITY * step;
            double vz = (this.currentZ[i] - this.prevZ[i]) * drag;

            double speed = Math.sqrt(vx * vx + vy * vy + vz * vz);
            double maxStep = MAX_VELOCITY * step;
            if (speed > maxStep) {
                double scale = maxStep / speed;
                vx *= scale;
                vy *= scale;
                vz *= scale;
            }

            this.prevX[i] = this.currentX[i];
            this.prevY[i] = this.currentY[i];
            this.prevZ[i] = this.currentZ[i];

            this.currentX[i] += vx;
            this.currentY[i] += vy;
            this.currentZ[i] += vz;
        }
    }

    /** Collects the block collision boxes around the whole chain once per sub-step. */
    private void collectColliders(World world, Entity entity) {
        Arrays.fill(this.contactUp, false);
        Arrays.fill(this.contactSide, false);
        this.colliders.clear();

        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, minZ = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
        for (int i = 1; i < NODE_COUNT; i++) {
            minX = Math.min(minX, this.currentX[i]);
            minY = Math.min(minY, this.currentY[i]);
            minZ = Math.min(minZ, this.currentZ[i]);
            maxX = Math.max(maxX, this.currentX[i]);
            maxY = Math.max(maxY, this.currentY[i]);
            maxZ = Math.max(maxZ, this.currentZ[i]);
        }

        double reach = BELL_HALF_EXTENT + 0.6;
        Box area = new Box(minX - reach, minY - reach - 0.4, minZ - reach, maxX + reach, maxY + reach, maxZ + reach);
        for (VoxelShape shape : world.getBlockCollisions(entity, area)) {
            if (!shape.isEmpty()) {
                this.colliders.add(shape.getBoundingBox());
            }
        }
    }

    private void solveDistanceConstraints(Vec3d anchor) {
        this.currentX[0] = anchor.x;
        this.currentY[0] = anchor.y;
        this.currentZ[0] = anchor.z;

        for (int i = 0; i < NODE_COUNT - 1; i++) {
            double dx = this.currentX[i + 1] - this.currentX[i];
            double dy = this.currentY[i + 1] - this.currentY[i];
            double dz = this.currentZ[i + 1] - this.currentZ[i];
            double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (distance < 1.0E-5) {
                continue;
            }

            double weightA = INVERSE_MASS[i];
            double weightB = INVERSE_MASS[i + 1];
            double weightSum = weightA + weightB;
            if (weightSum <= 0.0) {
                continue;
            }

            // The constraint is one sided, exactly like a real chain: links are pulled back to their
            // rest distance when stretched, but they never push each other away. Pushing apart - even
            // softly - lets the solver balance the bell's weight on a bunched up chain, which leaves
            // the bell hovering in mid air. Slack therefore piles up at the bell's hinge instead,
            // right where the chain meets the bell's ring.
            double difference = distance - SEGMENT_LENGTH[i];
            if (difference <= 0.0) {
                continue;
            }

            double correction = difference / distance;
            double shareA = weightA / weightSum;
            double shareB = weightB / weightSum;

            this.currentX[i] += dx * correction * shareA;
            this.currentY[i] += dy * correction * shareA;
            this.currentZ[i] += dz * correction * shareA;
            this.currentX[i + 1] -= dx * correction * shareB;
            this.currentY[i + 1] -= dy * correction * shareB;
            this.currentZ[i + 1] -= dz * correction * shareB;
        }
    }

    private void solveCollisions() {
        for (int i = 1; i < NODE_COUNT; i++) {
            double halfExtent = (i == 8) ? BELL_HALF_EXTENT : LINK_RADIUS;
            double centreX = this.currentX[i];
            double centreY = this.currentY[i] - ((i == 8) ? BELL_CENTRE_DROP : 0.0);
            double centreZ = this.currentZ[i];

            for (int c = 0; c < this.colliders.size(); c++) {
                Box shape = this.colliders.get(c);

                double overlapX = Math.min(centreX + halfExtent, shape.maxX) - Math.max(centreX - halfExtent, shape.minX);
                if (overlapX <= 0.0) {
                    continue;
                }
                double overlapY = Math.min(centreY + halfExtent, shape.maxY) - Math.max(centreY - halfExtent, shape.minY);
                if (overlapY <= 0.0) {
                    continue;
                }
                double overlapZ = Math.min(centreZ + halfExtent, shape.maxZ) - Math.max(centreZ - halfExtent, shape.minZ);
                if (overlapZ <= 0.0) {
                    continue;
                }

                double depthBelowTop = shape.maxY - (centreY - halfExtent);
                double depthAboveBottom = (centreY + halfExtent) - shape.minY;
                boolean aboveCentre = centreY >= (shape.minY + shape.maxY) * 0.5;

                if (aboveCentre && (depthBelowTop <= overlapX || depthBelowTop <= overlapZ)) {
                    // Landing on (or already standing on) a surface: lift the node onto it.
                    centreY += depthBelowTop;
                    this.contactUp[i] = true;
                } else if (overlapY < overlapX && overlapY < overlapZ) {
                    if (aboveCentre) {
                        centreY += depthBelowTop;
                    } else {
                        centreY -= depthAboveBottom;
                    }
                    this.contactUp[i] = true;
                } else if (overlapX <= overlapZ) {
                    centreX += (centreX >= (shape.minX + shape.maxX) * 0.5) ? overlapX : -overlapX;
                    this.contactSide[i] = true;
                } else {
                    centreZ += (centreZ >= (shape.minZ + shape.maxZ) * 0.5) ? overlapZ : -overlapZ;
                    this.contactSide[i] = true;
                }
            }

            this.currentX[i] = centreX;
            this.currentY[i] = centreY + ((i == 8) ? BELL_CENTRE_DROP : 0.0);
            this.currentZ[i] = centreZ;
        }
    }

    /** Turns contacts into friction and a small bounce once per sub-step. */
    private void applyContacts() {
        for (int i = 1; i < NODE_COUNT; i++) {
            if (!this.contactUp[i] && !this.contactSide[i]) {
                continue;
            }

            double vx = this.currentX[i] - this.prevX[i];
            double vy = this.currentY[i] - this.prevY[i];
            double vz = this.currentZ[i] - this.prevZ[i];

            if (this.contactUp[i]) {
                double friction = (i == 8) ? BELL_FRICTION : LINK_FRICTION;
                if (vy < 0.0) {
                    vy = (vy < -0.16) ? -vy * RESTITUTION : 0.0;
                }
                vx *= friction;
                vz *= friction;
            } else {
                vx *= 0.92;
                vz *= 0.92;
                if (vy > 0.0) {
                    vy *= 0.92;
                }
            }

            this.prevX[i] = this.currentX[i] - vx;
            this.prevY[i] = this.currentY[i] - vy;
            this.prevZ[i] = this.currentZ[i] - vz;
        }
    }

    private void updateBellState(Vec3d anchor) {
        this.bellGrounded = this.contactUp[8];

        double straight = Math.sqrt(
                (this.currentX[8] - anchor.x) * (this.currentX[8] - anchor.x)
                        + (this.currentY[8] - anchor.y) * (this.currentY[8] - anchor.y)
                        + (this.currentZ[8] - anchor.z) * (this.currentZ[8] - anchor.z));
        // 0 when the chain is piled up on the ground, 1 when it is pulled straight.
        this.tension = MathHelper.clamp((straight / TOTAL_LENGTH - 0.6) / 0.35, 0.0, 1.0);
    }

    private void playBellSounds(World world, double incomingX, double incomingY, double incomingZ, boolean wasGrounded) {
        if (this.ringCooldown > 0) {
            this.ringCooldown--;
        }
        if (this.scrapeCooldown > 0) {
            this.scrapeCooldown--;
        }
        if (world.isClient()) {
            return;
        }

        double x = this.currentX[8];
        double y = this.currentY[8] - BELL_CENTRE_DROP;
        double z = this.currentZ[8];

        double horizontal = Math.sqrt(incomingX * incomingX + incomingZ * incomingZ);
        double impact = Math.max(Math.abs(incomingY), horizontal * 0.65);

        if (this.bellGrounded && !wasGrounded && impact > 0.05 && this.ringCooldown <= 0) {
            float volume = (float) MathHelper.clamp(0.55 + impact * 2.4, 0.55, 1.6);
            float pitch = (float) MathHelper.clamp(0.62 + impact * 0.5, 0.6, 1.05) * (0.95f + world.random.nextFloat() * 0.1f);
            world.playSound(null, x, y, z, SoundEvents.BLOCK_BELL_USE, SoundCategory.NEUTRAL, volume, pitch);
            if (impact > 0.2) {
                world.playSound(null, x, y, z, SoundEvents.BLOCK_BELL_RESONATE, SoundCategory.NEUTRAL, volume * 0.9f, pitch * 0.9f);
                world.playSound(null, x, y, z, SoundEvents.BLOCK_ANVIL_LAND, SoundCategory.NEUTRAL, volume * 0.55f, 0.55f);
            }
            this.ringCooldown = 12;
            this.scrapeCooldown = 8;
            return;
        }

        if (!this.bellGrounded || this.scrapeCooldown > 0) {
            return;
        }

        double vx = this.currentX[8] - this.prevX[8];
        double vz = this.currentZ[8] - this.prevZ[8];
        double speed = Math.sqrt(vx * vx + vz * vz);
        if (speed <= 0.025) {
            return;
        }

        float volume = (float) MathHelper.clamp(0.35 + speed * 1.6, 0.35, 1.1);
        world.playSound(null, x, y, z, SoundEvents.BLOCK_CHAIN_STEP, SoundCategory.NEUTRAL, volume, 0.85f + world.random.nextFloat() * 0.25f);
        if (speed > 0.09 && world.random.nextFloat() < 0.5f) {
            world.playSound(null, x, y, z, SoundEvents.BLOCK_STONE_STEP, SoundCategory.NEUTRAL, volume * 0.7f, 0.7f + world.random.nextFloat() * 0.2f);
        }
        if (world.random.nextFloat() < 0.02f) {
            world.playSound(null, x, y, z, SoundEvents.BLOCK_BELL_USE, SoundCategory.NEUTRAL, 0.4f, 0.75f);
            this.ringCooldown = 30;
        }
        this.scrapeCooldown = (int) MathHelper.clamp(16.0 - speed * 45.0, 7.0, 16.0);
    }

    /**
     * Converts an offset from the golem's model space (feet at y = 0, y up, -Z towards the golem's
     * face, 16 model units per block) into a world space offset.
     *
     * <p>GeckoLib renders the entity rotated by {@code 180 - bodyYaw} around the Y axis, which turns
     * a model offset {@code (mx, my, mz)} into the world offset</p>
     *
     * <pre>{@code
     *   worldX =  sin(yaw) * mz - cos(yaw) * mx
     *   worldY =  my
     *   worldZ = -sin(yaw) * mx - cos(yaw) * mz
     * }</pre>
     */
    public static Vec3d modelToWorldOffset(Vec3d modelOffset, float bodyYaw) {
        double yaw = Math.toRadians(bodyYaw);
        double cos = Math.cos(yaw);
        double sin = Math.sin(yaw);
        return new Vec3d(
                sin * modelOffset.z - cos * modelOffset.x,
                modelOffset.y,
                -sin * modelOffset.x - cos * modelOffset.z);
    }

    /** Inverse of {@link #modelToWorldOffset(Vec3d, float)}: a world offset in the golem's model space. */
    public static Vec3d worldToModelOffset(Vec3d worldOffset, float bodyYaw) {
        double yaw = Math.toRadians(bodyYaw);
        double cos = Math.cos(yaw);
        double sin = Math.sin(yaw);
        return new Vec3d(
                -cos * worldOffset.x - sin * worldOffset.z,
                worldOffset.y,
                sin * worldOffset.x - cos * worldOffset.z);
    }

    /** World position of a node, as simulated this tick. */
    public Vec3d getNodePos(int index) {
        if (index < 0 || index >= NODE_COUNT) {
            return Vec3d.ZERO;
        }
        return new Vec3d(this.currentX[index], this.currentY[index], this.currentZ[index]);
    }

    /** World position of a node, interpolated for smooth rendering. */
    public Vec3d getLerpedNodePos(int index, float tickDelta) {
        if (index < 0 || index >= NODE_COUNT) {
            return Vec3d.ZERO;
        }
        double delta = MathHelper.clamp(tickDelta, 0.0f, 1.0f);
        return new Vec3d(
                MathHelper.lerp(delta, this.lerpX[index], this.currentX[index]),
                MathHelper.lerp(delta, this.lerpY[index], this.currentY[index]),
                MathHelper.lerp(delta, this.lerpZ[index], this.currentZ[index]));
    }

    /**
     * Direction the bell should be aligned with: the bell hangs from its ring, so it follows the last
     * chain segment while the chain is pulled straight and stays upright while the chain is slack.
     */
    public Vec3d getBellUp(float tickDelta) {
        Vec3d link = this.getLerpedNodePos(7, tickDelta);
        Vec3d bell = this.getLerpedNodePos(8, tickDelta);
        Vec3d direction = bell.subtract(link);
        double length = direction.length();

        double pull = this.tension;
        Vec3d up = new Vec3d(0.0, 1.0, 0.0).multiply(1.0 - pull);
        if (length > 1.0E-5) {
            up = up.add(direction.multiply(-pull / length));
        }

        return up.lengthSquared() < 1.0E-8 ? new Vec3d(0.0, 1.0, 0.0) : up.normalize();
    }

    /** True while the bell is resting on or scraping over a surface. */
    public boolean isBellGrounded() {
        return this.bellGrounded;
    }

    /** 0 when the chain is piled up at the mount, 1 when it is pulled straight. */
    public double getTension() {
        return this.tension;
    }
}
