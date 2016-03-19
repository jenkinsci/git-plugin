package hudson.plugins.git;

import com.google.common.base.Joiner;
import java.util.Arrays;

import java.util.regex.Pattern;

public class SubmoduleConfig implements java.io.Serializable {
    private static final long serialVersionUID = 1L;
    String   submoduleName;
    String[] branches;

    public String getSubmoduleName() {
        return submoduleName;
    }

    public void setSubmoduleName(String submoduleName) {
        this.submoduleName = submoduleName;
    }

    public String[] getBranches() {
        /* findbugs correctly complains that returning branches exposes the
         * internal representation of the class to callers.  Returning a copy
         * of the array does not expose internal representation, at the possible
         * expense of some additional memory.
         */
        return Arrays.copyOf(branches, branches.length);
    }

    public void setBranches(String[] branches) {
        /* findbugs correctly complains that assign to branches exposes the
         * internal representation of the class to callers.  Assigning a copy
         * of the array does not expose internal representation, at the possible
         * expense of some additional memory.
         */
        this.branches = Arrays.copyOf(branches, branches.length);
    }

    public boolean revisionMatchesInterest(Revision r) {
        for (Branch br : r.getBranches()) {
            if (branchMatchesInterest(br)) return true;
        }
        return false;
    }

    public boolean branchMatchesInterest(Branch br) {
        for (String regex : branches) {
            if (!Pattern.matches(regex, br.getName())) {
                return false;
            }
        }
        return true;
    }

    public String getBranchesString() {
        return Joiner.on(',').join(branches);
    }
}
