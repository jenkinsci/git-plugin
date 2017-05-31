package hudson.plugins.git;

import hudson.Launcher;
import hudson.Launcher.LocalLauncher;
import hudson.model.TaskListener;
import hudson.model.Hudson;
import hudson.util.ArgumentListBuilder;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang.StringUtils;

/**
 * Collection of parameterized procedures enabling a Git executable to be
 * used directly, without need for a working directory, etc.
 */
public class GitStandaloneAPI {

    public static final Pattern SHA1_REF_ENTRY = Pattern.compile("^([0-9a-f]{40})[ \t]+(refs/.*)$");

    public static GitTool anyGitTool() {
        final hudson.plugins.git.GitTool.DescriptorImpl descriptor = (hudson.plugins.git.GitTool.DescriptorImpl) Hudson.getInstance().getDescriptor(GitTool.class);
        final GitTool[] tools = descriptor.getInstallations();
        if (tools.length < 1) {
            throw new GitException ("No GitTool found.");
        }

        final GitTool tool = tools[0]; // Arbitrarily use the first tool presented.

        return tool;
    }

    public static String gitExecuteCommand(final GitTool tool, final ArgumentListBuilder args) {
        String result = null;

        final ByteArrayOutputStream fos = new ByteArrayOutputStream();
        final ByteArrayOutputStream err = new ByteArrayOutputStream();
        try {
            final Launcher launcher = new LocalLauncher(TaskListener.NULL);
            final String gitExe = tool.getGitExe();
            if (gitExe == null) {
                throw new GitException("gitExe is null");
            }
            args.prepend(gitExe);
            final int status = launcher.launch().cmds(args.toCommandArray()).stdout(fos).stderr(err).join();
            if (status != 0) {
                throw new GitException("Command \""
                        + StringUtils.join(args.toCommandArray(), " ")
                        + "\" returned status code " + status + ":\nstdout: "
                        + result + "\nstderr: " + err.toString());
            }
            result = fos.toString();
        } catch (GitException e) {
            throw e;
        } catch (Exception e) {
            throw new GitException("Error performing command: " + StringUtils.join(args.toCommandArray(), " "), e);
        }

        return result;
    }

    public static ArrayList<String> gitListReferences(final GitTool tool, final String repositoryURL) {
        final ArrayList<String> refs = new ArrayList<String>();

        // Assemble Git command
        final ArgumentListBuilder args = new ArgumentListBuilder("ls-remote", "--heads", "--tags", repositoryURL);

        final String result = gitExecuteCommand(tool, args);

        // Match refs
        final BufferedReader rdr = new BufferedReader(new StringReader(result));
        String line;
        try {
            while ((line = rdr.readLine()) != null) {
                final Matcher matcher = GitStandaloneAPI.SHA1_REF_ENTRY.matcher(line);
                if (matcher.matches()) {
                    final String ref = matcher.group(2);
                    refs.add(ref);
                }
            }
        } catch (IOException e) {
            throw new GitException("Error parsing ref list", e);
        }

        return refs;
    }

}
