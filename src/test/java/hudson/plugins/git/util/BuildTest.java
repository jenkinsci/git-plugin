package hudson.plugins.git.util;

import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;
import org.junit.Test;

public class BuildTest {

    @Test
    public void equalsContract() {
        EqualsVerifier.forClass(Build.class)
                .usingGetClass()
                .suppress(Warning.NONFINAL_FIELDS)
                .withIgnoredFields("hudsonBuildResult")
                .verify();
    }
}
