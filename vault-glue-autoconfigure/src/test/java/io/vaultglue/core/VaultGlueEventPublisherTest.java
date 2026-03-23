package io.vaultglue.core;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;
import io.vaultglue.core.event.CredentialRotatedEvent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class VaultGlueEventPublisherTest {

    @Test
    void publish_shouldDelegateToSpringPublisher() {
        ApplicationEventPublisher springPublisher = mock(ApplicationEventPublisher.class);
        VaultGlueEventPublisher publisher = new VaultGlueEventPublisher(springPublisher);

        CredentialRotatedEvent event = new CredentialRotatedEvent(
                this,
                "database",
                "primary",
                "old-user",
                "new-user",
                Duration.ofHours(1)
        );

        publisher.publish(event);

        ArgumentCaptor<ApplicationEvent> captor = ArgumentCaptor.forClass(ApplicationEvent.class);
        verify(springPublisher).publishEvent(captor.capture());

        CredentialRotatedEvent captured = (CredentialRotatedEvent) captor.getValue();
        assertThat(captured.getEngine()).isEqualTo("database");
        assertThat(captured.getNewUsername()).isEqualTo("new-user");
    }
}
