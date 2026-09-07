package hudson.plugins.git.util;

import hudson.model.Api;
import hudson.model.Result;
import hudson.plugins.git.Branch;
import hudson.plugins.git.Revision;
import hudson.plugins.git.UserRemoteConfig;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Random;

import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;
import org.junit.jupiter.api.BeforeEach;

import org.eclipse.jgit.lib.ObjectId;

import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;

/**
 * @author Mark Waite
 */
class BuildDataTest {

    private BuildData data;
    private final ObjectId sha1 = ObjectId.fromString("929e92e3adaff2e6e1d752a8168c1598890fe84c");
    private final String remoteUrl = "https://github.com/jenkinsci/git-plugin";

    @BeforeEach
    void beforeEach() throws Exception {
        data = new BuildData();
    }

    @Test
    void testGetDisplayName() throws Exception {
        assertThat(data.getDisplayName(), is("Git Build Data"));
    }

    @Test
    void testGetDisplayNameEmptyString() throws Exception {
        String scmName = "";
        BuildData dataWithSCM = new BuildData(scmName);
        assertThat(dataWithSCM.getDisplayName(), is("Git Build Data"));
    }

    @Test
    void testGetDisplayNameNullSCMName() throws Exception {
        BuildData dataWithNullSCM = new BuildData(null);
        assertThat(dataWithNullSCM.getDisplayName(), is("Git Build Data"));
    }

    @Test
    void testGetDisplayNameWithSCM() throws Exception {
        final String scmName = "testSCM";
        final BuildData dataWithSCM = new BuildData(scmName);
        assertThat("Git Build Data:" + scmName, is(dataWithSCM.getDisplayName()));
    }

    @Test
    void testGetIconFileName() {
        assertEquals("symbol-git-icon plugin-git", data.getIconFileName());
    }

    @Test
    void testGetUrlName() {
        assertThat(data.getUrlName(), is("git"));
    }

    @Test
    void testGetUrlNameMultipleEntries() {
        Random random = new Random();
        int randomIndex = random.nextInt(1234) + 1;
        data.setIndex(randomIndex);
        assertThat(data.getUrlName(), is("git-" + randomIndex));
    }

    @Test
    void testHasBeenBuilt() {
        assertFalse(data.hasBeenBuilt(sha1));
    }

    @Test
    void testGetLastBuild() {
        assertNull(data.getLastBuild(sha1));
    }

    @Test
    void testGetLastBuildSingleBranch() {
        String branchName = "origin/master";
        Collection<Branch> branches = new ArrayList<>();
        Branch branch = new Branch(branchName, sha1);
        branches.add(branch);
        Revision revision = new Revision(sha1, branches);
        Build build = new Build(revision, 13, Result.FAILURE);
        data.saveBuild(build);
        assertThat(data.getLastBuild(sha1), is(build));

        ObjectId newSha1 = ObjectId.fromString("31a987bc9fc0b08d1ad297cac8584d5871a21581");
        Revision newRevision = new Revision(newSha1, branches);
        Revision marked = revision;
        Build newBuild = new Build(marked, newRevision, 17, Result.SUCCESS);
        data.saveBuild(newBuild);
        assertThat(data.getLastBuild(newSha1), is(newBuild));

        assertThat(data.getLastBuild(sha1), is(newBuild));

        ObjectId unbuiltSha1 = ObjectId.fromString("da99ce34121292bc887e91fc0a9d60cf8a701662");
        assertThat(data.getLastBuild(unbuiltSha1), is(nullValue()));
    }

