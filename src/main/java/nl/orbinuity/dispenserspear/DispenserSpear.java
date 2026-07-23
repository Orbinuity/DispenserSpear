package nl.orbinuity.dispenserspear;

import net.fabricmc.api.ModInitializer;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;

import java.util.List;
import java.util.Optional;

public class DispenserSpear implements ModInitializer {
	public static final String MODID = "dispenserspear";

	@Override
	public void onInitialize() {
		//ServerPlayConnectionEvents.JOIN.register(this::onPlayerJoin);
		ServerTickEvents.END_WORLD_TICK.register(this::test);

		BuiltInRegistries.ITEM.forEach(item -> {
			DispenserBlock.registerBehavior(item, new DispenseSpearBehavior());
		});
	}

	private void test(ServerLevel level) {
		for (Entity entity : level.getAllEntities()) {
			if (entity instanceof Display.ItemDisplay display) {
				if (display.getTags().contains(DispenseSpearBehavior.dispensedSpearId)) {
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

					//boolean isNowPowered = level.hasNeighborSignal(dispenserPos);
					//boolean wasPoweredLastTick = poweredDispensers.contains(dispenserPos);

					if (dispenserBlock instanceof DispenserBlockEntity dispenser) {
                        /*if (dispenser.isEmpty() && !isNowPowered && wasPoweredLastTick) {

                            Optional<String> itemId = display.getTags().stream()
                                    .filter(s -> s.startsWith("item_id:"))
                                    .findFirst();

                            if (itemId.isPresent()) {
                                Item storedItem = Items.AIR;
                                String targetId = itemId.get().substring("item_id:".length());

                                for (Item candidate : ForgeRegistries.ITEMS.getValues()) {
                                    if (Objects.requireNonNull(ForgeRegistries.ITEMS.getKey(candidate)).toString().equals(targetId)) {
                                        storedItem = candidate;
                                        break;
                                    }
                                }
                                for (int i = 0; i < dispenser.getContainerSize(); i++) {
                                    if (dispenser.getItem(i).isEmpty()) {
                                        dispenser.setItem(i, new ItemStack(storedItem));
                                        break;
                                    }
                                }
                            }
                            display.discard();
                        }*/
					} else {
						Item storedItem;

						String itemId = display.getTags().stream()
								.filter(s -> s.startsWith("item_id:"))
								.findFirst().orElse("");

						if (itemId.isEmpty()) continue;

						Identifier itemIdentifier = Identifier.parse(itemId);
						Optional<Holder.Reference<Item>> optionalStoredItem = BuiltInRegistries.ITEM.get(itemIdentifier);
						storedItem = optionalStoredItem.map(Holder.Reference::value).orElse(Items.AIR);

						ItemEntity item = new ItemEntity(
								level,
								dispenserPos.getX() + 0.5,
								dispenserPos.getY() + 0.5,
								dispenserPos.getZ() + 0.5,
								new ItemStack(storedItem)
						);

						level.addFreshEntity(item);

						display.discard();
					}
				}
			}
		}
	}


	/*private void onPlayerJoin(ServerGamePacketListenerImpl serverGamePacketListener, PacketSender packetSender, MinecraftServer minecraftServer) {
		serverGamePacketListener.getPlayer().displayClientMessage(net.minecraft.network.chat.Component.literal("§6<DispenserSpear>§r Hi! Please note that this is a beta version and is still a work in progress"), false);
	}*/
}