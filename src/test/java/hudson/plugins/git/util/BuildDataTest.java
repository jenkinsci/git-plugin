package hudson.plugins.git.util;

import hudson.model.Api;
import hudson.model.Result;
import hudson.plugins.git.Branch;
import hudson.plugins.git.Revision;
import hudson.plugins.git.UserRemoteConfig;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Random;

import org.eclipse.jgit.lib.ObjectId;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;

/**
 * @author Mark Waite
 */
public class BuildDataTest {

    private BuildData data;
    private final ObjectId sha1 = ObjectId.fromString("929e92e3adaff2e6e1d752a8168c1598890fe84c");
    private final String remoteUrl = "https://github.com/jenkinsci/git-plugin";

    @Before
    public void setUp() throws Exception {
        data = new BuildData();
    }

    @Test
    public void testGetDisplayName() throws Exception {
        assertThat(data.getDisplayName(), is("Git Build Data"));
    }

    @Test
    public void testGetDisplayNameEmptyString() throws Exception {
        String scmName = "";
        BuildData dataWithSCM = new BuildData(scmName);
        assertThat(dataWithSCM.getDisplayName(), is("Git Build Data"));
    }

    @Test
    public void testGetDisplayNameNullSCMName() throws Exception {
        BuildData dataWithNullSCM = new BuildData((String)null);
        assertThat(dataWithNullSCM.getDisplayName(), is("Git Build Data"));
    }

    @Test
    public void testGetDisplayNameWithSCM() throws Exception {
        final String scmName = "testSCM";
        final BuildData dataWithSCM = new BuildData(scmName);
        assertThat("Git Build Data:" + scmName, is(dataWithSCM.getDisplayName()));
    }

    @Test
    public void testGetIconFileName() {
        assertThat(data.getIconFileName(), endsWith("/plugin/git/icons/git-32x32.png"));
    }

    @Test
    public void testGetUrlName() {
        assertThat(data.getUrlName(), is("git"));
    }

    @Test
    public void testGetUrlNameMultipleEntries() {
        Random random = new Random();
        int randomIndex = random.nextInt(1234) + 1;
        data.setIndex(randomIndex);
        assertThat(data.getUrlName(), is("git-" + randomIndex));
    }

    @Test
    public void testHasBeenBuilt() {
        assertFalse(data.hasBeenBuilt(sha1));
    }

    @Test
    public void testGetLastBuild() {
        assertEquals(null, data.getLastBuild(sha1));
    }

    @Test
    public void testSaveBuild() {
        Revision revision = new Revision(sha1);
        Build build = new Build(revision, 1, Result.SUCCESS);
        data.saveBuild(build);
        assertThat(data.getLastBuild(sha1), is(build));
    }

    @Test
    public void testGetLastBuildOfBranch() {
        String branchName = "origin/master";
        assertEquals(null, data.getLastBuildOfBranch(branchName));

        Collection<Branch> branches = new ArrayList<>();
        Branch branch = new Branch(branchName, sha1);
        branches.add(branch);
        Revision revision = new Revision(sha1, branches);
        Build build = new Build(revision, 13, Result.FAILURE);
        data.saveBuild(build);
        assertThat(data.getLastBuildOfBranch(branchName), is(build));
    }

    @Test
    public void testGetLastBuiltRevision() {
        Revision revision = new Revision(sha1);
        Build build = new Build(revision, 1, Result.SUCCESS);
        data.saveBuild(build);
        assertThat(data.getLastBuiltRevision(), is(revision));
    }

    @Test
    public void testGetBuildsByBranchName() {
        assertTrue(data.getBuildsByBranchName().isEmpty());
    }

    @Test
    public void testGetScmName() {
        assertThat(data.getScmName(), is(""));
    }

    @Test
    public void testSetScmName() {
        final String scmName = "Some SCM name";
        data.setScmName(scmName);
        assertThat(data.getScmName(), is(scmName));
    }

    @Test
    public void testAddRemoteUrl() {
        data.addRemoteUrl(remoteUrl);
        assertEquals(1, data.getRemoteUrls().size());

        String remoteUrl2 = "https://github.com/jenkinsci/git-plugin.git/";
        data.addRemoteUrl(remoteUrl2);
        assertFalse(data.getRemoteUrls().isEmpty());
        assertTrue("Second URL not found in remote URLs", data.getRemoteUrls().contains(remoteUrl2));
        assertEquals(2, data.getRemoteUrls().size());
    }

    @Test
    public void testHasBeenReferenced() {
        assertFalse(data.hasBeenReferenced(remoteUrl));
        data.addRemoteUrl(remoteUrl);
        assertTrue(data.hasBeenReferenced(remoteUrl));
        assertFalse(data.hasBeenReferenced(remoteUrl + "/"));
    }

