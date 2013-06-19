package hudson.plugins.git.extensions;

import hudson.plugins.git.GitSCM;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * Marker to designate that this extension point doesn't operate by implementing
 * the callbacks but instead {@link GitSCM} has a prior knowledge about this extension.
 *
 * <p>
 * This is primarily done to eliminate the "advanced" section completely in favor
 * of the extension list.
 *
 * @author Kohsuke Kawaguchi
 */
@Restricted(NoExternalUse.class)
public abstract class FakeGitSCMExtension extends GitSCMExtension {
}
