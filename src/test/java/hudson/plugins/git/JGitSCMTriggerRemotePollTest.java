package hudson.plugins.git;

import com.google.common.collect.Sets;
import hudson.plugins.git.extensions.GitClientType;
import hudson.plugins.git.extensions.impl.EnforceGitClient;
import org.apache.commons.lang.ClassUtils;
import org.apache.commons.lang.StringUtils;
import org.junit.*;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;
import org.junit.runners.BlockJUnit4ClassRunner;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.Assert.assertThat;

@RunWith(BlockJUnit4ClassRunner.class)
public class JGitSCMTriggerRemotePollTest extends SCMTriggerTest
{

    private static final String JUNIT_3_TEST_PREFIX = "test";
    /**
     * Currently some tests still fail due to bugs in productive code.
     * TODO: Fix bugs and enable tests.
     */

    @Rule
    public TestName name = new TestName();

    @Override
    protected EnforceGitClient getGitClient()
    {
        return new EnforceGitClient().set(GitClientType.JGIT);
    }
    
    @Override
    protected boolean isDisableRemotePoll()
    {
        return false;
    }

    @Override
    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
    }

    @Override
    @Ignore
    @Test
    public void testNamespaces_with_master() throws Exception {
        super.testNamespaces_with_master();
    }

    @Override
    @Ignore
    @Test
    public void testNamespaces_with_namespace2Master() throws Exception {
        super.testNamespaces_with_namespace2Master();
    }
    
    @Override
    @Ignore
    @Test
    public void testCommitAsBranchSpec() throws Exception {
        super.testCommitAsBranchSpec();
    }


    @Override
    @Test
    public void testNamespaces_with_refsHeadsMaster() throws Exception {
        super.testNamespaces_with_refsHeadsMaster();
    }

    @Override
    @Test
    public void testNamespaces_with_remotesOriginMaster() throws Exception {
        super.testNamespaces_with_remotesOriginMaster();
    }

    @Override
    @Test
    public void testNamespaces_with_refsRemotesOriginMaster() throws Exception {
        super.testNamespaces_with_refsRemotesOriginMaster();
    }

    @Override
    @Test
    public void testNamespaces_with_namespace1Master() throws Exception {
        super.testNamespaces_with_namespace1Master();
    }

    @Override
    @Test
    public void testNamespaces_with_refsHeadsNamespace1Master() throws Exception {
        super.testNamespaces_with_refsHeadsNamespace1Master();
    }

    @Override
    @Test
    public void testNamespaces_with_refsHeadsNamespace2Master() throws Exception {
        super.testNamespaces_with_refsHeadsNamespace2Master();
    }

    @Override
    @Test
    public void testTags_with_TagA() throws Exception {
        super.testTags_with_TagA();
    }

    @Override
    @Test
    public void testTags_with_TagBAnnotated() throws Exception {
        super.testTags_with_TagBAnnotated();
    }

    @Override
    @Test
    public void testTags_with_refsTagsTagA() throws Exception {
        super.testTags_with_refsTagsTagA();
    }

    @Override
    @Test
    public void testTags_with_refsTagsTagBAnnotated() throws Exception {
        super.testTags_with_refsTagsTagBAnnotated();
    }

    @Test
    public void confirm_all_tests_are_being_overridden() throws Exception {
        @SuppressWarnings(value = "unchecked")
        List<Class<?>> allSuperclasses = ClassUtils.getAllSuperclasses(this.getClass());

        Collection<String> testsThatMustBeOverridden = Sets.newHashSet();
        Collection<String> testsInThisClassThatStartWithPrefix = Sets.newHashSet();

        Method[] theseMethods = this.getClass().getDeclaredMethods();
        addTestMethodsToList(testsInThisClassThatStartWithPrefix, theseMethods);


        for (Class<?> superClass : allSuperclasses) {
            Method[] superMethods = superClass.getMethods();
            addTestMethodsToList(testsThatMustBeOverridden, superMethods);
        }

        String[] testsThatAreOverridden = testsInThisClassThatStartWithPrefix.toArray(new String[testsInThisClassThatStartWithPrefix.size()]);
        assertThat(testsThatMustBeOverridden, containsInAnyOrder(testsThatAreOverridden));

    }

    private void addTestMethodsToList(final Collection<String> targetCollection, Method[] methods) {
        for (Method method : methods) {
            if(StringUtils.startsWith(method.getName(), JUNIT_3_TEST_PREFIX)){
                targetCollection.add(method.getName());
            }
        }
    }

    @Override
    public String getName() {
        return name.getMethodName();
    }
}