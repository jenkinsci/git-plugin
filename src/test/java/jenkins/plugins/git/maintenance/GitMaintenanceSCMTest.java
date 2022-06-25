package jenkins.plugins.git.maintenance;

import hudson.plugins.git.BranchSpec;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.extensions.GitSCMExtension;
import jenkins.plugins.git.GitSampleRepoRule;
import jenkins.scm.api.SCMFileSystem;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.locks.Lock;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class GitMaintenanceSCMTest {

    @Rule
    public GitSampleRepoRule sampleRepo1 = new GitSampleRepoRule();

    @Rule
    public GitSampleRepoRule sampleRepo2 = new GitSampleRepoRule();

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    public void testGetCaches() throws Exception{
        sampleRepo1.init();
        sampleRepo1.git("checkout", "-b", "bug/JENKINS-42817");
        sampleRepo1.write("file", "modified");
        sampleRepo1.git("commit", "--all", "--message=dev");
        SCMFileSystem.of(j.createFreeStyleProject(), new GitSCM(GitSCM.createRepoList(sampleRepo1.toString(), null), Collections.singletonList(new BranchSpec("*/bug/JENKINS-42817")), null, null, Collections.emptyList()));

        sampleRepo2.init();
        sampleRepo2.git("checkout", "-b", "bug/JENKINS-42817");
        sampleRepo2.write("file", "modified");
        sampleRepo2.git("commit", "--all", "--message=dev");
        SCMFileSystem.of(j.createFreeStyleProject(), new GitSCM(GitSCM.createRepoList(sampleRepo2.toString(), null), Collections.singletonList(new BranchSpec("*/bug/JENKINS-42817")), null, null, Collections.<GitSCMExtension>emptyList()));

        GitMaintenanceSCM[] gitMaintenanceSCMS = new GitMaintenanceSCM[2];
        gitMaintenanceSCMS[0] = new GitMaintenanceSCM(sampleRepo1.toString());
        gitMaintenanceSCMS[1] = new GitMaintenanceSCM(sampleRepo2.toString());

        List<GitMaintenanceSCM.Cache> caches = GitMaintenanceSCM.getCaches();

        for(GitMaintenanceSCM.Cache cache : caches){
            String cacheDir = cache.getCacheFile().getName();
            boolean cachesExists = false;
            for(GitMaintenanceSCM gitMaintenanceSCM : gitMaintenanceSCMS){
                cachesExists = gitMaintenanceSCM.getCacheEntryForTest().equals(cacheDir);
                if(cachesExists)
                    break;
            }

            assertTrue(cachesExists);
        }
    }
//
//    @Test
//    public void testNoCachesExistsOnJenkinsController(){
//        List<GitMaintenanceSCM.Cache> caches = GitMaintenanceSCM.getCaches();
//        assertEquals(0,caches.size());
//    }
//
//    @Test
//    public void testGetLockAndCacheFile() throws Exception{
//        sampleRepo1.init();
//        sampleRepo1.git("checkout", "-b", "bug/JENKINS-4283423");
//        sampleRepo1.write("file", "modified");
//        sampleRepo1.git("commit", "--all", "--message=dev");
//        SCMFileSystem.of(j.createFreeStyleProject(), new GitSCM(GitSCM.createRepoList(sampleRepo1.toString(), null), Collections.singletonList(new BranchSpec("*/bug/JENKINS-4283423")), null, null, Collections.emptyList()));
//
//        List<GitMaintenanceSCM.Cache> caches = GitMaintenanceSCM.getCaches();
//        caches.stream().forEach(cache -> {
//            assertNotNull(cache.getLock());
//            assertThat(cache.getLock(),instanceOf(Lock.class));
//            assertNotNull(cache.getCacheFile());
//            assertThat(cache.getCacheFile(),instanceOf(File.class));
//        });
//    }
}
