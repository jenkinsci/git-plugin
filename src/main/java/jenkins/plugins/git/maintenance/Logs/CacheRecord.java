package jenkins.plugins.git.maintenance.Logs;

import jenkins.plugins.git.maintenance.TaskType;
import org.apache.commons.lang.time.DurationFormatUtils;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class CacheRecord {
    String repoName;
    long repoSize;
    String maintenanceType;
    long timeOfExecution;
    boolean executionStatus;
    long executionDuration;

    Map<String,List<CacheRecord>> maintenanceData;


    // This is to create a new Cache Record when cache is not present.
    public CacheRecord(String repoName,String maintenanceType){
        this.repoName = repoName;
        this.maintenanceType = maintenanceType;
        maintenanceData = new HashMap<>();

        for(TaskType taskType : TaskType.values()){
            maintenanceData.put(taskType.getTaskName(),new LinkedList<>());
        }
    }

    // This is to add maintenance data to existing Cache Record
    public CacheRecord(CacheRecord cacheRecord){
        setExecutionDuration(cacheRecord.executionDuration);
        setExecutionStatus(cacheRecord.getExecutionStatus());
        setRepoSize(cacheRecord.getRepoSize());
        setTimeOfExecution(cacheRecord.timeOfExecution);
        setMaintenanceType(cacheRecord.getMaintenanceType());
    }


    public String getRepoName() {
        return repoName;
    }

    public long getRepoSize() {
        return repoSize;
    }
    public void setRepoSize(long repoSize) {
        this.repoSize = repoSize;
    }

    public String getMaintenanceType() {
        return maintenanceType;
    }

    public void setMaintenanceType(String maintenanceType){
        this.maintenanceType = maintenanceType;
    }

    public String getTimeOfExecution() {
        Date date = new Date(timeOfExecution * 1000L);
        SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy HH:mm");
        return sdf.format(date);
    }

    public void setTimeOfExecution(long timeOfExecution) {
        this.timeOfExecution = timeOfExecution;
    }

    public boolean getExecutionStatus() {
        return executionStatus;
    }

    public void setExecutionStatus(boolean executionStatus) {
        this.executionStatus = executionStatus;
    }

    public String getExecutionDuration() {
        return DurationFormatUtils.formatDuration(executionDuration,"mmm:ss:SSS");
    }

    public void setExecutionDuration(long executionDuration) {
        this.executionDuration = executionDuration;
    }

    public void insertMaintenanceData(CacheRecord record){
        if(record != null && maintenanceData != null) {
            List<CacheRecord> list = maintenanceData.get(record.getMaintenanceType());
            if(list != null) {
                list.add(0,record);
                // Maximum storage of 5 Maintenance Records per Cache.
                if (list.size() > 5)
                    list.remove(list.size()-1);
            }
        }
    }

    public List<CacheRecord> getAllMaintenanceRecordsForSingleCache(){
        List<CacheRecord> maintenanceData = new ArrayList<>();

        for(Map.Entry<String,List<CacheRecord>> entry : this.maintenanceData.entrySet()){
            maintenanceData.addAll(entry.getValue());
        }

        Collections.sort(maintenanceData,(o1,o2) -> (int) (o2.timeOfExecution - o1.timeOfExecution));

        return maintenanceData;
    }

}
