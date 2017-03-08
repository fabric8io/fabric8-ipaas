#!/usr/bin/groovy
def imagesBuiltByPipline() {
  return ['apiman','apiman-gateway','elasticsearch-v1','message-broker','message-gateway','example-message-consumer','example-message-producer','kafka','zookeeper']
}

def repo(){
 return 'fabric8io/fabric8-ipaas'
}

def stage(){
  return stageProject{
    project = repo()
    useGitTagForNextVersion = true
  }
}

def approveRelease(project){
  def releaseVersion = project[1]
  approve{
    room = null
    version = releaseVersion
    console = null
    environment = 'fabric8'
  }
}

def updateDownstreamDependencies(stagedProject) {
  pushPomPropertyChangePR {
    propertyName = 'fabric8-ipaas.version'
    projects = [
            'fabric8io/fabric8-maven-dependencies'
    ]
    version = stagedProject[1]
  }
  pushPomPropertyChangePR {
    propertyName = 'fabric8.ipaas.version'
    projects = [
            'fabric8io/fabric8-platform',
            'fabric8io/ipaas-platform'
    ]
    version = stagedProject[1]
  }
}

def release(project){
  releaseProject{
    stagedProject = project
    useGitTagForNextVersion = true
    helmPush = false
    groupId = 'io.fabric8.ipaas.distro'
    githubOrganisation = 'fabric8io'
    artifactIdToWatchInCentral = 'distro'
    artifactExtensionToWatchInCentral = 'pom'
    promoteToDockerRegistry = 'docker.io'
    dockerOrganisation = 'fabric8'
    imagesToPromoteToDockerHub = imagesBuiltByPipline()
  }
}

def mergePullRequest(prId){
  mergeAndWaitForPullRequest{
    project = repo()
    pullRequestId = prId
  }

}
return this;
