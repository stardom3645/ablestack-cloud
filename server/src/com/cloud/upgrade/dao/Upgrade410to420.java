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

package com.cloud.upgrade.dao;

import com.cloud.utils.exception.CloudRuntimeException;
import com.cloud.utils.script.Script;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

import org.apache.log4j.Logger;

public class Upgrade410to420 implements DbUpgrade {
	final static Logger s_logger = Logger.getLogger(Upgrade410to420.class);

	@Override
	public String[] getUpgradableVersionRange() {
		return new String[] { "4.1.0", "4.2.0" };
	}

	@Override
	public String getUpgradedVersion() {
		return "4.2.0";
	}

	@Override
	public boolean supportsRollingUpgrade() {
		return false;
	}

	@Override
	public File[] getPrepareScripts() {
		String script = Script.findScript("", "db/schema-410to420.sql");
        if (script == null) {
            throw new CloudRuntimeException("Unable to find db/schema-410to420.sql");
        }

        return new File[] { new File(script) };
	}

	@Override
	public void performDataMigration(Connection conn) {
	    PreparedStatement sql = null;
	    try {
	        sql = conn.prepareStatement("update vm_template set image_data_store_id = 1 where type = 'SYSTEM' or type = 'BUILTIN'");
	        sql.executeUpdate();
        } catch (SQLException e) {
            throw new CloudRuntimeException("Failed to upgrade vm template data store uuid: " + e.toString());
        } finally {
            if (sql != null) {
                try {
                    sql.close();
                } catch (SQLException e) {
                }
            }
        }
	}

	@Override
	public File[] getCleanupScripts() {
        String script = Script.findScript("", "db/schema-410to420-cleanup.sql");
        if (script == null) {
            throw new CloudRuntimeException("Unable to find db/schema-410to420-cleanup.sql");
        }

        return new File[] { new File(script) };
	    }
    }