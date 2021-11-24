package com.matthewcasperson;

import java.util.Arrays;

public enum Cards {
  Entre(0, "cards/EntreOptions.json"),
  Drink(1, "cards/DrinkOptions.json"),
  Review(2, "cards/ReviewOrder.json"),
  ReviewAll(3, "cards/RecentOrders.json"),
  Confirmation(4, "cards/Confirmation.json");

  public final String file;
  public final int number;

  Cards(int number, String file) {
    this.file = file;
    this.number = number;
  }

  public static Cards findValueByTypeNumber(int number) throws Exception {
    return Arrays.stream(Cards.values()).filter(v ->
        v.number == number).findFirst().orElseThrow(() ->
        new Exception("Unknown Cards.number: " + number));
  }
}
