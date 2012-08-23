package hudson.plugins.git;

import java.util.Collections;
import java.util.List;

public class GitRefParameterTest extends AbstractGitTestCase {

    public void testDisplayInReverseOrder() throws Exception {
        // Create a Git repository
        final String commitFile = "Huey";
        commit(commitFile, johnDoe, "Imperceptible changeset.");

        // Lengthen the list of refs until we can meaningfully test ordering
        testRepo.tag("earlier");
        testRepo.tag("later");

        // Obtain the list in its "natural" ordering
        GitRefParameterDefinition pn = new GitRefParameterDefinition("ignore", testRepo.gitDir.getAbsolutePath(), false);
        List<String> natural = pn.getRefs();

        // Obtain the list in its reverse ordering
        GitRefParameterDefinition pr = new GitRefParameterDefinition("ignore", testRepo.gitDir.getAbsolutePath(), true);
        List<String> reversed = pr.getRefs();

        // Assert that the orderings are the reverse of each other
        Collections.reverse(reversed);
        assertEquals(natural, reversed);
    }

}