    @Test
    public void testGetApi() {
        Api api = data.getApi();
        Api apiClone = data.clone().getApi();
        assertEquals(api, api);
        assertEquals(api.getSearchUrl(), apiClone.getSearchUrl());
    }

    @Test
    public void testToString() {
        assertEquals(data.toString(), data.clone().toString());
    }

    @Test
    public void testToStringEmptyBuildData() {
        BuildData empty = new BuildData();
        assertThat(empty.toString(), endsWith("[scmName=<null>,remoteUrls=[],buildsByBranchName={},lastBuild=null]"));
    }

    @Test
    public void testToStringNullSCMBuildData() {
        BuildData nullSCM = new BuildData((String)null);
        assertThat(nullSCM.toString(), endsWith("[scmName=<null>,remoteUrls=[],buildsByBranchName={},lastBuild=null]"));
    }

    @Test
    public void testToStringNonNullSCMBuildData() {
        BuildData nonNullSCM = new BuildData("gitless");
        assertThat(nonNullSCM.toString(), endsWith("[scmName=gitless,remoteUrls=[],buildsByBranchName={},lastBuild=null]"));
    }

    @Test
    public void testEquals() {
        // Null object not equal non-null
        BuildData nullData = null;
        assertFalse("Null object not equal non-null", data.equals(nullData));

        // Object should equal itself
        assertEquals("Object not equal itself", data, data);
        assertTrue("Object not equal itself", data.equals(data));
        assertEquals("Object hashCode not equal itself", data.hashCode(), data.hashCode());

        // Cloned object equals original object
        BuildData data1 = data.clone();
        assertEquals("Cloned objects not equal", data1, data);
        assertTrue("Cloned objects not equal", data1.equals(data));
        assertTrue("Cloned objects not equal", data.equals(data1));
        assertEquals("Cloned object hashCodes not equal", data.hashCode(), data1.hashCode());

        // Saved build makes object unequal
        Revision revision1 = new Revision(sha1);
        Build build1 = new Build(revision1, 1, Result.SUCCESS);
        data1.saveBuild(build1);
        assertFalse("Distinct objects shouldn't be equal", data.equals(data1));
        assertFalse("Distinct objects shouldn't be equal", data1.equals(data));

        // Same saved build makes objects equal
        BuildData data2 = data.clone();
        data2.saveBuild(build1);
        assertTrue("Objects with same saved build not equal", data2.equals(data1));
        assertTrue("Objects with same saved build not equal", data1.equals(data2));
        assertEquals("Objects with same saved build not equal hashCodes", data2.hashCode(), data1.hashCode());

        // Add remote URL makes objects unequal
        final String remoteUrl2 = "git://github.com/jenkinsci/git-plugin.git";
        data1.addRemoteUrl(remoteUrl2);
        assertFalse("Distinct objects shouldn't be equal", data.equals(data1));
        assertFalse("Distinct objects shouldn't be equal", data1.equals(data));

        // Add same remote URL makes objects equal
        data2.addRemoteUrl(remoteUrl2);
        assertTrue("Objects with same remote URL not equal", data2.equals(data1));
        assertTrue("Objects with same remote URL not equal", data1.equals(data2));
        assertEquals("Objects with same remote URL not equal hashCodes", data2.hashCode(), data1.hashCode());

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
        assertTrue(data1.equals(data2));
        assertEquals(data1.hashCode(), data2.hashCode());

        // Saving different build results still equal BuildData,
        // because the different build results are equal
        data1.saveBuild(build1);
        data2.saveBuild(build2);
        assertTrue(data1.equals(data2));
        assertEquals(data1.hashCode(), data2.hashCode());

        // Set SCM name doesn't change equality or hashCode
        data1.setScmName("scm 1");
        assertTrue(data1.equals(data2));
        assertEquals(data1.hashCode(), data2.hashCode());
        data2.setScmName("scm 2");
        assertTrue(data1.equals(data2));
        assertEquals(data1.hashCode(), data2.hashCode());

        BuildData emptyData = new BuildData();
        emptyData.remoteUrls = null;
        assertNotEquals("Non-empty object equal empty", data, emptyData);
        assertNotEquals("Empty object similar to non-empty", emptyData, data);
    }

    @Test
    public void testSetIndex() {
        data.setIndex(null);
        assertEquals(null, data.getIndex());
        data.setIndex(-1);
        assertEquals(null, data.getIndex());
        data.setIndex(0);
        assertEquals(null, data.getIndex());
        data.setIndex(13);
        assertEquals(13, data.getIndex().intValue());
        data.setIndex(-1);
        assertEquals(null, data.getIndex());
    }

    @Test
    public void testSimilarToHttpsRemoteURL() {
        final String SIMPLE_URL = "https://github.com/jenkinsci/git-plugin";
        BuildData simple = new BuildData("git-" + SIMPLE_URL);
        simple.addRemoteUrl(SIMPLE_URL);
        permuteBaseURL(SIMPLE_URL, simple);
    }

