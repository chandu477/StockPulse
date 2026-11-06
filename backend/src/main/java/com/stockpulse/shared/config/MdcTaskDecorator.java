package com.stockpulse.shared.config;

import java.util.Map;
import org.slf4j.MDC;

/** Copies the caller's MDC (traceId) onto worker threads. */
public final class MdcTaskDecorator {
  private MdcTaskDecorator() {}

  public static Runnable wrap(Runnable task) {
    Map<String, String> context = MDC.getCopyOfContextMap();
    return () -> {
      Map<String, String> previous = MDC.getCopyOfContextMap();
      if (context != null) {
        MDC.setContextMap(context);
      } else {
        MDC.clear();
      }
      try {
        task.run();
      } finally {
        if (previous != null) {
          MDC.setContextMap(previous);
        } else {
          MDC.clear();
        }
      }
    };
  }
}
