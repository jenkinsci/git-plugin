package jenkins.plugins.git.maintenance;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.ManagementLink;
import hudson.security.Permission;
import jenkins.model.Jenkins;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.interceptor.RequirePOST;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;

@Extension
public class MaintenanceUI extends ManagementLink {

    @Override
    public String getIconFileName() {
        return jenkins.model.Jenkins.RESOURCE_PATH + "/plugin/git/icons/git-maintenance.svg";
    }

    @Override
    public String getDisplayName() {
        return "Git Maintenance";
    }

    @Override
    public String getUrlName() {
        return "maintenance";
    }

    @Override
    public String getDescription() {
        return "Maintain your Repositories to improve git command performance.";
    }

    public @NonNull String getCategoryName() {
        return "CONFIGURATION";
    }

    @RequirePOST
    @Restricted(NoExternalUse.class)
    public void doSave(StaplerRequest req, StaplerResponse res) throws IOException {
        if (!Jenkins.get().hasPermission(Jenkins.ADMINISTER)) {
            res.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }
        System.out.println("Saving");
        res.sendRedirect("");
    }

    @RequirePOST
    @Restricted(NoExternalUse.class)
    public void doExecute(StaplerRequest req, StaplerResponse res) throws IOException {
        if (!Jenkins.get().hasPermission(Jenkins.ADMINISTER)) {
            res.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }
        System.out.println("Executing...");
        res.sendRedirect("");
    }

    @RequirePOST
    @Restricted(NoExternalUse.class)
    public void doTerminate(StaplerRequest req, StaplerResponse res) throws IOException {
        if (!Jenkins.get().hasPermission(Jenkins.ADMINISTER)) {
            res.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }
        System.out.println("Stopping...");
        res.sendRedirect("");
    }

    public Map<TaskType,Task> getMaintenanceTask(){
        // Can check if git version doesn't support a maintenance task and remove that maintenance task form the UI.

        // Use a descriptor to remove hardcoded dependency
        return new MaintenanceTaskConfiguration().getMaintenanceTasks();
    }

    @NonNull
    @Override
    public Permission getRequiredPermission() {
        return Jenkins.ADMINISTER;
    }

}
