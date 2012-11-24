package hudson.plugins.git.util;

import hudson.plugins.git.AbstractGitTestCase;
import hudson.plugins.git.Branch;
import hudson.plugins.git.Revision;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jgit.lib.Repository;

/**
 * @author Kohsuke Kawaguchi
 */
public class CommitTimeComparatorTest extends AbstractGitTestCase {
    /**
     * Verifies that the sort is old to new.
     */
    public void testSort() throws Exception {
        boolean first = true;
        // create repository with three commits
        for (int i=0; i<3; i++) {
            // in Git, the precision of the timestamp is 1 sec, so we need a large delay to produce commits with different timestamps.
            if (first)      first = false;
            else            Thread.sleep(1000);

            commit("file" + i, johnDoe, "Commit #" + i);
            git.branch("branch" + i);
        }

        Map<Revision,Branch> branches = new HashMap<Revision,Branch>();
        List<Revision> revs = new ArrayList<Revision>();
        for (Branch b : git.getBranches()) {
            if (!b.getName().startsWith("branch"))  continue;
            Revision r = new Revision(b.getSHA1());
            revs.add(r);
            branches.put(r,b);
        }
        assertEquals(3,revs.size());

        for (int i=0; i<16; i++) {
            // shuffle, then sort.
            Collections.shuffle(revs);
            Repository repository = git.getRepository();
            try {
                Collections.sort(revs, new CommitTimeComparator(repository));
            } finally {
                repository.close();
            }

            // it should be always branch1, branch2, branch3
            for (int j=0; j<3; j++)
                assertEquals("branch"+j, branches.get(revs.get(j)).getName());
        }
    }
}
