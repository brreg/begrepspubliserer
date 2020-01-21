package no.brreg.begrep.slack;

import com.hubspot.slack.client.SlackClient;
import com.hubspot.slack.client.SlackClientFactory;
import com.hubspot.slack.client.SlackClientRuntimeConfig;


public class BasicRuntimeConfig {
    private static final String SLACK_TOKEN     = System.getenv("SLACK_TOKEN");


    public BasicRuntimeConfig() {
    }

    public static SlackClient getClient() {
        return (SLACK_TOKEN==null || SLACK_TOKEN.isEmpty() || "disabled".equalsIgnoreCase(SLACK_TOKEN))
                ? null : SlackClientFactory.defaultFactory().build(get(SLACK_TOKEN));
    }

    public static SlackClientRuntimeConfig get(final String token) {
        return SlackClientRuntimeConfig.builder().setTokenSupplier(() -> token).build();
    }
}
