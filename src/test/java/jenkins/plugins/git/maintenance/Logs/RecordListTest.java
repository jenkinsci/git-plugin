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

    String cacheName = "git-plugin";
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
        // Testing when adding the first cache record
        CacheRecord record = new CacheRecord(cacheName, TaskType.GC.getTaskName());
        record.setExecutionStatus(true);
        recordList.addRecord(record);
        assertEquals(1,recordList.getMaintenanceRecords().size());
        assertTrue(recordList.getCacheSet().contains(cacheName));

        // Testing the head of the List.
        CacheRecord headRecord = recordList.getMaintenanceRecords().get(0);
        assertEquals(TaskType.GC.getTaskName(),headRecord.getMaintenanceType());
        assertEquals(cacheName,headRecord.getRepoName());
        assertTrue(headRecord.getExecutionStatus());


        // Testing when adding more records for same cache
        CacheRecord gcCacheRecord = new CacheRecord(cacheName,TaskType.GC.getTaskName());
        CacheRecord prefetchRecord = new CacheRecord(cacheName,TaskType.PREFETCH.getTaskName());
        CacheRecord commitGraphRecord = new CacheRecord(cacheName,TaskType.COMMIT_GRAPH.getTaskName());
        CacheRecord incrementalRepackRecord = new CacheRecord(cacheName,TaskType.INCREMENTAL_REPACK.getTaskName());
        CacheRecord looseObjectsRecord = new CacheRecord(cacheName,TaskType.LOOSE_OBJECTS.getTaskName());
        looseObjectsRecord.setExecutionStatus(false);

        recordList.addRecord(gcCacheRecord);
        recordList.addRecord(prefetchRecord);
        recordList.addRecord(commitGraphRecord);
        recordList.addRecord(incrementalRepackRecord);
        recordList.addRecord(looseObjectsRecord);

        assertEquals(1,recordList.getMaintenanceRecords().size());
        assertTrue(recordList.getCacheSet().contains(cacheName));

        // Here the head of the List Cache data will be updated with the latest maintenance data.
        headRecord = recordList.getMaintenanceRecords().get(0);
        assertEquals(cacheName,headRecord.getRepoName());
        assertEquals(TaskType.LOOSE_OBJECTS.getTaskName(),headRecord.getMaintenanceType());
        assertFalse(headRecord.getExecutionStatus());
    }

    @Test
    public void testGetAllMaintenanceRecordsForSingleCache(){
        // Adding record for new Cache
        CacheRecord record = new CacheRecord(cacheName, TaskType.GC.getTaskName());
        record.setTimeOfExecution(1661593520);
        recordList.addRecord(record);

        CacheRecord gcCacheRecord = new CacheRecord(cacheName,TaskType.GC.getTaskName());
        CacheRecord prefetchRecord = new CacheRecord(cacheName,TaskType.PREFETCH.getTaskName());
        CacheRecord commitGraphRecord = new CacheRecord(cacheName,TaskType.COMMIT_GRAPH.getTaskName());
        CacheRecord incrementalRepackRecord = new CacheRecord(cacheName,TaskType.INCREMENTAL_REPACK.getTaskName());
        CacheRecord looseObjectsRecord = new CacheRecord(cacheName,TaskType.LOOSE_OBJECTS.getTaskName());

        // set the TimeofExecution for each cache
        gcCacheRecord.setTimeOfExecution(1661552520);
        prefetchRecord.setTimeOfExecution(1661553520);
        commitGraphRecord.setTimeOfExecution(1661552120);
        incrementalRepackRecord.setTimeOfExecution(1661572520);
        looseObjectsRecord.setTimeOfExecution(1661512520);

        recordList.addRecord(gcCacheRecord);
        recordList.addRecord(prefetchRecord);
        recordList.addRecord(commitGraphRecord);
        recordList.addRecord(incrementalRepackRecord);
        recordList.addRecord(looseObjectsRecord);

        List<CacheRecord> allMaintenanceRecordsForSingleCache = recordList.getAllMaintenanceRecordsForSingleCache(cacheName);

        assertEquals(6,allMaintenanceRecordsForSingleCache.size());

        boolean isSorted = true;

        for(int i=1;i<allMaintenanceRecordsForSingleCache.size();i++){
            if(allMaintenanceRecordsForSingleCache.get(i).timeOfExecution > allMaintenanceRecordsForSingleCache.get(i).timeOfExecution){
                isSorted = false;
                break;
            }
        }

        assertTrue(isSorted);
    }

}
