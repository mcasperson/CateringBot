package com.matthewcasperson.models;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import lombok.Data;

@Data
@Entity
public class LunchOrder {

  @Id
  @GeneratedValue(strategy= GenerationType.IDENTITY)
  private Long id;
  private String activityId;
  private java.sql.Timestamp orderCreated;
  private String entre;
  private String drink;
}
