// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package com.matthewcasperson;

import com.codepoetics.protonpack.collectors.CompletableFutures;
import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import com.google.gson.Gson;
import com.microsoft.bot.builder.ConversationState;
import com.microsoft.bot.builder.MessageFactory;
import com.microsoft.bot.builder.TurnContext;
import com.microsoft.bot.builder.UserState;
import com.microsoft.bot.schema.AdaptiveCardInvokeResponse;
import com.microsoft.bot.schema.AdaptiveCardInvokeValue;
import com.microsoft.bot.schema.Attachment;
import com.microsoft.bot.schema.ChannelAccount;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class implements the functionality of the Bot.
 *
 * <p>
 * This is where application specific logic for interacting with the users would be added. For this
 * sample, the {@link #onMessageActivity(TurnContext)} echos the text back to the user. The {@link
 * #onMembersAdded(List, TurnContext)} will send a greeting to new conversation participants.
 * </p>
 */
public class CateringBot extends FixedActivityHandler {

  private static final String CONTENT_TYPE = "application/vnd.microsoft.card.adaptive";
  private static final Logger LOGGER = LoggerFactory.getLogger(CateringBot.class);
  private final ConversationState conversationState;
  private final UserState userState;

  public CateringBot(final ConversationState conversationState, final UserState userState) {
    this.userState = userState;
    this.conversationState = conversationState;
  }

  @Override
  protected CompletableFuture<Void> onMessageActivity(TurnContext turnContext) {
    LOGGER.info("CateringBot.onMessageActivity(TurnContext)");

    try {
      return turnContext.sendActivity(
          MessageFactory.attachment(createCardAttachment("cards/EntreOptions.json"))
      ).thenApply(sendResult -> null);
    } catch (final IOException ex) {
      return turnContext.sendActivity(
          MessageFactory.text("An exception was thrown: " + ex)
      ).thenApply(sendResult -> null);
    }
  }

  @Override
  protected CompletableFuture<AdaptiveCardInvokeResponse> onAdaptiveCardInvoke(
      TurnContext turnContext, AdaptiveCardInvokeValue invokeValue) {
    LOGGER.info("CateringBot.onAdaptiveCardInvoke(TurnContext, AdaptiveCardInvokeValue)");

    try {
      if ("order".equals(invokeValue.getAction().getVerb())) {
        final AdaptiveCardInvokeResponse response = new AdaptiveCardInvokeResponse();
        response.setStatusCode(200);
        response.setType(CONTENT_TYPE);
        response.setValue(createObjectFromJsonResource("cards/DrinkOptions.json"));
        return CompletableFuture.completedFuture(response);
      }

      throw new Exception("Invalid verb " + invokeValue.getAction().getVerb());

    } catch (final Exception ex) {
      LOGGER.error("Exception thrown in onAdaptiveCardInvoke", ex);
      return CompletableFuture.failedFuture(ex);
    }
  }

  @Override
  protected CompletableFuture<Void> onMembersAdded(
      List<ChannelAccount> membersAdded,
      TurnContext turnContext
  ) {
    LOGGER.info("CateringBot.onMembersAdded(List<ChannelAccount>, TurnContext)");

    return membersAdded.stream()
        .filter(
            member -> !StringUtils
                .equals(member.getId(), turnContext.getActivity().getRecipient().getId())
        ).map(channel -> turnContext.sendActivity(MessageFactory.text(
            "Hello and welcome! Type any message to begin placing a lunch order.")))
        .collect(CompletableFutures.toFutureList()).thenApply(resourceResponses -> null);
  }

  private Attachment createCardAttachment(final String fileName) throws IOException {
    final Attachment attachment = new Attachment();
    attachment.setContentType(CONTENT_TYPE);
    attachment.setContent(createObjectFromJsonResource(fileName));
    return attachment;
  }

  private Object createObjectFromJsonResource(final String fileName) throws IOException {
    final String resource = readResource(fileName);
    final Map<String, String[]> objectMap = new Gson().fromJson(resource, Map.class);
    return objectMap;
  }

  private String readResource(final String fileName) throws IOException {
    return Resources.toString(Resources.getResource(fileName), Charsets.UTF_8);
  }

}
