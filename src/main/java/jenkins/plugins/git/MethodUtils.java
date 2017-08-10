/*
 * The MIT License
 *
 * Copyright (c) 2016 CloudBees, Inc.
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

package jenkins.plugins.git;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import javax.annotation.Nonnull;
import org.apache.commons.lang.ClassUtils;
import org.apache.commons.lang.Validate;

/**
 * Helper to identify methods that have not been implemented / overridden.
 */
class MethodUtils {
    /**
     * Checks if the  method defined on the base type with the given arguments
     * are overridden in the given derived type.
     */
    // TODO replace with core utility method once JENKINS-30002 is available in base version of Jenkins
    static boolean isOverridden(@Nonnull Class base, @Nonnull Class derived, @Nonnull String methodName,
                                       @Nonnull Class... types) {
        Method baseMethod = getMethodImpl(base, methodName, types);
        Method derivedMethod = getMethodImpl(derived, methodName, types);
        return baseMethod == null ?
                derivedMethod != null && !Modifier.isAbstract(derivedMethod.getModifiers())
                : !baseMethod.equals(derivedMethod);
    }

    /**
     * <p>Retrieves a method whether or not it's accessible. If no such method
     * can be found, return {@code null}.</p>
     *
     * @param cls            The class that will be subjected to the method search
     * @param methodName     The method that we wish to call
     * @param parameterTypes Argument class types
     * @return The method
     */
    static Method getMethodImpl(final Class<?> cls, final String methodName,
                                final Class<?>... parameterTypes) {
        Validate.notNull(cls, "Null class not allowed.");
        Validate.notEmpty(methodName, "Null or blank methodName not allowed.");

        // fast path, check if directly declared on the class itself
        for (final Method method : cls.getDeclaredMethods()) {
            if (methodName.equals(method.getName()) &&
                    Arrays.equals(parameterTypes, method.getParameterTypes())) {
                return method;
            }
        }
        if (!cls.isInterface()) {
            // ok, now check if directly implemented on a superclass
            // Java 8: note that super-interface implementations trump default methods
            for (Class<?> klass = cls.getSuperclass(); klass != null; klass = klass.getSuperclass()) {
                for (final Method method : klass.getDeclaredMethods()) {
                    if (methodName.equals(method.getName()) &&
                            Arrays.equals(parameterTypes, method.getParameterTypes())) {
                        return method;
                    }
                }
            }
        }
        // ok, now we are looking for an interface method... the most specific one
        // in the event that we have two unrelated interfaces both declaring a method of the same name
        // we will give up and say we could not find the method (the logic here is that we are primarily
        // checking for overrides, in the event of a Java 8 default method, that default only
        // applies if there is no conflict from an unrelated interface... thus if there are
        // default methods and they are unrelated then they don't exist... if there are multiple unrelated
        // abstract methods... well they won't count as a non-abstract implementation
        Method res = null;
        for (final Class<?> klass : (List<Class<?>>)ClassUtils.getAllInterfaces(cls)) {
            for (final Method method : klass.getDeclaredMethods()) {
                if (methodName.equals(method.getName()) &&
                        Arrays.equals(parameterTypes, method.getParameterTypes())) {
                    if (res == null) {
                        res = method;
                    } else {
                        Class<?> c = res.getDeclaringClass();
                        if (c == klass) {
                            // match, ignore
                        } else if (c.isAssignableFrom(klass)) {
                            // this is a more specific match
                            res = method;
                        } else if (!klass.isAssignableFrom(c)) {
                            // multiple overlapping interfaces declare this method and there is no common ancestor
                            return null;

                        }
                    }
                }
            }
        }
        return res;
    }
}
