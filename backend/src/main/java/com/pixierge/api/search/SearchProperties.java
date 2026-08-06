package com.pixierge.api.search;

import java.time.ZoneId;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "pixierge.search")
public class SearchProperties {
  private ZoneId timeZone = ZoneId.of("UTC");

  public ZoneId getTimeZone() {
    return timeZone;
  }

  public void setTimeZone(ZoneId timeZone) {
    this.timeZone = timeZone;
  }
}
