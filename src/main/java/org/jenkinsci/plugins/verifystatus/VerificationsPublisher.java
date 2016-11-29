package org.jenkinsci.plugins.verifystatus;

import com.google.common.base.MoreObjects;
import com.google.common.collect.*;
import com.google.gerrit.extensions.api.GerritApi;
import com.google.gerrit.extensions.api.changes.VerificationInfo;
import com.google.gerrit.extensions.api.changes.VerifyInput;
import com.google.gerrit.extensions.api.changes.VerifyStatusApi;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.sonyericsson.hudson.plugins.gerrit.trigger.GerritManagement;
import com.sonyericsson.hudson.plugins.gerrit.trigger.config.IGerritHudsonTriggerConfig;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.GerritTrigger;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.data.SkipVote;
import com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.GerritUserCause;
import com.urswolfer.gerrit.client.rest.GerritAuthData;
import com.urswolfer.gerrit.client.rest.GerritRestApiFactory;

import hudson.EnvVars;
import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Result;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Publisher;
import hudson.triggers.Trigger;

import org.kohsuke.stapler.DataBoundConstructor;

import java.io.IOException;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.jenkinsci.plugins.verifystatus.util.Localization.getLocalized;

public class VerificationsPublisher extends Publisher {

  public static final String GERRIT_FILE_DELIMITER = "/";
  public static final String EMPTY_STR = "";

  private static final Logger
    LOGGER = Logger.getLogger(VerificationsPublisher.class.getName());
  public static final String JOB_NAME_ENV_VAR_NAME = "JOB_NAME";
  public static final String BUILD_URL_ENV_VAR_NAME = "BUILD_URL";
  public static final String
    GERRIT_CHANGE_NUMBER_ENV_VAR_NAME ="GERRIT_CHANGE_NUMBER";
  public static final String GERRIT_NAME_ENV_VAR_NAME = "GERRIT_NAME";
  public static final String
    GERRIT_PATCHSET_NUMBER_ENV_VAR_NAME = "GERRIT_PATCHSET_NUMBER";
  public static final String
    GERRIT_EVENT_COMMENT_TEXT_ENV_VAR_NAME =  "GERRIT_EVENT_COMMENT_TEXT";

  private final String verifyStatusName;
  private final String verifyStatusURL;
  private final String verifyStatusComment;
  private final String verifyStatusReporter;
  private final String verifyStatusCategory;
  private final String verifyStatusRerun;


  @DataBoundConstructor
  public VerificationsPublisher(String verifyStatusName, String verifyStatusURL,
      String verifyStatusComment, String verifyStatusReporter,
      String verifyStatusCategory, String verifyStatusRerun) {

    this.verifyStatusName = MoreObjects.firstNonNull(verifyStatusName, "");
    this.verifyStatusURL = MoreObjects.firstNonNull(verifyStatusURL, "");
    this.verifyStatusComment = MoreObjects.firstNonNull(verifyStatusComment, "");
    this.verifyStatusReporter =
        MoreObjects.firstNonNull(verifyStatusReporter, "Jenkins");
    this.verifyStatusCategory =
        MoreObjects.firstNonNull(verifyStatusCategory, "");
    this.verifyStatusRerun =
        MoreObjects.firstNonNull(verifyStatusRerun, "");
  }

  public String getVerifyStatusName() {
    return verifyStatusName;
  }

  public String getVerifyStatusURL() {
    return verifyStatusURL;
  }

  public String getVerifyStatusComment() {
    return verifyStatusComment;
  }

  public String getVerifyStatusReporter() {
    return verifyStatusReporter;
  }

  public String getVerifyStatusCategory() {
    return verifyStatusCategory;
  }

  public String getVerifyStatusRerun() {
    return verifyStatusRerun;
  }

  // publish report after job completes to get correct duration
  @Override
  public boolean needsToRunAfterFinalized() {
    return true;
  }

  private GerritTrigger getGerritTrigger(AbstractBuild<?, ?> build) {
    AbstractProject<?,?> project = build.getProject();
    Iterator<?> it = project.getTriggers().values().iterator();
    while (it.hasNext()) {
      Trigger<?> t = (Trigger<?>) it.next();
      if (t.getClass().equals(GerritTrigger.class)) {
        GerritTrigger gerritTrigger = (GerritTrigger) t;
        return gerritTrigger;
      }
    }
    return null;
  }

