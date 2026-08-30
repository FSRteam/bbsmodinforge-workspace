package mchorse.bbs_together.mixin.client;

import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.graphs.IUIKeyframeGraph;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_together.TogetherKeyframeStyle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = IUIKeyframeGraph.class, remap = false)
public interface IUIKeyframeGraphMixin
{
    @Inject(
        method = "addKeyframeManually(Lmchorse/bbs_mod/ui/framework/elements/input/keyframes/UIKeyframeSheet;FLjava/lang/Object;)Lmchorse/bbs_mod/utils/keyframes/Keyframe;",
        at = @At("RETURN"),
        remap = false
    )
    private void bbs_together$applyDefaultStyle(
        UIKeyframeSheet sheet,
        float tick,
        Object value,
        CallbackInfoReturnable<Keyframe<?>> cir
    )
    {
        TogetherKeyframeStyle.apply(cir.getReturnValue());
    }
}
