package jenkins.plugins.git;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.EnvVars;
import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.model.Item;
import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.plugins.git.GitTool;
import hudson.plugins.git.util.GitUtils;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import jenkins.model.Jenkins;
import org.apache.commons.io.FileUtils;
import org.jenkinsci.plugins.gitclient.Git;
import org.jenkinsci.plugins.gitclient.GitClient;
import org.jenkinsci.plugins.gitclient.JGitApacheTool;
import org.jenkinsci.plugins.gitclient.JGitTool;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * A class which allows Git Plugin to choose a git implementation by estimating the size of a repository from a distance
 * without requiring a local checkout.
 */
public class GitToolChooser {

    private long sizeOfRepo = 0L;
    private String implementation;
    private String gitTool;
    private TaskListener listener;
    private Node currentNode;
    /**
     * Size to switch implementation in KiB
     */
    private static final int SIZE_TO_SWITCH = 5000;

    private boolean JGIT_SUPPORTED = false;

    /** Cache of repository sizes based on remoteURL. **/
    private static ConcurrentHashMap<String, Long> repositorySizeCache = new ConcurrentHashMap<>();

    /**
     * Instantiate class using the remote name. It looks for a cached .git directory first, calculates the
     * size if it is found else checks if the extension point has been implemented and asks for the size.
     * @param remoteName the repository url
     * @param projectContext the context where repository size is being estimated
     * @param credentialsId credential used to access the repository or null if no credential is required
     * @param gitExe Git tool ('git', 'jgit', 'jgitapache') to be used as the default tool
     * @param n A Jenkins agent used to check validity of git installation
     * @param listener TaskListener required by GitUtils.resolveGitTool()
     * @param useJGit if true the JGit is allowed as an implementation
     * @throws IOException on error
     * @throws InterruptedException on error
     */
    public GitToolChooser(
            String remoteName,
            Item projectContext,
            String credentialsId,
            GitTool gitExe,
            Node n,
            TaskListener listener,
            Boolean useJGit)
            throws IOException, InterruptedException {
        boolean useCache = false;
        if (useJGit != null) {
            JGIT_SUPPORTED = useJGit;
        }
        currentNode = n;
        this.listener = listener;
        implementation = "NONE";
        useCache = decideAndUseCache(remoteName);

        if (useCache) {
            implementation = determineSwitchOnSize(sizeOfRepo, gitExe);
        } else {
            decideAndUseAPI(remoteName, projectContext, credentialsId, gitExe);
        }
        gitTool = implementation;
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
        if (setSizeFromInternalCache(remoteName)) {
            LOGGER.log(Level.FINE, "Found cache key for {0} with size {1}", new Object[] {remoteName, sizeOfRepo});
            useCache = true;
            return useCache;
        }
        for (String repoUrl : remoteAlternatives(remoteName)) {
            String cacheEntry = AbstractGitSCMSource.getCacheEntry(repoUrl);
            File cacheDir = AbstractGitSCMSource.getCacheDir(cacheEntry, false);
            if (cacheDir != null) {
                Git git = Git.with(TaskListener.NULL, new EnvVars(EnvVars.masterEnvVars))
                        .in(cacheDir)
                        .using("git");
                GitClient client = git.getClient();
                if (client.hasGitRepo(false)) {
                    long clientRepoSize =
                            FileUtils.sizeOfDirectory(cacheDir) / 1024; // Conversion from Bytes to Kilo Bytes
                    if (clientRepoSize > sizeOfRepo) {
                        if (sizeOfRepo > 0) {
                            LOGGER.log(
                                    Level.FINE,
                                    "Replacing prior size estimate {0} with new size estimate {1} for remote {2} from cache {3}",
                                    new Object[] {sizeOfRepo, clientRepoSize, remoteName, cacheDir});
                        }
                        sizeOfRepo = clientRepoSize;
                        assignSizeToInternalCache(remoteName, sizeOfRepo);
                    }
                    useCache = true;
                    if (remoteName.equals(repoUrl)) {
                        LOGGER.log(Level.FINE, "Remote URL {0} found cache {1} with size {2}", new Object[] {
                            remoteName, cacheDir, sizeOfRepo
                        });
                    } else {
                        LOGGER.log(
                                Level.FINE,
                                "Remote URL {0} found cache {1} with size {2}, alternative URL {3}",
                                new Object[] {remoteName, cacheDir, sizeOfRepo, repoUrl});
                    }
                } else {
                    // Log the surprise but continue looking for a cache
                    LOGGER.log(
                            Level.FINE, "Remote URL {0} cache {1} has no git dir", new Object[] {remoteName, cacheDir});
                }
            }
        }
        if (!useCache) {
            LOGGER.log(Level.FINE, "Remote URL {0} cache not found", remoteName);
        }
        return useCache;
    }