    @Test
    void testGetLastBuildMultipleBranches() {

        String branchName = "origin/master";
        Collection<Branch> branches = new ArrayList<>();
        Branch branch = new Branch(branchName, sha1);
        branches.add(branch);
        Revision revision = new Revision(sha1, branches);
        Build build = new Build(revision, 13, Result.FAILURE);
        data.saveBuild(build);
        assertThat(data.getLastBuild(sha1), is(build));

        ObjectId newSha1 = ObjectId.fromString("31a987bc9fc0b08d1ad297cac8584d5871a21581");
        Branch newBranch = new Branch("origin/stable-3.x", newSha1);
        branches.add(newBranch);
        Revision newRevision = new Revision(newSha1, branches);
        Revision marked = revision;
        Build newBuild = new Build(marked, newRevision, 17, Result.SUCCESS);
        data.saveBuild(newBuild);
        assertThat(data.getLastBuild(newSha1), is(newBuild));

        assertThat(data.getLastBuild(sha1), is(newBuild));

        ObjectId unbuiltSha1 = ObjectId.fromString("da99ce34121292bc887e91fc0a9d60cf8a701662");
        assertThat(data.getLastBuild(unbuiltSha1), is(nullValue()));
    }

    @Test
    void testGetLastBuildWithNullSha1() {
        assertThat(data.getLastBuild(null), is(nullValue()));

        String branchName = "origin/master";
        Collection<Branch> branches = new ArrayList<>();
        Branch branch = new Branch(branchName, sha1);
        branches.add(branch);
        Revision revision = new Revision(null, branches); // A revision with a null sha1 (unexpected)
        Build build = new Build(revision, 29, Result.FAILURE);
        data.saveBuild(build);
        assertThat(data.getLastBuild(sha1), is(nullValue()));

        ObjectId unbuiltSha1 = ObjectId.fromString("da99ce34121292bc887e91fc0a9d60cf8a701662");
        assertThat(data.getLastBuild(unbuiltSha1), is(nullValue()));
    }

    @Test
    void testSaveBuild() {
        Revision revision = new Revision(sha1);
        Build build = new Build(revision, 1, Result.SUCCESS);
        data.saveBuild(build);
        assertThat(data.getLastBuild(sha1), is(build));

        Revision nullRevision = new Revision(null);
        Build newBuild = new Build(revision, nullRevision, 2, Result.SUCCESS);
        data.saveBuild(newBuild);
        assertThat(data.getLastBuild(sha1), is(newBuild));

        Build anotherBuild = new Build(nullRevision, revision, 3, Result.SUCCESS);
        data.saveBuild(anotherBuild);
        assertThat(data.getLastBuild(sha1), is(anotherBuild));
    }

    @Test
    void testGetLastBuildOfBranch() {
        String branchName = "origin/master";
        assertNull(data.getLastBuildOfBranch(branchName));

        Collection<Branch> branches = new ArrayList<>();
        Branch branch = new Branch(branchName, sha1);
        branches.add(branch);
        Revision revision = new Revision(sha1, branches);
        Build build = new Build(revision, 13, Result.FAILURE);
        data.saveBuild(build);
        assertThat(data.getLastBuildOfBranch(branchName), is(build));
    }

    @Test
    void testGetLastBuiltRevision() {
        Revision revision = new Revision(sha1);
        Build build = new Build(revision, 1, Result.SUCCESS);
        data.saveBuild(build);
        assertThat(data.getLastBuiltRevision(), is(revision));
    }

    @Test
    void testGetBuildsByBranchName() {
        assertTrue(data.getBuildsByBranchName().isEmpty());
    }

    @Test
    void testGetScmName() {
        assertThat(data.getScmName(), is(""));
    }

    @Test
    void testSetScmName() {
        final String scmName = "Some SCM name";
        data.setScmName(scmName);
        assertThat(data.getScmName(), is(scmName));
    }

    @Test
    void testAddRemoteUrl() {
        data.addRemoteUrl(remoteUrl);
        assertEquals(1, data.getRemoteUrls().size());

        String remoteUrl2 = "https://github.com/jenkinsci/git-plugin.git/";
        data.addRemoteUrl(remoteUrl2);
        assertFalse(data.getRemoteUrls().isEmpty());
        assertTrue(data.getRemoteUrls().contains(remoteUrl2), "Second URL not found in remote URLs");
        assertEquals(2, data.getRemoteUrls().size());
    }

    @Test
    void testHasBeenReferenced() {
        assertFalse(data.hasBeenReferenced(remoteUrl));
        data.addRemoteUrl(remoteUrl);
        assertTrue(data.hasBeenReferenced(remoteUrl));
        assertFalse(data.hasBeenReferenced(remoteUrl + "/"));
    }

