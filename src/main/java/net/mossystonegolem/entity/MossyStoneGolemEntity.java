package net.mossystonegolem.entity;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.control.BodyControl;
import net.minecraft.entity.ai.goal.*;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.GolemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.*;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.EnumSet;
import java.util.List;

public class MossyStoneGolemEntity extends GolemEntity implements GeoEntity {
    private static final TrackedData<Boolean> ATTACKING = DataTracker.registerData(MossyStoneGolemEntity.class, TrackedDataHandlerRegistry.BOOLEAN);

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    public final VerletChain chain = new VerletChain();

    public MossyStoneGolemEntity(EntityType<? extends GolemEntity> entityType, World world) {
        super(entityType, world);
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return MobEntity.createMobAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 300.0)
                .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 1.0)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.15)
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 24.0)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 32.0)
                .add(EntityAttributes.GENERIC_STEP_HEIGHT, 1.0);
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        super.initDataTracker(builder);
        builder.add(ATTACKING, false);
    }

    public boolean isAttacking() {
        return this.dataTracker.get(ATTACKING);
    }

    public void setAttacking(boolean attacking) {
        this.dataTracker.set(ATTACKING, attacking);
    }

    @Override
    public EntityDimensions getBaseDimensions(EntityPose pose) {
        return EntityDimensions.changing(1.95f, 2.2f).withEyeHeight(1.8f);
    }

    @Override
    protected BodyControl createBodyControl() {
        return new SluggishBodyControl(this);
    }

    /**
     * Rest position of the {@code back_mount} bone, in model space and in blocks.
     * The mount hangs off the rotated body bone (pivot [0, 16, 0], 8 degree pitch), which places it
     * at [0, 26.77, 9.59] model units - i.e. 1.673 blocks up and 0.599 blocks behind the golem's origin.
     */
    private static final double MOUNT_HEIGHT = 1.673;
    private static final double MOUNT_BACK = 0.599;

    /** World space position of the chain's anchor point on the golem's back. */
    public Vec3d getChainAnchor() {
        Vec3d offset = VerletChain.modelToWorldOffset(new Vec3d(0.0, MOUNT_HEIGHT, MOUNT_BACK), this.bodyYaw);
        return new Vec3d(this.getX() + offset.x, this.getY() + offset.y, this.getZ() + offset.z);
    }

    @Override
    public void tick() {
        super.tick();

        // Verlet physics simulation for the dragged chain and bell
        this.chain.tick(this.getWorld(), this, this.getChainAnchor());
    }

    @Override
    protected void initGoals() {
        this.goalSelector.add(1, new SwimGoal(this));
        this.goalSelector.add(2, new GolemSlamAttackGoal(this));
        this.goalSelector.add(3, new WanderAroundFarGoal(this, 0.85));
        this.goalSelector.add(4, new LookAtEntityGoal(this, PlayerEntity.class, 10.0f));
        this.goalSelector.add(5, new LookAroundGoal(this));

        this.targetSelector.add(1, new RevengeGoal(this));
        this.targetSelector.add(2, new ActiveTargetGoal<>(this, HostileEntity.class, true));
    }

    public void performGroundSlam() {
        World world = this.getWorld();
        double forwardDist = 2.2;
        double rad = Math.toRadians(this.getYaw());
        double impactX = this.getX() - Math.sin(rad) * forwardDist;
        double impactY = this.getY();
        double impactZ = this.getZ() + Math.cos(rad) * forwardDist;

        // Sounds
        world.playSound(null, impactX, impactY, impactZ, SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.HOSTILE, 1.4f, 0.55f);
        world.playSound(null, impactX, impactY, impactZ, SoundEvents.BLOCK_STONE_BREAK, SoundCategory.HOSTILE, 2.5f, 0.5f);
        world.playSound(null, impactX, impactY, impactZ, SoundEvents.BLOCK_ANVIL_LAND, SoundCategory.HOSTILE, 1.0f, 0.6f);

        // Particles: block.mossy_stone_bricks.break shockwave ring
        if (world instanceof ServerWorld serverWorld) {
            BlockState mossyBricks = Blocks.MOSSY_STONE_BRICKS.getDefaultState();
            for (int i = 0; i < 36; i++) {
                double angle = (i / 36.0) * Math.PI * 2.0;
                double r = 1.0 + world.random.nextDouble() * 2.5;
                double px = impactX + Math.cos(angle) * r;
                double pz = impactZ + Math.sin(angle) * r;
                serverWorld.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, mossyBricks), px, impactY + 0.1, pz, 4, 0.2, 0.3, 0.2, 0.15);
                serverWorld.spawnParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, px, impactY + 0.1, pz, 1, 0.1, 0.1, 0.1, 0.05);
            }
        }

        // AoE Damage & Knockback
        Box aoeBox = new Box(impactX - 3.5, impactY - 1.0, impactZ - 3.5, impactX + 3.5, impactY + 2.5, impactZ + 3.5);
        List<LivingEntity> targets = world.getEntitiesByClass(LivingEntity.class, aoeBox, e -> e != this && !(e instanceof MossyStoneGolemEntity));
        for (LivingEntity target : targets) {
            target.damage(this.getDamageSources().mobAttack(this), 24.0f);
            double dx = target.getX() - impactX;
            double dz = target.getZ() - impactZ;
            double dist = Math.max(0.1, Math.sqrt(dx * dx + dz * dz));
            target.addVelocity((dx / dist) * 1.6, 0.65, (dz / dist) * 1.6);
            target.velocityModified = true;
        }
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.ENTITY_IRON_GOLEM_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.ENTITY_IRON_GOLEM_DEATH;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        this.playSound(SoundEvents.ENTITY_IRON_GOLEM_STEP, 1.2f, 0.65f);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "movementController", 4, this::movementPredicate));
        controllers.add(new AnimationController<>(this, "attackController", 2, this::attackPredicate));
    }

    private PlayState movementPredicate(AnimationState<MossyStoneGolemEntity> event) {
        if (event.isMoving()) {
            return event.setAndContinue(RawAnimation.begin().thenLoop("animation.mossy_stone_golem.walk"));
        }
        return event.setAndContinue(RawAnimation.begin().thenLoop("animation.mossy_stone_golem.idle"));
    }

    private PlayState attackPredicate(AnimationState<MossyStoneGolemEntity> event) {
        if (this.isAttacking()) {
            return event.setAndContinue(RawAnimation.begin().thenPlay("animation.mossy_stone_golem.attack"));
        }
        event.getController().forceAnimationReset();
        return PlayState.STOP;
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }

    private static class SluggishBodyControl extends BodyControl {
        private final MobEntity mob;

        public SluggishBodyControl(MobEntity mob) {
            super(mob);
            this.mob = mob;
        }

        @Override
        public void tick() {
            // Smoothly interpolate bodyYaw towards headYaw at max ~2.2 degrees per tick
            float maxTurnSpeed = 2.2f;
            float diff = MathHelper.wrapDegrees(this.mob.headYaw - this.mob.bodyYaw);
            this.mob.bodyYaw += MathHelper.clamp(diff, -maxTurnSpeed, maxTurnSpeed);
            this.mob.setYaw(this.mob.bodyYaw);
        }
    }

    private static class GolemSlamAttackGoal extends Goal {
        private final MossyStoneGolemEntity golem;
        private int attackTicks = 0;
        private int cooldown = 0;

        public GolemSlamAttackGoal(MossyStoneGolemEntity golem) {
            this.golem = golem;
            this.setControls(EnumSet.of(Control.MOVE, Control.LOOK));
        }

        @Override
        public boolean canStart() {
            if (this.cooldown > 0) {
                this.cooldown--;
                return false;
            }
            LivingEntity target = this.golem.getTarget();
            if (target == null || !target.isAlive()) return false;
            return this.golem.squaredDistanceTo(target) <= 20.0;
        }

        @Override
        public void start() {
            this.attackTicks = 28;
            this.golem.setAttacking(true);
            this.golem.getNavigation().stop();
        }

        @Override
        public void tick() {
            LivingEntity target = this.golem.getTarget();
            if (target != null) {
                this.golem.getLookControl().lookAt(target, 15.0f, 15.0f);
            }
            this.attackTicks--;
            if (this.attackTicks == 14) {
                this.golem.performGroundSlam();
            }
        }

        @Override
        public boolean shouldContinue() {
            return this.attackTicks > 0;
        }

        @Override
        public void stop() {
            this.golem.setAttacking(false);
            this.cooldown = 40;
        }
    }
}
