package hudson.plugins.git;

import hudson.model.AbstractProject;
import hudson.scm.SCM;
import org.jenkinsci.plugins.multiplescms.MultiSCM;

import java.util.List;
import java.util.Set;

/**
 * @author Noam Y. Tenne
 */
public class MultipleScmResolver {

    public void resolveMultiScmIfConfigured(AbstractProject<?, ?> project, Set<SCM> projectScms) {
        SCM projectScm = project.getScm();
        if (projectScm instanceof MultiSCM) {
            List<SCM> configuredSCMs = ((MultiSCM) projectScm).getConfiguredSCMs();
            for (SCM configuredSCM : configuredSCMs) {
                if (configuredSCM instanceof GitSCM) {
                    projectScms.add(configuredSCM);
                }
            }

        }
    }
}
