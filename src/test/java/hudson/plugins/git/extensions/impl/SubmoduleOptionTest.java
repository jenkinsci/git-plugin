package hudson.plugins.git.extensions.impl;

import hudson.plugins.git.extensions.GitSCMExtension;
import hudson.plugins.git.extensions.impl.*;

import hudson.plugins.git.GitSCM;
import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;
import org.jenkinsci.plugins.gitclient.*;

import jenkins.security.MasterToSlaveCallable;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.jenkinsci.plugins.gitclient.*;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.TestExtension;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectStreamException;
import java.io.Serializable;
import java.net.URL;
import java.text.MessageFormat;
import java.util.*;
import org.eclipse.jgit.transport.RemoteConfig;
import static org.hamcrest.Matchers.*;
import static org.hamcrest.CoreMatchers.instanceOf;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;

import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.BeforeClass;

import hudson.model.Run;
import hudson.plugins.git.GitException;
import hudson.model.TaskListener;
import hudson.plugins.git.util.BuildData;
import hudson.plugins.git.util.Build;

import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;


public class SubmoduleOptionTest {

    @Issue("JENKINS-31934")
    @Test
    public void testSubmoduleUpdateThrowsIOException() throws Exception {
        SubmoduleOption submoduleOption = new SubmoduleOption(false, false, false, null, null, false);

        // In order to verify that the submodule option correctly converts
        // GitExceptions into IOExceptions, setup a SubmoduleOption, and run
        // it's onCheckoutCOmpleted extension point with a mocked git client
        // that always throws an exception.
        BuildData buildData = Mockito.mock(BuildData.class);
        Build lastBuild = Mockito.mock(Build.class);
        GitSCM scm = Mockito.mock(GitSCM.class);
        Run<?, ?> build = Mockito.mock(Run.class);
        GitClient client = Mockito.mock(GitClient.class);
        TaskListener listener = Mockito.mock(TaskListener.class);
        buildData.lastBuild = lastBuild;
        Mockito.when(scm.getBuildData(build)).thenReturn(buildData);
        Mockito.when(client.hasGitModules()).thenReturn(true);
        Mockito.when(client.submoduleUpdate()).thenThrow(new GitException("a git exception"));

        try {
            submoduleOption.onCheckoutCompleted(scm, build, client, listener);
            fail("Expected IOException to be thrown");
        } catch (IOException e) {
            assertThat(e.getMessage(), is("Could not perform submodule update"));
        }
    }

    @Test
    public void equalsContract() {
        EqualsVerifier.forClass(SubmoduleOption.class)
                .usingGetClass()
                .suppress(Warning.NONFINAL_FIELDS)
                .verify();
    }
}
