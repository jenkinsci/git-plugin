package jenkins.plugins.git;

import hudson.FilePath;
import hudson.model.Queue;
import org.apache.commons.io.FileUtils;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.jvnet.hudson.test.recipes.LocalData;

import java.io.File;
import java.io.IOException;
import java.net.URL;

import static hudson.Functions.isWindows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@WithJenkins
class GitBranchSCMHeadTest {

    private JenkinsRule r;

    @BeforeEach
    void beforeEach(JenkinsRule rule) {
        r = rule;
    }

    @AfterEach
    void afterEach() throws IOException {
        final File path = new File("/tmp/JENKINS-48061");
        if (path.exists() && path.isDirectory()) {
            FileUtils.deleteDirectory(path);
        }
    }

    // getBuilds.size()
    @Issue("JENKINS-48061")
    @Test
    @LocalData
    @Deprecated
    void testMigrationNoBuildStorm() throws Exception {
        if (isWindows()) { // Test is unreliable on Windows, too low value to investigate further
            /* Do not distract warnings system by using assumeThat to skip tests */
            return;
        }

        URL res = getClass().getResource("/jenkins/plugins/git/GitBranchSCMHeadTest/testMigrationNoBuildStorm_repositories.zip");
        final File path = new File("/tmp/JENKINS-48061");
        if (path.exists()) {
            if (path.isDirectory()) {
                FileUtils.deleteDirectory(path);
            } else {
                path.delete();
            }
        }

        new FilePath(new File(res.toURI())).unzip(new FilePath(path.getParentFile()));

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
}
