package hudson.plugins.git;

import hudson.Extension;
import hudson.model.AbstractBuild;
import hudson.model.TaskListener;
import hudson.scm.SCM;
import org.jenkinsci.plugins.tokenmacro.DataBoundTokenMacro;
import org.jenkinsci.plugins.tokenmacro.MacroEvaluationException;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * {@code GIT_DESCRIBE} token that expands to the description of a commit using the most recent tag reachable from it.
 *
 * @author Szymon Sasin
 */
@Extension(optional = true)
public class GitDescribeTokenMacro extends DataBoundTokenMacro {

    /**
     * Include tag part (default=true)
     */
    @Parameter
    public boolean tag = true;

    /**
     * Include distance to a tag part (default=true)
     */
    @Parameter
    public boolean distance = true;

    /**
     * Include revision part (default=true)
     */
    @Parameter()
    public boolean revision = true;

    public GitDescribeTokenMacro() {

    }

    GitDescribeTokenMacro(boolean tag, boolean distance, boolean revision) {
        this.tag = tag;
        this.distance = distance;
        this.revision = revision;
    }

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
        String gitDescribe = ((GitSCM) scm).readGitDescribe(ctx, listener);

        return parse(gitDescribe);
    }

    private String parse(String gitDescribe) {
        if (tag && distance && revision) {
            return gitDescribe;
        }

        Pattern p = Pattern.compile("(.+)-(\\d+)-g(.*)");
        Matcher matcher = p.matcher(gitDescribe);
        if (matcher.find()) {

            StringBuilder gitDesc = new StringBuilder();
            if (tag) {
                gitDesc.append(matcher.group(1));
            }
            if (distance) {
                if (gitDesc.length() != 0) gitDesc.append('-');
                gitDesc.append(matcher.group(2));
            }
            if (revision) {
                if (gitDesc.length() != 0) gitDesc.append("-g");
                gitDesc.append(matcher.group(3));
            }

            return gitDesc.toString();
        }

        if (tag){
            return gitDescribe;
        } else {
            return "";
        }
    }

}

