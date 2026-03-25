package nl.orbinuity.dispenserspear;

import net.fabricmc.api.ModInitializer;

import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.DispenserBlock;

public class DispenserSpear implements ModInitializer {
	public static final String MODID = "dispenserspear";

	@Override
	public void onInitialize() {
		ServerPlayConnectionEvents.JOIN.register(this::onPlayerJoin);

		DispenserBlock.registerBehavior(Items.WOODEN_SPEAR, new DispenseSpearBehavior());
		DispenserBlock.registerBehavior(Items.STONE_SPEAR, new DispenseSpearBehavior());
		DispenserBlock.registerBehavior(Items.COPPER_SPEAR, new DispenseSpearBehavior());
		DispenserBlock.registerBehavior(Items.GOLDEN_SPEAR, new DispenseSpearBehavior());
		DispenserBlock.registerBehavior(Items.DIAMOND_SPEAR, new DispenseSpearBehavior());
		DispenserBlock.registerBehavior(Items.NETHERITE_SPEAR, new DispenseSpearBehavior());
	}

	private void onPlayerJoin(ServerGamePacketListenerImpl serverGamePacketListener, PacketSender packetSender, MinecraftServer minecraftServer) {
		serverGamePacketListener.getPlayer().displayClientMessage(net.minecraft.network.chat.Component.literal("<DispenserSpear> Hi! Pleas note that this is a beta version and is still a work in progress"), false);
	}
}