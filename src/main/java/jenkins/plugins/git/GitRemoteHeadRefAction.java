package jenkins.plugins.git;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.model.InvisibleAction;
import java.io.Serializable;

/**
 * @author Stephen Connolly
 */
public class GitRemoteHeadRefAction extends InvisibleAction implements Serializable {

    private static final long serialVersionUID = 1L;

    @NonNull
    private final String remote;
    @NonNull
    private final String name;

    public GitRemoteHeadRefAction(@NonNull String remote, @NonNull String name) {
        this.remote = remote;
        this.name = name;
    }

    @NonNull
    public String getRemote() {
        return remote;
    }

    @NonNull
    public String getName() {
        return name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        GitRemoteHeadRefAction that = (GitRemoteHeadRefAction) o;

        if (!remote.equals(that.remote)) {
            return false;
        }
        return name.equals(that.name);
    }

    @Override
    public int hashCode() {
        int result = remote.hashCode();
        result = 31 * result + name.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "GitRemoteHeadRefAction{" +
                "remote='" + remote + '\'' +
                ", name='" + name + '\'' +
                '}';
    }


}
