package com.matthewcasperson;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.bot.builder.ActivityHandler;
import com.microsoft.bot.builder.InvokeResponse;
import com.microsoft.bot.builder.TurnContext;
import com.microsoft.bot.connector.Async;
import com.microsoft.bot.schema.Activity;
import com.microsoft.bot.schema.AdaptiveCardInvokeResponse;
import com.microsoft.bot.schema.AdaptiveCardInvokeValue;
import com.microsoft.bot.schema.Serialization;
import com.microsoft.bot.schema.SignInConstants;
import java.net.HttpURLConnection;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FixedActivityHandler extends ActivityHandler {
  private static final Logger LOGGER = LoggerFactory.getLogger(CateringBot.class);

  @Override
  protected CompletableFuture<InvokeResponse> onInvokeActivity(TurnContext turnContext) {
    if (StringUtils.equals(turnContext.getActivity().getName(), "adaptiveCard/action")) {
      AdaptiveCardInvokeValue invokeValue = null;
      try {
        invokeValue = getAdaptiveCardInvokeValue(turnContext.getActivity());
      } catch (InvokeResponseException e) {
        return Async.completeExceptionally(e);
      }
      return onAdaptiveCardInvoke(turnContext, invokeValue).thenApply(result -> createInvokeResponse(result));
    }

    if (
        StringUtils.equals(
            turnContext.getActivity().getName(), SignInConstants.VERIFY_STATE_OPERATION_NAME
        ) || StringUtils.equals(
            turnContext.getActivity().getName(), SignInConstants.TOKEN_EXCHANGE_OPERATION_NAME
        )
    ) {
      return onSignInInvoke(turnContext).thenApply(aVoid -> createInvokeResponse(null))
          .exceptionally(ex -> {
            if (
                ex instanceof CompletionException
                    && ex.getCause() instanceof InvokeResponseException
            ) {
              InvokeResponseException ire = (InvokeResponseException) ex.getCause();
              return new InvokeResponse(500, "");
            } else if (ex instanceof InvokeResponseException) {
              InvokeResponseException ire = (InvokeResponseException) ex;
              return new InvokeResponse(500, "");
            }
            return new InvokeResponse(HttpURLConnection.HTTP_INTERNAL_ERROR, null);
          });
    }

    CompletableFuture<InvokeResponse> result = new CompletableFuture<>();
    result.complete(new InvokeResponse(HttpURLConnection.HTTP_NOT_IMPLEMENTED, null));
    return result;
  }

  private AdaptiveCardInvokeValue getAdaptiveCardInvokeValue(Activity activity) throws InvokeResponseException {
    if (activity.getValue() == null) {
      AdaptiveCardInvokeResponse response = createAdaptiveCardInvokeErrorResponse(
          HttpURLConnection.HTTP_BAD_REQUEST, "BadRequest", "Missing value property");
      throw new InvokeResponseException(HttpURLConnection.HTTP_BAD_REQUEST, response);
    }

    /*
      The original code required activity.getValue() to be a JsonNode. It is in fact a
      LinkedHashMap, so this change allows the response to proceed.
     */
    Object obj = activity.getValue();
    AdaptiveCardInvokeValue invokeValue = null;
    if (obj instanceof JsonNode) {
      invokeValue = Serialization.treeToValue((JsonNode) obj, AdaptiveCardInvokeValue.class);
    } else if (obj instanceof Map) {
      final ObjectMapper objectMapper = new ObjectMapper();
      objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
      invokeValue = objectMapper.convertValue(obj, AdaptiveCardInvokeValue.class);
    } else {
      AdaptiveCardInvokeResponse response = createAdaptiveCardInvokeErrorResponse(
          HttpURLConnection.HTTP_BAD_REQUEST, "BadRequest", "Value property instanceof not properly formed");
      throw new InvokeResponseException(HttpURLConnection.HTTP_BAD_REQUEST, response);
    }
    /*
      End of changes
     */

    if (invokeValue == null) {
      AdaptiveCardInvokeResponse response = createAdaptiveCardInvokeErrorResponse(
          HttpURLConnection.HTTP_BAD_REQUEST, "BadRequest", "Value property instanceof not properly formed");
      throw new InvokeResponseException(HttpURLConnection.HTTP_BAD_REQUEST, response);
    }

    if (invokeValue.getAction() == null) {
      AdaptiveCardInvokeResponse response = createAdaptiveCardInvokeErrorResponse(
          HttpURLConnection.HTTP_BAD_REQUEST, "BadRequest", "Missing action property");
      throw new InvokeResponseException(HttpURLConnection.HTTP_BAD_REQUEST, response);
    }

    if (!invokeValue.getAction().getType().equals("Action.Execute")) {
      AdaptiveCardInvokeResponse response = createAdaptiveCardInvokeErrorResponse(
          HttpURLConnection.HTTP_BAD_REQUEST, "NotSupported",
          String.format("The action '%s'is not supported.", invokeValue.getAction().getType()));
      throw new InvokeResponseException(HttpURLConnection.HTTP_BAD_REQUEST, response);
    }

    return invokeValue;
  }

  private AdaptiveCardInvokeResponse createAdaptiveCardInvokeErrorResponse(
      Integer statusCode,
      String code,
      String message
  ) {
    AdaptiveCardInvokeResponse adaptiveCardInvokeResponse = new AdaptiveCardInvokeResponse();
    adaptiveCardInvokeResponse.setStatusCode(statusCode);
    adaptiveCardInvokeResponse.setType("application/vnd.getmicrosoft().error");
    com.microsoft.bot.schema.Error error = new com.microsoft.bot.schema.Error();
    error.setCode(code);
    error.setMessage(message);
    adaptiveCardInvokeResponse.setValue(error);
    return adaptiveCardInvokeResponse;
  }
}
