package hudson.plugins.git.util;

import hudson.DescriptorExtensionList;
import hudson.model.Descriptor;
import jenkins.model.Jenkins;

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
}
