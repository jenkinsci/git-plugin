package jenkins.plugins.git.maintenance;

public enum TaskType {
        GC("Gc"),
        PREFETCH("Prefetch"),
        COMMIT_GRAPH("Commit Graph"),
        LOOSE_OBJECTS("Loose Objects"),
        INCREMENTAL_REPACK("Incremental Repack");

        String taskName;
        TaskType(String taskName){
                this.taskName = taskName;
        }

        public String getTaskName(){
                return this.taskName;
        }
}
