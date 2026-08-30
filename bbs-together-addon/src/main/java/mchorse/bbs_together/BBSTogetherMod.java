package mchorse.bbs_together;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.api.addon.BBSAddonCapability;
import mchorse.bbs_mod.api.addon.BBSAddonDescriptor;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(BBSTogetherMod.MOD_ID)
public final class BBSTogetherMod
{
    public static final String MOD_ID = "bbs_together";
    public static final String VERSION = "1.6.14-fsr";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    public static final BBSAddonDescriptor DESCRIPTOR = BBSAddonDescriptor.builder(MOD_ID)
        .displayName("BBS Together")
        .addonVersion(VERSION)
        .capability(BBSAddonCapability.SOURCE_PACKS)
        .capability(BBSAddonCapability.SETTINGS)
        .capability(BBSAddonCapability.NETWORK)
        .capability(BBSAddonCapability.CLIENT_UI)
        .capability(BBSAddonCapability.EVENTS)
        .requiredMod("bbs")
        .namespace(MOD_ID)
        .build();

    public BBSTogetherMod(IEventBus modBus)
    {
        BBSMod.registerAddon(DESCRIPTOR, BBSTogetherAddon::new);
        NeoForge.EVENT_BUS.addListener(BBSTogetherMod::registerCommands);
        NeoForge.EVENT_BUS.addListener(BBSTogetherMod::onPlayerLoggedOut);
        NeoForge.EVENT_BUS.addListener(BBSTogetherMod::onServerStopped);
        NeoForge.EVENT_BUS.addListener(BBSTogetherMod::onServerTick);

        if (FMLEnvironment.dist == Dist.CLIENT)
        {
            try
            {
                Class<?> bootstrap = Class.forName("mchorse.bbs_together.BBSTogetherModClient");
                bootstrap.getMethod("initialize").invoke(null);
            }
            catch (ReflectiveOperationException exception)
            {
                LOGGER.error("Unable to initialize the BBS Together client bridge", exception);
            }
        }
    }

    private static void registerCommands(RegisterCommandsEvent event)
    {
        TogetherCommands.register(event.getDispatcher(), event.getBuildContext(), event.getCommandSelection());
    }

    private static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event)
    {
        if (event.getEntity() instanceof ServerPlayer player && player.getServer() != null)
        {
            TogetherServerNetwork.clearPlayerState(player, player.getServer());
        }
    }

    private static void onServerStopped(ServerStoppedEvent event)
    {
        TogetherServerNetwork.reset();
    }

    private static void onServerTick(ServerTickEvent.Post event)
    {
        TogetherServerNetwork.tick(event.getServer());
    }
}
