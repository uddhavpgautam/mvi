// Jenkins Job sample:
// http://njmd-069j1gc.prod-am.ameritrade.com:8080/job/iOS-Master/
//ANDROID_BRANCH is set in jenkins job
//

node {
	env.XPLAT_BRANCH = "feature/transition_hub"
	env.APP_VERSION = "5.5.8"
	// The starting date of git log to start release note
	env.RELEASE_NOTES_DATE = "02/09/2022"
	// Toggle flags:
	env.APPCENTER_UPLOAD = "true"
	env.CODE_COVERAGE_ENABLED = "false"
}
