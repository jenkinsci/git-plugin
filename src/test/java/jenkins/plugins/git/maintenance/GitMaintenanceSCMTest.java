package jenkins.plugins.git.maintenance;

import jenkins.plugins.git.GitSampleRepoRule;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.util.List;

public class GitMaintenanceSCMTest {

    @Rule
    public GitSampleRepoRule sampleRepo = new GitSampleRepoRule();

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Before
    public void setUp() throws Exception {
        // Need to create git caches on Jenkins controller.

    }

    @Test
    public void testGetCaches(){
        List<GitMaintenanceSCM.Cache> caches = GitMaintenanceSCM.getCaches();
        // check the given caches is same as the caches present on jenkins controller.

    }
}
