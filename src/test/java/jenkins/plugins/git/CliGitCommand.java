package jenkins.plugins.git;

import hudson.EnvVars;
import hudson.Launcher;
import hudson.model.TaskListener;
import hudson.util.ArgumentListBuilder;
import hudson.util.StreamTaskListener;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.jenkinsci.plugins.gitclient.GitClient;

import org.junit.Assert;

/**
 * Run a command line git command, return output as array of String, optionally
 * assert on contents of command output.
 *
 * @author Mark Waite
 */
class CliGitCommand {

    private final TaskListener listener;
    private final transient Launcher launcher;
    private final EnvVars env;
    private final File dir;
    private String[] output;
    private ArgumentListBuilder args;

    CliGitCommand(GitClient client, String... arguments) {
        args = new ArgumentListBuilder("git");
        args.add(arguments);
        listener = StreamTaskListener.NULL;
        launcher = new Launcher.LocalLauncher(listener);
        env = new EnvVars();
        if (client != null) {
            dir = client.getRepository().getWorkTree();
        } else {
            dir = new File(".");
        }
    }

    public String[] run(String... arguments) throws IOException, InterruptedException {
        args = new ArgumentListBuilder("git");
        args.add(arguments);
        return run(true);
    }

    public String[] run() throws IOException, InterruptedException {
        return run(true);
    }

    private String[] run(boolean assertProcessStatus) throws IOException, InterruptedException {
        ByteArrayOutputStream bytesOut = new ByteArrayOutputStream();
        ByteArrayOutputStream bytesErr = new ByteArrayOutputStream();
        Launcher.ProcStarter p = launcher.launch().cmds(args).envs(env).stdout(bytesOut).stderr(bytesErr).pwd(dir);
        int status = p.start().joinWithTimeout(1, TimeUnit.MINUTES, listener);
        String result = bytesOut.toString("UTF-8");
        if (bytesErr.size() > 0) {
            result = result + "\nstderr not empty:\n" + bytesErr.toString("UTF-8");
        }
        output = result.split("[\\n\\r]");
        if (assertProcessStatus) {
            Assert.assertEquals(args.toString() + " command failed and reported '" + Arrays.toString(output) + "'", 0, status);
        }
        return output;
    }

    public void assertOutputContains(String... expectedRegExes) {
        List<String> notFound = new ArrayList<>();
        boolean modified = notFound.addAll(Arrays.asList(expectedRegExes));
        Assert.assertTrue("Missing regular expressions in assertion", modified);
        for (String line : output) {
            for (Iterator<String> iterator = notFound.iterator(); iterator.hasNext();) {
                String regex = iterator.next();
                if (line.matches(regex)) {
                    iterator.remove();
                }
            }
        }
        if (!notFound.isEmpty()) {
            Assert.fail(Arrays.toString(output) + " did not match all strings in notFound: " + Arrays.toString(expectedRegExes));
        }
    }

    private String[] runWithoutAssert(String... arguments) throws IOException, InterruptedException {
        args = new ArgumentListBuilder("git");
        args.add(arguments);
        return run(false);
    }

    private void setConfigIfEmpty(String configName, String value) throws Exception {
        String[] cmdOutput = runWithoutAssert("config", configName);
        if (cmdOutput == null || cmdOutput[0].isEmpty() || cmdOutput[0].equals("[]")) {
            /* Set config value globally */
            cmdOutput = run("config", "--global", configName, value);
            /* Read config value */
            cmdOutput = run("config", configName);
            if (cmdOutput == null || cmdOutput[0].isEmpty() || !cmdOutput[0].equals(value)) {
                System.out.println("ERROR: git config " + configName + " reported '" + cmdOutput[0] + "' instead of '" + value + "'");
            }
        }
    }

    /**
     * Set git config values for user.name and user.email if they are not
     * already set. Many tests assume that "git commit" can be called without
     * failure, but a newly installed user account does not necessarily have
     * values assigned for user.name and user.email. This method checks the
     * existing values, and if they are not set, assigns default values.
     * If the values are already set, they are unchanged.
     *
     * @param userName user name to be defined (if value not already set)
     * @param userEmail email address to be defined (if value not already set)
     */
    public void setDefaults() throws Exception {
        setConfigIfEmpty("user.name", "Vojtěch-Zweibrücken-Šafařík");
        setConfigIfEmpty("user.email", "email.address.from.git.plugin.test@example.com");
    }
}
