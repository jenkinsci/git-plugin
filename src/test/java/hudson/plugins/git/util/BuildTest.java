package hudson.plugins.git.util;

import hudson.model.Result;
import hudson.plugins.git.Revision;
import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;
import org.junit.jupiter.api.Test;

import org.eclipse.jgit.lib.ObjectId;

import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;

class BuildTest {

    private final int BUILD_NUMBER = 1;
    private final Result BUILD_RESULT = Result.NOT_BUILT;

    /* A build with no revision */
    private final Build nullRevisionBuild = new Build(null, BUILD_NUMBER, BUILD_RESULT);

    /* A build of revision that has marked the same revision */
    private final ObjectId objectId = ObjectId.fromString("b04752abaaa9ee3112b0c9d1910093c977d4e583");
    private final Revision revision = new Revision(objectId);
    private final Build build = new Build(revision, BUILD_NUMBER, BUILD_RESULT);

    /* A build of revision that has marked a different revision */
    private final ObjectId markedId = ObjectId.fromString("deedbeadfeedcededeafc9d1910093c977d4e583");
    private final Revision markedRevision = new Revision(markedId);
    private final Build markedBuild = new Build(markedRevision, revision, BUILD_NUMBER, BUILD_RESULT);

    @Test
    void testGetSHA1() {
        assertThat(build.getSHA1(), is(revision.getSha1()));
    }

    @Test
    void testGetRevision() {
        assertThat(build.getRevision(), is(revision));
    }

    @Test
    void testGetMarked() {
        assertThat(build.getMarked(), is(revision));
    }

    @Test
    void testGetMarkedDifferentRevision() {
        assertThat(markedBuild.getMarked(), is(markedRevision));
    }

    @Test
    void testGetBuildNumber() {
        assertThat(build.getBuildNumber(), is(BUILD_NUMBER));
    }

    @Test
    void testGetBuildResult() {
        assertThat(build.getBuildResult(), is(BUILD_RESULT));
    }

    @Test
    void testToString() {
        assertThat(build.toString(), is("Build #" + BUILD_NUMBER + " of Revision " + revision.getSha1String() + " ()"));
    }

    @Test
    void testClone() {
        Build clonedBuild = build.clone();
        assertThat(clonedBuild.getSHA1(), is(build.getSHA1()));
    }

    @Test
    void testCloneNullRevision() {
        Build clonedBuild = nullRevisionBuild.clone();
        assertThat(clonedBuild.getRevision(), is(nullValue()));
    }

    @Test
    void testIsFor() {
        assertThat(build.isFor(revision.getSha1String()), is(true));
    }

    @Test
    void testIsForMarkedRevision() {
        assertThat(markedBuild.isFor(revision.getSha1String()), is(true));
        assertThat(markedBuild.isFor(markedRevision.getSha1String()), is(false));
    }

    @Test
    void testIsForNullRevision() {
        assertThat(nullRevisionBuild.isFor(revision.getSha1String()), is(false));
    }

    @Test
    void testReadResolve() throws Exception {
        assertThat(build.readResolve(), is(instanceOf(build.getClass())));
    }

    @Test
    void testReadResolveNullRevision() throws Exception {
        Object readObject = nullRevisionBuild.readResolve();
        assertThat(readObject, is(instanceOf(build.getClass())));
        Build readBuild = (Build) readObject;
        assertThat(readBuild.getMarked(), is(readBuild.getRevision()));
        assertThat(nullRevisionBuild.readResolve(), is(instanceOf(build.getClass())));
    }

    @Test
    void equalsContract() {
        EqualsVerifier.forClass(Build.class)
                .usingGetClass()
                .suppress(Warning.NONFINAL_FIELDS)
                .withIgnoredFields("hudsonBuildResult")
                .verify();
    }
}
