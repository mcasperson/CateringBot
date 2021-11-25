package com.matthewcasperson.repository;

import com.matthewcasperson.models.LunchOrder;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.CrudRepository;

public interface LunchOrderRepository extends CrudRepository<LunchOrder, Long> {
  List<LunchOrder> findByActivityId(String lastName);
  Page<LunchOrder> findAll(Pageable pageable);
}