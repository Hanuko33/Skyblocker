package de.hysky.skyblocker.config;

import com.google.gson.FieldNamingPolicy;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import de.hysky.skyblocker.SkyblockerMod;
import de.hysky.skyblocker.config.categories.*;
import de.hysky.skyblocker.debug.Debug;
import de.hysky.skyblocker.mixins.accessors.HandledScreenAccessor;
import de.hysky.skyblocker.utils.scheduler.Scheduler;
import dev.isxander.yacl3.api.YetAnotherConfigLib;
import dev.isxander.yacl3.config.v2.api.ConfigClassHandler;
import dev.isxander.yacl3.config.v2.api.serializer.GsonConfigSerializerBuilder;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.lang.StackWalker.Option;
import java.nio.file.Path;
import java.util.function.Consumer;

import org.apache.commons.lang3.function.Consumers;

public class SkyblockerConfigManager {
    public static final int CONFIG_VERSION = 4;
    private static final Path CONFIG_FILE = FabricLoader.getInstance().getConfigDir().resolve("skyblocker.json");
    private static final ConfigClassHandler<SkyblockerConfig> HANDLER = ConfigClassHandler.createBuilder(SkyblockerConfig.class)
            .serializer(config -> GsonConfigSerializerBuilder.create(config)
                    .setPath(CONFIG_FILE)
                    .setJson5(false)
                    .appendGsonBuilder(builder -> builder
                            .setFieldNamingPolicy(FieldNamingPolicy.IDENTITY)
                            .registerTypeHierarchyAdapter(Identifier.class, new CodecTypeAdapter<>(Identifier.CODEC)))
                    .build())
            .build();

    public static SkyblockerConfig get() {
        return HANDLER.instance();
    }

    /**
     * This method is caller sensitive and can only be called by the mod initializer,
     * this is enforced.
     */
    public static void init() {
        if (StackWalker.getInstance(Option.RETAIN_CLASS_REFERENCE).getCallerClass() != SkyblockerMod.class) {
            throw new RuntimeException("Skyblocker: Called config init from an illegal place!");
        }

        HANDLER.load();
        ClientCommandRegistrationCallback.EVENT.register(((dispatcher, registryAccess) -> dispatcher.register(ClientCommandManager.literal(SkyblockerMod.NAMESPACE).then(optionsLiteral("config")).then(optionsLiteral("options")))));
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (screen instanceof GenericContainerScreen genericContainerScreen && screen.getTitle().getString().equals("SkyBlock Menu")) {
                Screens.getButtons(screen).add(ButtonWidget
                        .builder(Text.literal("\uD83D\uDD27"), buttonWidget -> client.setScreen(createGUI(screen)))
                        .dimensions(((HandledScreenAccessor) genericContainerScreen).getX() + ((HandledScreenAccessor) genericContainerScreen).getBackgroundWidth() - 16, ((HandledScreenAccessor) genericContainerScreen).getY() + 4, 12, 12)
                        .tooltip(Tooltip.of(Text.translatable("skyblocker.config.title")))
                        .build());
            }
        });
    }

    @Deprecated(since = "1.21.5", forRemoval = true)
    public static void save() {
        update(Consumers.nop());
    }

    /**
     * Executes the given {@code action} to update fields in the config, then saves the changes.
     */
    public static void update(Consumer<SkyblockerConfig> action) {
    	action.accept(get());
    	HANDLER.save();
    }

    public static Screen createGUI(Screen parent) {
        return YetAnotherConfigLib.create(HANDLER, (defaults, config, builder) -> {
            builder.title(Text.translatable("skyblocker.config.title"))
                    .category(GeneralCategory.create(defaults, config))
                    .category(UIAndVisualsCategory.create(defaults, config))
                    .category(HelperCategory.create(defaults, config))
                    .category(DungeonsCategory.create(defaults, config))
                    .category(ForagingCategory.create(defaults, config))
                    .category(CrimsonIsleCategory.create(defaults, config))
                    .category(MiningCategory.create(defaults, config))
                    .category(FarmingCategory.create(defaults, config))
                    .category(HuntingCategory.create(defaults, config))
                    .category(OtherLocationsCategory.create(defaults, config))
                    .category(SlayersCategory.create(defaults, config))
                    .category(ChatCategory.create(defaults, config))
                    .category(QuickNavigationCategory.create(defaults, config))
                    .category(EventNotificationsCategory.create(defaults, config))
                    .category(MiscCategory.create(defaults, config));
            if (Debug.debugEnabled()) {
                builder.category(DebugCategory.create(defaults, config));
            }
            return builder;
        }).generateScreen(parent);
    }

    /**
     * Registers an options command with the given name. Used for registering both options and config as valid commands.
     *
     * @param name the name of the command node
     * @return the command builder
     */
    private static LiteralArgumentBuilder<FabricClientCommandSource> optionsLiteral(String name) {
        // Don't immediately open the next screen as it will be closed by ChatScreen right after this command is executed
        return ClientCommandManager.literal(name).executes(Scheduler.queueOpenScreenCommand(() -> createGUI(null)));
    }
}