    @Test
    public void testSimilarToScpRemoteURL() {
        final String SIMPLE_URL = "git@github.com:jenkinsci/git-plugin";
        BuildData simple = new BuildData("git-" + SIMPLE_URL);
        simple.addRemoteUrl(SIMPLE_URL);
        permuteBaseURL(SIMPLE_URL, simple);
    }

    @Test
    public void testSimilarToSshRemoteURL() {
        final String SIMPLE_URL = "ssh://git@github.com/jenkinsci/git-plugin";
        BuildData simple = new BuildData("git-" + SIMPLE_URL);
        simple.addRemoteUrl(SIMPLE_URL);
        permuteBaseURL(SIMPLE_URL, simple);
    }

    private void permuteBaseURL(String simpleURL, BuildData simple) {
        final String TRAILING_SLASH_URL = simpleURL + "/";
        BuildData trailingSlash = new BuildData("git-" + TRAILING_SLASH_URL);
        trailingSlash.addRemoteUrl(TRAILING_SLASH_URL);
        assertTrue("Trailing slash not similar to simple URL " + TRAILING_SLASH_URL,
                trailingSlash.similarTo(simple));

        final String TRAILING_SLASHES_URL = TRAILING_SLASH_URL + "//";
        BuildData trailingSlashes = new BuildData("git-" + TRAILING_SLASHES_URL);
        trailingSlashes.addRemoteUrl(TRAILING_SLASHES_URL);
        assertTrue("Trailing slashes not similar to simple URL " + TRAILING_SLASHES_URL,
                trailingSlashes.similarTo(simple));

        final String DOT_GIT_URL = simpleURL + ".git";
        BuildData dotGit = new BuildData("git-" + DOT_GIT_URL);
        dotGit.addRemoteUrl(DOT_GIT_URL);
        assertTrue("Dot git not similar to simple URL " + DOT_GIT_URL,
                dotGit.similarTo(simple));

        final String DOT_GIT_TRAILING_SLASH_URL = DOT_GIT_URL + "/";
        BuildData dotGitTrailingSlash = new BuildData("git-" + DOT_GIT_TRAILING_SLASH_URL);
        dotGitTrailingSlash.addRemoteUrl(DOT_GIT_TRAILING_SLASH_URL);
        assertTrue("Dot git trailing slash not similar to dot git URL " + DOT_GIT_TRAILING_SLASH_URL,
                dotGitTrailingSlash.similarTo(dotGit));

        final String DOT_GIT_TRAILING_SLASHES_URL = DOT_GIT_TRAILING_SLASH_URL + "///";
        BuildData dotGitTrailingSlashes = new BuildData("git-" + DOT_GIT_TRAILING_SLASHES_URL);
        dotGitTrailingSlashes.addRemoteUrl(DOT_GIT_TRAILING_SLASHES_URL);
        assertTrue("Dot git trailing slashes not similar to dot git URL " + DOT_GIT_TRAILING_SLASHES_URL,
                dotGitTrailingSlashes.similarTo(dotGit));
    }

