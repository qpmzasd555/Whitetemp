package me.mrbushy.whitetemp.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import me.mrbushy.whitetemp.Whitetemp;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;

import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class whitetempCommand {

    public static void registerCommands() {
        CommandRegistrationCallback.EVENT.register(whitetempCommand::register);
    }

    // Method to register commands
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher, CommandRegistryAccess registryAccess, CommandManager.RegistrationEnvironment environment) {
        dispatcher.register(
                literal("wtadd")
                        .requires(source -> source.hasPermissionLevel(2) || source.getEntity() == null) // Restrict to ops or console
                        .then(
                                argument("player", StringArgumentType.string())
                                        .then(
                                                argument("time", StringArgumentType.string()) // Argument for time with suffix (e.g., 10m, 1h)
                                                        .executes(context -> {
                                                            // Extract player name and time string from the command
                                                            String playerName = StringArgumentType.getString(context, "player");
                                                            String timeInput = StringArgumentType.getString(context, "time");

                                                            // Parse the time string into milliseconds
                                                            long duration = parseTimeString(timeInput);

                                                            if (duration <= 0) {
                                                                context.getSource().sendError(Text.of("Invalid time format! Use s, m, h, d, M, or Y."));
                                                                return Command.SINGLE_SUCCESS;
                                                            }

                                                            // Calculate expiration time
                                                            long expirationTime = System.currentTimeMillis() + duration;

                                                            // Add player to whitelist with expiration time
                                                            Whitetemp.addPlayerToWhitelist(playerName, expirationTime);

                                                            // Send feedback to the command sender
                                                            context.getSource().sendMessage(Text.of("Added " + playerName + " to the whitelist for " + timeInput + "."));

                                                            return Command.SINGLE_SUCCESS;
                                                        })
                                        )
                        )
        );

        // Command to remove a player from the whitelist
        dispatcher.register(
                literal("wtrem")
                        .requires(source -> source.hasPermissionLevel(2) || source.getEntity() == null) // Restrict to ops or console
                        .then(
                                argument("player", StringArgumentType.string()) // Argument for the player's name
                                        .executes(context -> {
                                            String playerName = StringArgumentType.getString(context, "player");

                                            // Call the Whitetemp.removePlayerFromWhitelist() method without checking
                                            Whitetemp.removePlayerFromWhitelist(playerName);

                                            // Always send a success message, even if the player wasn't on the list
                                            context.getSource().sendMessage(Text.of("Removed " + playerName + " from the whitelist."));

                                            return Command.SINGLE_SUCCESS;
                                        })
                        )
        );

        // Command to prolong a player's whitelist time using 'wtprlng <player> <time>'
        dispatcher.register(
                literal("wtprlng")
                        .requires(source -> source.hasPermissionLevel(2) || source.getEntity() == null) // Restrict to ops or console
                        .then(
                                argument("player", StringArgumentType.string()) // Argument for the player's name
                                        .then(
                                                argument("time", StringArgumentType.string()) // Argument for the prolongation time (e.g., 10m, 1h)
                                                        .executes(context -> {
                                                            // Extract player name and time string from the command
                                                            String playerName = StringArgumentType.getString(context, "player");
                                                            String timeInput = StringArgumentType.getString(context, "time");

                                                            // Parse the time string into milliseconds
                                                            long prolongDuration = parseTimeString(timeInput);

                                                            if (prolongDuration <= 0) {
                                                                context.getSource().sendError(Text.of("Invalid time format! Use s, m, h, d, M, or Y."));
                                                                return Command.SINGLE_SUCCESS;
                                                            }

                                                            // Fetch current whitelist expiration time
                                                            long currentExpiration = Whitetemp.getWhitelistExpiration(playerName);
                                                            long currentTime = System.currentTimeMillis();
                                                            long newExpirationTime;

                                                            // Calculate the new expiration time by adding prolongation
                                                            if (currentTime > currentExpiration) {
                                                                newExpirationTime = currentTime + prolongDuration;
                                                            } else {
                                                                newExpirationTime = currentExpiration + prolongDuration;
                                                            }

                                                            // Update the player's expiration time in the whitelist
                                                            Whitetemp.addPlayerToWhitelist(playerName, newExpirationTime);

                                                            // Send feedback to the command sender
                                                            context.getSource().sendMessage(Text.of("Prolonged " + playerName + "'s whitelist time by " + timeInput + "."));

                                                            return Command.SINGLE_SUCCESS;
                                                        })
                                        )
                        )
        );

        // Command to check player's time
        dispatcher.register(
                literal("wtcheck")
                        .requires(source -> source.hasPermissionLevel(2) || source.getEntity() == null) // Restrict to ops or console
                        .then(
                                argument("player", StringArgumentType.string()) // Argument for the player's name
                                        .executes(context -> {
                                            String playerName = StringArgumentType.getString(context, "player");

                                            // Get the expiration time
                                            long expirationTime = Whitetemp.getWhitelistExpiration(playerName);

                                            if (expirationTime == -1) {
                                                context.getSource().sendError(Text.of("Player " + playerName + " is not in the whitelist."));
                                            } else {
                                                long remainingTime = expirationTime - System.currentTimeMillis();
                                                String timeString = formatRemainingTime(remainingTime);
                                                context.getSource().sendMessage(Text.of("Player " + playerName + " is whitelisted for: " + timeString));
                                            }

                                            return Command.SINGLE_SUCCESS;
                                        })
                        )
        );
    }

    // Method to parse time strings into milliseconds
    private static long parseTimeString(String timeString) {
        Pattern pattern = Pattern.compile("(\\d+)([smhdMY])"); // Regex for numbers followed by a time unit (e.g., 10m)
        Matcher matcher = pattern.matcher(timeString);

        if (matcher.matches()) {
            long value = Long.parseLong(matcher.group(1));
            String unit = matcher.group(2);

            return switch (unit) {
                case "s" -> // Seconds
                        TimeUnit.SECONDS.toMillis(value);
                case "m" -> // Minutes
                        TimeUnit.MINUTES.toMillis(value);
                case "h" -> // Hours
                        TimeUnit.HOURS.toMillis(value);
                case "d" -> // Days
                        TimeUnit.DAYS.toMillis(value);
                case "M" -> // Months (approx. 30 days per month)
                        TimeUnit.DAYS.toMillis(value * 30);
                case "Y" -> // Years (approx. 365 days per year)
                        TimeUnit.DAYS.toMillis(value * 365);
                default -> -1; // Invalid unit
            };
        }
        return -1; // Invalid format
    }

    private static String formatRemainingTime(long milliseconds) {
        if (milliseconds <= 0) {
            return "expired"; // If the time has expired
        }

        long seconds = TimeUnit.MILLISECONDS.toSeconds(milliseconds);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(milliseconds);
        long hours = TimeUnit.MILLISECONDS.toHours(milliseconds);
        long days = TimeUnit.MILLISECONDS.toDays(milliseconds);

        StringBuilder timeString = new StringBuilder();

        if (days > 0) {
            timeString.append(days).append("d ");
        }
        if (hours > 0) {
            timeString.append(hours % 24).append("h ");
        }
        if (minutes > 0) {
            timeString.append(minutes % 60).append("m ");
        }
        if (seconds > 0) {
            timeString.append(seconds % 60).append("s ");
        }

        return timeString.toString().trim(); // Remove any trailing space
    }
}