    @Test
    void testGetApi() {
        Api api = data.getApi();
        Api apiClone = data.clone().getApi();
        assertEquals(api, api);
        assertEquals(api.getSearchUrl(), apiClone.getSearchUrl());
    }

    @Test
    void testToString() {
        assertEquals(data.toString(), data.clone().toString());
    }

    @Test
    void testToStringEmptyBuildData() {
        BuildData empty = new BuildData();
        assertThat(empty.toString(), endsWith("[scmName=<null>,remoteUrls=[],buildsByBranchName={},lastBuild=null]"));
    }

    @Test
    void testToStringNullSCMBuildData() {
        BuildData nullSCM = new BuildData(null);
        assertThat(nullSCM.toString(), endsWith("[scmName=<null>,remoteUrls=[],buildsByBranchName={},lastBuild=null]"));
    }

    @Test
    void testToStringNonNullSCMBuildData() {
        BuildData nonNullSCM = new BuildData("gitless");
        assertThat(nonNullSCM.toString(), endsWith("[scmName=gitless,remoteUrls=[],buildsByBranchName={},lastBuild=null]"));
    }

    @Test
    void testEquals() {
        // Null object not equal non-null
        BuildData nullData = null;
        assertNotEquals(nullData, data, "Null object not equal non-null");

        // Object should equal itself
        assertEquals(data, data, "Object not equal itself");
        assertEquals(data, data, "Object not equal itself");
        assertEquals(data.hashCode(), data.hashCode(), "Object hashCode not equal itself");

        // Cloned object equals original object
        BuildData data1 = data.clone();
        assertEquals(data1, data, "Cloned objects not equal");
        assertEquals(data1, data, "Cloned objects not equal");
        assertEquals(data, data1, "Cloned objects not equal");
        assertEquals(data.hashCode(), data1.hashCode(), "Cloned object hashCodes not equal");

        // Saved build makes object unequal
        Revision revision1 = new Revision(sha1);
        Build build1 = new Build(revision1, 1, Result.SUCCESS);
        data1.saveBuild(build1);
        assertNotEquals(data, data1, "Distinct objects shouldn't be equal");
        assertNotEquals(data1, data, "Distinct objects shouldn't be equal");

        // Same saved build makes objects equal
        BuildData data2 = data.clone();
        data2.saveBuild(build1);
        assertEquals(data2, data1, "Objects with same saved build not equal");
        assertEquals(data1, data2, "Objects with same saved build not equal");
        assertEquals(data2.hashCode(), data1.hashCode(), "Objects with same saved build not equal hashCodes");

        // Add remote URL makes objects unequal
        final String remoteUrl2 = "git@github.com:jenkinsci/git-plugin.git";
        data1.addRemoteUrl(remoteUrl2);
        assertNotEquals(data, data1, "Distinct objects shouldn't be equal");
        assertNotEquals(data1, data, "Distinct objects shouldn't be equal");

        // Add same remote URL makes objects equal
        data2.addRemoteUrl(remoteUrl2);
        assertEquals(data2, data1, "Objects with same remote URL not equal");
        assertEquals(data1, data2, "Objects with same remote URL not equal");
        assertEquals(data2.hashCode(), data1.hashCode(), "Objects with same remote URL not equal hashCodes");

        // Another saved build still keeps objects equal
        String branchName = "origin/master";
        Collection<Branch> branches = new ArrayList<>();
        Branch branch = new Branch(branchName, sha1);
        branches.add(branch);
        Revision revision2 = new Revision(sha1, branches);
        Build build2 = new Build(revision2, 1, Result.FAILURE);
        assertEquals(build1, build2); // Surprising, since build1 result is SUCCESS, build2 result is FAILURE
        data1.saveBuild(build2);
        data2.saveBuild(build2);
        assertEquals(data1, data2);
        assertEquals(data1.hashCode(), data2.hashCode());

        // Saving different build results still equal BuildData,
        // because the different build results are equal
        data1.saveBuild(build1);
        data2.saveBuild(build2);
        assertEquals(data1, data2);
        assertEquals(data1.hashCode(), data2.hashCode());

        // Set SCM name doesn't change equality or hashCode
        data1.setScmName("scm 1");
        assertEquals(data1, data2);
        assertEquals(data1.hashCode(), data2.hashCode());
        data2.setScmName("scm 2");
        assertEquals(data1, data2);
        assertEquals(data1.hashCode(), data2.hashCode());

        BuildData emptyData = new BuildData();
        emptyData.remoteUrls = null;
        assertNotEquals(data, emptyData, "Non-empty object equal empty");
        assertNotEquals(emptyData, data, "Empty object similar to non-empty");
    }

