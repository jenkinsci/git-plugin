package hudson.plugins.git.util;

import hudson.FilePath;
import hudson.FilePath.FileCallable;
import hudson.model.AbstractBuild;
import hudson.model.Action;
import hudson.model.ParametersAction;
import hudson.model.Result;
import hudson.plugins.git.Branch;
import hudson.plugins.git.BranchSpec;
import hudson.plugins.git.GitException;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.IGitAPI;
import hudson.plugins.git.Revision;
import hudson.remoting.VirtualChannel;
import hudson.util.XStream2;

import java.io.IOException;


import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;


import org.spearce.jgit.lib.ObjectId;
import org.spearce.jgit.transport.RemoteConfig;


public class BuildChooser implements IBuildChooser {  
    
    IGitAPI               git;
    GitUtils              utils;
    GitSCM                gitSCM;

    //-------- Data -----------
    BuildData             data;

    public BuildChooser(GitSCM gitSCM, IGitAPI git, GitUtils utils, BuildData data)
    {
        this.gitSCM = gitSCM;
        this.git = git;
        this.utils = utils;
        this.data = data;
        if( data == null )
            this.data = new BuildData();
    }
    /**
     * Determines which Revisions to build.
     * 
     * If only one branch is chosen and only one repository is listed, then
     * just attempt to find the latest revision number for the chosen branch.
     * 
     * If multiple branches are selected or the branches include wildcards, then
     * use the advanced usecase as defined in the getAdvancedCandidateRevisons
     * method.
     * 
     * @throws IOException 
     * @throws GitException 
     */
    public Collection<Revision> getCandidateRevisions(AbstractBuild build)
            throws GitException, IOException {
        // if we have multiple branches skip to advanced usecase
        if (gitSCM.getBranches().size() != 1)
            return getAdvancedCandidateRevisions(build);

        // if we have multiple repositories skip to advanced usecase
        if (gitSCM.getRepositories().size() != 1)
            return getAdvancedCandidateRevisions(build);

        String branch = gitSCM.getBranches().get(0).getName();
        String repository = gitSCM.getRepositories().get(0).getName();

        // replace repository wildcard with repository name
        if (branch.startsWith("*/"))
            branch = repository + branch.substring(1);

        // if the branch name contains more wildcards then the simple usecase
        // does not apply and we need to skip to the advanced usecase
        if (branch.contains("*"))
            return getAdvancedCandidateRevisions(build);

        // substitute build parameters if available
        if (build != null)
        {
            ParametersAction parameters = build.getAction(ParametersAction.class);
            if (parameters != null)
                branch = parameters.substitute(build, branch);
        }
        
        // check if we're trying to build a specific commit
        // this only makes sense for a build, there is no
        // reason to poll for a commit
        if (build != null && branch.matches("[0-9a-f]{6,40}"))
        {
            try
            {
                ObjectId sha1 = git.revParse(branch);
                Revision revision = new Revision(sha1);
                revision.getBranches().add(new Branch("detached", sha1));
                return Collections.singletonList(revision);
            }
            catch (GitException e)
            {
                // revision does not exist, may still be a branch
                // for example a branch called "badface" would show up here
            }
        }
        
        // fully qualify the branch if needed
        if (!branch.contains("/"))
            branch = repository + "/" + branch;
        
        try
        {
            ObjectId sha1 = git.revParse(branch);
            
            // if polling for changes don't select something that has
            // already been built as a build candidate
            if (build == null && data.hasBeenBuilt(sha1))
                return Collections.<Revision>emptyList();
            
            Revision revision = new Revision(sha1);
            revision.getBranches().add(new Branch(branch, sha1));
            return Collections.singletonList(revision);
        }
        catch (GitException e)
        {
            // branch does not exist, there is nothing to build
            return Collections.<Revision>emptyList();
        }
    }
    
    /**
     * In order to determine which Revisions to build.
     * 
     * Does the following :
     *  1. Find all the branch revisions
     *  2. Filter out branches that we don't care about from the revisions.
     *     Any Revisions with no interesting branches are dropped.
     *  3. Get rid of any revisions that are wholly subsumed by another
     *     revision we're considering.
     *  4. Get rid of any revisions that we've already built.
     *  
     *  NB: Alternate IBuildChooser implementations are possible - this
     *  may be beneficial if "only 1" branch is to be built, as much of
     *  this work is irrelevant in that usecase.
     * @throws IOException 
     * @throws GitException 
     */
    private Collection<Revision> getAdvancedCandidateRevisions(AbstractBuild build) throws GitException, IOException
    {
        // 1. Get all the (branch) revisions that exist 
        Collection<Revision> revs = utils.getAllBranchRevisions();

        // 2. Filter out any revisions that don't contain any branches that we
        // actually care about (spec)
        for (Iterator i = revs.iterator(); i.hasNext();)
        {
            Revision r = (Revision) i.next();

            // filter out uninteresting branches
            for (Iterator j = r.getBranches().iterator(); j.hasNext();)
            {
                Branch b = (Branch) j.next();
                boolean keep = false;
                for (BranchSpec bspec : gitSCM.getBranches())
                {
                    if (bspec.matches(b.getName()))
                    {
                        keep = true;
                        break;
                    }
                }

                if (!keep) j.remove();

            }

            if (r.getBranches().size() == 0) i.remove();

        }

        // 3. We only want 'tip' revisions
        revs = utils.filterTipBranches(revs);

        // 4. Finally, remove any revisions that have already been built.
        for (Iterator i = revs.iterator(); i.hasNext();)
        {
            Revision r = (Revision) i.next();

            if (data.hasBeenBuilt(r.getSha1())) 
            {
                i.remove();    
            }
        }
        
        // if we're trying to run a build (not an SCM poll) and nothing new
        // was found then just run the last build again
        if (build != null && revs.size() == 0 && data.getLastBuiltRevision() != null)
        {
            return Collections.singletonList(data.getLastBuiltRevision());
        }

        return revs;
    }

    public Build revisionBuilt(Revision revision, int buildNumber, Result result )
    {        
        Build build = new Build(revision, buildNumber, result);
        data.saveBuild(build);
        return build;
    }

    
    public Action getData()
    {
        return data;
    }

}
