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
package com.cloud.network;

import java.net.URISyntaxException;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.cloud.network.Networks.BroadcastDomainType;

/**
 * @author dhoogland
 * 
 */
public class NetworksTest {

    @Before
    public void setUp() {
    }

    @Test
    public void emptyBroadcastDomainTypeTest() throws URISyntaxException {
        BroadcastDomainType type = BroadcastDomainType.getTypeOf("");
        Assert.assertEquals(
                "an empty uri should mean a broadcasttype of undecided",
                BroadcastDomainType.UnDecided, type);
    }

    @Test
    public void vlanBroadcastDomainTypeTest() throws URISyntaxException {
        String uri1 = "vlan://1";
        String uri2 = "vlan:2";
        BroadcastDomainType type1 = BroadcastDomainType.getTypeOf(uri1);
        BroadcastDomainType type2 = BroadcastDomainType.getTypeOf(uri2);
        String id1 = BroadcastDomainType.getValue(uri1);
        String id2 = BroadcastDomainType.getValue(uri2);
        Assert.assertEquals("uri1 should be of broadcasttype vlan",
                BroadcastDomainType.Vlan, type1);
        Assert.assertEquals("uri2 should be of broadcasttype vlan",
                BroadcastDomainType.Vlan, type2);
        Assert.assertEquals("id1 should be \"1\"", "1", id1);
        Assert.assertEquals("id1 should be \"2\"", "2", id2);
    }

    @Test
    public void otherTypesTest() throws URISyntaxException {
        String bogeyUri = "lswitch://1";
        String uri2 = "mido:2";
        BroadcastDomainType type1 = BroadcastDomainType.getTypeOf(bogeyUri);
        BroadcastDomainType type2 = BroadcastDomainType.getTypeOf(uri2);
        String id1 = BroadcastDomainType.getValue(bogeyUri);
        String id2 = BroadcastDomainType.getValue(uri2);
        Assert.assertEquals("uri1 should be of broadcasttype vlan",
                BroadcastDomainType.Lswitch, type1);
        Assert.assertEquals("uri2 should be of broadcasttype vlan",
                BroadcastDomainType.Mido, type2);
        Assert.assertEquals("id1 should be \"//1\"", "//1", id1);
        Assert.assertEquals("id1 should be \"2\"", "2", id2);
    }
}
