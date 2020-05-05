package jenkins.plugins.git.traits;

import jenkins.plugins.git.GitSampleRepoRule;
import jenkins.plugins.git.GitSCMSource;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.libs.GlobalLibraries;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.libs.LibraryConfiguration;
import org.jenkinsci.plugins.workflow.libs.SCMSourceRetriever;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.RestartableJenkinsRule;

import java.util.Collections;
import java.util.Random;

public class MultibranchProjectTraitsTest {
    @Rule
    public RestartableJenkinsRule story = new RestartableJenkinsRule();
    @Rule
    public GitSampleRepoRule sharedLibrarySampleRepo = new GitSampleRepoRule();
    @Rule
    public GitSampleRepoRule multibranchProjectSampleRepo = new GitSampleRepoRule();

    private Random random = new Random();

    @Before
    public void setUpTestRepositories() throws Exception {
        sharedLibrarySampleRepo.init();
        sharedLibrarySampleRepo.write("vars/book.groovy", "def call() {echo 'Greetings from the library'}");
        sharedLibrarySampleRepo.git("checkout", "-b", "libraryBranch");
        sharedLibrarySampleRepo.git("add", "vars");
        sharedLibrarySampleRepo.git("commit", "--message=Shared library created in libraryBranch");
    }

    /*
     Tests a checkout step in a pipeline using Symbol names instead of $class
     */
    @Test
    public void basicSharedLibrarySymbolsTest() throws Exception {
        story.then( r -> {
            GlobalLibraries.get().setLibraries(Collections.singletonList(new LibraryConfiguration("thelibrary", new SCMSourceRetriever(new GitSCMSource(sharedLibrarySampleRepo.toString())))));
            WorkflowJob job = story.j.jenkins.createProject(WorkflowJob.class, "test-pipeline-job");
            job.setDefinition(new CpsFlowDefinition(
                            "library 'thelibrary@libraryBranch'\n"
                            + "node() {\n"
                            + "  book()\n"
                            + "  checkout gitSCM(\n"
                            // Put in some randomized thing here
                            // + "    browser: gitLabBrowser(repoUrl: 'https://prettymuchanythinghere.io/a/b', version: '9.0'),\n"
                            + randomBrowserSymbolName()
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

    /*
     Tests a checkout step in a pipeline using $class for backward compatibility
    */
    @Test
    public void basicSharedLibraryClassTest() throws Exception {
        story.then( r -> {
            GlobalLibraries.get().setLibraries(Collections.singletonList(new LibraryConfiguration("thelibrary", new SCMSourceRetriever(new GitSCMSource(sharedLibrarySampleRepo.toString())))));
            WorkflowJob job = story.j.jenkins.createProject(WorkflowJob.class, "test-pipeline-job");
            job.setDefinition(new CpsFlowDefinition(
                    "library 'thelibrary@libraryBranch'\n"
                            + "node() {\n"
                            + "  book()\n"
                            + "  checkout gitSCM(\n"
                            + "    browser: [$class: 'CGit', repoUrl: 'https://prettymuchanythinghere.io/a/b'],\n"
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

    private String randomBrowserSymbolName() {
        String[] browsers = {
                "    browser: assemblaWeb('https://app.assembla.com/spaces/git-plugin/git/source'),\n",
                "    browser: bitbucketWeb('https://markewaite@bitbucket.org/markewaite/git-plugin'),\n",
                "    browser: cgit('https://git.zx2c4.com/cgit'),\n",
                "    browser: fisheye('https://fisheye.apache.org/browse/ant-git'),\n",
                "    browser: gitBlitRepositoryBrowser(repoUrl: 'https://github.com/MarkEWaite/git-client-plugin', projectName: 'git-plugin-project-name-value'),\n",
                "    browser: gitLabBrowser(repoUrl: 'https://gitlab.com/MarkEWaite/git-client-plugin', version: '12.10.1'),\n",
                "    browser: gitList('http://gitlist.org/'),\n", // Not a real gitlist site, just the org home page
                "    browser: gitWeb('https://git.ti.com/gitweb'),\n",
                "    browser: githubWeb('https://github.com/jenkinsci/git-plugin'),\n",
                "    browser: gitiles('https://gerrit.googlesource.com/gitiles/'),\n",
                "    browser: gitoriousWeb('https://gerrit.googlesource.com/gitiles/'),\n",
                "    browser: gogs('https://try.gogs.io/MarkEWaite/git-plugin'),\n", // Should this be gogsGit?
                "    browser: kiln('https://kiln.example.com/MarkEWaite/git-plugin'),\n",
                "    browser: microsoftTFS('https://markwaite.visualstudio.com/DefaultCollection/git-plugin/_git/git-plugin'),\n",
                "    browser: phabricator(repo: 'source/tool-spacemedia', repoUrl: 'https://phabricator.wikimedia.org/source/tool-spacemedia/'),\n",
                "    browser: redmineWeb('https://www.redmine.org/projects/redmine/repository'),\n",
                "    browser: rhodeCode('https://code.rhodecode.com/rhodecode-enterprise-ce'),\n",
                "    browser: viewGit(repoUrl: 'https://repo.or.cz/viewgit.git', projectName: 'viewgit-project-name-value'),\n", // Not likely a viewGit site, but reasonable approximation
        };
        String browser = browsers[random.nextInt(browsers.length)];
        return browser;
    }


}
