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

            VaultGlueDelegatingDataSource ds;
            if ("static".equalsIgnoreCase(props.getType())) {
                ds = createStaticDataSource(name, props, credentialProvider, refreshScheduler);
            } else if ("dynamic".equalsIgnoreCase(props.getType())) {
                ds = createDynamicDataSource(name, props, vaultTemplate, leaseContainer,
                        rotator, eventPublisher, failureStrategyHandler);
            } else {
                throw new IllegalArgumentException(
                        "Unknown database type '" + props.getType() + "' for '" + name
                                + "'. Supported: static, dynamic");
            }

            sources.put(name, ds);
            log.info("[VaultGlue] DataSource '{}' initialized", name);
        });

        return new VaultGlueDataSources(sources);
    }

    @Bean
    @ConditionalOnMissingBean(DataSource.class)
    public DataSource dataSource(VaultGlueDataSources vaultGlueDataSources,
                                  VaultGlueDatabaseProperties databaseProperties) {
        // primary로 설정된 것 찾기
        for (var entry : databaseProperties.getSources().entrySet()) {
            if (entry.getValue().isPrimary() && vaultGlueDataSources.contains(entry.getKey())) {
                log.info("[VaultGlue] Registering '{}' as primary DataSource", entry.getKey());
                return vaultGlueDataSources.get(entry.getKey());
            }
        }

        // primary 설정 없으면 첫 번째 것
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

        HikariDataSource hikari = HikariDataSourceFactory.create(props, cred.username(), cred.password());
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

        String credPath = props.getBackend() + "/creds/" + props.getRole();
        var response = vaultTemplate.read(credPath);
        if (response == null || response.getData() == null) {
            throw new RuntimeException(
                    "[VaultGlue] Failed to read dynamic credential from: " + credPath);
        }

        String username = (String) response.getData().get("username");
        String password = (String) response.getData().get("password");

        HikariDataSource hikari = HikariDataSourceFactory.create(props, username, password);
        VaultGlueDelegatingDataSource delegating =
                new VaultGlueDelegatingDataSource(name, hikari, username);

        if (leaseContainer != null) {
            DynamicLeaseListener listener = new DynamicLeaseListener(
                    leaseContainer, rotator, eventPublisher, failureStrategyHandler, vaultTemplate);
            listener.register(name, delegating, props);
        } else {
            log.warn("[VaultGlue] SecretLeaseContainer not available. " +
                    "Dynamic lease renewal disabled for '{}'", name);
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
