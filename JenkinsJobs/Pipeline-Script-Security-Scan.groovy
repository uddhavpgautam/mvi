node {
    try {
        echo "Current Workspace is:  ${WORKSPACE}"

        stage('Clean workspace') {
            echo 'Cleaning existing directories'
            sh 'if [ -d "tdamobile-xplat-core" ]; then rm -Rf tdamobile-xplat-core; fi'
            sh 'if [ -d "tdamobile-android" ]; then rm -Rf tdamobile-android; fi'
        }

        stage('Setup Source') {
            echo "Using Android branch: ${env.ANDROID_BRANCH}"
            checkout([$class: 'GitSCM', 
                branches: [[name: env.ANDROID_BRANCH]], 
                doGenerateSubmoduleConfigurations: false, 
                extensions: [[$class: 'RelativeTargetDirectory', relativeTargetDir: 'tdamobile-android']], 
                submoduleCfg: [], 
                userRemoteConfigs: [[url: 'ssh://git@bitbucket.associatesys.local/tma/tdamobile-android.git']]])

            dir('tdamobile-android') {
                echo 'Loading build information'
                load('JenkinsJobs/Pipeline-Variables.groovy')
            }

            echo "Using Xplat branch: ${env.XPLAT_BRANCH}"
            checkout([$class: 'GitSCM', 
                branches: [[name: env.XPLAT_BRANCH]], 
                doGenerateSubmoduleConfigurations: false, 
                extensions: [[$class: 'RelativeTargetDirectory', relativeTargetDir: 'tdamobile-xplat-core']], 
                submoduleCfg: [], 
                userRemoteConfigs: [[url: 'ssh://git@bitbucket.associatesys.local/tma/tdamobile-xplat-core.git']]])
        }

        stage('Package Sources') {
            // this will build the APK and package everything.
            sh './gradlew packageSourceRetailRelease'
        }

        stage('Publish for security scan') {
            cifsPublisher(publishers: [[configName: 'TDAMobileSecurityScan',
                                        transfers: [[cleanRemote: false,
                                                     flatten: false,
                                                     makeEmptyDirs: false,
                                                     noDefaultExcludes: false,
                                                     remoteDirectory: "'TDA Mobile Android/${env.ANDROID_BRANCH}/Jenkins-'yyyy-MM-dd''",
                                                     remoteDirectorySDF: true,
                                                     sourceFiles: 'sourceCode-TDAMobileR3.zip']],
                                        usePromotionTimestamp: false,
                                        useWorkspaceInPromotion: false,
                                        verbose: false]])
        }

    } catch (e) {
        // If there was an exception thrown, the build failed
        currentBuild.result = "FAILED"
        throw e
    }
}
