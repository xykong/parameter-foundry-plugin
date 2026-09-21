package io.jenkins.plugins.unifiedparameters.retirement;

import java.util.List;

/** Every retirement finding plus the repository-side blind spot of the same scan. */
public record RetirementReport(List<RetirementFinding> findings, RepositoryScan repositoryScan) {

    public RetirementReport {
        findings = List.copyOf(findings);
    }

    public List<RetirementFinding> getFindings() {
        return findings;
    }

    public RepositoryScan getRepositoryScan() {
        return repositoryScan;
    }
}
