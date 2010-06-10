package hudson.plugins.git;

import hudson.DescriptorExtensionList;
import hudson.EnvVars;
import hudson.Extension;
import hudson.model.EnvironmentSpecific;
import hudson.model.Hudson;
import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.scm.SCM;
import hudson.scm.SCMDescriptor;
import hudson.slaves.NodeSpecific;
import hudson.tools.ToolDescriptor;
import hudson.tools.ToolInstallation;
import hudson.tools.ToolProperty;
import hudson.util.FormValidation;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import javax.servlet.ServletException;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


/**
 * Information about Git installation.
 *
 * @author Jyrki Puttonen
 */
public final class GitTool extends ToolInstallation implements NodeSpecific<GitTool>, EnvironmentSpecific<GitTool> {

    @DataBoundConstructor
    public GitTool(String name, String home, List<? extends ToolProperty<?>> properties) {
        super(name, home, properties);
    }

    static transient final String defaultValueName = "Default";

    public String getGitExe() {
        return getHome();
    }

    public static void onLoaded() {
        //Creates default tool installation if needed. Uses "git" or migrates data from previous versions

        DescriptorImpl descriptor = (DescriptorImpl) Hudson.getInstance().getDescriptor(GitTool.class);
        GitTool[] installations = getInstallations(descriptor);

        if (installations.length > 0) {
            //No need to initialize if there's already something
            return;
        }
        DescriptorExtensionList<SCM, SCMDescriptor<?>> scms = GitSCM.all();
        String defaultGitExe = File.separatorChar != '/' ? "git.exe" : "git";

        for (SCMDescriptor<?> s : scms) {
            if (s instanceof GitSCM.DescriptorImpl) {
                //Get previous settings from descriptor
                GitSCM.DescriptorImpl desc = (GitSCM.DescriptorImpl) s;
                if (desc.getOldGitExe() != null) {
                    defaultGitExe = desc.getOldGitExe();
                }
            }
        }

        GitTool tool = new GitTool(defaultValueName, defaultGitExe, false, Collections.<ToolProperty<?>>emptyList());

        descriptor.setInstallations(new GitTool[] { tool });
        descriptor.save();
    }

    private static GitTool[] getInstallations(DescriptorImpl descriptor) {
        GitTool[] installations = null;
        try {
            installations = descriptor.getInstallations();
        } catch (NullPointerException e) {
            installations = new GitTool[0];
        }
        return installations;
    }


    public GitTool forNode(Node node, TaskListener log) throws IOException, InterruptedException {
        return new GitTool(getName(), translateFor(node, log), Collections.<ToolProperty<?>>emptyList());
    }

    public GitTool forEnvironment(EnvVars environment) {
        return new GitTool(getName(), environment.expand(getHome()), Collections.<ToolProperty<?>>emptyList());
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) Hudson.getInstance().getDescriptor(GitTool.class);
    }

    @Extension
    public static class DescriptorImpl extends ToolDescriptor<GitTool> {

        public DescriptorImpl() {
            super();
            load();
        }

        @Override
        public String getDisplayName() {
            return "Git";
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject json) throws FormException {
            super.configure(req, json);
            save();
            return true;
        }

        public FormValidation doCheckHome(@QueryParameter File value)
            throws IOException, ServletException {

            Hudson.getInstance().checkPermission(Hudson.ADMINISTER);
            String path = value.getPath();

            return FormValidation.validateExecutable(path);

        }
    }

}

