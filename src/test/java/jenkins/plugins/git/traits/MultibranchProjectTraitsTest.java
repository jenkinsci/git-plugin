package jenkins.plugins.git.traits;

import jenkins.plugins.git.GitSampleRepoRule;
import jenkins.plugins.git.GitSCMSource;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.libs.GlobalLibraries;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.libs.LibraryConfiguration;
import org.jenkinsci.plugins.workflow.libs.SCMSourceRetriever;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.RestartableJenkinsRule;

import java.util.Collections;

public class MultibranchProjectTraitsTest {
    @Rule
    public RestartableJenkinsRule story = new RestartableJenkinsRule();
    @Rule
    public GitSampleRepoRule sharedLibrarySampleRepo = new GitSampleRepoRule();
    @Rule
    public GitSampleRepoRule multibranchProjectSampleRepo = new GitSampleRepoRule();

    @Test
    public void basicSharedLibraryTest() throws Exception {
        story.then( r -> {
            sharedLibrarySampleRepo.init();
            sharedLibrarySampleRepo.write("vars/book.groovy", "def call() {echo 'Greetings from the library'}");
            sharedLibrarySampleRepo.git("checkout", "-b", "libraryBranch");
            sharedLibrarySampleRepo.git("add", "vars");
            sharedLibrarySampleRepo.git("commit", "--message=init");
            GlobalLibraries.get().setLibraries(Collections.singletonList(new LibraryConfiguration("thelibrary", new SCMSourceRetriever(new GitSCMSource(null, sharedLibrarySampleRepo.toString(), "", "*", "", true)))));
            WorkflowJob job = story.j.jenkins.createProject(WorkflowJob.class, "test-pipeline-job");
            job.setDefinition(new CpsFlowDefinition(
                            "library 'thelibrary@libraryBranch'\n"
                            + "node() {\n"
                            + "  book()\n"
                            + "  checkout gitSCM(\n"
                            // Put in some randomized thing here
                            + "    browser: gitLabBrowser(repoUrl: 'https://prettymuchanythinghere.io/a/b', version: '9.0'),\n"
                            + "    userRemoteConfigs: [[url: $/" + sharedLibrarySampleRepo + "/$]]\n"
                            + "  )"
                            + "}"
                    ,
                    true));

            WorkflowRun run = story.j.waitForCompletion(job.scheduleBuild2(0).waitForStart());
            story.j.waitForCompletion(run);
            story.j.waitForMessage("Finished: SUCCESS", run);
            story.j.assertLogContains("Greetings from the library", run);
        });
    }

//    @Test
//    public void multibranchSharedLibraryTest() throws Exception {
//        // story.then something
//    }

}
