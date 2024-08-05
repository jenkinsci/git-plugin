/*
 * The MIT License
 *
 * Copyright 2015 CloudBees, Inc.
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

import edu.umd.cs.findbugs.annotations.CheckForNull;
import org.htmlunit.WebResponse;
import org.htmlunit.util.NameValuePair;
import hudson.Launcher;
import hudson.model.TaskListener;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.ApiTokenPropertyConfiguration;
import hudson.util.StreamTaskListener;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.scm.impl.mock.AbstractSampleDVCSRepoRule;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.RepositoryBuilder;
import org.jvnet.hudson.test.JenkinsRule;

/**
 * Manages a sample Git repository.
 */
public final class GitSampleRepoRule extends AbstractSampleDVCSRepoRule {

    private static boolean initialized = false;

    private static final Logger LOGGER = Logger.getLogger(GitSampleRepoRule.class.getName());

    public static final String INVALID_NOTIFY_COMMIT_TOKEN = "invalid-notifyCommit-token";

    @Override
    public void before() throws Throwable {
        super.before();
        GitSCM.ALLOW_LOCAL_CHECKOUT = true;
    }

    @Override
    public void after() {
        super.after();
        GitSCM.ALLOW_LOCAL_CHECKOUT = false;
    }

    public void git(String... cmds) throws Exception {
        run("git", cmds);
    }

    private static void checkGlobalConfig() throws Exception {
        if (initialized) return;
        initialized = true;
        CliGitCommand gitCmd = new CliGitCommand(null);
        gitCmd.setDefaults();
    }

    @Override
    public void init() throws Exception {
        run(true, tmp.getRoot(), "git", "version");
        checkGlobalConfig();
        git("init", "--template="); // initialize without copying the installation defaults to ensure a vanilla repo that behaves the same everywhere
	if (gitVersionAtLeast(2, 30)) {
	    // Force branch name to master even if system default is not master
	    // Fails on git 2.25, 2.20, and 2.17
	    // Works on git 2.30 and later
            git("branch", "-m", "master");
	}
        write("file", "");
        git("add", "file");
        git("config", "user.name", "Git SampleRepoRule");
        git("config", "user.email", "gits@mplereporule");
        git("config", "init.defaultbranch", "master");
        git("config", "commit.gpgsign", "false");
        git("config", "tag.gpgSign", "false");
        git("commit", "--message=init");
    }

    public final boolean mkdirs(String rel) throws IOException {
        return new File(this.sampleRepo, rel).mkdirs();
    }

    public void notifyCommit(JenkinsRule r) throws Exception {
        synchronousPolling(r);
        String notifyCommitToken = ApiTokenPropertyConfiguration.get().generateApiToken("notifyCommit").getString("value");
        WebResponse webResponse = r.createWebClient()
                .goTo("git/notifyCommit?url=" + bareUrl() + "&token=" + notifyCommitToken, "text/plain").getWebResponse();
        LOGGER.log(Level.FINE, webResponse.getContentAsString());
        for (NameValuePair pair : webResponse.getResponseHeaders()) {
            if (pair.getName().equals("Triggered")) {
                LOGGER.log(Level.FINE, "Triggered: " + pair.getValue());
            }
        }
        r.waitUntilNoActivity();
    }

    public String notifyCommitWithResults(JenkinsRule r) throws Exception {
        String notifyCommitToken = ApiTokenPropertyConfiguration.get().generateApiToken("notifyCommit").getString("value");
        return notifyCommitWithResults(r, notifyCommitToken, null);
    }

    public String notifyCommitWithResults(JenkinsRule r, @CheckForNull String notifyCommitToken) throws Exception {
        return notifyCommitWithResults(r, notifyCommitToken, null);
    }

