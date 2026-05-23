package me.autobot.client.mixin;

import me.autobot.client.DelayTask;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.component.KineticWeapon;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

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

		final Vec3 oldPos = player.position();
		DelayTask.TASKS.add(new DelayTask(7, () -> {
			if (player.getVehicle() != null)
				return;

			if (player.getKnownMovement().lengthSqr() > 0.00615)
				return;

			Vec3 targetPos = Vec3.ZERO;
			double dist = 0;
			out: for (int i = 0; i < 3; i++) {
				for (int j = 0; j < 2; j++) {
					int v = i * (j % 2 == 0 ? -1 : 1);
					targetPos = posBehind(player, v);
					dist = targetPos.distanceTo(oldPos);

					if (dist < 4.5)
						continue;
					if (dist > 9.9)
						continue;
					break out;
				}
			}

			if (targetPos == Vec3.ZERO)
				return;
			if (dist < 4.5)
				return;
			if (dist > 9.9)
				return;

			ServerboundMovePlayerPacket posPacket = new ServerboundMovePlayerPacket.Pos(targetPos.x, targetPos.y+0.25, targetPos.z, true, false);
			this.connection.send(posPacket);

			ServerboundMovePlayerPacket oldPacket = new ServerboundMovePlayerPacket.Pos(oldPos.x, oldPos.y, oldPos.z, true, false);
			this.connection.send(oldPacket);
		}));
	}

	@Unique
	private static Vec3 posBehind(Player player, int yOffset) {
		Level level = player.level();
		Vec3 playerPos = player.position();

		Vec3 look = player.getViewVector(0);
		Vec3 horizontalBack = new Vec3(-look.x, 0, -look.z).normalize();

		if (horizontalBack == Vec3.ZERO)
			return Vec3.ZERO;

		double targetY = playerPos.y + yOffset;
		Vec3 origin = new Vec3(playerPos.x, targetY, playerPos.z);

		double targetDistance = 9.5;

		Vec3 bestPos = null;
		double maxAvailableDist = -1.0;

		for (int offsetDeg = 0; offsetDeg <= 45; offsetDeg += 5) {
			int directionsCount = (offsetDeg == 0) ? 1 : 2;

			for (int d = 0; d < directionsCount; d++) {
				int angleDeg = (d == 0) ? offsetDeg : -offsetDeg;

				double rad = Math.toRadians(angleDeg);

				double cos = Math.cos(rad);
				double sin = Math.sin(rad);
				double scanX = horizontalBack.x * cos - horizontalBack.z * sin;
				double scanZ = horizontalBack.x * sin + horizontalBack.z * cos;
				Vec3 scanDir = new Vec3(scanX, 0, scanZ).normalize();

				double availableDist = getAvailableDistance(level, player, origin, scanDir, targetDistance);

				if (Math.abs(availableDist - targetDistance) < 0.01) {
					return origin.add(scanDir.scale(targetDistance));
				}

				if (availableDist > maxAvailableDist) {
					maxAvailableDist = availableDist;
					bestPos = origin.add(scanDir.scale(availableDist));
				}
			}
		}

		return bestPos != null ? bestPos : origin;
	}

	@Unique
	private static double getAvailableDistance(Level level, Player player, Vec3 origin, Vec3 dir, double maxDist) {
		double step = 0.25;
		CollisionContext context = CollisionContext.of(player);

		for (double d = step; d <= maxDist; d += step) {
			Vec3 checkPoint = origin.add(dir.scale(d));
			BlockPos pos = BlockPos.containing(checkPoint);

			if (!level.getBlockState(pos).getCollisionShape(level, pos, context).isEmpty()) {
				return Math.max(0, d - step);
			}
		}
		return maxDist;
	}}