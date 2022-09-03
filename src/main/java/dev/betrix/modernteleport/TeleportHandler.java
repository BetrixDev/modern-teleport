package dev.betrix.modernteleport;

import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.configuration.Configuration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;

record TeleportRequest(Player sender, Player target, long time) {
}

public class TeleportHandler {

    private final ModernTeleport modernTeleport;
    private final Configuration config;
    private final HashMap<String, Long> coolDowns = new HashMap<>();
    private final HashMap<String, TeleportRequest> requests = new HashMap<>();

    TeleportHandler(ModernTeleport modernTeleport) {
        this.modernTeleport = modernTeleport;
        this.config = modernTeleport.getConfig();
    }

    public boolean hasPendingRequest(Player target) {
        return requests.containsKey(target.getUniqueId().toString());
    }

    public void removePendingRequest(Player target) {
        // Remove request from data
        requests.remove(target.getUniqueId().toString());

        TeleportRequest request = requests.get(target.getUniqueId().toString());

        // Send players messages dictating the event
        modernTeleport.messagePlayer(request.sender(), "messages.target_denied_message",
                Placeholder.unparsed("target_name", target.getName()));

        modernTeleport.messagePlayer(target, "messages.denied_message_confirmation",
                Placeholder.unparsed("sender_name", request.sender().getName()));
    }

    public boolean canUseCommand(Player player) {
        boolean usePermission = config.getBoolean("use_permissions");

        if (!usePermission) {
            return true;
        }

        return player.hasPermission("modernteleport.teleport");
    }


    public void setCoolDown(Player player) {
        if (player.hasPermission("modernteleport.bypasscooldown")) {
            return;
        }

        coolDowns.put(player.getUniqueId().toString(), System.currentTimeMillis());
    }

    public long getCoolDownTimeLeft(Player player) {
        String uid = player.getUniqueId().toString();
        long coolDown = this.config.getLong("cool_down");

        if (coolDowns.containsKey(uid)) {
            long time = coolDowns.get(uid);

            return (coolDown * 1000) - (System.currentTimeMillis() - time);
        } else {
            return 0;
        }
    }

    public void sendRequest(Player sender, Player target) {
        modernTeleport.messagePlayer(target, "messages.request_message",
                Placeholder.unparsed("sender_name", sender.getName()));

        // Store the request
        requests.put(target.getUniqueId().toString(), new TeleportRequest(sender, target, System.currentTimeMillis()));

        int timeout = config.getInt("request_timout");

        if (timeout == 0) {
            // A timeout of 0 disables it, so we return early
            return;
        }

        // Create a task to run after the timeout has been reached to delete the request if ignored
        new BukkitRunnable() {
            final String targetUid = target.getUniqueId().toString();

            @Override
            public void run() {
                if (!requests.containsKey(targetUid)) {
                    cancel();
                }

                requests.remove(targetUid);

                modernTeleport.messagePlayer(sender, "messages.target_not_respond",
                        Placeholder.unparsed("target_name", target.getName()));

                // Cancel this tasks
                cancel();
            }
        }.runTaskLaterAsynchronously(modernTeleport, 20L * timeout);
    }

    public TeleportResult canTeleport(@NotNull Player sender, @Nullable Player target) {
        if (!canUseCommand(sender)) {
            return new TeleportResult(false, "messages.no_permission");
        }

        if (target == null) {
            return new TeleportResult(false, "messages.invalid_usage");
        }

        if (!canUseCommand(target)) {
            return new TeleportResult(false, "messages.target_no_permission");
        }

        if (sender.getUniqueId().toString().equals(target.getUniqueId().toString())) {
            return new TeleportResult(false, "messages.request_yourself");
        }

        if (hasPendingRequest(target)) {
            return new TeleportResult(false, "messages.has_pending_request");
        }

        long coolDown = getCoolDownTimeLeft(sender);
        long targetCoolDown = getCoolDownTimeLeft(target);

        if (coolDown > 0) {
            return new TeleportResult(false, "messages.target_cool_down");
        } else if (targetCoolDown > 0) {
            return new TeleportResult(false, "messages.target_cool_down");
        }

        return new TeleportResult(true, "messages.request_sent");
    }

    public boolean doTeleport(Player target) {
        TeleportRequest request = requests.get(target.getUniqueId().toString());

        // Run command "/tp PLAYER_NAME TARGET_NAME"
        boolean success = modernTeleport.getServer().dispatchCommand(modernTeleport.getServer().getConsoleSender(),
                "tp " + request.sender().getName() + " " + target.getName());

        if (success) {
            requests.remove(target.getUniqueId().toString());
            setCoolDown(target);
            setCoolDown(request.sender());
        }

        return success;
    }
}
