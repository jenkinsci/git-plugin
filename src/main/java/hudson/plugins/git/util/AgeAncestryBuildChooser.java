package hudson.plugins.git.util;

import hudson.Extension;
import hudson.model.TaskListener;
import hudson.plugins.git.AncestryRefSpec;
import hudson.plugins.git.GitException;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.Revision;

import java.io.IOException;
import java.util.Collection;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.jgit.errors.AmbiguousObjectException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.errors.RevisionSyntaxException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.jenkinsci.plugins.gitclient.GitClient;
import org.joda.time.DateMidnight;
import org.joda.time.DateTime;
import org.joda.time.base.AbstractInstant;
import org.kohsuke.stapler.DataBoundConstructor;

import com.google.common.base.Predicate;
import com.google.common.collect.Lists;

/**
 * Selects branches to build based on commits contained in their
 * ancestry.
 * 
 * This is useful for introducing new Jenkins jobs
 * that are specific to some new feature in the repo, without
 * causing retroactive builds of (potentially thousands of)
 * branches that do not support that feature.
 *
 */
public class AgeAncestryBuildChooser extends BuildChooser {

	private static final long serialVersionUID = -4349783151051016888L;

	private static final Logger LOGGER = Logger.getLogger(AgeAncestryBuildChooser.class.getName());
	
	@DataBoundConstructor
	public AgeAncestryBuildChooser() {}


	static class BranchAgeBuildFilter implements Predicate<RevCommit> {
		
		private final int age_days;
		AbstractInstant oldest_allowable_commit_date = null;
		
		private boolean isEnabled() {
			return this.age_days > 0;
		}
		
		BranchAgeBuildFilter(int age_days) {
			this.age_days = age_days;
			if (isEnabled())
				this.oldest_allowable_commit_date = DateMidnight.now().minusDays(age_days);
		}

		public boolean apply(RevCommit revision_commit) {
			return !isEnabled()
					|| new DateTime(revision_commit.getCommitterIdent().getWhen())
						.isAfter(this.oldest_allowable_commit_date);
		}
	}
	
	/**
	 * Note that a RevWalk instance is being passed all over the place,
	 * instead of being recreated from a Repository instance.
	 * This is because RevWalk.isMergedInto() must only operate on commits
	 * that have been parsed by the same RevWalk instance!
	 */
	static class AncestryFilter implements Predicate<RevCommit> {

		private final Collection<RevCommit> valid_ancestors;
		private final RevWalk revwalk;
		
		public static AncestryFilter createNew(Repository repository, RevWalk walk, Collection<AncestryRefSpec> configured_ancestors) {
			Collection<RevCommit> valid_ancestors = getAncestorWhitelist(repository, walk, configured_ancestors);
			return new AncestryFilter(valid_ancestors, walk);
		}
		
		private AncestryFilter(
				Collection<RevCommit> valid_ancestors,
				RevWalk revwalk) {
			this.valid_ancestors = valid_ancestors;
			this.revwalk = revwalk;
		}
		
		public boolean apply(RevCommit revision_commit) {

			if (this.valid_ancestors.isEmpty())
				return true;
			
			try {
				return hasWhitelistedAncestor(revision_commit, this.valid_ancestors, this.revwalk);
			} catch (MissingObjectException e) {
				e.printStackTrace();
			} catch (IncorrectObjectTypeException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
			
			return false;
		}
		
		/**
		 * Validates ancestors specified in the configuration fields
		 * as valid Git commits.
		 */
		private static Collection<RevCommit> getAncestorWhitelist(Repository repository, RevWalk walk, Collection<AncestryRefSpec> configured_required_ancestor_refs) {
			
			Collection<RevCommit> valid_ancestors = Lists.newArrayList();		
			for (AncestryRefSpec refspec : configured_required_ancestor_refs) {

				try {

					ObjectId resolved_ancestor_object = repository.resolve(refspec.getName());
					if (resolved_ancestor_object != null) {
						RevCommit resolved_ancestor_commit = walk.parseCommit(resolved_ancestor_object);
						valid_ancestors.add(resolved_ancestor_commit);
					}
					
				} catch (RevisionSyntaxException e) {
					e.printStackTrace();
				} catch (AmbiguousObjectException e) {
					e.printStackTrace();
				} catch (IncorrectObjectTypeException e) {
					e.printStackTrace();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}

			return valid_ancestors;
		}
		
		private static boolean hasWhitelistedAncestor(RevCommit revision_commit, Collection<RevCommit> valid_ancestors, RevWalk walk) throws MissingObjectException, IncorrectObjectTypeException, IOException {
			for (RevCommit valid_ancestor : valid_ancestors)
				if (walk.isMergedInto(valid_ancestor, revision_commit))
					return true;

			return false;
		}
	}
	
	/**
	 * Determines which revision to build
	 * @throws InterruptedException 
	 */
	@Override
	public Collection<Revision> getCandidateRevisions(boolean isPollCall,
			String singleBranch,
			GitClient git,
			TaskListener listener,
			BuildData data,
			BuildChooserContext context)
					throws GitException, IOException, InterruptedException {
		
		BuildChooser default_chooser = new DefaultBuildChooser();
		
		// This fails with the following error:
		//		java.lang.ClassCastException: class hudson.model.FreeStyleBuild is
		//		returned from public hudson.model.AbstractBuild
		//		hudson.plugins.git.GitSCM$BuildChooserContextImpl$1.getBuild() on class
		//		hudson.plugins.git.GitSCM$BuildChooserContextImpl$1 but it's not serializable
//		GitSCM git_scm = (GitSCM) context.getBuild().getProject().getScm();

		final GitSCM git_scm = this.gitSCM;
		default_chooser.gitSCM = git_scm;
		Collection<Revision> original_revision_collection = default_chooser.getCandidateRevisions(
				isPollCall,
				singleBranch,
				git,
				listener,
				data,
				context);

		Repository repository = git.getRepository();
		RevWalk walk = new RevWalk(repository);
		
		Predicate<RevCommit> age_filter = new BranchAgeBuildFilter(git_scm.maxBranchAgeDays);
		Predicate<RevCommit> ancestry_filter = AncestryFilter.createNew(
				repository,
				walk,
				git_scm.getAncestors());

		Collection<Revision> filtered_revision_collection = Lists.newArrayList(); 
		for (Revision revision : original_revision_collection) {

			RevCommit revision_commit = walk.parseCommit(revision.getSha1());

			if (age_filter.apply(revision_commit)) {
				if (ancestry_filter.apply(revision_commit))
					filtered_revision_collection.add(revision);
				else
					LOGGER.log(Level.INFO, String.format(
							"The branch %s is young enough, but is not a descendant of any required ancestors.",
							revision.getBranches().iterator().next().getName()
					));

			} else {
				
				String info_message = String.format(
						"The branch %s is too old to be built.",
						revision.getBranches().iterator().next().getName()
				);

				LOGGER.log(Level.INFO, info_message);
			}
		}

		return filtered_revision_collection;
	}

	@Extension
	public static final class DescriptorImpl
	extends BuildChooserDescriptor {
		@Override
		public String getDisplayName() {
			return "Restrict by Age and/or Ancestry";
		}
	}
}
