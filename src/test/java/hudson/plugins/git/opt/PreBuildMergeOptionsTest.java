/*
 * The MIT License
 *
 * Copyright 2014 Jesse Glick.
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

package hudson.plugins.git.opt;

import hudson.model.FreeStyleProject;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.UserMergeOptions;
import hudson.plugins.git.UserRemoteConfig;
import hudson.plugins.git.extensions.GitSCMExtension;
import hudson.plugins.git.extensions.impl.PreBuildMerge;
import org.jenkinsci.plugins.gitclient.MergeCommand;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;

import java.util.Collections;

public class PreBuildMergeOptionsTest {

    @Rule public JenkinsRule r = new JenkinsRule();

    @Issue("JENKINS-9843")
    @Test public void exporting() throws Exception {
        FreeStyleProject p = r.createFreeStyleProject();
        p.setScm(new GitSCM(Collections.singletonList(new UserRemoteConfig("http://wherever/thing.git", "repo", null, null)), null, null, null, null, null, Collections.<GitSCMExtension>singletonList(new PreBuildMerge(new UserMergeOptions("repo", "master", null, MergeCommand.Strategy.DEFAULT.name(), MergeCommand.GitPluginFastForwardMode.FF)))));
        r.createWebClient().goToXml(p.getUrl() + "api/xml?depth=2");
    }

}
