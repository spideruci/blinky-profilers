package org.spideruci.analysis.dynamic.profilers;

import java.io.PrintStream;

import org.spideruci.analysis.dynamic.api.EmptyProfiler;
import org.spideruci.analysis.trace.EventType;
import org.spideruci.analysis.trace.TraceEvent;

import org.spideruci.analysis.dynamic.Profiler;

public class TimeAndCountTracker extends EmptyProfiler {

  private PrintStream REAL_OUT() {
    return Profiler.REAL_OUT;
  }


  public static final int K = 22;
  public static final EventType[] eventTypes = EventType.values();
  public static final int insnStartType = EventType.$enter$.ordinal();
  
  public static long probeTime = 0;

  private static long probeCounter = 0L;
  
  private static long counter = 0L;
  private static long[] timers = new long[K];
  private static long[] counters = new long[K];
  
  private void tick(EventType type, long time) {
    int idx = type.ordinal() - insnStartType;
    long counter = counters[idx];
    counters[idx] = counter + 1;
    TimeAndCountTracker.counter += 1;
    
    long timer = timers[idx];
    timers[idx] = timer + time;
  }
  
  private void tick(final TraceEvent e) {
    probeCounter += 1;
    EventType type = e.getExecInsnType();
    long time = System.currentTimeMillis() - probeTime;
    tick(type, time);
  }

  @Override
  public String description() {
    return "TimeAndCountTracker";
  }

  @Override
  public void startProfiling() {
    probeTime = 0L;
    probeCounter = 0L;

    counter = 0L;

    for (int i = 0; i < K; i += 1) {
      timers[i] = 0L;
      counters[i] = 0L;
    }
  }

  @Override
  public void endProfiling() {
    REAL_OUT().println("Total ticks:" + probeCounter);
    REAL_OUT().println("Total Event Count:" + counter);
    for (int i = 0; i < K; i += 1) {
      EventType eventType = eventTypes[i + insnStartType];
      final String eventTypeName = eventType.toString();
      REAL_OUT().println((i + insnStartType) + " // " + eventTypeName + ":: Event Count:" + counters[i] + "; Event Time:" + timers[i]);
    }
  }
  
  @Override
  public void willProfile() {
    probeTime = System.currentTimeMillis();
  }

  @Override
  public void profileMethodEntry(final TraceEvent e) {
    tick(e);
  }

  @Override
  public void profileMethodArgument(final TraceEvent e) {
    tick(e);
  }

  @Override
  public void profileMethodInvoke(final TraceEvent e) {
    tick(e);
  }

  @Override
  public void profileInsn(final TraceEvent e) {
    tick(e);
  }

  @Override
  public void profileFieldInsn(final TraceEvent e) {
    tick(e);
  }

  @Override
  public void profileVarInsn(final TraceEvent e) {
    tick(e);
  }

  @Override
  public void profileArrayInsn(final TraceEvent e) {
    tick(e);
  }

  @Override
  public void profileMethodExit(final TraceEvent e) {
    tick(e);
  }

}
