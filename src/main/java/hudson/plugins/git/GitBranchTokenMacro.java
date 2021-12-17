/*
 * The MIT License
 *
 * Copyright 2011 CloudBees, Inc.
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

import hudson.Extension;
import hudson.FilePath;
import hudson.model.AbstractBuild;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.plugins.git.util.BuildData;
import org.jenkinsci.plugins.tokenmacro.DataBoundTokenMacro;
import org.jenkinsci.plugins.tokenmacro.MacroEvaluationException;

import java.io.IOException;

/**
 * {@code GIT_BRANCH} token that expands to the branch(es) that was built.
 *
 * @author Kohsuke Kawaguchi
 */
@Extension(optional=true)
public class GitBranchTokenMacro extends DataBoundTokenMacro {
    /**
     * If true, list up all the branches not just the first one.
     */
    @Parameter
    public boolean all;

    /**
     * If true, include all the prefixes of the branch name
     */
    @Parameter
    public boolean fullName;

    @Override
    public boolean acceptsMacroName(String macroName) {
        return macroName.equals("GIT_BRANCH");
    }

    @Override
    public String evaluate(AbstractBuild<?, ?> context, TaskListener listener, String macroName) throws MacroEvaluationException, IOException, InterruptedException {
        return evaluate(context, context.getWorkspace(), listener, macroName);
    }

    @Override
    public String evaluate(Run<?, ?> context, FilePath workspace, TaskListener listener, String macroName) throws MacroEvaluationException, IOException, InterruptedException {
        BuildData data = context.getAction(BuildData.class);
        if (data == null) {
            return "";  // shall we report an error more explicitly?
        }

        Revision lb = data.getLastBuiltRevision();
        if (lb==null || lb.getBranches().isEmpty())   return "";

        if (all) {
            StringBuilder buf = new StringBuilder();
            for (Branch b : lb.getBranches()) {
                if (buf.length()>0) buf.append(',');
                buf.append(format(b));
            }
            return buf.toString();
        } else {
            return format(lb.getBranches().iterator().next());
        }
    }

    private String format(Branch b) {
        String n = b.getName();
        if (fullName)   return n;
        return n.substring(n.indexOf('/')+1); // trim off '/'
    }
}
