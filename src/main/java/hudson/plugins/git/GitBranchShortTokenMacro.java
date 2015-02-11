package hudson.plugins.git;

import hudson.Extension;
import hudson.model.AbstractBuild;
import hudson.model.TaskListener;
import hudson.plugins.git.util.BuildData;
import org.jenkinsci.plugins.tokenmacro.DataBoundTokenMacro;
import org.jenkinsci.plugins.tokenmacro.MacroEvaluationException;

import java.io.IOException;

/**
 * {@code GIT_BRANCH_SHORT} token that expands to the plain branch(es) that was built.
 *
 * @author Horst Krause
 */
@Extension(optional=true)
public class GitBranchShortTokenMacro extends GitBranchTokenMacro {

    @Override
    public boolean acceptsMacroName(String macroName) {

        return macroName.equals("GIT_BRANCH_SHORT");
    }

    @Override
    public String evaluate(AbstractBuild<?, ?> context, TaskListener listener, String macroName) throws MacroEvaluationException, IOException, InterruptedException {

        String name = super.evaluate(context, listener, macroName);

        // if single branch and not fullName requested, then remove everything except the plain branch name
        if ((!all) && (!fullName) && (name.lastIndexOf(GitSCM.ORIGIN + "/") > -1)) {
            name = name.substring(name.lastIndexOf(GitSCM.ORIGIN + "/") + GitSCM.ORIGIN.length() + 1);
        }
        return name;
    }

}
