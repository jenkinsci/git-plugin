package jenkins.plugins.git;

import hudson.EnvVars;
import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.model.Item;
import hudson.model.TaskListener;
import hudson.plugins.git.GitTool;
import hudson.plugins.git.util.GitUtils;
import jenkins.model.Jenkins;
import org.apache.commons.io.FileUtils;
import org.jenkinsci.plugins.gitclient.Git;
import org.jenkinsci.plugins.gitclient.GitClient;
import org.jenkinsci.plugins.gitclient.JGitApacheTool;
import org.jenkinsci.plugins.gitclient.JGitTool;

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
    private static final int SIZE_TO_SWITCH = 5000;
    private boolean JGIT_SUPPORTED = false;

    /**
     * Instantiate class using the remote name. It looks for a cached .git directory first, calculates the
     * size if it is found else checks if the extension point has been implemented and asks for the size.
     * @param remoteName the repository url
     * @param projectContext the context where repository size is being estimated
     * @param credentialsId credential used to access the repository or null if no credential is required
     * @param gitExe name of the git tool ('git', 'jgit', 'jgitapache') to be used as the default tool
     * @param useJGit if true the JGit is allowed as an implementation
     * @throws IOException on error
     * @throws InterruptedException on error
     */
    public GitToolChooser(String remoteName, Item projectContext, String credentialsId, String gitExe, Boolean useJGit) throws IOException, InterruptedException {
        boolean useCache = false;
        if (useJGit != null) {
            JGIT_SUPPORTED = useJGit;
        }

        implementation = "NONE";
        useCache = decideAndUseCache(remoteName);

        if (useCache) {
            implementation = determineSwitchOnSize(sizeOfRepo, gitExe);
        } else {
            decideAndUseAPI(remoteName, projectContext, credentialsId, gitExe);
        }
        determineGitTool(implementation, gitExe);
    }

    /**
     * Determine and estimate the size of a .git cached directory
     * @param remoteName: Use the repository url to access a cached Jenkins directory, we do not lock it.
     * @return useCache
     * @throws IOException on error
     * @throws InterruptedException on error
     */
    private boolean decideAndUseCache(String remoteName) throws IOException, InterruptedException {
        boolean useCache = false;
        String cacheEntry = AbstractGitSCMSource.getCacheEntry(remoteName);
        File cacheDir = AbstractGitSCMSource.getCacheDir(cacheEntry, false);
        if (cacheDir != null) {
            Git git = Git.with(TaskListener.NULL, new EnvVars(EnvVars.masterEnvVars)).in(cacheDir).using("jgit");
            GitClient client = git.getClient();
            if (client.hasGitRepo()) {
                sizeOfRepo = FileUtils.sizeOfDirectory(cacheDir);
                sizeOfRepo = (sizeOfRepo/1000); // Conversion from Bytes to Kilo Bytes
                useCache = true;
            }
        }
        return useCache;
    }

    private void decideAndUseAPI(String remoteName, Item context, String credentialsId, String gitExe) {
        if (setSizeFromAPI(remoteName, context, credentialsId)) {
            implementation = determineSwitchOnSize(sizeOfRepo, gitExe);
        }
    }

    /**
     * Check if the desired implementation of extension is present and ask for the size of repository if it does
     * @param repoUrl: The remote name derived from {@link GitSCMSource} object
     * @return boolean useAPI or not.
     */
    private boolean setSizeFromAPI(String repoUrl, Item context, String credentialsId) {
        List<RepositorySizeAPI> acceptedRepository = Objects.requireNonNull(RepositorySizeAPI.all())
                .stream()
                .filter(r -> r.isApplicableTo(repoUrl, context, credentialsId))
                .collect(Collectors.toList());

        if (acceptedRepository.size() > 0) {
            try {
                for (RepositorySizeAPI repo: acceptedRepository) {
                    long size = repo.getSizeOfRepository(repoUrl, context, credentialsId);
                    if (size != 0) { sizeOfRepo = size; }
                }
            } catch (Exception e) {
                LOGGER.log(Level.INFO, "Not using performance improvement from REST API: {0}", e.getMessage());
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
    String determineSwitchOnSize(Long sizeOfRepo, String gitExe) {
        if (sizeOfRepo != 0L) {
            if (sizeOfRepo < SIZE_TO_SWITCH) {
                if (!JGIT_SUPPORTED) {
                    return "NONE";
                }
                return determineToolName(gitExe, JGitTool.MAGIC_EXENAME);
            } else {
                return determineToolName(gitExe, "git");
            }
        }
        return "NONE";
    }

    /**
     * For a given recommended git implementation, validate if the installation exists and provide no suggestion if
     * implementation doesn't exist.
     * @param gitImplementation: The recommended git implementation, "git" or "jgit" on the basis of the heuristics.
     */
    private void determineGitTool(String gitImplementation, String gitExe) {
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

    private String determineToolName(String gitExe, String recommendation) {
        if (gitExe.contains(recommendation) && !gitExe.equals(JGitTool.MAGIC_EXENAME) && !gitExe.equals(JGitApacheTool.MAGIC_EXENAME)) {
            return gitExe;
        }
        if (!recommendation.equals(gitExe)) {
            if (gitExe.equals(JGitApacheTool.MAGIC_EXENAME) && recommendation.equals(JGitTool.MAGIC_EXENAME)) {
                return gitExe;
            }
        }
        return recommendation;
    }

    /**
     * Other plugins can estimate the size of repository using this extension point
     * The size is assumed to be in KiBs
     */
    public static abstract class RepositorySizeAPI implements ExtensionPoint {

        public abstract boolean isApplicableTo(String remote, Item context, String credentialsId);

        public abstract Long getSizeOfRepository(String remote, Item context, String credentialsId) throws Exception;

        public static ExtensionList<RepositorySizeAPI> all() {
            return Jenkins.get().getExtensionList(RepositorySizeAPI.class);
        }
    }

    private static final Logger LOGGER = Logger.getLogger(GitToolChooser.class.getName());
}
