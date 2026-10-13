package io.jenkins.plugins.parameterfoundry.companion.history;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import hudson.model.Item;
import hudson.model.ParametersAction;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.StringParameterDefinition;
import hudson.model.StringParameterValue;
import hudson.model.User;
import hudson.security.ACL;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import jenkins.model.Jenkins;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.kohsuke.stapler.StaplerResponse2;

@WithJenkins
class HistoricalFileDownloadTest {
    @Test
    void readerCanDownloadOnlyTheActualVisibleRunValue(JenkinsRule jenkins) throws Exception {
        var job = jenkins.createFreeStyleProject();
        job.addProperty(new ParametersDefinitionProperty(new StringParameterDefinition("UPLOAD", "")));
        var value = new StringParameterValue("UPLOAD", "metadata");
        var run = job.scheduleBuild2(0, new ParametersAction(value)).get();
        jenkins.jenkins.setSecurityRealm(jenkins.createDummySecurityRealm());
        jenkins.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
                .grant(Jenkins.READ, Item.READ)
                .everywhere()
                .to("reader"));
        var response = mock(StaplerResponse2.class);
        var output = new ByteArrayOutputStream();
        var servletOutput = new jakarta.servlet.ServletOutputStream() {
            @Override
            public void write(int value) {
                output.write(value);
            }

            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setWriteListener(jakarta.servlet.WriteListener listener) {}
        };
        when(response.getOutputStream()).thenReturn(servletOutput);
        var opened = new AtomicBoolean();
        try (var ignored = ACL.as2(User.getById("reader", true).impersonate2())) {
            assertFalse(job.hasPermission(Item.BUILD));
            HistoricalFileDownload.send(value, run, response, owner -> {
                assertSame(run, owner);
                opened.set(true);
                return new ByteArrayInputStream(new byte[] {0, -1, 13});
            });
        }
        assertTrue(opened.get());
        assertArrayEquals(new byte[] {0, -1, 13}, output.toByteArray());
        verify(response).setHeader("Content-Disposition", "attachment");
        verify(response).setHeader("X-Content-Type-Options", "nosniff");
        verify(response).setContentType("application/octet-stream");
        var detached = mock(StaplerResponse2.class);
        HistoricalFileDownload.send(new StringParameterValue("UPLOAD", "metadata"), run, detached, owner -> {
            fail("Detached value must not open a source");
            return null;
        });
        verify(detached).sendError(404);
        verify(detached, never()).getOutputStream();
    }

    @Test
    void anonymousAndCoreFilteredValuesNeverOpenTheSource(JenkinsRule jenkins) throws Exception {
        var job = jenkins.createFreeStyleProject();
        job.addProperty(new ParametersDefinitionProperty(new StringParameterDefinition("UPLOAD", "")));
        var value = new StringParameterValue("UPLOAD", "metadata");
        var run = job.scheduleBuild2(0, new ParametersAction(value)).get();
        var hidden = new StringParameterValue("HIDDEN", "metadata");
        run.replaceAction(new ParametersAction(value, hidden));
        assertFalse(run.getAction(ParametersAction.class).getParameters().contains(hidden));
        var response = mock(StaplerResponse2.class);
        HistoricalFileDownload.send(hidden, run, response, owner -> {
            fail("Core-filtered value must not open a source");
            return null;
        });
        verify(response).sendError(404);
        jenkins.jenkins.setSecurityRealm(jenkins.createDummySecurityRealm());
        jenkins.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy());
        try (var ignored = ACL.as2(Jenkins.ANONYMOUS2)) {
            assertThrows(
                    org.springframework.security.access.AccessDeniedException.class,
                    () -> HistoricalFileDownload.send(value, run, response, owner -> {
                        fail("Anonymous request must not open a source");
                        return null;
                    }));
        }
    }

    @Test
    void outputAcquisitionFailureStillClosesTheSource(JenkinsRule jenkins) throws Exception {
        var job = jenkins.createFreeStyleProject();
        job.addProperty(new ParametersDefinitionProperty(new StringParameterDefinition("UPLOAD", "")));
        var value = new StringParameterValue("UPLOAD", "metadata");
        var run = job.scheduleBuild2(0, new ParametersAction(value)).get();
        var response = mock(StaplerResponse2.class);
        var failure = new IOException("Disconnected client");
        when(response.getOutputStream()).thenThrow(failure);
        var closed = new AtomicBoolean();
        var source = new ByteArrayInputStream(new byte[] {1}) {
            @Override
            public void close() {
                closed.set(true);
            }
        };
        assertSame(
                failure,
                assertThrows(
                        IOException.class, () -> HistoricalFileDownload.send(value, run, response, owner -> source)));
        assertTrue(closed.get());
    }
}
