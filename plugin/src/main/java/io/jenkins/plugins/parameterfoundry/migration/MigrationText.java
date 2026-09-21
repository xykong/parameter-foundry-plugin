package io.jenkins.plugins.unifiedparameters.migration;

import io.jenkins.plugins.unifiedparameters.Messages;
import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import org.jvnet.localizer.ResourceBundleHolder;

/**
 * A message key plus its arguments. The text is resolved when it is rendered, so a plan built
 * earlier in a request still shows the reader's locale on the migration page.
 *
 * <p>The keys live in the plugin {@code Messages} bundle, so the migration page, the retirement
 * dashboard, and the CLI share one translation set with the rest of the parameter UI.
 */
public final class MigrationText implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final ResourceBundleHolder HOLDER = ResourceBundleHolder.get(Messages.class);

    private final String key;
    private final String literal;
    private final List<String> arguments;

    private MigrationText(String key, String literal, List<String> arguments) {
        this.key = key;
        this.literal = literal;
        this.arguments = arguments;
    }

    /** A message key such as {@code MigrationReasonGitMapped}, plus its arguments. */
    public static MigrationText of(String key, Object... arguments) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("A migration message key is required.");
        }
        List<String> encoded = new ArrayList<>();
        for (Object argument : arguments) {
            encoded.add(argument == null ? "" : String.valueOf(argument));
        }
        return new MigrationText(key, null, List.copyOf(encoded));
    }

    /** Source data that is shown as it is, such as a branch name or a sort mode. */
    public static MigrationText value(String text) {
        return new MigrationText(null, text == null ? "" : text, List.of());
    }

    /** Message key as declared in {@code Messages.properties}. */
    public String getKey() {
        return key;
    }

    public List<String> getArguments() {
        return arguments;
    }

    public String render() {
        if (key == null) {
            return literal;
        }
        return HOLDER.format(key, arguments.toArray());
    }

    @Override
    public String toString() {
        return render();
    }
}
