package me.mrbushy.whitetemp;

import me.mrbushy.whitetemp.command.whitetempCommand;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.api.ModInitializer;

import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

public class Whitetemp implements ModInitializer {
	public static final String MOD_ID = "whitetemp";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	// Now stores the player name and expiration time
	private static final Map<String, Long> WHITELIST = new HashMap<>();
	private static final Path CONFIG_DIR = Paths.get("config");
	private static final File WHITELIST_FILE = CONFIG_DIR.resolve("whitetemp_list.json").toFile();
	private static final Gson GSON = new Gson();

	@Override
	public void onInitialize() {
		LOGGER.info("Initializing whitelist mod...");

		createConfigDirectory();
		loadWhitelist();

		// Register commands
		whitetempCommand.registerCommands();

		ServerPlayConnectionEvents.JOIN.register(this::onPlayerJoin);
		ServerPlayConnectionEvents.DISCONNECT.register(this::onPlayerDisconnect);
	}

	private void onPlayerDisconnect(ServerPlayNetworkHandler serverPlayNetworkHandler,
									MinecraftServer minecraftServer) {
		String playerName = serverPlayNetworkHandler.player.getName().getString();

		if (WHITELIST.containsKey(playerName.toLowerCase())) {
			long expirationTime = WHITELIST.get(playerName.toLowerCase());
			long currentTime = System.currentTimeMillis();
			if (currentTime < expirationTime) {
				minecraftServer.getPlayerManager().broadcast(Text.literal(
						playerName + " has left the game!").formatted(Formatting.GOLD), false);
			}
		}
	}


	private void onPlayerJoin(ServerPlayNetworkHandler serverPlayNetworkHandler,
							  PacketSender packetSender,
							  MinecraftServer minecraftServer) {
		loadWhitelist();

		String playerName = serverPlayNetworkHandler.player.getName().getString();

		// Check if the player is in the whitelist
		if (!WHITELIST.containsKey(playerName.toLowerCase())) {
			serverPlayNetworkHandler.disconnect(Text.of("You are not whitelisted on this server."));
			return;
		}

		long expirationTime = WHITELIST.get(playerName.toLowerCase());
		long currentTime = System.currentTimeMillis();

		if (currentTime > expirationTime) { // Check if the player's whitelist has expired
			serverPlayNetworkHandler.disconnect(Text.of("Your whitelist access has expired."));
			return;
		}

		minecraftServer.getPlayerManager().broadcast(Text.literal(playerName + " has joined the game!").formatted(Formatting.YELLOW), false);
	}

	// Create config directory if it doesn't exist
	private void createConfigDirectory() {
		try {
			if (!Files.exists(CONFIG_DIR)) {
				Files.createDirectories(CONFIG_DIR);
                LOGGER.info("Created config directory: {}", CONFIG_DIR.toAbsolutePath());
			}
		} catch (IOException e) {
			LOGGER.error("Failed to create config directory", e);
		}
	}

	// Method to load the whitelist from the JSON file
	private void loadWhitelist() {
		if (WHITELIST_FILE.exists()) {
			try (FileReader reader = new FileReader(WHITELIST_FILE)) {
				Type mapType = new TypeToken<Map<String, Long>>() {}.getType();
				Map<String, Long> loadedWhitelist = GSON.fromJson(reader, mapType);

				if (loadedWhitelist != null) {
					WHITELIST.putAll(loadedWhitelist);
				}
			} catch (IOException e) {
				LOGGER.error("Failed to load the whitelist", e);
			}
		}
	}

	// Method to save the whitelist to the JSON file
	private static void saveWhitelist() {
		try (FileWriter writer = new FileWriter(WHITELIST_FILE)) {
			GSON.toJson(WHITELIST, writer);
		} catch (IOException e) {
			System.err.println("Failed to save the whitelist: " + e.getMessage());
		}
	}

	// Adding a player to the whitelist with expiration time and saving the file
	public static void addPlayerToWhitelist(String playerName, long expirationTime) {
		WHITELIST.put(playerName.toLowerCase(), expirationTime);
		saveWhitelist();
	}

	// Removing a player from the whitelist and saving the file
	public static void removePlayerFromWhitelist(String playerName) {
		WHITELIST.remove(playerName.toLowerCase());
		saveWhitelist();
	}

	// Method to get the whitelist expiration time for a player
	public static Long getWhitelistExpiration(String playerName) {
		// Retrieve the player's expiration time from the whitelist
		return WHITELIST.get(playerName.toLowerCase()); // Assuming WHITELIST is a Map<String, Long>
	}
}
