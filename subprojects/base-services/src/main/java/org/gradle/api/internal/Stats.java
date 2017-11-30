/*
 * Copyright 2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.api.internal;

public class Stats {
    private static final Operation analysis = new Operation();
    private static final Operation snapshot = new Operation();
    private static final Operation compileProcessing = new Operation();
    private static final Operation compileExecution = new Operation();

    public static void snapshot(String path, long startNs, long endNs) {
        snapshot.add(startNs, endNs);
    }

    public static void analysis(String path, long startNs, long endNs) {
        analysis.add(startNs, endNs);
    }

    public static void compileProcessing(String path, long startNs, long endNs) {
        compileProcessing.add(startNs, endNs);
    }

    public static void compileExecution(String path, long startNs, long endNs) {
        compileExecution.add(startNs, endNs);
    }

    public static void report() {
        System.out.println("ANALYSIS");
        analysis.reportAndClear();
        System.out.println("SNAPSHOT");
        snapshot.reportAndClear();
        System.out.println("COMPILE PROCESSING");
        compileProcessing.reportAndClear();
        System.out.println("COMPILE EXECUTION");
        compileExecution.reportAndClear();
    }

    private static class Operation {
        int count;
        long durationNs;

        void add(long startNs, long endNs) {
            synchronized (this) {
                count++;
                durationNs += (endNs - startNs);
            }
        }

        void reportAndClear() {
            synchronized (this) {
                System.out.println("count: " + count);
                System.out.println("total duration " + durationNs / 1000000.0);
                if (count == 0) {
                    System.out.println("average duration: -");
                } else {
                    System.out.println("average duration: " + (durationNs / (count * 1000000.0)));
                }
                count = 0;
                durationNs = 0;
            }
        }
    }
}
