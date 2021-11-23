package com.matthewcasperson.models;

import lombok.Data;

@Data
public class CardOptions {
  private Integer nextCardToSend;
  private Integer currentCard;
  private String option;
  private String custom;
}
