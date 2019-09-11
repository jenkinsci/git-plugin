/*
 * The MIT License
 *
 * Copyright 2019 Mark Waite.
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
package hudson.plugins.git.util;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.model.Label;
import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.model.labels.LabelAtom;
import hudson.plugins.git.GitTool;
import hudson.slaves.DumbSlave;
import hudson.util.StreamTaskListener;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;
import org.junit.ClassRule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

public class GitUtilsJenkinsRuleTest {

    @ClassRule
    public static JenkinsRule j = new JenkinsRule();

    @Test
    public void testWorkspaceToNode() throws Exception {
        String labelString = "label-" + UUID.randomUUID().toString();
        Label label = new LabelAtom(labelString);
        DumbSlave agent = j.createOnlineSlave(label);
        FilePath workspace = agent.getWorkspaceRoot();
        assertThat(GitUtils.workspaceToNode(workspace).getLabelString(), is(labelString));

        /* Check that workspace on master reports master even when agent connected */
        assertThat(GitUtils.workspaceToNode(j.getInstance().getRootPath()), is(j.getInstance()));
    }

    @Test
    public void testWorkspaceToNodeRootPath() {
        assertThat(GitUtils.workspaceToNode(j.getInstance().getRootPath()), is(j.getInstance()));
    }

    @Test
    public void testWorkspaceToNodeNullWorkspace() {
        assertThat(GitUtils.workspaceToNode(null), is(j.getInstance()));
    }

    @Test
    public void testResolveGitTool() {
        TaskListener listener = StreamTaskListener.NULL;
        String gitTool = "Default";
        GitTool tool = GitUtils.resolveGitTool(gitTool, listener);
        assertThat(tool.getGitExe(), startsWith("git"));
    }

    @Test
    public void testResolveGitToolNull() {
        TaskListener listener = StreamTaskListener.NULL;
        String gitTool = null;
        GitTool tool = GitUtils.resolveGitTool(gitTool, listener);
        assertThat(tool.getGitExe(), startsWith("git"));
    }

    @Test
    public void testResolveGitToolNonExistentTool() {
        TaskListener listener = StreamTaskListener.NULL;
        String gitTool = "non-existent-tool";
        GitTool tool = GitUtils.resolveGitTool(gitTool, listener);
        assertThat(tool.getGitExe(), startsWith("git"));
    }

    @Test
    public void testResolveGitToolBuiltOnNull() {
        TaskListener listener = StreamTaskListener.NULL;
        String gitTool = null;
        Node builtOn = null;
        EnvVars env = new EnvVars();
        GitTool tool = GitUtils.resolveGitTool(gitTool, builtOn, env, listener);
        assertThat(tool.getGitExe(), startsWith("git"));
    }

    @Test
    public void testResolveGitToolBuiltOnAgent() throws Exception {
        TaskListener listener = StreamTaskListener.NULL;
        String gitTool = "/opt/my-non-existing-git/bin/git";
        String labelString = "label-" + UUID.randomUUID().toString();
        Label label = new LabelAtom(labelString);
        DumbSlave agent = j.createOnlineSlave(label);
        EnvVars env = new EnvVars();
        GitTool tool = GitUtils.resolveGitTool(gitTool, agent, env, listener);
        assertThat(tool.getGitExe(), startsWith("git"));
    }
}
