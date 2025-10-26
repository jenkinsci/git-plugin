package jenkins.plugins.git.maintenance;

import antlr.ANTLRException;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.jvnet.hudson.test.JenkinsRule;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(Parameterized.class)
public class ParameterizedCronSyntaxTest {

    @ClassRule
    public static JenkinsRule j = new JenkinsRule();
    String cronSyntax;
    boolean isValid;

    TaskScheduler taskScheduler;

    public ParameterizedCronSyntaxTest(String cronSyntax,boolean isValid){
        this.cronSyntax = cronSyntax;
        this.isValid = isValid;
        taskScheduler = new TaskScheduler();

    }

    @Parameterized.Parameters(name = "{0}")
    public static Collection permuteCronSyntax(){
       List<Object[]> crons = new ArrayList<>();
       // valid cron syntax
       crons.add(new Object[]{"H * * * *",true});
       crons.add(new Object[]{"* * * * *", true});
       crons.add(new Object[]{"@hourly",true});
       crons.add(new Object[]{"@weekly",true});
       crons.add(new Object[]{"@daily",true});
       crons.add(new Object[]{"H H 1,15 1-11 *",true});

       // invalid cron syntax;
        crons.add(new Object[]{"",false});
        crons.add(new Object[]{"**", false});
        crons.add(new Object[]{"60 1 1 1 1",false});
        crons.add(new Object[]{"1 1 1 1 9",false});
        crons.add(new Object[]{"1 24 32 11 5",false});
        crons.add(new Object[]{"",false});
        return crons;
    }

    @Test
    public void testCorrectAndIncorrectSyntaxInput(){
        try {
            assertNotNull(taskScheduler.getCronTabList(cronSyntax));
            assertTrue(isValid);
        }catch(ANTLRException e){
            assertFalse(isValid);
        }
    }

}
