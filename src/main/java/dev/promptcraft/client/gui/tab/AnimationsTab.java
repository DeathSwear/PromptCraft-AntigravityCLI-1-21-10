package dev.promptcraft.client.gui.tab;

import dev.promptcraft.client.gui.Layout;
import dev.promptcraft.client.gui.SettingsContext;
import dev.promptcraft.client.gui.widget.FlatButton;
import net.minecraft.text.Text;

public final class AnimationsTab extends AbstractSettingsTab {

    private FlatButton previewButton;

    public AnimationsTab(SettingsContext ctx, Layout layout) {
        super(ctx, layout);
    }

    @Override
    public String title() {
        return t("Animations", "Анимации");
    }

    @Override
    public void init() {
        previewButton = add(new FlatButton(ctx, layout.contentX() - 5, layout.contentY() + 5, 190, 20,
                Text.literal(previewLabel()),
                b -> {
                    state().toggleShowPreview();
                    b.setMessage(Text.literal(previewLabel()));
                }));
    }

    private String previewLabel() {
        return t("Dynamic Preview: ", "Динамический предпросмотр: ")
                + (state().showPreview() ? t("ON", "ВКЛ") : t("OFF", "ВЫКЛ"));
    }

    @Override
    public void refreshLabels() {
        if (previewButton != null) {
            previewButton.setMessage(Text.literal(previewLabel()));
        }
    }
}