package io.jenkins.plugins.parameterfoundry.companion.history;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import hudson.ExtensionList;
import io.jenkins.plugins.parameterfoundry.provider.ParameterFoundryGlobalConfiguration;
import java.io.IOException;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class LegacyFileHistoryReaderTest {
    @Test
    void configurationLinkageFailureBecomesStickyErrorInsteadOfEscapingInitializer(JenkinsRule jenkins)
            throws Exception {
        var reader = spy(new LegacyFileHistoryReader());
        doThrow(new NoClassDefFoundError("Synthetic configuration lookup failure"))
                .when(reader)
                .configuration();
        assertDoesNotThrow(reader::start);
        assertEquals(LegacyFileHistoryReader.State.ERROR, reader.getState());
        assertFalse(reader.isCompatibilityAccepted());
        assertTrue(reader.isActivated());
        assertEquals(
                "ERROR\n",
                Files.readString(jenkins.jenkins
                        .getRootDir()
                        .toPath()
                        .resolve("parameter-foundry-history/file-reader-state.txt")));
    }

    @Test
    void getterAndStateRootFailuresAlsoRemainClosed(JenkinsRule jenkins) {
        var configuration = mock(ParameterFoundryGlobalConfiguration.class);
        when(configuration.isLegacyFileHistoryEnabled()).thenReturn(true);
        when(configuration.getLegacyFileHistoryMaxBytes())
                .thenThrow(new IllegalStateException("Synthetic policy getter failure"));
        var reader = spy(new LegacyFileHistoryReader());
        doReturn(configuration).when(reader).configuration();
        assertDoesNotThrow(reader::start);
        assertEquals(LegacyFileHistoryReader.State.ERROR, reader.getState());
        assertFalse(reader.isCompatibilityAccepted());
        var rootFailure = spy(new LegacyFileHistoryReader());
        doThrow(new NoClassDefFoundError("Synthetic status root failure"))
                .when(rootFailure)
                .managedRoot();
        assertDoesNotThrow(rootFailure::start);
        assertEquals(LegacyFileHistoryReader.State.ERROR, rootFailure.getState());
        assertFalse(rootFailure.isCompatibilityAccepted());
    }

    @Test
    void defaultOffDoesNotExposeOldTypesAndLiveOptInCannotRead(JenkinsRule jenkins) throws Exception {
        var configuration = ParameterFoundryGlobalConfiguration.get();
        var reader = ExtensionList.lookupSingleton(LegacyFileHistoryReader.class);
        assertFalse(configuration.isLegacyFileHistoryEnabled());
        assertEquals(104857600L, configuration.getLegacyFileHistoryMaxBytes());
        assertEquals(LegacyFileHistoryReader.State.DISABLED, reader.getState());
        assertFalse(reader.isActivated());
        for (String type : new String[] {
            "AbstractFileParameterValue",
            "Base64FileParameterValue",
            "StashedFileParameterValue",
            "FileParameterWrapper"
        }) {
            assertThrows(ClassNotFoundException.class, () -> jenkins.jenkins
                    .getPluginManager()
                    .uberClassLoader
                    .loadClass("io.jenkins.plugins.file_parameters." + type));
        }
        assertThrows(IOException.class, LegacyFileHistoryReader::maximumFileBytes);
        configuration.setLegacyFileHistoryEnabled(true);
        assertEquals(LegacyFileHistoryReader.State.RESTART_REQUIRED, reader.getState());
        assertFalse(reader.isCompatibilityAccepted());
        assertThrows(IOException.class, LegacyFileHistoryReader::maximumFileBytes);
        configuration.load();
        assertTrue(configuration.isLegacyFileHistoryEnabled());
    }

    @Test
    void changedHistoryBudgetIsPersistedButCannotGrantLiveAcceptance(JenkinsRule jenkins) throws Exception {
        var configuration = ParameterFoundryGlobalConfiguration.get();
        assertThrows(IllegalArgumentException.class, () -> configuration.setLegacyFileHistoryMaxBytes(0));
        configuration.setLegacyFileHistoryMaxBytes(209715200L);
        configuration.load();
        assertEquals(209715200L, configuration.getLegacyFileHistoryMaxBytes());
        var reader = ExtensionList.lookupSingleton(LegacyFileHistoryReader.class);
        assertEquals(LegacyFileHistoryReader.State.RESTART_REQUIRED, reader.getState());
        assertThrows(IOException.class, LegacyFileHistoryReader::maximumFileBytes);
        assertEquals(Long.MAX_VALUE, LegacyFileHistoryReader.addBudget(Long.MAX_VALUE - 1, 10));
        assertEquals(13, LegacyFileHistoryReader.addBudget(10, 3));
    }
}
