package mchorse.bbs_together;

import mchorse.bbs_mod.api.addon.BBSAddon;
import mchorse.bbs_mod.api.addon.BBSAddonDescriptor;
import mchorse.bbs_mod.api.addon.BBSAddonRegistrationContext;
import mchorse.bbs_mod.resources.packs.InternalAssetsSourcePack;
import mchorse.bbs_mod.ui.utils.icons.Icons;

/** Common API 2.0 registration object. */
public final class BBSTogetherAddon implements BBSAddon
{
    @Override
    public BBSAddonDescriptor descriptor()
    {
        return BBSTogetherMod.DESCRIPTOR;
    }

    @Override
    public void register(BBSAddonRegistrationContext context)
    {
        context.settings().register(Icons.GLOBE, BBSTogetherMod.MOD_ID, TogetherSettings::register);
        context.resources().registerSourcePack(new InternalAssetsSourcePack(
            BBSTogetherMod.MOD_ID,
            BBSTogetherMod.MOD_ID,
            "assets/" + BBSTogetherMod.MOD_ID + "/assets",
            BBSTogetherMod.class
        ));
        TogetherServerNetwork.register(context.network());
        context.diagnostics().info("BBS Together 1.6.14-fsr registered through Addon API 2.0");
    }
}
