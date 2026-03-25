package nl.orbinuity.dispenserspear;

import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraftforge.eventbus.api.bus.BusGroup;
import net.minecraftforge.eventbus.api.bus.EventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

// TODO: Add command for clean up
@Mod(DispenserSpear.MODID)
public class DispenserSpear {
    public static final String MODID = "dispenserspear";

    public DispenserSpear(FMLJavaModLoadingContext context) {
        BusGroup busGroup = context.getModBusGroup();
        EventBus<FMLCommonSetupEvent> bus = FMLCommonSetupEvent.getBus(busGroup);

        bus.addListener(this::commonSetup);

        // TODO: Only send message on join and not spawn
        net.minecraftforge.event.entity.EntityJoinLevelEvent.BUS.addListener(this::onPlayerJoin);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            DispenserBlock.registerBehavior(Items.WOODEN_SPEAR, new DispenseSpearBehavior());
            DispenserBlock.registerBehavior(Items.STONE_SPEAR, new DispenseSpearBehavior());
            DispenserBlock.registerBehavior(Items.COPPER_SPEAR, new DispenseSpearBehavior());
            DispenserBlock.registerBehavior(Items.GOLDEN_SPEAR, new DispenseSpearBehavior());
            DispenserBlock.registerBehavior(Items.DIAMOND_SPEAR, new DispenseSpearBehavior());
            DispenserBlock.registerBehavior(Items.NETHERITE_SPEAR, new DispenseSpearBehavior());
        });
    }

    private void onPlayerJoin(net.minecraftforge.event.entity.EntityJoinLevelEvent event) {
        if (event.getEntity() instanceof net.minecraft.world.entity.player.Player player) {
            if (!event.getLevel().isClientSide()) {
                player.displayClientMessage(net.minecraft.network.chat.Component.literal("<DispenserSpear> Hi! Pleas note that this is a beta version and is still a work in progress"), false);
            }
        }
    }
}