package jenkins.plugins.git.maintenance.Logs;

import jenkins.plugins.git.maintenance.TaskType;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.MatcherAssert.assertThat;

public class RecordListTest {

    RecordList recordList;
    @Before
    public void setUp() throws Exception {
       recordList = new RecordList();
    }

    @Test
    public void testGetAllMaintenanceRecords() {
       assertThat(recordList.getMaintenanceRecords(),instanceOf(List.class));
    }

    @Test
    public void testAddRecord() {
        CacheRecord record = new CacheRecord("git-plugin", TaskType.GC.getTaskName());
        recordList.addRecord(record);



    }

}
