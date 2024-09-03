/*
 See the documentation for more options:
 https://github.com/jenkins-infra/pipeline-library/
*/
buildPlugin(
  forkCount: '1C', // Run a JVM per core in tests
  // we use Docker for containerized tests
  useContainerAgent: false,
  // because Apache Mina Snapshot
  useArtifactCachingProxy: false,
  configurations: [
    [platform: 'linux', jdk: 21, jenkins: '2.462'],
    [platform: 'windows', jdk: 17],
])
