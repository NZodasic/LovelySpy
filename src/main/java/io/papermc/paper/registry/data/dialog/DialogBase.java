package io.papermc.paper.registry.data.dialog;

import net.kyori.adventure.text.Component;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import java.util.List;

public interface DialogBase {
    static Builder builder(Component component) {
        return null;
    }

    interface Builder {
        Builder body(List<DialogBody> body);
        Builder inputs(List<DialogInput> inputs);
        DialogBase build();
    }
}
