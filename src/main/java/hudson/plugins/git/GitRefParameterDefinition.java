package hudson.plugins.git;

import hudson.Extension;
import hudson.Launcher;
import hudson.Launcher.LocalLauncher;
import hudson.model.ParameterValue;
import hudson.model.SimpleParameterDefinition;
import hudson.model.TaskListener;
import hudson.model.Hudson;
import hudson.util.ArgumentListBuilder;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.sf.json.JSONObject;

import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

public class GitRefParameterDefinition extends SimpleParameterDefinition {

    private static final long serialVersionUID = 7966621119782624226L;
    private String repo;

    @DataBoundConstructor
    public GitRefParameterDefinition(String name, String repo) {
        super(name, "Select a Git reference (Repository URL: " + repo + ")");
        this.repo = repo;
    }

    @Override
    public ParameterValue createValue(StaplerRequest req, JSONObject jo) {
        GitRefParameterValue value = req.bindJSON(GitRefParameterValue.class,
                jo);
        return value;
    }

    @Override
    public ParameterValue createValue(String ref) {
        return new GitRefParameterValue(getName(), getRepo(), ref);
    }

    @Extension
    public static class DescriptorImpl extends ParameterDescriptor {
        @Override
        public String getDisplayName() {
            return "Git Reference Selector";
        }
    }

    public List<String> getRefs() {
        ArrayList<String> refs = new ArrayList<String>();

        // Identify Git command
        hudson.plugins.git.GitTool.DescriptorImpl descriptor = (hudson.plugins.git.GitTool.DescriptorImpl) Hudson
                .getInstance().getDescriptor(GitTool.class);
        GitTool[] tools = descriptor.getInstallations();
        if (tools.length < 1) {
            // TODO indicate that no Git tool could be located
        }
        GitTool tool = tools[0]; // Arbitrarily use the first tool presented.

        // Assemble Git command
        ArgumentListBuilder args = new ArgumentListBuilder("ls-remote",
                "--heads", "--tags", repo);

        // Execute Git command
        String result = null;
        ByteArrayOutputStream fos = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        try {
            Launcher launcher = new LocalLauncher(TaskListener.NULL); // TODO
            // GitSCM.VERBOSE?listener:TaskListener.NULL);
            args.prepend(tool.getGitExe()); // TODO error check the getter
            int status = launcher.launch().cmds(args.toCommandArray())
                    .stdout(fos).stderr(err).pwd("/tmp").join(); // TODO Get tmp
            // dir from
            // Java
            result = fos.toString();
            if (status != 0) {
                throw new GitException("Command \""
                        + StringUtils.join(args.toCommandArray(), " ")
                        + "\" returned status code " + status + ":\nstdout: "
                        + result + "\nstderr: " + err.toString());
            }
        } catch (GitException e) {
            throw e;
        } catch (Exception e) {
            throw new GitException("Error performing command: "
                    + StringUtils.join(args.toCommandArray(), " "), e);
        }

        // Match refs
        BufferedReader rdr = new BufferedReader(new StringReader(result));
        String line;
        try {
            Pattern patty = Pattern.compile("^([0-9a-f]{40})\t(refs/.*)$");
            while ((line = rdr.readLine()) != null) {
                Matcher matcher = patty.matcher(line);
                if (matcher.matches()) {
                    String ref = matcher.group(2);
                    refs.add(ref);
                }
            }
        } catch (IOException e) {
            throw new GitException("Error parsing ref list", e);
        }

        return refs;
    }

    public String getRepo() {
        return repo;
    }

    public void setRepo(String repo) {
        this.repo = repo;
    }

}
