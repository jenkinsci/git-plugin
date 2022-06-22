/*
 * The MIT License
 *
 * Copyright 2017-2019 Mark Waite.
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
package hudson.plugins.git;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.eclipse.jgit.lib.ObjectId;
import org.junit.Before;
import org.junit.Test;
import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertFalse;

@Deprecated
public class SubmoduleConfigTest {

    private SubmoduleConfig config = new SubmoduleConfig();
    private SubmoduleConfig configWithBranchArray;
    private SubmoduleConfig configWithBranchList;

    private static final String SHA1 = "beaddeedfeedcededeafcadebadeabadedfeedad";
    private static final ObjectId ID = ObjectId.fromString(SHA1);
    private static final ObjectId ID2 = ObjectId.fromString(SHA1.replace('a', 'e'));

    private final String[] branchNames = {"master", "comma,chameleon", "develop"};

    private final Revision emptyRevision;
    private final Revision noBranchesRevision;
    private final Revision multipleBranchesRevision;

    private final Branch masterBranch;
    private final Branch masterAliasBranch;
    private final Branch developBranch;

    private final List<String> branchNameList = new ArrayList<>();

    public SubmoduleConfigTest() {
        List<Branch> emptyBranchList = new ArrayList<>();
        emptyRevision = new Revision(ID, emptyBranchList);
        masterBranch = new Branch("master", ID);
        masterAliasBranch = new Branch("masterAlias", ID);
        developBranch = new Branch("develop", ID2);
        List<Branch> branchList = new ArrayList<>();
        branchList.add(masterBranch);
        branchList.add(masterAliasBranch);
        branchList.add(developBranch);
        noBranchesRevision = new Revision(ID);
        multipleBranchesRevision = new Revision(ID, branchList);
        branchNameList.add("master");
        branchNameList.add("masterAlias");
        branchNameList.add("develop");
    }

    @Before
    public void setUp() {
        config = new SubmoduleConfig();
        configWithBranchArray = new SubmoduleConfig("submodule-branch-names-from-array", branchNames);
        configWithBranchList = new SubmoduleConfig("submodule-branch-names-from-list", branchNameList);
    }

    @Test
    public void testGetSubmoduleName() {
        assertThat(config.getSubmoduleName(), is(nullValue()));
    }

    @Test
    public void testSetSubmoduleName() {
        String name = "name-of-submodule";
        config.setSubmoduleName(name);
        assertThat(config.getSubmoduleName(), is(nullValue()));
        name = "another-submodule";
        config.setSubmoduleName(name);
        assertThat(config.getSubmoduleName(), is(nullValue()));
    }

    @Test
    public void testGetBranches() {
        assertThat(config.getBranches(), is(arrayWithSize(0)));
    }

    public void testGetBranchesFromArray() {
        assertThat(configWithBranchArray.getBranches(), is(branchNames));
    }

    public void testGetBranchesFromList() {
        assertThat(configWithBranchList.getBranches(), is(branchNames));
    }

    @Test
    public void testSetBranches() {
        config.setBranches(branchNames);
        assertThat(config.getBranches(), is(arrayWithSize(0)));
        String[] newBranchNames = Arrays.copyOf(branchNames, branchNames.length);
        newBranchNames[0] = "new-master";
        config.setBranches(newBranchNames);
        assertThat(config.getBranches(), is(arrayWithSize(0)));
    }

    @Test
    public void testGetBranchesStringEmpty() {
        assertThat(config.getBranchesString(), is(""));
    }

    @Test
    public void testGetBranchesString() {
        config.setBranches(branchNames);
        assertThat(config.getBranchesString(), is(""));
    }

    @Test
    public void testGetBranchesStringFromList() {
        assertThat(configWithBranchList.getBranchesString(), is(""));
        configWithBranchList.setBranches(branchNames);
        assertThat(configWithBranchList.getBranchesString(), is(""));
    }

    @Test
    public void testGetBranchesStringFromArray() {
        assertThat(configWithBranchArray.getBranchesString(), is(""));
        configWithBranchArray.setBranches(branchNames);
        assertThat(configWithBranchArray.getBranchesString(), is(""));
    }

    @Test
    public void testRevisionMatchesInterestNoBranches() {
        assertFalse(config.revisionMatchesInterest(noBranchesRevision));
        assertFalse(configWithBranchList.revisionMatchesInterest(noBranchesRevision));
        assertFalse(configWithBranchArray.revisionMatchesInterest(noBranchesRevision));
    }

    @Test
    public void testRevisionMatchesInterestEmptyBranchList() {
        assertFalse(config.revisionMatchesInterest(emptyRevision));
        assertFalse(configWithBranchList.revisionMatchesInterest(emptyRevision));
        assertFalse(configWithBranchArray.revisionMatchesInterest(emptyRevision));
    }

    @Test
    public void testRevisionMatchesInterestNPE() {
        assertFalse(config.revisionMatchesInterest(multipleBranchesRevision));
    }

    @Test
    public void testRevisionMatchesInterestMasterOnly() {
        String[] masterOnly = {"master"};
        config.setBranches(masterOnly);
        assertFalse(config.revisionMatchesInterest(multipleBranchesRevision));

        assertFalse(configWithBranchList.revisionMatchesInterest(multipleBranchesRevision));
        configWithBranchList.setBranches(masterOnly);
        assertFalse(configWithBranchList.revisionMatchesInterest(multipleBranchesRevision));

        assertFalse(configWithBranchArray.revisionMatchesInterest(multipleBranchesRevision));
        configWithBranchArray.setBranches(masterOnly);
        assertFalse(configWithBranchArray.revisionMatchesInterest(multipleBranchesRevision));
    }

    @Test
    public void testRevisionMatchesInterestAlias() {
        String[] aliasName = {"masterAlias"};
        config.setBranches(aliasName);
        assertFalse(config.revisionMatchesInterest(multipleBranchesRevision));

        assertFalse(configWithBranchList.revisionMatchesInterest(multipleBranchesRevision));
        configWithBranchList.setBranches(aliasName);
        assertFalse(configWithBranchList.revisionMatchesInterest(multipleBranchesRevision));

        assertFalse(configWithBranchArray.revisionMatchesInterest(multipleBranchesRevision));
        configWithBranchArray.setBranches(aliasName);
        assertFalse(configWithBranchArray.revisionMatchesInterest(multipleBranchesRevision));
    }

    @Test
    public void testRevisionMatchesInterest() {
        String[] masterDevelop = {"master", "develop"};
        config.setBranches(masterDevelop);
        assertFalse(config.revisionMatchesInterest(multipleBranchesRevision));
        assertFalse(configWithBranchList.revisionMatchesInterest(multipleBranchesRevision));
        assertFalse(configWithBranchArray.revisionMatchesInterest(multipleBranchesRevision));
    }

    @Test
    public void testBranchMatchesInterest() {
        String[] masterOnly = {"master"};
        config.setBranches(masterOnly);
        assertFalse(config.branchMatchesInterest(masterBranch));
        assertFalse(config.branchMatchesInterest(masterAliasBranch));
        assertFalse(config.branchMatchesInterest(developBranch));

        assertFalse(configWithBranchList.branchMatchesInterest(masterBranch));
        assertFalse(configWithBranchList.branchMatchesInterest(masterAliasBranch));
        assertFalse(configWithBranchList.branchMatchesInterest(developBranch));
        configWithBranchList.setBranches(masterOnly);
        assertFalse(configWithBranchList.branchMatchesInterest(masterBranch));
        assertFalse(configWithBranchList.branchMatchesInterest(masterAliasBranch));
        assertFalse(configWithBranchList.branchMatchesInterest(developBranch));
    }

    @Test
    public void testBranchMatchesInterestWithRegex() {
        String[] masterOnlyRegex = {"m.st.r"};
        config.setBranches(masterOnlyRegex);
        assertFalse(config.branchMatchesInterest(masterBranch));
        assertFalse(config.branchMatchesInterest(masterAliasBranch));
        assertFalse(config.branchMatchesInterest(developBranch));
    }

    @Test
    public void testBranchMatchesInterestMasterDevelop() {
        String[] masterDevelop = {"master", "develop"};
        config.setBranches(masterDevelop);
        assertFalse(config.branchMatchesInterest(masterBranch));
        assertFalse(config.branchMatchesInterest(masterAliasBranch));
        assertFalse(config.branchMatchesInterest(developBranch));
    }

    @Test
    public void testBranchMatchesInterestCommaInBranchName() {
        config.setBranches(branchNames);
        assertFalse(config.branchMatchesInterest(masterBranch));
        assertFalse(config.branchMatchesInterest(masterAliasBranch));
        assertFalse(config.branchMatchesInterest(developBranch));
    }
}
