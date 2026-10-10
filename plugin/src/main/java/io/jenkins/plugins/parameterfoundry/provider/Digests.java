package io.jenkins.plugins.parameterfoundry.provider;

import io.jenkins.plugins.parameterfoundry.model.Option;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import net.sf.json.JSONArray;
import net.sf.json.JSONNull;

public final class Digests {

    private Digests() {}

    public static String sha256(String value) {
        try {
            return "sha256:"
                    + HexFormat.of()
                            .formatHex(MessageDigest.getInstance("SHA-256")
                                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    public static String dependencies(Map<String, String> values) {
        StringBuilder encoded = new StringBuilder();
        values.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> encoded.append(entry.getKey())
                .append('\u0000')
                .append(entry.getValue())
                .append('\u0001'));
        return sha256(encoded.toString());
    }

    public static String options(List<Option> options) {
        if (options.stream().anyMatch(option -> option.getSourceChoiceMetadata() != null)) {
            JSONArray encoded = new JSONArray();
            for (Option option : options) {
                JSONArray fields = new JSONArray();
                fields.add(option.getValue());
                fields.add(option.getLabel());
                fields.add(option.getDescription());
                fields.add(option.getGroup());
                fields.add(option.isDisabled());
                fields.add(option.getDisabledReason());
                fields.add(JSONArray.fromObject(option.getTags()));
                if (option.getSourceChoiceMetadata() == null) {
                    fields.add(JSONNull.getInstance());
                } else {
                    JSONArray source = new JSONArray();
                    source.add(option.getSourceChoiceMetadata().selected());
                    source.add(option.getSourceChoiceMetadata().apiSelected());
                    source.add(option.getSourceChoiceMetadata().renderFailure());
                    source.add(option.getSourceChoiceMetadata().apiValue());
                    source.add(option.getSourceChoiceMetadata().selectedApiValue());
                    fields.add(source);
                }
                encoded.add(fields);
            }
            return sha256("source-choice-options-v1\u0000" + encoded);
        }
        StringBuilder encoded = new StringBuilder();
        for (Option option : options) {
            encoded.append(option.getValue())
                    .append('\u0000')
                    .append(option.getLabel())
                    .append('\u0000')
                    .append(option.getDescription())
                    .append('\u0000')
                    .append(option.getGroup())
                    .append('\u0000')
                    .append(option.isDisabled())
                    .append('\u0000')
                    .append(option.getDisabledReason())
                    .append('\u0000')
                    .append(option.getTags())
                    .append('\u0001');
        }
        return sha256(encoded.toString());
    }

    public static String axis(List<io.jenkins.plugins.parameterfoundry.model.AxisOption> axis) {
        StringBuilder encoded = new StringBuilder();
        if (axis != null) {
            for (io.jenkins.plugins.parameterfoundry.model.AxisOption option : axis) {
                encoded.append(option.getValue())
                        .append('\u0000')
                        .append(option.getLabel())
                        .append('\u0001');
            }
        }
        return sha256(encoded.toString());
    }
}
