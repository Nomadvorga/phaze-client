package vorga.phazeclient.api.system.font.msdf;

import com.google.gson.Gson;
import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

public final class ResourceProvider {
    private static final Gson GSON = new Gson();

    private ResourceProvider() {
    }

    private static ResourceManager resourceManager() {
        return MinecraftClient.getInstance().getResourceManager();
    }

    public static <T> T fromJsonToInstance(Identifier identifier, Class<T> clazz) {
        return GSON.fromJson(toString(identifier), clazz);
    }

    public static String toString(Identifier identifier) {
        try {
            Optional<Resource> resource = resourceManager().getResource(identifier);
            if (resource.isPresent()) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(resource.get().getInputStream(), StandardCharsets.UTF_8))) {
                    StringBuilder builder = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        builder.append(line).append('\n');
                    }
                    return builder.toString();
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to read resource: " + identifier, e);
        }

        throw new RuntimeException("Resource not found: " + identifier);
    }
}
