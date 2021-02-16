package hudson.plugins.git;

import com.google.common.base.Joiner;
import org.apache.commons.collections.CollectionUtils;
import org.jenkinsci.plugins.scriptsecurity.sandbox.whitelists.Whitelisted;
import org.kohsuke.stapler.DataBoundConstructor;

import java.util.Arrays;

import java.util.Collection;
import java.util.Collections;
import java.util.regex.Pattern;

/**
 * Deprecated data class used in a submodule configuration experiment.
 * Deprecated as inaccessible in git plugin 4.6.0.  Class retained for
 * binary compatibility.
 *
 * @deprecated
 */
@Deprecated
public class SubmoduleConfig implements java.io.Serializable {
    private static final long serialVersionUID = 1L;
    private static final String[] EMPTY_ARRAY = new String[0];
    String   submoduleName = null;
    String[] branches = EMPTY_ARRAY;

    public SubmoduleConfig() {
        this(null, Collections.emptySet());
    }

    public SubmoduleConfig(String submoduleName, String[] branches) {
    }

    @DataBoundConstructor
    public SubmoduleConfig(String submoduleName, Collection<String> branches) {
    }

    @Whitelisted
    public String getSubmoduleName() {
        return submoduleName;
    }

    public void setSubmoduleName(String submoduleName) {
    }

    public String[] getBranches() {
        return EMPTY_ARRAY;
    }

    public void setBranches(String[] branches) {
    }

    public boolean revisionMatchesInterest(Revision r) {
        return false;
    }

    public boolean branchMatchesInterest(Branch br) {
        return false;
    }

    public String getBranchesString() {
        return "";
    }
}
