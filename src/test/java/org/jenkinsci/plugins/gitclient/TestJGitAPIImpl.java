package org.jenkinsci.plugins.gitclient;

import hudson.model.TaskListener;
import org.jenkinsci.plugins.gitclient.jgit.PreemptiveAuthHttpClientConnectionFactory;

import java.io.File;

/**
 * This is just here to make the constructors public
 * AbstractGitSCMSourceTest#when_commits_added_during_discovery_we_do_not_crash()
 */
public class TestJGitAPIImpl extends JGitAPIImpl {
    public TestJGitAPIImpl(File workspace, TaskListener listener) {
        super(workspace, listener);
    }

    public TestJGitAPIImpl(File workspace, TaskListener listener, PreemptiveAuthHttpClientConnectionFactory httpConnectionFactory) {
        super(workspace, listener, httpConnectionFactory);
    }
}
