package nl.orbinuity.dispenserspear;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.SharedConstants;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class DispenserSpearHelper {
    public static final String dispensedSpearId = DispenserSpear.MODID + ":dispensed_spear";

    public static boolean isSpear(Item item) {
        return item == Items.WOODEN_SPEAR || item == Items.STONE_SPEAR ||
                item == Items.IRON_SPEAR || item == Items.COPPER_SPEAR ||
                item == Items.GOLDEN_SPEAR || item == Items.DIAMOND_SPEAR ||
                item == Items.NETHERITE_SPEAR;
    }

    public static String getCurrentVersion() {
        return FabricLoader.getInstance()
                .getModContainer(DispenserSpear.MODID)
                .map(mod -> mod.getMetadata().getVersion().getFriendlyString())
                .orElse("0.0");
    }

    public static String getLatestVersion(String updateUri) {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(updateUri))
                .GET()
                .build();

        try {
            // This stops the code and WAITS for the internet to respond
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
                if (json.has("promos")) {
                    JsonObject promos = json.getAsJsonObject("promos");
                    String mcVersion = SharedConstants.getCurrentVersion().name(); // Use getName() instead of name()

                    if (promos.has(mcVersion + "-latest")) {
                        return promos.get(mcVersion + "-latest").getAsString(); // Return it right here!
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("[UpdateChecker] Failed to fetch or parse update JSON: " + e.getMessage());
        }

        return ""; // Return empty string if it fails
    }
}
