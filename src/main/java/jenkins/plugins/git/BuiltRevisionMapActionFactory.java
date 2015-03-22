package jenkins.plugins.git;

import hudson.Extension;
import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.model.TransientProjectActionFactory;
import hudson.plugins.git.GitSCM;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;

/**
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 */
@Extension
public class BuiltRevisionMapActionFactory extends TransientProjectActionFactory {

    @Override
    public Collection<? extends Action> createFor(AbstractProject target) {
        if (target.getScm() instanceof GitSCM)
            try {
                return Collections.singleton(BuiltRevisionMap.forProject(target));
            } catch (IOException e) {
                // FIXME
        }
        return Collections.emptyList();
    }
}