    @Test
    void equalsContract() {
        EqualsVerifier.forClass(BuildData.class)
                .usingGetClass()
                .suppress(Warning.NONFINAL_FIELDS)
                .withIgnoredFields("index", "scmName")
                .verify();
    }

    @Test
    void testSetIndex() {
        data.setIndex(null);
        assertNull(data.getIndex());
        data.setIndex(-1);
        assertNull(data.getIndex());
        data.setIndex(0);
        assertNull(data.getIndex());
        data.setIndex(13);
        assertEquals(13, data.getIndex().intValue());
        data.setIndex(-1);
        assertNull(data.getIndex());
    }

    @Test
    void testSimilarToHttpsRemoteURL() {
        final String SIMPLE_URL = "https://github.com/jenkinsci/git-plugin";
        BuildData simple = new BuildData("git-" + SIMPLE_URL);
        simple.addRemoteUrl(SIMPLE_URL);
        permuteBaseURL(SIMPLE_URL, simple);
    }

    @Test
    void testSimilarToScpRemoteURL() {
        final String SIMPLE_URL = "git@github.com:jenkinsci/git-plugin";
        BuildData simple = new BuildData("git-" + SIMPLE_URL);
        simple.addRemoteUrl(SIMPLE_URL);
        permuteBaseURL(SIMPLE_URL, simple);
    }

    @Test
    void testSimilarToSshRemoteURL() {
        final String SIMPLE_URL = "ssh://git@github.com/jenkinsci/git-plugin";
        BuildData simple = new BuildData("git-" + SIMPLE_URL);
        simple.addRemoteUrl(SIMPLE_URL);
        permuteBaseURL(SIMPLE_URL, simple);
    }

    private void permuteBaseURL(String simpleURL, BuildData simple) {
        final String TRAILING_SLASH_URL = simpleURL + "/";
        BuildData trailingSlash = new BuildData("git-" + TRAILING_SLASH_URL);
        trailingSlash.addRemoteUrl(TRAILING_SLASH_URL);
        assertTrue(trailingSlash.similarTo(simple),
                "Trailing slash not similar to simple URL " + TRAILING_SLASH_URL);

        final String TRAILING_SLASHES_URL = TRAILING_SLASH_URL + "//";
        BuildData trailingSlashes = new BuildData("git-" + TRAILING_SLASHES_URL);
        trailingSlashes.addRemoteUrl(TRAILING_SLASHES_URL);
        assertTrue(trailingSlashes.similarTo(simple),
                "Trailing slashes not similar to simple URL " + TRAILING_SLASHES_URL);

        final String DOT_GIT_URL = simpleURL + ".git";
        BuildData dotGit = new BuildData("git-" + DOT_GIT_URL);
        dotGit.addRemoteUrl(DOT_GIT_URL);
        assertTrue(dotGit.similarTo(simple),
                "Dot git not similar to simple URL " + DOT_GIT_URL);

        final String DOT_GIT_TRAILING_SLASH_URL = DOT_GIT_URL + "/";
        BuildData dotGitTrailingSlash = new BuildData("git-" + DOT_GIT_TRAILING_SLASH_URL);
        dotGitTrailingSlash.addRemoteUrl(DOT_GIT_TRAILING_SLASH_URL);
        assertTrue(dotGitTrailingSlash.similarTo(dotGit),
                "Dot git trailing slash not similar to dot git URL " + DOT_GIT_TRAILING_SLASH_URL);

        final String DOT_GIT_TRAILING_SLASHES_URL = DOT_GIT_TRAILING_SLASH_URL + "///";
        BuildData dotGitTrailingSlashes = new BuildData("git-" + DOT_GIT_TRAILING_SLASHES_URL);
        dotGitTrailingSlashes.addRemoteUrl(DOT_GIT_TRAILING_SLASHES_URL);
        assertTrue(dotGitTrailingSlashes.similarTo(dotGit),
                "Dot git trailing slashes not similar to dot git URL " + DOT_GIT_TRAILING_SLASHES_URL);
    }

