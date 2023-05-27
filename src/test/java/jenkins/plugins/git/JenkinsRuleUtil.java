/*
 * The MIT License
 *
 * Copyright 2023 Mark Waite.
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
package jenkins.plugins.git;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import hudson.Launcher;
import hudson.model.TaskListener;
import hudson.util.ArgumentListBuilder;
import java.io.File;
import java.util.concurrent.TimeUnit;

public class JenkinsRuleUtil {

    public static void makeFilesWritable(File dir, TaskListener listener) throws Exception {
        if (isWindows()) {
            /* Make all files writable so they can be deleted */
            System.out.println("**** dir being made writable is " + dir.getAbsolutePath());
            Launcher launcher = new Launcher.LocalLauncher(listener);
            ArgumentListBuilder args = new ArgumentListBuilder("attrib");
            args.add("-R", "/s");
            Launcher.ProcStarter p = launcher.launch().cmds(args).pwd(dir);
            int status = p.start().joinWithTimeout(13, TimeUnit.SECONDS, listener);
            assertThat("Windows attrib.exe -r /s failed", status, is(0));
        }
    }

    private static boolean isWindows() {
        return File.pathSeparatorChar == ';';
    }
}
