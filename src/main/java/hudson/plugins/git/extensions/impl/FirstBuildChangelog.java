package hudson.plugins.git.extensions.impl;

import hudson.Extension;
import hudson.plugins.git.extensions.GitSCMExtension;
import hudson.plugins.git.extensions.GitSCMExtensionDescriptor;
import java.util.Objects;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

/**
 * First Build generates a changelog.
 *
 * @author Derek Inskeep
 */
public class FirstBuildChangelog extends GitSCMExtension {
    private boolean makeChangelog;

    @DataBoundConstructor
    public FirstBuildChangelog() {}

    public boolean isMakeChangelog() {
        return makeChangelog;
    }

    @DataBoundSetter
    public void setMakeChangelog(boolean makeChangelog) {
        this.makeChangelog = makeChangelog;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        FirstBuildChangelog that = (FirstBuildChangelog) o;
        return makeChangelog == that.makeChangelog;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return Objects.hash(makeChangelog);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return "FirstBuildChangelog{" + "makeChangelog=" + makeChangelog + '}';
    }

    @Extension
    @Symbol("firstBuildChangelog")
    public static class DescriptorImpl extends GitSCMExtensionDescriptor {

        /**
         * {@inheritDoc}
         */
        @Override
        public String getDisplayName() {
            return "First Build Changelog";
        }
    }
}
