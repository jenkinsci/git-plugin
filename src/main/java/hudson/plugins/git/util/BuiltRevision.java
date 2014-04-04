package hudson.plugins.git.util;

import hudson.model.Result;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.Revision;
import hudson.scm.SCMRevisionState;
import org.eclipse.jgit.lib.ObjectId;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

import java.io.IOException;
import java.io.Serializable;

/**
 * Remembers which build built which {@link Revision}.
 */
@ExportedBean(defaultVisibility = 999)
public class BuiltRevision extends Build {
    private static final long serialVersionUID = 1L;

    /**
     * Revision in the repository marked as built.
     *
     * <p>
     * This field is used to avoid doing the same build twice, by (normally) recording the commit in the upstream repository
     * that initiated the build.
     *
     * <p>
     * For simple use cases, this value is normally the same as {@link #revision}. Where this gets different is when
     * a revision to checkout is decorated and differs from the commit found in the repository (for example, a merge
     * before a build.) In such a situation, we need to remember the commit that came from the upstream so that
     * future polling and build will not attempt to do another build from the same upstream commit.
     *
     * <p>
     * In some other kind of speculative merging, such as github pull request build, this field should point
     * to the same value as {@link #revision}, as we want to be able to build two pull requests rooted at the same
     * commit in the base repository.
     */
    public Revision marked;

    /**
     * Revision that was actually built.
     *
     * <p>
     * This points to the commit that was checked out to the workspace when {@link GitSCM#checkout} left.
     */
    public Revision revision;

    public BuiltRevision(Revision marked, Revision revision) {
        this.marked = marked;
        this.revision = revision;
    }

    @Deprecated
    public BuiltRevision(Revision marked, Revision revision, int buildNumber, Result result) {
        this(marked, revision);
        super.hudsonBuildNumber = buildNumber;
        super.hudsonBuildResult = result;
    }

    @Deprecated
    public BuiltRevision(Revision revision, int buildNumber, Result result) {
        this(revision,revision,buildNumber,result);
    }

    public ObjectId getSHA1() {
        return revision.getSha1();
    }

    @Exported
    public Revision getRevision() {
        return revision;
    }

    @Exported
    public Revision getMarked() {
        return marked;
    }

    public @Override String toString() {
        return "Built " + revision.toString();
    }

    @Override
    public BuiltRevision clone() {
        BuiltRevision clone;
        try {
            clone = (BuiltRevision) super.clone();
        }
        catch (CloneNotSupportedException e) {
            throw new RuntimeException("Error cloning Build", e);
        }

        if (revision != null)
            clone.revision = revision.clone();
        if (marked != null)
            clone.marked = marked.clone();
        return clone;
    }

    public boolean isFor(String sha1) {
        if (revision!=null      && revision.getSha1String().startsWith(sha1))  return true;
        return false;
    }

    public Object readResolve() throws IOException {
        if (marked==null) // this field was introduced later than 'revision'
            marked = revision;
        return this;
    }
}