package com.secret.dogbreeder;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.passive.WolfEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class DogBreederClient implements ClientModInitializer {
    private static final double RANGE = 4.25;
    private static final int STATUS_EVERY_ACTIONS = 8;

    private static boolean enabled;
    private static int statusDelay;
    private static int actionsSinceStatus;
    private static int previousHotbarSlot = -1;
    private static int babyCursor;
    private static int adultCursor;

    @Override
    public void onInitializeClient() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(ClientCommandManager.literal("breed")
                    .executes(context -> {
                        setEnabled(!enabled);
                        context.getSource().sendFeedback(statusText());
                        return 1;
                    })
                    .then(ClientCommandManager.literal("on").executes(context -> {
                        setEnabled(true);
                        context.getSource().sendFeedback(statusText());
                        return 1;
                    }))
                    .then(ClientCommandManager.literal("off").executes(context -> {
                        setEnabled(false);
                        context.getSource().sendFeedback(statusText());
                        return 1;
                    }))
                    .then(ClientCommandManager.literal("status").executes(context -> {
                        context.getSource().sendFeedback(statusText());
                        return 1;
                    }))
            );
        });

        ClientTickEvents.END_CLIENT_TICK.register(DogBreederClient::tick);
    }

    private static void setEnabled(boolean value) {
        MinecraftClient client = MinecraftClient.getInstance();

        if (value && !enabled && client.player != null) {
            previousHotbarSlot = client.player.getInventory().getSelectedSlot();
        }

        enabled = value;
        statusDelay = 0;
        actionsSinceStatus = 0;
        babyCursor = 0;
        adultCursor = 0;

        if (!enabled && client.player != null && previousHotbarSlot >= 0 && previousHotbarSlot <= 8) {
            client.player.getInventory().setSelectedSlot(previousHotbarSlot);
            previousHotbarSlot = -1;
        }
    }

    private static Text statusText() {
        return Text.literal("DogBreeder: ")
                .append(Text.literal(enabled ? "ON • MAX SPAM" : "OFF")
                        .formatted(enabled ? Formatting.GREEN : Formatting.RED));
    }

    private static void tick(MinecraftClient client) {
        if (!enabled || client.player == null || client.world == null || client.interactionManager == null) {
            return;
        }

        ClientPlayerEntity player = client.player;
        if (statusDelay > 0) statusDelay--;

        if (!ensureSteakInHand(client)) {
            if (statusDelay <= 0) {
                player.sendMessage(Text.literal("DogBreeder: no steak found in your inventory.")
                        .formatted(Formatting.RED), true);
                statusDelay = 40;
            }
            return;
        }

        List<WolfEntity> wolves = ownedNearbyWolves(client);
        if (wolves.isEmpty()) {
            if (statusDelay <= 0) {
                player.sendMessage(Text.literal("DogBreeder: no owned wolves within " + RANGE + " blocks.")
                        .formatted(Formatting.YELLOW), true);
                statusDelay = 40;
            }
            return;
        }

        WolfEntity target = chooseTarget(player, wolves);
        if (target == null) return;

        boolean baby = target.isBaby();
        client.interactionManager.interactEntity(player, target, Hand.MAIN_HAND);
        player.swingHand(Hand.MAIN_HAND);
        actionsSinceStatus++;

        if (actionsSinceStatus >= STATUS_EVERY_ACTIONS) {
            String action = baby ? "Spam-growing babies" : "Spam-feeding ALL adults";
            player.sendMessage(Text.literal("DogBreeder: " + action + " • steak " + totalSteakCount(player))
                    .formatted(Formatting.GREEN), true);
            actionsSinceStatus = 0;
        }
    }

    private static List<WolfEntity> ownedNearbyWolves(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        List<WolfEntity> result = new ArrayList<>();

        for (WolfEntity wolf : client.world.getEntitiesByClass(
                WolfEntity.class,
                player.getBoundingBox().expand(RANGE),
                wolf -> wolf.isAlive() && wolf.isTamed() && wolf.isOwner(player))) {
            result.add(wolf);
        }

        result.sort(Comparator.comparingDouble(wolf -> player.squaredDistanceTo(wolf)));
        return result;
    }

    private static WolfEntity chooseTarget(ClientPlayerEntity player, List<WolfEntity> wolves) {
        List<WolfEntity> babies = new ArrayList<>();
        List<WolfEntity> adults = new ArrayList<>();

        for (WolfEntity wolf : wolves) {
            if (wolf.isBaby()) babies.add(wolf);
            else adults.add(wolf);
        }

        // If any babies exist, spam-feed them first to force growth as fast as the server accepts it.
        if (!babies.isEmpty()) {
            babies.sort(Comparator
                    .comparingInt(WolfEntity::getBreedingAge)
                    .thenComparingDouble(wolf -> player.squaredDistanceTo(wolf)));
            WolfEntity target = babies.get(Math.floorMod(babyCursor, babies.size()));
            babyCursor++;
            adultCursor = 0;
            return target;
        }

        // No babies: spam EVERY adult in round-robin order, regardless of love state or breeding cooldown.
        if (!adults.isEmpty()) {
            adults.sort(Comparator.comparingDouble(wolf -> player.squaredDistanceTo(wolf)));
            WolfEntity target = adults.get(Math.floorMod(adultCursor, adults.size()));
            adultCursor++;
            babyCursor = 0;
            return target;
        }

        return null;
    }

    private static boolean ensureSteakInHand(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        if (player == null || client.interactionManager == null) return false;

        int selected = player.getInventory().getSelectedSlot();
        ItemStack selectedStack = player.getInventory().getStack(selected);
        if (selectedStack.isOf(Items.COOKED_BEEF) && !selectedStack.isEmpty()) {
            return true;
        }

        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = player.getInventory().getStack(slot);
            if (stack.isOf(Items.COOKED_BEEF) && !stack.isEmpty()) {
                player.getInventory().setSelectedSlot(slot);
                return true;
            }
        }

        if (client.currentScreen == null && player.currentScreenHandler.syncId == 0) {
            for (int slot = 9; slot < 36; slot++) {
                ItemStack stack = player.getInventory().getStack(slot);
                if (stack.isOf(Items.COOKED_BEEF) && !stack.isEmpty()) {
                    client.interactionManager.clickSlot(
                            player.currentScreenHandler.syncId,
                            slot,
                            selected,
                            SlotActionType.SWAP,
                            player
                    );
                    return false;
                }
            }
        }

        return false;
    }

    private static int totalSteakCount(ClientPlayerEntity player) {
        int total = 0;
        for (int slot = 0; slot < 36; slot++) {
            ItemStack stack = player.getInventory().getStack(slot);
            if (stack.isOf(Items.COOKED_BEEF)) total += stack.getCount();
        }
        return total;
    }
}
