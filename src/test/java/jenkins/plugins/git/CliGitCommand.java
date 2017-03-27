/*
 * The MIT License
 *
 * Copyright 2016-2017 Mark Waite.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package jenkins.plugins.git;

import hudson.EnvVars;
import hudson.Launcher;
import hudson.model.TaskListener;
import hudson.util.ArgumentListBuilder;
import hudson.util.StreamTaskListener;
import hudson.plugins.git.GitException;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import static org.hamcrest.Matchers.hasItems;
import org.jenkinsci.plugins.gitclient.GitClient;
import org.junit.Assert;
import static org.junit.Assert.assertThat;

/**
 * Run git commands, capture output, and assert contents of output.
 *
 * @author Mark Waite
 */
public class CliGitCommand {

    private final TaskListener listener;
    private final transient Launcher launcher;
    private final EnvVars env;
    private final File dir;
    private String[] output;
    private ArgumentListBuilder args;

    public CliGitCommand(GitClient client, String... arguments) {
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
        String[] cmdOutput = runWithoutAssert("config", "--global", configName);
        if (cmdOutput == null || cmdOutput[0].isEmpty() || cmdOutput[0].equals("[]")) {
            /* Set config value globally */
            cmdOutput = run("config", "--global", configName, value);
            assertThat(Arrays.asList(cmdOutput), hasItems(""));
            cmdOutput = run("config", "--global", configName);
            if (cmdOutput == null || cmdOutput[0].isEmpty() || !cmdOutput[0].equals(value)) {
                throw new GitException("ERROR: git config --global " + configName + " reported '" + cmdOutput[0] + "' instead of '" + value + "'");
            }
        }
    }

    /**
     * Set git config values for user.name and user.email if they are not
     * already set. Many tests assume that "git commit" can be called without
     * failure, but a newly installed user account does not necessarily have
     * values assigned for user.name and user.email. This method checks the
     * existing values, and if they are not set, assigns default values. If the
     * values are already set, they are unchanged.
     * @throws java.lang.Exception
     */
    public void setDefaults() throws Exception {
        setConfigIfEmpty("user.name", "Name From Git-Plugin-Test");
        setConfigIfEmpty("user.email", "email.from.git.plugin.test@example.com");
    }
}
