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
import hudson.model.AbstractBuild;
import hudson.model.TaskListener;
import hudson.plugins.git.util.BuildData;
import org.jenkinsci.plugins.tokenmacro.DataBoundTokenMacro;
import org.jenkinsci.plugins.tokenmacro.MacroEvaluationException;

import java.io.IOException;

/**
 * {@code GIT_BRANCH_SHORT} token that expands to the plain branch(es) that was built.
 *
 * @author Horst Krause
 */
@Extension(optional=true)
public class GitBranchShortTokenMacro extends GitBranchTokenMacro {

    @Override
    public boolean acceptsMacroName(String macroName) {

        return macroName.equals("GIT_BRANCH_SHORT");
    }

    @Override
    public String evaluate(AbstractBuild<?, ?> context, TaskListener listener, String macroName) throws MacroEvaluationException, IOException, InterruptedException {

        String name = super.evaluate(context, listener, macroName);

        // if single branch and not fullName requested, then remove everything except the plain branch name
        if ((!all) && (!fullName) && (name.lastIndexOf(GitSCM.ORIGIN + "/") > -1)) {
            name = name.substring(name.lastIndexOf(GitSCM.ORIGIN + "/") + GitSCM.ORIGIN.length() + 1);
        }
        return name;
    }

}

