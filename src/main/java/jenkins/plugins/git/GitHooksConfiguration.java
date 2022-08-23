/*
 * The MIT License
 *
 * Copyright (c) 2021 CloudBees, Inc.
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
 *
 */
package jenkins.plugins.git;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.Functions;
import hudson.model.PersistentDescriptor;
import hudson.remoting.Channel;
import jenkins.model.GlobalConfiguration;
import jenkins.model.GlobalConfigurationCategory;
import org.apache.commons.lang.StringUtils;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.gitclient.GitClient;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

import java.io.IOException;
import java.util.logging.Logger;



@Extension @Symbol("gitHooks") @Restricted(NoExternalUse.class)
public class GitHooksConfiguration extends GlobalConfiguration implements PersistentDescriptor {

    public static final String DISABLED_WIN = "NUL:";
    public static final String DISABLED_NIX = "/dev/null";
    static final Logger LOGGER = Logger.getLogger(GitHooksConfiguration.class.getName());

    private boolean allowedOnController = false;
    private boolean allowedOnAgents = false;

    @NonNull
    public static GitHooksConfiguration get() {
        final GitHooksConfiguration configuration = GlobalConfiguration.all().get(GitHooksConfiguration.class);
        if (configuration == null) {
            throw new IllegalStateException("[BUG] No configuration registered, make sure not running on an agent or that Jenkins has started properly.");
        }
        return configuration;
    }

    public boolean isAllowedOnController() {
        return allowedOnController;
    }

    public void setAllowedOnController(final boolean allowedOnController) {
        this.allowedOnController = allowedOnController;
        save();
    }

    public boolean isAllowedOnAgents() {
        return allowedOnAgents;
    }

    public void setAllowedOnAgents(final boolean allowedOnAgents) {
        this.allowedOnAgents = allowedOnAgents;
        save();
    }

    @Override @NonNull
    public GlobalConfigurationCategory getCategory() {
        return GlobalConfigurationCategory.get(GlobalConfigurationCategory.Security.class);
    }

    public static void configure(GitClient client) throws IOException, InterruptedException {
        final GitHooksConfiguration configuration = GitHooksConfiguration.get();
        configure(client, configuration.isAllowedOnController(), configuration.isAllowedOnAgents());
    }

    public static void configure(GitClient client, final boolean allowedOnController, final boolean allowedOnAgents) throws IOException, InterruptedException {
        if (Channel.current() == null) {
            //Running on controller
            try (Repository ignored = client.getRepository()){
                //That went well, so the code runs on the controller and the repo is local
                configure(client, allowedOnController);
            } catch (UnsupportedOperationException e) {
                // Client represents a remote repository, so this code runs on the controller but the repo is on an agent
                configure(client, allowedOnAgents);
            }
        } else {
            //Running on agent
            configure(client, allowedOnAgents);
        }
    }

    public static void configure(GitClient client, final boolean allowed) throws IOException, InterruptedException {
        if (!allowed) {
            client.withRepository((repo, channel) -> {
                disable(repo);
                return null;
            });
        } else {
            client.withRepository((repo, channel) -> {
                unset(repo);
                return null;
            });
        }
    }

    private static void unset(final Repository repo) throws IOException {
        final StoredConfig repoConfig = repo.getConfig();
        final String val = repoConfig.getString("core", null, "hooksPath");
        if (!StringUtils.isEmpty(val) && !(DISABLED_NIX.equals(val) || DISABLED_WIN.equals(val))) {
            LOGGER.warning(() -> String.format("core.hooksPath explicitly set to %s and will be left intact on %s.", val, repo.getDirectory()));
        } else {
            repoConfig.unset("core", null, "hooksPath");
            repoConfig.save();
        }
    }

    private static void disable(final Repository repo) throws IOException {
        final String VAL = Functions.isWindows() ? DISABLED_WIN : DISABLED_NIX;
        final StoredConfig repoConfig = repo.getConfig();
        repoConfig.setString("core", null, "hooksPath", VAL);
        repoConfig.save();
    }
}
