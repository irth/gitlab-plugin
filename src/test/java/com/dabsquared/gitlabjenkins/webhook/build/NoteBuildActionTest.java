package com.dabsquared.gitlabjenkins.webhook.build;

import static com.dabsquared.gitlabjenkins.cause.CauseDataBuilder.causeData;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

import com.dabsquared.gitlabjenkins.GitLabPushTrigger;
import com.dabsquared.gitlabjenkins.cause.CauseData;
import com.dabsquared.gitlabjenkins.cause.GitLabWebHookCause;
import com.dabsquared.gitlabjenkins.gitlab.hook.model.NoteHook;
import hudson.model.FreeStyleProject;
import hudson.model.ParametersAction;
import hudson.model.StringParameterValue;
import hudson.model.queue.QueueTaskFuture;
import hudson.plugins.git.GitSCM;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import org.apache.commons.io.IOUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.jvnet.hudson.test.JenkinsRule;
import org.kohsuke.stapler.HttpResponses;
import org.kohsuke.stapler.StaplerResponse;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

/**
 * @author Nikolay Ustinov
 */
@RunWith(MockitoJUnitRunner.class)
public class NoteBuildActionTest {

    @ClassRule
    public static JenkinsRule jenkins = new JenkinsRule();

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Rule
    public ExpectedException exception = ExpectedException.none();

    @Mock
    private StaplerResponse response;

    @Mock
    private GitLabPushTrigger trigger;

    private String gitRepoUrl;
    private String commitSha1;

    @Before
    public void setup() throws Exception {
        Git.init().setDirectory(tmp.getRoot()).call();
        tmp.newFile("test");
        Git git = Git.open(tmp.getRoot());
        git.add().addFilepattern("test");
        RevCommit commit = git.commit().setMessage("test").call();
        commitSha1 = commit.getId().getName();
        gitRepoUrl = tmp.getRoot().toURI().toString();
    }


    @Test
    public void build() throws IOException {
        FreeStyleProject testProject = jenkins.createFreeStyleProject();
        testProject.addTrigger(trigger);

        exception.expect(HttpResponses.HttpResponseException.class);
        new NoteBuildAction(testProject, getJson("NoteEvent.json"), null).execute(response);

        verify(trigger).onPost(any(NoteHook.class));
    }

    @Test
    public void build_alreadyBuiltMR_alreadyBuiltMR() throws IOException, ExecutionException, InterruptedException {
        FreeStyleProject testProject = jenkins.createFreeStyleProject();
        testProject.addTrigger(trigger);
        testProject.setScm(new GitSCM(gitRepoUrl));
        QueueTaskFuture<?> future = testProject.scheduleBuild2(0, new ParametersAction(new StringParameterValue("gitlabTargetBranch", "master")));
        future.get();

        exception.expect(HttpResponses.HttpResponseException.class);
        new NoteBuildAction(testProject, getJson("NoteEvent_alreadyBuiltMR.json"), null).execute(response);

        verify(trigger).onPost(any(NoteHook.class));
    }

    @Test
    public void build_alreadyBuiltMR_differentTargetBranch() throws IOException, ExecutionException, InterruptedException {
        FreeStyleProject testProject = jenkins.createFreeStyleProject();
        testProject.addTrigger(trigger);
        testProject.setScm(new GitSCM(gitRepoUrl));
        QueueTaskFuture<?> future = testProject.scheduleBuild2(0, new GitLabWebHookCause(causeData()
                .withActionType(CauseData.ActionType.NOTE)
                .withSourceProjectId(1)
                .withTargetProjectId(1)
                .withBranch("feature")
                .withSourceBranch("feature")
                .withUserName("")
                .withSourceRepoHomepage("https://gitlab.org/test")
                .withSourceRepoName("test")
                .withSourceNamespace("test-namespace")
                .withSourceRepoUrl("git@gitlab.org:test.git")
                .withSourceRepoSshUrl("git@gitlab.org:test.git")
                .withSourceRepoHttpUrl("https://gitlab.org/test.git")
                .withMergeRequestTitle("Test")
                .withMergeRequestId(1)
                .withMergeRequestIid(1)
                .withTargetBranch("master")
                .withTargetRepoName("test")
                .withTargetNamespace("test-namespace")
                .withTargetRepoSshUrl("git@gitlab.org:test.git")
                .withTargetRepoHttpUrl("https://gitlab.org/test.git")
                .withTriggeredByUser("test")
                .withLastCommit("123")
                .withTargetProjectUrl("https://gitlab.org/test")
                .build()));
        future.get();

        exception.expect(HttpResponses.HttpResponseException.class);
        new NoteBuildAction(testProject, getJson("NoteEvent_alreadyBuiltMR.json"), null).execute(response);

        verify(trigger).onPost(any(NoteHook.class));
    }

    private String getJson(String name) throws IOException {
        return IOUtils.toString(getClass().getResourceAsStream(name)).replace("${commitSha1}", commitSha1);
    }
}
