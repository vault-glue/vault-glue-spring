package io.vaultglue.autoconfigure;

import com.zaxxer.hikari.HikariDataSource;
import io.vaultglue.core.FailureStrategyHandler;
import io.vaultglue.core.VaultGlueEventPublisher;
import io.vaultglue.database.DataSourceRotator;
import io.vaultglue.database.HikariDataSourceFactory;
import io.vaultglue.database.VaultGlueDatabaseProperties;
import io.vaultglue.database.VaultGlueDatabaseProperties.DataSourceProperties;
import io.vaultglue.database.VaultGlueDelegatingDataSource;
import io.vaultglue.database.dynamic.DynamicLeaseListener;
import io.vaultglue.database.static_.StaticCredentialProvider;
import io.vaultglue.database.static_.StaticRefreshScheduler;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.vault.core.VaultTemplate;
import org.springframework.vault.core.lease.SecretLeaseContainer;

@AutoConfiguration(after = VaultGlueCoreAutoConfiguration.class)
@ConditionalOnClass(HikariDataSource.class)
@ConditionalOnBean(VaultTemplate.class)
@EnableConfigurationProperties(VaultGlueDatabaseProperties.class)
public class VaultGlueDatabaseAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(VaultGlueDatabaseAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    public DataSourceRotator vaultGlueDataSourceRotator(VaultGlueEventPublisher eventPublisher) {
        return new DataSourceRotator(eventPublisher);
    }

    @Bean
    @ConditionalOnMissingBean
    public StaticCredentialProvider vaultGlueStaticCredentialProvider(VaultTemplate vaultTemplate) {
        return new StaticCredentialProvider(vaultTemplate);
    }

    @Bean(destroyMethod = "shutdown")
    @ConditionalOnMissingBean
    public StaticRefreshScheduler vaultGlueStaticRefreshScheduler(
            StaticCredentialProvider credentialProvider,
            DataSourceRotator rotator,
            FailureStrategyHandler failureStrategyHandler) {
        return new StaticRefreshScheduler(credentialProvider, rotator, failureStrategyHandler);
    }

    @Bean
    @ConditionalOnMissingBean(name = "vaultGlueDataSources")
    public VaultGlueDataSources vaultGlueDataSources(
            VaultGlueDatabaseProperties databaseProperties,
            StaticCredentialProvider credentialProvider,
            StaticRefreshScheduler refreshScheduler,
            DataSourceRotator rotator,
            VaultGlueEventPublisher eventPublisher,
            FailureStrategyHandler failureStrategyHandler,
            VaultTemplate vaultTemplate,
            ObjectProvider<SecretLeaseContainer> leaseContainerProvider) {

        Map<String, VaultGlueDelegatingDataSource> sources = new LinkedHashMap<>();
        SecretLeaseContainer leaseContainer = leaseContainerProvider.getIfAvailable();

        databaseProperties.getSources().forEach((name, props) -> {
            if (!props.isEnabled()) {
                log.info("[VaultGlue] DataSource '{}' is disabled, skipping", name);
                return;
            }

            log.info("[VaultGlue] Initializing DataSource '{}' (type={})", name, props.getType());

            if (props.getType() == null) {
                throw new IllegalArgumentException(
                        "[VaultGlue] DataSource type is required for '" + name
                                + "'. Supported: static, dynamic");
            }
            if (props.getRole() == null || props.getRole().isBlank()) {
                throw new IllegalArgumentException(
                        "[VaultGlue] DataSource role is required for '" + name + "'");
            }
            if (props.getJdbcUrl() == null || props.getJdbcUrl().isBlank()) {
                throw new IllegalArgumentException(
                        "[VaultGlue] DataSource jdbc-url is required for '" + name + "'");
            }

            VaultGlueDelegatingDataSource ds = switch (props.getType()) {
                case STATIC -> createStaticDataSource(name, props, credentialProvider, refreshScheduler);
                case DYNAMIC -> createDynamicDataSource(name, props, vaultTemplate, leaseContainer,
                        rotator, eventPublisher, failureStrategyHandler);
            };

            sources.put(name, ds);
            log.info("[VaultGlue] DataSource '{}' initialized", name);
        });

        return new VaultGlueDataSources(sources);
    }

    @Bean
    @ConditionalOnMissingBean(DataSource.class)
    public DataSource dataSource(VaultGlueDataSources vaultGlueDataSources,
                                  VaultGlueDatabaseProperties databaseProperties) {
        // Find the source marked as primary
        for (var entry : databaseProperties.getSources().entrySet()) {
            if (entry.getValue().isPrimary() && vaultGlueDataSources.contains(entry.getKey())) {
                log.info("[VaultGlue] Registering '{}' as primary DataSource", entry.getKey());
                return vaultGlueDataSources.get(entry.getKey());
            }
        }

        // Fall back to the first source if no primary is configured
        var first = vaultGlueDataSources.getAll().entrySet().iterator();
        if (first.hasNext()) {
            var entry = first.next();
            log.info("[VaultGlue] Registering '{}' as primary DataSource (first source)", entry.getKey());
            return entry.getValue();
        }

        throw new IllegalStateException("[VaultGlue] No DataSource sources configured");
    }

    private VaultGlueDelegatingDataSource createStaticDataSource(
            String name, DataSourceProperties props,
            StaticCredentialProvider credentialProvider,
            StaticRefreshScheduler refreshScheduler) {

        StaticCredentialProvider.DbCredential cred =
                credentialProvider.getCredential(props.getBackend(), props.getRole());

        HikariDataSource hikari = HikariDataSourceFactory.create(name, props, cred.username(), cred.password());
        VaultGlueDelegatingDataSource delegating =
                new VaultGlueDelegatingDataSource(name, hikari, cred.username());

        refreshScheduler.schedule(name, delegating, props);
        return delegating;
    }

    private VaultGlueDelegatingDataSource createDynamicDataSource(
            String name, DataSourceProperties props,
            VaultTemplate vaultTemplate,
            SecretLeaseContainer leaseContainer,
            DataSourceRotator rotator,
            VaultGlueEventPublisher eventPublisher,
            FailureStrategyHandler failureStrategyHandler) {

        if (leaseContainer == null) {
            throw new IllegalStateException(
                    "[VaultGlue] SecretLeaseContainer is required for dynamic DataSource '" + name
                            + "'. Ensure spring-cloud-vault-config is on the classpath.");
        }

        // Start with a placeholder DataSource — replaced via rotation once SecretLeaseContainer issues credentials
        HikariDataSource placeholder = HikariDataSourceFactory.create(
                name, props, "placeholder", "placeholder");
        VaultGlueDelegatingDataSource delegating =
                new VaultGlueDelegatingDataSource(name, placeholder, "pending");

        DynamicLeaseListener listener = new DynamicLeaseListener(
                leaseContainer, rotator, eventPublisher, failureStrategyHandler);
        try {
            // register() requests credentials from SecretLeaseContainer and waits for the initial credential
            listener.register(name, delegating, props);
        } catch (Exception e) {
            // Close placeholder on registration failure to prevent connection pool leak
            if (!placeholder.isClosed()) {
                placeholder.close();
                log.debug("[VaultGlue] Closed placeholder DataSource for '{}' after registration failure", name);
            }
            throw e;
        }

        // Close placeholder after successful rotation — real DataSource is now active
        if (!placeholder.isClosed()) {
            placeholder.close();
            log.debug("[VaultGlue] Closed placeholder DataSource for '{}'", name);
        }

        return delegating;
    }

    /**
     * Container for all VaultGlue-managed DataSources.
     * Users can inject this to access named DataSources.
     */
    public static class VaultGlueDataSources {

        private final Map<String, VaultGlueDelegatingDataSource> sources;

        public VaultGlueDataSources(Map<String, VaultGlueDelegatingDataSource> sources) {
            this.sources = Collections.unmodifiableMap(sources);
        }

        public DataSource get(String name) {
            VaultGlueDelegatingDataSource ds = sources.get(name);
            if (ds == null) {
                throw new IllegalArgumentException(
                        "[VaultGlue] No DataSource found with name: " + name);
            }
            return ds;
        }

        public boolean contains(String name) {
            return sources.containsKey(name);
        }

        public Map<String, VaultGlueDelegatingDataSource> getAll() {
            return sources;
        }
    }
}
