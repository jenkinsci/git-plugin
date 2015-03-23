package jenkins.plugins.git;

import hudson.BulkChange;
import hudson.Functions;
import hudson.XmlFile;
import hudson.model.Action;
import hudson.model.Job;
import hudson.model.Run;
import hudson.model.Saveable;
import hudson.model.listeners.SaveableListener;
import hudson.plugins.git.Branch;
import hudson.plugins.git.util.BuildData;
import org.eclipse.jgit.lib.ObjectId;
import org.kohsuke.stapler.export.ExportedBean;

import javax.annotation.CheckForNull;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static hudson.Util.fixNull;

/**
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
@ExportedBean
public class BuiltRevisionMap implements Action, Saveable {

    public static final String FILE = BuiltRevisionMap.class.getName() + ".xml";

    private Map<String, BuiltRevision> revisions;
    private Set<BuiltRevision> detached;
    private BuiltRevision last;

    private transient XmlFile configFile;

    private BuiltRevisionMap(XmlFile configFile) {
        this.configFile = configFile;
        this.revisions = new HashMap<String, BuiltRevision>();
        this.detached = new HashSet<BuiltRevision>();
    }

    public static BuiltRevisionMap forProject(Job job) throws IOException {
        XmlFile configFile = new XmlFile(new File(job.getRootDir(), FILE));
        if (configFile.exists()) {
            BuiltRevisionMap it = (BuiltRevisionMap) configFile.read();
            it.configFile = configFile;
            return it;
        } else {
            BuiltRevisionMap revisionMap = new BuiltRevisionMap(configFile);
            revisionMap.migrateLegacyBuildData(job);
            return revisionMap;
        }
    }

    private void migrateLegacyBuildData(Job job) throws IOException {
        Run lastBuild = job.getLastBuild();
        if (lastBuild != null) {
            BuildData data = lastBuild.getAction(BuildData.class);
            if (data != null) {
                for (Map.Entry<String, BuiltRevision> entry : data.getBuildsByBranchName().entrySet()) {
                    revisions.put(entry.getKey(), entry.getValue());
                }
                save();
            }
        }
    }

    public Collection<String> getBranches() {
        return Collections.unmodifiableCollection(revisions.keySet());
    }

    public Map<String, BuiltRevision> getRevisions() {
        return Collections.unmodifiableMap(revisions);
    }

    public @CheckForNull BuiltRevision lastBuiltOnBranch(String branch) {
        return revisions.get(branch);
    }

    public Collection<BuiltRevision> getDetached() {
        return Collections.unmodifiableCollection(detached);
    }

    public synchronized void addBuild(BuiltRevision revision) throws IOException {
        for (Branch branch : revision.marked.getBranches()) {
            revisions.put(fixNull(branch.getName()), revision);
        }
        for (Branch branch : revision.revision.getBranches()) {
            revisions.put(fixNull(branch.getName()), revision);
        }
        last = revision;
        save();
    }

    public synchronized void addDetached(BuiltRevision revision) throws IOException {
        detached.add(revision);
        last = revision;
        save();
    }


    public void save() throws IOException {
        if (BulkChange.contains(this)) return;
        configFile.write(this);
        SaveableListener.fireOnChange(this, configFile);
    }

    private Object readResolve() {
        if (revisions == null)
            revisions = new HashMap<String, BuiltRevision>();
        if (detached == null)
            detached = new HashSet<BuiltRevision>();
        return this;
    }


    public String getDisplayName() {
        return "Built Git Revisions";
    }

    public String getIconFileName() {
        return Functions.getResourcePath()+"/plugin/git/icons/git-32x32.png";
    }

    public String getUrlName() {
        return "git-built-revisions";
    }

    public boolean hasBeenBuilt(ObjectId sha1) {
        return getBuildFor(sha1) != null;
    }

    public BuiltRevision getBuildFor(ObjectId sha1) {
        // fast check
        if (last != null && (last.revision.equals(sha1) || last.marked.equals(sha1))) return last;
        for(BuiltRevision b : revisions.values()) {
            if(b.revision.getSha1().equals(sha1) || b.marked.getSha1().equals(sha1))
                return b;
        }
        return null;
    }

    public BuiltRevision getLastBuiltRevision() {
        return last;
    }

    @Override
    public String toString() {
        return "BuiltRevisionMap{" +
                "revisions=" + revisions +
                ", detached=" + detached +
                ", last=" + last +
                '}';
    }
}
