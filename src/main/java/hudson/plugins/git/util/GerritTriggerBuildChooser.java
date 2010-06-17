package hudson.plugins.git.util;


import hudson.Extension;
import hudson.model.TaskListener;
import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.model.Result;
import hudson.plugins.git.*;
import hudson.plugins.git.util.Build;
import hudson.plugins.git.util.BuildData;
import hudson.plugins.git.util.GitUtils;
import hudson.util.DescribableList;
import org.joda.time.DateTime;
import org.kohsuke.stapler.DataBoundConstructor;
import org.spearce.jgit.lib.ObjectId;

import java.io.BufferedReader;
import java.io.StringReader;
import java.io.IOException;
import java.util.*;

public class GerritTriggerBuildChooser extends BuildChooser {
    private final String separator = "#";

    @DataBoundConstructor
    public GerritTriggerBuildChooser() {
    }

    /**
     * Determines which Revisions to build.
     *
     * 
     *
     * Doesn't care about branches.
     * @throws IOException
     * @throws GitException
     */
    @Override
    public Collection<Revision> getCandidateRevisions(boolean isPollCall, String singleBranch,
                                                      IGitAPI git, TaskListener listener, BuildData data)
            throws GitException, IOException {

        try {
            ObjectId sha1 = git.revParse("FETCH_HEAD");
            
            Revision revision = new Revision(sha1);
            revision.getBranches().add(new Branch(singleBranch, sha1));

            return Collections.singletonList(revision);
        }
        catch (GitException e) {
            // branch does not exist, there is nothing to build
            return Collections.<Revision>emptyList();
        }
    }

    @Override
    public Build prevBuildForChangelog(String singleBranch, BuildData data, IGitAPI git) {
        ObjectId sha1 = git.revParse("FETCH_HEAD");

        // Now we cheat and add the parent as the last build on the branch, so we can
        // get the changelog working properly-ish.
        ObjectId parentSha1 = getFirstParent(ObjectId.toString(sha1), git);
        Revision parentRev = new Revision(parentSha1);
        parentRev.getBranches().add(new Branch(singleBranch, parentSha1));
        
        int prevBuildNum = 0;
        Result r = null;
        
        Build lastBuild = data.getLastBuildOfBranch(singleBranch);
        if (lastBuild != null) {
            prevBuildNum = lastBuild.getBuildNumber();
            r = lastBuild.getBuildResult();
        }

        Build newLastBuild = new Build(parentRev, prevBuildNum, r);
        
        return newLastBuild;
    }
        
    private ObjectId getFirstParent(String revName, IGitAPI git) throws GitException {
        String result = ((GitAPI)git).launchCommand("log", "-1", "--pretty=format:%P", revName);
        return ObjectId.fromString(firstLine(result).trim());
    }

    private String firstLine(String result) {
        BufferedReader reader = new BufferedReader(new StringReader(result));
        String line;
        try {
            line = reader.readLine();
            if (line == null)
                return null;
            if (reader.readLine() != null)
                throw new GitException("Result has multiple lines");
        } catch (IOException e) {
            throw new GitException("Error parsing result", e);
        }

        return line;
    }

    @Extension
    public static final class DescriptorImpl extends BuildChooserDescriptor {
        @Override
        public String getDisplayName() {
            return "Gerrit Trigger";
        }

        @Override
        public String getLegacyId() {
            return "Gerrit Trigger";
        }
    }

}
