# Overview
The Jenkins verify-status-reporter plugin allows Jenkins to publish test reports
to Gerrit instances that are using the [Gerrit verify-status plugin].

# Reqiurements
 * Gerrit ver 2.13+
 * Jenkins ver 1.625.3+
 * Jenkins [Gerrit Trigger Plugin] ver 2.20.0+

# Quick Start Guide
This is a quick start guide on how to quickly install and configure Gerrit and
Jenkins so that Jenkins can report job results to Gerrit.

_NOTE_: This Guide is meant for the impatient hacker who doesn't want to read
an endless streams of documentation and the developers who do don't want to
write it :)  

## Install Gerrit verify-status plugin
  * Setup database connection info in gerrit.config
```
[plugin "verify-status"]
  dbType = h2
  database = /home/joe/gerrit_testsite/db/CiDB
```
  * Copy the verify-status.jar file into the Gerrit $site/plugins directory
  * Stop Gerrit
  * Run `java -jar gerrit.war init -d $site` to [initialize the database]
  * Start Gerrit
  * Create a 'jenkins' user in Gerrit.
  * [Configure access controls] for the 'Non-Interactive Users' group
```
Projects -> List -> All-Projects -> Access
Edit
Global Capabilities -> Add Permission ->  Save Verification Report -> Add Group
Allow: Non-Interactive Users
Global Capabilities -> Add Permission ->  Stream Events -> Add Group
Allow: Non-Interactive Users
Save settings
```
  * Add the 'Jenkins' user to the 'Non-Interactive Users' group.
```
Gerrit -> People -> List Groups -> Non-Interactive Users
Add 'Jenkins' user to the group
```  
  * Create an http password for the 'Jenkins' user.
```
Settings -> HTTP Password -> Generate Password
```  

## Install Jenkins verify-status-reporter plugin
  * Install the Jenkins Gerrit trigger plugin using the Jenkins plugin manager.
  * Install this plugin.
  * Configure the Gerrit trigger global config to connect to a Gerrit instance.
```
Jenkins -> Manage Jenkins -> Gerrit Trigger
```
  * Configure it to use the [Gerrit Rest API]. The config is under the 'Advanced' setting.
  * Configure Jenkins Global configuration to [enable environment variables].
```
Jenkins -> Manage Jenkins -> Configure System -> Global properties
Enable Environment variables
Save settings
```
  * Create a new Jenkins job
  * Setup a Gerrit trigger event and set all of the paramters to [Human readable]
  * Add a [regular expression trigger] event.  Make it trigger on 'recheck'.
  * Configure the Gerrit project settings.
  * Add a 'Post a verification to Gerrit' [post-build action].
  * Set the Rerun Comment to 'recheck', you can leave the other [verification parameters] blank.
  * Save the job.


_Note_: The job abstain and value data are automatically determined:
* abstain is true if the Gerrit Trigger is set to SilentMode or any SkipVote parameter is enable.
* value is set to +1 for pass, -1 for fail and 0 for unstable result.

## Testing
  * Login into Gerrit with any user.
  * View any patchset.
  * Reply to a patchset with a 'recheck' message (this should auotmatically kick off a jenkins build).
  * After Jenkins has completed running the build it will send a verification report to Gerrit.
  * The report should now appear on the Gerrit UI (on the replied to patchset).

## Debugging
  * Check the Gerrit logs and Jenkins logs to see if there are any errors
  * Communication between Gerrit and Jenkins are done using SSH and REST APIs so make sure the security is setup so that one server can communicate with the other.

[Gerrit Trigger Plugin]: https://wiki.jenkins-ci.org/display/JENKINS/Gerrit+Trigger
[Gerrit verify-status plugin]: https://gerrit.googlesource.com/plugins/verify-status/+/master/src/main/resources/Documentation/about.md
[initialize the database]: https://gerrit.googlesource.com/plugins/verify-status/+/master/src/main/resources/Documentation/database.md
[Configure access controls]: http://imgur.com/fs4jEJu
[Gerrit Rest API]: http://imgur.com/hRo40Vo
[regular expression trigger]: http://imgur.com/VaZTEO6
[post-build action]: http://imgur.com/EXMhHal
[enable environment variables]: http://imgur.com/sDWN5J3
[Configure access controls]: http://imgur.com/fs4jEJu
[verification parameters]: http://imgur.com/u1iwCBm
[Human readable]: https://imgur.com/a/h8B4z