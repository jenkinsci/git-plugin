/*
 *  The MIT License
 *
 *  Copyright 2010 Sony Ericsson Mobile Communications. All rights reservered.
 *  Copyright 2012 Sony Mobile Communications AB. All rights reservered.
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in
 *  all copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 *  THE SOFTWARE.
 */
package hudson.plugins.git.rebuild;

import hudson.matrix.MatrixRun;
import hudson.model.Action;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Cause;
import hudson.model.CauseAction;
import hudson.model.Hudson;
import hudson.plugins.git.Branch;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.Revision;
import hudson.plugins.git.util.BuildData;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.servlet.ServletException;

import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import com.google.common.base.Joiner;
import com.google.common.collect.Lists;

public class GitRebuildAction implements Action {

    public static final String REBUILDURL = "gitrebuild";
    private transient AbstractBuild<?, ?> build;

    public GitRebuildAction() {
    }

    public AbstractBuild<?, ?> getBuild() {
        return build;
    }

    public String getRebuildurl() {
        return REBUILDURL;
    }

    /**
     * Method will return current project.
     *
     * @return currentProject.
     */
    public AbstractProject getProject() {

        if (build != null) {
            return build.getProject();
        }

        AbstractProject currentProject = null;
        StaplerRequest request = Stapler.getCurrentRequest();
        if (request != null) {
            currentProject = request.findAncestorObject(AbstractProject.class);
        }

        return currentProject;
    }

    public String getIconFileName() {
        if (isRebuildAvailable()) {
            return "clock.gif";
        } else {
            return null;
        }
    }

    public String getDisplayName() {
        if (isRebuildAvailable()) {
            return "Rebuild Git Revision";
        } else {
            return null;
        }
    }

    public String getUrlName() {
        if (isRebuildAvailable()) {
            return getRebuildurl();
        } else {
            return null;
        }
    }

    /**
     * Handles the rebuild request and redirects to parameterized
     * and non parameterized build when needed.
     *
     * @param request  StaplerRequest the request.
     * @param response StaplerResponse the response handler.
     * @throws IOException          in case of Stapler issues
     * @throws ServletException     if something unfortunate happens.
     * @throws InterruptedException if something unfortunate happens.
     */
    public void doIndex(StaplerRequest request, StaplerResponse response) throws
            IOException, ServletException, InterruptedException {
    	
        AbstractBuild currentBuild = request.findAncestorObject(AbstractBuild.class);
        if (currentBuild != null) {
            nonParameterizedRebuild(currentBuild, response);
        }
    }

    /**
     * Call this method while rebuilding
     * non parameterized build.     .
     *
     * @param currentBuild current build.
     * @param response     current response object.
     * @throws ServletException     if something unfortunate happens.
     * @throws IOException          if something unfortunate happens.
     * @throws InterruptedException if something unfortunate happens.
     */
    public void nonParameterizedRebuild(AbstractBuild currentBuild, StaplerResponse
            response) throws ServletException, IOException, InterruptedException {

        getProject().checkPermission(AbstractProject.BUILD);
        List<Action> actions = copyBuildCausesAndAddUserCause(currentBuild);
        Hudson.getInstance().getQueue().schedule(currentBuild.getProject(), 0, actions);
        response.sendRedirect("../../");
    }

    public static Revision getLastRevision(AbstractBuild<?, ?> fromBuild) {
    	GitSCM git_scm = (GitSCM) fromBuild.getProject().getScm();
        BuildData build_data = git_scm.getBuildData(fromBuild, false);
        return build_data.lastBuild.getRevision();
    }
    
    public static class GitRebuildCause extends Cause {

    	public final Revision original_revision;
    	
        public GitRebuildCause(Revision original_revision) {
        	this.original_revision = original_revision;
        }
        
        public String getSHA1() {
            return this.original_revision.getSha1String();
        }
        
        public String getBranchList() {
        	Collection<String> branch_names = Lists.newArrayList();
        	for (Branch x : this.original_revision.getBranches())
        		branch_names.add(x.getName());
            return Joiner.on(", ").join(branch_names);
        }

        @Override
        public String getShortDescription() {
            String description = "This is a rebuild of SHA1: " + getSHA1() + ", which is (was) pointed to by these branches: " + getBranchList();
            return description;
        }
    }
    
    /**
     * Extracts the build causes and adds or replaces the {@link hudson.model.Cause.UserIdCause}. The result is a
     * list of all build causes from the original build (might be an empty list), plus a
     * {@link hudson.model.Cause.UserIdCause} for the user who started the rebuild.
     *
     * @param fromBuild the build to copy the causes from.
     * @return list with all original causes and a {@link hudson.model.Cause.UserIdCause}.
     */
    private List<Action> copyBuildCausesAndAddUserCause(AbstractBuild<?, ?> fromBuild) {
        List currentBuildCauses = fromBuild.getCauses();

        List<Action> actions = new ArrayList<Action>(currentBuildCauses.size());
        boolean hasUserCause = false;
        for (Object buildCause : currentBuildCauses) {
            if (buildCause instanceof Cause.UserIdCause) {
                hasUserCause = true;
                actions.add(new CauseAction(new Cause.UserIdCause()));
            } else {
                actions.add(new CauseAction((Cause) buildCause));
            }
        }
        if (!hasUserCause) {
            actions.add(new CauseAction(new Cause.UserIdCause()));
        }

        if (fromBuild.getProject().getScm() instanceof GitSCM)
        	actions.add(new CauseAction(new GitRebuildCause(getLastRevision(fromBuild))));

        return actions;
    }

    /**
     * Method for checking whether current build is sub job(MatrixRun) of Matrix
     * build.
     *
     * @return boolean
     */
    public boolean isMatrixRun() {
        StaplerRequest request = Stapler.getCurrentRequest();
        if (request != null) {
            build = request.findAncestorObject(AbstractBuild.class);
            if (build != null && build instanceof MatrixRun) {
                return true;
            }
        }
        return false;
    }

    /**
     * Method for checking,whether the rebuild functionality would be available
     * for build.
     *
     * @return boolean
     */
    public boolean isRebuildAvailable() {

        AbstractProject project = getProject();
        return project != null && 
                project.hasPermission(AbstractProject.BUILD) && 
                project.isBuildable() && !(project.isDisabled()) &&
                !isMatrixRun() &&
                project.getScm() instanceof GitSCM;

    }
}
