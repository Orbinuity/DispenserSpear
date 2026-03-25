package nl.orbinuity.dispenserspear;

import net.fabricmc.api.ModInitializer;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.*;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;

import java.net.URI;
import java.util.*;

public class DispenserSpear implements ModInitializer {
	private static final Map<BlockPos, Boolean> lastPowerStatus = new HashMap<>();
	private static final Map<BlockPos, Long> actionTicks = new HashMap<>();
	public static final String MODID = "dispenserspear";

	@Override
	public void onInitialize() {
		ServerPlayConnectionEvents.JOIN.register(this::onPlayerJoin);
		ServerTickEvents.END_WORLD_TICK.register(this::tickEvent);

		BuiltInRegistries.ITEM.forEach(item -> DispenserBlock.registerBehavior(item, new DispenseSpearBehavior()));
	}

	private void tickEvent(ServerLevel level) {
		for (Entity entity : level.getAllEntities()) {
			if (entity instanceof Display.ItemDisplay display) {
				if (display.getTags().contains(DispenserSpearHelper.dispensedSpearId)) {
					Optional<String> origin = display.getTags().stream()
							.filter(s -> s.startsWith("origin:"))
							.findFirst();

					if (origin.isEmpty()) continue;

					String originPath = origin.get().substring("origin:".length());
					String[] dispenserLocation = originPath.split(",");

					if (dispenserLocation.length != 3) continue;

					List<Integer> dispenserLocations = new java.util.ArrayList<>();

					for (String dl : dispenserLocation) {
						dispenserLocations.add(Integer.parseInt(dl));
					}

					BlockPos dispenserPos = new BlockPos(dispenserLocations.get(0), dispenserLocations.get(1), dispenserLocations.get(2));

					BlockEntity dispenserBlock = level.getBlockEntity(dispenserPos);

					if (dispenserBlock instanceof DispenserBlockEntity dispenser) {
						if (display.tickCount > 1) {
							boolean thisLastPowerStatus = lastPowerStatus.get(dispenserPos) != null && lastPowerStatus.get(dispenserPos);
							if (dispenser.isEmpty() && !thisLastPowerStatus && level.hasNeighborSignal(dispenserPos)) {
								actionTicks.put(dispenserPos, level.getDayTime()+4);
							}
						}

						boolean thisActionTicks = actionTicks.get(dispenserPos) != null && actionTicks.get(dispenserPos) <= level.getDayTime();
						if (thisActionTicks) {
							actionTicks.remove(dispenserPos);

							ItemStack storedItem = Objects.requireNonNull(display.getSlot(0)).get();
							ItemStack stack = dispenser.getItem(0);
							stack.setCount(1);

							if (storedItem.isEmpty()) {
								storedItem = stack;
							}

							display.discard();

							boolean isFull = true;

							for (int i = 0; i < dispenser.getContainerSize(); i++) {
								ItemStack slotItem = dispenser.getItem(i);

								if (slotItem.isEmpty()) {
									isFull = false;
									break;
								}
							}

							if (!isFull && DispenserSpearHelper.isSpear(stack.getItem())) {
								dispenser.insertItem(stack);
							} else {
								ItemStack refundStack = storedItem.copy();

								Direction side = Direction.UP;

								if (dispenser.getLevel() != null) {
									Storage<ItemVariant> handler = ItemStorage.SIDED.find(
											dispenser.getLevel(),
											dispenser.getBlockPos(),
											dispenser.getBlockState(),
											dispenser,
											side
									);

									if (handler != null) {
										long insertedAmount;

										try (Transaction transaction = Transaction.openOuter()) {
											insertedAmount = handler.insert(
													ItemVariant.of(refundStack),
													refundStack.getCount(),
													transaction
											);
											transaction.commit();
										}

										ItemStack remainder = refundStack.copy();
										remainder.shrink((int) insertedAmount);

										if (!remainder.isEmpty()) {
											ItemEntity drop = new ItemEntity(
													level,
													dispenserPos.getX() + 0.5,
													dispenserPos.getY() + 0.5,
													dispenserPos.getZ() + 0.5,
													remainder
											);
											drop.setDefaultPickUpDelay();

											level.addFreshEntity(drop);
										}
									}
								}
							}
						}
						lastPowerStatus.put(dispenserPos, level.hasNeighborSignal(dispenserPos));
					} else {
						ItemStack storedItem = Objects.requireNonNull(display.getSlot(0)).get();

						if (!storedItem.isEmpty()) {
							ItemEntity item = new ItemEntity(
									level,
									dispenserPos.getX() + 0.5,
									dispenserPos.getY() + 0.5,
									dispenserPos.getZ() + 0.5,
									storedItem
							);

							level.addFreshEntity(item);
						}

						display.discard();
					}
				}
			}
		}
	}


	private void onPlayerJoin(ServerGamePacketListenerImpl serverGamePacketListener, PacketSender packetSender, MinecraftServer minecraftServer) {
		String latestVersion = DispenserSpearHelper.getLatestVersion("https://data.orbinuity.nl/DispenserSpear/update.json");
		if (!DispenserSpearHelper.getCurrentVersion().equals(latestVersion)) {
			Component link = Component.literal("v"+latestVersion)
					.withStyle(style -> style
							.withColor(ChatFormatting.BLUE)
							.withUnderlined(true)
							.withClickEvent(new ClickEvent.OpenUrl(URI.create("https://orbinuity.nl/project/DispenserSpear")))
							.withHoverEvent(new HoverEvent.ShowText(Component.literal("Click to open download page")))
					);

			serverGamePacketListener.getPlayer().displayClientMessage(Component.literal("§6<DispenserSpear>§r Hey! Theres a new version: ").append(link).append(" (Yours: "+DispenserSpearHelper.getCurrentVersion()+")"), false);
		}
	}
}