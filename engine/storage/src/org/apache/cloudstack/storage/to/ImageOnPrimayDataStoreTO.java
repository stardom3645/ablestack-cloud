/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.cloudstack.storage.to;

import org.apache.cloudstack.storage.volume.TemplateOnPrimaryDataStoreInfo;

public class ImageOnPrimayDataStoreTO {
    private final String pathOnPrimaryDataStore;
    private  PrimaryDataStoreTO dataStore;
    private final TemplateTO template;
    public ImageOnPrimayDataStoreTO(TemplateOnPrimaryDataStoreInfo template) {
        this.pathOnPrimaryDataStore = template.getPath();
        //this.dataStore = template.getPrimaryDataStore().getDataStoreTO();
        this.template = new TemplateTO(template.getTemplate());
    }
    
    public String getPathOnPrimaryDataStore() {
        return this.pathOnPrimaryDataStore;
    }
    
    public PrimaryDataStoreTO getPrimaryDataStore() {
        return this.dataStore;
    }
    
    public TemplateTO getTemplate() {
        return this.template;
    }
}
