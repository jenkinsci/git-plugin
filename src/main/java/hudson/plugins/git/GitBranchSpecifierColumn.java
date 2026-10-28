package hudson.plugins.git;

import hudson.views.ListViewColumn;
import hudson.Extension;
import hudson.model.Item;
import hudson.scm.SCM;
import hudson.views.ListViewColumnDescriptor;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.triggers.SCMTriggerItem;
import org.apache.commons.lang3.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Class that adds an optional 'Git branches to build' column to a list view.
 *
 * @author Mads
 */
public class GitBranchSpecifierColumn extends ListViewColumn {

    private static final Logger LOGGER = Logger.getLogger(GitBranchSpecifierColumn.class.getName());

    @DataBoundConstructor
    public GitBranchSpecifierColumn() { }

    public List<String> getBranchSpecifier( final Item item ) {
        List<String> branchSpec = new ArrayList<>();
        SCMTriggerItem s = SCMTriggerItem.SCMTriggerItems.asSCMTriggerItem(item);
        if(s != null) {
            Collection<? extends SCM> scms;
            try {
                scms = s.getSCMs();
            } catch (RuntimeException e) {
                // e.g. its SCM plugin was uninstalled; do not break rendering of the whole view
                LOGGER.log(Level.FINE, e, () -> "Unable to read the SCMs of " + item.getFullName());
                return branchSpec;
            }
            for(SCM scm : scms) {
                if (scm instanceof GitSCM gitScm) {
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
