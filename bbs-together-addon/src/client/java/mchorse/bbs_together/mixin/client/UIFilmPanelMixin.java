package mchorse.bbs_together.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import mchorse.bbs_mod.client.renderer.MorphRenderer;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_together.TogetherClientNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = UIFilmPanel.class, remap = false)
public abstract class UIFilmPanelMixin
{
    @Inject(method = "dupeData", at = @At("RETURN"), remap = false)
    private void bbs_together$filmDuplicated(String name, CallbackInfo ci)
    {
        UIFilmPanel self = (UIFilmPanel) (Object) this;
        if (self.getData() != null && name.equals(self.getData().getId()))
        {
            TogetherClientNetwork.sendFilmListChanged();
        }
    }
}

/** Keeps collaborating players out of the Film preview world render. */
@Mixin(value = MorphRenderer.class, remap = false)
abstract class MorphRendererMixin
{
    @Inject(method = "renderPlayer", at = @At("HEAD"), cancellable = true, remap = false)
    private static void bbs_together$hideCollaboratingPlayer(
        AbstractClientPlayer player,
        float bodyYaw,
        float tickDelta,
        PoseStack pose,
        MultiBufferSource buffers,
        int light,
        CallbackInfoReturnable<Boolean> cir
    )
    {
        if (!MorphRenderer.hidePlayer)
        {
            return;
        }

        Minecraft client = Minecraft.getInstance();
        if (client.player != null && !client.player.getUUID().equals(player.getUUID())
            && TogetherClientNetwork.PLAYERS_IN_FILM_SCREEN.containsKey(player.getUUID()))
        {
            cir.setReturnValue(true);
        }
    }
}
