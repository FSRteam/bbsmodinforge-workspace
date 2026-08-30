package mchorse.bbs_together.mixin.client;

import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.ui.dashboard.panels.UIDataDashboardPanel;
import mchorse.bbs_mod.ui.dashboard.panels.overlay.UIDataOverlayPanel;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_together.TogetherClientNetwork;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = UIDataOverlayPanel.class, remap = false)
public abstract class UIDataOverlayPanelMixin
{
    @Shadow
    protected UIDataDashboardPanel<?> panel;

    @Unique
    private String bbs_together$previousFilmId;

    @Inject(
        method = "addNewData(Ljava/lang/String;Lmchorse/bbs_mod/data/types/MapType;)V",
        at = @At("RETURN"),
        remap = false
    )
    private void bbs_together$filmCreated(String name, MapType data, CallbackInfo ci)
    {
        if (this.bbs_together$isCurrentFilm(name))
        {
            TogetherClientNetwork.sendFilmListChanged();
        }
    }

    @Inject(method = "dupeData", at = @At("RETURN"), remap = false)
    private void bbs_together$filmDuplicated(String name, CallbackInfo ci)
    {
        if (this.bbs_together$isCurrentFilm(name))
        {
            TogetherClientNetwork.sendFilmListChanged();
        }
    }

    @Inject(method = "renameData", at = @At("HEAD"), remap = false)
    private void bbs_together$captureRenamedFilm(String name, CallbackInfo ci)
    {
        this.bbs_together$previousFilmId = this.bbs_together$currentFilmId();
    }

    @Inject(method = "renameData", at = @At("RETURN"), remap = false)
    private void bbs_together$filmRenamed(String name, CallbackInfo ci)
    {
        String previous = this.bbs_together$previousFilmId;
        this.bbs_together$previousFilmId = null;
        if (previous != null && !previous.equals(name) && this.bbs_together$isCurrentFilm(name))
        {
            TogetherClientNetwork.sendFilmRenamed(previous, name);
        }
    }

    @Inject(method = "removeData", at = @At("HEAD"), remap = false)
    private void bbs_together$captureDeletedFilm(CallbackInfo ci)
    {
        this.bbs_together$previousFilmId = this.bbs_together$currentFilmId();
    }

    @Inject(method = "removeData", at = @At("RETURN"), remap = false)
    private void bbs_together$filmDeleted(CallbackInfo ci)
    {
        String previous = this.bbs_together$previousFilmId;
        this.bbs_together$previousFilmId = null;
        if (previous != null && !previous.equals(this.bbs_together$currentFilmId()))
        {
            TogetherClientNetwork.sendFilmDeleted(previous);
        }
    }

    @Unique
    private boolean bbs_together$isCurrentFilm(String id)
    {
        return id != null && id.equals(this.bbs_together$currentFilmId());
    }

    @Unique
    private String bbs_together$currentFilmId()
    {
        if (!(this.panel instanceof UIFilmPanel) || !(this.panel.getData() instanceof Film film))
        {
            return null;
        }

        return film.getId();
    }
}
