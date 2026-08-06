package com.pixierge.api.catalog;

import java.util.UUID;

/** A versioned, durable change that can be replayed into a recovered catalog. */
public interface CatalogChange {

  String type();

  int version();

  String aggregateType();

  UUID aggregateId();

  Object payload();
}