    /**
     * Use WebClient to call notifyCommit on the current repository.
     *
     * If the caller expects an error and does not want an
     * exception thrown by the web response, the notifyCommitToken
     * must contain the invalid notifyCommit token string.
     *
     * If the caller wants to pass no access token, the
     * notifyCommitToken needs to be null
     *
     * If the caller wants to pass no SHA-1, the sha1 parameter needs to be null.
     *
     * @param r JenkinsRule to receive the commit notification
     * @param notifyCommitToken token used for notifyCommit authentication
     * @param sha1 SHA-1 hash to included in notifyCommit
     **/
    public String notifyCommitWithResults(JenkinsRule r, @CheckForNull String notifyCommitToken, @CheckForNull String sha1) throws Exception {
        boolean expectError = notifyCommitToken == null || notifyCommitToken.contains(INVALID_NOTIFY_COMMIT_TOKEN);
        synchronousPolling(r);
        JenkinsRule.WebClient webClient = r.createWebClient();
        if (expectError) {
            /* Return without exception on failing status code */
            webClient.getOptions().setThrowExceptionOnFailingStatusCode(false);
            /* Do not clutter output with failures that are expected and checked by the caller */
            webClient.getOptions().setPrintContentOnFailingStatusCode(false);
        }
        String responseFormat = expectError ? "text/html" : "text/plain";
        String tokenArgument = notifyCommitToken != null ? "&token=" + notifyCommitToken : "";
        String sha1Argument = sha1 != null ? "&sha1=" + sha1 : "";

        WebResponse webResponse = webClient.goTo("git/notifyCommit?url=" + bareUrl() + tokenArgument + sha1Argument, responseFormat).getWebResponse();
        StringBuilder sb = new StringBuilder(webResponse.getContentAsString());
        if (!expectError) {
            LOGGER.log(Level.FINE, sb.toString());
        }

        for (NameValuePair pair : webResponse.getResponseHeaders()) {
            if (pair.getName().equals("Triggered")) {
                sb.append('\n');
                sb.append("Triggered: ");
                sb.append(pair.getValue());
                if (!expectError) {
                    LOGGER.log(Level.FINE, "Triggered: " + pair.getValue());
                }
            }
        }
        r.waitUntilNoActivity();
        return sb.toString();
    }

    public String head() throws Exception {
        return new RepositoryBuilder().setWorkTree(sampleRepo).build().resolve(Constants.HEAD).name();
    }

    public File getRoot() {
        return this.sampleRepo;
    }

    public boolean gitVersionAtLeast(int neededMajor, int neededMinor) {
        return gitVersionAtLeast(neededMajor, neededMinor, 0);
    }

    public boolean gitVersionAtLeast(int neededMajor, int neededMinor, int neededPatch) {
        final TaskListener procListener = StreamTaskListener.fromStderr();
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            int returnCode = new Launcher.LocalLauncher(procListener).launch().cmds("git", "--version").stdout(out).join();
            if (returnCode != 0) {
                LOGGER.log(Level.WARNING, "Command 'git --version' returned " + returnCode);
            }
        } catch (IOException | InterruptedException ex) {
            LOGGER.log(Level.WARNING, "Exception checking git version " + ex);
        }
        final String versionOutput = out.toString().trim();
        final String[] fields = versionOutput.split(" ")[2].replaceAll("msysgit.", "").replaceAll("windows.", "").split("\\.");
        final int gitMajor = Integer.parseInt(fields[0]);
        final int gitMinor = Integer.parseInt(fields[1]);
        final int gitPatch = Integer.parseInt(fields[2]);
        if (gitMajor < 1 || gitMajor > 3) {
            LOGGER.log(Level.WARNING, "Unexpected git major version " + gitMajor + " parsed from '" + versionOutput + "', field:'" + fields[0] + "'");
        }
        if (gitMinor < 0 || gitMinor > 50) {
            LOGGER.log(Level.WARNING, "Unexpected git minor version " + gitMinor + " parsed from '" + versionOutput + "', field:'" + fields[1] + "'");
        }
        if (gitPatch < 0 || gitPatch > 20) {
            LOGGER.log(Level.WARNING, "Unexpected git patch version " + gitPatch + " parsed from '" + versionOutput + "', field:'" + fields[2] + "'");
        }

        return gitMajor >  neededMajor ||
              (gitMajor == neededMajor && gitMinor >  neededMinor) ||
              (gitMajor == neededMajor && gitMinor == neededMinor  && gitPatch >= neededPatch);
    }

    public boolean hasGitLFS() {
        final TaskListener procListener = StreamTaskListener.fromStderr();
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            int returnCode = new Launcher.LocalLauncher(procListener).launch().cmds("git", "lfs", "version").stdout(out).join();
            if (returnCode != 0) {
                return false;
            }
        } catch (IOException | InterruptedException ex) {
            return false;
        }
        final String versionOutput = out.toString().trim();
        return versionOutput.startsWith("git-lfs/");
    }
}
