/*
 * Copyright 2026 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.template.soy.data.internal;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import com.google.template.soy.data.RecordProperty;
import com.google.template.soy.data.restricted.StringData;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class ParamStoreTest {
  private static final RecordProperty VALUE = RecordProperty.get("value");
  private static final RecordProperty TEXT = RecordProperty.get("text");

  @Test
  public void freeze_marksStoreFrozenAndRejectsSubsequentMutations() {
    ParamStore store = new ParamStore(1).setField(VALUE, StringData.forValue("en"));
    assertThat(store.isFrozen()).isFalse();

    assertThat(store.freeze()).isSameInstanceAs(store);
    assertThat(store.isFrozen()).isTrue();
    assertThat(store.freeze()).isSameInstanceAs(store);
    assertThat(store.isFrozen()).isTrue();

    StringData english = StringData.forValue("English");
    assertThrows(IllegalStateException.class, () -> store.setField(TEXT, english));
  }

  @Test
  public void soyRecordImpl_concurrentWrapOfFrozenParamStore_isThreadSafe() throws Exception {
    ParamStore sharedConstantStore =
        new ParamStore(2)
            .setField(VALUE, StringData.forValue("af"))
            .setField(TEXT, StringData.forValue("Afrikaans"))
            .freeze();

    int threadCount = 8;
    int iterationsPerThread = 1_000;
    ExecutorService executor = Executors.newFixedThreadPool(threadCount);
    CountDownLatch ready = new CountDownLatch(threadCount);
    CountDownLatch start = new CountDownLatch(1);
    try {
      List<Future<Integer>> futures = new ArrayList<>(threadCount);
      for (int t = 0; t < threadCount; t++) {
        futures.add(
            executor.submit(() -> {
              ready.countDown();
              start.await();
              int count = 0;
              for (int i = 0; i < iterationsPerThread; i++) {
                SoyRecordImpl record = new SoyRecordImpl(sharedConstantStore);
                if (record.getField(VALUE).stringValue().equals("af")
                    && record.getField(TEXT).stringValue().equals("Afrikaans")) {
                  count++;
                }
              }
              return count;
            }));
      }
      ready.await();
      start.countDown();
      for (Future<Integer> future : futures) {
        assertThat(future.get()).isEqualTo(iterationsPerThread);
      }
    } finally {
      executor.shutdownNow();
    }
    assertThat(sharedConstantStore.isFrozen()).isTrue();
  }
}
