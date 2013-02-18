package hudson.plugins.git.util;

import hudson.model.Result;
import hudson.plugins.git.Revision;

/**
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
public class MergeBuild extends Build {

    /**
     * Revision that was subject to a merge.
     */
    public Revision mergeRevision;


    public MergeBuild(Revision revision, int buildNumber, Revision mergeRevision, Result result) {
        super(revision, buildNumber, result);
        this.mergeRevision = mergeRevision;
    }

    @Override
    public String toString() {
        return super.toString() + " merged with " + mergeRevision;
    }

    @Override
    public MergeBuild clone() {
        MergeBuild clone = (MergeBuild) super.clone();
        clone.mergeRevision = mergeRevision.clone();
        return clone;
    }

    @Override
    public boolean isFor(String sha1) {
        if (mergeRevision!=null && mergeRevision.getSha1String().startsWith(sha1))  return true;
        return super.isFor(sha1);
    }
}
