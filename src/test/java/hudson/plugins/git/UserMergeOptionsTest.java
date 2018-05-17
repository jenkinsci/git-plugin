package hudson.plugins.git;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.jenkinsci.plugins.gitclient.MergeCommand;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class UserMergeOptionsTest {

    private final UserMergeOptions options;
    private final UserMergeOptions deprecatedOptions;

    private final String expectedMergeRemote;
    private final String expectedMergeTarget;
    private final MergeCommand.Strategy expectedMergeStrategy;
    private final MergeCommand.GitPluginFastForwardMode expectedFastForwardMode;

    public UserMergeOptionsTest(
            String mergeRemote,
            String mergeTarget,
            MergeCommand.Strategy mergeStrategy,
            MergeCommand.GitPluginFastForwardMode fastForwardMode) {
        this.expectedMergeRemote = mergeRemote;
        this.expectedMergeTarget = mergeTarget;
        this.expectedMergeStrategy = mergeStrategy;
        this.expectedFastForwardMode = fastForwardMode;
        options = new UserMergeOptions(
                mergeRemote,
                mergeTarget,
                mergeStrategy == null ? null : mergeStrategy.toString(),
                fastForwardMode);
        deprecatedOptions = new UserMergeOptions(
                mergeRemote,
                mergeTarget,
                mergeStrategy == null ? null : mergeStrategy.toString());
    }

    @Parameterized.Parameters(name = "{0}+{1}+{2}+{3}")
    public static Collection mergeOptionVariants() {
        List<Object[]> mergeOptions = new ArrayList<>();
        String[] remotes = new String[]{null, "src_remote"};
        String[] targets = new String[]{null, "dst_remote"};
        MergeCommand.Strategy[] mergeStrategies = new MergeCommand.Strategy[]{
            null,
            MergeCommand.Strategy.DEFAULT,
            MergeCommand.Strategy.OCTOPUS,
            MergeCommand.Strategy.OURS,
            MergeCommand.Strategy.RECURSIVE,
            MergeCommand.Strategy.RESOLVE,
            MergeCommand.Strategy.SUBTREE
        };
        MergeCommand.GitPluginFastForwardMode[] fastForwardModes = new MergeCommand.GitPluginFastForwardMode[]{
            null,
            MergeCommand.GitPluginFastForwardMode.FF,
            MergeCommand.GitPluginFastForwardMode.FF_ONLY,
            MergeCommand.GitPluginFastForwardMode.NO_FF
        };
        for (String remote : remotes) {
            for (String target : targets) {
                for (MergeCommand.Strategy strategy : mergeStrategies) {
                    for (MergeCommand.GitPluginFastForwardMode mode : fastForwardModes) {
                        Object[] mergeOption = {remote, target, strategy, mode};
                        mergeOptions.add(mergeOption);
                    }
                }
            }
        }
        return mergeOptions;
    }

    @Test
    public void testGetMergeRemote() {
        assertEquals(expectedMergeRemote, options.getMergeRemote());
    }

    @Test
    public void testGetMergeTarget() {
        assertEquals(expectedMergeTarget, options.getMergeTarget());
    }

    @Test
    public void testGetRef() {
        assertEquals(expectedMergeRemote + "/" + expectedMergeTarget, options.getRef());
    }

    @Test
    public void testGetMergeStrategy() {
        assertEquals(expectedMergeStrategy == null ? MergeCommand.Strategy.DEFAULT : expectedMergeStrategy, options.getMergeStrategy());
    }

    @Test
    public void testGetFastForwardMode() {
        assertEquals(expectedFastForwardMode == null ? MergeCommand.GitPluginFastForwardMode.FF : expectedFastForwardMode, options.getFastForwardMode());
    }

    @Test
    public void testToString() {
        final String expected = "UserMergeOptions{"
                + "mergeRemote='" + expectedMergeRemote + "', "
                + "mergeTarget='" + expectedMergeTarget + "', "
                + "mergeStrategy='" + (expectedMergeStrategy == null ? MergeCommand.Strategy.DEFAULT : expectedMergeStrategy).name() + "', "
                + "fastForwardMode='" + (expectedFastForwardMode == null ? MergeCommand.GitPluginFastForwardMode.FF : expectedFastForwardMode).name() + "'"
                + '}';
        assertEquals(expected, options.toString());
    }

    @Test
    public void testEqualsSymmetric() {
        UserMergeOptions expected = new UserMergeOptions(
                this.expectedMergeRemote,
                this.expectedMergeTarget,
                this.expectedMergeStrategy == null ? null : this.expectedMergeStrategy.toString(),
                this.expectedFastForwardMode);
        assertEquals(expected, options);
        assertEquals(options, expected);
    }

    @Test
    public void testEqualsReflexive() {
        UserMergeOptions expected = new UserMergeOptions(
                this.expectedMergeRemote,
                this.expectedMergeTarget,
                this.expectedMergeStrategy == null ? null : this.expectedMergeStrategy.toString(),
                this.expectedFastForwardMode);
        /* reflexive */
        assertEquals(options, options);
        assertEquals(expected, expected);
    }

    @Test
    public void testEqualsTransitive() {
        UserMergeOptions expected = new UserMergeOptions(
                this.expectedMergeRemote,
                this.expectedMergeTarget,
                this.expectedMergeStrategy == null ? null : this.expectedMergeStrategy.toString(),
                this.expectedFastForwardMode);
        UserMergeOptions expected1 = new UserMergeOptions(
                this.expectedMergeRemote,
                this.expectedMergeTarget,
                this.expectedMergeStrategy == null ? null : this.expectedMergeStrategy.toString(),
                this.expectedFastForwardMode);
        assertEquals(expected, expected1);
        assertEquals(expected1, options);
        assertEquals(expected, options);
    }

    @Test
    public void testEqualsDeprecatedConstructor() {
        if (this.expectedFastForwardMode == MergeCommand.GitPluginFastForwardMode.FF) {
            assertEquals(options, deprecatedOptions);
        } else {
            assertNotEquals(options, deprecatedOptions);
        }
    }

    @Test
    public void testNotEquals() {
        UserMergeOptions notExpected1 = new UserMergeOptions(
                "x" + this.expectedMergeRemote,
                this.expectedMergeTarget,
                this.expectedMergeStrategy == null ? null : this.expectedMergeStrategy.toString(),
                this.expectedFastForwardMode);
        assertNotEquals(notExpected1, options);
        UserMergeOptions notExpected2 = new UserMergeOptions(
                this.expectedMergeRemote,
                "y" + this.expectedMergeTarget,
                this.expectedMergeStrategy == null ? null : this.expectedMergeStrategy.toString(),
                this.expectedFastForwardMode);
        assertNotEquals(notExpected2, options);
        assertNotEquals(options, "A different data type");
    }

    @Test
    public void testHashCode() {
        UserMergeOptions expected = new UserMergeOptions(
                this.expectedMergeRemote,
                this.expectedMergeTarget,
                this.expectedMergeStrategy == null ? null : this.expectedMergeStrategy.toString(),
                this.expectedFastForwardMode);
        assertEquals(expected, options);
        assertEquals(expected.hashCode(), options.hashCode());
    }
}
