package com.vlineaim.training;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Visible practice helpers for an offline single-player world.
 * The automatic attack feature only targets Mob entities, never Player entities,
 * and refuses to run outside single-player. No anti-cheat bypass or stealth logic
 * is included.
 */
public final class VLineTrainingClient implements ClientModInitializer {
    private static final String MOD_ID = "vline_training";
    private static final double ALERT_DISTANCE = 4.0;
    private static final double HITBOX_PADDING = 0.20;
    private static final int ALERT_REFRESH_TICKS = 5;
    private static final int MIN_AUTO_ATTACK_TICKS = 2;

    private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(
            Identifier.fromNamespaceAndPath(MOD_ID, "training")
    );

    private static final KeyMapping HITBOX_KEY = KeyMappingHelper.registerKeyMapping(
            new KeyMapping("key.vline_training.hitboxes", InputConstants.Type.KEYSYM,
                    InputConstants.KEY_F6, CATEGORY)
    );
    private static final KeyMapping PROXIMITY_KEY = KeyMappingHelper.registerKeyMapping(
            new KeyMapping("key.vline_training.proximity", InputConstants.Type.KEYSYM,
                    InputConstants.KEY_F7, CATEGORY)
    );
    private static final KeyMapping AUTO_HIT_KEY = KeyMappingHelper.registerKeyMapping(
            new KeyMapping("key.vline_training.autohit", InputConstants.Type.KEYSYM,
                    InputConstants.KEY_F8, CATEGORY)
    );

    private boolean proximityEnabled = true;
    private boolean autoHitEnabled = false;
    private boolean sessionWasActive = false;
    private boolean hitboxesTouchedByMod = false;
    private boolean originalHitboxState = false;
    private int ticksSinceAttack = MIN_AUTO_ATTACK_TICKS;
    private int alertTicker = 0;
    private int lastAlertEntityId = -1;

    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(this::onClientTick);
    }

    private void onClientTick(Minecraft client) {
        if (client.player == null || client.level == null) {
            closeTrainingSession(client);
            return;
        }

        if (!client.isSingleplayer()) {
            closeTrainingSession(client);
            boolean keyPressed = HITBOX_KEY.consumeClick();
            keyPressed |= PROXIMITY_KEY.consumeClick();
            keyPressed |= AUTO_HIT_KEY.consumeClick();
            if (client.screen == null && keyPressed) {
                client.player.sendSystemMessage(Component.literal(
                        "VLine Practice Tools only run in offline single-player worlds."));
            }
            return;
        }

        if (!sessionWasActive) {
            sessionWasActive = true;
            originalHitboxState = client.getEntityRenderDispatcher().shouldRenderHitboxes();
        }

        if (client.screen != null) {
            return;
        }

        while (HITBOX_KEY.consumeClick()) {
            boolean next = !client.getEntityRenderDispatcher().shouldRenderHitboxes();
            client.getEntityRenderDispatcher().setRenderHitboxes(next);
            hitboxesTouchedByMod = true;
            say(client, "Hitbox display " + (next ? "ON" : "OFF") + " (visible vanilla debug boxes)");
        }

        while (PROXIMITY_KEY.consumeClick()) {
            proximityEnabled = !proximityEnabled;
            lastAlertEntityId = -1;
            say(client, "Proximity alerts " + (proximityEnabled ? "ON" : "OFF"));
        }

        while (AUTO_HIT_KEY.consumeClick()) {
            autoHitEnabled = !autoHitEnabled;
            ticksSinceAttack = MIN_AUTO_ATTACK_TICKS;
            say(client, "Training auto-hit " + (autoHitEnabled
                    ? "ON — offline single-player mobs only; never targets players"
                    : "OFF"));
        }

        ticksSinceAttack++;
        if (!proximityEnabled && !autoHitEnabled) {
            return;
        }

        Mob nearMob = findMobNearAim(client);
        if (proximityEnabled && nearMob != null) {
            if (alertTicker-- <= 0 || nearMob.getId() != lastAlertEntityId) {
                double distance = client.player.getEyePosition().distanceTo(nearMob.position());
                String formatted = String.format(Locale.ROOT, "%.1f", distance);
                client.player.sendOverlayMessage(Component.literal(
                        "Practice target: " + nearMob.getDisplayName().getString() +
                                "  •  " + formatted + " blocks  •  near hitbox"));
                alertTicker = ALERT_REFRESH_TICKS;
                lastAlertEntityId = nearMob.getId();
            }
        } else {
            alertTicker = 0;
            lastAlertEntityId = -1;
        }

        if (autoHitEnabled && nearMob != null && ticksSinceAttack >= MIN_AUTO_ATTACK_TICKS) {
            // Attack only a mob actually selected by vanilla's own crosshair result.
            // This ensures the training helper does not add reach or attack through walls.
            boolean vanillaSelectedSameMob = client.hitResult instanceof EntityHitResult hit
                    && hit.getEntity() == nearMob;
            boolean cooledDown = client.player.getAttackStrengthScale(0.0F) >= 0.92F;
            if (vanillaSelectedSameMob && cooledDown && client.gameMode != null && nearMob.isAlive()) {
                client.gameMode.attack(client.player, nearMob);
                client.player.swing(InteractionHand.MAIN_HAND);
                ticksSinceAttack = 0;
            }
        }
    }

    /** Finds an ordinary Mob whose slightly expanded hitbox intersects the aim ray. */
    private Mob findMobNearAim(Minecraft client) {
        Vec3 start = client.player.getEyePosition();
        Vec3 direction = client.player.getViewVector(1.0F);
        Vec3 end = start.add(direction.scale(ALERT_DISTANCE));
        AABB searchBox = client.player.getBoundingBox().inflate(ALERT_DISTANCE + 1.0);
        List<Mob> candidates = client.level.getEntitiesOfClass(Mob.class, searchBox,
                mob -> mob.isAlive() && !mob.isSpectator());

        return candidates.stream()
                .map(mob -> new MobHit(mob, mob.getBoundingBox().inflate(HITBOX_PADDING).clip(start, end)))
                .filter(hit -> hit.intersection().isPresent())
                .min(Comparator.comparingDouble(hit -> start.distanceTo(hit.intersection().orElseThrow())))
                .map(MobHit::mob)
                .orElse(null);
    }

    private void say(Minecraft client, String message) {
        if (client.player != null) {
            client.player.sendSystemMessage(Component.literal(message));
        }
    }

    private void closeTrainingSession(Minecraft client) {
        autoHitEnabled = false;
        proximityEnabled = true;
        alertTicker = 0;
        lastAlertEntityId = -1;
        ticksSinceAttack = MIN_AUTO_ATTACK_TICKS;

        if (sessionWasActive) {
            if (hitboxesTouchedByMod) {
                client.getEntityRenderDispatcher().setRenderHitboxes(originalHitboxState);
            }
            sessionWasActive = false;
            hitboxesTouchedByMod = false;
        }
    }

    private record MobHit(Mob mob, Optional<Vec3> intersection) {}
}
