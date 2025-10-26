package jenkins.plugins.git.maintenance;

/**
 * TaskType describes the type of maintenance task. There are 5 types of maintenance tasks. They are-
 * <ul>
 *     <li>Prefetch</li>
 *     <li>Garbage Collection</li>
 *     <li>Commit Graph</li>
 *     <li>Loose Objects</li>
 *     <li>Incremental Repack</li>
 * </ul>
 *
 * @author Hrushikesh Rao
 */
public enum TaskType {
        GC("Garbage Collection"),
        PREFETCH("Prefetch"),
        COMMIT_GRAPH("Commit Graph"),
        LOOSE_OBJECTS("Loose Objects"),
        INCREMENTAL_REPACK("Incremental Repack");

        String taskName;

        /**
         *
         * @param taskName Assign a name for maintenance task.
         */
        TaskType(String taskName){
                this.taskName = taskName;
        }

        /**
         *
         * @return name of the maintenance task.
         */
        public String getTaskName(){
                return this.taskName;
        }
}