    @Test
    @Issue("JENKINS-43630")
    public void testSimilarToContainsNullURL() {
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
    public void testGetIndex() {
        assertEquals(null, data.getIndex());
    }

    @Test
    public void testGetRemoteUrls() {
        assertTrue(data.getRemoteUrls().isEmpty());
    }

    @Test
    public void testClone() {
        // Tested in testSimilarTo and testEquals
    }

    @Test
    public void testSimilarTo() {
        data.addRemoteUrl(remoteUrl);

        // Null object not similar to non-null
        BuildData dataNull = null;
        assertFalse("Null object similar to non-null", data.similarTo(dataNull));

        BuildData emptyData = new BuildData();
        assertFalse("Non-empty object similar to empty", data.similarTo(emptyData));
        assertFalse("Empty object similar to non-empty", emptyData.similarTo(data));
        emptyData.remoteUrls = null;
        assertFalse("Non-empty object similar to empty", data.similarTo(emptyData));
        assertFalse("Empty object similar to non-empty", emptyData.similarTo(data));

        // Object should be similar to itself
        assertTrue("Object not similar to itself", data.similarTo(data));

        // Object should not be similar to constructed variants
        Collection<UserRemoteConfig> emptyList = new ArrayList<>();
        assertFalse("Object similar to data with SCM name", data.similarTo(new BuildData("abc")));
        assertFalse("Object similar to data with SCM name & empty", data.similarTo(new BuildData("abc", emptyList)));

        BuildData dataSCM = new BuildData("scm");
        assertFalse("Object similar to data with SCM name", dataSCM.similarTo(data));
        assertTrue("Object with SCM name not similar to data with SCM name", dataSCM.similarTo(new BuildData("abc")));
        assertTrue("Object with SCM name not similar to data with SCM name & empty", dataSCM.similarTo(new BuildData("abc", emptyList)));

        // Cloned object equals original object
        BuildData dataClone = data.clone();
        assertTrue("Clone not similar to origin", dataClone.similarTo(data));
        assertTrue("Origin not similar to clone", data.similarTo(dataClone));

        // Saved build makes objects dissimilar
        Revision revision1 = new Revision(sha1);
        Build build1 = new Build(revision1, 1, Result.SUCCESS);
        dataClone.saveBuild(build1);
        assertFalse("Unmodified origin similar to modified clone", data.similarTo(dataClone));
        assertFalse("Modified clone similar to unmodified origin", dataClone.similarTo(data));
        assertTrue("Modified clone not similar to itself", dataClone.similarTo(dataClone));

        // Same saved build makes objects similar
        BuildData data2 = data.clone();
        data2.saveBuild(build1);
        assertFalse("Unmodified origin similar to modified clone", data.similarTo(data2));
        assertTrue("Objects with same saved build not similar (1)", data2.similarTo(dataClone));
        assertTrue("Objects with same saved build not similar (2)", dataClone.similarTo(data2));

        // Add remote URL makes objects dissimilar
        final String remoteUrl = "git://github.com/jenkinsci/git-client-plugin.git";
        dataClone.addRemoteUrl(remoteUrl);
        assertFalse("Distinct objects shouldn't be similar (1)", data.similarTo(dataClone));
        assertFalse("Distinct objects shouldn't be similar (2)", dataClone.similarTo(data));

        // Add same remote URL makes objects similar
        data2.addRemoteUrl(remoteUrl);
        assertTrue("Objects with same remote URL dissimilar", data2.similarTo(dataClone));
        assertTrue("Objects with same remote URL dissimilar", dataClone.similarTo(data2));

        // Add different remote URL objects similar
        final String trailingSlash = "git-client-plugin.git/"; // Unlikely as remote URL
        dataClone.addRemoteUrl(trailingSlash);
        assertFalse("Distinct objects shouldn't be similar", data.similarTo(dataClone));
        assertFalse("Distinct objects shouldn't be similar", dataClone.similarTo(data));

        data2.addRemoteUrl(trailingSlash);
        assertTrue("Objects with same remote URL dissimilar", data2.similarTo(dataClone));
        assertTrue("Objects with same remote URL dissimilar", dataClone.similarTo(data2));

        // Add different remote URL objects
        final String noSlash = "git-client-plugin"; // Unlikely as remote URL
        dataClone.addRemoteUrl(noSlash);
        assertFalse("Distinct objects shouldn't be similar", data.similarTo(dataClone));
        assertFalse("Distinct objects shouldn't be similar", dataClone.similarTo(data));

        data2.addRemoteUrl(noSlash);
        assertTrue("Objects with same remote URL dissimilar", data2.similarTo(dataClone));
        assertTrue("Objects with same remote URL dissimilar", dataClone.similarTo(data2));

        // Another saved build still keeps objects similar
        String branchName = "origin/master";
        Collection<Branch> branches = new ArrayList<>();
        Branch branch = new Branch(branchName, sha1);
        branches.add(branch);
        Revision revision2 = new Revision(sha1, branches);
        Build build2 = new Build(revision2, 1, Result.FAILURE);
        dataClone.saveBuild(build2);
        assertTrue("Another saved build, still similar (1)", dataClone.similarTo(data2));
        assertTrue("Another saved build, still similar (2)", data2.similarTo(dataClone));
        data2.saveBuild(build2);
        assertTrue("Another saved build, still similar (3)", dataClone.similarTo(data2));
        assertTrue("Another saved build, still similar (4)", data2.similarTo(dataClone));

        // Saving different build results still similar BuildData
        dataClone.saveBuild(build1);
        assertTrue("Saved build with different results, similar (5)", dataClone.similarTo(data2));
        assertTrue("Saved build with different results, similar (6)", data2.similarTo(dataClone));
        data2.saveBuild(build2);
        assertTrue("Saved build with different results, similar (7)", dataClone.similarTo(data2));
        assertTrue("Saved build with different results, similar (8)", data2.similarTo(dataClone));

        // Set SCM name doesn't change similarity
        dataClone.setScmName("scm 1");
        assertTrue(dataClone.similarTo(data2));
        data2.setScmName("scm 2");
        assertTrue(dataClone.similarTo(data2));
    }

    @Test
    public void testHashCodeEmptyData() {
        BuildData emptyData = new BuildData();
        assertEquals(emptyData.hashCode(), emptyData.hashCode());
        emptyData.remoteUrls = null;
        assertEquals(emptyData.hashCode(), emptyData.hashCode());
    }
}
