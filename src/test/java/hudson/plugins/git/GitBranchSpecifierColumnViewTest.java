package hudson.plugins.git;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;

import hudson.model.FreeStyleProject;
import hudson.model.ListView;
import hudson.model.Result;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import org.htmlunit.html.HtmlPage;
import org.jenkinsci.plugins.workflow.cps.CpsScmFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.LogRecorder;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

/**
 * Renders a list view with the "Git Branches" column, as opposed to {@link GitBranchSpecifierColumnTest}.
 */
@WithJenkins
class GitBranchSpecifierColumnViewTest {

    private final LogRecorder logging = new LogRecorder();

    @Test
    @Issue("https://github.com/jenkinsci/git-plugin/issues/3940")
    void listViewShowsBranchesOfHealthyJobNextToJobWithUnreadableSCM(JenkinsRule r) throws Exception {
        // A Pipeline job whose SCM class could not be loaded, e.g. after its plugin was uninstalled
        WorkflowJob unreadable = r.createProject(WorkflowJob.class, "unreadable-scm");
        unreadable.setDefinition(new CpsScmFlowDefinition(null, "Jenkinsfile"));
        // WorkflowJob.getSCMs() only reaches the null SCM once the job has a completed build
        r.buildAndAssertStatus(Result.FAILURE, unreadable);

        FreeStyleProject healthy = r.createFreeStyleProject("healthy");
        healthy.setScm(new GitSCM(
                GitSCM.createRepoList("https://example.com/repo.git", null),
                List.of(new BranchSpec("*/branch-shown-in-view")),
                null, null,
                Collections.emptyList()));

        ListView view = new ListView("git-branches");
        r.jenkins.addView(view);
        view.add(unreadable);
        view.add(healthy);
        view.getColumns().add(new GitBranchSpecifierColumn());
        view.save();

        // Jelly logs an exception thrown while evaluating the column, with its stack trace, and renders an empty cell
        logging.record("hudson.ExpressionFactory2", Level.WARNING)
                .record("hudson.ExpressionFactory2$JexlExpression", Level.WARNING)
                .capture(100);

        // goTo fails the test if the page does not render successfully
        HtmlPage page = r.createWebClient().goTo(view.getUrl());

        assertThat(page.asNormalizedText(), containsString("*/branch-shown-in-view"));
        assertThat(logging.getMessages(), not(hasItem(containsString("getBranchSpecifier"))));
    }
}
