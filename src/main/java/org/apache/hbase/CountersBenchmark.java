/*
 * Copyright (c) 2014, Oracle America, Inc.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  * Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 *
 *  * Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 *
 *  * Neither the name of Oracle nor the names of its contributors may be used
 *    to endorse or promote products derived from this software without
 *    specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF
 * THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.apache.hbase;

import java.util.Random;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.hadoop.hbase.util.Counter;
import org.apache.hadoop.metrics2.lib.MutableHistogram;
import org.apache.hadoop.metrics2.lib.MutableTimeHistogram;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;

import com.lmax.disruptor.EventFactory;
import com.lmax.disruptor.EventHandler;
import com.lmax.disruptor.InsufficientCapacityException;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.dsl.Disruptor;

@State(Scope.Benchmark)
public class CountersBenchmark {

  private AtomicLong atomicLong = new AtomicLong();
  private Counter counter = new Counter();

  // HBase's metric histograms
  private MutableTimeHistogram timeHistogram = new MutableTimeHistogram("foo", "bar");
  private MutableHistogram histogram = new MutableHistogram("baz", "baa");

  private Random random = new Random();

  private final ArrayBlockingQueue<Long> q = new ArrayBlockingQueue<Long>(5000);

  public static final class ValueEvent {
      private long value;

      public long getValue() {
          return value;
      }

      public void setValue(final long value) {
          this.value = value;
      }

      public final static EventFactory<ValueEvent> EVENT_FACTORY = new EventFactory<ValueEvent>() {
          @Override
          public ValueEvent newInstance(){
              return new ValueEvent();
          }
      };
  }

  final EventHandler<ValueEvent> handler = new EventHandler<ValueEvent>() {
      @Override
      public void onEvent(final ValueEvent event, final long sequence, final boolean endOfBatch)
          throws Exception {
          histogram.add(event.value);
      }
  };

  private Disruptor<ValueEvent> disruptor;
  private RingBuffer<ValueEvent> ringBuffer;
  private ExecutorService executor;

  public CountersBenchmark() {
  }

  @Setup
  public void setup() {
    executor = Executors.newSingleThreadExecutor();
    disruptor =
        new Disruptor<ValueEvent>(ValueEvent.EVENT_FACTORY, 4096, executor);
    disruptor.handleEventsWith(handler);
    ringBuffer = disruptor.start();
  }

  @TearDown
  public void tearDown() {
    executor.shutdown();
    disruptor.shutdown();
  }

  //@Benchmark
  public long testIncrementAtomicLong() {
    return atomicLong.incrementAndGet();
  }

  //@Benchmark
  public void testIncrementCounter() {
    counter.increment();
  }

  //@Benchmark
  public void testAddHistogramConstantValue() {
    histogram.add(1);
  }

  @Benchmark
  public void testAddToDisruptor() {
    long seq = -1;
    try {
      seq = ringBuffer.tryNext();
      ValueEvent valueEvent = ringBuffer.get(seq);
      valueEvent.setValue(random.nextInt(1000));
    } catch (InsufficientCapacityException e) {
      // just ignore
    } finally {
      if (seq >= 0) {
        ringBuffer.publish(seq);
      }
    }
  }


  @Benchmark
  public void testAddToBlockingQueue() {
    q.offer(new Long(random.nextInt(1000)));
    if (q.size() >= 4000) {
      q.clear();
    }
  }

  @Benchmark
  public void testAddHistogramRandomValue() {
    histogram.add(random.nextInt(1000));
  }

  //@Benchmark
  public void testAddTimeHistogramConstantValue() {
    timeHistogram.add(1);
  }

  //@Benchmark
  public void testAddTimeHistogramRandomValue() {
    timeHistogram.add(random.nextInt(1000));
  }
}
