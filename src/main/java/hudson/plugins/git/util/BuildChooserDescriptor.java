package hudson.plugins.git.util;

import hudson.model.Descriptor;

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
}
