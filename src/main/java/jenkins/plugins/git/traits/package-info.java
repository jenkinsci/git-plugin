/*
 * The MIT License
 *
 * Copyright (c) 2017 CloudBees, Inc.
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
 *
 */

/**
 * The common behaviours that can be used by all {@link jenkins.plugins.git.GitSCMSource} instances and most
 * {@link jenkins.plugins.git.AbstractGitSCMSource} instances.
 * A lot of these will be effectively simple wrappers over {@link hudson.plugins.git.extensions.GitSCMExtension}
 * however we do not want every {@link hudson.plugins.git.extensions.GitSCMExtension} to have a corresponding
 * {@link jenkins.plugins.git.traits.GitSCMExtensionTrait} as some of the extensions do not make sense in the context
 * of a {@link jenkins.plugins.git.GitSCMSource}.
 * <p>
 * There are some recommendations for {@link hudson.plugins.git.extensions.GitSCMExtension} implementations that are
 * being exposed as {@link jenkins.plugins.git.traits.GitSCMExtensionTrait} types:
 * <ul>
 *     <li>Implement an {@link hudson.plugins.git.extensions.GitSCMExtension#equals(java.lang.Object)}</li>
 *     <li>Implement a {@link hudson.plugins.git.extensions.GitSCMExtension#hashCode()} returning {@link java.lang.Class#hashCode()}</li>
 *     <li>Implement {@link hudson.plugins.git.extensions.GitSCMExtension#toString()}</li>
 * </ul>
 *
 * @since 3.4.0
 */
package jenkins.plugins.git.traits;