  @Override
  public boolean perform(AbstractBuild build, Launcher launcher,
      BuildListener listener) throws IOException, InterruptedException {

    Result result = build.getResult();
    // do not report results for aborted builds
    // TODO: possibly allow saving report even if build did not complete?
    if (!result.isCompleteBuild()) {
      return true;
    }

    // Prepare Gerrit REST API client
    // Check Gerrit configuration is available
    String gerritNameEnvVar =
        getEnvVar(build, listener, GERRIT_NAME_ENV_VAR_NAME);
    GerritTrigger trigger = GerritTrigger.getTrigger(build.getProject());
    String gerritServerName = gerritNameEnvVar != null ? gerritNameEnvVar
        : trigger != null ? trigger.getServerName() : null;
    if (gerritServerName == null) {
      logMessage(listener, "jenkins.plugin.error.gerrit.server.empty",
          Level.SEVERE);
      return false;
    }
    IGerritHudsonTriggerConfig gerritConfig =
        GerritManagement.getConfig(gerritServerName);
    if (gerritConfig == null) {
      logMessage(listener, "jenkins.plugin.error.gerrit.config.empty",
          Level.SEVERE);
      return false;
    }

    if (!gerritConfig.isUseRestApi()) {
      logMessage(listener, "jenkins.plugin.error.gerrit.restapi.off",
          Level.SEVERE);
      return false;
    }
    if (gerritConfig.getGerritHttpUserName() == null) {
      logMessage(listener, "jenkins.plugin.error.gerrit.user.empty",
          Level.SEVERE);
      return false;
    }
    // System Environment variables may not be enabled
    String buildUrl = getEnvVar(build, listener, BUILD_URL_ENV_VAR_NAME);
    if (buildUrl == null){
      logMessage(listener, "jenkins.plugin.error.gerrit.sysenv.disabled",
          Level.WARNING);
    }

    GerritRestApiFactory gerritRestApiFactory = new GerritRestApiFactory();
    GerritAuthData.Basic authData =
        new GerritAuthData.Basic(gerritConfig.getGerritFrontEndUrl(),
            gerritConfig.getGerritHttpUserName(),
            gerritConfig.getGerritHttpPassword());
    GerritApi gerritApi = gerritRestApiFactory.create(authData);
    try {
      int changeNumber = Integer.parseInt(
          getEnvVar(build, listener, GERRIT_CHANGE_NUMBER_ENV_VAR_NAME));
      int patchSetNumber = Integer.parseInt(
          getEnvVar(build, listener, GERRIT_PATCHSET_NUMBER_ENV_VAR_NAME));
      VerifyStatusApi verifyStatusApi = gerritApi.changes().id(changeNumber)
          .revision(patchSetNumber).verifyStatus();
      logMessage(listener, "jenkins.plugin.connected.to.gerrit", Level.INFO,
          new Object[] {gerritServerName, changeNumber, patchSetNumber});

      // Create verification to Gerrit from build
      VerificationInfo data = new VerificationInfo();
      String jobName = getVerifyStatusName();
      if (jobName.isEmpty()) {
        jobName = getEnvVar(build, listener, JOB_NAME_ENV_VAR_NAME);
      }
      String inBuildUrl = getVerifyStatusURL();
      if (!inBuildUrl.isEmpty()) {
        data.url = inBuildUrl;
      } else {
        if (buildUrl != null) {
          data.url = buildUrl;
        }
      }
      String reporter = getVerifyStatusReporter();
      if (!reporter.isEmpty()) {
        data.reporter = reporter;
      } else {
        data.reporter = gerritConfig.getGerritHttpUserName();
      }
      String inComment = getVerifyStatusComment();
      if (!inComment.isEmpty()) {
        data.comment = inComment;
      }
      data.duration = build.getDurationString();

      // let Gerrit know whether this job abstained from voting.
      GerritTrigger gerritTrigger = getGerritTrigger(build);
      if (gerritTrigger != null) {
        SkipVote sv = gerritTrigger.getSkipVote();
        if (gerritTrigger.isSilentMode() || sv.isOnFailed()
            || sv.isOnNotBuilt() || sv.isOnSuccessful() || sv.isOnUnstable()) {
          data.abstain = true;
        }
      }

      // Gerrit event may not contain a comment message
      String replyComment = getEnvVar(build, listener,
          GERRIT_EVENT_COMMENT_TEXT_ENV_VAR_NAME);
      if (replyComment != null &&
          replyComment.contains(getVerifyStatusRerun().trim())) {
        data.rerun = true;
      }
      if (build.getCause(GerritUserCause.class) != null){
        data.rerun = true;
      }
      String inCategory = getVerifyStatusCategory();
      if (!inCategory.isEmpty()) {
        data.category = inCategory;
      }

      // determine the vote value from build result
      if (result.isWorseOrEqualTo(Result.FAILURE)) {
        data.value = -1;
      } else if (result == Result.UNSTABLE) {
        data.value = 0;
      } else {
        data.value = 1;
      }

      // Post verification
      Map<String, VerificationInfo> jobResult = Maps.newHashMap();
      jobResult.put(jobName, data);
      VerifyInput verifyInput = new VerifyInput();
      verifyInput.verifications = jobResult;
      verifyStatusApi.verifications(verifyInput);
      logMessage(listener, "jenkins.plugin.verification.sent", Level.INFO);
    } catch (RestApiException e) {
      listener.getLogger()
          .println("Unable to post verification: " + e.getMessage());
      LOGGER.severe("Unable to post verification: " + e.getMessage());
      return false;
    }

    return true;
  }

  private String getEnvVar(AbstractBuild build, BuildListener listener,
      String name) throws IOException, InterruptedException {
    EnvVars envVars = build.getEnvironment(listener);
    return envVars.get(name);
  }

  private void logMessage(BuildListener listener, String message, Level l,
      Object... params) {
    message = getLocalized(message, params);
    if (listener != null) { // it can be it tests
      listener.getLogger().println(message);
    }
    LOGGER.log(l, message);
  }

  // Overridden for better type safety.
  // If your plugin doesn't really define any property on Descriptor,
  // you don't have to do this.
  @Override
  public DescriptorImpl getDescriptor() {
    return (DescriptorImpl) super.getDescriptor();
  }

  @Override
  public BuildStepMonitor getRequiredMonitorService() {
    return BuildStepMonitor.NONE;
  }

  /**
   * Descriptor for {@link VerificationsPublisher}. Used as a singleton. The
   * class is marked as public so that it can be accessed from views.
   */
  @Extension // This indicates to Jenkins that this is an implementation of an
             // extension point.
  public static final class DescriptorImpl
      extends BuildStepDescriptor<Publisher> {

    /**
     * In order to load the persisted global configuration, you have to call
     * load() in the constructor.
     */
    public DescriptorImpl() {
      load();
    }

    @Override
    public boolean isApplicable(Class<? extends AbstractProject> aClass) {
      // Indicates that this builder can be used with all kinds of project types
      return true;
    }

    /**
     * This human readable name is used in the configuration screen.
     */
    @Override
    public String getDisplayName() {
      return getLocalized("jenkins.plugin.build.step.name");
    }

  }
}

