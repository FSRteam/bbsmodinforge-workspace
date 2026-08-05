package mchorse.bbs_together;

import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.settings.ui.UIValueFactory;
import mchorse.bbs_mod.settings.ui.UIValueMap;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.context.UIInterpolationContextMenu;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.shapes.IKeyframeShapeRenderer;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.shapes.KeyframeShapeRenderers;
import mchorse.bbs_mod.ui.framework.tooltips.InterpolationTooltip;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.interps.IInterp;
import mchorse.bbs_mod.utils.interps.Interpolation;
import mchorse.bbs_mod.utils.interps.Interpolations;
import mchorse.bbs_mod.utils.keyframes.KeyframeShape;
import mchorse.bbs_together.values.ValueKeyframeInterpolation;
import mchorse.bbs_together.values.ValueKeyframeShape;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Client factories for the addon's shape and interpolation settings. */
public final class TogetherSettingUIs
{
    private static boolean registered;

    private TogetherSettingUIs()
    {}

    public static synchronized void register()
    {
        if (registered)
        {
            return;
        }

        registered = true;
        UIValueMap.register(ValueKeyframeShape.class, (value, ui) ->
        {
            UIIcon button = new UIIcon(
                () -> shapeIcon(value.get()),
                b -> b.getContext().replaceContextMenu(menu ->
                {
                    KeyframeShape current = shapeAt(value.get());

                    for (KeyframeShape shape : KeyframeShape.values())
                    {
                        IKeyframeShapeRenderer renderer = KeyframeShapeRenderers.SHAPES.get(shape);

                        if (renderer != null)
                        {
                            menu.action(renderer.getIcon(), renderer.getLabel(), shape == current, () -> value.set(shape.ordinal()));
                        }
                    }
                })
            );
            button.tooltip(shapeLabel(value.get()));

            return Arrays.asList(UIValueFactory.column(button, value));
        });
        UIValueMap.register(ValueKeyframeInterpolation.class, (value, ui) ->
        {
            UIIcon button = new UIIcon(
                () -> interpolationIcon(interpolationAt(value.get())),
                b ->
                {
                    Interpolation interpolation = new Interpolation("bbs_together_default_interpolation", Interpolations.MAP, interpolationAt(value.get()));

                    b.getContext().replaceContextMenu(new UIInterpolationContextMenu(interpolation)
                        .callback(() -> value.set(interpolationIndex(interpolation.getInterp()))));
                }
            );
            button.tooltip(new InterpolationTooltip(0F, 0.5F, () -> interpolationAt(value.get())));

            return Arrays.asList(UIValueFactory.column(button, value));
        });
    }

    private static KeyframeShape shapeAt(int ordinal)
    {
        KeyframeShape[] values = KeyframeShape.values();

        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : KeyframeShape.SQUARE;
    }

    private static Icon shapeIcon(int ordinal)
    {
        IKeyframeShapeRenderer renderer = KeyframeShapeRenderers.SHAPES.get(shapeAt(ordinal));

        return renderer == null ? Icons.SHAPES : renderer.getIcon();
    }

    private static IKey shapeLabel(int ordinal)
    {
        IKeyframeShapeRenderer renderer = KeyframeShapeRenderers.SHAPES.get(shapeAt(ordinal));

        return renderer == null ? UIKeys.KEYFRAMES_SHAPES_SQUARE : renderer.getLabel();
    }

    private static IInterp interpolationAt(int index)
    {
        List<IInterp> values = interpolations();

        return index >= 0 && index < values.size() ? values.get(index) : Interpolations.LINEAR;
    }

    private static Icon interpolationIcon(IInterp interpolation)
    {
        return UIInterpolationContextMenu.INTERP_ICON_MAP.getOrDefault(interpolation, Icons.INTERP_LINEAR);
    }

    private static int interpolationIndex(IInterp interpolation)
    {
        int index = interpolations().indexOf(interpolation);

        return Math.max(0, index);
    }

    private static List<IInterp> interpolations()
    {
        return new ArrayList<>(Interpolations.MAP.values());
    }
}
