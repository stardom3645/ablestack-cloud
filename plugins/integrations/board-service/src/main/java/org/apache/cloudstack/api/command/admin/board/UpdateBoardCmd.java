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

package org.apache.cloudstack.api.command.admin.board;

import javax.inject.Inject;

import org.apache.cloudstack.acl.RoleType;
import org.apache.cloudstack.api.APICommand;
import org.apache.cloudstack.api.ApiConstants;
import org.apache.cloudstack.api.ApiErrorCode;
import org.apache.cloudstack.api.BaseCmd;
import org.apache.cloudstack.api.Parameter;
import org.apache.cloudstack.api.ResponseObject;
import org.apache.cloudstack.api.ServerApiException;
import org.apache.cloudstack.api.command.admin.AdminCmd;
import org.apache.cloudstack.api.response.BoardResponse;
import org.apache.log4j.Logger;

import com.cloud.exception.ConcurrentOperationException;
import com.cloud.utils.exception.CloudRuntimeException;
import com.cloud.board.Board;
import com.cloud.board.BoardService;

@APICommand(name = UpdateBoardCmd.APINAME,
        description = "Update a Board",
        responseObject = BoardResponse.class,
        responseView = ResponseObject.ResponseView.Full,
        entityType = {Board.class},
        authorized = {RoleType.Admin, RoleType.DomainAdmin})
public class UpdateBoardCmd extends BaseCmd implements AdminCmd {
    public static final Logger LOGGER = Logger.getLogger(UpdateBoardCmd.class.getName());
    public static final String APINAME = "updateBoard";

    @Inject
    private BoardService boardService;

    /////////////////////////////////////////////////////
    //////////////// API parameters /////////////////////
    /////////////////////////////////////////////////////
    @Parameter(name = ApiConstants.ID, type = BaseCmd.CommandType.UUID,
            entityType = BoardResponse.class,
            description = "the ID of the Resource Request",
            required = true)
    private Long id;

    @Parameter(name = ApiConstants.TITLE, type = CommandType.STRING,
            description = "the format for the template. Possible values include QCOW2")
    private String title;

    @Parameter(name = ApiConstants.TYPE, type = CommandType.STRING,
    description = "the format for the template. Possible values include QCOW2")
    private String type;

    @Parameter(name = ApiConstants.CONTENT, type = CommandType.STRING,
    description = "the format for the template. Possible values include QCOW2")
    private String content;

    /////////////////////////////////////////////////////
    /////////////////// Accessors ///////////////////////
    /////////////////////////////////////////////////////
    @Override
    public String getCommandName() {
        return APINAME.toLowerCase() + "response";
    }


    public Long getId() {
        return id;
    }


    public String getTitle() {
        return title;
    }


    public String getType() {
        return type;
    }


    public String getContent() {
        return content;
    }


    @Override
    public long getEntityOwnerId() {
        return 0;
    }

    /////////////////////////////////////////////////////
    /////////////// API Implementation///////////////////
    /////////////////////////////////////////////////////
    @Override
    public void execute() throws ServerApiException, ConcurrentOperationException {
        try {
            BoardResponse response = boardService.updateBoard(this);
            if (response == null) {
                throw new ServerApiException(ApiErrorCode.INTERNAL_ERROR, "Failed to update desktop conroller version");
            }
            response.setResponseName(getCommandName());
            setResponseObject(response);
        } catch (CloudRuntimeException ex) {
            throw new ServerApiException(ApiErrorCode.INTERNAL_ERROR, ex.getMessage());
        }
    }
}