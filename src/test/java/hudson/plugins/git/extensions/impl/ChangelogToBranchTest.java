package hudson.plugins.git.extensions.impl;

import hudson.plugins.git.ChangelogToBranchOptions;

import org.junit.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertThrows;

public class ChangelogToBranchTest {
    @Test
    public void checkConstructorIllegalArgument() {
        ChangelogToBranchOptions nullOptions = null;
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                                                  () -> new ChangelogToBranch(nullOptions));
        assertThat(e.getMessage(), containsString("options may not be null"));
    }
}
