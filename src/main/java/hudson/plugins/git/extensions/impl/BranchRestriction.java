package hudson.plugins.git.extensions.impl;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.gitclient.GitClient;
import org.kohsuke.stapler.DataBoundConstructor;

import hudson.Extension;
import hudson.model.TaskListener;
import hudson.plugins.git.Branch;
import hudson.plugins.git.GitChangeSet;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.extensions.GitSCMExtension;
import hudson.plugins.git.extensions.GitSCMExtensionDescriptor;
import hudson.plugins.git.util.BuildData;

/**
 * {@link GitSCMExtension} that allows to white- and black-list branches.
 *
 * @author Marcus Klein
 */
public class BranchRestriction extends GitSCMExtension {

	private String blacklist;
	private String whitelist;

    @DataBoundConstructor
	public BranchRestriction(String whitelist, String blacklist) {
		super();
		this.whitelist = whitelist;
		this.blacklist = blacklist;
	}

    @Override
    public boolean requiresWorkspaceForPolling() {
        return true;
    }

    public String getWhitelist() {
		return whitelist;
	}

	public String getBlacklist() {
		return blacklist;
	}

	private String[] normalize(String s) {
		return StringUtils.isBlank(s) ? null : s.split(",");
	}

	private Set<String> getWhitelistSet() {
		return getSet(normalize(whitelist));
	}

	private Set<String> getBlacklistSet() {
		return getSet(normalize(blacklist));
	}

    private Set<String> getSet(String[] list) {
    	if (null != list) {
    		Set<String> retval = new HashSet<String>(list.length);
    		for (String s : list) {
    			retval.add(s);
    		}
    		return retval;
    	}
		return Collections.emptySet();
	}

	@Override
    public Boolean isRevExcluded(GitSCM scm, GitClient git, GitChangeSet commit, TaskListener listener, BuildData buildData) throws InterruptedException {
		List<Branch> branches = git.getBranchesContaining(commit.getId(), true);
		for (Branch branch : branches) {
			String branchName = getBranchName(branch);
			Set<String> whitelist = getWhitelistSet();
			Set<String> blacklist = getBlacklistSet();
			String message = "Checking branch " + branchName + " against whitelist " + whitelist + " and blacklist " + blacklist + " with result: ";
			if (!whitelist.isEmpty()) {
				if (whitelist.contains(branchName)) {
					listener.getLogger().println(message + "whitelisted.");
					return false;
				} else {
					listener.getLogger().println(message + "not in whitelist.");
					return true;
				}
			}
			if (!blacklist.isEmpty()) {
				if (blacklist.contains(branchName)) {
					listener.getLogger().println(message + "blacklisted.");
					return true;
				} else {
					listener.getLogger().println(message + "not in blacklist.");
				}
			}
		}
    	return null;
    }

    private String getBranchName(Branch branch) {
        String name = branch.getName();
        return name.startsWith("remotes/") ? name.substring(name.indexOf('/', "remotes/".length()) + 1) : name;
    }

	@Extension
    public static class DescriptorImpl extends GitSCMExtensionDescriptor {
        @Override
        public String getDisplayName() {
            return "Polling ignores commits in certain branches";
        }
    }
}
