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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import edu.umd.cs.findbugs.annotations.NonNull;

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

    /** Cache of repository sizes based on remoteURL. */
    private static ConcurrentHashMap<String, Long> repositorySizeCache = new ConcurrentHashMap<>();

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

        LOGGER.log(Level.INFO,
                   "GitToolChooser constructor remote {0}, gitExe {1}, useJGit {2}, JGIT_SUPPORTED {3}",
                   new Object[]{remoteName, gitExe, useJGit, JGIT_SUPPORTED});

        implementation = "NONE";
        useCache = decideAndUseCache(remoteName);

        if (useCache) {
            LOGGER.log(Level.INFO, "GitToolChooser using cache in constructor");
            implementation = determineSwitchOnSize(sizeOfRepo, gitExe);
        } else {
            LOGGER.log(Level.INFO, "GitToolChooser not using cache in constructor");
            decideAndUseAPI(remoteName, projectContext, credentialsId, gitExe);
        }
        determineGitTool(implementation, gitExe);
        LOGGER.log(Level.INFO,
                   "GitToolChooser constructor sizeOfRepo {0}, implementation {1}, gitTool {2}, JGIT_SUPPORTED {3}",
                   new Object[]{sizeOfRepo, implementation, gitTool, JGIT_SUPPORTED});
    }

    /* Git repository URLs frequently end with the ".git" suffix.
     * However, many repositories (especially https) do not require the ".git" suffix.
     *
     * Add remoteURL with the ".git" suffix and without the ".git" suffix to the
     * list of alternatives.
     */
    private void addSuffixVariants(@NonNull String remoteURL, @NonNull Set<String> alternatives) {
        alternatives.add(remoteURL);
        String suffix = ".git";
        if (remoteURL.endsWith(suffix)) {
            alternatives.add(remoteURL.substring(0, remoteURL.length() - suffix.length()));
        } else {
            alternatives.add(remoteURL + suffix);
        }
    }

    /* Protocol patterns to extract hostname and path from typical repository URLs */
    private static Pattern gitProtocolPattern = Pattern.compile("^git://([^/]+)/(.+?)/*$");
    private static Pattern httpProtocolPattern = Pattern.compile("^https?://([^/]+)/(.+?)/*$");
    private static Pattern sshAltProtocolPattern = Pattern.compile("^[\\w]+@(.+):(.+?)/*$");
    private static Pattern sshProtocolPattern = Pattern.compile("^ssh://[\\w]+@([^/]+)/(.+?)/*$");

    /* Return a list of alternate remote URL's based on permutations of remoteURL.
     * Varies the protocol (https, git, ssh) and the suffix of the repository URL.
     * Package protected for testing
     */
    /* package */ @NonNull Set<String> remoteAlternatives(String remoteURL) {
        Set<String> alternatives = new LinkedHashSet<>();
        if (remoteURL == null || remoteURL.isEmpty()) {
            LOGGER.log(Level.FINE, "Null or empty remote URL not cached");
            return alternatives;
        }

        Pattern [] protocolPatterns = {
            gitProtocolPattern,
            httpProtocolPattern,
            sshAltProtocolPattern,
            sshProtocolPattern,
        };

        String[] matcherReplacements = {
            "git://$1/$2",     // git protocol
            "git@$1:$2",       // ssh protocol alternate URL
            "https://$1/$2",   // https protocol
            "ssh://git@$1/$2", // ssh protocol
        };

        /* For each matching protocol, form alternatives by iterating over replacements */
        boolean matched = false;
        for (Pattern protocolPattern : protocolPatterns) {
            Matcher protocolMatcher = protocolPattern.matcher(remoteURL);
            if (protocolMatcher.matches()) {
                for (String replacement : matcherReplacements) {
                    String alternativeURL = protocolMatcher.replaceAll(replacement);
                    addSuffixVariants(alternativeURL, alternatives);
                }
                matched = true;
            }
        }

        // Must include original remote in case none of the protocol patterns match
        // For example, file://srv/git/repo.git is matched by none of the patterns
        if (!matched) {
            addSuffixVariants(remoteURL, alternatives);
        }

        LOGGER.log(Level.FINE, "Cache repo alternative URLs: {0}", alternatives);
        return alternatives;
    }

    /** Cache the estimated repository size for variants of repository URL */
    private void storeRepositorySize(String repoURL, long repoSize) {
        for (String url : remoteAlternatives(repoURL)) {
            if (repositorySizeCache.containsKey(url)) {
                long oldSize = repositorySizeCache.get(url);
                if (oldSize < repoSize) {
                    LOGGER.log(Level.FINE, "Replacing old repo size {0} with new size {1} for repo {2}", new Object[]{oldSize, repoSize, url});
                    repositorySizeCache.put(url, repoSize);
                } else if (oldSize > repoSize) {
                    LOGGER.log(Level.FINE, "Ignoring new size {1} in favor of old size {0} for repo {2}", new Object[]{oldSize, repoSize, url});
                }
            } else {
                LOGGER.log(Level.FINE, "Caching repo size {0} for repo {1}", new Object[]{repoSize, url});
                repositorySizeCache.put(url, repoSize);
            }
        }
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
        for (String repoUrl : remoteAlternatives(remoteName)) {
            if (repositorySizeCache.containsKey(repoUrl)) {
                sizeOfRepo = repositorySizeCache.get(repoUrl);
                useCache = true;
                LOGGER.log(Level.FINER, "Found cached size estimate {0} for remote {1}", new Object[]{sizeOfRepo, repoUrl});
                break;
            }
            String cacheEntry = AbstractGitSCMSource.getCacheEntry(repoUrl);
            File cacheDir = AbstractGitSCMSource.getCacheDir(cacheEntry, false);
            if (cacheDir != null) {
                Git git = Git.with(TaskListener.NULL, new EnvVars(EnvVars.masterEnvVars)).in(cacheDir).using("jgit");
                GitClient client = git.getClient();
                if (client.hasGitRepo()) {
                    long clientRepoSize = FileUtils.sizeOfDirectory(cacheDir) / 1024;
                    if (clientRepoSize > sizeOfRepo) {
                        /* Use the size of the largest cache */
                        if (sizeOfRepo > 0) {
                            LOGGER.log(Level.FINE, "Replacing prior size estimate {0} with new size estimate {1} for remote {2} from cache {3}",
                                       new Object[]{sizeOfRepo, clientRepoSize, remoteName, cacheDir});
                        }
                        sizeOfRepo = clientRepoSize;
                        storeRepositorySize(remoteName, sizeOfRepo);
                    }
                    useCache = true;
                    if (remoteName.equals(repoUrl)) {
                        LOGGER.log(Level.FINE, "Remote URL {0} found cache {1} with size {2}",
                                   new Object[]{remoteName, cacheDir, sizeOfRepo});
                    } else {
                        LOGGER.log(Level.FINE, "Remote URL {0} found cache {1} with size {2}, alternative URL {3}",
                                   new Object[]{remoteName, cacheDir, sizeOfRepo, repoUrl});
                    }
                } else {
                    // Log the surprise but continue looking for a cache
                    LOGGER.log(Level.FINE, "Remote URL {0} cache {1} has no git dir", new Object[]{remoteName, cacheDir});
                }
            }
        }
        if (!useCache) {
            LOGGER.log(Level.FINE, "Remote URL {0} cache not found", remoteName);
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
                    if (size != 0) {
                        sizeOfRepo = size;
                        storeRepositorySize(repoUrl, size);
                    }
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
