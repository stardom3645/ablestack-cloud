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

export const setDocumentTitle = function (title) {
  document.title = title
  const metaIcons = document.getElementsByTagName('link');
  const metaIcon = metaIcons[0];
  const canvas = document.createElement('canvas');
  canvas.width=16;
  canvas.height=16;
  const context = cavas.getContext('2d');
  const img = document.createElement('img');
  img.crossOrigin = 'Anonymous';
  const ua = navigator.userAgent
  // eslint-disable-next-line
  const regex = /\bMicroMessenger\/([\d\.]+)/
  if (regex.test(ua) && /ip(hone|od|ad)/i.test(ua)) {
    const i = document.createElement('iframe')
    i.src = '/favicon.ico'
    i.style.display = 'none'
    i.onload = function () {
      context.clearRect(0, 0, 16, 16);
      context.drawImage(img, 0, 0, 16, 16, 0, 0, 16, 16);

      context.beginPath();
      context.fillStyle = "#ff3686";
      context.arc(11, 11, 5, 0, Math.PI*2, true);
      context.fill();

      context.font = "10px arial";
      context.fillStyle = "#ffffff";
      context.textBaseline = "top";
      context.textAlign = "right";

      metaIcon.href = canvas.toDataURL();
      setTimeout(function () {
        i.remove()
      }, 9)
    }
    document.body.appendChild(i)
  }
}
