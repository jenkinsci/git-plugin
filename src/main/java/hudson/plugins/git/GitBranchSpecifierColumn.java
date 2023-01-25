package hudson.plugins.git;

import hudson.views.ListViewColumn;
import hudson.Extension;
import hudson.model.Item;
import hudson.scm.SCM;
import hudson.views.ListViewColumnDescriptor;
import java.util.ArrayList;
import java.util.List;
import jenkins.triggers.SCMTriggerItem;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Class that adds an optional 'Git branches to build' column to a list view.
 *
 * @author Mads
 */
public class GitBranchSpecifierColumn extends ListViewColumn {

    @DataBoundConstructor
    public GitBranchSpecifierColumn() { }

    public List<String> getBranchSpecifier( final Item item ) {
        List<String> branchSpec = new ArrayList<>();
        SCMTriggerItem s = SCMTriggerItem.SCMTriggerItems.asSCMTriggerItem(item);
        if(s != null) {
            for(SCM scm : s.getSCMs()) {
                if (scm instanceof GitSCM) {
                    GitSCM gitScm = (GitSCM)scm;
                    for(BranchSpec spec : gitScm.getBranches()) {
                        branchSpec.add(spec.getName());
                    }
                }
            }
        }
        return branchSpec;
    }

    public String breakOutString(List<String> branches) {
        return StringUtils.join(branches, ", ");
    }

    @Extension
    public static class DescriptorImpl extends ListViewColumnDescriptor {

        @Override
        public String getDisplayName() {
            return "Git Branches";
        }

        @Override
        public boolean shownByDefault() {
            return false;
        }

    }

}
