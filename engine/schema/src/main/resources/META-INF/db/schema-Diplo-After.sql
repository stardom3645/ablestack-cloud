-- Licensed to the Apache Software Foundation (ASF) under one
-- or more contributor license agreements.  See the NOTICE file
-- distributed with this work for additional information
-- regarding copyright ownership.  The ASF licenses this file
-- to you under the Apache License, Version 2.0 (the
-- "License"); you may not use this file except in compliance
-- with the License.  You may obtain a copy of the License at
--
--   http://www.apache.org/licenses/LICENSE-2.0
--
-- Unless required by applicable law or agreed to in writing,
-- software distributed under the License is distributed on an
-- "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
-- KIND, either express or implied.  See the License for the
-- specific language governing permissions and limitations
-- under the License.

--;
-- Schema upgrade from ablestack-cerato to ablestack-diplo
--;

DROP VIEW IF EXISTS `cloud`.`event_view`;
CREATE VIEW `cloud`.`event_view` AS
    SELECT
        event.id,
        event.uuid,
        event.type,
        event.state,
        event.description,
        event.resource_id,
        event.resource_type,
        event.created,
        event.level,
        event.parameters,
        event.start_id,
        eve.uuid start_uuid,
        event.user_id,
        event.archived,
        event.display,
        event.client_ip,
        user.username user_name,
        account.id account_id,
        account.uuid account_uuid,
        account.account_name account_name,
        account.type account_type,
        domain.id domain_id,
        domain.uuid domain_uuid,
        domain.name domain_name,
        domain.path domain_path,
        projects.id project_id,
        projects.uuid project_uuid,
        projects.name project_name
    FROM
        `cloud`.`event`
            INNER JOIN
        `cloud`.`account` ON event.account_id = account.id
            INNER JOIN
        `cloud`.`domain` ON event.domain_id = domain.id
            INNER JOIN
        `cloud`.`user` ON event.user_id = user.id
            LEFT JOIN
        `cloud`.`projects` ON projects.project_account_id = event.account_id
            LEFT JOIN
        `cloud`.`event` eve ON event.start_id = eve.id;
