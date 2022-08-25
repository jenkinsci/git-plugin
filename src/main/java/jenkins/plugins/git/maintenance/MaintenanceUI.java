package jenkins.plugins.git.maintenance;

import antlr.ANTLRException;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.ManagementLink;
import hudson.model.PeriodicWork;
import hudson.security.Permission;
import hudson.util.FormValidation;
import jenkins.model.GlobalConfiguration;
import jenkins.model.Jenkins;
import jenkins.plugins.git.maintenance.Logs.CacheRecord;
import jenkins.plugins.git.maintenance.Logs.XmlSerialize;
import net.sf.json.JSONObject;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.bind.JavaScriptMethod;
import org.kohsuke.stapler.interceptor.RequirePOST;
import org.kohsuke.stapler.verb.POST;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

@Extension
public class MaintenanceUI extends ManagementLink {

    JSONObject notification = new JSONObject();
    String OK = "OK";
    String ERROR = "ERROR";

    private static final Logger LOGGER = Logger.getLogger(MaintenanceUI.class.getName());

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
            LOGGER.log(Level.WARNING,"User doesn't have the required permission to access git-maintenance.");
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

                config.setCronSyntax(taskType, cronSyntax);
                config.setIsTaskConfigured(taskType, isApplied);
            }
            config.save();
            LOGGER.log(Level.FINE, "Maintenance configuration data stored successfully on Jenkins.");
            setNotification("Data saved on Jenkins.",OK);
            res.sendRedirect("");
            return;
        }
        LOGGER.log(Level.WARNING,"Couldn't load Global git maintenance configuration. Internal Error.");
        setNotification("Internal Error! Data not saved.",ERROR);
    }

    @RequirePOST
    @Restricted(NoExternalUse.class)
    public void doExecuteMaintenanceTask(StaplerRequest req, StaplerResponse res) throws IOException {
        if (!Jenkins.get().hasPermission(Jenkins.ADMINISTER)) {
            LOGGER.log(Level.WARNING,"User doesn't have the required permission to access git-maintenance");
            res.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }

        MaintenanceTaskConfiguration config = GlobalConfiguration.all().get(MaintenanceTaskConfiguration.class);
        if(config != null) {

            // Todo
            // schedule maintenance tasks only if all cron syntax are valid.
            // else can't schedule maintenance tasks.

            boolean updatedGitMaintenanceExecutionStatus = true;
            config.setIsGitMaintenanceRunning(updatedGitMaintenanceExecutionStatus);
            config.save();
            LOGGER.log(Level.FINE, "Git Maintenance tasks are scheduled for execution.");
            setNotification("Scheduled Maintenance Tasks.",OK);
        }else{
            setNotification("Internal Error! Tasks not scheduled.",ERROR);
        }

        res.sendRedirect("");
        return;
    }

    @RequirePOST
    @Restricted(NoExternalUse.class)
    public void doTerminateMaintenanceTask(StaplerRequest req, StaplerResponse res) throws IOException {
        if (!Jenkins.get().hasPermission(Jenkins.ADMINISTER)) {
            LOGGER.log(Level.WARNING,"User doesn't have the required permission to access git-maintenance");
            res.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }

        MaintenanceTaskConfiguration config = GlobalConfiguration.all().get(MaintenanceTaskConfiguration.class);
        if(config != null) {
            boolean updatedGitMaintenanceExecutionStatus = false;
            config.setIsGitMaintenanceRunning(updatedGitMaintenanceExecutionStatus);
            config.save();

            Cron cron = PeriodicWork.all().get(Cron.class);
            if (cron != null) {
                cron.terminateMaintenanceTaskExecution();
                cron.cancel();
                LOGGER.log(Level.FINE, "Terminated scheduling of Git Maintenance tasks.");
                setNotification("Terminated Maintenance Tasks.",OK);
            } else {
                LOGGER.log(Level.WARNING, "Couldn't Terminate Maintenance Task. Internal Error.");
                setNotification("Internal Error! Couldn't Terminate Tasks.",ERROR);
            }
        }else{
            setNotification("Internal Error! Couldn't Terminate Tasks.",ERROR);
        }
        res.sendRedirect("");
        return;
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
        LOGGER.log(Level.WARNING,"Couldn't load Global git maintenance configuration. Internal Error.");
        return new ArrayList<>();
    }

    public boolean getIsGitMaintenanceRunning(){
        MaintenanceTaskConfiguration config = GlobalConfiguration.all().get(MaintenanceTaskConfiguration.class);
        if(config != null)
            return config.getIsGitMaintenanceRunning();
        LOGGER.log(Level.WARNING,"Couldn't load Global git maintenance configuration. Internal Error.");
        return false;
    }

    public String getGitVersion(){
        List<Integer> gitVersion = MaintenanceTaskConfiguration.getGitVersion();
        return gitVersion.get(0) + "." + gitVersion.get(1) + "." + gitVersion.get(2);
    }

    public String updateGitVersionHelperText(){
        List<Integer> gitVersion = MaintenanceTaskConfiguration.getGitVersion();
        int gitMajor = gitVersion.get(0);
        int gitMinor = gitVersion.get(1);

        if((gitMajor == 2 && gitMinor >= 30) || (gitMajor > 2))
            return "";

        return "Use git version >= 2.30 to get full benefits of git maintenance.";
    }

    @NonNull
    @Override
    public Permission getRequiredPermission() {
        return Jenkins.ADMINISTER;
    }

    @JavaScriptMethod
    public void setNotification(String notification, String type){
        this.notification.put("msg",notification);
        this.notification.put("type",type);
    }

    @JavaScriptMethod
    public JSONObject getNotification(){
        // creating a copy...
        return JSONObject.fromObject(notification.toString());
    }

    public List<CacheRecord> getMaintenanceRecords(){
        // Currently on every refresh, parsing xml file from disk and then displaying it.
        // Todo improve performance by creating an object which loads the data initially from xml file and UI consumes the data from this object.
        // Need to plan a way to load data async. Not the entire data. Based on user requirements.
        return new XmlSerialize().getMaintenanceRecords();
    }
}
