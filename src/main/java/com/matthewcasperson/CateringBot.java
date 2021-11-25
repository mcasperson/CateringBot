// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package com.matthewcasperson;

import com.codepoetics.protonpack.collectors.CompletableFutures;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import com.google.gson.Gson;
import com.matthewcasperson.models.CardOptions;
import com.matthewcasperson.models.LunchOrder;
import com.matthewcasperson.repository.LunchOrderRepository;
import com.microsoft.bot.builder.ConversationState;
import com.microsoft.bot.builder.MessageFactory;
import com.microsoft.bot.builder.StatePropertyAccessor;
import com.microsoft.bot.builder.TurnContext;
import com.microsoft.bot.builder.UserState;
import com.microsoft.bot.schema.AdaptiveCardInvokeResponse;
import com.microsoft.bot.schema.AdaptiveCardInvokeValue;
import com.microsoft.bot.schema.Attachment;
import com.microsoft.bot.schema.ChannelAccount;
import com.mitchellbosecke.pebble.PebbleEngine;
import com.mitchellbosecke.pebble.template.PebbleTemplate;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

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
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private final ConversationState conversationState;
  private final UserState userState;

  @Autowired
  LunchOrderRepository lunchOrderRepository;

  public CateringBot(final ConversationState conversationState, final UserState userState) {
    this.userState = userState;
    this.conversationState = conversationState;
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

  @Override
  protected CompletableFuture<Void> onMessageActivity(TurnContext turnContext) {
    LOGGER.info("CateringBot.onMessageActivity(TurnContext)");

    StatePropertyAccessor<LunchOrder> profileAccessor = userState.createProperty("lunch");
    CompletableFuture<LunchOrder> lunchOrderFuture =
        profileAccessor.get(turnContext, LunchOrder::new);

    try {
      final LunchOrder lunchOrder = lunchOrderFuture.get();
      lunchOrder.setActivityId(turnContext.getActivity().getId());
      lunchOrder.setOrderCreated(Timestamp.from(Instant.now()));

      return turnContext.sendActivity(
          MessageFactory.attachment(createCardAttachment("cards/EntreOptions.json"))
      ).thenApply(sendResult -> null);
    } catch (final Exception ex) {
      return turnContext.sendActivity(
          MessageFactory.text("An exception was thrown: " + ex)
      ).thenApply(sendResult -> null);
    }
  }

  @Override
  protected CompletableFuture<AdaptiveCardInvokeResponse> onAdaptiveCardInvoke(
      TurnContext turnContext, AdaptiveCardInvokeValue invokeValue) {
    LOGGER.info("CateringBot.onAdaptiveCardInvoke(TurnContext, AdaptiveCardInvokeValue)");

    StatePropertyAccessor<LunchOrder> profileAccessor = userState.createProperty("lunch");
    CompletableFuture<LunchOrder> lunchOrderFuture =
        profileAccessor.get(turnContext, LunchOrder::new);

    try {
      final LunchOrder lunchOrder = lunchOrderFuture.get();
      if ("order".equals(invokeValue.getAction().getVerb())) {

        final CardOptions cardOptions = convertObject(invokeValue.getAction().getData(),
            CardOptions.class);

        if (cardOptions.getCurrentCard() == Cards.Entre.number) {
          lunchOrder.setEntre(
              StringUtils.isAllEmpty(cardOptions.getCustom()) ? cardOptions.getOption()
                  : cardOptions.getCustom());
        } else if (cardOptions.getCurrentCard() == Cards.Drink.number) {
          lunchOrder.setDrink(
              StringUtils.isAllEmpty(cardOptions.getCustom()) ? cardOptions.getOption()
                  : cardOptions.getCustom());
        } else if (cardOptions.getCurrentCard() == Cards.Review.number) {
          lunchOrderRepository.save(lunchOrder);
        }



        final AdaptiveCardInvokeResponse response = new AdaptiveCardInvokeResponse();
        response.setStatusCode(200);
        response.setType(CONTENT_TYPE);
        response.setValue(createObjectFromJsonResource(
            Cards.findValueByTypeNumber(cardOptions.getNextCardToSend()).file,
            new HashMap<>() {{
              put("drink", lunchOrder.getDrink());
              put("entre", lunchOrder.getEntre());
              putAll(getRecentOrdersMap());
            }}));

        return CompletableFuture.completedFuture(response);
      }

      throw new Exception("Invalid verb " + invokeValue.getAction().getVerb());

    } catch (final Exception ex) {
      LOGGER.error("Exception thrown in onAdaptiveCardInvoke", ex);
      return CompletableFuture.failedFuture(ex);
    }
  }

  @Override
  public CompletableFuture<Void> onTurn(TurnContext turnContext) {
    return super.onTurn(turnContext)
        // Save any state changes that might have occurred during the turn.
        .thenCompose(turnResult -> conversationState.saveChanges(turnContext))
        .thenCompose(saveResult -> userState.saveChanges(turnContext));
  }

  private Attachment createCardAttachment(final String fileName) throws IOException {
    return createCardAttachment(fileName, null);
  }

  private Attachment createCardAttachment(final String fileName, final Map<String, Object> context)
      throws IOException {
    final Attachment attachment = new Attachment();
    attachment.setContentType(CONTENT_TYPE);
    attachment.setContent(createObjectFromJsonResource(fileName, context));
    return attachment;
  }

  private Object createObjectFromJsonResource(final String fileName,
      final Map<String, Object> context) throws IOException {
    final String resource = readResource(fileName);
    final String processedResource = context == null
        ? resource
        : processTemplate(resource, context);
    final Map<String, String[]> objectMap = new Gson().fromJson(processedResource, Map.class);
    return objectMap;
  }

  private String processTemplate(final String template, final Map<String, Object> context)
      throws IOException {
    final PebbleEngine engine = new PebbleEngine.Builder().autoEscaping(false).build();
    final PebbleTemplate compiledTemplate = engine.getLiteralTemplate(template);
    final Writer writer = new StringWriter();
    compiledTemplate.evaluate(writer, context);
    return writer.toString();
  }

  private String readResource(final String fileName) throws IOException {
    return Resources.toString(Resources.getResource(fileName), Charsets.UTF_8);
  }

  private <T> T convertObject(final Object object, final Class<T> convertTo) {
    final ObjectMapper objectMapper = new ObjectMapper();
    objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    return objectMapper.convertValue(object, convertTo);
  }

  private Map<String, String> getRecentOrdersMap() {
    final List<LunchOrder> recentOrders = lunchOrderRepository.findAll(
        PageRequest.of(0, 3, Sort.by(Sort.Order.desc("orderCreated")))).getContent();

    final Map<String, String> map = new HashMap<>();

    for (int i = 0; i < 3; ++i) {
      map.put("drink" + (i +1), recentOrders.get(i).getDrink());
      map.put("entre" + (i +1), recentOrders.get(i).getEntre());
      map.put("orderCreated" + (i +1), recentOrders.get(i).getOrderCreated().toString());
    }

    return map;
  }
}
