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

package com.cloud.board;

import org.apache.cloudstack.api.command.admin.board.AddBoardCmd;
import org.apache.cloudstack.api.command.admin.board.AddBoardFilesCmd;
import org.apache.cloudstack.api.command.admin.board.DeleteBoardCmd;
import org.apache.cloudstack.api.command.admin.board.ListBoardCmd;
import org.apache.cloudstack.api.command.admin.board.UpdateBoardCmd;
import org.apache.cloudstack.api.response.ListResponse;
import org.apache.cloudstack.api.response.BoardResponse;
import org.apache.cloudstack.framework.config.ConfigKey;
import org.apache.cloudstack.framework.config.Configurable;

import com.cloud.utils.component.PluggableService;
import com.cloud.utils.exception.CloudRuntimeException;

public interface BoardService extends PluggableService, Configurable {

    static final ConfigKey<Boolean> BoardEnabled = new ConfigKey<Boolean>("Advanced", Boolean.class,
    "cloud.board.enabled",
    "false",
    "Indicates whether board service plugin is enabled or not. Management server restart needed on change",
    true);

    ListResponse<BoardResponse> listBoard(ListBoardCmd cmd);
    BoardResponse addBoard(AddBoardCmd cmd);
    BoardResponse addBoardFiles(AddBoardFilesCmd cmd);
    boolean deleteBoard(DeleteBoardCmd cmd) throws CloudRuntimeException;
    BoardResponse updateBoard(UpdateBoardCmd cmd) throws CloudRuntimeException;
}