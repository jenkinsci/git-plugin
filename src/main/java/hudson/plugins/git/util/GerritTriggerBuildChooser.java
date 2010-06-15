package hudson.plugins.git.util;


import hudson.Extension;
import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.model.Result;
import hudson.plugins.git.*;
import hudson.plugins.git.util.Build;
import hudson.plugins.git.util.BuildData;
import hudson.plugins.git.util.GitUtils;
import hudson.util.DescribableList;
import org.joda.time.DateTime;
import org.spearce.jgit.lib.ObjectId;

import java.io.BufferedReader;
import java.io.StringReader;
import java.io.IOException;
import java.util.*;

public class GerritTriggerBuildChooser implements IBuildChooser {

    private final String separator = "#";
    private IGitAPI               git;
    private GitUtils utils;
    private GitSCM                gitSCM;

    //-------- Data -----------
    private BuildData data;

    public GerritTriggerBuildChooser() {
        this.gitSCM = null;
        this.git = null;
        this.utils = null;
        this.data = null;

    }

    public GerritTriggerBuildChooser(GitSCM gitSCM, IGitAPI git, GitUtils utils, BuildData data)
    {
        this.gitSCM = gitSCM;
        this.git = git;
        this.utils = utils;
        this.data = data == null ? new BuildData() : data;
    }
    
    public void setUtilities(GitSCM gitSCM, IGitAPI git, GitUtils gitUtils) {
        this.gitSCM = gitSCM;
        this.git = git;
        this.utils = gitUtils;
        this.data = data == null ? new BuildData() : data;
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
    public Collection<Revision> getCandidateRevisions(boolean isPollCall, String singleBranch)
            throws GitException, IOException {

        try {
            ObjectId sha1 = git.revParse("FETCH_HEAD");
            
            // if polling for changes don't select something that has
            // already been built as a build candidate
            if (isPollCall && data.hasBeenBuilt(sha1))
                return Collections.<Revision>emptyList();

            Revision revision = new Revision(sha1);
            revision.getBranches().add(new Branch(singleBranch, sha1));

            if (!isPollCall) {
                // Now we cheat and add the parent as the last build on the branch, so we can
                // get the changelog working properly-ish.
                ObjectId parentSha1 = getFirstParent(ObjectId.toString(sha1));
                Revision parentRev = new Revision(parentSha1);
                parentRev.getBranches().add(new Branch(singleBranch, parentSha1));

                int prevBuildNum = 0;
                Result r = null;
                
                Build lastBuild = data.getLastBuildOfBranch(singleBranch);
                if (lastBuild != null) {
                    prevBuildNum = lastBuild.getBuildNumber();
                    r = lastBuild.getBuildResult();
                }

                // And we add this as the last revision built for this branch, so that history works.
                revisionBuilt(parentRev, prevBuildNum, r);
            }
                
            return Collections.singletonList(revision);
        }
        catch (GitException e) {
            // branch does not exist, there is nothing to build
            return Collections.<Revision>emptyList();
        }
    }

    @Override
    public Build revisionBuilt(Revision revision, int buildNumber, Result result) {
        Build build = new Build(revision, buildNumber, result);
        data.saveBuild(build);
        return build;
    }
    

    @Override
    public Action getData() {
        return data;
    }

    private ObjectId getFirstParent(String revName) throws GitException {
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


}
