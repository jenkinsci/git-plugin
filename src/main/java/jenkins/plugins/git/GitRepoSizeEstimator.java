package jenkins.plugins.git;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.EnvVars;
import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.model.TaskListener;
import hudson.plugins.git.GitTool;
import hudson.plugins.git.util.GitUtils;
import jenkins.model.Jenkins;
import org.apache.commons.io.FileUtils;
import org.jenkinsci.plugins.gitclient.Git;
import org.jenkinsci.plugins.gitclient.GitClient;

import java.io.File;
import java.io.IOException;
import java.util.Objects;

public class GitRepoSizeEstimator {

    private long sizeOfRepo = 0L;
    private String implementation;
    private String gitTool;
    public static final int SIZE_TO_SWITCH = 5000;

    public GitRepoSizeEstimator(@NonNull AbstractGitSCMSource source) throws IOException, InterruptedException {
        boolean useCache;
        boolean useAPI = false;

        useCache = determineCacheEstimation(source);

        if (useCache) {
            implementation = determineSwitchOnSize(sizeOfRepo);
        } else {
            useAPI = getSizeFromAPI(source.getRemote());
        }

        if (useAPI) {
            implementation = determineSwitchOnSize(sizeOfRepo);
        }

        if (!useAPI && !useCache) {
            implementation = "DEFAULT";
        }
        determineGitTool(implementation);
    }

    public GitRepoSizeEstimator(String remoteName) {
        boolean useAPI = getSizeFromAPI(remoteName);

        if (useAPI) {
            implementation = determineSwitchOnSize(sizeOfRepo);
        } else {
            implementation = "DEFAULT";
        }
        determineGitTool(implementation);
    }

    private void determineGitTool(String gitImplementation) {
        if (implementation.equals("DEFAULT")) {
            gitTool = null;
        }
        final Jenkins jenkins = Jenkins.get();
        GitTool tool = GitUtils.resolveGitTool(implementation, jenkins, null, TaskListener.NULL);
        if (tool != null) {
            gitTool = tool.getGitExe();
        }
    }

    private boolean determineCacheEstimation(@NonNull AbstractGitSCMSource source) throws IOException, InterruptedException {
        boolean useCache;
        String cacheEntry = source.getCacheEntry();
        File cacheDir = AbstractGitSCMSource.getCacheDir(cacheEntry);
        if (cacheDir != null) {
            Git git = Git.with(TaskListener.NULL, new EnvVars(EnvVars.masterEnvVars)).in(cacheDir).using("git");
            GitClient client = git.getClient();
            if (!client.hasGitRepo()) {
                useCache = false;
            } else {
                useCache = true;
                sizeOfRepo = FileUtils.sizeOfDirectory(cacheDir);
            }
        } else {
            useCache = false;
        }
        return useCache;
    }

    private String determineSwitchOnSize(Long sizeOfRepo) {
        if (sizeOfRepo != 0L) {
            if (sizeOfRepo >= SIZE_TO_SWITCH) {
                return "git";
            } else {
                return "jgit";
            }
        }
        return "DEFAULT";
    }

    // Second option: Determine size of repository using public APIs provided by Github, GitLab etc.
    public static abstract class RepositorySizeAPI implements ExtensionPoint {

        public abstract Long getSizeOfRepository(String remote);

        public static ExtensionList<RepositorySizeAPI> all() {
            Jenkins jenkins = Jenkins.getInstanceOrNull();
            if (jenkins == null) {
                return null;
            }
            return jenkins.getExtensionList(RepositorySizeAPI.class);
        }
    }

    private boolean getSizeFromAPI(String repoUrl) {
        for (RepositorySizeAPI r: Objects.requireNonNull(RepositorySizeAPI.all())) {
            if (r != null) {
                sizeOfRepo = r.getSizeOfRepository(repoUrl);
                return true;
            }
        }
        return false;
    }

    public String getGitTool() {
        return gitTool;
    }
}