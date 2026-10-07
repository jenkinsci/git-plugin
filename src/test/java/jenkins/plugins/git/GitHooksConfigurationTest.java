/*
 * The MIT License
 *
 * Copyright 2021 CloudBees, Inc.
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
import hudson.model.TaskListener;

import java.io.File;
import java.nio.file.Files;
import java.util.Random;
import org.eclipse.jgit.lib.StoredConfig;
import org.jenkinsci.plugins.gitclient.Git;
import org.jenkinsci.plugins.gitclient.GitClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

import static hudson.Functions.isWindows;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@WithJenkins
class GitHooksConfigurationTest {

    private JenkinsRule r;

    private GitHooksConfiguration configuration;
    private GitClient client;

    private final Random random = new Random();
    private static final String NULL_HOOKS_PATH = isWindows() ? "NUL:" : "/dev/null";

    @BeforeEach
    void beforeEach(JenkinsRule rule) throws Exception {
        r = rule;

        configuration = GitHooksConfiguration.get();
        Git git = Git.with(TaskListener.NULL, new EnvVars());
        client = git.getClient();
    }

    @AfterEach
    void afterEach() throws Exception {
        client.withRepository((repo, channel) -> {
            final StoredConfig repoConfig = repo.getConfig();
            repoConfig.unset("core", null, "hooksPath");
            repoConfig.save();
            return null;
        });
    }

    @Test
    void testGet() {
        assertThat(GitHooksConfiguration.get(), is(configuration));
    }

    @Test
    void testIsAllowedOnController() {
        assertFalse(configuration.isAllowedOnController());
    }

    @Test
    void testSetAllowedOnController() {
        configuration.setAllowedOnController(true);
        assertTrue(configuration.isAllowedOnController());
    }

    @Test
    void testSetAllowedOnControllerFalse() {
        configuration.setAllowedOnController(false);
        assertFalse(configuration.isAllowedOnController());
    }

    @Test
    void testIsAllowedOnAgents() {
        assertFalse(configuration.isAllowedOnAgents());
    }

    @Test
    void testSetAllowedOnAgents() {
        configuration.setAllowedOnAgents(true);
        assertTrue(configuration.isAllowedOnAgents());
    }

    @Test
    void testSetAllowedOnAgentsFalse() {
        configuration.setAllowedOnAgents(false);
        assertFalse(configuration.isAllowedOnAgents());
    }

    @Test
    void testGetCategory() {
        assertThat(GitHooksConfiguration.get().getCategory(), is(configuration.getCategory()));
    }

    private void setCoreHooksPath(String hooksPath) throws Exception {
        /* Configure a core.hook with path `hooksPath` */
        client.withRepository((repo, channel) -> {
            final StoredConfig repoConfig = repo.getConfig();
            repoConfig.setString("core", null, "hooksPath", hooksPath);
            repoConfig.save();
            return null;
        });
    }

    private String getCoreHooksPath() throws Exception {
        String hooksPath = client.withRepository((repo, channel) -> {
            final StoredConfig repoConfig = repo.getConfig();
            return repoConfig.getString("core", null, "hooksPath");
        });
        return hooksPath;
    }

    @Test
    void testConfigure_GitClient() throws Exception {
        GitHooksConfiguration.configure(client);

        /* Check configured value from repository */
        String hooksPath = getCoreHooksPath();
        assertThat(hooksPath, is(NULL_HOOKS_PATH));
    }

    @Test
    void testConfigure_GitClient_boolean() throws Exception {
        boolean allowed = true;
        GitHooksConfiguration.configure(client, allowed);

        /* Check configured value from repository */
        String hooksPath = getCoreHooksPath();
        assertThat(hooksPath, is(nullValue()));
    }

    @Test
    void testConfigure_GitClient_booleanFalse() throws Exception {
        boolean allowed = false;
        GitHooksConfiguration.configure(client, allowed);

        /* Check configured value from repository */
        String hooksPath = getCoreHooksPath();
        assertThat(hooksPath, is(NULL_HOOKS_PATH));
    }

    private final String ALTERNATE_HOOKS_PATH = "not-a-valid-hooks-path";

    private void configure_3args(boolean allowedOnController) throws Exception {
        /* Change the hooksPath in repository */
        setCoreHooksPath(ALTERNATE_HOOKS_PATH);

        /* Confirm the hooksPath was changed in repository */
        String hooksPathBefore = getCoreHooksPath();
        assertThat(hooksPathBefore, is(ALTERNATE_HOOKS_PATH));

        /* Reconfigure repository.
         * Agent arg is ignored on controller, thus pass a random boolean
         */
        GitHooksConfiguration.configure(client, allowedOnController, random.nextBoolean());
    }

    @Test
    void testConfigure_3args() throws Exception {
        boolean allowedOnController = true;

        /* Change the hooksPath in repository */
        configure_3args(allowedOnController);

        /* Check configured value from repository */
        String hooksPath = getCoreHooksPath();
        assertThat(hooksPath, is(ALTERNATE_HOOKS_PATH));
    }

    @Test
    void testConfigure_3argsFalse() throws Exception {
        boolean allowedOnController = false;

        /* Change the hooksPath in repository */
        configure_3args(allowedOnController);

        /* Check configured value from repository */
        String hooksPath = getCoreHooksPath();
        assertThat(hooksPath, is(NULL_HOOKS_PATH));
    }

    private File createConfigLock() throws Exception {
        return client.withRepository((repo, channel) -> {
            File lock = new File(repo.getDirectory(), "config.lock");
            Files.deleteIfExists(lock.toPath());
            assertTrue(lock.createNewFile());
            return lock;
        });
    }

    // JENKINS-71349: re-disabling an already-disabled repo must not save(). A planted stale
    // config.lock would otherwise make save() throw LockFailedException.
    @Test
    void testConfigureDisabledIsNoOpWhenAlreadyDisabled() throws Exception {
        GitHooksConfiguration.configure(client, false);
        assertThat(getCoreHooksPath(), is(NULL_HOOKS_PATH));

        File configLock = createConfigLock();
        try {
            GitHooksConfiguration.configure(client, false);
            assertThat(getCoreHooksPath(), is(NULL_HOOKS_PATH));
            assertTrue(configLock.exists(), "DisableHooks must not touch an unrelated stale lock");
        } finally {
            Files.deleteIfExists(configLock.toPath());
        }
    }

    // The guard must skip only the disabled sentinel, never a different value (SECURITY-2754).
    @Test
    void testConfigureDisabledOverwritesNonDisabledValue() throws Exception {
        setCoreHooksPath(ALTERNATE_HOOKS_PATH);
        assertThat(getCoreHooksPath(), is(ALTERNATE_HOOKS_PATH));

        GitHooksConfiguration.configure(client, false);

        assertThat(getCoreHooksPath(), is(NULL_HOOKS_PATH));
    }

    @Test
    void testConfigureDisabledRepeatedlyIsNoOp() throws Exception {
        GitHooksConfiguration.configure(client, false);
        assertThat(getCoreHooksPath(), is(NULL_HOOKS_PATH));

        File configLock = createConfigLock();
        try {
            for (int i = 0; i < 5; i++) {
                GitHooksConfiguration.configure(client, false);
            }
            assertThat(getCoreHooksPath(), is(NULL_HOOKS_PATH));
            assertTrue(configLock.exists(), "repeated disable must not touch the stale lock");
        } finally {
            Files.deleteIfExists(configLock.toPath());
        }
    }

    // JENKINS-71349, allow-hooks path: UnsetHooks must not save() when hooksPath is already absent.
    @Test
    void testConfigureAllowedIsNoOpWhenHooksPathAbsent() throws Exception {
        GitHooksConfiguration.configure(client, true);
        assertThat(getCoreHooksPath(), is(nullValue()));

        File configLock = createConfigLock();
        try {
            GitHooksConfiguration.configure(client, true);
            assertThat(getCoreHooksPath(), is(nullValue()));
            assertTrue(configLock.exists(), "UnsetHooks must not touch the stale lock when hooksPath is absent");
        } finally {
            Files.deleteIfExists(configLock.toPath());
        }
    }
}
