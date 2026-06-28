package io.papermc.paper.registry.data.dialog.action;

import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.audience.Audience;
import io.papermc.paper.dialog.DialogResponseView;
import java.util.function.BiConsumer;

public interface DialogAction {
    static DialogAction customClick(BiConsumer<DialogResponseView, Audience> consumer, ClickCallback.Options options) {
        return null;
    }
}
