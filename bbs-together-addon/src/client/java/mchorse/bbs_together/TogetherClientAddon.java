package mchorse.bbs_together;

import mchorse.bbs_mod.events.BBSAddonMod;
import mchorse.bbs_mod.events.Subscribe;
import mchorse.bbs_mod.events.register.RegisterL10nEvent;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.resources.Link;

/** Small API 1.x event bridge for the client-only L10n event. */
public final class TogetherClientAddon implements BBSAddonMod
{
    private static L10n pending;
    private static L10n registered;

    public static synchronized void activateTranslations()
    {
        if (pending == null || pending == registered)
        {
            return;
        }

        pending.registerOne(language -> new Link(BBSTogetherMod.MOD_ID, "strings/" + language + ".json"));
        registered = pending;
    }

    @Subscribe
    public void onRegisterL10n(RegisterL10nEvent event)
    {
        pending = event.l10n;
    }
}
