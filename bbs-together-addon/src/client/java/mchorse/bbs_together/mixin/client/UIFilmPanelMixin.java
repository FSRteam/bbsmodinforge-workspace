package mchorse.bbs_together.mixin.client;

import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_together.TogetherClientNetwork;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

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
