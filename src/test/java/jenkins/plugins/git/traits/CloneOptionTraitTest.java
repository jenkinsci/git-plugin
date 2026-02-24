package jenkins.plugins.git.traits;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import hudson.plugins.git.extensions.impl.CloneOption;
import jenkins.plugins.git.GitSCMSourceContext;

class CloneOptionTraitTest {

    @Test
    void testShallowDisabledByDefault() {
        GitSCMSourceContext<?, ?> context = new GitSCMSourceContext<>(null, null);
        assertFalse(context.wantShallow());
        assertNull(context.depth());
    }

    @Test
    void testDecorateWithDefaultCloneOption() {
        GitSCMSourceContext<?, ?> context = new GitSCMSourceContext<>(null, null);
        CloneOption cloneOption = new CloneOption(false, null, null);
        CloneOptionTrait cloneOptionTrait = new CloneOptionTrait(cloneOption);
        cloneOptionTrait.decorateContext(context);
        assertFalse(context.wantShallow());
        assertNull(context.depth());
    }

    @Test
    void testDecorateCloneOptionWithShallow() {
        GitSCMSourceContext<?, ?> context = new GitSCMSourceContext<>(null, null);
        CloneOption cloneOption = new CloneOption(true, null, null);
        cloneOption.setDepth(10);
        CloneOptionTrait cloneOptionTrait = new CloneOptionTrait(cloneOption);
        cloneOptionTrait.decorateContext(context);
        assertTrue(context.wantShallow());
        assertEquals(context.depth(), 10);
    }
}