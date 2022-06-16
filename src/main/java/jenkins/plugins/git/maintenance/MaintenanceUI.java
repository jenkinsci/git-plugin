package jenkins.plugins.git.maintenance;

import antlr.ANTLRException;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.ManagementLink;
import hudson.security.Permission;
import hudson.util.FormValidation;
import jenkins.model.GlobalConfiguration;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.interceptor.RequirePOST;
import org.kohsuke.stapler.verb.POST;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;

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
    public void doSave(StaplerRequest req, StaplerResponse res) throws IOException, ServletException {
        if (!Jenkins.get().hasPermission(Jenkins.ADMINISTER)) {
            res.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }

        JSONObject formData = req.getSubmittedForm();
        MaintenanceTaskConfiguration config = GlobalConfiguration.all().get(MaintenanceTaskConfiguration.class);

        if(config != null) {
            for (TaskType taskType : TaskType.values()) {
                JSONObject maintenanceData = formData.getJSONObject(taskType.toString());
                String cronSyntax = maintenanceData.getString("cronSyntax");
                boolean isApplied = maintenanceData.getBoolean("isApplied");

                // Need to perform form validation again to avoid incorrect save of data.
                config.setCronSyntax(taskType, cronSyntax);
                config.setIsTaskConfigured(taskType, isApplied);
            }
            config.save();
        }

        System.out.println("Saving");
        res.sendRedirect("");
    }

    @RequirePOST
    @Restricted(NoExternalUse.class)
    public void doToggleExecutionState(StaplerRequest req, StaplerResponse res) throws IOException {
        if (!Jenkins.get().hasPermission(Jenkins.ADMINISTER)) {
            res.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }

        MaintenanceTaskConfiguration config = GlobalConfiguration.all().get(MaintenanceTaskConfiguration.class);
        if(config != null) {
            config.setIsGitMaintenanceRunning();
            config.save();
        }

        System.out.println("Executing...");
        res.sendRedirect("");
    }

    @POST
    @Restricted(NoExternalUse.class)
    public FormValidation doCheckCronSyntax(@QueryParameter String cronSyntax) throws ANTLRException {
        try {
            Jenkins.get().checkPermission(Jenkins.ADMINISTER);
            if (cronSyntax.isEmpty())
                return FormValidation.ok();

            String msg = MaintenanceTaskConfiguration.checkSanity(cronSyntax);

            if (msg != null) {
                return FormValidation.error(msg);
            }
            return FormValidation.ok();
        }catch(ANTLRException e){
            return FormValidation.error(e.getMessage());
        }
    }

    public List<Task> getMaintenanceTasks(){
        // Can check if git version doesn't support a maintenance task and remove that maintenance task from the UI.
        MaintenanceTaskConfiguration config = GlobalConfiguration.all().get(MaintenanceTaskConfiguration.class);
        if(config != null)
            return config.getMaintenanceTasks();

        // need to throw error;
        return null;
    }

    public boolean getIsGitMaintenanceRunning(){
        MaintenanceTaskConfiguration config = GlobalConfiguration.all().get(MaintenanceTaskConfiguration.class);
        if(config != null)
            return config.getIsGitMaintenanceRunning();

        // need to throw error;
        return false;
    }

    @NonNull
    @Override
    public Permission getRequiredPermission() {
        return Jenkins.ADMINISTER;
    }

}
