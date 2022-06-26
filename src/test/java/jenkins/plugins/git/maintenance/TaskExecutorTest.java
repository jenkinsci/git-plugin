package jenkins.plugins.git.maintenance;

import antlr.ANTLRException;
import hudson.plugins.git.BranchSpec;
import hudson.plugins.git.GitSCM;
import jenkins.plugins.git.GitSampleRepoRule;
import jenkins.scm.api.SCMFileSystem;
import org.jenkinsci.plugins.gitclient.GitClient;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertNotNull;

public class TaskExecutorTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Rule
    public GitSampleRepoRule sampleRepo1 = new GitSampleRepoRule();

    TaskExecutor taskExecutor;

    @Before
    public void setUp() throws Exception{
        // Tested for a single maintenance tasks. Do I have to test for all maintenance tasks?
        MaintenanceTaskConfiguration config = new MaintenanceTaskConfiguration();
        config.setIsTaskConfigured(TaskType.COMMIT_GRAPH,true);
        config.setCronSyntax(TaskType.COMMIT_GRAPH,"* * * * *");
        List<Task> tasks = config.getMaintenanceTasks().stream().filter(Task::getIsTaskConfigured).collect(Collectors.toList());
        Task configuredTask = tasks.get(0);

        taskExecutor = new TaskExecutor(configuredTask);

        sampleRepo1.init();
        sampleRepo1.git("checkout", "-b", "bug/JENKINS-42817");
        sampleRepo1.write("file", "modified");
        sampleRepo1.git("commit", "--all", "--message=dev");
        SCMFileSystem.of(j.createFreeStyleProject(), new GitSCM(GitSCM.createRepoList(sampleRepo1.toString(), null), Collections.singletonList(new BranchSpec("*/bug/JENKINS-42817")), null, null, Collections.emptyList()));
    }

    @Test
    public void testGitClient() throws IOException, InterruptedException {
       // Get directory of a single cache.
        GitMaintenanceSCM.Cache cache = taskExecutor.getCaches().get(0);
        File cacheFile = cache.getCacheFile();
        assertNotNull(taskExecutor.getGitClient(cacheFile));
        assertThat(taskExecutor.getGitClient(cacheFile),instanceOf(GitClient.class));
    }

    @Test
    public void testExecuteMaintenanceTask(){
        return;
    }

    @Test
    public void testRun(){
        return;
    }

}
