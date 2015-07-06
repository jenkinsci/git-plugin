package hudson.plugins.git.util;

import hudson.plugins.git.AbstractGitRepository;
import hudson.plugins.git.Branch;
import hudson.plugins.git.Revision;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import static org.junit.Assert.*;
import org.junit.Test;

/**
 * @author Kohsuke Kawaguchi
 */
public class CommitTimeComparatorTest extends AbstractGitRepository {

    /**
     * Verifies that the sort is old to new.
     */
    @Test
    public void testSort() throws Exception {
        boolean first = true;
        // create repository with three commits
        for (int i=0; i<3; i++) {
            // in Git, the precision of the timestamp is 1 sec, so we need a large delay to produce commits with different timestamps.
            if (first)      first = false;
            else            Thread.sleep(1000);

            commitNewFile("file" + i);
            testGitClient.branch("branch" + i);
        }

        Map<Revision,Branch> branches = new HashMap<Revision,Branch>();
        List<Revision> revs = new ArrayList<Revision>();
        for (Branch b : testGitClient.getBranches()) {
            if (!b.getName().startsWith("branch"))  continue;
            Revision r = new Revision(b.getSHA1());
            revs.add(r);
            branches.put(r,b);
        }
        assertEquals(3,revs.size());

        for (int i=0; i<16; i++) {
            // shuffle, then sort.
            Collections.shuffle(revs);
            Collections.sort(revs, new CommitTimeComparator(testGitClient.getRepository()));

            // it should be always branch1, branch2, branch3
            for (int j=0; j<3; j++)
                assertEquals("branch"+j, branches.get(revs.get(j)).getName());
        }
    }
}
