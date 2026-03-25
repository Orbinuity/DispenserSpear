package nl.orbinuity.dispenserspear;

import net.fabricmc.api.ModInitializer;

import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.DispenserBlock;

public class DispenserSpear implements ModInitializer {
	public static final String MODID = "dispenserspear";

	@Override
	public void onInitialize() {
		ServerPlayConnectionEvents.JOIN.register(this::onPlayerJoin);

		BuiltInRegistries.ITEM.forEach(item -> {
			DispenserBlock.registerBehavior(item, new DispenseSpearBehavior());
		});
	}

	private void onPlayerJoin(ServerGamePacketListenerImpl serverGamePacketListener, PacketSender packetSender, MinecraftServer minecraftServer) {
		serverGamePacketListener.getPlayer().displayClientMessage(net.minecraft.network.chat.Component.literal("§6<DispenserSpear>§r Hi! Please note that this is a beta version and is still a work in progress"), false);
	}
}