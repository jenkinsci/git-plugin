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
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * A class which allows Git Plugin to choose a git implementation by estimating the size of a repository from a distance
 * without requiring a local checkout.
 */
public class GitToolChooser {

    private long sizeOfRepo = 0L;
    private String implementation;
    private String gitTool;
    /**
     * Size to switch implementation in KiB
     */
    public static final int SIZE_TO_SWITCH = 5000;

    /**
     * Instantiate class using {@link AbstractGitSCMSource}. It looks for a cached .git directory first, calculates the
     * size if it is found else checks if the extension point has been implemented and asks for the size.
     * @param source the {@link AbstractGitSCMSource}
     * @throws IOException
     * @throws InterruptedException
     */
    public GitToolChooser(@NonNull AbstractGitSCMSource source) throws IOException, InterruptedException {
        boolean useCache = false;

        implementation = "NONE";
        useCache = decideAndUseCache(source);

        if (useCache) {
            implementation = determineSwitchOnSize(sizeOfRepo);
        } else {
            decideAndUseAPI(source.getRemote());
        }
        determineGitTool(implementation);
    }

    /**
     * Estimate size of a repository using the extension point
     * @param remoteName: The URL of the repository
     */
    public GitToolChooser(String remoteName) {
        implementation = determineSwitchOnSize(sizeOfRepo);
        decideAndUseAPI(remoteName);
        determineGitTool(implementation);
    }

    /**
     * Determine and estimate the size of a .git cached directory
     * @param source: Use a {@link AbstractGitSCMSource} to access a cached Jenkins directory, we do not lock it.
     * @return useCache
     * @throws IOException
     * @throws InterruptedException
     */
    private boolean decideAndUseCache(@NonNull AbstractGitSCMSource source) throws IOException, InterruptedException {
        boolean useCache = false;
        String cacheEntry = source.getCacheEntry();
        File cacheDir = AbstractGitSCMSource.getCacheDir(cacheEntry);
        if (cacheDir != null) {
            Git git = Git.with(TaskListener.NULL, new EnvVars(EnvVars.masterEnvVars)).in(cacheDir).using("git");
            GitClient client = git.getClient();
            if (client.hasGitRepo()) {
                sizeOfRepo = FileUtils.sizeOfDirectory(cacheDir);
                sizeOfRepo = (sizeOfRepo/1000); // Conversion from Bytes to Kilo Bytes
                useCache = true;
            }
        }
        return useCache;
    }

    private void decideAndUseAPI(String remoteName) {
        if (setSizeFromAPI(remoteName)) {
            implementation = determineSwitchOnSize(sizeOfRepo);
        }
    }

    /**
     * Check if the desired implementation of extension is present and ask for the size of repository if it does
     * @param repoUrl: The remote name derived from {@link GitSCMSource} object
     * @return boolean useAPI or not.
     */
    private boolean setSizeFromAPI(String repoUrl) {
        List<RepositorySizeAPI> acceptedRepository = Objects.requireNonNull(RepositorySizeAPI.all())
                .stream()
                .filter(r -> r.isApplicableTo(repoUrl))
                .collect(Collectors.toList());

        if (acceptedRepository.size() > 0) {
            try {
                for (RepositorySizeAPI repo: acceptedRepository) {
                    long size = repo.getSizeOfRepository(repoUrl);
                    if (size != 0) { sizeOfRepo = size; }
                }
            } catch (Exception e) {
                LOGGER.log(Level.INFO, "Not using performance improvement from REST API: " + e.getMessage());
                return false;
            }
            return sizeOfRepo != 0; // Check if the size of the repository is zero
        } else {
            return false;
        }
    }

    /**
     * Recommend a git implementation on the basis of the given size of a repository
     * @param sizeOfRepo: Size of a repository (in KiBs)
     * @return a git implementation, "git" or "jgit"
     */
    private String determineSwitchOnSize(Long sizeOfRepo) {
        if (sizeOfRepo != 0L) {
            if (sizeOfRepo >= SIZE_TO_SWITCH) {
                return "git";
            } else {
                return "jgit";
            }
        }
        return "NONE";
    }

    /**
     * For a given recommended git implementation, validate if the installation exists and provide no suggestion if
     * implementation doesn't exist.
     * @param gitImplementation: The recommended git implementation, "git" or "jgit" on the basis of the heuristics.
     */
    private void determineGitTool(String gitImplementation) {
        if (gitImplementation.equals("NONE")) {
            gitTool = "NONE";
            return; // Recommend nothing (GitToolRecommendation = NONE)
        }
        final Jenkins jenkins = Jenkins.get();
        GitTool tool = GitUtils.resolveGitTool(gitImplementation, jenkins, null, TaskListener.NULL);
        if (tool != null) {
            gitTool = tool.getGitExe();
        }
    }

    /**
     * Recommend git tool to be used by the git client
     * @return git implementation recommendation in the form of a string
     */
    public String getGitTool() {
        return gitTool;
    }

    /**
     * Other plugins can estimate the size of repository using this extension point
     * The size is assumed to be in KiBs
     */
    public static abstract class RepositorySizeAPI implements ExtensionPoint {

        public abstract boolean isApplicableTo(String remote);

        public abstract Long getSizeOfRepository(String remote) throws Exception;

        public static ExtensionList<RepositorySizeAPI> all() {
            return Jenkins.get().getExtensionList(RepositorySizeAPI.class);
        }
    }

    private static final Logger LOGGER = Logger.getLogger(GitToolChooser.class.getName());
}
