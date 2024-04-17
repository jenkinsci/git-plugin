package jenkins.plugins.git;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import hudson.FilePath;
import hudson.model.Queue;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import org.apache.commons.io.FileUtils;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProject;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.recipes.LocalData;

public class GitBranchSCMHeadTest {

    @Rule
    public JenkinsRule r = new JenkinsRule() {
        @Override
        public void before() throws Throwable {
            if (!isWindows()
                    && "testMigrationNoBuildStorm"
                            .equals(this.getTestDescription().getMethodName())) {
                URL res = getClass()
                        .getResource(
                                "/jenkins/plugins/git/GitBranchSCMHeadTest/testMigrationNoBuildStorm_repositories.zip");
                final File path = new File("/tmp/JENKINS-48061");
                if (path.exists()) {
                    if (path.isDirectory()) {
                        FileUtils.deleteDirectory(path);
                    } else {
                        path.delete();
                    }
                }

                new FilePath(new File(res.toURI())).unzip(new FilePath(path.getParentFile()));
            }
            super.before();
        }
    };

    @After
    public void removeRepos() throws IOException {
        final File path = new File("/tmp/JENKINS-48061");
        if (path.exists() && path.isDirectory()) {
            FileUtils.deleteDirectory(path);
        }
    }

    @Issue("JENKINS-48061")
    @Test
    @LocalData
    @Deprecated // getBuilds.size()
    public void testMigrationNoBuildStorm() throws Exception {
        if (isWindows()) { // Test is unreliable on Windows, too low value to investigate further
            /* Do not distract warnings system by using assumeThat to skip tests */
            return;
        }
        final WorkflowMultiBranchProject job = r.jenkins.getItemByFullName("job", WorkflowMultiBranchProject.class);
        assertEquals(4, job.getItems().size());
        WorkflowJob master = job.getItem("master");
        assertEquals(1, master.getBuilds().size());
        WorkflowJob dev = job.getItem("dev");
        assertEquals(1, dev.getBuilds().size());
        WorkflowJob v4 = job.getItem("v4");
        assertEquals(0, v4.getBuilds().size());

        final Queue.Item item = job.scheduleBuild2(0);
        assertNotNull(item);
        item.getFuture().waitForStart();
        r.waitUntilNoActivity();

        assertEquals(4, job.getItems().size());
        master = job.getItem("master");
        assertEquals(1, master.getBuilds().size());
        dev = job.getItem("dev");
        assertEquals(1, dev.getBuilds().size());
        v4 = job.getItem("v4");
        assertEquals(0, v4.getBuilds().size());
    }

    /** inline ${@link hudson.Functions#isWindows()} to prevent a transient remote classloader issue */
    private boolean isWindows() {
        return File.pathSeparatorChar == ';';
    }
}
