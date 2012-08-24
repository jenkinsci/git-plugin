package hudson.plugins.git;

import hudson.Extension;
import hudson.model.ParameterValue;
import hudson.model.SimpleParameterDefinition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.sf.json.JSONObject;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

public class GitRefParameterDefinition extends SimpleParameterDefinition {

    private String repo;
    private boolean reverse;

    @DataBoundConstructor
    public GitRefParameterDefinition(String name, String repo, boolean reverse) {
        super(name, "Select a Git reference (Repository URL: " + repo + ")");
        this.repo = repo;
        this.reverse = reverse;
    }

    @Override
    public ParameterValue createValue(StaplerRequest req, JSONObject jo) {
        final GitRefParameterValue value = req.bindJSON(GitRefParameterValue.class, jo);
        return value;
    }

    @Override
    public ParameterValue createValue(String ref) {
        return new GitRefParameterValue(getName(), getRepo(), ref);
    }

    @Extension
    public static class DescriptorImpl extends ParameterDescriptor {
        @Override
        public String getDisplayName() {
            return "Git Reference Selector";
        }
    }

    public List<String> getRefs() {
        final ArrayList<String> refs = GitStandaloneAPI.gitListReferences(GitStandaloneAPI.anyGitTool(), repo);

        // Display the refs in reverse order?
        if (reverse) {
            Collections.reverse(refs);
        }

        return refs;
    }

    public String getRepo() {
        return repo;
    }

    public void setRepo(String repo) {
        this.repo = repo;
    }

    public boolean getReverse() {
        return reverse;
    }

    public void setReverse(boolean reverse) {
        this.reverse = reverse;
    }

}
