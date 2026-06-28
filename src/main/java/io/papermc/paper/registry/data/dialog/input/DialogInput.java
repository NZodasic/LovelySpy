package io.papermc.paper.registry.data.dialog.input;

import net.kyori.adventure.text.Component;
import java.util.List;

public interface DialogInput {
    static NumberRangeBuilder numberRange(String key, Component component, float min, float max) {
        return null;
    }

    static TextBuilder text(String key, Component component) {
        return null;
    }

    static SingleOptionBuilder singleOption(String key, Component component, List<SingleOptionDialogInput.OptionEntry> options) {
        return null;
    }

    interface NumberRangeBuilder {
        NumberRangeBuilder step(float step);
        NumberRangeBuilder initial(float initial);
        NumberRangeBuilder width(int width);
        DialogInput build();
    }

    interface TextBuilder {
        TextBuilder initial(String initial);
        TextBuilder width(int width);
        DialogInput build();
    }

    interface SingleOptionBuilder {
        SingleOptionBuilder width(int width);
        DialogInput build();
    }
}
