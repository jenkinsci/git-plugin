package hudson.plugins.git.util;

import hudson.DescriptorExtensionList;
import hudson.model.Descriptor;
import jenkins.model.Jenkins;
import hudson.model.Item;

/**
 * @author Kohsuke Kawaguchi
 */
public abstract class BuildChooserDescriptor extends Descriptor<BuildChooser> {
    /**
     * Before this extension point is formalized, existing {@link BuildChooser}s had
     * a hard-coded ID name used for the persistence.
     *
     * This method returns those legacy ID, if any, to keep compatibility with existing data.
     */
    public String getLegacyId() {
        return null;
    }

    public static DescriptorExtensionList<BuildChooser,BuildChooserDescriptor> all() {
        return Jenkins.getInstance().getDescriptorList(BuildChooser.class);
    }

    /**
     * Returns true if this build chooser is applicable to the given job type.
     *
     * @param job the type of job or item.
     * @return true to allow user to select this build chooser.
     */
    public boolean isApplicable(java.lang.Class<? extends Item> job) {
        return true;
    }
}
