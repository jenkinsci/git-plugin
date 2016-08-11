package hudson.plugins.git;

import hudson.views.ListViewColumn;
import hudson.Extension;
import hudson.model.Item;
import hudson.scm.SCM;
import hudson.views.ListViewColumnDescriptor;
import java.util.ArrayList;
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

    public String getBranchSpecifier( final Item item ) {
        String branchSpec = "";
        SCMTriggerItem s = SCMTriggerItem.SCMTriggerItems.asSCMTriggerItem(item);
        if(s != null) {
            for(SCM scm : s.getSCMs()) {
                if (scm instanceof GitSCM) {
                    GitSCM gitScm = (GitSCM)scm;
                    ArrayList<String> branches = new ArrayList<String>();
                    for(BranchSpec spec : gitScm.getBranches()) {
                        branches.add(spec.getName());
                    }
                    branchSpec = StringUtils.join(branches, " ");
                }
            }
        }
        return branchSpec;
    }

    @Extension
    public static class DescriptorImpl extends ListViewColumnDescriptor {

        @Override
        public String getDisplayName() {
            return "Git branches to build";
        }

        @Override
        public boolean shownByDefault() {
            return false;
        }

    }

}
