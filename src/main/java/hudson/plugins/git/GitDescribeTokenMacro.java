package hudson.plugins.git;

import hudson.Extension;
import hudson.model.AbstractBuild;
import hudson.model.TaskListener;
import hudson.plugins.git.util.BuildData;
import hudson.scm.SCM;
import org.jenkinsci.plugins.tokenmacro.DataBoundTokenMacro;
import org.jenkinsci.plugins.tokenmacro.MacroEvaluationException;

import java.io.IOException;

/**
 * {@code GIT_DESCRIBE} token that expands to the description of a commit using the most recent tag reachable from it.
 *
 * @author Szymon Sasin
 */
@Extension(optional = true)
public class GitDescribeTokenMacro extends DataBoundTokenMacro {

    @Override
    public boolean acceptsMacroName(String macroName) {
        return "GIT_DESCRIBE".equals(macroName);
    }

    @Override
    public String evaluate(AbstractBuild<?, ?> ctx, TaskListener listener, String macroName) throws MacroEvaluationException, IOException, InterruptedException {

        SCM scm = ctx.getProject().getScm();
        if (!(scm instanceof GitSCM)) {
            return "error: not a git";
        }
        return ((GitSCM) scm).readGitDescribe(ctx, listener);
    }

}

