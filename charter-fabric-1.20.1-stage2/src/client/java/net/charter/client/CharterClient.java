package net.charter.client;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.charter.CharterMod;
import net.charter.logic.Curses;
import net.charter.net.CharterNetworking;
import net.charter.registry.ModEntities;
import net.charter.registry.ModItems;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.item.ModelPredicateProviderRegistry;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public class CharterClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		EntityRendererRegistry.register(ModEntities.CHARTER_FX, CharterFxRenderer::new);

		// Dusk's Epitaph switches to its two-handed model while the player holds it with both hands
		ModelPredicateProviderRegistry.register(ModItems.DUSK_EPITAPH, CharterMod.id("two_handed"),
				(stack, world, entity, seed) -> entity != null && TwoHanded.is(entity) ? 1.0F : 0.0F);

		// the server tells us which body parts everybody has lost
		ClientPlayNetworking.registerGlobalReceiver(CharterNetworking.BODY, (client, handler, buf, responseSender) -> {
			int count = buf.readVarInt();
			Map<UUID, Integer> masks = new HashMap<>();
			for (int i = 0; i < count; i++) {
				UUID id = buf.readUuid();
				masks.put(id, buf.readVarInt());
			}
			client.execute(() -> CharterClientState.applyBody(client, masks));
		});

		ClientTickEvents.END_WORLD_TICK.register(CharterClientState::tick);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> CharterClientState.clear());

		// a cursed weapon says whose it is
		ItemTooltipCallback.EVENT.register((stack, context, lines) -> {
			if (Curses.isCursed(stack)) {
				String signer = Curses.signerNameOf(stack);
				lines.add(Text.translatable("tooltip.charter.cursed", signer.isEmpty() ? "?" : signer).formatted(Formatting.RED, Formatting.ITALIC));
			}
		});
	}
}
