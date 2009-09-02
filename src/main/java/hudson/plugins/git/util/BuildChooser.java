package hudson.plugins.git.util;

import hudson.model.Action;
import hudson.model.Result;
import hudson.plugins.git.Branch;
import hudson.plugins.git.BranchSpec;
import hudson.plugins.git.GitException;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.IGitAPI;
import hudson.plugins.git.Revision;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.Set;

import org.spearce.jgit.lib.ObjectId;

public class BuildChooser implements IBuildChooser {

    private final IGitAPI               git;
    private final GitUtils              utils;
    private final GitSCM                gitSCM;

    //-------- Data -----------
    private final BuildData             data;

    public BuildChooser(GitSCM gitSCM, IGitAPI git, GitUtils utils, BuildData data)
    {
        this.gitSCM = gitSCM;
        this.git = git;
        this.utils = utils;
        this.data = data == null ? new BuildData() : data;
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
    public Collection<Revision> getCandidateRevisions(boolean isPollCall, String singleBranch)
            throws GitException, IOException {
        // if the branch name contains more wildcards then the simple usecase
        // does not apply and we need to skip to the advanced usecase
        if (singleBranch == null || singleBranch.contains("*"))
            return getAdvancedCandidateRevisions(isPollCall);

        // check if we're trying to build a specific commit
        // this only makes sense for a build, there is no
        // reason to poll for a commit
        if (!isPollCall && singleBranch.matches("[0-9a-f]{6,40}"))
        {
            try
            {
                ObjectId sha1 = git.revParse(singleBranch);
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

        // if it doesn't contain '/' then it could be either a tag or an unqualified branch
        if (!singleBranch.contains("/")) {
	        // the 'branch' could actually be a tag:
	        Set<String> tags = git.getTagNames(singleBranch);
	        if(tags.size() == 0) {
		        // its not a tag, so lets fully qualify the branch
		            String repository = gitSCM.getRepositories().get(0).getName();
		            singleBranch = repository + "/" + singleBranch;
	        }
        }

        try
        {
            ObjectId sha1 = git.revParse(singleBranch);

            // if polling for changes don't select something that has
            // already been built as a build candidate
            if (isPollCall && data.hasBeenBuilt(sha1))
                return Collections.<Revision>emptyList();

            Revision revision = new Revision(sha1);
            revision.getBranches().add(new Branch(singleBranch, sha1));
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
    private Collection<Revision> getAdvancedCandidateRevisions(boolean isPollCall) throws GitException, IOException
    {
        // 1. Get all the (branch) revisions that exist
        Collection<Revision> revs = utils.getAllBranchRevisions();

        // 2. Filter out any revisions that don't contain any branches that we
        // actually care about (spec)
        for (Iterator<Revision> i = revs.iterator(); i.hasNext();)
        {
            Revision r = i.next();

            // filter out uninteresting branches
            for (Iterator<Branch> j = r.getBranches().iterator(); j.hasNext();)
            {
                Branch b = j.next();
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
        for (Iterator<Revision> i = revs.iterator(); i.hasNext();)
        {
            Revision r = i.next();

            if (data.hasBeenBuilt(r.getSha1()))
            {
                i.remove();
            }
        }

        // if we're trying to run a build (not an SCM poll) and nothing new
        // was found then just run the last build again
        if (!isPollCall && revs.size() == 0 && data.getLastBuiltRevision() != null)
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
