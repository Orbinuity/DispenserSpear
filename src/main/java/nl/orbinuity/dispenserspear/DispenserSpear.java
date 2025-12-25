package nl.orbinuity.dispenserspear;

import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.bus.BusGroup;
import net.minecraftforge.eventbus.api.bus.EventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(DispenserSpear.MODID)
public class DispenserSpear {
    public static final String MODID = "dispenserspear";

    public DispenserSpear(FMLJavaModLoadingContext context) {
        BusGroup busGroup = context.getModBusGroup();
        EventBus<FMLCommonSetupEvent> bus = FMLCommonSetupEvent.getBus(busGroup);

        bus.addListener(this::commonSetup);

        PlayerEvent.PlayerLoggedInEvent.BUS.addListener(this::onPlayerJoin);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            net.minecraftforge.registries.ForgeRegistries.ITEMS.getEntries().forEach(entry -> {
                DispenserBlock.registerBehavior(entry.getValue(), new DispenseSpearBehavior());
            });
        });
    }

    private void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        net.minecraft.world.entity.player.Player player = event.getEntity();

        if (!player.level().isClientSide()) {
            player.displayClientMessage(
                    net.minecraft.network.chat.Component.literal("§6<DispenserSpear>§r Hi! Please note that this is a beta version and is still a work in progress"),
                    false
            );
        }
    }
}