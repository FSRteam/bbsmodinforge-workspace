package mchorse.bbs_together.mixin.client;

import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.ui.film.replays.overlays.UIReplaysOverlayPanel;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_together.TogetherClientNetwork;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = UIReplaysOverlayPanel.class, remap = false)
public abstract class UIReplaysOverlayPanelMixin
{
    @Inject(method = "renderBackground", at = @At("RETURN"), remap = false)
    private void bbs_together$applyReplayLock(UIContext context, CallbackInfo ci)
    {
        UIReplaysOverlayPanel self = (UIReplaysOverlayPanel) (Object) this;
        Film film = self.replays == null || self.replays.panel == null ? null : self.replays.panel.getData();
        Replay replay = self.replays == null ? null : self.replays.getSelectedReplayFirst();
        if (film == null || replay == null)
        {
            return;
        }

        int replayIndex = film.replays.getList().indexOf(replay);
        boolean locked = replayIndex >= 0 && TogetherClientNetwork.isReplayFormLocked(film.getId(), replayIndex);
        self.pickEdit.pick.setEnabled(!locked);
        self.pickEdit.edit.setEnabled(!locked && replay.form.get() != null);
    }
}
