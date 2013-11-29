package hudson.plugins.git.extensions.impl;

import hudson.Extension;
import hudson.Util;
import hudson.model.TaskListener;
import hudson.plugins.git.GitChangeSet;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.extensions.GitSCMExtension;
import hudson.plugins.git.extensions.GitSCMExtensionDescriptor;
import hudson.plugins.git.util.BuildData;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.gitclient.GitClient;
import org.kohsuke.stapler.DataBoundConstructor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

/**
 * {@link GitSCMExtension} that ignores commits that only affects specific paths.
 *
 * @author Juerg Haefliger
 * @author Andrew Bayer
 * @author Kohsuke Kawaguchi
 */
public class PathRestriction extends GitSCMExtension {
    private final String includedRegions;
    private final String excludedRegions;

    // compiled cache
    private transient volatile List<Pattern> includedPatterns,excludedPatterns;

    @DataBoundConstructor
    public PathRestriction(String includedRegions, String excludedRegions) {
        this.includedRegions = includedRegions;
        this.excludedRegions = excludedRegions;
    }

    @Override
    public boolean requiresWorkspaceForPolling () {
    	return true;
    }
    
    public String getIncludedRegions() {
        return includedRegions;
    }

    public String getExcludedRegions() {
        return excludedRegions;
    }

    public String[] getExcludedRegionsNormalized() {
        return normalize(excludedRegions);
    }

    public String[] getIncludedRegionsNormalized() {
        return normalize(includedRegions);
    }

    private String[] normalize(String s) {
        return StringUtils.isBlank(s) ? null : s.split("[\\r\\n]+");
    }

    private List<Pattern> getIncludedPatterns() {
        if (includedPatterns==null)
            includedPatterns = getRegionsPatterns(getIncludedRegionsNormalized());
        return includedPatterns;
    }

    private List<Pattern> getExcludedPatterns() {
        if (excludedPatterns==null)
            excludedPatterns = getRegionsPatterns(getExcludedRegionsNormalized());
        return excludedPatterns;
    }

    private List<Pattern> getRegionsPatterns(String[] regions) {
        if (regions != null) {
            List<Pattern> patterns = new ArrayList<Pattern>(regions.length);

            for (String region : regions) {
                patterns.add(Pattern.compile(region));
            }

            return patterns;
        }

        return Collections.emptyList();
    }

    @Override
    public Boolean isRevExcluded(GitSCM scm, GitClient git, GitChangeSet commit, TaskListener listener, BuildData buildData) {
        Collection<String> paths = commit.getAffectedPaths();
        if (paths.isEmpty()) {// nothing modified, so no need to compute any of this
            return null;
        }

        List<Pattern> included = getIncludedPatterns();
        List<Pattern> excluded = getExcludedPatterns();

        // Assemble the list of included paths
        List<String> includedPaths = new ArrayList<String>(paths.size());
        if (!included.isEmpty()) {
            for (String path : paths) {
                for (Pattern pattern : included) {
                    if (pattern.matcher(path).matches()) {
                        includedPaths.add(path);
                        break;
                    }
                }
            }
        } else {
            includedPaths.addAll(paths);
        }

        // Assemble the list of excluded paths
        List<String> excludedPaths = new ArrayList<String>();
        if (!excluded.isEmpty()) {
            for (String path : includedPaths) {
                for (Pattern pattern : excluded) {
                    if (pattern.matcher(path).matches()) {
                        excludedPaths.add(path);
                        break;
                    }
                }
            }
        }

        // If every affected path is excluded, return true.
        if (includedPaths.size() == excludedPaths.size()) {
            listener.getLogger().println("Ignored commit " + commit.getCommitId()
                    + ": Found only excluded paths: "
                    + Util.join(excludedPaths, ", "));
            return true;
        }

        return null;
    }

    @Extension
    public static class DescriptorImpl extends GitSCMExtensionDescriptor {
        @Override
        public String getDisplayName() {
            return "Polling ignores commits in certain paths";
        }
    }
}
