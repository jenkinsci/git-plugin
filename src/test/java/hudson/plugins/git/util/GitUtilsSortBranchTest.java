/*
 * The MIT License
 *
 * Copyright 2017 Mark Waite.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package hudson.plugins.git.util;

import hudson.EnvVars;
import hudson.model.TaskListener;
import hudson.plugins.git.Branch;
import hudson.plugins.git.BranchSpec;
import hudson.plugins.git.Revision;
import hudson.util.StreamTaskListener;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Random;
import jenkins.plugins.git.GitSampleRepoRule;
import org.eclipse.jgit.lib.ObjectId;
import static org.hamcrest.Matchers.is;
import org.jenkinsci.plugins.gitclient.Git;
import org.jenkinsci.plugins.gitclient.GitClient;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class GitUtilsSortBranchTest {

    @Rule
    public GitSampleRepoRule sampleRepo = new GitSampleRepoRule();

    private final Random random = new Random();

    private final String[] branchNames = {
        "master",
        "sally-2",
        "baker-1",
        "able-4"
    };
    private final String otherBranchName = "other-branch";

    private ObjectId headId = null;
    private ObjectId priorHeadId = null;

    private Revision headRevision = null;
    private Revision priorRevision = null;

    private final String priorTagName1 = "prior-tag-1";
    private final String priorTagName2 = "prior-tag-2-annotated";
    private final String headTagName1 = "head-tag-1";
    private final String headTagName2 = "head-tag-2-annotated";

    private List<BranchSpec> branchSpecList = null;
    private List<BranchSpec> priorBranchSpecList = null;
    private List<Branch> branchList = null;
    private final EnvVars env;
    private final TaskListener listener = StreamTaskListener.NULL;

    private GitUtils gitUtils;
    private GitClient gitClient;

    public GitUtilsSortBranchTest() {
        this.env = new EnvVars();
    }

    @Before
    public void createSampleRepo() throws Exception {
        String fileName = "README";
        sampleRepo.init();
        sampleRepo.git("tag", priorTagName1);
        sampleRepo.git("tag", "-a", priorTagName2, "-m", "Annotated tag " + priorTagName2);
        priorHeadId = ObjectId.fromString(sampleRepo.head());

        sampleRepo.git("checkout", "-b", otherBranchName);
        branchList = new ArrayList<>();
        branchList.add(new Branch(otherBranchName, priorHeadId));
        priorRevision = new Revision(priorHeadId, branchList);
        priorBranchSpecList = new ArrayList<>();
        priorBranchSpecList.add(new BranchSpec(otherBranchName));

        sampleRepo.write(fileName, "This is the README file " + random.nextInt());
        sampleRepo.git("add", fileName);
        sampleRepo.git("commit", "-m", "Adding " + fileName, fileName);
        sampleRepo.git("tag", headTagName1);
        sampleRepo.git("tag", "-a", headTagName2, "-m", "Annotated tag " + headTagName2);
        headId = ObjectId.fromString(sampleRepo.head());
        branchSpecList = new ArrayList<>();
        branchList = new ArrayList<>();
        branchSpecList.add(new BranchSpec("master"));
        branchList.add(new Branch("master", headId));
        for (String branchName : branchNames) {
            if (!branchName.equals("master")) {
                sampleRepo.git("checkout", "-b", branchName);
                branchSpecList.add(new BranchSpec(branchName));
                branchList.add(new Branch(branchName, headId));
            }
        }
        headRevision = new Revision(headId, branchList);
        File gitDir = sampleRepo.getRoot();
        this.gitClient = Git.with(listener, env).in(gitDir).using("git").getClient();
        this.gitUtils = new GitUtils(listener, gitClient);
    }

    @Test
    public void testSortBranchesForRevision_Revision_List() {
        Revision result = gitUtils.sortBranchesForRevision(headRevision, branchSpecList);
        assertEquals(headRevision, result);
    }

    @Test
    public void testSortBranchesForRevision_Revision_List_Prior() {
        Revision result = gitUtils.sortBranchesForRevision(priorRevision, priorBranchSpecList);
        assertEquals(priorRevision, result);
    }

    @Test
    public void testSortBranchesForRevision_Revision_List_Mix_1() {
        Revision result = gitUtils.sortBranchesForRevision(headRevision, priorBranchSpecList);
        assertEquals(headRevision, result);
    }

    @Test
    public void testSortBranchesForRevision_Revision_List_Mix_2() {
        Revision result = gitUtils.sortBranchesForRevision(priorRevision, branchSpecList);
        assertEquals(priorRevision, result);
    }

    @Test
    public void testSortBranchesForRevision_Revision_List_Prior_3_args() {
        Revision result = gitUtils.sortBranchesForRevision(headRevision, branchSpecList, env);
        assertEquals(headRevision, result);
    }

    @Test
    public void testFilterTipBranches() throws Exception {
        Collection<Revision> multiRevisionList = new ArrayList<>();
        multiRevisionList.add(priorRevision);
        multiRevisionList.add(headRevision);
        Collection<Revision> filteredRevisions = new ArrayList<>();
        filteredRevisions.add(headRevision);
        List<Revision> result = gitUtils.filterTipBranches(multiRevisionList);
        assertThat(result, is(filteredRevisions));
    }
}
