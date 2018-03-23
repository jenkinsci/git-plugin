package jenkins.plugins.git;

import hudson.FilePath;
import hudson.Functions;
import hudson.model.Queue;
import org.apache.commons.io.FileUtils;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProject;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.recipes.LocalData;

import java.io.File;
import java.net.URL;

import static org.junit.Assert.*;
import static org.junit.Assume.assumeFalse;

public class GitBranchSCMHeadTest {

    @Rule
    public JenkinsRule j = new JenkinsRule() {
        @Override
        public void before() throws Throwable {
            if (!Functions.isWindows() && "testMigrationNoBuildStorm".equals(this.getTestDescription().getMethodName())) {
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
            }
            super.before();
        }
    };


    @Issue("JENKINS-48061")
    @Test
    @LocalData
    public void testMigrationNoBuildStorm() throws Exception {
        assumeFalse(Functions.isWindows());
        final WorkflowMultiBranchProject job = j.jenkins.getItemByFullName("job", WorkflowMultiBranchProject.class);
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
        j.waitUntilNoActivity();

        assertEquals(4, job.getItems().size());
        master = job.getItem("master");
        assertEquals(1, master.getBuilds().size());
        dev = job.getItem("dev");
        assertEquals(1, dev.getBuilds().size());
        v4 = job.getItem("v4");
        assertEquals(0, v4.getBuilds().size());
    }

}