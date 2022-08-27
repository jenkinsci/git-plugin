package jenkins.plugins.git.maintenance.Logs;

import jenkins.plugins.git.maintenance.TaskType;
import org.junit.Before;
import org.junit.Test;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class CacheRecordTest {

    CacheRecord record;

    @Before
    public void setUp() throws Exception {
        record = new CacheRecord("git-plugin", TaskType.GC.getTaskName());
    }

    @Test
    public void testGetRepoName(){
        assertEquals("git-plugin",record.getRepoName());
    }

    @Test
    public void testSetRepoSize(){
        record.setRepoSize("5MB");
        assertEquals(record.getRepoSize(),"5MB");
    }

    @Test
    public void testSetMaintenanceType(){
        assertEquals(record.getMaintenanceType(),TaskType.GC.getTaskName());

        record.setMaintenanceType(TaskType.PREFETCH.getTaskName());
        assertEquals(record.getMaintenanceType(),TaskType.PREFETCH.getTaskName());
    }

    @Test
    public void testSetTimeOfExecution(){
        long timeOfExecution = 1661552520;
        record.setTimeOfExecution(timeOfExecution);

        Date date = new Date(timeOfExecution * 1000L);
        SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy HH:mm");
        assertEquals(record.getTimeOfExecution(),sdf.format(date));
    }

    @Test
    public void testSetExecutionStatus(){
        record.setExecutionStatus(false);
        assertFalse(record.getExecutionStatus());

        record.setExecutionStatus(true);
        assertTrue(record.getExecutionStatus());
    }

    @Test
    public void testSetExecutionDuration(){
        long duration = System.currentTimeMillis();
        record.setExecutionDuration(duration);
        assertEquals(record.getExecutionDuration(),duration);
    }

    @Test
    public void testInsertMaintenanceData(){
        // Can add other metadata to these caches.
        CacheRecord gcCacheRecord = new CacheRecord("git-plugin",TaskType.GC.getTaskName());
        CacheRecord prefetchRecord = new CacheRecord("git-plugin",TaskType.PREFETCH.getTaskName());
        CacheRecord commitGraphRecord = new CacheRecord("git-plugin",TaskType.COMMIT_GRAPH.getTaskName());
        CacheRecord incrementalRepackRecord = new CacheRecord("git-plugin",TaskType.INCREMENTAL_REPACK.getTaskName());
        CacheRecord looseObjectsRecord = new CacheRecord("git-plugin",TaskType.LOOSE_OBJECTS.getTaskName());

        record.insertMaintenanceData(gcCacheRecord);
        record.insertMaintenanceData(prefetchRecord);
        record.insertMaintenanceData(commitGraphRecord);
        record.insertMaintenanceData(incrementalRepackRecord);
        record.insertMaintenanceData(looseObjectsRecord);

        for(Map.Entry<String, LinkedList<CacheRecord>> entry : record.maintenanceData.entrySet()){
            assertEquals(entry.getValue().size(),1);
        }
    }

    @Test
    public void getAllMaintenanceRecordsForSingleCache(){
        CacheRecord gcCacheRecord = new CacheRecord("git-plugin",TaskType.GC.getTaskName());
        CacheRecord prefetchRecord = new CacheRecord("git-plugin",TaskType.PREFETCH.getTaskName());
        CacheRecord commitGraphRecord = new CacheRecord("git-plugin",TaskType.COMMIT_GRAPH.getTaskName());
        CacheRecord incrementalRepackRecord = new CacheRecord("git-plugin",TaskType.INCREMENTAL_REPACK.getTaskName());
        CacheRecord looseObjectsRecord = new CacheRecord("git-plugin",TaskType.LOOSE_OBJECTS.getTaskName());

        // set the TimeofExecution for each cache
        gcCacheRecord.setTimeOfExecution(1661552520);
        prefetchRecord.setTimeOfExecution(1661553520);
        commitGraphRecord.setTimeOfExecution(1661552120);
        incrementalRepackRecord.setTimeOfExecution(1661572520);
        looseObjectsRecord.setTimeOfExecution(1661512520);

        record.insertMaintenanceData(gcCacheRecord);
        record.insertMaintenanceData(prefetchRecord);
        record.insertMaintenanceData(commitGraphRecord);
        record.insertMaintenanceData(incrementalRepackRecord);
        record.insertMaintenanceData(looseObjectsRecord);

        // checking if the data received is in sorted manner or not.
        boolean isSorted = true;

        List<CacheRecord> cacheRecords = record.getAllMaintenanceRecordsForSingleCache();

        for(int i=1;i<cacheRecords.size();i++){
            if(cacheRecords.get(i).timeOfExecution > cacheRecords.get(i-1).timeOfExecution) {
                isSorted = false;
                break;
            }
        }

        assertTrue(isSorted);
    }

    @Test
    public void copyCacheRecord(){
        long duration = System.currentTimeMillis();
        long timeOfExecution = 1661552520;

        record.setExecutionDuration(duration);
        record.setExecutionStatus(true);
        record.setTimeOfExecution(timeOfExecution);
        record.setRepoSize("5MB");
        CacheRecord copyCacheRecord = new CacheRecord(record);

        assertEquals(copyCacheRecord.getExecutionDuration(),duration);
        assertTrue(record.getExecutionStatus());
        assertEquals(copyCacheRecord.getMaintenanceType(),TaskType.GC.getTaskName());
        assertEquals(copyCacheRecord.getRepoSize(),"5MB");


        Date date = new Date(timeOfExecution * 1000L);
        SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy HH:mm");
        assertEquals(copyCacheRecord.getTimeOfExecution(),sdf.format(date));
    }



}
