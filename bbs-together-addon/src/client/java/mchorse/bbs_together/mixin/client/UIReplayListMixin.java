package mchorse.bbs_together.mixin.client;

import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.settings.values.core.ValueForm;
import mchorse.bbs_mod.ui.film.replays.UIReplayList;
import mchorse.bbs_mod.ui.forms.UIFormPalette;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.events.UIRemovedEvent;
import mchorse.bbs_together.TogetherClientNetwork;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.function.Consumer;

@Mixin(value = UIReplayList.class, remap = false)
public abstract class UIReplayListMixin
{
    @Unique
    private String bbs_together$lockDocument;
    @Unique
    private int bbs_together$lockReplayIndex = -1;

    @Inject(
        method = "openFormEditor(Lmchorse/bbs_mod/settings/values/core/ValueForm;ZLjava/util/function/Consumer;)V",
        at = @At("HEAD"),
        cancellable = true,
        remap = false
    )
    private void bbs_together$acquireReplayLock(
        ValueForm form,
        boolean editing,
        Consumer<Form> consumer,
        CallbackInfo ci
    )
    {
        this.bbs_together$lockDocument = null;
        this.bbs_together$lockReplayIndex = -1;
        UIReplayList self = (UIReplayList) (Object) this;
        Film film = self.panel == null ? null : self.panel.getData();
        if (!TogetherClientNetwork.isConnected() || film == null)
        {
            return;
        }

        List<Replay> replays = film.replays.getList();
        int replayIndex = -1;
        for (int i = 0; i < replays.size(); i++)
        {
            if (replays.get(i).form == form)
            {
                replayIndex = i;
                break;
            }
        }
        if (replayIndex < 0)
        {
            return;
        }
        if (TogetherClientNetwork.isReplayFormLocked(film.getId(), replayIndex))
        {
            ci.cancel();
            return;
        }

        this.bbs_together$lockDocument = film.getId();
        this.bbs_together$lockReplayIndex = replayIndex;
        TogetherClientNetwork.sendReplayFormLock(film.getId(), replayIndex, true);
    }

    @Redirect(
        method = "openFormEditor(Lmchorse/bbs_mod/settings/values/core/ValueForm;ZLjava/util/function/Consumer;)V",
        at = @At(
            value = "INVOKE",
            target = "Lmchorse/bbs_mod/ui/forms/UIFormPalette;open(Lmchorse/bbs_mod/ui/framework/elements/UIElement;ZLmchorse/bbs_mod/forms/forms/Form;Ljava/util/function/Consumer;)Lmchorse/bbs_mod/ui/forms/UIFormPalette;"
        ),
        remap = false
    )
    private UIFormPalette bbs_together$releaseReplayLockOnClose(
        UIElement target,
        boolean editing,
        Form form,
        Consumer<Form> consumer
    )
    {
        UIFormPalette palette;
        String document = this.bbs_together$lockDocument;
        int replayIndex = this.bbs_together$lockReplayIndex;
        this.bbs_together$lockDocument = null;
        this.bbs_together$lockReplayIndex = -1;

        try
        {
            palette = UIFormPalette.open(target, editing, form, consumer);
        }
        catch (RuntimeException exception)
        {
            this.bbs_together$release(document, replayIndex);
            throw exception;
        }

        if (palette == null)
        {
            this.bbs_together$release(document, replayIndex);
        }
        else if (document != null && replayIndex >= 0)
        {
            palette.getEvents().register(UIRemovedEvent.class, event -> this.bbs_together$release(document, replayIndex));
        }

        return palette;
    }

    @Unique
    private void bbs_together$release(String document, int replayIndex)
    {
        if (document != null && replayIndex >= 0)
        {
            TogetherClientNetwork.sendReplayFormLock(document, replayIndex, false);
        }
    }
}
