package jenkins.plugins.git.maintenance.Logs;

import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

public class RecordList {
    LinkedList<CacheRecord> maintenanceRecords;
    Set<String> cacheSet;

    public RecordList(){
        maintenanceRecords = new LinkedList<>();
        cacheSet = new HashSet<>();
    }

    public List<CacheRecord> getMaintenanceRecords(){
        return new LinkedList<>(maintenanceRecords);
    }

    public void addRecord(CacheRecord cacheRecord){
        String repoName = cacheRecord.getRepoName();
        if(cacheSet.contains(repoName)){
            // need to add cache to existing record
            Iterator<CacheRecord> itr = maintenanceRecords.iterator();

            CacheRecord record;
            while(itr.hasNext()){
                record = itr.next();
                if(record.getRepoName().equals(repoName)){
                    itr.remove();
                    CacheRecord childCacheRecord = new CacheRecord(cacheRecord);

                    record.insertMaintenanceData(childCacheRecord);

                    // Updates the Top most Cache with fresh data
                    record.setTimeOfExecution(childCacheRecord.timeOfExecution);
                    record.setExecutionStatus(childCacheRecord.getExecutionStatus());
                    record.setRepoSize(childCacheRecord.getRepoSize());
                    record.setMaintenanceType(childCacheRecord.getMaintenanceType());
                    record.setExecutionDuration(childCacheRecord.executionDuration);

                    // Adds the latest cache to the top of the list
                    maintenanceRecords.addFirst(record);

                    break;
                }
            }
            return;
        }

        // Creates a new Cache Entry and adds the data.
        maintenanceRecords.addFirst(cacheRecord);
        cacheSet.add(repoName);
    }

    public List<CacheRecord> getAllMaintenanceRecordsForSingleCache(String cacheName) {
        List<CacheRecord> allRecords = null;

        Iterator<CacheRecord> itr = maintenanceRecords.iterator();

        CacheRecord record;
        while(itr.hasNext()){
            record = itr.next();
            if(record.getRepoName().equals(cacheName)){
                allRecords = record.getAllMaintenanceRecordsForSingleCache();
                break;
            }
        }

        return allRecords;
    }
}
