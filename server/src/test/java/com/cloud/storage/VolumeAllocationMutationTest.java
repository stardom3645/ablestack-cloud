// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.
package com.cloud.storage;

import java.util.Map;
import org.apache.cloudstack.resourcedetail.dao.DiskOfferingDetailsDao;
import com.cloud.storage.dao.VolumeDao;
import com.cloud.storage.dao.VolumeDetailsDao;
import com.cloud.exception.InvalidParameterValueException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.InjectMocks;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.test.util.ReflectionTestUtils;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;

@RunWith(MockitoJUnitRunner.Silent.class)
public class VolumeAllocationMutationTest {
    @InjectMocks private VolumeApiServiceImpl service;
    @Mock private VolumeDao volumes;
    @Mock private VolumeDetailsDao details;
    @Mock private DiskOfferingDetailsDao offerings;
    @Mock private VolumeVO volume;
    @Before public void setup() { when(volumes.findById(1L)).thenReturn(volume); when(volume.getId()).thenReturn(1L); }
    private void validate(Long target) { ReflectionTestUtils.invokeMethod(service, "validateAllocationMutation", 1L, target, "resize"); }
    @Test(expected = InvalidParameterValueException.class) public void fullResizeStopsBeforeMutation() { when(details.listDetailsKeyPairs(1L)).thenReturn(VolumeAllocationPolicy.request("full", "raw", null)); validate(null); }
    @Test(expected = InvalidParameterValueException.class) public void explicitThinLifecycleAlsoNeedsQualification() { when(details.listDetailsKeyPairs(1L)).thenReturn(VolumeAllocationPolicy.request("off", "raw", null)); validate(null); }
    @Test(expected = InvalidParameterValueException.class) public void legacyVolumeCannotAcquireFullByOfferingChange() { when(details.listDetailsKeyPairs(1L)).thenReturn(Map.of()); when(offerings.listDetailsKeyPairs(2L)).thenReturn(VolumeAllocationPolicy.request("full", "raw", null)); validate(2L); }
    @Test public void legacyPathRetainsExistingValidationAndDoesNotWriteEvidence() { when(details.listDetailsKeyPairs(1L)).thenReturn(Map.of()); validate(null); verify(volumes, never()).update(anyLong(), any()); verifyNoMoreInteractions(offerings); }
}
