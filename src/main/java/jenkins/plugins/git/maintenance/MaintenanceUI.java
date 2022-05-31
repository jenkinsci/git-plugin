package jenkins.plugins.git.maintenance;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.ManagementLink;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.interceptor.RequirePOST;

import java.io.IOException;
import java.util.ArrayList;

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
        System.out.println("Saving");
        res.sendRedirect("");
    }

    @RequirePOST
    @Restricted(NoExternalUse.class)
    public void doExecute(StaplerRequest req, StaplerResponse res) throws IOException {
        System.out.println("Executing...");
        res.sendRedirect("");
    }

    @RequirePOST
    @Restricted(NoExternalUse.class)
    public void doTerminate(StaplerRequest req, StaplerResponse res) throws IOException {
        System.out.println("Stopping...");
        res.sendRedirect("");
    }

    public ArrayList<TaskData> getMaintenanceTask(){
        // Can check if git version doesn't support a maintenance task and remove that maintenance task form the UI.
        ArrayList<TaskData> tasks = new ArrayList();
        tasks.add(new TaskData("gc","Click to view gc"));
        tasks.add(new TaskData("commit-graph","Click to view commit graph"));
        tasks.add(new TaskData("incremental-repack","Click to view Incremental Repack"));
        tasks.add(new TaskData("prefetch","Click to view prefetch"));
        tasks.add(new TaskData("loose-objects","Click to view loose objects"));
        return tasks;
    }
//
//    @NonNull
//    @Override
//    public Permission getRequiredPermission() {
//        return Jenkins.ADMINISTER;
//    }
//
}
