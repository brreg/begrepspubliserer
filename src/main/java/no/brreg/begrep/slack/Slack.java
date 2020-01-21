package no.brreg.begrep.slack;

import com.hubspot.algebra.Result;
import com.hubspot.slack.client.SlackClient;
import com.hubspot.slack.client.methods.params.chat.ChatPostMessageParams;
import com.hubspot.slack.client.models.response.SlackError;
import com.hubspot.slack.client.models.response.chat.ChatPostMessageResponse;


public class Slack {

    public static final String PRODFEIL_CHANNEL = "#prodfeil";


    public ChatPostMessageResponse postMessage(final String channel, final String message) {
        SlackClient slackClient = getClient();
        if (slackClient == null) {
            return null;
        }

        Result<ChatPostMessageResponse, SlackError> postResult = slackClient.postMessage(
                ChatPostMessageParams.builder()
                    .setText(message)
                    .setChannelId(channel)
                    .build()
            ).join();

        return postResult.unwrapOrElseThrow();
    }

    SlackClient getClient() {
        return BasicRuntimeConfig.getClient();
    }

}
