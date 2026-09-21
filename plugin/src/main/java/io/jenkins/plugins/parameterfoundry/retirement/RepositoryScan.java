package io.jenkins.plugins.parameterfoundry.retirement;

import java.util.List;

/**
 * Jobs whose parameter definitions live in a repository Jenkinsfile, plus the multibranch and
 * organization folders that generate them. The retirement dashboard cannot see those definitions,
 * so their source plugins must stay installed until the repository side is reviewed too.
 */
public record RepositoryScan(
        List<String> pipelineFromScmJobs, List<String> multibranchProjects, int materializedJobs, boolean unscanned) {

    public RepositoryScan {
        pipelineFromScmJobs = List.copyOf(pipelineFromScmJobs);
        multibranchProjects = List.copyOf(multibranchProjects);
    }

    public List<String> getPipelineFromScmJobs() {
        return pipelineFromScmJobs;
    }

    public List<String> getMultibranchProjects() {
        return multibranchProjects;
    }

    public int getMaterializedJobs() {
        return materializedJobs;
    }

    public boolean isUnscanned() {
        return unscanned;
    }

    public int getUnscannedJobCount() {
        return pipelineFromScmJobs.size();
    }

    public int getMultibranchProjectCount() {
        return multibranchProjects.size();
    }
}
