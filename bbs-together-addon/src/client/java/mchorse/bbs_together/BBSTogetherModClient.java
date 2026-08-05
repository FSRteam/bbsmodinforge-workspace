package mchorse.bbs_together;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.api.client.BBSClientApi;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import mchorse.bbs_mod.ui.framework.UIScreen;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Client-only bootstrap, loaded reflectively behind the physical-side guard. */
public final class BBSTogetherModClient
{
    private static boolean initialized;
    private static boolean colorCallbackRegistered;

    private BBSTogetherModClient()
    {}

    public static synchronized void initialize()
    {
        if (initialized)
        {
            return;
        }

        initialized = true;
        BBSMod.registerAddon(BBSTogetherMod.MOD_ID + "_client", TogetherClientAddon::new);
        TogetherSettingUIs.register();
        TogetherClientNetwork.register();
        BBSClientApi.onClientStarted(BBSTogetherModClient::onClientStarted);
        BBSClientApi.onDisconnect(client -> TogetherClientNetwork.reset());
        BBSClientApi.onClientStopping(client -> TogetherClientNetwork.reset());
        BBSClientApi.onEndClientTick(client -> TogetherClientNetwork.expireTransfers());
        BBSClientApi.onHudRender(BBSTogetherModClient::renderHud);
        NeoForge.EVENT_BUS.addListener(BBSTogetherModClient::onLoggingIn);
        BBSTogetherMod.LOGGER.info("BBS Together client bridge registered");
    }

    private static synchronized void onClientStarted(Minecraft client)
    {
        TogetherClientAddon.activateTranslations();
        BBSModClient.getL10n().reload();

        if (!colorCallbackRegistered && BBSSettings.primaryColor != null)
        {
            BBSSettings.primaryColor.postCallback((value, flag) -> TogetherClientNetwork.sendPlayerColor(BBSSettings.primaryColor.get()));
            colorCallbackRegistered = true;
        }
    }

    private static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event)
    {
        TogetherClientNetwork.onConnected();
    }

    private static void renderHud(GuiGraphics graphics, float tickDelta)
    {
        Minecraft client = Minecraft.getInstance();
        if (!TogetherClientNetwork.isConnected() || !TogetherClientNetwork.damageControlActive
            || UIScreen.getCurrentMenu() != null || client.player == null)
        {
            return;
        }

        int padding = 8;
        int x = padding;
        int y = client.getWindow().getGuiScaledHeight() - Icons.LOCKED.h - padding;
        Batcher2D batcher = new Batcher2D(graphics);
        batcher.box(x - 3, y - 3, x + Icons.LOCKED.w + 3, y + Icons.LOCKED.h + 3, 0x55000000);
        batcher.icon(Icons.LOCKED, -1, x, y);
        batcher.flush();
    }
}
