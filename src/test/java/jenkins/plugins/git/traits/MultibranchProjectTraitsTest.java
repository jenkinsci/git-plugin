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

    private Random random = new Random();

    @Before
    public void setUpTestRepositories() throws Exception {
        // Initialize our repository and put shared library code in the branch `libraryBranch`
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
            GlobalLibraries.get().setLibraries(Collections.singletonList( // TODO: Something more interesting with traits, maybe gitBranchDiscovery
                    new LibraryConfiguration("thelibrary", new SCMSourceRetriever(new GitSCMSource(sharedLibrarySampleRepo.toString())))));;
            WorkflowJob job = story.j.jenkins.createProject(WorkflowJob.class, "test-pipeline-job");
            job.setDefinition(new CpsFlowDefinition(
                            "library 'thelibrary@libraryBranch'\n"
                            + "node() {\n"
                            + "  book()\n"
                            + "  checkout scmGit(\n"
                            + randomBrowserSymbolName()
                            + "    userRemoteConfigs: [[url: $/" + sharedLibrarySampleRepo + "/$]]\n"
                            + "  )"
                            + "}"
                    , true));
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
                            + "node {\n"
                            + "  book()\n"
                            + "  checkout(\n"
                            + "    [$class: 'GitSCM', \n"
                            + randomBrowserClass()
                            + "    userRemoteConfigs: [[url: $/" + sharedLibrarySampleRepo + "/$]]]\n"
                            + "  )"
                            + "}", true));
            WorkflowRun run = story.j.waitForCompletion(job.scheduleBuild2(0).waitForStart());
            story.j.waitForCompletion(run);
            story.j.waitForMessage("Finished: SUCCESS", run);
            story.j.assertLogContains("Greetings from the library", run);
        });
    }

    /*
     Returns a randomly selected browser for use in a pipeline checkout, using Symbol names
     */
    private String randomBrowserSymbolName() {
        String[] browsersBySymbolName = {
                "    browser: assembla('https://app.assembla.com/spaces/git-plugin/git/source'),\n",
                "    browser: bitbucket('https://markewaite@bitbucket.org/markewaite/git-plugin'),\n",
                "    browser: cgit('https://git.zx2c4.com/cgit'),\n",
                "    browser: fisheye('https://fisheye.apache.org/browse/ant-git'),\n",
                "    browser: gitblit(repoUrl: 'https://github.com/MarkEWaite/git-client-plugin', projectName: 'git-plugin-project-name-value'),\n",
                "    browser: gitLab(repoUrl: 'https://gitlab.com/MarkEWaite/git-client-plugin', version: '12.10.1'),\n",
                "    browser: gitList('http://gitlist.org/'),\n", // Not a real gitlist site, just the org home page
                "    browser: gitWeb('https://git.ti.com/gitweb'),\n",
                "    browser: github('https://github.com/jenkinsci/git-plugin'),\n",
                "    browser: gitiles('https://gerrit.googlesource.com/gitiles/'),\n",
                // No symbol for gitorious - dead site
                // "    browser: gitoriousWeb('https://gerrit.googlesource.com/gitiles/'),\n",
                "    browser: gogs('https://try.gogs.io/MarkEWaite/git-plugin'),\n", // Should this be gogsGit?
                "    browser: kiln('https://kiln.example.com/MarkEWaite/git-plugin'),\n",
                "    browser: microsoftTFS('https://markwaite.visualstudio.com/DefaultCollection/git-plugin/_git/git-plugin'),\n",
                "    browser: phabricator(repo: 'source/tool-spacemedia', repoUrl: 'https://phabricator.wikimedia.org/source/tool-spacemedia/'),\n",
                "    browser: redmine('https://www.redmine.org/projects/redmine/repository'),\n",
                "    browser: rhodeCode('https://code.rhodecode.com/rhodecode-enterprise-ce'),\n",
                "    browser: viewgit(repoUrl: 'https://repo.or.cz/viewgit.git', projectName: 'viewgit-project-name-value'),\n", // Not likely a viewgit site, but reasonable approximation
        };
        String browser = browsersBySymbolName[random.nextInt(browsersBySymbolName.length)];
        return browser;
    }

    /*
     Returns a randomly selected browser for use in a pipeline checkout, using the $class syntax
     */
    private String randomBrowserClass() {
        String[] browsersByClass = {
                "    browser: [$class: 'AssemblaWeb', repoUrl: 'https://app.assembla.com/spaces/git-plugin/git/source'],\n",
                "    browser: [$class: 'BitbucketWeb', repoUrl: 'https://markewaite@bitbucket.org/markewaite/git-plugin'],\n",
                "    browser: [$class: 'CGit', repoUrl: 'https://git.zx2c4.com/cgit'],\n",
                "    browser: [$class: 'FisheyeGitRepositoryBrowser', repoUrl: 'https://fisheye.apache.org/browse/ant-git'],\n",
                "    browser: [$class: 'GitBlitRepositoryBrowser', repoUrl: 'https://github.com/MarkEWaite/git-plugin', projectName: 'git-plugin-project-name-value'],\n",
                "    browser: [$class: 'GitLab', repoUrl: 'https://gitlab.com/MarkEWaite/git-client-plugin', version: '12.10.1'],\n",
                "    browser: [$class: 'GitList', repoUrl: 'http://gitlist.org/'],\n", // Not a real gitlist site, just the org home page
                "    browser: [$class: 'GitWeb', repoUrl: 'https://git.ti.com/gitweb'],\n",
                "    browser: [$class: 'GithubWeb', repoUrl: 'https://github.com/jenkinsci/git-plugin'],\n",
                "    browser: [$class: 'Gitiles', repoUrl: 'https://gerrit.googlesource.com/gitiles/'],\n",
                "    browser: [$class: 'GogsGit', repoUrl: 'https://try.gogs.io/MarkEWaite/git-plugin'],\n",
                "    browser: [$class: 'KilnGit', repoUrl: 'https://kiln.example.com/MarkEWaite/git-plugin'],\n",
                "    browser: [$class: 'Phabricator', repo: 'source/tool-spacemedia', repoUrl: 'https://phabricator.wikimedia.org/source/tool-spacemedia/'],\n",
                "    browser: [$class: 'RedmineWeb', repoUrl: 'https://www.redmine.org/projects/redmine/repository'],\n",
                "    browser: [$class: 'Stash', repoUrl: 'https://markewaite@bitbucket.org/markewaite/git-plugin'],\n",
                "    browser: [$class: 'TFS2013GitRepositoryBrowser', repoUrl: 'https://markwaite.visualstudio.com/DefaultCollection/git-plugin/_git/git-plugin'],\n",
                "    browser: [$class: 'RhodeCode', repoUrl: 'https://code.rhodecode.com/rhodecode-enterprise-ce'],\n",
                "    browser: [$class: 'ViewGitWeb', repoUrl: 'https://git.ti.com/gitweb', projectName: 'viewgitweb-project-name-value'],\n", // Not likely a viewgit site, but reasonable approximation
        };
        String browser = browsersByClass[random.nextInt(browsersByClass.length)];
        return browser;
    }
}
