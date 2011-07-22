package hudson.plugins.git;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.Iterables;

import hudson.Util;

import java.util.ArrayList;
import java.util.Collection;

import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;
import org.eclipse.jgit.lib.ObjectId;

/**
 * A Revision is a SHA1 in the object tree, and the collection of branches that
 * share this ID. Unlike other SCMs, git can have >1 branches point at the
 * _same_ commit.
 * 
 * @author magnayn
 */
@ExportedBean(defaultVisibility = 999)
public class Revision implements java.io.Serializable, Cloneable {
    private static final long serialVersionUID = -7203898556389073882L;

    ObjectId           sha1;
    Collection<Branch> branches;

    public Revision(ObjectId sha1) {
        this.sha1 = sha1;
        this.branches = new ArrayList<Branch>();
    }

    public Revision(ObjectId sha1, Collection<Branch> branches) {
        this.sha1 = sha1;
        this.branches = branches;
    }

    public ObjectId getSha1() {
        return sha1;
    }

    @Exported(name = "SHA1")
    public String getSha1String() {
        return sha1 == null ? "" : sha1.name();
    }

    public void setSha1(ObjectId sha1) {
        this.sha1 = sha1;
    }

    @Exported(name = "branch")
    public Collection<Branch> getBranches() {
        return branches;
    }

    public void setBranches(Collection<Branch> branches) {
        this.branches = branches;
    }

    public boolean containsBranchName(String name) {
        for (Branch b : branches) {
            if (b.getName().equals(name)) {
                return true;
            }
        }
        return false;
    }

    public String toString() {
        StringBuilder s = new StringBuilder("Revision " + sha1.name() + " (");
        Joiner.on(", ").appendTo(s,
                Iterables.transform(branches, new Function<Branch, String>() {

                    public String apply(Branch from) {
                        return Util.fixNull(from.getName());
                    }
                }));
        s.append(')');
        return s.toString();
    }

    @Override
    public Revision clone() {
        Revision clone;
	try {
            clone = (Revision) super.clone();
        }
        catch (CloneNotSupportedException e) {
            throw new RuntimeException("Error cloning Revision", e);
        }
        clone.branches = new ArrayList<Branch>(branches);
        return clone;
    }

}
