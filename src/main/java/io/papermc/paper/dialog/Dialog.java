package io.papermc.paper.dialog;

import java.util.function.Consumer;

public interface Dialog {
    static Dialog create(Consumer<Builder> consumer) {
        return null;
    }

    interface Builder {
        Builder empty();
        Builder base(io.papermc.paper.registry.data.dialog.DialogBase base);
        Builder type(io.papermc.paper.registry.data.dialog.type.DialogType type);
        Dialog build();
    }
}
