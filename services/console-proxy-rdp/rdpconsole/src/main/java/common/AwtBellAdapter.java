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
package common;

import java.awt.Toolkit;

import streamer.BaseElement;
import streamer.ByteBuffer;
import streamer.Element;
import streamer.FakeSource;
import streamer.Link;
import streamer.Pipeline;
import streamer.PipelineImpl;

public class AwtBellAdapter extends BaseElement {

  public AwtBellAdapter(String id) {
    super(id);
    declarePads();
  }

  private void declarePads() {
    inputPads.put(STDIN, null);
  }

  @Override
  public void handleData(ByteBuffer buf, Link link) {
    if (verbose)
      System.out.println("[" + this + "] INFO: Data received: " + buf + ".");

    if (buf == null)
      return;

    Toolkit.getDefaultToolkit().beep();
  }

  public String toString() {
    return "Bell(" + id + ")";
  }

  /**
   * Example.
   */
  public static void main(String args[]) {
    System.setProperty("streamer.Element.debug", "true");

    Element source = new FakeSource("source") {
      {
        this.incommingBufLength = 0;
        this.delay = 1000;
        this.numBuffers = 3;
      }
    };

    Element sink = new AwtBellAdapter("sink");

    Pipeline pipeline = new PipelineImpl("test");
    pipeline.addAndLink(source, sink);
    pipeline.runMainLoop("source", STDOUT, false, false);
  }

}
