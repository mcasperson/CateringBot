// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package com.matthewcasperson;

import com.codepoetics.protonpack.collectors.CompletableFutures;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Charsets;
import com.google.common.io.Files;
import com.google.common.io.Resources;
import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.gson.JsonParser;
import com.microsoft.bot.builder.ActivityHandler;
import com.microsoft.bot.builder.MessageFactory;
import com.microsoft.bot.builder.TurnContext;
import com.microsoft.bot.schema.Attachment;
import com.microsoft.bot.schema.ChannelAccount;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * This class implements the functionality of the Bot.
 *
 * <p>
 * This is where application specific logic for interacting with the users would be added. For this
 * sample, the {@link #onMessageActivity(TurnContext)} echos the text back to the user. The {@link
 * #onMembersAdded(List, TurnContext)} will send a greeting to new conversation participants.
 * </p>
 */
public class CateringBot extends ActivityHandler {

  private static final String CONTENT_TYPE = "application/vnd.microsoft.card.adaptive";

  @Override
  protected CompletableFuture<Void> onMessageActivity(TurnContext turnContext) {
    try {
      return turnContext.sendActivity(
          MessageFactory.attachment(createCardAttachment("cards/InputField.json"))
      ).thenApply(sendResult -> null);
    } catch (final IOException ex) {
      return turnContext.sendActivity(
          MessageFactory.text("An exception was thrown: " + ex)
      ).thenApply(sendResult -> null);
    }
  }

  @Override
  protected CompletableFuture<Void> onMembersAdded(
      List<ChannelAccount> membersAdded,
      TurnContext turnContext
  ) {
    return membersAdded.stream()
        .filter(
            member -> !StringUtils
                .equals(member.getId(), turnContext.getActivity().getRecipient().getId())
        ).map(channel -> turnContext.sendActivity(MessageFactory.text("Hello and welcome!")))
        .collect(CompletableFutures.toFutureList()).thenApply(resourceResponses -> null);
  }

  private Attachment createCardAttachment(final String fileName) throws IOException {
    final String resource = readResource(fileName);
    final Attachment attachment = new Attachment();
    attachment.setContentType(CONTENT_TYPE);

    Map<String, String[]> son = new Gson().fromJson(resource, Map.class);

    attachment.setContent(son);
    return attachment;
  }

  private String readResource(final String fileName) throws IOException {
    return Resources.toString(Resources.getResource(fileName), Charsets.UTF_8);
  }

}
