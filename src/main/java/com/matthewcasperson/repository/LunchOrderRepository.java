package com.matthewcasperson.repository;

import com.matthewcasperson.models.LunchOrder;
import java.util.List;
import org.springframework.data.repository.CrudRepository;

public interface LunchOrderRepository extends CrudRepository<LunchOrder, Long> {
  List<LunchOrder> findByActivityId(String lastName);
}