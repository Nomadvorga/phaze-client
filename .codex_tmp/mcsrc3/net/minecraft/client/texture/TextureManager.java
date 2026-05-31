package net.minecraft.client.texture;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.realms.gui.screen.BuyRealmsScreen;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceReloader;
import net.minecraft.util.Identifier;
import net.minecraft.util.crash.CrashException;
import net.minecraft.util.crash.CrashReport;
import net.minecraft.util.crash.CrashReportSection;
import org.slf4j.Logger;

@Environment(EnvType.CLIENT)
public class TextureManager implements ResourceReloader, TextureTickListener, AutoCloseable {
	private static final Logger LOGGER = LogUtils.getLogger();
	public static final Identifier MISSING_IDENTIFIER = Identifier.ofVanilla("");
	private final Map<Identifier, AbstractTexture> textures = new HashMap();
	private final Set<TextureTickListener> tickListeners = new HashSet();
	private final ResourceManager resourceContainer;

	public TextureManager(ResourceManager resourceManager) {
		this.resourceContainer = resourceManager;
		NativeImage nativeImage = MissingSprite.createImage();
		this.registerTexture(MissingSprite.getMissingSpriteId(), new NativeImageBackedTexture(nativeImage));
	}

	public void registerTexture(Identifier id, ReloadableTexture texture) {
		try {
			texture.reload(this.loadTexture(id, texture));
		} catch (Throwable var6) {
			CrashReport crashReport = CrashReport.create(var6, "Uploading texture");
			CrashReportSection crashReportSection = crashReport.addElement("Uploaded texture");
			crashReportSection.add("Resource location", texture.getId());
			crashReportSection.add("Texture id", id);
			throw new CrashException(crashReport);
		}

		this.registerTexture(id, (AbstractTexture)texture);
	}

	private TextureContents loadTexture(Identifier id, ReloadableTexture texture) {
		try {
			return loadTexture(this.resourceContainer, id, texture);
		} catch (Exception var4) {
			LOGGER.error("Failed to load texture {} into slot {}", texture.getId(), id, var4);
			return TextureContents.createMissing();
		}
	}

	public void registerTexture(Identifier id) {
		this.registerTexture(id, (AbstractTexture)(new ResourceTexture(id)));
	}

	public void registerTexture(Identifier id, AbstractTexture texture) {
		AbstractTexture abstractTexture = (AbstractTexture)this.textures.put(id, texture);
		if (abstractTexture != texture) {
			if (abstractTexture != null) {
				this.closeTexture(id, abstractTexture);
			}

			if (texture instanceof TextureTickListener textureTickListener) {
				this.tickListeners.add(textureTickListener);
			}
		}
	}

	private void closeTexture(Identifier id, AbstractTexture texture) {
		this.tickListeners.remove(texture);

		try {
			texture.close();
		} catch (Exception var4) {
			LOGGER.warn("Failed to close texture {}", id, var4);
		}

		texture.clearGlId();
	}

	public AbstractTexture getTexture(Identifier id) {
		AbstractTexture abstractTexture = (AbstractTexture)this.textures.get(id);
		if (abstractTexture != null) {
			return abstractTexture;
		} else {
			ResourceTexture resourceTexture = new ResourceTexture(id);
			this.registerTexture(id, (ReloadableTexture)resourceTexture);
			return resourceTexture;
		}
	}

	@Override
	public void tick() {
		for (TextureTickListener textureTickListener : this.tickListeners) {
			textureTickListener.tick();
		}
	}

	public void destroyTexture(Identifier id) {
		AbstractTexture abstractTexture = (AbstractTexture)this.textures.remove(id);
		if (abstractTexture != null) {
			this.closeTexture(id, abstractTexture);
		}
	}

	public void close() {
		this.textures.forEach(this::closeTexture);
		this.textures.clear();
		this.tickListeners.clear();
	}

	@Override
	public CompletableFuture<Void> reload(ResourceReloader.Synchronizer synchronizer, ResourceManager manager, Executor prepareExecutor, Executor applyExecutor) {
		List<TextureManager.ReloadedTexture> list = new ArrayList();
		this.textures.forEach((id, texture) -> {
			if (texture instanceof ReloadableTexture reloadableTexture) {
				list.add(reloadTexture(manager, id, reloadableTexture, prepareExecutor));
			}
		});
		return CompletableFuture.allOf((CompletableFuture[])list.stream().map(TextureManager.ReloadedTexture::newContents).toArray(CompletableFuture[]::new))
			.thenCompose(synchronizer::whenPrepared)
			.thenAcceptAsync(void_ -> {
				BuyRealmsScreen.refreshImages(this.resourceContainer);

				for (TextureManager.ReloadedTexture reloadedTexture : list) {
					reloadedTexture.texture.reload((TextureContents)reloadedTexture.newContents.join());
				}
			}, applyExecutor);
	}

	public void dumpDynamicTextures(Path path) {
		if (!RenderSystem.isOnRenderThread()) {
			RenderSystem.recordRenderCall(() -> this.dumpDynamicTexturesInternal(path));
		} else {
			this.dumpDynamicTexturesInternal(path);
		}
	}

	private void dumpDynamicTexturesInternal(Path path) {
		try {
			Files.createDirectories(path);
		} catch (IOException var3) {
			LOGGER.error("Failed to create directory {}", path, var3);
			return;
		}

		this.textures.forEach((id, texture) -> {
			if (texture instanceof DynamicTexture dynamicTexture) {
				try {
					dynamicTexture.save(id, path);
				} catch (IOException var5) {
					LOGGER.error("Failed to dump texture {}", id, var5);
				}
			}
		});
	}

	private static TextureContents loadTexture(ResourceManager resourceManager, Identifier textureId, ReloadableTexture texture) throws IOException {
		try {
			return texture.loadContents(resourceManager);
		} catch (FileNotFoundException var4) {
			if (textureId != MISSING_IDENTIFIER) {
				LOGGER.warn("Missing resource {} referenced from {}", texture.getId(), textureId);
			}

			return TextureContents.createMissing();
		}
	}

	private static TextureManager.ReloadedTexture reloadTexture(
		ResourceManager resourceManager, Identifier textureId, ReloadableTexture texture, Executor prepareExecutor
	) {
		return new TextureManager.ReloadedTexture(texture, CompletableFuture.supplyAsync(() -> {
			try {
				return loadTexture(resourceManager, textureId, texture);
			} catch (IOException var4) {
				throw new UncheckedIOException(var4);
			}
		}, prepareExecutor));
	}

	@Environment(EnvType.CLIENT)
	record ReloadedTexture(ReloadableTexture texture, CompletableFuture<TextureContents> newContents) {
	}
}
