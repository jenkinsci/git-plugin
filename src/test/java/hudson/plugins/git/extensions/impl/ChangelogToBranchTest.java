package hudson.plugins.git.extensions.impl;

import hudson.plugins.git.ChangelogToBranchOptions;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ChangelogToBranchTest {

    @Test
    void checkConstructorIllegalArgument() {
        ChangelogToBranchOptions nullOptions = null;
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                                                  () -> new ChangelogToBranch(nullOptions));
        assertThat(e.getMessage(), containsString("options may not be null"));
    }
}
