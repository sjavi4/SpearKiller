package me.autobot.client.mixin;

import me.autobot.client.DelayTask;
import me.autobot.client.SpearKillerClient;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.component.KineticWeapon;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

@Mixin(MultiPlayerGameMode.class)
public class MixinMultiPlayerGameMode_SpearUseItem {
	@Shadow
	@Final
	private ClientPacketListener connection;

	@Inject(method = "useItem", at = @At(value = "RETURN", ordinal = 1))
	private void onUseSpearItem(Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
		if (cir.getReturnValue() != InteractionResult.CONSUME)
			return;

		KineticWeapon kineticWeapon = player.getItemInHand(hand).get(DataComponents.KINETIC_WEAPON);
		if (kineticWeapon == null)
			return;

		if (player.getVehicle() != null)
			return;

		DelayTask.TASKS.add(new DelayTask(6, () -> {
			SpearKillerClient.SUPPRESS = true;
		}));
		DelayTask.TASKS.add(new DelayTask(8, () -> {
			SpearKillerClient.SUPPRESS = false;
		}));


		DelayTask.TASKS.add(new DelayTask(7, () -> {
			if (player.getItemInHand(hand).get(DataComponents.KINETIC_WEAPON) == null)
				return;

			if (player.getVehicle() != null) {
				player.sendOverlayMessage(Component.literal("Cannot inside Vehicle"));
				return;
			}

			//System.out.println("------------------- ");

			final Vec3 oldPos = player.position();

			//System.out.println("OriginalPos: " + oldPos);

			Vec3 viewVec = player.getViewVector(0);

			if (Math.abs(viewVec.y()) == 1) {
				player.sendOverlayMessage(Component.literal("Incorrect View Direction"));
				return;
			}
			//System.out.println("View Vec: " + viewVec);


			Vec3 planeViewVecBack = viewVec.multiply(1, 0 ,1).normalize();
			if (planeViewVecBack == Vec3.ZERO) {
				player.sendOverlayMessage(Component.literal("Incorrect View Direction"));
				return;
			}

			Vec3 planeViewVec = Vec3.ZERO;
			for (int i = 0; i <= 45; i += 5) {
				double radians = Math.toRadians(i);
				double cos = Math.cos(radians);
				double sin = Math.sin(radians);

				Vec3 tempViewVec1, tempViewVec2, tempFurthest;

				double rx1 = planeViewVecBack.x * cos - planeViewVecBack.z * sin;
				double rz1 = planeViewVecBack.x * sin + planeViewVecBack.z * cos;
				tempViewVec1 = getFurthestBackwardVector(player, new Vec3( rx1, 0, rz1));

				double rx2 = planeViewVecBack.x * cos - planeViewVecBack.z * (-sin);
				double rz2 = planeViewVecBack.x * (-sin) + planeViewVecBack.z * cos;
				tempViewVec2 = getFurthestBackwardVector(player, new Vec3( rx2, 0, rz2));

				tempFurthest = tempViewVec2.lengthSqr() > tempViewVec1.lengthSqr() ? tempViewVec2 : tempViewVec1;
				if (tempFurthest.lengthSqr() > planeViewVec.lengthSqr())
					planeViewVec = tempFurthest;

				if (planeViewVec == Vec3.ZERO)
					continue;

				double lengthSq = planeViewVec.lengthSqr();
				if (lengthSq < 16 || lengthSq > 98.01)
					continue;
				break;
			}

			if (planeViewVec == Vec3.ZERO) {
				player.sendOverlayMessage(Component.literal("Insufficient Backward Spaces"));
				return;
			}

			Vec3 backDir = planeViewVec;
			Vec3 targetPos = oldPos.add(backDir);
			/*
			System.out.println("View Plane Vec: " + planeViewVec);

			Vec3 backDir = planeViewVec;
			//Vec3 backDir = planeViewVec.scale(-9.5);

			double distanceTo = targetPos.distanceTo(oldPos);

			List<VoxelShape> blockCollisions = new ArrayList<>();
			level.getBlockCollisions(player, box.expandTowards(backDir)).forEach(blockCollisions::add);

			for (var b : blockCollisions) {
				BlockPos blockPos = BlockPos.containing(b.bounds().minX, b.bounds().minY, b.bounds().minZ);
				System.out.println("CollisionBlocks: " + level.getBlockState(blockPos));
			}

			if (!blockCollisions.isEmpty())
				return;

			System.out.println("Distance: " + distanceTo);
			if (distanceTo < 4 || distanceTo > 9.9)
				return;


			System.out.println("Execute");
			System.out.println("TargetPos: " + targetPos);
			System.out.println("Difference: " + targetPos.subtract(oldPos));
			 */

			player.sendOverlayMessage(Component.literal("Spear Activated"));

			ServerboundMovePlayerPacket posPacket = new ServerboundMovePlayerPacket.Pos(targetPos.x, targetPos.y, targetPos.z, true, false);
			this.connection.send(posPacket);


			ServerboundMovePlayerPacket oldPacket = new ServerboundMovePlayerPacket.Pos(oldPos.x, oldPos.y, oldPos.z, true, false);
			this.connection.send(oldPacket);
		}));
	}



	@Unique
	private static Vec3 getFurthestBackwardVector(Player player, Vec3 backDir) {
		Vec3 oldPos = player.position();
		Level level = player.level();
		AABB box = player.getBoundingBox();

		backDir = backDir.scale(-9.5);
		Vec3 targetPos = oldPos.add(backDir);

		List<VoxelShape> blockCollisions = new ArrayList<>();
		level.getBlockCollisions(player, box.expandTowards(backDir)).forEach(blockCollisions::add);

		if (!blockCollisions.isEmpty())
			return Vec3.ZERO;

		return targetPos.subtract(oldPos);
	}
}