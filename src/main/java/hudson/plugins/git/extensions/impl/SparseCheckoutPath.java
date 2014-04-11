package hudson.plugins.git.extensions.impl;

import com.google.common.base.Function;
import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.model.Hudson;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.Serializable;

public class SparseCheckoutPath extends AbstractDescribableImpl<SparseCheckoutPath> implements Serializable {

    private static final long serialVersionUID = -6177158367915899356L;

    public static final transient SparseCheckoutPathToPath SPARSE_CHECKOUT_PATH_TO_PATH = new SparseCheckoutPathToPath();

    private String path;

    @DataBoundConstructor
    public SparseCheckoutPath(String path) {
        this.path = path;
    }

    public String getPath() {
        return path;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SparseCheckoutPath)) return false;

        SparseCheckoutPath that = (SparseCheckoutPath) o;

        return path.equals(that.path);

    }

    @Override
    public int hashCode() {
        return path.hashCode();
    }

    @Override
    public String toString() {
        return path;
    }

    private static class SparseCheckoutPathToPath implements Function<SparseCheckoutPath, String>, Serializable {
        public String apply(SparseCheckoutPath sparseCheckoutPath) {
            return sparseCheckoutPath.getPath();
        }
    }

    public Descriptor<SparseCheckoutPath> getDescriptor()
    {
        return Hudson.getInstance().getDescriptor(getClass());
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<SparseCheckoutPath> {
        @Override
        public String getDisplayName() { return "Path"; }
    }
}
