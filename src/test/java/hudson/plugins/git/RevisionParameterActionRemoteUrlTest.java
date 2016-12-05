package hudson.plugins.git;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.eclipse.jgit.transport.RemoteConfig;
import org.eclipse.jgit.transport.URIish;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class RevisionParameterActionRemoteUrlTest {

    @Test
    public void noRemoteURLSet() throws Exception {
        RevisionParameterAction target = new RevisionParameterAction("sha1");
        URIish remoteURL = new URIish("https://github.com/jenkinsci/git-plugin.git");
        assertTrue("should always return true when no remote set", target.canOriginateFrom(remotes(remoteURL)));
    }

    @Test
    public void remoteURLSetButDoesntMatch() throws Exception {
        URIish actionURL = new URIish("https://github.com/jenkinsci/multiple-scms-plugin.git");
        RevisionParameterAction target = new RevisionParameterAction("sha1", actionURL);

        URIish remoteURL = new URIish("https://github.com/jenkinsci/git-plugin.git");
        assertFalse("should return false on different remotes", target.canOriginateFrom(remotes(remoteURL)));
    }

    @Test
    public void remoteURLSetAndMatches() throws Exception {
        URIish actionURL = new URIish("https://github.com/jenkinsci/git-plugin.git");
        RevisionParameterAction target = new RevisionParameterAction("sha1", actionURL);

        URIish remoteURL = new URIish("https://github.com/jenkinsci/git-plugin.git");
        assertTrue("should return true on same remotes", target.canOriginateFrom(remotes(remoteURL)));
    }

    @Test
    public void multipleRemoteURLsSetAndOneMatches() throws Exception {
        URIish actionURL = new URIish("https://github.com/jenkinsci/git-plugin.git");
        RevisionParameterAction target = new RevisionParameterAction("sha1", actionURL);

        URIish remoteURL1 = new URIish("https://github.com/jenkinsci/multiple-scms-plugin.git");
        URIish remoteURL2 = new URIish("https://github.com/jenkinsci/git-plugin.git");
        assertTrue("should return true when any remote matches", target.canOriginateFrom(remotes(remoteURL1, remoteURL2)));
    }

    private List<RemoteConfig> remotes(URIish... remoteURLs) {
        List<RemoteConfig> result = new ArrayList<>();
        for (URIish remoteURL : remoteURLs) {
            result.add(remote(remoteURL));
        }
        return result;
    }

    private RemoteConfig remote(URIish remoteURL) {
        RemoteConfig result = mock(RemoteConfig.class);
        when(result.getURIs()).thenReturn(Arrays.asList(remoteURL));
        return result;
    }
}
