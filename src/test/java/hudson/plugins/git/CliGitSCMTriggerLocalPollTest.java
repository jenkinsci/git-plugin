package hudson.plugins.git;

import hudson.plugins.git.extensions.GitClientType;
import hudson.plugins.git.extensions.impl.EnforceGitClient;

import static java.util.Arrays.asList;
import static java.util.concurrent.TimeUnit.SECONDS;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.plugins.git.extensions.impl.EnforceGitClient;
import hudson.scm.PollingResult;
import hudson.triggers.SCMTrigger;
import hudson.util.RunList;
import hudson.model.TaskListener;
import hudson.util.StreamTaskListener;

import org.jenkinsci.plugins.gitclient.GitClient;

import org.eclipse.jgit.lib.ObjectId;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.Enumeration;
import java.util.Properties;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.apache.commons.io.FileUtils;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.Issue;

public class CliGitSCMTriggerLocalPollTest extends SCMTriggerTest
{

    @Override
    protected EnforceGitClient getGitClient()
    {
        return new EnforceGitClient().set(GitClientType.GITCLI);
    }

    @Override
    protected boolean isDisableRemotePoll()
    {
        return true;
    }

    @Test
    public void testNamespaces_with_refsHeadsMaster() throws Exception {
        check(namespaceRepoZip, namespaceRepoCommits,
            "refs/heads/master",
            namespaceRepoCommits.getProperty("refs/heads/master"),
            "origin/master");
    }

    /*
     * local workspace polling should avoid using out-of-date local branches
     * when reporting new candidates to build. This does not affect remote
     * polling as it does not use a workspace.
     */
    @Test
    public void testLocalBranchesDontImpactPolling() throws Exception {
        TaskListener listener = StreamTaskListener.fromStderr();

        String remote = prepareRepo(namespaceRepoZip);

        FreeStyleProject project = setupProject(asList(new UserRemoteConfig(remote, null, null, null)),
                    asList(new BranchSpec("master")),
                    //empty scmTriggerSpec, SCMTrigger triggered manually
                    "", isDisableRemotePoll(), getGitClient()); 

        //Speedup test - avoid waiting 1 minute
        triggerSCMTrigger(project.getTrigger(SCMTrigger.class));

        FreeStyleBuild build1 = waitForBuildFinished(project, 1, 60000);
        assertNotNull("Job has not been triggered", build1);

        assertEquals("Unexpected GIT_COMMIT", 
                    namespaceRepoCommits.getProperty("refs/heads/master"),
                    build1.getEnvironment(null).get("GIT_COMMIT"));
        assertEquals("Unexpected GIT_BRANCH", 
                    "origin/master", build1.getEnvironment(null).get("GIT_BRANCH"));

        GitSCM scm = (GitSCM)project.getScm();
        GitClient client = scm.createClient(listener, null, build1, build1.getWorkspace());

        // Set master to a commit which isn't in the remote
        build1.getWorkspace().child("aNewFile").touch(0);
        client.add("aNewFile");
        client.commit("add a new file");
        client.checkout("HEAD", "master");

        // Verify that polling doesn't think the local master branch is a new commit to build
        PollingResult poll = project.poll(listener);
        assertEquals("Expected and actual polling results disagree", false, poll.hasChanges());

        //Speedup test - avoid waiting 1 minute
        triggerSCMTrigger(project.getTrigger(SCMTrigger.class)).get(20, SECONDS);

        FreeStyleBuild build2 = waitForBuildFinished(project, 2, 2000);
        assertNull("Found build 2 although no new changes and no multi candidate build", build2);

    }


}
