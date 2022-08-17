package jenkins.plugins.git.maintenance.Logs;

public class Record {
    String repoName;
    long repoSize;
    String maintenanceType;
    int prevExecution;
    boolean executionStatus;
    long executionTime;

    // Can add space saved.

    public Record(String repoName,String maintenanceType){
        this.repoName = repoName;
        this.maintenanceType = maintenanceType;
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

    public int getPrevExecution() {
        return prevExecution;
    }

    public void setPrevExecution(int prevExecution) {
        this.prevExecution = prevExecution;
    }

    public boolean getExecutionStatus() {
        return executionStatus;
    }

    public void setExecutionStatus(boolean executionStatus) {
        this.executionStatus = executionStatus;
    }

    public long getExecutionTime() {
        return executionTime;
    }

    public void setExecutionTime(long executionTime) {
        this.executionTime = executionTime;
    }
}
