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

    List<CacheRecord> getMaintenanceRecords(){
        return new LinkedList<>(maintenanceRecords);
    }

    void addRecord(CacheRecord cacheRecord){
        String repoName = cacheRecord.getRepoName();
        Set<String> cacheSet = getCacheSet();
        if(cacheSet.contains(repoName)){
            // adding record to existing cache list
            Iterator<CacheRecord> itr = maintenanceRecords.iterator();

            CacheRecord record;
            while(itr.hasNext()){
                record = itr.next();
                if(record.getRepoName().equals(repoName)){

                    // To not lose data of the first maintenance task
                    if(record.getAllMaintenanceRecordsForSingleCache().size() == 0){
                        CacheRecord oldCacheRecord = new CacheRecord(record);
                        record.insertMaintenanceData(oldCacheRecord);
                    }
                    CacheRecord childCacheRecord = new CacheRecord(cacheRecord);

                    record.insertMaintenanceData(childCacheRecord);

                    // Updates the Top most Cache with fresh data
                    record.setTimeOfExecution(childCacheRecord.timeOfExecution);
                    record.setExecutionStatus(childCacheRecord.getExecutionStatus());
                    record.setRepoSize(childCacheRecord.getRepoSize());
                    record.setMaintenanceType(childCacheRecord.getMaintenanceType());
                    record.setExecutionDuration(childCacheRecord.executionDuration);

                    break;
                }
            }
            return;
        }

        // Creates a new Cache Entry and adds the data.
        maintenanceRecords.addFirst(cacheRecord);
        cacheSet.add(repoName);
    }

    List<CacheRecord> getAllMaintenanceRecordsForSingleCache(String cacheName) {
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

    Set<String> getCacheSet(){
        return this.cacheSet;
    }
}
