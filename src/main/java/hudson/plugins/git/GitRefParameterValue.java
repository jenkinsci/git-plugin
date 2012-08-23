package hudson.plugins.git;

import hudson.EnvVars;
import hudson.model.ParameterValue;
import hudson.model.AbstractBuild;
import hudson.util.VariableResolver;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.export.Exported;

public class GitRefParameterValue extends ParameterValue {

    @Exported
    private String repo;

    @Exported
    private String ref;

    @DataBoundConstructor
    public GitRefParameterValue(String name, String repo, String ref) {
        super(name);
        this.repo = repo;
        this.ref = ref;
    }

    @Override
    public void buildEnvVars(AbstractBuild<?, ?> build, EnvVars env) {
        env.put(getName(), getRef());
    }

    @Override
    public VariableResolver<String> createVariableResolver(AbstractBuild<?, ?> build) {
        return new VariableResolver<String>() {
            public String resolve(String name) {
                return GitRefParameterValue.this.name.equals(name) ? getRef() : null;
            }
        };
    }

    public String getRepo() {
        return repo;
    }

    public void setRepo(String repo) {
        this.repo = repo;
    }

    public String getRef() {
        return ref;
    }

    public void setRef(String ref) {
        this.ref = ref;
    }

}
