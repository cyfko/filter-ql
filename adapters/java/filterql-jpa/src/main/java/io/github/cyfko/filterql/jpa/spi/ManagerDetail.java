package io.github.cyfko.filterql.jpa.spi;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

public record ManagerDetail(Predicate predicate, CriteriaQuery<?> query, CriteriaBuilder cb, Root<?> root){}
