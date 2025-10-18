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
import hudson.plugins.git.util.GitUtilsTest;
import hudson.util.ArgumentListBuilder;
import hudson.util.StreamTaskListener;
import hudson.plugins.git.GitException;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.eclipse.jgit.lib.Repository;
import static org.hamcrest.Matchers.hasItems;
import static org.junit.jupiter.api.Assertions.*;

import org.jenkinsci.plugins.gitclient.GitClient;

import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Run a command line git command, return output as array of String, optionally
 * assert on contents of command output.
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

    public CliGitCommand(GitClient client, String... arguments) throws GitException {
        this(client, GitUtilsTest.getConfigNoSystemEnvsVars(), arguments);
    }

    public CliGitCommand(GitClient client, EnvVars envVars, String... arguments) throws GitException {
        args = new ArgumentListBuilder("git");
        args.add(arguments);
        listener = StreamTaskListener.NULL;
        launcher = new Launcher.LocalLauncher(listener);
        env = envVars;
        if (client != null) {
            try (@SuppressWarnings("deprecation") // Local repository reference
                 Repository repo = client.getRepository()) {
                dir = repo.getWorkTree();
            }
        } else {
            dir = new File(".");
        }
    }

    public String[] run(boolean checkForErrors, String... arguments) throws IOException, InterruptedException {
        args = new ArgumentListBuilder("git");
        args.add(arguments);
        return run(checkForErrors);
    }

    public String[] run(String... arguments) throws IOException, InterruptedException {
        return run(true, arguments);
    }

    public String[] run() throws IOException, InterruptedException {
        return run(true);
    }

    private String[] run(boolean assertProcessStatus) throws IOException, InterruptedException {
        ByteArrayOutputStream bytesOut = new ByteArrayOutputStream();
        ByteArrayOutputStream bytesErr = new ByteArrayOutputStream();
        Launcher.ProcStarter p = launcher.launch().cmds(args).envs(env).stdout(bytesOut).stderr(bytesErr).pwd(dir);
        int status = p.start().joinWithTimeout(1, TimeUnit.MINUTES, listener);
        String result = bytesOut.toString(StandardCharsets.UTF_8);
        if (bytesErr.size() > 0) {
            result = result + "\nstderr not empty:\n" + bytesErr.toString(StandardCharsets.UTF_8);
        }
        output = result.split("[\\n\\r]");
        if (assertProcessStatus) {
            assertEquals(0, status, args.toString() + " command failed and reported '" + Arrays.toString(output) + "'");
        }
        return output;
    }

    public void assertOutputContains(String... expectedRegExes) {
        List<String> notFound = new ArrayList<>();
        boolean modified = notFound.addAll(Arrays.asList(expectedRegExes));
        assertTrue(modified, "Missing regular expressions in assertion");
        for (String line : output) {
            notFound.removeIf(line::matches);
        }
        if (!notFound.isEmpty()) {
            fail(Arrays.toString(output) + " did not match all strings in notFound: " + Arrays.toString(expectedRegExes));
        }
    }

    private void setConfigIfEmpty(String configName, String value) throws Exception {
        boolean checkForErrors = false;
        String[] cmdOutput = run(checkForErrors, "config", "--global", configName);
        if (cmdOutput == null || cmdOutput[0].isEmpty() || cmdOutput[0].equals("[]")) {
            /* Set config value globally */
            cmdOutput = run("config", "--global", configName, value);
            assertThat(Arrays.asList(cmdOutput), hasItems(""));
            /* Read config value */
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
     * existing values when run in a Jenkins job, and if they are not set,
     * assigns default values. If the
     * values are already set, they are unchanged.
     * @throws Exception on error
     */
    public void setDefaults() throws Exception {
        if (System.getenv("JENKINS_URL") != null && System.getenv("BUILD_NUMBER") != null) {
            /* We're in a Jenkins agent environment */
	    setConfigIfEmpty("user.name", "Name From Git-Plugin-Test");
	    setConfigIfEmpty("user.email", "email.from.git.plugin.test@example.com");
	}
    }

    /**
     * This will add env value to the process running the command line
     * @param key env var name
     * @param value env var value
     * @return the current {@link CliGitCommand}
     */
    public CliGitCommand env(String key, String value) {
        env.put(key, value);
        return this;
    }

}
