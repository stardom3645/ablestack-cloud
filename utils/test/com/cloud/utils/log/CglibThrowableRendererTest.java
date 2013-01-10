// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// the License.  You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.
package com.cloud.utils.log;

import junit.framework.TestCase;

import org.apache.log4j.Logger;

import com.cloud.utils.component.ComponentContext;
import com.cloud.utils.db.DB;
import com.cloud.utils.exception.CloudRuntimeException;


public class CglibThrowableRendererTest extends TestCase {
    private final static Logger s_logger = Logger.getLogger(CglibThrowableRendererTest.class);
    public static class Test {
        @DB
        public void exception1() {
            throw new IllegalArgumentException("What a bad exception");
        }
        public void exception2() {
            try {
                exception1();
            } catch (Exception e) {
                throw new CloudRuntimeException("exception2", e);
            }
        }
        @DB
        public void exception() {
            try {
                exception2();
            } catch (Exception e) {
                throw new CloudRuntimeException("exception", e);
            }
        }
    }

    public void testException() {
        Test test = ComponentContext.inject(Test.class);
        try {
            test.exception();
        } catch (Exception e) {
            s_logger.warn("exception caught", e);
        }
    }
}
