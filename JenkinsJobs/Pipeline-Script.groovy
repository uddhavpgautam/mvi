// Pipeline Script for Jenkins
// Remember to set your variables in Pipeline-Variables.groovy!

// import com.amtd.SlackConfig
import com.amtd.Utils

// def config = new SlackConfig(channels:"team-mobile-dynamics", token:"0B9MgIp0mlwYy8yR2uyCATPq")
def utils = new Utils()

node {
    try {
	    echo "Current Workspace is:  ${WORKSPACE}"
        // notifySlack(config, 'STARTED')

    	def FLAVOR = env.FLAVOR ?: 'retail'
    	def BUILD_TYPE = env.BUILD_TYPE ?: 'debug'
		def APP_NAME = env.APP_NAME ?: ''
		def SET_GROUPS = env.SET_GROUPS ?: ''
		def ADD_GROUPS = env.ADD_GROUPS ?: ''

		if(APP_NAME == "") {
			echo "Error:  APP_NAME not defined!"
			currentBuild.result = 'FAILURE'
			return
		}

		stage('Clean workspace') {
		    if (env.CLEAN_BUILD == "true") {
				echo 'Cleaning existing directories'
				sh 'if [ -d "tdamobile-xplat-core" ]; then rm -Rf tdamobile-xplat-core; fi'
				sh 'if [ -d "tdamobile-android" ]; then rm -Rf tdamobile-android; fi'
			}
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

        VARIANT = utils.capitalize(FLAVOR) + utils.capitalize(BUILD_TYPE)
		echo "Building variant: ${env.VARIANT} (${FLAVOR} ${BUILD_TYPE})"

	    stage('Test Xplat') {
	    	dir('tdamobile-xplat-core') {
				if (env.CODE_COVERAGE_ENABLED == "true") {
					sh 'make clean test_cpp_coverage'
					sh 'cp -Rf build/cpp_test_report.xml ../cpp_test_report.xml'
					dir('scripts') {
						sh './line_count.sh'
					}
					sh 'cp -Rf linecount-library.sc ../linecount-xplat.sc'
				} else {
					sh 'make clean test_cpp'	
				}
			}
	    }

	    stage('Build Xplat for Android') {
			dir('tdamobile-xplat-core') {
				sh 'make clean regen'
			}
		}

	    stage('Test Android') {
	    	dir('tdamobile-android') {
	    		echo "Building testing with coverage: ${env.CODE_COVERAGE_ENABLED}"

				//eventually running fix_assert_crash.sh should be removed once djinni officially gets fixed
				//remote Jenkins DEBUG fix
				dir('scripts') {
					sh './fix_assert_crash.sh'
				}

				if (env.CODE_COVERAGE_ENABLED == "true") {
	                sh "./gradlew TDAMobileR3::lineCount -DAPP=phone"
	                sh "./gradlew TDAChart::createDebugCoverageReport"
	                sh "./gradlew TDACommon::createDebugCoverageReport"
	                sh "./gradlew TDAMobileR3::jacoco${utils.capitalize(FLAVOR)}DebugTestReport"
				} else {
	                sh "./gradlew TDAChart::testDebugUnitTest"
	                sh "./gradlew TDACommon::testDebugUnitTest"
	                sh "./gradlew TDAMobileR3::test${utils.capitalize(FLAVOR)}DebugUnitTest"
				}
		    }
		}

		stage('Generate Lint Report') {
			sh 'if [ ! -d "AndroidLintReports" ]; then mkdir -p "AndroidLintReports"; fi'
			dir('tdamobile-android') {
				sh "./gradlew TDAMobileR3::lint${VARIANT}"
				sh "cp TDAMobileR3/build/reports/lint-results-${VARIANT}.xml ../AndroidLintReports/lint-results.xml"
				publishHTML([allowMissing: false, alwaysLinkToLastBuild: true, keepAll: true, reportDir: 'TDAMobileR3/build/reports', reportFiles: "lint-results-${VARIANT}.html", reportName: 'Android Lint Report', reportTitles: 'Android Lint Report'])
			}
			recordIssues(tools: [androidLintParser(pattern: '**/AndroidLintReports/lint-results.xml')])
		}

		stage('Build Android') {
            dir('tdamobile-android') {
				sh "./gradlew TDAMobileR3::assemble${VARIANT}"
			}
		}

        stage('Upload to AppCenter') {
            if(env.APPCENTER_UPLOAD == "true") {
	            dir('tdamobile-android') {
					def PARAMS = getParams(APP_NAME, env.RELEASE_NOTES_DATE, SET_GROUPS, ADD_GROUPS)
					sh "./gradlew TDAMobileR3::appCenterUpload${VARIANT} ${PARAMS}"
				}
            } else {
                echo "Skipping upload to App Center"
            }
        }

	} catch (e) {
		// If there was an exception thrown, the build failed
		currentBuild.result = "FAILED"
		throw e
    } finally {
        // Success or failure, always send notifications
        // notifySlack(config, currentBuild.result)
	}
}

static def getParams(appName, notesDate, setGroups, addGroups) {
	def params = "-Dappname=\"${appName}\" -DnotesFrom=\"${notesDate}\""
	if(setGroups != '') {
		params += " -Dusegroups=\"${setGroups}\""
	}
	if(addGroups != '') {
		params += " -Daddgroups=\"${addGroups}\""
	}
	params
}
