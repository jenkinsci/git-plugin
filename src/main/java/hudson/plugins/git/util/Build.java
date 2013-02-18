package hudson.plugins.git.util;

import hudson.model.Result;
import hudson.plugins.git.Revision;
import org.eclipse.jgit.lib.ObjectId;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

import java.io.Serializable;

@ExportedBean(defaultVisibility = 999)
public class Build implements Serializable, Cloneable {
    private static final long serialVersionUID = 1L;

    /**
     * Revision marked as being built.
     */
    public Revision revision;

    public int      hudsonBuildNumber;
    public Result   hudsonBuildResult;

    // TODO: We don't currently store the result correctly.

    public Build(Revision revision, int buildNumber, Result result) {
        this.revision = revision;
        this.hudsonBuildNumber = buildNumber;
        this.hudsonBuildResult = result;
    }

    public ObjectId getSHA1() {
        return revision.getSha1();
    }

    @Exported
    public Revision getRevision() {
        return revision;
    }

    @Exported
    public int getBuildNumber() {
        return hudsonBuildNumber;
    }

    @Exported
    public Result getBuildResult() {
        return hudsonBuildResult;
    }

    public @Override String toString() {
        return "Build #" + hudsonBuildNumber + " of " + revision.toString();
    }

    @Override
    public Build clone() {
        Build clone;
        try {
            clone = (Build) super.clone();
        }
        catch (CloneNotSupportedException e) {
            throw new RuntimeException("Error cloning Build", e);
        }

        if (revision != null)
            clone.revision = revision.clone();
        return clone;
    }

    public boolean isFor(String sha1) {
        if (revision!=null      && revision.getSha1String().startsWith(sha1))  return true;
        return false;
    }
}