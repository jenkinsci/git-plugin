package jenkins.plugins.git.maintenance;

import hudson.plugins.git.BranchSpec;
import hudson.plugins.git.GitSCM;
import jenkins.plugins.git.GitSampleRepoRule;
import jenkins.scm.api.SCMFileSystem;
import org.jenkinsci.plugins.gitclient.GitClient;
import org.junit.BeforeClass;
import org.junit.ClassRule;
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

    @ClassRule
    public static JenkinsRule j = new JenkinsRule();

    @ClassRule
    public static GitSampleRepoRule sampleRepo1 = new GitSampleRepoRule();

    private static TaskExecutor taskExecutor;

    @BeforeClass
    public static void setUp() throws Exception{
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
    public void testGitVersionAtLeast(){
        // This test is dependent on users machine.
        taskExecutor.gitVersionAtLeast(2,1,1);
    }

    @Test
    public void testGetCaches(){
        assertNotNull(taskExecutor.getCaches());
    }

    @Test
    public void testExecuteMaintenanceTask() throws InterruptedException {
        GitMaintenanceSCM.Cache cache = taskExecutor.getCaches().get(0);
        File cacheFile = cache.getCacheFile();
        GitClient gitClient = taskExecutor.getGitClient(cacheFile);
        TaskType taskType = TaskType.COMMIT_GRAPH;
        taskExecutor.executeMaintenanceTask(gitClient,taskType);
    }
}
