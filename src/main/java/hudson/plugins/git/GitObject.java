package hudson.plugins.git;
import java.io.Serializable;

import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;
import org.eclipse.jgit.lib.ObjectId;

@ExportedBean(defaultVisibility = 999)
public class GitObject implements Serializable {

    private static final long serialVersionUID = 1L;

    ObjectId sha1;
    String name;

    public GitObject(String name, ObjectId sha1) {
        this.name = name;
        this.sha1 = sha1;
    }

    public ObjectId getSHA1() {
        return sha1;
    }

    @Exported
    public String getName() {
        return name;
    }

    @Exported(name="SHA1")
    public String getSHA1String() {
        return sha1.name();
    }
}
