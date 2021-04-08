package hudson.plugins.git.extensions.impl;

import com.google.common.base.Function;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.EnvVars;
import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.scriptsecurity.sandbox.whitelists.Whitelisted;
import org.kohsuke.stapler.DataBoundConstructor;

import javax.annotation.Nullable;
import java.io.Serializable;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SparseCheckoutPath extends AbstractDescribableImpl<SparseCheckoutPath> implements Serializable {

    private static final long serialVersionUID = -6177158367915899356L;

    @SuppressFBWarnings(value="SE_TRANSIENT_FIELD_NOT_RESTORED", justification="Default value is OK in deserialization")
    public static final transient SparseCheckoutPathToPath SPARSE_CHECKOUT_PATH_TO_PATH = new SparseCheckoutPathToPath(null);

    private final String path;

    @DataBoundConstructor
    public SparseCheckoutPath(String path) {
        this.path = path;
    }

    @Whitelisted
    public String getPath() {
        return path;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        SparseCheckoutPath that = (SparseCheckoutPath) o;

        return Objects.equals(path, that.path);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(path);
    }

    @Override
    public String toString() {
        return path;
    }

    public static class SparseCheckoutPathToPath implements Function<SparseCheckoutPath, String>, Serializable {
        @Nullable
        private EnvVars envVars;

        SparseCheckoutPathToPath(@Nullable EnvVars envVars) {
            this.envVars = envVars;
        }

        public String apply(@NonNull SparseCheckoutPath sparseCheckoutPath) {
            String path = sparseCheckoutPath.getPath();
            if (envVars == null) {
                return path;
            }

            // Pattern to look for substring of the form ${ENV_VAR}.
            Pattern pattern = Pattern.compile("\\$\\{(.*?)\\}");
            Matcher matcher = pattern.matcher(path);
            while (matcher.find()) {
                String varName = matcher.group(1);
                String value = envVars.get(varName, "");
                path = path.replace("${" + varName + "}", value);
            }
            return path;
        }
    }

    public Descriptor<SparseCheckoutPath> getDescriptor()
    {
        return Jenkins.get().getDescriptor(getClass());
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<SparseCheckoutPath> {
        @Override
        public String getDisplayName() { return "Path"; }
    }
}
