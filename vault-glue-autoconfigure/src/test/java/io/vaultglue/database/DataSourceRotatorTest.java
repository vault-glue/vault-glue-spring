package io.vaultglue.database;

import io.vaultglue.core.VaultGlueEventPublisher;
import io.vaultglue.core.event.CredentialRotatedEvent;
import io.vaultglue.database.VaultGlueDatabaseProperties.DataSourceProperties;
import java.time.Duration;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.context.ApplicationEventPublisher;

import static org.assertj.core.api.Assertions.assertThat;

class DataSourceRotatorTest {

    @Test
    void rotate_shouldSwapDelegateAndPublishEvent() {
        ApplicationEventPublisher applicationEventPublisher = Mockito.mock(ApplicationEventPublisher.class);
        VaultGlueEventPublisher eventPublisher = new VaultGlueEventPublisher(applicationEventPublisher);
        DataSourceRotator rotator = new DataSourceRotator(eventPublisher);

        DataSource initialDs = Mockito.mock(DataSource.class);
        VaultGlueDelegatingDataSource delegating =
                new VaultGlueDelegatingDataSource("primary", initialDs, "old_user");

        DataSourceProperties props = new DataSourceProperties();
        props.setJdbcUrl("jdbc:h2:mem:testrotate;DB_CLOSE_DELAY=-1");
        props.setDriverClassName("org.h2.Driver");

        rotator.rotate(delegating, props, "new_user", "new_pass", Duration.ofHours(1));

        assertThat(delegating.getCurrentUsername()).isEqualTo("new_user");

        ArgumentCaptor<org.springframework.context.ApplicationEvent> eventCaptor =
                ArgumentCaptor.forClass(org.springframework.context.ApplicationEvent.class);
        Mockito.verify(applicationEventPublisher).publishEvent(eventCaptor.capture());

        Object published = eventCaptor.getValue();
        assertThat(published).isInstanceOf(CredentialRotatedEvent.class);

        CredentialRotatedEvent event = (CredentialRotatedEvent) published;
        assertThat(event.getOldUsername()).isEqualTo("old_user");
        assertThat(event.getNewUsername()).isEqualTo("new_user");
    }
}