    @Test
    @Issue("JENKINS-43630")
    void testSimilarToContainsNullURL() {
        final String SIMPLE_URL = "ssh://git@github.com/jenkinsci/git-plugin";
        BuildData simple = new BuildData("git-" + SIMPLE_URL);
        simple.addRemoteUrl(SIMPLE_URL);
        simple.addRemoteUrl(null);
        simple.addRemoteUrl(SIMPLE_URL);

        BuildData simple2 = simple.clone();
        assertTrue(simple.similarTo(simple2));

        BuildData simple3 = new BuildData("git-" + SIMPLE_URL);
        simple3.addRemoteUrl(SIMPLE_URL);
        simple3.addRemoteUrl(null);
        simple3.addRemoteUrl(SIMPLE_URL);
        assertTrue(simple.similarTo(simple3));
    }

    @Test
    void testGetIndex() {
        assertNull(data.getIndex());
    }

    @Test
    void testGetRemoteUrls() {
        assertTrue(data.getRemoteUrls().isEmpty());
    }

    @Test
    void testClone() {
        // Tested in testSimilarTo and testEquals
    }

    @Test
    void testSimilarTo() {
        data.addRemoteUrl(remoteUrl);

        // Null object not similar to non-null
        BuildData dataNull = null;
        assertFalse(data.similarTo(dataNull), "Null object similar to non-null");

        BuildData emptyData = new BuildData();
        assertFalse(data.similarTo(emptyData), "Non-empty object similar to empty");
        assertFalse(emptyData.similarTo(data), "Empty object similar to non-empty");
        emptyData.remoteUrls = null;
        assertFalse(data.similarTo(emptyData), "Non-empty object similar to empty");
        assertFalse(emptyData.similarTo(data), "Empty object similar to non-empty");

        // Object should be similar to itself
        assertTrue(data.similarTo(data), "Object not similar to itself");

        // Object should not be similar to constructed variants
        Collection<UserRemoteConfig> emptyList = new ArrayList<>();
        assertFalse(data.similarTo(new BuildData("abc")), "Object similar to data with SCM name");
        assertFalse(data.similarTo(new BuildData("abc", emptyList)), "Object similar to data with SCM name & empty");

        BuildData dataSCM = new BuildData("scm");
        assertFalse(dataSCM.similarTo(data), "Object similar to data with SCM name");
        assertTrue(dataSCM.similarTo(new BuildData("abc")), "Object with SCM name not similar to data with SCM name");
        assertTrue(dataSCM.similarTo(new BuildData("abc", emptyList)), "Object with SCM name not similar to data with SCM name & empty");

        // Cloned object equals original object
        BuildData dataClone = data.clone();
        assertTrue(dataClone.similarTo(data), "Clone not similar to origin");
        assertTrue(data.similarTo(dataClone), "Origin not similar to clone");

        // Saved build makes objects dissimilar
        Revision revision1 = new Revision(sha1);
        Build build1 = new Build(revision1, 1, Result.SUCCESS);
        dataClone.saveBuild(build1);
        assertFalse(data.similarTo(dataClone), "Unmodified origin similar to modified clone");
        assertFalse(dataClone.similarTo(data), "Modified clone similar to unmodified origin");
        assertTrue(dataClone.similarTo(dataClone), "Modified clone not similar to itself");

        // Same saved build makes objects similar
        BuildData data2 = data.clone();
        data2.saveBuild(build1);
        assertFalse(data.similarTo(data2), "Unmodified origin similar to modified clone");
        assertTrue(data2.similarTo(dataClone), "Objects with same saved build not similar (1)");
        assertTrue(dataClone.similarTo(data2), "Objects with same saved build not similar (2)");

        // Add remote URL makes objects dissimilar
        final String remoteUrl = "https://github.com/jenkinsci/git-client-plugin.git";
        dataClone.addRemoteUrl(remoteUrl);
        assertFalse(data.similarTo(dataClone), "Distinct objects shouldn't be similar (1)");
        assertFalse(dataClone.similarTo(data), "Distinct objects shouldn't be similar (2)");

        // Add same remote URL makes objects similar
        data2.addRemoteUrl(remoteUrl);
        assertTrue(data2.similarTo(dataClone), "Objects with same remote URL dissimilar");
        assertTrue(dataClone.similarTo(data2), "Objects with same remote URL dissimilar");

        // Add different remote URL objects similar
        final String trailingSlash = "git-client-plugin.git/"; // Unlikely as remote URL
        dataClone.addRemoteUrl(trailingSlash);
        assertFalse(data.similarTo(dataClone), "Distinct objects shouldn't be similar");
        assertFalse(dataClone.similarTo(data), "Distinct objects shouldn't be similar");

        data2.addRemoteUrl(trailingSlash);
        assertTrue(data2.similarTo(dataClone), "Objects with same remote URL dissimilar");
        assertTrue(dataClone.similarTo(data2), "Objects with same remote URL dissimilar");

        // Add different remote URL objects
        final String noSlash = "git-client-plugin"; // Unlikely as remote URL
        dataClone.addRemoteUrl(noSlash);
        assertFalse(data.similarTo(dataClone), "Distinct objects shouldn't be similar");
        assertFalse(dataClone.similarTo(data), "Distinct objects shouldn't be similar");

        data2.addRemoteUrl(noSlash);
        assertTrue(data2.similarTo(dataClone), "Objects with same remote URL dissimilar");
        assertTrue(dataClone.similarTo(data2), "Objects with same remote URL dissimilar");

        // Another saved build still keeps objects similar
        String branchName = "origin/master";
        Collection<Branch> branches = new ArrayList<>();
        Branch branch = new Branch(branchName, sha1);
        branches.add(branch);
        Revision revision2 = new Revision(sha1, branches);
        Build build2 = new Build(revision2, 1, Result.FAILURE);
        dataClone.saveBuild(build2);
        assertTrue(dataClone.similarTo(data2), "Another saved build, still similar (1)");
        assertTrue(data2.similarTo(dataClone), "Another saved build, still similar (2)");
        data2.saveBuild(build2);
        assertTrue(dataClone.similarTo(data2), "Another saved build, still similar (3)");
        assertTrue(data2.similarTo(dataClone), "Another saved build, still similar (4)");

        // Saving different build results still similar BuildData
        dataClone.saveBuild(build1);
        assertTrue(dataClone.similarTo(data2), "Saved build with different results, similar (5)");
        assertTrue(data2.similarTo(dataClone), "Saved build with different results, similar (6)");
        data2.saveBuild(build2);
        assertTrue(dataClone.similarTo(data2), "Saved build with different results, similar (7)");
        assertTrue(data2.similarTo(dataClone), "Saved build with different results, similar (8)");

        // Set SCM name doesn't change similarity
        dataClone.setScmName("scm 1");
        assertTrue(dataClone.similarTo(data2));
        data2.setScmName("scm 2");
        assertTrue(dataClone.similarTo(data2));
    }

    @Test
    void testHashCode() {
        // Tested in testEquals
    }

    @Test
    void testHashCodeEmptyData() {
        BuildData emptyData = new BuildData();
        assertEquals(emptyData.hashCode(), emptyData.hashCode());
        emptyData.remoteUrls = null;
        assertEquals(emptyData.hashCode(), emptyData.hashCode());
    }





}
