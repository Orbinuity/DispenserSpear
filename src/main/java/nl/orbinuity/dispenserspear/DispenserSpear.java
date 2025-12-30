package nl.orbinuity.dispenserspear;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Display.ItemDisplay;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.bus.BusGroup;
import net.minecraftforge.eventbus.api.bus.EventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Mod(DispenserSpear.MODID)
public class DispenserSpear {
    public static final String MODID = "dispenserspear";
    private final java.util.Set<BlockPos> poweredDispensers = new java.util.HashSet<>();

    public DispenserSpear(FMLJavaModLoadingContext context) {
        BusGroup busGroup = context.getModBusGroup();
        EventBus<FMLCommonSetupEvent> bus = FMLCommonSetupEvent.getBus(busGroup);

        bus.addListener(this::commonSetup);

        PlayerEvent.PlayerLoggedInEvent.BUS.addListener(this::onPlayerJoin);
        TickEvent.LevelTickEvent.Post.BUS.addListener(this::test);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            ForgeRegistries.ITEMS.getEntries().forEach(entry -> {
                DispenserBlock.registerBehavior(entry.getValue(), new DispenseSpearBehavior());
            });
        });
    }

    private void test(TickEvent.LevelTickEvent.Post postEvent) {
        if (postEvent.level().isClientSide()) return;
        ServerLevel level = (ServerLevel) postEvent.level();

        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof ItemDisplay display) {
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
                        Item storedItem = Items.AIR;

                        Optional<String> itemId = display.getTags().stream()
                                .filter(s -> s.startsWith("item_id:"))
                                .findFirst();

                        if (itemId.isEmpty()) continue;

                        for (Item candidate : ForgeRegistries.ITEMS.getValues()) {
                            String candidateId = Objects.requireNonNull(ForgeRegistries.ITEMS.getKey(candidate)).toString();
                            if (candidateId.equals(itemId.get().substring("item_id:".length()))) {
                                storedItem = candidate;
                                break;
                            }
                        }

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

    private void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        Player player = event.getEntity();

        if (!player.level().isClientSide()) {
            player.displayClientMessage(
                    Component.literal("§6<DispenserSpear>§r Hi! Please note that this is a beta version and is still a work in progress"),
                    false
            );
        }
    }
}