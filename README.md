# verify-status
The Jenkins verify-status plugin supports the functionality of the Gerrit verify-status plugin.

# Quick Start Guide
This is a quick start guide on how to setup and configure Gerrit and Jenkins to get a complete CI workflow.

## Install [Gerrit verify-status plugin]
  * Setup database connection info in gerrit.config
```
[plugin "verify-status"]
  dbType = h2
  database = /home/joe/gerrit_testsite/db/CiDB
```
  * Copy the plugin jar file into $site/plugins directory
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
  * Create an http password for the 'Jenkins' user
```
Settings -> HTTP Password -> Generate Password
```  
## Install Jenkins verify status plugin
  * Install the jar file.
  * Configure the Gerrit trigger global config to connect to the Gerrit instance.
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
  * Add a regular expression [Gerrit trigger event].  Make it trigger on 'recheck'.
  * Configure the Gerrit project settings. 
  * Add a 'Post a verification to Gerrit' [post-build action]. You can leave the [verification parameters] blank.
  * Save the job.

## Testing
  * Login into Gerrit with any user.
  * View any change.
  * Reply to a change with a 'recheck' message.
  * After Jenkins has completed running the job it will send a report to Gerrit.
  * The report should now appear on the Gerrit UI.


[Gerrit verify-status plugin]: https://gerrit.googlesource.com/plugins/verify-status/+/master/src/main/resources/Documentation/about.md
[initialize the database]: https://gerrit.googlesource.com/plugins/verify-status/+/master/src/main/resources/Documentation/database.md
[Configure access controls]: http://imgur.com/fs4jEJu
[Gerrit Rest API]: http://imgur.com/hRo40Vo
[Gerrit trigger event]: http://imgur.com/VaZTEO6
[post-build action]: http://imgur.com/EXMhHal
[enable environment variables]: http://imgur.com/sDWN5J3
[Configure access controls]: http://imgur.com/fs4jEJu
[verification parameters]: http://imgur.com/u1iwCBm
