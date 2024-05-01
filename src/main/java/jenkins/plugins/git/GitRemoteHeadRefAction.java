package jenkins.plugins.git;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.model.InvisibleAction;
import java.io.Serializable;
import java.util.Objects;

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

        return Objects.equals(remote, that.remote)
                && Objects.equals(name, that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(remote, name);
    }

    @Override
    public String toString() {
        return "GitRemoteHeadRefAction{" +
                "remote='" + remote + '\'' +
                ", name='" + name + '\'' +
                '}';
    }


}
