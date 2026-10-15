package io.jenkins.plugins.parameterfoundry.provider;

import java.net.URI;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Finite administrator-declared destinations; never inferred from an upstream Location. */
final class RawRedirectTargets {
    private final List<URI> targets;
    private final Set<String> exact;

    private RawRedirectTargets(List<URI> targets) {
        this.targets = List.copyOf(targets);
        var values = new HashSet<String>();
        for (URI target : targets) {
            if (!values.add(target.toASCIIString())) throw new IllegalArgumentException();
        }
        exact = Set.copyOf(values);
    }

    static RawRedirectTargets freeze(List<RestRawLiteral> declarations) {
        if (declarations.size() > 64) throw new IllegalArgumentException();
        return new RawRedirectTargets(
                declarations.stream().map(row -> checkedUri(row.getValue())).toList());
    }

    static URI checkedUri(String value) {
        if (value == null || value.isEmpty() || value.length() > 8192) throw new IllegalArgumentException();
        URI uri = URI.create(value);
        if (!("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()))
                || uri.getHost() == null
                || uri.getRawUserInfo() != null
                || uri.getRawFragment() != null
                || !value.equals(uri.toASCIIString())
                || !uri.equals(uri.normalize())
                || uri.getRawPath().contains("\\")
                || uri.getRawPath().matches("(?i).*(%2e|%2f|%5c).*")) {
            throw new IllegalArgumentException();
        }
        return uri;
    }

    void validate(ConnectionProfile profile) {
        for (URI target : targets) profile.validateResolvedUri(target);
    }

    boolean isEmpty() {
        return targets.isEmpty();
    }

    boolean permits(URI target) {
        return exact.contains(target.toASCIIString());
    }

    List<URI> uris() {
        return targets;
    }
}
