package com.lucciano.ironssablecompat.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.lucciano.ironssablecompat.helpers.TransformHelper;
import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.util.Utils;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.entity.LivingEntity;

@Mixin(value = Utils.class, remap = false)
public class UtilsMixin {

    @WrapMethod(method = "handleSpellTeleport")
    private static boolean fixTeleportDestination(AbstractSpell spell, Entity entity, Vec3 destination, Operation<Boolean> original) {
        Vec3 globalDest = TransformHelper.AccountForSublevels(entity.level(), destination);
        return original.call(spell, entity, globalDest);
    }

    @Unique
    private static Vec3 irons_sable_compat$projectLocationToStartSpace(Level level, Vec3 start, Vec3 hitLocation) {
        SubLevelAccess startSubLevel = SableCompanion.INSTANCE.getContaining(level, start);
        SubLevelAccess hitSubLevel = SableCompanion.INSTANCE.getContaining(level, hitLocation);

        if (startSubLevel == hitSubLevel) {
            return hitLocation;
        }

        // First project the hit to real-world coordinates
        Vec3 realWorldHit = TransformHelper.AccountForSublevels(level, hitLocation);

        if (startSubLevel != null) {
            // Caster/start is in a sub-level, project realWorldHit into the caster's
            // sub-level space
            return startSubLevel.logicalPose().transformPositionInverse(realWorldHit);
        } else {
            // Caster/start is in the real world, use the real world coordinates
            return realWorldHit;
        }
    }

    @Inject(method = "raycastForBlock(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/level/ClipContext$Fluid;)Lnet/minecraft/world/phys/BlockHitResult;", remap = false, at = @At("RETURN"), cancellable = true)
    private static void projectRaycastForBlock(Level level, Vec3 start, Vec3 end, ClipContext.Fluid clipContext,
            CallbackInfoReturnable<BlockHitResult> cir) {
        BlockHitResult original = cir.getReturnValue();
        Vec3 hitLocation = original.getLocation();
        Vec3 adjusted = irons_sable_compat$projectLocationToStartSpace(level, start, hitLocation);

        if (adjusted != hitLocation) {
            cir.setReturnValue(new BlockHitResult(
                    adjusted,
                    original.getDirection(),
                    original.getBlockPos(), // Keep original sub-level block position intact
                    original.isInside()));
        }
    }

    @Inject(method = "getTargetBlock(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/level/ClipContext$Fluid;D)Lnet/minecraft/world/phys/BlockHitResult;", remap = false, at = @At("RETURN"), cancellable = true)
    private static void projectGetTargetBlock(Level level, LivingEntity entity, ClipContext.Fluid clipContext,
            double reach, CallbackInfoReturnable<BlockHitResult> cir) {
        BlockHitResult original = cir.getReturnValue();
        BlockPos blockPos = original.getBlockPos();
        Vec3 hitLocation = original.getLocation();

        // Check if blockPos lives in a sublevel
        Vec3 blockVec = Vec3.atCenterOf(blockPos);
        Vec3 projectedBlockVec = TransformHelper.AccountForSublevels(level, blockVec);

        if (projectedBlockVec.distanceToSqr(blockVec) > 0.01) {
            // BlockPos is in sublevel space — convert Vec3 to the SAME sublevel space
            // so Iron's code (solveTeleportDestination, TouchDig, etc.) gets consistent
            // inputs
            SubLevelAccess subLevel = SableCompanion.INSTANCE.getContaining(level, projectedBlockVec);
            if (subLevel != null) {
                Vec3 sublevelVec = subLevel.logicalPose().transformPositionInverse(hitLocation);
                cir.setReturnValue(new BlockHitResult(
                        sublevelVec,
                        original.getDirection(),
                        blockPos,
                        original.isInside()));
            }
        }
    }
}
