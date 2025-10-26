package jenkins.plugins.git.maintenance;

import hudson.plugins.git.AbstractGitRepository;
import hudson.plugins.git.AbstractGitTestCase;
import hudson.plugins.git.BranchSpec;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.extensions.GitSCMExtension;
import jenkins.model.Jenkins;
import jenkins.plugins.git.AbstractGitSCMSource;
import jenkins.plugins.git.AbstractGitSCMSourceTest;
import jenkins.plugins.git.GitSampleRepoRule;
import jenkins.scm.api.SCMFileSystem;
import org.apache.commons.io.FileUtils;
import org.junit.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.JenkinsRule;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.configuration.IMockitoConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.*;

public class GitMaintenanceSCMTest {

    @Rule
    public JenkinsRule rule = new JenkinsRule();


    @Test
    public void testGetCaches() throws Exception{

        // Cannot test this due to static methods not public,
        // AbstractGitSCmSource is in different package
        List<GitMaintenanceSCM.Cache>  caches = new ArrayList<>();
        caches.add(new GitMaintenanceSCM.Cache(new File("test.txt"),new ReentrantLock()));


        mockStatic(GitMaintenanceSCM.class)
                .when(()->GitMaintenanceSCM.getCaches())
                .thenReturn(caches);

        assertEquals(caches.size(),GitMaintenanceSCM.getCaches().size());

    }

    @Test
    public void testGetCacheFile(){
        File file = Jenkins.getInstanceOrNull().getRootDir();
        Lock lock = new ReentrantLock();
        GitMaintenanceSCM.Cache cache = new GitMaintenanceSCM.Cache(file,lock);
        assertEquals(file.getAbsolutePath(),cache.getCacheFile().getAbsolutePath());
    }

    @Test
    public void testGetLock(){
        File file = Jenkins.getInstanceOrNull().getRootDir();
        Lock lock = new ReentrantLock();
        GitMaintenanceSCM.Cache cache = new GitMaintenanceSCM.Cache(file,lock);
        assertNotNull(cache.getLock());
    }
}
