package jenkins.plugins.git.junit.jupiter;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.junit.jupiter.api.extension.ExtendWith;

import jenkins.plugins.git.GitSampleRepoRule;

/**
 * A meta annotation for the {@link GitSampleRepoExtension} to use {@link GitSampleRepoRule} with Junit5.
 * Two possible usages:
 * <p>
 * 1. Class annotation: each method of the class can have the rule injected as a parameter:
 *
 * <blockquote>
 * <pre>
 * &#64;WithGitSampleRepo
 * class ClassLevelAnnotationTest {
 *
 *     &#64;Test
 *     void usingRule(GitSampleRepoRule rule) {
 *         // ...
 *     }
 *
 *     &#64;Test
 *     void notUsingRule() {
 *         // ...
 *     }
 * }
 * </pre>
 * </blockquote>
 * <p>
 * 2. Method annotation: only the annotated method can have the rule injected as a parameter:
 *
 * <blockquote>
 * <pre>
 * class MethodLevelAnnotationTest {
 *
 *     &#64;WithGitSampleRepo
 *     &#64;Test
 *     void usingRule(GitSampleRepoRule rule) {
 *         // ...
 *     }
 *
 *     &#64;Test
 *     void notUsingRule() {
 *         // ...
 *     }
 * }
 * </pre>
 * </blockquote>
 */
@Target({ ElementType.TYPE, ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
@Documented
@ExtendWith(GitSampleRepoExtension.class)
public @interface WithGitSampleRepo {
}
