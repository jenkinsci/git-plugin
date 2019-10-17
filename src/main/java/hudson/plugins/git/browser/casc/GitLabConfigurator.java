package hudson.plugins.git.browser.casc;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.plugins.git.browser.GitLab;
import io.jenkins.plugins.casc.BaseConfigurator;
import io.jenkins.plugins.casc.ConfigurationContext;
import io.jenkins.plugins.casc.Configurator;
import io.jenkins.plugins.casc.ConfiguratorException;
import io.jenkins.plugins.casc.model.CNode;
import io.jenkins.plugins.casc.model.Mapping;
import org.apache.commons.lang.StringUtils;

import java.util.Collections;
import java.util.List;

@Extension(optional = true)
public class GitLabConfigurator extends BaseConfigurator<GitLab> {

    @Override
    protected GitLab instance(Mapping mapping, ConfigurationContext context) throws ConfiguratorException {
        if (mapping == null) {
            return new GitLab("", "");
        }
        final String url = (mapping.get("repoUrl") != null ? mapping.getScalarValue("repoUrl") : "");
        final String version = (mapping.get("version") != null ? mapping.getScalarValue("version") : "");
        return new GitLab(url, version);
    }

    @CheckForNull
    @Override
    public CNode describe(GitLab instance, ConfigurationContext context) throws Exception {
        Mapping mapping = new Mapping();
        mapping.put("repoUrl", StringUtils.defaultIfBlank(instance.getRepoUrl(), ""));
        mapping.put("version", String.valueOf(instance.getVersion()));
        return mapping;
    }

    @Override
    public boolean canConfigure(Class clazz) {
        return clazz == GitLab.class;
    }

    @Override
    public Class<GitLab> getTarget() {
        return GitLab.class;
    }

    @NonNull
    @Override
    public List<Configurator<GitLab>> getConfigurators(ConfigurationContext context) {
        return Collections.singletonList(this);
    }

}