    private void decideAndUseAPI(String remoteName, Item context, String credentialsId, GitTool gitExe) {
        if (setSizeFromAPI(remoteName, context, credentialsId)) {
            implementation = determineSwitchOnSize(sizeOfRepo, gitExe);
        }
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

    /* Git repository URLs frequently end with the ".git" suffix.
     * However, many repositories (especially https) do not require the ".git" suffix.
     *
     * Add remoteURL with the ".git" suffix if not present
     */
    private String addSuffix(@NonNull String canonicalURL) {
        String suffix = ".git";
        if (!canonicalURL.endsWith(suffix)) {
            canonicalURL = canonicalURL + suffix;
        }
        return canonicalURL;
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
    /* package */ @NonNull
    String convertToCanonicalURL(String remoteURL) {
        if (remoteURL == null || remoteURL.isEmpty()) {
            LOGGER.log(Level.FINE, "Null or empty remote URL not cached");
            return ""; // return an empty string
        }

        Pattern[] protocolPatterns = {
            sshAltProtocolPattern, sshProtocolPattern, gitProtocolPattern,
        };

        String matcherReplacement = "https://$1/$2";
        /* For each matching protocol, convert alternatives to canonical form by https replacement */
        remoteURL = addSuffix(remoteURL);
        String canonicalURL = remoteURL;
        if (httpProtocolPattern.matcher(remoteURL).matches()) {
            canonicalURL = remoteURL;
        } else {
            for (Pattern protocolPattern : protocolPatterns) {
                Matcher protocolMatcher = protocolPattern.matcher(remoteURL);
                if (protocolMatcher.matches()) {
                    canonicalURL = protocolMatcher.replaceAll(matcherReplacement);
                    break;
                }
            }
        }

        LOGGER.log(Level.FINE, "Cache repo URL: {0}", canonicalURL);
        return canonicalURL;
    }

    private boolean setSizeFromInternalCache(String repoURL) {
        repoURL = convertToCanonicalURL(repoURL);
        if (repositorySizeCache.containsKey(repoURL)) {
            sizeOfRepo = repositorySizeCache.get(repoURL);
            return true;
        }
        return false;
    }

    /* Return a list of alternate remote URL's based on permutations of remoteURL.
     * Varies the protocol (https, git, ssh) and the suffix of the repository URL.
     * Package protected for testing
     */
    /* package */ @NonNull
    Set<String> remoteAlternatives(String remoteURL) {
        Set<String> alternatives = new LinkedHashSet<>();
        if (remoteURL == null || remoteURL.isEmpty()) {
            LOGGER.log(Level.FINE, "Null or empty remote URL not cached");
            return alternatives;
        }

        Pattern[] protocolPatterns = {
            gitProtocolPattern, httpProtocolPattern, sshAltProtocolPattern, sshProtocolPattern,
        };

        String[] matcherReplacements = {
            "git://$1/$2", // git protocol
            "git@$1:$2", // ssh protocol alternate URL
            "https://$1/$2", // https protocol
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
    private void assignSizeToInternalCache(String repoURL, long repoSize) {
        repoURL = convertToCanonicalURL(repoURL);
        if (repositorySizeCache.containsKey(repoURL)) {
            long oldSize = repositorySizeCache.get(repoURL);
            if (oldSize < repoSize) {
                LOGGER.log(Level.FINE, "Replacing old repo size {0} with new size {1} for repo {2}", new Object[] {
                    oldSize, repoSize, repoURL
                });
                repositorySizeCache.put(repoURL, repoSize);
            } else if (oldSize > repoSize) {
                LOGGER.log(Level.FINE, "Ignoring new size {1} in favor of old size {0} for repo {2}", new Object[] {
                    oldSize, repoSize, repoURL
                });
            }
        } else {
            LOGGER.log(Level.FINE, "Caching repo size {0} for repo {1}", new Object[] {repoSize, repoURL});
            repositorySizeCache.put(repoURL, repoSize);
        }
    }

    /**
     * Check if the desired implementation of extension is present and ask for the size of repository if it does
     * @param repoUrl: The remote name derived from {@link GitSCMSource} object
     * @return boolean useAPI or not.
     */
    private boolean setSizeFromAPI(String repoUrl, Item context, String credentialsId) {
        List<RepositorySizeAPI> acceptedRepository = Objects.requireNonNull(RepositorySizeAPI.all()).stream()
                .filter(r -> r.isApplicableTo(repoUrl, context, credentialsId))
                .collect(Collectors.toList());

        if (acceptedRepository.size() > 0) {
            try {
                for (RepositorySizeAPI repo : acceptedRepository) {
                    long size = repo.getSizeOfRepository(repoUrl, context, credentialsId);
                    if (size != 0) {
                        sizeOfRepo = size;
                        assignSizeToInternalCache(repoUrl, size);
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
    String determineSwitchOnSize(Long sizeOfRepo, GitTool tool) {
        if (sizeOfRepo != 0L) {
            if (sizeOfRepo < SIZE_TO_SWITCH) {
                if (!JGIT_SUPPORTED) {
                    return "NONE";
                }
                GitTool rTool = resolveGitToolForRecommendation(tool, JGitTool.MAGIC_EXENAME);
                if (rTool == null) {
                    return "NONE";
                }
                return rTool.getGitExe();
            } else {
                GitTool rTool = resolveGitToolForRecommendation(tool, "git");
                return rTool.getGitExe();
            }
        }
        return "NONE";
    }

    private GitTool resolveGitToolForRecommendation(GitTool userChoice, String recommendation) {
        GitTool tool;
        if (recommendation.equals(JGitTool.MAGIC_EXENAME)) {
            if (userChoice.getGitExe().equals(JGitApacheTool.MAGIC_EXENAME)) {
                recommendation = JGitApacheTool.MAGIC_EXENAME;
            }
            // check if jgit or jgitapache is enabled
            tool = getResolvedGitTool(recommendation);
            if (tool.getName().equals(recommendation)) {
                return tool;
            } else {
                return null;
            }
        } else {
            if (!userChoice.getName().equals(JGitTool.MAGIC_EXENAME)
                    && !userChoice.getName().equals(JGitApacheTool.MAGIC_EXENAME)) {
                return userChoice;
            } else {
                return recommendGitToolOnAgent(userChoice);
            }
        }
    }

    public GitTool recommendGitToolOnAgent(GitTool userChoice) {
        List<GitTool> preferredToolList = new ArrayList<>();
        GitTool correctTool = GitTool.getDefaultInstallation();
        String toolName = userChoice.getName();
        if (toolName.equals(JGitTool.MAGIC_EXENAME) || toolName.equals(JGitApacheTool.MAGIC_EXENAME)) {
            GitTool[] toolList = Jenkins.get()
                    .getDescriptorByType(GitTool.DescriptorImpl.class)
                    .getInstallations();
            for (GitTool tool : toolList) {
                if (!tool.getProperties().isEmpty()) {
                    preferredToolList.add(tool);
                }
            }
            for (GitTool tool : preferredToolList) {
                if (tool.getName().equals(getResolvedGitTool(tool.getName()).getName())) {
                    correctTool = getResolvedGitTool(tool.getName());
                }
            }
        }
        return correctTool;
    }

    /**
     * Provide a git tool considering the node specific installations
     * @param recommendation: Tool name
     * @return resolved git tool
     */
    private GitTool getResolvedGitTool(String recommendation) {
        if (currentNode == null) {
            currentNode = Jenkins.get();
        }
        return GitUtils.resolveGitTool(recommendation, currentNode, null, listener);
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
    public abstract static class RepositorySizeAPI implements ExtensionPoint {

        public abstract boolean isApplicableTo(String remote, Item context, String credentialsId);

        public abstract Long getSizeOfRepository(String remote, Item context, String credentialsId) throws Exception;

        public static ExtensionList<RepositorySizeAPI> all() {
            return Jenkins.get().getExtensionList(RepositorySizeAPI.class);
        }
    }

    /**
     * Clear the cache of repository sizes.
     */
    public static void clearRepositorySizeCache() {
        repositorySizeCache = new ConcurrentHashMap<>();
    }

    /**
     * Insert an entry into the cache of repository sizes.
     * For testing only - not to be used outside the git plugin.
     *
     * @param repoURL repository URL to be added as a cache key
     * @param repoSize repository size in kilobytes
     */
    @Restricted(NoExternalUse.class)
    public static void putRepositorySizeCache(String repoURL, long repoSize) {
        /* Half-baked conversion to canonical URL for test use */
        if (!repoURL.endsWith(".git")) {
            repoURL = repoURL + ".git";
        }
        repositorySizeCache.put(repoURL, repoSize);
    }

    private static final Logger LOGGER = Logger.getLogger(GitToolChooser.class.getName());
}
