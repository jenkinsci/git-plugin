/*
 * The MIT License
 *
 * Copyright 2017 CloudBees, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package hudson.plugins.git;

import hudson.FilePath;
import hudson.slaves.DumbSlave;
import java.io.File;

import static org.assertj.core.api.Assertions.*;
import org.junit.Test;
import org.junit.Rule;
import org.jvnet.hudson.test.JenkinsRule;

public class CacheTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    public CacheTest() {
    }

    /*
     * Because setup and teardown of agents in a JenkinsRule is expensive,
     * this test performs many different assertions in a single test.
     * Includes no concurrent access tests.
     */
    @Test
    public void testGetCacheDir_Node_String() throws Exception {
        final String remoteA = "https://github.com/jenkinsci/git-plugin";
        final String remoteAplus = remoteA + ".git";
        final String remoteB = "https://github.com/jenkinsci/git-client-plugin";
        final String remoteBplus = remoteB + ".git";
        final String[] remotes = {remoteA, remoteAplus, remoteB, remoteBplus};

        /* Create caches on the master */
        File cacheA = Cache.getCacheDir(remoteA);
        File cacheAplus = Cache.getCacheDir(remoteAplus);
        File cacheB = Cache.getCacheDir(remoteB);
        File cacheBplus = Cache.getCacheDir(remoteBplus);
        File[] fileCaches = {cacheA, cacheAplus, cacheB, cacheBplus};

        assertThat(cacheA).exists();
        assertThat(cacheA).isNotEqualTo(cacheAplus);
        assertThat(cacheA).isNotEqualTo(cacheB);
        assertThat(cacheA).isNotEqualTo(cacheBplus);

        assertThat(cacheAplus).exists();
        assertThat(cacheAplus).isNotEqualTo(cacheB);
        assertThat(cacheAplus).isNotEqualTo(cacheBplus);

        assertThat(cacheB).exists();
        assertThat(cacheB).isNotEqualTo(cacheBplus);

        assertThat(cacheBplus).exists();

        /* Create an agent and two caches on the agent */
        DumbSlave agent0 = j.createOnlineSlave();

        FilePath cache0A = Cache.getCacheDir(agent0, remoteA);
        FilePath cache0B = Cache.getCacheDir(agent0, remoteB);

        assertThat(cache0A).isNotEqualTo(cache0B);

        /* Create another agent and two caches on the agent */
        DumbSlave agent1 = j.createOnlineSlave();

        FilePath cache1A = Cache.getCacheDir(agent1, remoteA);
        FilePath cache1B = Cache.getCacheDir(agent1, remoteB);

        FilePath[] filePathCaches = {cache0A, cache0B, cache1A, cache1B};
        DumbSlave[] agents = {agent0, agent1};

        assertThat(agent0).isNotEqualTo(agent1);

        assertThat(cache0A).isNotEqualTo(cache1A);
        assertThat(cache0A).isNotEqualTo(cache1B);

        assertThat(cache0B).isNotEqualTo(cache1A);
        assertThat(cache0B).isNotEqualTo(cache1B);

        assertThat(cache1A).isNotEqualTo(cache1B);

        boolean useLock = true;
        /* some tests with lock, some without */
        for (String remote : remotes) {
            for (File fileCache : fileCaches) {
                if (useLock) {
                    Cache.lock(remote);
                }
                assertThat(fileCache).as("Remote '%s' missing cache dir %s", remote, fileCache.getAbsolutePath()).exists();
                if (useLock) {
                    Cache.unlock(remote);
                }
            }
            for (DumbSlave agent : agents) {
                for (FilePath filePathCache : filePathCaches) {
                    if (useLock) {
                        Cache.lock(agent, remote);
                    }
                    assertThat(filePathCache.exists()).as("Remote '%s', agent '%s' missing FilePath cache dir", remote, agent.getDisplayName()).isEqualTo(true);
                    if (useLock) {
                        Cache.unlock(agent, remote);
                    }
                }
            }
            useLock = !useLock;
        }

        Cache.lock(remoteA);
        Cache.lock(remoteB);
        Cache.unlock(remoteA);
        Cache.unlock(remoteB);

        Cache.lock(agent0, remoteA);
        Cache.lock(agent0, remoteB);
        Cache.unlock(agent0, remoteA);
        Cache.unlock(agent0, remoteB);

        Cache.lock(agent0, remoteA);
        Cache.lock(agent1, remoteA);
        Cache.unlock(agent0, remoteA);
        Cache.unlock(agent1, remoteA);

        /* Unlock caches which aren't locked - throws IllegalMonitorStateException */
        try {
            Cache.unlock(agent1, remoteB);
            fail("Should have thrown IllegalMonitorStateException");
        } catch (IllegalMonitorStateException illegalState) {
            // Excepted exception caught
        }
        try {
            Cache.unlock(remoteA);
            fail("Should have thrown IllegalMonitorStateException");
        } catch (IllegalMonitorStateException illegalState) {
            // Excepted exception caught
        }
    }

    // @Test
    public void testGetCacheDir_String() {
        // Tested in testGetCacheDir_Node_String
    }

    // @Test
    public void testLock_String() {
        // Tested in testGetCacheDir_Node_String
    }

    // @Test
    public void testUnlock_String() {
        // Tested in testGetCacheDir_Node_String
    }

    // @Test
    public void testLock_Node_String() {
        // Tested in testGetCacheDir_Node_String
    }

    // @Test
    public void testUnlock_Node_String() {
        // Tested in testGetCacheDir_Node_String
    }
}
