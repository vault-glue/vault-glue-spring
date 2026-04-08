package io.vaultglue.core;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "vault-glue")
public class VaultGlueProperties {

    private FailureStrategy onFailure = FailureStrategy.RETRY;
    private RetryProperties retry = new RetryProperties();
    private ActuatorProperties actuator = new ActuatorProperties();

    public FailureStrategy getOnFailure() {
        return onFailure;
    }

    public void setOnFailure(FailureStrategy onFailure) {
        this.onFailure = onFailure;
    }

    public RetryProperties getRetry() {
        return retry;
    }

    public void setRetry(RetryProperties retry) {
        this.retry = retry;
    }

    public ActuatorProperties getActuator() {
        return actuator;
    }

    public void setActuator(ActuatorProperties actuator) {
        this.actuator = actuator;
    }

    public static class RetryProperties {
        private int maxAttempts = 3;
        private long delay = 5_000;

        public int getMaxAttempts() {
            return maxAttempts;
        }

        public void setMaxAttempts(int maxAttempts) {
            this.maxAttempts = maxAttempts;
        }

        public long getDelay() {
            return delay;
        }

        public void setDelay(long delay) {
            this.delay = delay;
        }
    }

    public static class ActuatorProperties {
        private boolean enabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }
}
