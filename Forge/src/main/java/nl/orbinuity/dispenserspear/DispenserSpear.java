package nl.orbinuity.dispenserspear;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Display.ItemDisplay;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.bus.BusGroup;
import net.minecraftforge.eventbus.api.bus.EventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.registries.ForgeRegistries;

import java.net.URI;
import java.util.*;

@Mod(DispenserSpear.MODID)
public class DispenserSpear {
    public static final Map<BlockPos, Boolean> lastPowerStatus = new HashMap<>();
    public static final Map<BlockPos, Long> actionTicks = new HashMap<>();
    public static final String MODID = "dispenserspear";

    public DispenserSpear(FMLJavaModLoadingContext context) {
        BusGroup busGroup = context.getModBusGroup();
        EventBus<FMLCommonSetupEvent> bus = FMLCommonSetupEvent.getBus(busGroup);

        bus.addListener(this::commonSetup);

        PlayerEvent.PlayerLoggedInEvent.BUS.addListener(this::onPlayerJoin);
        TickEvent.LevelTickEvent.Post.BUS.addListener(this::tickEvent);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() ->
                ForgeRegistries.ITEMS.getEntries().forEach(entry ->
                        DispenserBlock.registerBehavior(entry.getValue(), new DispenseSpearBehavior())
                )
        );
    }

    private void tickEvent(TickEvent.LevelTickEvent.Post postEvent) {
        if (postEvent.level().isClientSide()) return;
        ServerLevel level = (ServerLevel) postEvent.level();

        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof ItemDisplay display) {
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

                                dispenser.getCapability(ForgeCapabilities.ITEM_HANDLER).ifPresent(handler -> {
                                    ItemStack remainder = ItemHandlerHelper.insertItemStacked(handler, refundStack, false);
                                    if (!remainder.isEmpty()) {
                                        ItemEntity drop = new ItemEntity(
                                                level, dispenserPos.getX() + 0.5, dispenserPos.getY() + 0.5, dispenserPos.getZ() + 0.5, remainder
                                        );
                                        level.addFreshEntity(drop);
                                    }
                                });
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

    private void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        Player player = event.getEntity();

        if (!player.level().isClientSide()) {
            String latestVersion = DispenserSpearHelper.getLatestVersion("https://data.orbinuity.nl/DispenserSpear/update.json");
		    if (!DispenserSpearHelper.getCurrentVersion().equals(latestVersion)) {
			    Component link = Component.literal("v"+latestVersion)
					.withStyle(style -> style
							.withColor(ChatFormatting.BLUE)
							.withUnderlined(true)
							.withClickEvent(new ClickEvent.OpenUrl(URI.create("https://orbinuity.nl/project/DispenserSpear")))
							.withHoverEvent(new HoverEvent.ShowText(Component.literal("Click to open download page")))
					);

			    player.displayClientMessage(Component.literal("§6<DispenserSpear>§r Hey! Theres a new version: ").append(link).append(" (Yours: "+DispenserSpearHelper.getCurrentVersion()+")"), false);
		    }
        }
    }
}