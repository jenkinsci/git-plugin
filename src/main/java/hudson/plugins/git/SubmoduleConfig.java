package hudson.plugins.git;

import com.google.common.base.Joiner;

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
        return branches;
    }

    public void setBranches(String[] branches) {
        this.branches = branches;
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
