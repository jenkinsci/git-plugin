package hudson.plugins.git;

import org.apache.commons.lang.StringUtils;
import org.junit.*;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;
import org.junit.runners.BlockJUnit4ClassRunner;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;

import static com.google.common.collect.Sets.newHashSet;
import static org.apache.commons.lang.ClassUtils.getAllSuperclasses;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.Assert.assertThat;

@RunWith(BlockJUnit4ClassRunner.class)
public abstract class JUnit4SCMTriggerTestAdapter extends SCMTriggerTest {

    private static final String JUNIT_3_TEST_PREFIX = "test";

    @Rule
    public TestName name = new TestName();

    @Override
    public String getName() {
        return name.getMethodName();
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
    @Test
    public void testNamespaces_with_master() throws Exception {
        super.testNamespaces_with_master();
    }

    @Override
    @Test
    public void testNamespaces_with_namespace2Master() throws Exception {
        super.testNamespaces_with_namespace2Master();
    }

    @Override
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
        List<Class<?>> allSuperclasses = getAllSuperclasses(this.getClass());

        Collection<String> testsThatMustBeOverridden = newHashSet();
        Collection<String> testsInThisClassThatStartWithPrefix = newHashSet();

        Method[] theseMethods = JUnit4SCMTriggerTestAdapter.class.getDeclaredMethods();
        addTestMethodsToList(testsInThisClassThatStartWithPrefix, theseMethods);


        for (Class<?> superClass : allSuperclasses) {
            Method[] superMethods = superClass.getMethods();
            addTestMethodsToList(testsThatMustBeOverridden, superMethods);
        }

        String[] testsThatAreOverridden = testsInThisClassThatStartWithPrefix.toArray(new String[testsInThisClassThatStartWithPrefix.size()]);
        assertThat( testsThatMustBeOverridden, containsInAnyOrder( testsThatAreOverridden ));

    }

    private void addTestMethodsToList(final Collection<String> targetCollection, Method[] methods) {
        for (Method method : methods) {
            if(StringUtils.startsWith(method.getName(), JUNIT_3_TEST_PREFIX)){
                targetCollection.add(method.getName());
            }
        }
    }
}
